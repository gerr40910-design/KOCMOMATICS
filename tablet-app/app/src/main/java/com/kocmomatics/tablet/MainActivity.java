package com.kocmomatics.tablet;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

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

        status = new TextView(this);
        status.setText("Компьютер: не подключён");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(17);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 45, 0, 35);

        Button statusButton = new Button(this);
        statusButton.setText("Проверить состояние");
        statusButton.setOnClickListener(v ->
            status.setText("Приложение работает. Связь с ПК не настроена.")
        );

        Button connectButton = new Button(this);
        connectButton.setText("Подключиться к ПК");
        connectButton.setOnClickListener(v ->
            status.setText("Удалённое подключение пока не настроено.")
        );

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(status);
        layout.addView(statusButton);
        layout.addView(connectButton);

        setContentView(layout);
    }
}
