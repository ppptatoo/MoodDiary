package com.hearttrace.mooddiary.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import com.hearttrace.mooddiary.supabase.SupabaseStorageClient;
import com.hearttrace.mooddiary.supabase.SyncManager;
import com.hearttrace.mooddiary.utils.AiHealingHelper;
import com.hearttrace.mooddiary.utils.DoubaoApiClient;
import com.hearttrace.mooddiary.utils.PrefManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private static final String[] WEEK_LABELS = {"日", "一", "二", "三", "四", "五", "六"};
    private static final String[] WEEKDAY_NAMES =
            {"", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private long userId;
    private AppDatabase db;
    private ExecutorService executor;
    private final DoubaoApiClient apiClient = new DoubaoApiClient();
    private SyncManager syncManager;
    private SupabaseStorageClient storageClient;
    private boolean isFirstSync = true;

    private TextView tvMonthTitle;
    private GridLayout gridWeekHeader;
    private GridLayout gridCalendar;
    private LinearLayout cardDayPreview;
    private TextView tvPreviewDate;
    private TextView tvPreviewMood;
    private TextView tvPreviewContent;

    private int displayYear;
    private int displayMonth;
    private int selectedDay = -1;

    private List<MoodEntry> allEntries = new ArrayList<>();
    private final Map<Integer, MoodEntry> dayEntryMap = new HashMap<>();

    private static final int PERMISSION_ALBUM = 201;
    private RecordMoodDialog recordMoodDialog;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        userId = getIntent().getLongExtra("USER_ID", -1);
        if (userId <= 0) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        initSyncManager();

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && recordMoodDialog != null) {
                        String path = getRealPathFromURI(uri);
                        recordMoodDialog.setSelectedImage(uri, path);
                    }
                });

        bindViews();
        initCalendarToToday();
        setupWeekHeader();
        setupListeners();
        setupBottomNav();
        loadEntriesAndRender();
    }

    private void bindViews() {
        tvMonthTitle = findViewById(R.id.tv_month_title);
        gridWeekHeader = findViewById(R.id.grid_week_header);
        gridCalendar = findViewById(R.id.grid_calendar);
        cardDayPreview = findViewById(R.id.card_day_preview);
        tvPreviewDate = findViewById(R.id.tv_preview_date);
        tvPreviewMood = findViewById(R.id.tv_preview_mood);
        tvPreviewContent = findViewById(R.id.tv_preview_content);
    }

    private void initCalendarToToday() {
        Calendar today = Calendar.getInstance();
        displayYear = today.get(Calendar.YEAR);
        displayMonth = today.get(Calendar.MONTH);
        selectedDay = today.get(Calendar.DAY_OF_MONTH);
    }

    private void setupWeekHeader() {
        gridWeekHeader.removeAllViews();
        for (String label : WEEK_LABELS) {
            TextView tv = new TextView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setGravity(Gravity.CENTER);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setText(label);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tv.setTextSize(13f);
            gridWeekHeader.addView(tv);
        }
    }

    private void setupListeners() {
        findViewById(R.id.btn_record_mood).setOnClickListener(v -> showRecordMoodDialog());

        findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            displayMonth--;
            if (displayMonth < 0) {
                displayMonth = 11;
                displayYear--;
            }
            selectedDay = -1;
            cardDayPreview.setVisibility(View.GONE);
            loadEntriesAndRender();
        });

        findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            displayMonth++;
            if (displayMonth > 11) {
                displayMonth = 0;
                displayYear++;
            }
            selectedDay = -1;
            cardDayPreview.setVisibility(View.GONE);
            loadEntriesAndRender();
        });

        cardDayPreview.setOnClickListener(v -> {
            MoodEntry entry = dayEntryMap.get(selectedDay);
            if (entry != null) {
                showDiaryDetailDialog(entry);
            }
        });
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_calendar);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calendar) {
                return true;
            }
            Intent intent = null;
            if (id == R.id.nav_statistics) {
                intent = new Intent(this, StatisticsActivity.class);
            } else if (id == R.id.nav_history) {
                intent = new Intent(this, DiaryListActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }
            if (intent != null) {
                intent.putExtra("USER_ID", userId);
                startActivity(intent);
            }
            bottomNav.setSelectedItemId(R.id.nav_calendar);
            return false;
        });
    }

    /** 初始化 Supabase 同步管理器 */
    private void initSyncManager() {
        if (SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
            syncManager = null; // 未配置 Supabase
            storageClient = null;
            return;
        }
        PrefManager pm = new PrefManager(this);
        String uuid = pm.getSupabaseUserUuid();
        String token = pm.getSupabaseToken();
        if (uuid.isEmpty() || token.isEmpty()) {
            syncManager = null;
            storageClient = null;
            return;
        }
        SupabaseDataClient dataClient = new SupabaseDataClient(
                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, token);
        syncManager = new SyncManager(db, uuid, dataClient);
        storageClient = new SupabaseStorageClient(
                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, token);
    }

    private void loadEntriesAndRender() {
        executor.execute(() -> {
            // ===== 首次启动：从云端恢复数据 + 推送未同步的日记 =====
            // 解决 AS 重装 APK 后 Room 数据库被清除、日记丢失的问题
            if (isFirstSync && syncManager != null) {
                isFirstSync = false;
                try {
                    // 先推送本地尚未同步到云端的日记（防止重装前创建的日记丢失）
                    syncManager.pushPendingEntries(userId);
                    // 再从云端拉取全部日记恢复到本地
                    syncManager.pullFromCloud(userId);
                } catch (Exception e) {
                    android.util.Log.e("HomeActivity", "首次同步失败", e);
                }
            }

            allEntries = db.moodEntryDao().getMoodEntriesByUserId(userId);
            runOnUiThread(this::renderCalendar);
        });
    }

    private void renderCalendar() {
        tvMonthTitle.setText(String.format(Locale.getDefault(), "%d年%d月", displayYear, displayMonth + 1));
        buildDayEntryMap();
        gridCalendar.removeAllViews();

        Calendar cal = Calendar.getInstance();
        cal.set(displayYear, displayMonth, 1, 0, 0, 0);
        int firstWeekday = cal.get(Calendar.DAY_OF_WEEK);
        int offset = firstWeekday - Calendar.SUNDAY;
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < offset; i++) {
            addEmptyCell();
        }

        for (int day = 1; day <= maxDay; day++) {
            View cell = LayoutInflater.from(this).inflate(R.layout.item_calendar_day, gridCalendar, false);
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) cell.getLayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cell.setLayoutParams(lp);

            LinearLayout container = cell.findViewById(R.id.day_container);
            TextView tvDay = cell.findViewById(R.id.tv_day_number);
            View moodDot = cell.findViewById(R.id.view_mood_dot);

            tvDay.setText(String.valueOf(day));

            MoodEntry entry = dayEntryMap.get(day);
            if (entry != null) {
                moodDot.setVisibility(View.VISIBLE);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(getMoodColor(entry.getUserEmotionLabel()));
                moodDot.setBackground(dot);
            } else {
                moodDot.setVisibility(View.GONE);
            }

            boolean isSelected = day == selectedDay;
            if (isSelected) {
                container.setBackgroundResource(R.drawable.bg_day_selected);
            } else {
                container.setBackground(null);
            }

            final int clickDay = day;
            cell.setOnClickListener(v -> onDaySelected(clickDay));

            gridCalendar.addView(cell);
        }

        if (selectedDay > 0 && dayEntryMap.containsKey(selectedDay)) {
            updatePreviewCard(dayEntryMap.get(selectedDay));
        } else if (selectedDay > 0) {
            cardDayPreview.setVisibility(View.GONE);
        }
    }

    private void addEmptyCell() {
        View spacer = new View(this);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = 48;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        spacer.setLayoutParams(lp);
        gridCalendar.addView(spacer);
    }

    private void buildDayEntryMap() {
        dayEntryMap.clear();
        for (MoodEntry entry : allEntries) {
            Calendar entryCal = Calendar.getInstance();
            entryCal.setTimeInMillis(entry.getTimestamp());
            if (entryCal.get(Calendar.YEAR) == displayYear
                    && entryCal.get(Calendar.MONTH) == displayMonth) {
                int day = entryCal.get(Calendar.DAY_OF_MONTH);
                MoodEntry existing = dayEntryMap.get(day);
                if (existing == null || entry.getTimestamp() > existing.getTimestamp()) {
                    dayEntryMap.put(day, entry);
                }
            }
        }
    }

    private void onDaySelected(int day) {
        selectedDay = day;
        renderCalendar();

        MoodEntry entry = dayEntryMap.get(day);
        if (entry == null) {
            cardDayPreview.setVisibility(View.GONE);
            Toast.makeText(this, "该日期暂无日记", Toast.LENGTH_SHORT).show();
            return;
        }

        updatePreviewCard(entry);
        showDiaryDetailDialog(entry);
    }

    private void updatePreviewCard(MoodEntry entry) {
        cardDayPreview.setVisibility(View.VISIBLE);

        Calendar cal = Calendar.getInstance();
        cal.set(displayYear, displayMonth, selectedDay);
        int weekday = cal.get(Calendar.DAY_OF_WEEK);
        tvPreviewDate.setText(String.format(Locale.getDefault(), "%d月%d日 · %s",
                displayMonth + 1, selectedDay, WEEKDAY_NAMES[weekday]));

        String mood = entry.getUserEmotionLabel();
        if (mood == null || mood.isEmpty()) {
            mood = "未标记";
        }
        tvPreviewMood.setText(mood);
        tvPreviewContent.setText(entry.getText());

        int moodColor = getMoodColor(mood);
        tvPreviewMood.setTextColor(moodColor);
    }

    private void showDiaryDetailDialog(MoodEntry entry) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_diary_detail, null);

        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        TextView tvMood = dialogView.findViewById(R.id.tv_dialog_mood);
        ImageView ivMoodIcon = dialogView.findViewById(R.id.iv_dialog_mood_icon);
        TextView tvContent = dialogView.findViewById(R.id.tv_dialog_content);
        TextView tvAi = dialogView.findViewById(R.id.tv_dialog_ai);
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(entry.getTimestamp());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        tvDate.setText(sdf.format(cal.getTime()));

        String mood = entry.getUserEmotionLabel();
        if (mood == null || mood.isEmpty()) {
            mood = "未标记";
        }
        
        String normalized = com.hearttrace.mooddiary.utils.MoodUiHelper.normalizeMood(mood);
