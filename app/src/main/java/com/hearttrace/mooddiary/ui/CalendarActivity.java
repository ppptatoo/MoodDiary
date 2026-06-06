package com.hearttrace.mooddiary.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.utils.AiHealingHelper;
import com.hearttrace.mooddiary.utils.DoubaoApiClient;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarActivity extends AppCompatActivity {

    private long userId;
    private CalendarView calendarView;
    private ListView lvDayDiary;
    private TextView tvTip;
    private TextView tvAiReply;
    private TextView btnRecordMood;

    private AppDatabase db;
    private ExecutorService executor;
    private List<MoodEntry> dayDiaryList;
    private DiaryAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DoubaoApiClient apiClient = new DoubaoApiClient();

    private int currentYear, currentMonth, currentDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        userId = getIntent().getLongExtra("USER_ID", -1);
        if (userId == -1) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();

        // 绑定控件
        calendarView = findViewById(R.id.calendarView);
        lvDayDiary = findViewById(R.id.lv_day_diary);
        tvTip = findViewById(R.id.tv_tip);
        tvAiReply = findViewById(R.id.tv_ai_reply);
        btnRecordMood = findViewById(R.id.btn_record_mood);

        dayDiaryList = new ArrayList<>();

        // 初始化当前日期
        Calendar today = Calendar.getInstance();
        currentYear = today.get(Calendar.YEAR);
        currentMonth = today.get(Calendar.MONTH);
        currentDay = today.get(Calendar.DAY_OF_MONTH);

        // 日历日期点击监听
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            currentYear = year;
            currentMonth = month;
            currentDay = dayOfMonth;

            String selectDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            tvTip.setText("当前选中：" + selectDate);
            btnRecordMood.setText(String.format(Locale.getDefault(), "记录 %d月%d日 心情", month + 1, dayOfMonth));
            loadDiaryByDate(year, month, dayOfMonth);
        });

        // 记录心情按钮：跳转到日记编辑页，并传递选中的日期
        btnRecordMood.setOnClickListener(v -> {
            // 用 GregorianCalendar 构造函数直接创建指定日期的日历，完全不依赖 Calendar.getInstance()
            java.util.GregorianCalendar gCal = new java.util.GregorianCalendar(
                    currentYear, currentMonth, currentDay, 12, 0, 0);
            gCal.set(java.util.Calendar.MILLISECOND, 0);
            long selectedTimestamp = gCal.getTimeInMillis();

            // 【调试】显示将要记录的日期
            String debugDate = String.format(Locale.getDefault(),
                    "%d-%02d-%02d", currentYear, currentMonth + 1, currentDay);
            Toast.makeText(CalendarActivity.this,
                    "将记录日期：" + debugDate, Toast.LENGTH_LONG).show();

            Intent intent = new Intent(CalendarActivity.this, DetailActivity.class);
            intent.putExtra("USER_ID", userId);
            intent.putExtra("SELECTED_DATE", selectedTimestamp);
            intent.putExtra("SELECTED_YEAR", currentYear);
            intent.putExtra("SELECTED_MONTH", currentMonth);
            intent.putExtra("SELECTED_DAY", currentDay);
            startActivity(intent);
        });
    }

    /**
     * 加载当日日记，并自动生成AI回复
     */
    private void loadDiaryByDate(int year, int month, int day) {
        executor.execute(() -> {
            List<MoodEntry> allDiary = db.moodEntryDao().getMoodEntriesByUserId(userId);
            dayDiaryList.clear();

            // 使用 GregorianCalendar 构造函数直接创建，避免 Calendar.getInstance() 残留值干扰日期范围计算
            java.util.GregorianCalendar startGCal = new java.util.GregorianCalendar(year, month, day, 0, 0, 0);
            startGCal.set(java.util.Calendar.MILLISECOND, 0);
            long startTime = startGCal.getTimeInMillis();

            java.util.GregorianCalendar endGCal = new java.util.GregorianCalendar(year, month, day, 23, 59, 59);
            endGCal.set(java.util.Calendar.MILLISECOND, 999);
            long endTime = endGCal.getTimeInMillis();

            // 筛选当天日记
            for (MoodEntry entry : allDiary) {
                long time = entry.getTimestamp();
                if (time >= startTime && time <= endTime) {
                    dayDiaryList.add(entry);
                }
            }

            // 更新UI
            mainHandler.post(() -> {
                adapter = new DiaryAdapter(CalendarActivity.this, dayDiaryList);
                lvDayDiary.setAdapter(adapter);
                if (dayDiaryList.isEmpty()) {
                    Toast.makeText(CalendarActivity.this, "该日期暂无日记", Toast.LENGTH_SHORT).show();
                    tvAiReply.setVisibility(View.GONE);
                    return;
                }
                // 调用AI生成回复
                generateAiReply(dayDiaryList);
            });
        });
    }

    /** 与首页弹窗共用日记治愈 AI，不与 AiChatActivity 聊天混用 */
    private void generateAiReply(List<MoodEntry> diaryList) {
        // 优化：优先使用已缓存的 AI 治愈话语
        // 从最新的一条日记开始检查是否有缓存
        MoodEntry latestEntry = null;
        String cachedQuote = null;
        for (int i = diaryList.size() - 1; i >= 0; i--) {
            MoodEntry e = diaryList.get(i);
            if (e.getAiQuote() != null && !e.getAiQuote().isEmpty()) {
                cachedQuote = e.getAiQuote();
                break;
            }
            if (latestEntry == null) {
                latestEntry = e;
            }
        }
        if (latestEntry == null && !diaryList.isEmpty()) {
            latestEntry = diaryList.get(diaryList.size() - 1);
        }

        if (cachedQuote != null) {
            tvAiReply.setVisibility(View.VISIBLE);
            tvAiReply.setText("AI 治愈共鸣：\u201c" + cachedQuote + "\u201d");
            return;
        }

        // 无缓存，发起 AI 请求
        final MoodEntry entryToSave = latestEntry;
        AiHealingHelper.requestHealingForDiary(executor, apiClient, diaryList, new AiHealingHelper.Callback() {
            @Override
            public void onSuccess(String reply) {
                tvAiReply.setVisibility(View.VISIBLE);
                tvAiReply.setText("AI 治愈共鸣：\u201c" + reply + "\u201d");
                // 保存到数据库，下次打开直接显示
                if (entryToSave != null) {
                    entryToSave.setAiQuote(reply);
                    executor.execute(() -> {
                        db.moodEntryDao().updateMoodEntry(entryToSave);
                    });
                }
            }

            @Override
            public void onError(String message) {
                tvAiReply.setVisibility(View.VISIBLE);
                tvAiReply.setText(message);
            }
        });
    }

    /**
     * 日记适配器（完全适配你原有 MoodEntry）
     */
    private class DiaryAdapter extends BaseAdapter {
        private final Context context;
        private final List<MoodEntry> list;

        public DiaryAdapter(Context context, List<MoodEntry> list) {
            this.context = context;
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return list.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_diary, parent, false);
            }

            MoodEntry entry = list.get(position);
            TextView tvContent = convertView.findViewById(R.id.tv_content);
            TextView tvTime = convertView.findViewById(R.id.tv_time);
            android.widget.ImageView ivThumb = convertView.findViewById(R.id.iv_thumb);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);
            Button btnEdit = convertView.findViewById(R.id.btn_edit);

            // 使用你原有 getText() / getFormattedDate()
            tvContent.setText(entry.getText());
            tvTime.setText(entry.getFormattedDate());

            String imgPath = entry.getImagePath();
            if (imgPath != null && !imgPath.isEmpty()) {
                ivThumb.setVisibility(View.VISIBLE);
                try {
                    ivThumb.setImageURI(Uri.parse("file://" + imgPath));
                } catch (Exception e) {
                    ivThumb.setVisibility(View.GONE);
                }
            } else {
                ivThumb.setVisibility(View.GONE);
            }

            // 删除按钮
            btnDelete.setOnClickListener(v -> executor.execute(() -> {
                db.moodEntryDao().deleteMoodEntry(entry.getId());
                loadDiaryByDate(currentYear, currentMonth, currentDay);
            }));

            // 编辑按钮：替换成你项目真实的编辑Activity
            btnEdit.setOnClickListener(v -> {
                // 请自行替换为你项目里的编辑页类名
//                Intent intent = new Intent(context, 你的编辑Activity.class);
//                intent.putExtra("DIARY_ID", entry.getId());
//                context.startActivity(intent);
            });

            // 条目点击：替换成你项目真实的详情Activity
            convertView.setOnClickListener(v -> {
                // 请自行替换为你项目里的详情页类名
//                Intent intent = new Intent(context, 你的详情Activity.class);
//                intent.putExtra("DIARY_ID", entry.getId());
//                context.startActivity(intent);
            });

            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}