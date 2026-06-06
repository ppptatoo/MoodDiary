package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;

public class PrivacyPolicyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView tvContactEmail = findViewById(R.id.tv_contact_email);

        btnBack.setOnClickListener(v -> finish());

        tvContactEmail.setOnClickListener(v -> {
            String email = "zengziyii1021@163.com";
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + email));
            intent.putExtra(Intent.EXTRA_SUBJECT, "隐私政策相关咨询");
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "未找到邮件应用", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
