package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.utils.ThemeHelper;

public class ThemeSettingsActivity extends AppCompatActivity {

    private LinearLayout cardWarm;
    private LinearLayout cardDark;
    private ImageView ivCheckWarm;
    private ImageView ivCheckDark;
    private TextView tvPreviewBg;
    private TextView tvPreviewPrimary;
    private TextView tvPreviewCard;
    private TextView tvPreviewAccent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_settings);

        ImageView btnBack = findViewById(R.id.btn_back);
        cardWarm = findViewById(R.id.card_warm);
        cardDark = findViewById(R.id.card_dark);
        ivCheckWarm = findViewById(R.id.iv_check_warm);
        ivCheckDark = findViewById(R.id.iv_check_dark);
        tvPreviewBg = findViewById(R.id.tv_preview_bg);
        tvPreviewPrimary = findViewById(R.id.tv_preview_primary);
        tvPreviewCard = findViewById(R.id.tv_preview_card);
        tvPreviewAccent = findViewById(R.id.tv_preview_accent);

        btnBack.setOnClickListener(v -> finish());

        cardWarm.setOnClickListener(v -> selectTheme(ThemeHelper.THEME_WARM));
        cardDark.setOnClickListener(v -> selectTheme(ThemeHelper.THEME_DARK));

        loadTheme();
    }

    private void selectTheme(String theme) {
        String current = ThemeHelper.getCurrentTheme(this);
        if (current.equals(theme)) {
            return; // 已经是当前主题，不重复切换
        }

        // 保存偏好并立即应用主题
        ThemeHelper.saveTheme(this, theme);

        // 重建整个task栈以应用新主题到所有Activity
        long userId = getIntent().getLongExtra("USER_ID", -1);
        if (userId == -1) {
            com.hearttrace.mooddiary.utils.PrefManager pm =
                    new com.hearttrace.mooddiary.utils.PrefManager(this);
            userId = pm.getUserId();
        }

        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (userId != -1) {
            intent.putExtra("USER_ID", userId);
        }
        startActivity(intent);
        finish();
    }

    private void loadTheme() {
        boolean isWarm = !ThemeHelper.isDarkTheme(this);

        // 更新选中状态
        ivCheckWarm.setVisibility(isWarm ? ImageView.VISIBLE : ImageView.GONE);
        ivCheckDark.setVisibility(isWarm ? ImageView.GONE : ImageView.VISIBLE);

        // 更新卡片边框
        cardWarm.setBackgroundResource(isWarm ? R.drawable.bg_theme_card_selected : R.drawable.bg_theme_card);
        cardDark.setBackgroundResource(isWarm ? R.drawable.bg_theme_card : R.drawable.bg_theme_card_selected);

        // 更新预览颜色
        if (isWarm) {
            tvPreviewBg.setBackgroundColor(0xFFF7F9FC);
            tvPreviewPrimary.setBackgroundColor(0xFF5B9A9E);
            tvPreviewCard.setBackgroundColor(0xFFFFFFFF);
            tvPreviewAccent.setBackgroundColor(0xFF8FB996);
        } else {
            tvPreviewBg.setBackgroundColor(0xFF0F1118);
            tvPreviewPrimary.setBackgroundColor(0xFF5B9A9E);
            tvPreviewCard.setBackgroundColor(0xFF1A1D28);
            tvPreviewAccent.setBackgroundColor(0xFF7AAA85);
        }
    }
}
