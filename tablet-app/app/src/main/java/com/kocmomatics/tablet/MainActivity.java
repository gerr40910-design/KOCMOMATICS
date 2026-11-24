package com.kocmomatics.tablet;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView status;
    private EditText ipInput;
    private Button requestButton;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(35, 15, 55));
        getWindow().setNavigationBarColor(Color.rgb(20, 10, 30));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(28, 28, 28, 28);
        layout.setBackgroundColor(Color.rgb(20, 10, 30));

        TextView title = new TextView(this);
        title.setText("KOCMOMATICS");
        title.setTextColor(Color.rgb(180, 100, 255));
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("TABLET CONTROL");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setHint("IP компьютера, например 192.168.1.10");
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.LTGRAY);

        status = new TextView(this);
        status.setText("Компьютер: не проверен");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(17);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 25, 0, 25);

        requestButton = new Button(this);
        requestButton.setText("Запросить подключение");
        requestButton.setOnClickListener(v -> requestConnection());

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(ipInput);
        layout.addView(status);
        layout.addView(requestButton);

        setContentView(layout);
    }

    private void requestConnection() {
        String host = ipInput.getText().toString().trim();

        if (!host.matches(
                "\\d{1,3}(\\.\\d{1,3}){3}")) {
            status.setText("Введи правильный IP компьютера");
            return;
        }

        String[] parts = host.split("\\.");
        for (String part : parts) {
            int number = Integer.parseInt(part);
            if (number > 255) {
                status.setText("Неправильный IP компьютера");
                return;
            }
        }

        requestButton.setEnabled(false);
        status.setText("Отправляем запрос Windows...");

        executor.execute(() -> {
            try {
                String base = "http://" + host + ":8765";

                JSONObject request = sendPost(base + "/request");
                String requestId = request.getString("request_id");

                updateStatus("Запрос отправлен. Ожидаем владельца...");

                long deadline = System.currentTimeMillis() + 65000;

                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(1000);

                    JSONObject answer = getJson(
                            base + "/decision?id=" + requestId
                    );

                    String decision = answer.optString(
                            "decision", "unknown"
                    );

                    if ("allowed".equals(decision)) {
                        updateStatus(
                                "Разрешено! Согласие владельца получено."
                        );
                        return;
                    }

                    if ("denied".equals(decision)) {
                        updateStatus("Владелец отклонил запрос.");
                        return;
                    }

                    if ("expired".equals(decision)) {
                        updateStatus("Время ожидания истекло.");
                        return;
                    }

                    if ("unknown".equals(decision)) {
                        updateStatus("Запрос больше не действителен.");
                        return;
                    }
                }

                updateStatus("Нет ответа от Windows. Попробуй снова.");

            } catch (Exception e) {
                updateStatus(
                        "Ошибка связи. Проверь IP и подключение к Wi-Fi."
                );
            }
        });
    }

    private JSONObject sendPost(String address) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)
                    new URL(address).openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int code = connection.getResponseCode();

            if (code != 200) {
                throw new Exception("HTTP " + code);
            }

            return readJson(connection);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)
                    new URL(address).openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != 200) {
                throw new Exception("Ошибка ответа Windows");
            }

            return readJson(connection);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject readJson(HttpURLConnection connection)
            throws Exception {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream(), "UTF-8"
                )
        );

        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();

        return new JSONObject(result.toString());
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                status.setText(message);
                requestButton.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
