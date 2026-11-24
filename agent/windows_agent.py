import json
import socket
import uuid
import time
import queue
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import tkinter as tk

PORT = 8765
REQUEST_TIMEOUT = 60

requests_queue = queue.Queue()
events_queue = queue.Queue()
lock = threading.Lock()
decisions = {}
active_request = None

minecraft_status = "not_in_world"


def get_lan_ip():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.0.2.1", 80))
        return sock.getsockname()[0]
    except OSError:
        return "IP не определён"
    finally:
        sock.close()


def make_response(handler, code, data):
    body = json.dumps(data, ensure_ascii=False).encode("utf-8")
    handler.send_response(code)
    handler.send_header(
        "Content-Type", "application/json; charset=utf-8"
    )
    handler.send_header("Content-Length", str(len(body)))
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()
    handler.wfile.write(body)


class AgentHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        global active_request

        if self.path == "/status":
            make_response(self, 200, {
                "app": "KOCMOMATICS",
                "status": "online"
            })
            return

        if self.path == "/minecraft-status":
            with lock:
                current_status = minecraft_status

            make_response(self, 200, {
                "status": "online",
                "minecraft": current_status
            })
            return

        if self.path.startswith("/decision?id="):
            request_id = self.path.split("=", 1)[1]

            with lock:
                item = decisions.get(request_id)

                if item is None:
                    result = "unknown"
                elif item["status"] == "pending":
                    if time.time() > item["expires"]:
                        item["status"] = "expired"
                    result = item["status"]
                else:
                    result = item["status"]

            make_response(self, 200, {"decision": result})
            return

        make_response(self, 404, {"error": "not found"})

    def do_POST(self):
        global active_request, minecraft_status

        if self.path == "/minecraft-event":
            if self.client_address[0] not in ("127.0.0.1", "::1"):
                make_response(self, 403, {"error": "local only"})
                return

            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0 or length > 1024:
                    make_response(self, 400, {"error": "invalid size"})
                    return

                data = json.loads(self.rfile.read(length))
                event = data.get("event")

                if event not in ("join", "leave"):
                    make_response(self, 400, {"error": "invalid event"})
                    return

                with lock:
                    if event == "join":
                        minecraft_status = "in_world"
                    else:
                        minecraft_status = "not_in_world"

                events_queue.put(event)
                print("Minecraft event:", event)

                make_response(self, 200, {"status": "received"})
            except (ValueError, json.JSONDecodeError):
                make_response(self, 400, {"error": "invalid JSON"})
            return

        if self.path != "/request":
            make_response(self, 404, {"error": "not found"})
            return

        client_ip = self.client_address[0]
        request_id = uuid.uuid4().hex

        with lock:
            if active_request is not None:
                current = decisions.get(active_request)

                if current and current["status"] == "pending":
                    if time.time() <= current["expires"]:
                        make_response(self, 409, {
                            "error": "request already pending"
                        })
                        return

                    current["status"] = "expired"

            decisions[request_id] = {
                "status": "pending",
                "expires": time.time() + REQUEST_TIMEOUT
            }
            active_request = request_id

        requests_queue.put({
            "id": request_id,
            "ip": client_ip
        })

        print("Получен запрос от планшета:", client_ip)

        make_response(self, 200, {
            "request_id": request_id,
            "status": "pending"
        })

    def log_message(self, format, *args):
        pass


def resolve_request(request_id, decision):
    global active_request

    with lock:
        item = decisions.get(request_id)

        if item and item["status"] == "pending":
            item["status"] = decision

        if active_request == request_id:
            active_request = None


def show_confirmation(root, request):
    request_id = request["id"]
    client_ip = request["ip"]

    with lock:
        item = decisions.get(request_id)
        if not item or item["status"] != "pending":
            return

    window = tk.Toplevel(root)
    window.title("KOCMOMATICS — Запрос")
    window.geometry("390x210")
    window.resizable(False, False)
    window.attributes("-topmost", True)

    tk.Label(
        window,
        text="KOCMOMATICS",
        font=("Arial", 18, "bold")
    ).pack(pady=12)

    tk.Label(
        window,
        text=f"Устройство {client_ip}\nзапрашивает подключение.",
        font=("Arial", 12),
        justify="center"
    ).pack(pady=8)

    buttons = tk.Frame(window)
    buttons.pack(pady=12)

    def choose(value):
        resolve_request(request_id, value)
        print("Решение владельца:", value)
        window.destroy()

    tk.Button(
        buttons,
        text="Разрешить",
        width=13,
        command=lambda: choose("allowed")
    ).pack(side="left", padx=8)

    tk.Button(
        buttons,
        text="Отклонить",
        width=13,
        command=lambda: choose("denied")
    ).pack(side="left", padx=8)

    window.protocol(
        "WM_DELETE_WINDOW",
        lambda: choose("denied")
    )

    window.grab_set()
    window.focus_force()


def show_minecraft_event(root, event):
    window = tk.Toplevel(root)
    window.title("KOCMOMATICS — Minecraft")
    window.geometry("350x150")
    window.resizable(False, False)
    window.attributes("-topmost", True)

    if event == "join":
        message = "Minecraft: игрок вошёл в мир."
    else:
        message = "Minecraft: игрок покинул мир."

    tk.Label(
        window,
        text="KOCMOMATICS",
        font=("Arial", 17, "bold")
    ).pack(pady=12)

    tk.Label(
        window,
        text=message,
        font=("Arial", 11)
    ).pack(pady=8)

    tk.Button(
        window,
        text="Закрыть",
        command=window.destroy
    ).pack(pady=8)


def process_requests(root):
    try:
        while True:
            request = requests_queue.get_nowait()
            show_confirmation(root, request)
    except queue.Empty:
        pass

    try:
        while True:
            event = events_queue.get_nowait()
            show_minecraft_event(root, event)
    except queue.Empty:
        pass

    root.after(250, lambda: process_requests(root))


def main():
    root = tk.Tk()
    root.withdraw()

    server = ThreadingHTTPServer(
        ("0.0.0.0", PORT),
        AgentHandler
    )

    worker = threading.Thread(
        target=server.serve_forever,
        daemon=True
    )
    worker.start()

    print("===== KOCMOMATICS WINDOWS AGENT =====")
    print("Статус: запущен")
    print("IP компьютера:", get_lan_ip())
    print("Порт:", PORT)
    print("Minecraft status: /minecraft-status")
    print("Для остановки нажми Ctrl+C в этой консоли.")

    process_requests(root)

    try:
        root.mainloop()
    except KeyboardInterrupt:
        pass
    finally:
        server.shutdown()
        server.server_close()
        root.destroy()


if __name__ == "__main__":
    main()
