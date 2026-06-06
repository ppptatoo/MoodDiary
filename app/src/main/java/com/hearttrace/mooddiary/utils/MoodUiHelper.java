package com.hearttrace.mooddiary.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.hearttrace.mooddiary.R;

/**
 * 心情展示：emoji、图标、背景色、标签色。
 */
public final class MoodUiHelper {

    private static final String[] LABELS = {"开心", "难过", "生气", "平静", "焦虑", "疲惫"};
    private static final String[] EMOJIS = {"🤩", "😢", "😠", "😌", "😥", "😫"};
    private static final int[] ICONS = {
            R.drawable.ic_happy,
            R.drawable.ic_sad,
            R.drawable.ic_angry,
            R.drawable.ic_calm,
            R.drawable.ic_anxious,
            R.drawable.ic_tired
    };
    private static final int[] MOOD_COLORS = {
            R.color.mood_happy,
            R.color.mood_sad,
            R.color.mood_angry,
            R.color.mood_calm,
            R.color.mood_anxious,
            R.color.mood_tired
    };
    private static final int[] ICON_BG_COLORS = {
            R.color.mood_icon_bg_happy,
            R.color.mood_icon_bg_sad,
            R.color.mood_icon_bg_angry,
            R.color.mood_icon_bg_calm,
            R.color.mood_icon_bg_anxious,
            R.color.mood_icon_bg_tired
    };

    private MoodUiHelper() {
    }

    public static String normalizeMood(String mood) {
        if (mood == null || mood.isEmpty()) {
            return "平静";
        }
        if ("伤心".equals(mood)) {
            return "难过";
        }
        for (String label : LABELS) {
            if (label.equals(mood)) {
                return mood;
            }
        }
        return "平静";
    }

    public static String emojiFor(String mood) {
        String m = normalizeMood(mood);
        for (int i = 0; i < LABELS.length; i++) {
            if (LABELS[i].equals(m)) {
                return EMOJIS[i];
            }
        }
        return "😐";
    }

    @DrawableRes
    public static int iconFor(String mood) {
        String m = normalizeMood(mood);
        for (int i = 0; i < LABELS.length; i++) {
            if (LABELS[i].equals(m)) {
                return ICONS[i];
            }
        }
        return ICONS[3];
    }

    public static int moodColor(Context context, String mood) {
        String m = normalizeMood(mood);
        for (int i = 0; i < LABELS.length; i++) {
            if (LABELS[i].equals(m)) {
                return ContextCompat.getColor(context, MOOD_COLORS[i]);
            }
        }
        return ContextCompat.getColor(context, R.color.mood_default);
    }

    public static int iconBgColor(Context context, String mood) {
        String m = normalizeMood(mood);
        for (int i = 0; i < LABELS.length; i++) {
            if (LABELS[i].equals(m)) {
                return ContextCompat.getColor(context, ICON_BG_COLORS[i]);
            }
        }
        return ContextCompat.getColor(context, R.color.mood_icon_bg_calm);
    }

    @NonNull
    public static GradientDrawable roundedSquareBg(Context context, @ColorRes int colorRes, float cornerDp) {
        float px = cornerDp * context.getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(px);
        gd.setColor(ContextCompat.getColor(context, colorRes));
        return gd;
    }

    @NonNull
    public static GradientDrawable roundedSquareBgColor(Context context, int colorInt, float cornerDp) {
        float px = cornerDp * context.getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(px);
        gd.setColor(colorInt);
        return gd;
    }
}
