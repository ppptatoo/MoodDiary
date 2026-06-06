package com.hearttrace.mooddiary.ui;

import android.app.Dialog;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseStorageClient;
import com.hearttrace.mooddiary.supabase.SyncManager;

import java.io.File;
import java.util.concurrent.ExecutorService;

/**
 * 首页「记录今日心情」弹窗：6 种心情 + 日记正文 + 配图。
 */
public class RecordMoodDialog {

    private static final class MoodItem {
        final String label;
        final int iconRes;

        MoodItem(String label, int iconRes) {
            this.label = label;
            this.iconRes = iconRes;
        }
    }

    private static final MoodItem[] MOODS = {
            new MoodItem("开心", R.drawable.ic_happy),
            new MoodItem("难过", R.drawable.ic_sad),
            new MoodItem("生气", R.drawable.ic_angry),
            new MoodItem("平静", R.drawable.ic_calm),
            new MoodItem("焦虑", R.drawable.ic_anxious),
            new MoodItem("疲惫", R.drawable.ic_tired),
    };

    private final AppCompatActivity activity;
    private final long userId;
    private final AppDatabase db;
    private final ExecutorService executor;
    private final SyncManager syncManager;
    private final SupabaseStorageClient storageClient;
    private final Runnable onSaved;

    private final Dialog dialog;
    private final EditText etContent;
    private final ImageView ivPreview;
    private final TextView tvAddPlus;
    private final FrameLayout btnAddImage;

    private String selectedEmotion = "";
    private LinearLayout selectedCard;
    private String selectedImagePath;

    // 从日历选中的日期，-1 表示未选中（使用今日时间）
    private int selectedYear = -1;
    private int selectedMonth = -1;
    private int selectedDay = -1;

    public RecordMoodDialog(
            AppCompatActivity activity,
            long userId,
            AppDatabase db,
            ExecutorService executor,
            SyncManager syncManager,
            SupabaseStorageClient storageClient,
            Runnable onSaved) {
        this.activity = activity;
        this.userId = userId;
        this.db = db;
        this.executor = executor;
        this.syncManager = syncManager;
        this.storageClient = storageClient;
        this.onSaved = onSaved;

        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_record_mood, null);
        etContent = root.findViewById(R.id.et_diary_content);
        ivPreview = root.findViewById(R.id.iv_image_preview);
        tvAddPlus = root.findViewById(R.id.tv_add_image_plus);
        btnAddImage = root.findViewById(R.id.btn_add_image);
        GridLayout gridMoods = root.findViewById(R.id.grid_moods);
        TextView btnCancel = root.findViewById(R.id.btn_cancel);
        TextView btnSave = root.findViewById(R.id.btn_save);

        setupMoodGrid(gridMoods);

        dialog = new Dialog(activity, R.style.Theme_MoodDiary_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCancelable(true);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> save());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            window.setDimAmount(0.45f);
            window.setAttributes(lp);
        }
    }

    private void setupMoodGrid(GridLayout grid) {
        grid.removeAllViews();
        for (MoodItem item : MOODS) {
            View card = LayoutInflater.from(activity).inflate(R.layout.item_mood_card, grid, false);
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) card.getLayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            card.setLayoutParams(lp);

            ImageView iv = card.findViewById(R.id.iv_mood_icon);
            TextView tv = card.findViewById(R.id.tv_mood_label);
            iv.setImageResource(item.iconRes);
            tv.setText(item.label);

            card.setOnClickListener(v -> selectMood((LinearLayout) card, item.label));
            grid.addView(card);
        }
    }

    private void selectMood(LinearLayout card, String emotion) {
        if (selectedCard != null) {
            selectedCard.setSelected(false);
        }
        selectedCard = card;
        selectedCard.setSelected(true);
        selectedEmotion = emotion;
    }

    public void setOnPickImageClickListener(View.OnClickListener listener) {
        btnAddImage.setOnClickListener(listener);
    }

    public void setSelectedImage(Uri uri, String path) {
        selectedImagePath = path;
        ivPreview.setVisibility(View.VISIBLE);
        tvAddPlus.setVisibility(View.GONE);
        ivPreview.setImageURI(uri);
    }

    /** 设置弹窗要记录的日期（从日历选中），传入 -1 表示使用今天 */
    public void setSelectedDate(int year, int month, int day) {
        this.selectedYear = year;
        this.selectedMonth = month;
        this.selectedDay = day;
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void save() {
        String text = etContent.getText().toString().trim();
        if (selectedEmotion.isEmpty()) {
            Toast.makeText(activity, "请选择心情", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.isEmpty()) {
            Toast.makeText(activity, "日记内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            // 如果从日历选中了日期则使用选中日期 + 当前实际时分秒
            long timestamp;
            if (selectedYear > 0 && selectedMonth >= 0 && selectedDay > 0) {
                java.util.GregorianCalendar gCal = new java.util.GregorianCalendar();
                java.util.GregorianCalendar now = new java.util.GregorianCalendar();
                gCal.set(selectedYear, selectedMonth, selectedDay,
                        now.get(java.util.Calendar.HOUR_OF_DAY),
                        now.get(java.util.Calendar.MINUTE),
                        now.get(java.util.Calendar.SECOND));
                gCal.set(java.util.Calendar.MILLISECOND, now.get(java.util.Calendar.MILLISECOND));
                timestamp = gCal.getTimeInMillis();
            } else {
                timestamp = System.currentTimeMillis();
            }

            // ===== 上传用户选择的配图到 Supabase Storage =====
            String imageUrl = selectedImagePath;
            if (selectedImagePath != null && !selectedImagePath.isEmpty()
                    && storageClient != null
                    && !selectedImagePath.startsWith("http")) {
                try {
                    File imageFile = selectedImagePath.startsWith("file://")
                            ? new File(selectedImagePath.substring(7))
                            : new File(selectedImagePath);
                    String remotePath = "user_photos/user_" + userId + "/entry_" + timestamp + ".jpg";
                    imageUrl = storageClient.uploadImage(imageFile, remotePath);
                } catch (Exception e) {
                    android.util.Log.w("RecordMood", "上传配图到 Storage 失败，使用本地路径", e);
                }
            }

            MoodEntry entry = new MoodEntry(userId, timestamp, text);
            entry.setUserEmotionLabel(selectedEmotion);
            entry.setImagePath(imageUrl);

            // 使用同步管理器（云端+本地）
            if (syncManager != null) {
                syncManager.createMoodEntry(entry);
            } else {
                db.moodEntryDao().insertMoodEntry(entry);
            }

            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "保存成功", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (onSaved != null) {
                    onSaved.run();
                }
            });
        });
    }
}
