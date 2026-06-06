package com.hearttrace.mooddiary.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "current_theme";
    public static final String THEME_WARM = "warm";
    public static final String THEME_DARK = "dark";

    public static void applyTheme(String theme) {
        if (THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    public static void applyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_WARM);
        applyTheme(theme);
    }

    public static void saveTheme(Context context, String theme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, theme).apply();
        applyTheme(theme);
    }

    public static String getCurrentTheme(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_WARM);
    }

    public static boolean isDarkTheme(Context context) {
        return THEME_DARK.equals(getCurrentTheme(context));
    }
}
