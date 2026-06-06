package com.hearttrace.mooddiary.ui;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;
import java.util.Locale;

public class NotificationSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_DAILY = "daily_reminder";
    private static final String KEY_WEEKLY = "weekly_report";
    private static final String KEY_ENCOURAGE = "encourage";
    private static final String KEY_SOUND = "sound";
    private static final String KEY_TIME = "reminder_time";

    private SharedPreferences prefs;
    private Switch swDailyReminder;
    private Switch swWeeklyReport;
    private Switch swEncourage;
    private Switch swSound;
    private TextView tvReminderTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ImageView btnBack = findViewById(R.id.btn_back);
        swDailyReminder = findViewById(R.id.sw_daily_reminder);
        swWeeklyReport = findViewById(R.id.sw_weekly_report);
        swEncourage = findViewById(R.id.sw_encourage);
        swSound = findViewById(R.id.sw_sound);
        tvReminderTime = findViewById(R.id.tv_reminder_time);
        LinearLayout itemReminderTime = findViewById(R.id.item_reminder_time);

        btnBack.setOnClickListener(v -> finish());

        loadSettings();

        swDailyReminder.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_DAILY, isChecked).apply());
        swWeeklyReport.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_WEEKLY, isChecked).apply());
        swEncourage.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_ENCOURAGE, isChecked).apply());
        swSound.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_SOUND, isChecked).apply());

        itemReminderTime.setOnClickListener(v -> showTimePicker());
    }

    private void loadSettings() {
        swDailyReminder.setChecked(prefs.getBoolean(KEY_DAILY, true));
        swWeeklyReport.setChecked(prefs.getBoolean(KEY_WEEKLY, true));
        swEncourage.setChecked(prefs.getBoolean(KEY_ENCOURAGE, false));
        swSound.setChecked(prefs.getBoolean(KEY_SOUND, true));
        tvReminderTime.setText(prefs.getString(KEY_TIME, "20:00"));
    }

    private void showTimePicker() {
        String current = prefs.getString(KEY_TIME, "20:00");
        String[] parts = current.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        TimePickerDialog dialog = new TimePickerDialog(this,
                (view, hourOfDay, minute1) -> {
                    String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1);
                    tvReminderTime.setText(time);
                    prefs.edit().putString(KEY_TIME, time).apply();
                }, hour, minute, true);
        dialog.show();
    }
}
