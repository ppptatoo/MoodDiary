package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyFavoritesActivity extends AppCompatActivity {

    private static final String[] WEEKDAY_NAMES =
            {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};

    private PrefManager prefManager;
    private AppDatabase db;
    private ExecutorService executor;
    private SyncManager syncManager;
    private SupabaseStorageClient storageClient;
    private RecyclerView rvFavorites;
    private TextView tvEmptyTip;
    private ImageView btnBack;

    private List<MoodEntry> favoriteEntries = new ArrayList<>();
    private FavoriteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_favorites);

        prefManager = new PrefManager(this);
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();

        btnBack = findViewById(R.id.btn_back);
        rvFavorites = findViewById(R.id.rv_favorites);
        tvEmptyTip = findViewById(R.id.tv_empty_tip);

        btnBack.setOnClickListener(v -> finish());

        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavoriteAdapter();
        rvFavorites.setAdapter(adapter);

        initSyncManager();
        loadFavorites();
    }

    private void loadFavorites() {
        long userId = prefManager.getUserId();
        if (userId == -1) {
            finish();
            return;
        }
        executor.execute(() -> {
            favoriteEntries = db.moodEntryDao().getFavoriteEntriesByUserId(userId);
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                boolean empty = favoriteEntries.isEmpty();
                tvEmptyTip.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvFavorites.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private String formatCardDate(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        int weekday = cal.get(Calendar.DAY_OF_WEEK);
        return sdf.format(cal.getTime()) + " " + WEEKDAY_NAMES[weekday];
    }

    private class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_diary, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MoodEntry entry = favoriteEntries.get(position);
            String mood = MoodUiHelper.normalizeMood(entry.getUserEmotionLabel());

            holder.tvDate.setText(formatCardDate(entry.getTimestamp()));
            holder.tvContent.setText(
                    entry.getText() != null && !entry.getText().isEmpty()
                            ? entry.getText() : "（暂无文字内容）");

            holder.tvMoodTag.setText(mood);
            int moodColor = MoodUiHelper.moodColor(MyFavoritesActivity.this, mood);
            holder.tvMoodTag.setTextColor(moodColor);
            GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(20f * getResources().getDisplayMetrics().density);
            pill.setColor(adjustAlpha(moodColor, 0.22f));
            holder.tvMoodTag.setBackground(pill);

            // 收藏图标固定为实心
            holder.ivFavorite.setImageResource(R.drawable.ic_heart_filled);
            holder.ivFavorite.setOnClickListener(v -> {
                entry.setFavorite(false);
                // 走 SyncManager 乐观更新：先写 Room，再推云端，保证数据一致
                new Thread(() -> {
                    if (syncManager != null) {
                        syncManager.updateMoodEntry(entry);
                    } else {
                        db.moodEntryDao().updateMoodEntry(entry);
                    }
                }).start();
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < favoriteEntries.size()) {
                    favoriteEntries.remove(pos);
                    notifyItemRemoved(pos);
                    if (favoriteEntries.isEmpty()) {
                        tvEmptyTip.setVisibility(View.VISIBLE);
                        rvFavorites.setVisibility(View.GONE);
                    }
                }
                Toast.makeText(MyFavoritesActivity.this, "已取消收藏", Toast.LENGTH_SHORT).show();
            });

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MyFavoritesActivity.this, DiaryDetailActivity.class);
                intent.putExtra("DIARY_ID", entry.getId());
                startActivity(intent);
            });

            // AI 治愈话语：优先从数据库读取
            String savedQuote = entry.getAiQuote();
            if (savedQuote != null && !savedQuote.isEmpty()) {
                holder.tvAiQuote.setText("\u201c" + savedQuote + "\u201d");
            } else {
                holder.tvAiQuote.setText("正在生成治愈话语…");
                List<MoodEntry> single = new ArrayList<>();
                single.add(entry);
                AiHealingHelper.requestHistoryDiaryHealing(executor, new DoubaoApiClient(), single,
                        new AiHealingHelper.Callback() {
                            @Override
                            public void onSuccess(String reply) {
                                int pos = holder.getBindingAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION
                                        && pos < favoriteEntries.size()
                                        && favoriteEntries.get(pos).getId() == entry.getId()) {
                                    holder.tvAiQuote.setText("\u201c" + reply + "\u201d");
                                    entry.setAiQuote(reply);
                                    new Thread(() -> db.moodEntryDao().updateMoodEntry(entry)).start();
                                }
                            }

                            @Override
                            public void onError(String message) {
                                int pos = holder.getBindingAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION
                                        && pos < favoriteEntries.size()
                                        && favoriteEntries.get(pos).getId() == entry.getId()) {
                                    holder.tvAiQuote.setText(message);
                                }
                            }
                        });
            }
            bindCardImage(holder, entry);
        }

        @Override
        public int getItemCount() {
            return favoriteEntries.size();
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

    private void bindCardImage(FavoriteAdapter.Holder holder, MoodEntry entry) {
        holder.pbDiaryImage.setVisibility(View.GONE);
        Glide.with(this).clear(holder.ivDiaryImage);
        holder.ivDiaryImage.setImageDrawable(null);
        holder.ivDiaryImage.setBackgroundResource(R.drawable.bg_ai_image_placeholder);

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
            new Thread(() -> db.moodEntryDao().updateMoodEntry(entry)).start();
            return;
        }

        holder.pbDiaryImage.setVisibility(View.VISIBLE);
        AiIllustrationHelper.requestIllustration(
                executor, new DoubaoApiClient(), this, entry, storageClient,
                new AiIllustrationHelper.Callback() {
                    @Override
                    public void onSuccess(String localImagePath) {
                        int pos = holder.getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION
                                || pos >= favoriteEntries.size()
                                || favoriteEntries.get(pos).getId() != entry.getId()) {
                            return;
                        }
                        holder.pbDiaryImage.setVisibility(View.GONE);
                        loadImage(holder.ivDiaryImage, localImagePath);
                        entry.setAiImagePath(localImagePath);
                        new Thread(() -> db.moodEntryDao().updateMoodEntry(entry)).start();
                    }

                    @Override
                    public void onError(String message) {
                        int pos = holder.getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION
                                || pos >= favoriteEntries.size()
                                || favoriteEntries.get(pos).getId() != entry.getId()) {
                            return;
                        }
                        holder.pbDiaryImage.setVisibility(View.GONE);
                    }
                });
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
    }

    private static int adjustAlpha(int color, float factor) {
        int a = Math.round(255 * factor);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** 初始化 Supabase 同步管理器 */
    private void initSyncManager() {
        if (SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) return;
        String uuid = prefManager.getSupabaseUserUuid();
        String token = prefManager.getSupabaseToken();
        if (uuid.isEmpty() || token.isEmpty()) return;
        SupabaseDataClient dataClient = new SupabaseDataClient(
                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, token);
        syncManager = new SyncManager(db, uuid, dataClient);
        storageClient = new SupabaseStorageClient(
                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, token);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}