package com.hearttrace.mooddiary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.User;
import com.hearttrace.mooddiary.supabase.SupabaseAuthClient;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import com.hearttrace.mooddiary.utils.PrefManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etConfirmPassword;
    private TextView btnRegister, btnBackLogin;
    private AppDatabase db;
    private ExecutorService executor;
    private SupabaseAuthClient authClient;
    private PrefManager pref;
    private boolean isClicking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        pref = new PrefManager(this);
        authClient = new SupabaseAuthClient(SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        btnBackLogin = findViewById(R.id.btn_back_login);

        btnRegister.setOnClickListener(v -> register());
        btnBackLogin.setOnClickListener(v -> finish());
    }

    private void register() {
        if (isClicking) return;
        isClicking = true;

        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "用户名和密码不能为空", Toast.LENGTH_SHORT).show();
            isClicking = false;
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "密码长度不能少于6位", Toast.LENGTH_SHORT).show();
            isClicking = false;
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show();
            isClicking = false;
            return;
        }

        executor.execute(() -> {
            // 本地检查用户名是否已存在
            User existing = db.userDao().findUserByUsername(username);
            if (existing != null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show();
                    isClicking = false;
                });
                return;
            }

            // 云端注册（Supabase）
            String cloudError = null;
            String supabaseUuid = null;
            String supabaseToken = null;
            if (!SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
                try {
                    String email = username.contains("@") ? username : username + "@example.com";
                    SupabaseAuthClient.AuthResult result = authClient.signUp(email, password);
                    supabaseUuid = result.userId;
                    supabaseToken = result.accessToken;
                    android.util.Log.d("Register", "Supabase 注册成功: " + supabaseUuid);

                    // 云端注册成功后，同步创建用户资料
                    try {
                        SupabaseDataClient dataClient = new SupabaseDataClient(
                                SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY, supabaseToken);
                        dataClient.upsertUserProfile(supabaseUuid, username, "记录生活的每一刻", "");
                    } catch (Exception e) {
                        android.util.Log.w("Register", "创建用户资料失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    cloudError = e.getMessage();
                    android.util.Log.w("Register", "Supabase 注册失败: " + cloudError);
                }
            }

            // 本地 Room 注册（始终执行，保证离线可用）
            long userId = db.userDao().insertUser(new User(username, password, System.currentTimeMillis()));

            // 保存本地登录信息（云端注册也保存，避免自动登录时卡死）
            if (userId > 0) {
                pref.saveLogin(userId, username);
            }

            // 保存 Supabase 信息
            if (supabaseUuid != null) {
                pref.saveSupabaseUserUuid(supabaseUuid);
                pref.saveSupabaseToken(supabaseToken);
            }

            String finalCloudError = cloudError;
            runOnUiThread(() -> {
                if (userId > 0) {
                    String msg = "注册成功，请登录";
                    if (finalCloudError != null) {
                        msg += "（云端暂不可用）";
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "注册失败", Toast.LENGTH_SHORT).show();
                }
                isClicking = false;
            });
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
