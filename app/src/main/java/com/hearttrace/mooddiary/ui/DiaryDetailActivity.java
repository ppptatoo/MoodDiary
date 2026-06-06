package com.hearttrace.mooddiary.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import com.hearttrace.mooddiary.supabase.SupabaseStorageClient;
import com.hearttrace.mooddiary.supabase.SyncManager;
import com.hearttrace.mooddiary.utils.MoodUiHelper;
import com.hearttrace.mooddiary.utils.AiIllustrationHelper;
import com.hearttrace.mooddiary.utils.DoubaoApiClient;
import com.hearttrace.mooddiary.utils.PrefManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiaryDetailActivity extends AppCompatActivity {

    private static final String[] WEEKDAY_NAMES =
            {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};

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

    private long diaryId;
    private AppDatabase db;
    private ExecutorService executor;
    private MoodEntry currentEntry;
    private SyncManager syncManager;
    private SupabaseStorageClient storageClient;

    private FrameLayout flMoodBadge;
    private ImageView ivDetailMoodIcon;
    private TextView tvDetailTitle;
    private TextView tvDetailSubtitle;
    private FrameLayout flImageContainer;
    private ImageView ivDetailImg;
    private TextView tvChangeImageHint;
    private TextView tvAddImageEntry;
    private LinearLayout layoutViewContent;
    private TextView tvDetailContent;
    private EditText etDetailContent;
    private TextView tvEditMoodLabel;
    private GridLayout gridEditMoods;
    private TextView tvCreatedTime;
    private TextView btnBack;
    private TextView btnEdit;
    private TextView btnDeleteImage;
    private TextView btnDeleteDiary;

    private boolean isEditMode = false;
    private String selectedMood;
    private String selectedImagePath;
    private LinearLayout selectedMoodCard;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_detail);

        // 在 super.onCreate() 之后注册，避免 IllegalStateException
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri == null) return;
                    selectedImagePath = uri.toString();
                    showImage(resolveDisplayImagePath());
                });

        diaryId = getIntent().getLongExtra("DIARY_ID", -1);
        if (diaryId == -1) {
            finish();
            return;
        }

        bindViews();
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();

        try {
            initSyncManager();
        } catch (Exception e) {
            Log.e("DiaryDetail", "initSyncManager failed", e);
            syncManager = null;
        }

        btnBack.setOnClickListener(v -> {
            if (isEditMode) {
                exitEditMode();
            } else {
                finish();
            }
        });
        btnEdit.setOnClickListener(v -> {
            if (currentEntry == null) {
                return;
            }
            if (isEditMode) {
                saveDiary();
            } else {
                enterEditMode();
            }
        });

        btnDeleteImage.setOnClickListener(v -> {
            if (!isEditMode) return;
            selectedImagePath = null;                 // 只清空用户图片引用
            showImage(resolveDisplayImagePath());     // 会回退显示 AI 缓存图（如果有）
            Toast.makeText(this, "已移除图片（保存后生效）", Toast.LENGTH_SHORT).show();
        });

        btnDeleteDiary.setOnClickListener(v -> {
            if (currentEntry == null) return;
            new AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除这条日记吗？删除后无法恢复。")
                    .setPositiveButton("删除", (dialog, which) -> deleteDiary())
                    .setNegativeButton("取消", null)
                    .show();
        });

        flImageContainer.setOnClickListener(v -> {
            if (isEditMode) {
                pickImage();
            }
        });
        tvAddImageEntry.setOnClickListener(v -> {
            if (isEditMode) {
                pickImage();
            }
        });

        try {
            setupMoodGrid();
        } catch (Exception e) {
            Log.e("DiaryDetail", "setupMoodGrid failed", e);
            Toast.makeText(this, "界面初始化失败，请重试", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadDiaryData();
    }

    private void bindViews() {
        flMoodBadge = findViewById(R.id.fl_mood_badge);
        ivDetailMoodIcon = findViewById(R.id.iv_detail_mood_icon);
        tvDetailTitle = findViewById(R.id.tv_detail_title);
        tvDetailSubtitle = findViewById(R.id.tv_detail_subtitle);
        flImageContainer = findViewById(R.id.fl_image_container);
        ivDetailImg = findViewById(R.id.iv_detail_img);
        tvChangeImageHint = findViewById(R.id.tv_change_image_hint);
        tvAddImageEntry = findViewById(R.id.tv_add_image_entry);
        layoutViewContent = findViewById(R.id.layout_view_content);
        tvDetailContent = findViewById(R.id.tv_detail_content);
        etDetailContent = findViewById(R.id.et_detail_content);
        tvEditMoodLabel = findViewById(R.id.tv_edit_mood_label);
        gridEditMoods = findViewById(R.id.grid_edit_moods);
        tvCreatedTime = findViewById(R.id.tv_created_time);
        btnBack = findViewById(R.id.btn_back);
        btnEdit = findViewById(R.id.btn_edit);
        btnDeleteImage = findViewById(R.id.btn_delete_image);
        btnDeleteDiary = findViewById(R.id.btn_delete_diary);
    }

    private void setupMoodGrid() {
        if (gridEditMoods == null) return;
        gridEditMoods.removeAllViews();
        for (MoodItem item : MOODS) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_mood_card, gridEditMoods, false);
            ViewGroup.LayoutParams rawLp = card.getLayoutParams();
            GridLayout.LayoutParams lp;
            if (rawLp instanceof GridLayout.LayoutParams) {
                lp = (GridLayout.LayoutParams) rawLp;
            } else {
                lp = new GridLayout.LayoutParams();
            }
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            card.setLayoutParams(lp);

            ImageView iv = card.findViewById(R.id.iv_mood_icon);
            TextView tv = card.findViewById(R.id.tv_mood_label);
            if (iv != null) iv.setImageResource(item.iconRes);
            if (tv != null) tv.setText(item.label);

            final String moodLabel = item.label;
            card.setOnClickListener(v -> selectMood((LinearLayout) card, moodLabel));
            gridEditMoods.addView(card);
        }
    }

    private void selectMood(LinearLayout card, String mood) {
        try {
            if (selectedMoodCard != null) {
                selectedMoodCard.setSelected(false);
            }
            selectedMoodCard = card;
            if (selectedMoodCard != null) selectedMoodCard.setSelected(true);
            selectedMood = mood;
            applyMoodHeader(mood);
        } catch (Exception e) {
            Log.e("DiaryDetail", "selectMood error", e);
        }
    }

    private void loadDiaryData() {
        executor.execute(() -> {
            try {
                MoodEntry entry = db.moodEntryDao().getMoodEntryById(diaryId);
                if (entry == null) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(DiaryDetailActivity.this, "日记不存在", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                    return;
                }
                currentEntry = entry;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        try {
                            bindDiary(entry);
                        } catch (Exception e) {
                            Log.e("DiaryDetail", "bindDiary failed", e);
                            Toast.makeText(DiaryDetailActivity.this, "加载数据失败", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("DiaryDetail", "loadDiaryData failed", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(DiaryDetailActivity.this, "加载日记失败", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        });
    }

    private void bindDiary(MoodEntry entry) {
        if (entry == null) return;
        try {
            selectedMood = MoodUiHelper.normalizeMood(entry.getUserEmotionLabel());
            selectedImagePath = entry.getImagePath();

            String title = formatTitle(entry.getTimestamp());
            String subtitle = selectedMood;
            String content = entry.getText() == null ? "" : entry.getText();
            String timeInfo = buildCreateUpdateText(entry);

            if (tvDetailTitle != null) tvDetailTitle.setText(title);
            if (tvDetailSubtitle != null) tvDetailSubtitle.setText(subtitle);
            if (tvDetailContent != null) tvDetailContent.setText(content);
            if (etDetailContent != null) etDetailContent.setText(content);
            if (tvCreatedTime != null) tvCreatedTime.setText(timeInfo);

            applyMoodHeader(selectedMood);
            applySelectedMoodCard();
            showImage(resolveDisplayImagePath());
            updateModeUi();
        } catch (Exception e) {
            Log.e("DiaryDetail", "bindDiary error", e);
            throw e;
        }
    }

    private String resolveDisplayImagePath() {
        if (selectedImagePath != null && !selectedImagePath.trim().isEmpty()) {
        return selectedImagePath;
        }
        if (currentEntry == null) {
            return null;
        }
        return AiIllustrationHelper.getCachedPath(this, currentEntry.getId());
    }

    

    private void applyMoodHeader(String mood) {
        try {
            String normalizedMood = MoodUiHelper.normalizeMood(mood);
            int bgColor = MoodUiHelper.iconBgColor(this, normalizedMood);
            if (flMoodBadge != null)
                flMoodBadge.setBackground(MoodUiHelper.roundedSquareBgColor(this, bgColor, 18f));
            if (ivDetailMoodIcon != null)
                ivDetailMoodIcon.setImageResource(MoodUiHelper.iconFor(normalizedMood));
            if (tvDetailSubtitle != null)
                tvDetailSubtitle.setText(normalizedMood);
        } catch (Exception e) {
            Log.e("DiaryDetail", "applyMoodHeader error", e);
        }
    }

    private void applySelectedMoodCard() {
        if (gridEditMoods == null) return;
        try {
            selectedMoodCard = null;
            for (int i = 0; i < gridEditMoods.getChildCount(); i++) {
                View child = gridEditMoods.getChildAt(i);
                if (!(child instanceof LinearLayout)) continue;
                LinearLayout card = (LinearLayout) child;
                TextView tv = card.findViewById(R.id.tv_mood_label);
                if (tv == null) continue;
                boolean selected = tv.getText().toString().equals(selectedMood);
                card.setSelected(selected);
                if (selected) selectedMoodCard = card;
            }
        } catch (Exception e) {
            Log.e("DiaryDetail", "applySelectedMoodCard error", e);
        }
    }

    private void updateModeUi() {
        try {
            if (layoutViewContent != null)
                layoutViewContent.setVisibility(isEditMode ? View.GONE : View.VISIBLE);
            if (etDetailContent != null)
                etDetailContent.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            if (tvEditMoodLabel != null)
                tvEditMoodLabel.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            if (gridEditMoods != null)
                gridEditMoods.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            boolean imageVisible = flImageContainer != null && flImageContainer.getVisibility() == View.VISIBLE;
            if (tvChangeImageHint != null)
                tvChangeImageHint.setVisibility(isEditMode && imageVisible ? View.VISIBLE : View.GONE);
            if (tvAddImageEntry != null)
                tvAddImageEntry.setVisibility(isEditMode && !imageVisible ? View.VISIBLE : View.GONE);
            if (btnBack != null) btnBack.setText(isEditMode ? "取消" : "返回");
            if (btnEdit != null) btnEdit.setText(isEditMode ? "保存" : "编辑");
            if (btnDeleteImage != null)
                btnDeleteImage.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.e("DiaryDetail", "updateModeUi error", e);
        }
    }

    private void enterEditMode() {
        isEditMode = true;
        if (etDetailContent != null) etDetailContent.requestFocus();
        updateModeUi();
    }

    private void exitEditMode() {
        isEditMode = false;
        if (currentEntry != null) {
            bindDiary(currentEntry);
        } else {
            updateModeUi();
        }
    }

    private void deleteDiary() {
        if (currentEntry == null) return;
        if (btnDeleteDiary != null) btnDeleteDiary.setEnabled(false);
        executor.execute(() -> {
            try {
                if (syncManager != null) {
                    syncManager.deleteMoodEntry(currentEntry);
                } else {
                    db.moodEntryDao().deleteMoodEntry(currentEntry.getId());
                }
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(DiaryDetailActivity.this, "日记已删除", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (Exception e) {
                Log.e("DiaryDetail", "deleteDiary error", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && btnDeleteDiary != null) {
                        btnDeleteDiary.setEnabled(true);
                        Toast.makeText(DiaryDetailActivity.this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void saveDiary() {
        if (currentEntry == null) return;
        String text = etDetailContent != null ? etDetailContent.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(this, "日记内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedMood == null || selectedMood.isEmpty()) {
            Toast.makeText(this, "请选择心情", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnEdit != null) btnEdit.setEnabled(false);
        final MoodEntry entryToSave = currentEntry;
        executor.execute(() -> {
            try {
                // 如果更换了图片且不是网络URL，上传到 Storage
                String imageUrl = selectedImagePath;
                if (selectedImagePath != null && !selectedImagePath.isEmpty()
                        && storageClient != null
                        && !selectedImagePath.startsWith("http")) {
                    try {
                        File imageFile;
                        if (selectedImagePath.startsWith("content://")) {
                            // 从 Content URI 复制到临时文件
                            java.io.InputStream is = getContentResolver().openInputStream(android.net.Uri.parse(selectedImagePath));
                            if (is != null) {
                                File tempFile = new File(getCacheDir(), "temp_edit_" + System.currentTimeMillis() + ".jpg");
                                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = is.read(buf)) != -1) {
                                        fos.write(buf, 0, len);
                                    }
                                }
                                is.close();
                                imageFile = tempFile;
                            } else {
                                imageFile = null;
                            }
                        } else {
                            imageFile = selectedImagePath.startsWith("file://")
                                    ? new File(selectedImagePath.substring(7))
                                    : new File(selectedImagePath);
                        }
                        if (imageFile != null && imageFile.exists()) {
                            String remotePath = "user_photos/user_" + entryToSave.getUserId() + "/entry_" + entryToSave.getId() + "_edit_" + System.currentTimeMillis() + ".jpg";
                            imageUrl = storageClient.uploadImage(imageFile, remotePath);
                        }
                    } catch (Exception e) {
                        Log.w("DiaryDetail", "上传配图到 Storage 失败，使用原始路径", e);
                    }
                }

                entryToSave.setText(text);
                entryToSave.setUserEmotionLabel(selectedMood);
                entryToSave.setImagePath(imageUrl);
                entryToSave.setUpdateTime(System.currentTimeMillis());

                if (syncManager != null) {
                    syncManager.updateMoodEntry(entryToSave);
                } else {
                    db.moodEntryDao().updateMoodEntry(entryToSave);
                }

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (btnEdit != null) btnEdit.setEnabled(true);
                        isEditMode = false;
                        bindDiary(entryToSave);
                        Toast.makeText(DiaryDetailActivity.this, "修改成功", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DiaryDetail", "saveDiary error", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (btnEdit != null) btnEdit.setEnabled(true);
                        Toast.makeText(DiaryDetailActivity.this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void pickImage() {
        if (imagePickerLauncher != null) {
            try {
                imagePickerLauncher.launch("image/*");
            } catch (Exception e) {
                Log.e("DiaryDetail", "pickImage error", e);
                Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showImage(String path) {
        if (flImageContainer == null) return;
        if (path == null || path.trim().isEmpty()) {
            flImageContainer.setVisibility(View.GONE);
            if (ivDetailImg != null) ivDetailImg.setImageDrawable(null);
            updateModeUi();
            return;
        }

        flImageContainer.setVisibility(View.VISIBLE);
        if (tvAddImageEntry != null) tvAddImageEntry.setVisibility(View.GONE);
        if (ivDetailImg != null && !isDestroyed()) {
            Glide.with(this)
                    .load(toImageModel(path))
                    .placeholder(R.drawable.bg_ai_image_placeholder)
                    .centerCrop()
                    .into(ivDetailImg);
        }
        updateModeUi();
    }

    private Object toImageModel(String path) {
        if (path.startsWith("content://") || path.startsWith("file://")
                || path.startsWith("http://") || path.startsWith("https://")) {
            return Uri.parse(path);
        }
        File file = new File(path);
        return file.exists() ? file : Uri.parse(path);
    }

    private String formatTitle(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int weekday = cal.get(Calendar.DAY_OF_WEEK);
        return String.format(Locale.getDefault(), "%d月%d日 %s",
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                WEEKDAY_NAMES[weekday]);
    }

    private String buildCreateUpdateText(MoodEntry entry) {
        String create = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                .format(entry.getTimestamp());
        String update = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                .format(entry.getUpdateTime());
        if (entry.getTimestamp() == entry.getUpdateTime()) {
            return "创建于 " + create;
        }
        return "创建于 " + create + "  ·  更新于 " + update;
    }

    /** 初始化 Supabase 同步管理器 */
    private void initSyncManager() {
        if (SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) return;
        PrefManager pm = new PrefManager(this);
        String uuid = pm.getSupabaseUserUuid();
        String token = pm.getSupabaseToken();
        if (uuid.isEmpty() || token.isEmpty()) return;
        SupabaseDataClient dataClient = new SupabaseDataClient(
                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, token);
        syncManager = new SyncManager(db, uuid, dataClient);
        storageClient = new SupabaseStorageClient(
                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, token);
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}
