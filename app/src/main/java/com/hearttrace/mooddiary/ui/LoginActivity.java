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
import com.hearttrace.mooddiary.utils.PrefManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private TextView btnLogin, btnRegister;
    private AppDatabase db;
    private ExecutorService executor;
    private PrefManager pref;
    private SupabaseAuthClient authClient;
    private boolean isClicking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 初始化控件和工具类
        pref = new PrefManager(this);
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        authClient = new SupabaseAuthClient(SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);

        // 自动登录判断
        if (pref.isLoggedIn()) {
            long savedUserId = pref.getUserId();
            if (savedUserId > 0) {
                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                intent.putExtra("USER_ID", savedUserId);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return;
            }
        }

        btnLogin.setOnClickListener(v -> login());
        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void login() {
        if (isClicking) return;
        isClicking = true;

        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "用户名和密码不能为空", Toast.LENGTH_SHORT).show();
            isClicking = false;
            return;
        }

        executor.execute(() -> {
            // 1️⃣ 先尝试 Supabase 云端登录
            final boolean[] cloudLoginSuccess = new boolean[]{false};
            if (SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
                // 未配置 Supabase，跳过云端登录
                android.util.Log.d("LoginActivity", "Supabase 未配置，跳过云端登录");
            } else {
                try {
                    String email = username.contains("@") ? username : username + "@example.com";
                    SupabaseAuthClient.AuthResult result = authClient.signIn(email, password);
                    // 云端登录成功，保存 token 和 UUID
                    pref.saveSupabaseToken(result.accessToken);
                    pref.saveSupabaseUserUuid(result.userId);
                    cloudLoginSuccess[0] = true;
                    android.util.Log.d("LoginActivity", "Supabase 云端登录成功: " + result.userId);
                } catch (Exception e) {
                    android.util.Log.w("LoginActivity", "Supabase 登录失败: " + e.getMessage());
                }
            }

            // 2️⃣ 本地 Room 登录（兜底）
            User user = db.userDao().login(username, password);

            runOnUiThread(() -> {
                if (user != null) {
                    // 本地登录成功
                    pref.saveLogin(user.getId(), user.getUsername());
                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    intent.putExtra("USER_ID", user.getId());
                    startActivity(intent);
                    finish();
                } else if (cloudLoginSuccess[0]) {
                    // 云端登录成功但本地没有账号 → 自动创建本地账号
                    executor.execute(() -> {
                        User newUser = new User(username, password, System.currentTimeMillis());
                        long localId = db.userDao().insertUser(newUser);
                        runOnUiThread(() -> {
                            pref.saveLogin(localId, username);
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            intent.putExtra("USER_ID", localId);
                            startActivity(intent);
                            finish();
                        });
                    });
                } else {
                    Toast.makeText(LoginActivity.this, "用户名或密码错误", Toast.LENGTH_SHORT).show();
                    isClicking = false;
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}