tvMood.setText(normalized);

        // 图标
       ivMoodIcon.setImageResource(com.hearttrace.mooddiary.utils.MoodUiHelper.iconFor(normalized));

        // 更清晰的“深字 + 浅底”
        int textColor = com.hearttrace.mooddiary.utils.MoodUiHelper.moodColor(this, normalized);
        int bgColor = com.hearttrace.mooddiary.utils.MoodUiHelper.iconBgColor(this, normalized);
        tvMood.setTextColor(textColor);

        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(20f * getResources().getDisplayMetrics().density);
        pill.setColor(bgColor);
        dialogView.findViewById(R.id.layout_dialog_mood).setBackground(pill);
        
        tvContent.setText(entry.getText());

        Dialog dialog = new Dialog(this, R.style.Theme_MoodDiary_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            window.setDimAmount(0.45f);
            window.setAttributes(lp);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 优化：优先使用已缓存的 AI 治愈话语，避免重复请求 API
        String cachedQuote = entry.getAiQuote();
        if (cachedQuote != null && !cachedQuote.isEmpty()) {
            tvAi.setText("\u201c" + cachedQuote + "\u201d");
        } else {
            tvAi.setText("正在为你生成治愈话语…");
        }

        dialog.show();

        // 只有缓存为空时才请求 API
        if (cachedQuote == null || cachedQuote.isEmpty()) {
            List<MoodEntry> single = new ArrayList<>();
            single.add(entry);
            AiHealingHelper.requestHealingForDiary(executor, apiClient, single, new AiHealingHelper.Callback() {
                @Override
                public void onSuccess(String reply) {
                    if (dialog.isShowing()) {
                        tvAi.setText("\u201c" + reply + "\u201d");
                    }
                    // 将 AI 回复保存到数据库并同步云端
                    entry.setAiQuote(reply);
                    executor.execute(() -> {
                        db.moodEntryDao().updateMoodEntry(entry);
                        if (syncManager != null) {
                            syncManager.updateMoodEntry(entry);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    if (dialog.isShowing()) {
                        tvAi.setText(message);
                    }
                }
            });
        }
    }

    private void showRecordMoodDialog() {
        recordMoodDialog = new RecordMoodDialog(
                this, userId, db, executor, syncManager, storageClient, this::loadEntriesAndRender);
        // 如果用户在日历中选中了某一天，将选中日期传给弹窗
        if (selectedDay > 0) {
            recordMoodDialog.setSelectedDate(displayYear, displayMonth, selectedDay);
        }
        recordMoodDialog.setOnPickImageClickListener(v -> {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_MEDIA_IMAGES
                }, PERMISSION_ALBUM);
            } else {
                imagePickerLauncher.launch("image/*");
            }
        });
        recordMoodDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ALBUM
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            imagePickerLauncher.launch("image/*");
        } else if (requestCode == PERMISSION_ALBUM) {
            Toast.makeText(this, "需要相册权限才能选择图片", Toast.LENGTH_SHORT).show();
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        try (Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null)) {
            if (cursor == null) {
                return contentUri.toString();
            }
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(columnIndex);
        } catch (Exception e) {
            return contentUri.toString();
        }
    }

    private int getMoodColor(String mood) {
        if (mood == null) {
            return ContextCompat.getColor(this, R.color.mood_default);
        }
        switch (mood) {
            case "开心":
                return ContextCompat.getColor(this, R.color.mood_happy);
            case "平静":
                return ContextCompat.getColor(this, R.color.mood_calm);
            case "难过":
                return ContextCompat.getColor(this, R.color.mood_sad);
            case "生气":
                return ContextCompat.getColor(this, R.color.mood_angry);
            case "焦虑":
                return ContextCompat.getColor(this, R.color.mood_anxious);
            case "疲惫":
                return ContextCompat.getColor(this, R.color.mood_tired);
            default:
                return ContextCompat.getColor(this, R.color.mood_default);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_calendar);
        }
        loadEntriesAndRender();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要退出应用吗？")
                .setPositiveButton("退出", (dialog, which) -> finishAffinity())
                .setNegativeButton("取消", null)
                .show();
    }
}
