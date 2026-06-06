package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;

public class AboutUsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "about_prefs";
    private static final String KEY_CONTACT = "contact_email";
    private static final String KEY_GITHUB = "github_url";
    private static final String KEY_WEBSITE = "website_url";

    private SharedPreferences prefs;
    private TextView tvContact;
    private TextView tvGithub;
    private TextView tvWebsite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ImageView btnBack = findViewById(R.id.btn_back);
        tvContact = findViewById(R.id.tv_contact_value);
        tvGithub = findViewById(R.id.tv_github_value);
        tvWebsite = findViewById(R.id.tv_website_value);
        LinearLayout itemContact = findViewById(R.id.item_contact);
        LinearLayout itemGithub = findViewById(R.id.item_github);
        LinearLayout itemWebsite = findViewById(R.id.item_website);

        btnBack.setOnClickListener(v -> finish());

        loadConfig();

        itemContact.setOnClickListener(v -> openUrl("mailto:" + tvContact.getText().toString()));
        itemGithub.setOnClickListener(v -> openUrl("https://" + tvGithub.getText().toString()));
        itemWebsite.setOnClickListener(v -> openUrl("https://" + tvWebsite.getText().toString()));

        // 长按可修改（管理员功能）
        itemContact.setOnLongClickListener(v -> { showEditDialog(KEY_CONTACT, "联系我们"); return true; });
        itemGithub.setOnLongClickListener(v -> { showEditDialog(KEY_GITHUB, "开源地址"); return true; });
        itemWebsite.setOnLongClickListener(v -> { showEditDialog(KEY_WEBSITE, "官方网站"); return true; });
    }

    private void loadConfig() {
        tvContact.setText(prefs.getString(KEY_CONTACT, "support@mooddiary.app"));
        tvGithub.setText(prefs.getString(KEY_GITHUB, "github.com/mooddiary"));
        tvWebsite.setText(prefs.getString(KEY_WEBSITE, "www.mooddiary.app"));
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditDialog(String key, String title) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("修改" + title);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(prefs.getString(key, ""));
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String value = input.getText().toString().trim();
            prefs.edit().putString(key, value).apply();
            loadConfig();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
