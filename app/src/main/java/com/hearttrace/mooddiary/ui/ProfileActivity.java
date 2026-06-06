package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.utils.BitmapUtils;
import com.hearttrace.mooddiary.utils.PrefManager;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.dao.MoodEntryDao;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseAuthClient;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private PrefManager prefManager;
    private AppDatabase db;
    private MoodEntryDao moodEntryDao;
    private ExecutorService executor;

    private TextView tvStatDays;
    private TextView tvStatStreak;
    private TextView tvStatWords;
    private TextView tvProfileName;
    private ImageView ivAvatarTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefManager = new PrefManager(this);
        db = AppDatabase.getInstance(this);
        moodEntryDao = db.moodEntryDao();
        executor = Executors.newSingleThreadExecutor();

        initViews();
        setupBottomNav();
        loadUserInfo();
        loadAvatar();
        loadStatistics();
        syncCloudProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从个人资料返回时刷新用户名显示和头像
        loadUserInfo();
        loadAvatar();
    }

    private void initViews() {
        ivAvatarTop = findViewById(R.id.iv_avatar_top);
        tvStatDays = findViewById(R.id.tv_stat_days);
        tvStatStreak = findViewById(R.id.tv_stat_streak);
        tvStatWords = findViewById(R.id.tv_stat_words);
        tvProfileName = findViewById(R.id.tv_profile_name);

        LinearLayout itemProfile = findViewById(R.id.item_profile);
        LinearLayout itemNotification = findViewById(R.id.item_notification);
        LinearLayout itemFavorites = findViewById(R.id.item_favorites);
        LinearLayout itemTheme = findViewById(R.id.item_theme);
        LinearLayout itemAbout = findViewById(R.id.item_about);
        LinearLayout itemPrivacy = findViewById(R.id.item_privacy);

        TextView btnLogout = findViewById(R.id.btn_logout);

        itemProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileDetailActivity.class)));
        itemNotification.setOnClickListener(v -> startActivity(new Intent(this, NotificationSettingsActivity.class)));
        itemFavorites.setOnClickListener(v -> startActivity(new Intent(this, MyFavoritesActivity.class)));
        itemTheme.setOnClickListener(v -> {
            Intent intent = new Intent(this, ThemeSettingsActivity.class);
            intent.putExtra("USER_ID", prefManager.getUserId());
            startActivity(intent);
        });
        itemAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutUsActivity.class)));
        itemPrivacy.setOnClickListener(v -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));

        btnLogout.setOnClickListener(v -> logout());
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                return true;
            }
            Intent intent = null;
            if (id == R.id.nav_calendar) {
                intent = new Intent(this, HomeActivity.class);
            } else if (id == R.id.nav_statistics) {
                intent = new Intent(this, StatisticsActivity.class);
            } else if (id == R.id.nav_history) {
                intent = new Intent(this, DiaryListActivity.class);
            }
            if (intent != null) {
                intent.putExtra("USER_ID", prefManager.getUserId());
                startActivity(intent);
                finish();
            }
            return false;
        });
    }

    private void loadUserInfo() {
        TextView tvUsername = findViewById(R.id.tv_username);
        String username = prefManager.getUsername();
        tvUsername.setText(username);
        tvProfileName.setText(username);
    }

    /** 加载顶部圆形头像 */
    private void loadAvatar() {
        File avatarFile = new File(getFilesDir(), "avatars/avatar_" + prefManager.getUserId() + ".jpg");
        if (avatarFile.exists()) {
            Bitmap raw = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
            if (raw != null) {
                Bitmap circle = BitmapUtils.toCircleBitmap(raw);
                ivAvatarTop.setImageBitmap(circle);
                ivAvatarTop.setImageTintList(null); // 清除默认占位图的白色 tint
                raw.recycle();
            }
        }
    }

    private void loadStatistics() {
        new Thread(() -> {
            long userId = prefManager.getUserId();

            int totalDays = moodEntryDao.countDistinctDays(userId);

            int totalWords = moodEntryDao.sumTotalWords(userId);

            int streak = calculateStreak(userId);

            String formattedWords = formatWordCount(totalWords);

            new Handler(Looper.getMainLooper()).post(() -> {
                tvStatDays.setText(String.valueOf(totalDays));
                tvStatStreak.setText(streak + "天");
                tvStatWords.setText(formattedWords);
            });
        }).start();
    }

    private int calculateStreak(long userId) {
        List<MoodEntry> entries = moodEntryDao.getMoodEntriesByUserId(userId);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        int streak = 0;
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar entryDate = Calendar.getInstance();

        for (int i = 0; i < 365; i++) {
            Calendar checkDate = (Calendar) today.clone();
            checkDate.add(Calendar.DATE, -i);

            boolean hasEntry = false;
            for (MoodEntry entry : entries) {
                entryDate.setTime(new Date(entry.getTimestamp()));
                entryDate.set(Calendar.HOUR_OF_DAY, 0);
                entryDate.set(Calendar.MINUTE, 0);
                entryDate.set(Calendar.SECOND, 0);
                entryDate.set(Calendar.MILLISECOND, 0);

                if (checkDate.getTimeInMillis() == entryDate.getTimeInMillis()) {
                    hasEntry = true;
                    break;
                }
            }

            if (hasEntry) {
                streak++;
            } else if (i > 0) {
                break;
            }
        }

        return streak;
    }

    private String formatWordCount(int count) {
        if (count >= 10000) {
            return String.format("%.1fk", count / 10000.0);
        } else if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    private void logout() {
        // 如果已登录 Supabase，先云端登出
        if (prefManager.isSupabaseLoggedIn() && !SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
            executor.execute(() -> {
                try {
                    SupabaseAuthClient authClient = new SupabaseAuthClient(
                            SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY);
                    authClient.setAccessToken(prefManager.getSupabaseToken());
                    authClient.signOut();
                } catch (Exception e) {
                    Log.w("ProfileActivity", "Supabase 登出失败: " + e.getMessage());
                }
            });
        }
        prefManager.clearUserData();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** 从云端同步用户资料到本地 */
    private void syncCloudProfile() {
        if (!prefManager.isSupabaseLoggedIn() || SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
            return;
        }
        executor.execute(() -> {
            try {
                SupabaseDataClient dataClient = new SupabaseDataClient(
                        SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY,
                        prefManager.getSupabaseToken());
                org.json.JSONObject profile = dataClient.getUserProfile(prefManager.getSupabaseUserUuid());
                if (profile != null) {
                    String cloudUsername = profile.optString("username", "");
                    String cloudSignature = profile.optString("signature", "");
                    if (!cloudUsername.isEmpty()) {
                        prefManager.saveLogin(prefManager.getUserId(), cloudUsername);
                        prefManager.saveSignature(cloudSignature);
                        runOnUiThread(() -> {
                            tvProfileName.setText(cloudUsername);
                            TextView tvUsername = findViewById(R.id.tv_username);
                            if (tvUsername != null) tvUsername.setText(cloudUsername);
                        });
                    }
                }
            } catch (Exception e) {
                Log.w("ProfileActivity", "云端资料同步失败: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
