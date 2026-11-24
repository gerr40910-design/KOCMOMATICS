package com.kocmomatics.tablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.Toast;

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

    private TextView statusText;
    private TextView minecraftText;
    private Button requestButton;

    private SharedPreferences preferences;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final ScheduledExecutorService statusExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> statusTask;

    private String getHost() {
        return preferences.getString("computer_ip", "");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(
                "kocmomatics_settings", MODE_PRIVATE
        );

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

        requestButton = new Button(this);
        requestButton.setText("ПОДКЛЮЧИТЬСЯ");
        requestButton.setOnClickListener(v -> requestConnection());

        statusText = new TextView(this);
        statusText.setText("●");
        statusText.setTextColor(Color.LTGRAY);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 20, 0, 5);

        minecraftText = new TextView(this);
        minecraftText.setText("");
        minecraftText.setTextColor(Color.rgb(180, 100, 255));
        minecraftText.setTextSize(14);
        minecraftText.setGravity(Gravity.CENTER);

        Button settingsButton = new Button(this);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(20);
        settingsButton.setOnClickListener(v -> openSettings());

        layout.addView(title);
        layout.addView(requestButton);
        layout.addView(statusText);
        layout.addView(minecraftText);
        layout.addView(settingsButton);

        setContentView(layout);

        if (!getHost().isEmpty()) {
            startStatusPolling();
        }
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

    private void openSettings() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("IP компьютера");
        input.setText(getHost());

        new AlertDialog.Builder(this)
                .setTitle("Настройки подключения")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String host = input.getText().toString().trim();

                    if (!isValidIp(host)) {
                        Toast.makeText(
                                this,
                                "Неверный IP-адрес",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    preferences.edit()
                            .putString("computer_ip", host)
                            .apply();

                    startStatusPolling();

                    Toast.makeText(
                            this,
                            "Адрес сохранён",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void startStatusPolling() {
        String host = getHost();

        if (statusTask != null) {
            statusTask.cancel(false);
            statusTask = null;
        }

        if (!isValidIp(host)) {
            updateConnectionStatus("●");
            updateMinecraftStatus("");
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
                updateMinecraftStatus("Minecraft: неизвестно");
            }

            updateConnectionStatus("● Подключено");

        } catch (Exception e) {
            updateConnectionStatus("● Нет связи");
            updateMinecraftStatus("");
        }
    }

    private void requestConnection() {
        String host = getHost();

        if (!isValidIp(host)) {
            openSettings();
            return;
        }

        requestButton.setEnabled(false);
        updateConnectionStatus("Ожидание ответа владельца");

        executor.execute(() -> {
            try {
                String base = "http://" + host + ":8765";

                JSONObject request = sendPost(base + "/request");
                String requestId = request.getString("request_id");

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
                        finishRequest("Владелец разрешил запрос");
                        return;
                    }

                    if ("denied".equals(decision)) {
                        finishRequest("Владелец отклонил запрос");
                        return;
                    }

                    if ("expired".equals(decision)) {
                        finishRequest("Время ожидания истекло");
                        return;
                    }

                    if ("unknown".equals(decision)) {
                        finishRequest("Запрос недействителен");
                        return;
                    }
                }

                finishRequest("Нет ответа от компьютера");

            } catch (Exception e) {
                finishRequest("Ошибка связи");
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

    private void updateConnectionStatus(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                statusText.setText(message);
            }
        });
    }

    private void updateMinecraftStatus(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                minecraftText.setText(message);
            }
        });
    }

    private void finishRequest(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                statusText.setText(message);
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
