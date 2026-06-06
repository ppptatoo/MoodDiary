package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import com.hearttrace.mooddiary.supabase.SupabaseStorageClient;
import com.hearttrace.mooddiary.supabase.SyncManager;
import com.hearttrace.mooddiary.utils.AiHealingHelper;
import com.hearttrace.mooddiary.utils.AiIllustrationHelper;
import com.hearttrace.mooddiary.utils.DoubaoApiClient;
import com.hearttrace.mooddiary.utils.MoodUiHelper;
import com.hearttrace.mooddiary.utils.PrefManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiaryListActivity extends AppCompatActivity {

    private static final String[] WEEKDAY_NAMES =
            {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};

    private long userId;
    private AppDatabase db;
    private ExecutorService executor;
    private final DoubaoApiClient apiClient = new DoubaoApiClient();
    private SyncManager syncManager;
    private SupabaseStorageClient storageClient;
    private boolean isFirstSync = true;

    private LinearLayout llDateStrip;
    private HorizontalScrollView scrollDates;
    private RecyclerView rvDiaries;
    private TextView tvEmptyTip;
    private TextView tvMonthYear;
    private ProgressBar pbLoading;

    private List<MoodEntry> allEntries = new ArrayList<>();
    private final Map<Integer, MoodEntry> dayEntryMap = new HashMap<>();
    private final List<Integer> stripDays = new ArrayList<>();
    private volatile boolean isLoading = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int displayYear;
    private int displayMonth;
    private int selectedDay = -1;

    private DiaryAdapter diaryAdapter;

    // 优化：标记是否正在首次加载（用于控制 loading 指示器）
    private boolean isFirstLoad = true;
    // 优化：避免 onResume 时无意义重载（从详情页返回时不需要重新拉取）
    private boolean skipNextResumeReload = false;
    // 优化：缓存当前月份，只在月份切换时重建日期条
    private int cachedMonthForStrip = -1;
    private int cachedYearForStrip = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_list);

        try {
            userId = getIntent().getLongExtra("USER_ID", -1);
            if (userId == -1) {
                finish();
                return;
            }

            Calendar today = Calendar.getInstance();
            displayYear = today.get(Calendar.YEAR);
            displayMonth = today.get(Calendar.MONTH);
            selectedDay = today.get(Calendar.DAY_OF_MONTH);

            llDateStrip = findViewById(R.id.ll_date_strip);
            scrollDates = findViewById(R.id.scroll_dates);
            rvDiaries = findViewById(R.id.rv_diaries);
            tvEmptyTip = findViewById(R.id.tv_empty_tip);
            tvMonthYear = findViewById(R.id.tv_month_year);
            pbLoading = findViewById(R.id.pb_loading);
            updateMonthYearLabel();

            if (rvDiaries != null) {
                rvDiaries.setLayoutManager(new LinearLayoutManager(this));
                rvDiaries.setHasFixedSize(true);
                rvDiaries.setItemViewCacheSize(10);
                diaryAdapter = new DiaryAdapter();
                rvDiaries.setAdapter(diaryAdapter);
            }

            db = AppDatabase.getInstance(this);
            executor = Executors.newSingleThreadExecutor();

            try {
                initSyncManager();
            } catch (Exception e) {
                android.util.Log.e("DiaryList", "initSyncManager failed", e);
                syncManager = null;
            }

            try {
                setupBottomNav();
            } catch (Exception e) {
                android.util.Log.e("DiaryList", "setupBottomNav failed", e);
            }
            loadDiaries();
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "onCreate error", e);
            Toast.makeText(this, "页面初始化失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.nav_history);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                return true;
            }
            Intent intent = null;
            if (id == R.id.nav_calendar) {
                intent = new Intent(this, HomeActivity.class);
            } else if (id == R.id.nav_statistics) {
                intent = new Intent(this, StatisticsActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }
            if (intent != null) {
                intent.putExtra("USER_ID", userId);
                startActivity(intent);
                finish();
            }
            return false;
        });
    }

    private void loadDiaries() {
        if (isLoading) return;
        isLoading = true;
        // 首次加载时显示loading指示器
        if (isFirstLoad && pbLoading != null) {
            pbLoading.setVisibility(View.VISIBLE);
        }
        executor.execute(() -> {
            try {
                // ===== 第一步：快速从 Room 本地读取，立即显示 =====
                final List<MoodEntry> entries = db.moodEntryDao().getMoodEntriesByUserId(userId);

                // 合并内存中的最新状态：用户刚点击收藏/取消收藏时，Room 写入可能还在另一个线程中
                // 这里用当前 allEntries 中的状态覆盖 Room 数据，确保用户操作不会"丢失"
                if (allEntries != null && !allEntries.isEmpty()) {
                    java.util.Map<Long, Boolean> memoryFav = new java.util.HashMap<>();
                    for (MoodEntry e : allEntries) {
                        memoryFav.put(e.getId(), e.isFavorite());
                    }
                    for (MoodEntry e : entries) {
                        Boolean fav = memoryFav.get(e.getId());
                        if (fav != null && fav != e.isFavorite()) {
                            e.setFavorite(fav);
                        }
                    }
                }

                allEntries = entries;

                mainHandler.post(() -> {
                    isLoading = false;
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        // 隐藏loading
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        boolean dateStripNeeded = (displayYear != cachedYearForStrip
                                || displayMonth != cachedMonthForStrip);
                        buildDayEntryMap();
                        boolean empty = allEntries.isEmpty();
                        if (tvEmptyTip != null) tvEmptyTip.setVisibility(empty ? View.VISIBLE : View.GONE);
                        if (rvDiaries != null) rvDiaries.setVisibility(empty ? View.GONE : View.VISIBLE);
                        if (scrollDates != null) scrollDates.setVisibility(empty ? View.GONE : View.VISIBLE);
                        if (diaryAdapter != null) diaryAdapter.notifyDataSetChanged();
                        // 日期条只在月份变化时重建，否则仅刷新选中状态
                        if (dateStripNeeded) {
                            buildDateStrip();
                            cachedYearForStrip = displayYear;
                            cachedMonthForStrip = displayMonth;
                        } else {
                            refreshDateStripSelection();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DiaryList", "loadDiaries phase1 error", e);
                    }
                });

                // ===== 第二步：后台异步同步云端，不阻塞UI =====
                performBackgroundSync();

            } catch (Exception e) {
                isLoading = false;
                mainHandler.post(() -> {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                });
                android.util.Log.e("DiaryList", "loadDiaries error", e);
            }
        });
    }

    /**
     * 后台异步同步云端数据，完成后无缝刷新UI。
     * 完全独立于 loadDiaries 主流程，不会阻塞首次展示。
     */
    private void performBackgroundSync() {
        if (syncManager == null) return;
        executor.execute(() -> {
            try {
                // 记录同步前的条目数
                int countBefore = allEntries.size();
                boolean isFirst = isFirstSync;
                if (isFirstSync) {
                    isFirstSync = false;
                }
                // 先推送本地未同步的数据，再拉取云端，避免并发重复
                syncManager.pushPendingEntries(userId);
                syncManager.pullFromCloud(userId);

                // 同步后重新读取本地数据
                final List<MoodEntry> updatedEntries = db.moodEntryDao().getMoodEntriesByUserId(userId);

                // 如果数据有变化，刷新UI
                if (updatedEntries.size() != countBefore || isFirst) {
                    allEntries = updatedEntries;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        try {
                            buildDayEntryMap();
                            boolean empty = allEntries.isEmpty();
                            if (tvEmptyTip != null) tvEmptyTip.setVisibility(empty ? View.VISIBLE : View.GONE);
                            if (rvDiaries != null) rvDiaries.setVisibility(empty ? View.GONE : View.VISIBLE);
                            if (scrollDates != null) scrollDates.setVisibility(empty ? View.GONE : View.VISIBLE);
                            if (diaryAdapter != null) diaryAdapter.notifyDataSetChanged();
                            // 重建日期条（数据可能变了）
                            buildDateStrip();
                            cachedYearForStrip = displayYear;
                            cachedMonthForStrip = displayMonth;
                        } catch (Exception e) {
                            android.util.Log.e("DiaryList", "backgroundSync ui error", e);
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("DiaryList", "backgroundSync error", e);
            }
        });
    }

    /** 仅刷新日期条的选中状态，不重建全部View */
    private void refreshDateStripSelection() {
        if (llDateStrip == null) return;
        try {
            for (int i = 0; i < llDateStrip.getChildCount(); i++) {
                int day = (i < stripDays.size()) ? stripDays.get(i) : (i + 1);
                bindDateStripItem(llDateStrip.getChildAt(i), day);
            }
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "refreshDateStripSelection error", e);
        }
    }

    private void buildDayEntryMap() {
        dayEntryMap.clear();
        for (MoodEntry entry : allEntries) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(entry.getTimestamp());
            if (c.get(Calendar.YEAR) == displayYear
                    && c.get(Calendar.MONTH) == displayMonth) {
                int day = c.get(Calendar.DAY_OF_MONTH);
                MoodEntry existing = dayEntryMap.get(day);
                if (existing == null || entry.getTimestamp() > existing.getTimestamp()) {
                    dayEntryMap.put(day, entry);
                }
            }
        }
    }

    private void updateMonthYearLabel() {
        if (tvMonthYear != null) {
            tvMonthYear.setText(String.format(Locale.getDefault(), "%d年%d月",
                    displayYear, displayMonth + 1));
        }
    }

    private void buildDateStrip() {
        if (llDateStrip == null) return;
        try {
            updateMonthYearLabel();
            llDateStrip.removeAllViews();
            stripDays.clear();

            Calendar cal = Calendar.getInstance();
            cal.set(displayYear, displayMonth, 1);
            int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            LayoutInflater inflater = LayoutInflater.from(this);
            for (int day = 1; day <= maxDay; day++) {
                stripDays.add(day);
                View item = inflater.inflate(R.layout.item_history_date, llDateStrip, false);
                bindDateStripItem(item, day);
                llDateStrip.addView(item);
            }

            llDateStrip.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    try {
                        scrollToSelectedDay();
                    } catch (Exception e) {
                        android.util.Log.e("DiaryList", "scrollToSelectedDay error", e);
                    }
                }
            });
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "buildDateStrip error", e);
        }
    }

    private void bindDateStripItem(View item, int day) {
        if (item == null) return;
        try {
            FrameLayout flEmpty = item.findViewById(R.id.fl_empty_day);
            FrameLayout flMood = item.findViewById(R.id.fl_mood_day);
            TextView tvDayEmpty = item.findViewById(R.id.tv_day_empty);
            TextView tvDayOnIcon = item.findViewById(R.id.tv_day_on_icon);
            LinearLayout llMoodBg = item.findViewById(R.id.ll_mood_icon_bg);
            ImageView ivMood = item.findViewById(R.id.iv_mood_icon);

            MoodEntry entry = dayEntryMap.get(day);
            boolean hasEntry = entry != null;
            boolean isSelected = day == selectedDay;

            if (hasEntry) {
                if (flEmpty != null) flEmpty.setVisibility(View.GONE);
                if (flMood != null) flMood.setVisibility(View.VISIBLE);

                String mood = MoodUiHelper.normalizeMood(entry.getUserEmotionLabel());
                if (tvDayOnIcon != null) tvDayOnIcon.setText(String.valueOf(day));

                int bgColor = isSelected
                        ? ContextCompat.getColor(this, R.color.history_date_selected)
                        : MoodUiHelper.iconBgColor(this, mood);
                if (llMoodBg != null)
                    llMoodBg.setBackground(MoodUiHelper.roundedSquareBgColor(this, bgColor, 12f));

                if (tvDayOnIcon != null) {
                    if (isSelected) {
                        tvDayOnIcon.setTextColor(ContextCompat.getColor(this, R.color.white));
                    } else {
                        tvDayOnIcon.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    }
                }

                if (ivMood != null) {
                    ivMood.setVisibility(View.VISIBLE);
                    ivMood.setImageResource(MoodUiHelper.iconFor(mood));
                }

                if (flMood != null) {
                    if (isSelected) {
                        GradientDrawable ring = new GradientDrawable();
                        ring.setShape(GradientDrawable.RECTANGLE);
                        ring.setCornerRadius(12f * getResources().getDisplayMetrics().density);
                        ring.setStroke(
                                (int) (2 * getResources().getDisplayMetrics().density),
                                ContextCompat.getColor(this, R.color.history_date_selected));
                        flMood.setForeground(ring);
                    } else {
                        flMood.setForeground(null);
                    }
                }
            } else {
                if (flMood != null) flMood.setVisibility(View.GONE);
                if (flEmpty != null) flEmpty.setVisibility(View.VISIBLE);
                if (tvDayEmpty != null) tvDayEmpty.setText(String.valueOf(day));

                if (isSelected) {
                    if (flEmpty != null) {
                        GradientDrawable sel = new GradientDrawable();
                        sel.setShape(GradientDrawable.OVAL);
                        sel.setColor(ContextCompat.getColor(this, R.color.history_date_selected));
                        flEmpty.setBackground(sel);
                    }
                    if (tvDayEmpty != null)
                        tvDayEmpty.setTextColor(ContextCompat.getColor(this, R.color.white));
                } else {
                    if (flEmpty != null)
                        flEmpty.setBackgroundResource(R.drawable.bg_history_date_empty);
                    if (tvDayEmpty != null)
                        tvDayEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                }
            }

            final int clickDay = day;
            item.setOnClickListener(v -> {
                selectedDay = clickDay;
                buildDateStrip();
                scrollToDiaryForDay(clickDay);
            });
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "bindDateStripItem error day=" + day, e);
        }
    }

    private void scrollToSelectedDay() {
        try {
            if (llDateStrip == null || scrollDates == null) return;
            int index = stripDays.indexOf(selectedDay);
            if (index < 0 || index >= llDateStrip.getChildCount()) return;
            View child = llDateStrip.getChildAt(index);
            if (child != null && scrollDates.getWidth() > 0) {
                int scrollX = child.getLeft() - (scrollDates.getWidth() - child.getWidth()) / 2;
                scrollDates.smoothScrollTo(Math.max(0, scrollX), 0);
            }
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "scrollToSelectedDay error", e);
        }
    }

    private void scrollToDiaryForDay(int day) {
        try {
            MoodEntry target = dayEntryMap.get(day);
            if (target == null || rvDiaries == null) return;
            for (int i = 0; i < allEntries.size(); i++) {
                if (allEntries.get(i).getId() == target.getId()) {
                    int pos = i;
                    rvDiaries.smoothScrollToPosition(pos);
                    return;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "scrollToDiaryForDay error", e);
        }
    }

    private String formatCardDate(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        int weekday = cal.get(Calendar.DAY_OF_WEEK);
        return sdf.format(cal.getTime()) + " " + WEEKDAY_NAMES[weekday];
    }

    private class DiaryAdapter extends RecyclerView.Adapter<DiaryAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            try {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_history_diary, parent, false);
                return new Holder(v);
            } catch (Exception e) {
                android.util.Log.e("DiaryList", "onCreateViewHolder error", e);
                // 返回一个最小化的空View避免崩溃扩散
                View fallback = new View(parent.getContext());
                fallback.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return new Holder(fallback);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            try {
                MoodEntry entry = allEntries.get(position);
                if (entry == null) return;
                String mood = MoodUiHelper.normalizeMood(entry.getUserEmotionLabel());

                if (holder.tvDate != null)
                    holder.tvDate.setText(formatCardDate(entry.getTimestamp()));
                if (holder.tvContent != null)
                    holder.tvContent.setText(
                            entry.getText() != null && !entry.getText().isEmpty()
                                    ? entry.getText() : "（暂无文字内容）");

                if (holder.tvMoodTag != null) {
                    holder.tvMoodTag.setText(mood);
                    int moodColor = MoodUiHelper.moodColor(DiaryListActivity.this, mood);
                    holder.tvMoodTag.setTextColor(moodColor);
                    GradientDrawable pill = new GradientDrawable();
                    pill.setShape(GradientDrawable.RECTANGLE);
                    pill.setCornerRadius(20f * getResources().getDisplayMetrics().density);
                    pill.setColor(adjustAlpha(moodColor, 0.22f));
                    holder.tvMoodTag.setBackground(pill);
                }

                bindCardImage(holder, entry);

                // 收藏状态
                if (holder.ivFavorite != null) {
                    updateFavoriteIcon(holder.ivFavorite, entry.isFavorite());
                    holder.ivFavorite.setOnClickListener(v -> {
                        try {
                            boolean newState = !entry.isFavorite();
                            entry.setFavorite(newState);
                            updateFavoriteIcon(holder.ivFavorite, newState);
                            new Thread(() -> updateEntry(entry)).start();
                            Toast.makeText(DiaryListActivity.this,
                                    newState ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            android.util.Log.e("DiaryList", "favorite click error", e);
                        }
                    });
                }

                holder.itemView.setOnClickListener(v -> {
                    skipNextResumeReload = true;
                    Intent intent = new Intent(DiaryListActivity.this, DiaryDetailActivity.class);
                    intent.putExtra("DIARY_ID", entry.getId());
                    startActivity(intent);
                });

                // AI 治愈话语
                if (holder.tvAiQuote != null) {
                    String savedQuote = entry.getAiQuote();
                    if (savedQuote != null && !savedQuote.isEmpty()) {
                        holder.tvAiQuote.setText("\u201c" + savedQuote + "\u201d");
                    } else {
                        holder.tvAiQuote.setText("正在生成治愈话语…");
                        List<MoodEntry> single = new ArrayList<>();
                        single.add(entry);
                        AiHealingHelper.requestHistoryDiaryHealing(executor, apiClient, single,
                                new AiHealingHelper.Callback() {
                                    @Override
                                    public void onSuccess(String reply) {
                                        int pos = holder.getBindingAdapterPosition();
                                        if (pos != RecyclerView.NO_POSITION
                                                && pos < allEntries.size()
                                                && allEntries.get(pos).getId() == entry.getId()) {
                                            if (holder.tvAiQuote != null)
                                                holder.tvAiQuote.setText("\u201c" + reply + "\u201d");
                                            entry.setAiQuote(reply);
                                            updateEntry(entry);
                                        }
                                    }

                                    @Override
                                    public void onError(String message) {
                                        int pos = holder.getBindingAdapterPosition();
                                        if (pos != RecyclerView.NO_POSITION
                                                && pos < allEntries.size()
                                                && allEntries.get(pos).getId() == entry.getId()) {
                                            if (holder.tvAiQuote != null)
                                                holder.tvAiQuote.setText(message);
                                        }
                                    }
                                });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("DiaryList", "onBindViewHolder error at pos=" + position, e);
            }
        }

        @Override
        public int getItemCount() {
            return allEntries.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvDate;
            final TextView tvMoodTag;
            final TextView tvContent;
            final TextView tvAiQuote;
            final ImageView ivDiaryImage;
            final ProgressBar pbDiaryImage;
            final ImageView ivFavorite;

            Holder(View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tv_date);
                tvMoodTag = itemView.findViewById(R.id.tv_mood_tag);
                tvContent = itemView.findViewById(R.id.tv_content);
                tvAiQuote = itemView.findViewById(R.id.tv_ai_quote);
                ivDiaryImage = itemView.findViewById(R.id.iv_diary_image);
                pbDiaryImage = itemView.findViewById(R.id.pb_diary_image);
                ivFavorite = itemView.findViewById(R.id.iv_favorite);
            }
        }
    }

    private void bindCardImage(DiaryAdapter.Holder holder, MoodEntry entry) {
        try {
            if (holder.pbDiaryImage != null) holder.pbDiaryImage.setVisibility(View.GONE);
            if (holder.ivDiaryImage != null) {
                if (!isDestroyed()) Glide.with(this).clear(holder.ivDiaryImage);
                holder.ivDiaryImage.setImageDrawable(null);
                holder.ivDiaryImage.setBackgroundResource(R.drawable.bg_ai_image_placeholder);
            }

            String userPath = entry.getImagePath();
            if (isExistingImagePath(userPath)) {
                loadImage(holder.ivDiaryImage, userPath);
                return;
            }

            // 优先从数据库读取AI图片路径
            String dbAiPath = entry.getAiImagePath();
            if (dbAiPath != null && !dbAiPath.isEmpty() && isExistingImagePath(dbAiPath)) {
                loadImage(holder.ivDiaryImage, dbAiPath);
                return;
            }

            String cachedAi = AiIllustrationHelper.getCachedPath(this, entry.getId());
            if (cachedAi != null) {
                loadImage(holder.ivDiaryImage, cachedAi);
                entry.setAiImagePath(cachedAi);
                updateEntry(entry);
                return;
            }

            if (holder.pbDiaryImage != null) holder.pbDiaryImage.setVisibility(View.VISIBLE);
            AiIllustrationHelper.requestIllustration(
                    executor, apiClient, this, entry, storageClient,
                    new AiIllustrationHelper.Callback() {
                        @Override
                        public void onSuccess(String localImagePath) {
                            int pos = holder.getBindingAdapterPosition();
                            if (pos == RecyclerView.NO_POSITION
                                    || pos >= allEntries.size()
                                    || allEntries.get(pos).getId() != entry.getId()) {
                                return;
                            }
                            if (holder.pbDiaryImage != null)
                                holder.pbDiaryImage.setVisibility(View.GONE);
                            loadImage(holder.ivDiaryImage, localImagePath);
                            entry.setAiImagePath(localImagePath);
                            updateEntry(entry);
                        }

                        @Override
                        public void onError(String message) {
                            int pos = holder.getBindingAdapterPosition();
                            if (pos == RecyclerView.NO_POSITION
                                    || pos >= allEntries.size()
                                    || allEntries.get(pos).getId() != entry.getId()) {
                                return;
                            }
                            if (holder.pbDiaryImage != null)
                                holder.pbDiaryImage.setVisibility(View.GONE);
                        }
                    });
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "bindCardImage error", e);
        }
    }

    private static boolean isExistingImagePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return true;
        }
        String filePath = path.startsWith("file://") ? path.substring(7) : path;
        return new File(filePath).exists();
    }

    private void loadImage(ImageView imageView, String path) {
        if (imageView == null || path == null || isDestroyed()) return;
        try {
            imageView.setBackground(null);
            if (path.startsWith("http://") || path.startsWith("https://")) {
                Glide.with(this)
                        .load(path)
                        .centerCrop()
                        .placeholder(R.drawable.bg_ai_image_placeholder)
                        .into(imageView);
            } else {
                Uri uri = path.startsWith("file://") ? Uri.parse(path) : Uri.parse("file://" + path);
                Glide.with(this)
                        .load(uri)
                        .centerCrop()
                        .placeholder(R.drawable.bg_ai_image_placeholder)
                        .into(imageView);
            }
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "loadImage error path=" + path, e);
        }
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(255 * factor);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static void updateFavoriteIcon(ImageView iv, boolean isFavorite) {
        iv.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
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

    /** 统一的日记更新方法（含 Supabase 同步） */
    private void updateEntry(MoodEntry entry) {
        try {
            if (syncManager != null) {
                syncManager.updateMoodEntry(entry);
            } else {
                db.moodEntryDao().updateMoodEntry(entry);
            }
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "updateEntry error", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_history);
            }
            // 优化：首次加载或标记为需要刷新时才重新加载
            // 从详情页返回等场景 skipNextResumeReload 为 true，跳过不必要的全量加载
            if (isFirstLoad) {
                isFirstLoad = false;
                loadDiaries();
            } else if (!skipNextResumeReload) {
                loadDiaries();
            }
            skipNextResumeReload = false; // 重置标志位
        } catch (Exception e) {
            android.util.Log.e("DiaryList", "onResume error", e);
        }
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}
