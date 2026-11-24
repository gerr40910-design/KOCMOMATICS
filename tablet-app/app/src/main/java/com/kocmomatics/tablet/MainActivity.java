package com.kocmomatics.tablet;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.text.Editable;
import android.text.TextWatcher;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private TextView connectionStatus;
    private TextView minecraftStatus;
    private EditText ipInput;
    private Button requestButton;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ScheduledExecutorService statusExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> statusTask;

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

        connectionStatus = new TextView(this);
        connectionStatus.setText("Компьютер: не проверен");
        connectionStatus.setTextColor(Color.LTGRAY);
        connectionStatus.setTextSize(16);
        connectionStatus.setGravity(Gravity.CENTER);
        connectionStatus.setPadding(0, 20, 0, 10);

        minecraftStatus = new TextView(this);
        minecraftStatus.setText("Minecraft: ожидаем подключения");
        minecraftStatus.setTextColor(Color.rgb(180, 100, 255));
        minecraftStatus.setTextSize(18);
        minecraftStatus.setGravity(Gravity.CENTER);
        minecraftStatus.setPadding(0, 10, 0, 25);

        requestButton = new Button(this);
        requestButton.setText("Запросить подключение");
        requestButton.setOnClickListener(v -> requestConnection());

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(ipInput);
        layout.addView(connectionStatus);
        layout.addView(minecraftStatus);
        layout.addView(requestButton);

        setContentView(layout);

        ipInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                startStatusPolling();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private boolean isValidIp(String host) {
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }

        String[] parts = host.split("\\.");

        for (String part : parts) {
            int number = Integer.parseInt(part);
            if (number > 255) {
                return false;
            }
        }

        return true;
    }

    private void startStatusPolling() {
        String host = ipInput.getText().toString().trim();

        if (statusTask != null) {
            statusTask.cancel(false);
            statusTask = null;
        }

        if (!isValidIp(host)) {
            minecraftStatus.setText(
                    "Minecraft: введи IP компьютера"
            );
            connectionStatus.setText(
                    "Компьютер: не проверен"
            );
            return;
        }

        statusTask = statusExecutor.scheduleWithFixedDelay(
                () -> checkMinecraftStatus(host),
                0,
                3,
                TimeUnit.SECONDS
        );
    }

    private void checkMinecraftStatus(String host) {
        try {
            JSONObject data = getJson(
                    "http://" + host + ":8765/minecraft-status"
            );

            String status = data.optString("minecraft", "unknown");

            if ("in_world".equals(status)) {
                updateMinecraftStatus("Minecraft: В мире");
            } else if ("not_in_world".equals(status)) {
                updateMinecraftStatus("Minecraft: Не в мире");
            } else {
                updateMinecraftStatus(
                        "Minecraft: статус неизвестен"
                );
            }

            updateConnectionStatus("Компьютер: подключён");

        } catch (Exception e) {
            updateMinecraftStatus("Minecraft: нет связи");
            updateConnectionStatus("Компьютер: недоступен");
        }
    }

    private void requestConnection() {
        String host = ipInput.getText().toString().trim();

        if (!isValidIp(host)) {
            connectionStatus.setText(
                    "Введи правильный IP компьютера"
            );
            return;
        }

        requestButton.setEnabled(false);
        connectionStatus.setText(
                "Отправляем запрос Windows..."
        );

        executor.execute(() -> {
            try {
                String base = "http://" + host + ":8765";

                JSONObject request = sendPost(base + "/request");
                String requestId = request.getString("request_id");

                updateConnectionStatus(
                        "Запрос отправлен. Ожидаем владельца..."
                );

                long deadline =
                        System.currentTimeMillis() + 65000;

                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(1000);

                    JSONObject answer = getJson(
                            base + "/decision?id=" + requestId
                    );

                    String decision = answer.optString(
                            "decision", "unknown"
                    );

                    if ("allowed".equals(decision)) {
                        finishRequest(
                                "Владелец разрешил запрос."
                        );
                        return;
                    }

                    if ("denied".equals(decision)) {
                        finishRequest(
                                "Владелец отклонил запрос."
                        );
                        return;
                    }

                    if ("expired".equals(decision)) {
                        finishRequest(
                                "Время ожидания истекло."
                        );
                        return;
                    }

                    if ("unknown".equals(decision)) {
                        finishRequest(
                                "Запрос больше не действителен."
                        );
                        return;
                    }
                }

                finishRequest(
                        "Нет ответа от Windows. Попробуй снова."
                );

            } catch (Exception e) {
                finishRequest(
                        "Ошибка связи. Проверь IP и Wi-Fi."
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

            if (connection.getResponseCode() != 200) {
                throw new Exception("HTTP error");
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
                throw new Exception("HTTP error");
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

    private void updateMinecraftStatus(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                minecraftStatus.setText(message);
            }
        });
    }

    private void updateConnectionStatus(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                connectionStatus.setText(message);
            }
        });
    }

    private void finishRequest(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                connectionStatus.setText(message);
                requestButton.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (statusTask != null) {
            statusTask.cancel(true);
        }

        statusExecutor.shutdownNow();
        executor.shutdownNow();

        super.onDestroy();
    }
}
