package com.hearttrace.mooddiary.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.hearttrace.mooddiary.R;
import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.User;
import com.hearttrace.mooddiary.supabase.SupabaseAuthClient;
import com.hearttrace.mooddiary.supabase.SupabaseConfig;
import com.hearttrace.mooddiary.supabase.SupabaseDataClient;
import com.hearttrace.mooddiary.utils.BitmapUtils;
import com.hearttrace.mooddiary.utils.PrefManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileDetailActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1001;
    private static final int REQUEST_CAMERA = 1002;
    private Uri cameraImageUri;

    private PrefManager prefManager;
    private AppDatabase db;
    private ExecutorService executor;
    private EditText etNickname;
    private EditText etEmail;
    private EditText etSignature;
    private EditText etOldPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private ImageView ivAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_detail);

        prefManager = new PrefManager(this);
        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();

        ImageView btnBack = findViewById(R.id.btn_back);
        ivAvatar = findViewById(R.id.iv_avatar);
        etNickname = findViewById(R.id.et_nickname);
        etEmail = findViewById(R.id.et_email);
        etSignature = findViewById(R.id.et_signature);
        etOldPassword = findViewById(R.id.et_old_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        TextView btnSave = findViewById(R.id.btn_save);

        btnBack.setOnClickListener(v -> finish());

        // 点击头像区域更换头像
        ivAvatar.setOnClickListener(v -> showImagePickerDialog());
        findViewById(R.id.tv_change_avatar_hint).setOnClickListener(v -> showImagePickerDialog());

        loadUserInfo();
        loadAvatar();

        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String nickname = etNickname.getText().toString().trim();
        if (nickname.isEmpty()) {
            Toast.makeText(this, "用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId = prefManager.getUserId();
        String email = etEmail.getText().toString().trim();
        String signature = etSignature.getText().toString().trim();

        // 保存基本信息到PrefManager
        prefManager.saveLogin(userId, nickname);
        prefManager.saveEmail(email);
        prefManager.saveSignature(signature);

        // 同步更新数据库中的用户名
        executor.execute(() -> {
            db.userDao().updateUsername(userId, nickname);
        });

        // 同步昵称和签名到 Supabase 云端 user_profiles
        if (prefManager.isSupabaseLoggedIn() && !SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
            executor.execute(() -> {
                try {
                    SupabaseDataClient dataClient = new SupabaseDataClient(
                            SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY,
                            prefManager.getSupabaseToken());
                    dataClient.upsertUserProfile(prefManager.getSupabaseUserUuid(),
                            nickname, signature, null);
                    Log.d("ProfileDetail", "云端资料同步成功");
                } catch (Exception e) {
                    Log.w("ProfileDetail", "云端资料同步失败: " + e.getMessage());
                }
            });
        }

        // 处理密码修改
        String oldPassword = etOldPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (!oldPassword.isEmpty() || !newPassword.isEmpty() || !confirmPassword.isEmpty()) {
            if (oldPassword.isEmpty()) {
                Toast.makeText(this, "请输入旧密码", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPassword.length() < 6) {
                Toast.makeText(this, "新密码长度不能少于6位", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            executor.execute(() -> {
                // 从数据库获取当前用户（避免用户名修改后缓存不一致）
                User currentUser = db.userDao().getUserById(userId);
                if (currentUser == null) {
                    runOnUiThread(() -> Toast.makeText(this, "用户信息异常", Toast.LENGTH_SHORT).show());
                    return;
                }
                User user = db.userDao().login(currentUser.getUsername(), oldPassword);
                runOnUiThread(() -> {
                    if (user == null) {
                        Toast.makeText(this, "旧密码错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    executor.execute(() -> {
                        // 更新 Room 本地密码
                        db.userDao().updatePassword(userId, newPassword);

                        // 同步更新 Supabase 云端密码
                        if (prefManager.isSupabaseLoggedIn() && !SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
                            try {
                                SupabaseAuthClient authClient = new SupabaseAuthClient(
                                        SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY);
                                authClient.setAccessToken(prefManager.getSupabaseToken());
                                authClient.updatePassword(newPassword);
                                Log.d("ProfileDetail", "云端密码更新成功");
                            } catch (Exception e) {
                                Log.w("ProfileDetail", "云端密码更新失败: " + e.getMessage());
                            }
                        }

                        runOnUiThread(() -> {
                            clearPasswordFields();
                            Toast.makeText(this, "保存成功，密码已更新", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                });
            });
        } else {
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void clearPasswordFields() {
        etOldPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
    }

    /**
     * 自动保存昵称、邮箱和签名，防止用户未点击保存就返回导致数据丢失
     */
    private void autoSaveBasicInfo() {
        String nickname = etNickname.getText().toString().trim();
        if (nickname.isEmpty()) return;

        long userId = prefManager.getUserId();
        prefManager.saveLogin(userId, nickname);
        prefManager.saveEmail(etEmail.getText().toString().trim());
        prefManager.saveSignature(etSignature.getText().toString().trim());

        executor.execute(() -> {
            db.userDao().updateUsername(userId, nickname);

            // 同步到 Supabase 云端
            if (prefManager.isSupabaseLoggedIn() && !SupabaseConfig.SUPABASE_URL.contains("YOUR_PROJECT_ID")) {
                try {
                    SupabaseDataClient dataClient = new SupabaseDataClient(
                            SupabaseConfig.SUPABASE_URL, SupabaseConfig.SUPABASE_ANON_KEY,
                            prefManager.getSupabaseToken());
                    dataClient.upsertUserProfile(prefManager.getSupabaseUserUuid(),
                            nickname, prefManager.getSignature(), null);
                } catch (Exception e) {
                    Log.w("ProfileDetail", "自动保存云端失败: " + e.getMessage());
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 页面离开（包括点击返回键）时自动保存昵称、邮箱、签名
        autoSaveBasicInfo();
    }

    private void showImagePickerDialog() {
        String[] options = {"从相册选择", "拍照"};
        new AlertDialog.Builder(this)
                .setTitle("更换头像")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        intent.setType("image/*");
                        startActivityForResult(intent, REQUEST_IMAGE_PICK);
                    } else {
                        // 创建临时文件用于相机拍照
                        File photoFile = new File(getCacheDir(), "camera_avatar_" + System.currentTimeMillis() + ".jpg");
                        cameraImageUri = FileProvider.getUriForFile(this,
                                getPackageName() + ".fileprovider", photoFile);
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivityForResult(intent, REQUEST_CAMERA);
                        } else {
                            Toast.makeText(this, "相机不可用", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        Uri imageUri = null;
        if (requestCode == REQUEST_IMAGE_PICK && data != null) {
            imageUri = data.getData();
        } else if (requestCode == REQUEST_CAMERA) {
            imageUri = cameraImageUri;
            // 如果 cameraImageUri 为空，尝试从 intent extras 获取缩略图
            if (imageUri == null && data != null) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap bitmap = (Bitmap) extras.get("data");
                    if (bitmap != null) {
                        String path = saveBitmapToInternalStorage(bitmap);
                        if (path != null) {
                            setAvatarFromFile(path);
                            Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                }
            }
        }

        if (imageUri != null) {
            // 从 URI 读取并保存到内部存储
            String savedPath = saveUriToInternalStorage(imageUri);
            if (savedPath != null) {
                setAvatarFromFile(savedPath);
                Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 将 Bitmap 保存到内部存储，返回文件路径
     */
    private String saveBitmapToInternalStorage(Bitmap bitmap) {
        try {
            File avatarFile = getAvatarFile();
            FileOutputStream fos = new FileOutputStream(avatarFile);
            // 裁剪为正方形并压缩
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            int x = (bitmap.getWidth() - size) / 2;
            int y = (bitmap.getHeight() - size) / 2;
            Bitmap cropped = Bitmap.createBitmap(bitmap, x, y, size, size);
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
            fos.close();
            if (cropped != bitmap) cropped.recycle();
            return avatarFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将 Content URI 对应的图片保存到内部存储，返回文件路径
     */
    private String saveUriToInternalStorage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) return null;
            String path = saveBitmapToInternalStorage(bitmap);
            bitmap.recycle();
            return path;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取头像文件
     */
    private File getAvatarFile() {
        File dir = new File(getFilesDir(), "avatars");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "avatar_" + prefManager.getUserId() + ".jpg");
    }

    /**
     * 从文件加载头像并清除 tint，应用圆形裁剪
     */
    private void setAvatarFromFile(String path) {
        Bitmap raw = BitmapFactory.decodeFile(path);
        if (raw != null) {
            Bitmap circle = BitmapUtils.toCircleBitmap(raw);
            ivAvatar.setImageBitmap(circle);
            raw.recycle();
        } else {
            ivAvatar.setImageBitmap(null);
        }
        ivAvatar.setImageTintList(null); // 清除 XML 中的白色 tint
        prefManager.saveAvatarUri(path); // 保存本地文件路径
    }

    private void loadUserInfo() {
        String username = prefManager.getUsername();
        etNickname.setText(username);
        etEmail.setText(prefManager.getEmail());
        etSignature.setText(prefManager.getSignature());
    }

    private void loadAvatar() {
        String avatarPath = prefManager.getAvatarUri();
        File avatarFile = getAvatarFile();
        // 优先从文件加载，并应用圆形裁剪
        if (avatarFile.exists()) {
            Bitmap raw = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
            if (raw != null) {
                Bitmap circle = BitmapUtils.toCircleBitmap(raw);
                ivAvatar.setImageBitmap(circle);
                raw.recycle();
            }
            ivAvatar.setImageTintList(null); // 清除 tint
        } else if (!avatarPath.isEmpty()) {
            // 兼容旧版：如果保存的是 content URI，尝试加载
            try {
                Uri uri = Uri.parse(avatarPath);
                if ("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())) {
                    ivAvatar.setImageURI(uri);
                    ivAvatar.setImageTintList(null);
                }
            } catch (Exception e) {
                // 加载失败，保持默认占位图
            }
        }
        // 没有头像时保持 XML 默认的占位图（白色 tint 正常显示）
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}