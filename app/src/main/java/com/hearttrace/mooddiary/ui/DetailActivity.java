package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import com.hearttrace.mooddiary.supabase.SyncManager;
import com.hearttrace.mooddiary.utils.PrefManager;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    private long userId;
    private long diaryId = -1;
    private EditText etDiaryContent;
    private TextView tvWordCount;
    private AppDatabase db;
    private ExecutorService executor;
    private SyncManager syncManager;

    private String selectedEmotion = "";
    private Button selectedButton;

    private static final int REQUEST_CODE_SELECT_IMAGE = 100;
    private static final int PERMISSION_ALBUM = 200;
    private Button btnSelectImage;
    private ImageView ivPreviewImage;
    private String selectedImagePath = null;

    // 从日历页面传来的指定日期（毫秒时间戳），-1 表示使用当前时间
    private long selectedDate = -1;
    private int selectedYear = -1;
    private int selectedMonth = -1;
    private int selectedDay = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        userId = getIntent().getLongExtra("USER_ID", -1);
        diaryId = getIntent().getLongExtra("DIARY_ID", -1);
        String content = getIntent().getStringExtra("DIARY_CONTENT");
        String emotion = getIntent().getStringExtra("DIARY_EMOTION");
        selectedDate = getIntent().getLongExtra("SELECTED_DATE", -1);
        selectedYear = getIntent().getIntExtra("SELECTED_YEAR", -1);
        selectedMonth = getIntent().getIntExtra("SELECTED_MONTH", -1);
        selectedDay = getIntent().getIntExtra("SELECTED_DAY", -1);

        if (userId == -1) {
            finish();
            return;
        }

        // 1. 先初始化数据库和线程池（关键：先初始化，再使用！）
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        initSyncManager();

        // 2. 绑定控件
        tvWordCount = findViewById(R.id.tv_word_count);
        etDiaryContent = findViewById(R.id.et_diary_content);
        Button btnSave = findViewById(R.id.btn_save);
        btnSelectImage = findViewById(R.id.btn_select_image);
        ivPreviewImage = findViewById(R.id.iv_preview_image);

        // 3. 初始化情绪按钮
        initEmotionButtons();

        // 4. 编辑模式回填数据
        if (diaryId != -1) {
            etDiaryContent.setText(content);
            if (emotion != null) {
                selectedEmotion = emotion;
                highlightSelectedEmotion(emotion);
            }
            setTitle("编辑日记");

            // 回填图片（现在 executor 已经初始化了，不会崩溃）
            executor.execute(() -> {
                MoodEntry oldEntry = db.moodEntryDao().getMoodEntryById(diaryId);
                if (oldEntry != null && oldEntry.getImagePath() != null) {
                    selectedImagePath = oldEntry.getImagePath();
                    runOnUiThread(() -> {
                        ivPreviewImage.setVisibility(View.VISIBLE);
                        ivPreviewImage.setImageURI(Uri.parse("file://" + selectedImagePath));
                    });
                }
            });
        } else {
            if (selectedYear > 0 && selectedMonth >= 0 && selectedDay > 0) {
                setTitle(String.format(Locale.getDefault(), "%d月%d日 新建日记",
                        selectedMonth + 1, selectedDay));
            } else if (selectedDate > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(selectedDate);
                setTitle(String.format(Locale.getDefault(), "%d月%d日 新建日记",
                        cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)));
            } else {
                setTitle("新建日记");
            }
        }

        // 5. 保存按钮点击
        btnSave.setOnClickListener(v -> saveOrUpdate());

        // 6. 实时字数监听
        etDiaryContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int len = s.length();
                tvWordCount.setText("字数：" + len);
            }
        });

        // 初始化显示字数
        tvWordCount.setText("字数：" + etDiaryContent.getText().length());
    }

    private void initEmotionButtons() {
        Button btnHappy = findViewById(R.id.btn_happy);
        Button btnSad = findViewById(R.id.btn_sad);
        Button btnAngry = findViewById(R.id.btn_angry);
        Button btnCalm = findViewById(R.id.btn_calm);
        Button btnAnxious = findViewById(R.id.btn_anxious);
        Button btnTired = findViewById(R.id.btn_tired);

        android.view.View.OnClickListener listener = v -> {
            if (selectedButton != null) selectedButton.setSelected(false);
            selectedButton = (Button) v;
            selectedButton.setSelected(true);

            int id = v.getId();
            if (id == R.id.btn_happy) selectedEmotion = "开心";
            else if (id == R.id.btn_sad) selectedEmotion = "难过";
            else if (id == R.id.btn_angry) selectedEmotion = "生气";
            else if (id == R.id.btn_calm) selectedEmotion = "平静";
            else if (id == R.id.btn_anxious) selectedEmotion = "焦虑";
            else if (id == R.id.btn_tired) selectedEmotion = "疲惫";
        };

        btnHappy.setOnClickListener(listener);
        btnSad.setOnClickListener(listener);
        btnAngry.setOnClickListener(listener);
        btnCalm.setOnClickListener(listener);
        btnAnxious.setOnClickListener(listener);
        btnTired.setOnClickListener(listener);

        // 选择图片按钮（含权限判断）
        btnSelectImage.setOnClickListener(v -> {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_MEDIA_IMAGES
                }, PERMISSION_ALBUM);
            } else {
                openAlbum();
            }
        });
    }

    private void openAlbum() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ALBUM) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openAlbum();
            } else {
                Toast.makeText(this, "需要相册权限才能选择图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void highlightSelectedEmotion(String emotion) {
        int btnId = 0;
        if ("开心".equals(emotion)) btnId = R.id.btn_happy;
        else if ("难过".equals(emotion)) btnId = R.id.btn_sad;
        else if ("生气".equals(emotion)) btnId = R.id.btn_angry;
        else if ("平静".equals(emotion)) btnId = R.id.btn_calm;
        else if ("焦虑".equals(emotion)) btnId = R.id.btn_anxious;
        else if ("疲惫".equals(emotion)) btnId = R.id.btn_tired;

        if (btnId != 0) {
            selectedButton = findViewById(btnId);
            selectedButton.setSelected(true);
        }
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
    }

    private void saveOrUpdate() {
        final String text = etDiaryContent.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "日记内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedEmotion.isEmpty()) {
            Toast.makeText(this, "请选择心情标签", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            if (diaryId == -1) {
                // 新建日记：用 GregorianCalendar 构造函数直接创建指定日期，避免 getInstance() 残留值干扰
                long timestamp;
                if (selectedYear > 0 && selectedMonth >= 0 && selectedDay > 0) {
                    java.util.GregorianCalendar gCal = new java.util.GregorianCalendar(
                            selectedYear, selectedMonth, selectedDay, 12, 0, 0);
                    gCal.set(java.util.Calendar.MILLISECOND, 0);
                    timestamp = gCal.getTimeInMillis();
                    // 【调试】显示将保存的日期
                    android.util.Log.d("DetailActivity",
                            String.format(Locale.getDefault(),
                                    "保存日期：%d-%02d-%02d  时间戳：%d",
                                    selectedYear, selectedMonth + 1, selectedDay, timestamp));
                } else if (selectedDate > 0) {
                    timestamp = selectedDate;
                    android.util.Log.d("DetailActivity",
                            "使用原始时间戳：" + timestamp);
                } else {
                    timestamp = System.currentTimeMillis();
                    android.util.Log.d("DetailActivity",
                            "使用当前时间：" + timestamp);
                }
                MoodEntry entry = new MoodEntry(userId, timestamp, text);
                entry.setUserEmotionLabel(selectedEmotion);
                entry.setImagePath(selectedImagePath);

                if (syncManager != null) {
                    syncManager.createMoodEntry(entry);
                } else {
                    db.moodEntryDao().insertMoodEntry(entry);
                }

                runOnUiThread(() -> {
                    Toast.makeText(DetailActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                // 编辑日记
                MoodEntry old = db.moodEntryDao().getMoodEntryById(diaryId);
                if (old == null) {
                    runOnUiThread(() -> Toast.makeText(DetailActivity.this, "日记不存在", Toast.LENGTH_SHORT).show());
                    return;
                }
                MoodEntry newEntry = new MoodEntry(userId, old.getTimestamp(), text);
                newEntry.setId(diaryId);
                newEntry.setUserEmotionLabel(selectedEmotion);
                newEntry.setImagePath(selectedImagePath);
                newEntry.setUpdateTime(System.currentTimeMillis());
                newEntry.setRemoteId(old.getRemoteId());
                newEntry.setSyncStatus(old.getSyncStatus());

                if (syncManager != null) {
                    syncManager.updateMoodEntry(newEntry);
                } else {
                    db.moodEntryDao().updateMoodEntry(newEntry);
                }

                runOnUiThread(() -> {
                    Toast.makeText(DetailActivity.this, "更新成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            selectedImagePath = getRealPathFromURI(selectedImageUri);
            ivPreviewImage.setVisibility(View.VISIBLE);
            ivPreviewImage.setImageURI(selectedImageUri);
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }
}