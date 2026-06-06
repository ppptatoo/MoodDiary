package com.hearttrace.mooddiary.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {
    private static final String NAME = "user_pref";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_SIGNATURE = "signature";
    private static final String KEY_AVATAR_URI = "avatar_uri";
    private static final String KEY_SUPABASE_TOKEN = "supabase_token";
    private static final String KEY_SUPABASE_USER_UUID = "supabase_user_uuid";

    private final SharedPreferences sp;

    public PrefManager(Context context) {
        sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // 保存登录信息
    public void saveLogin(long userId, String username) {
        sp.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    // 获取用户ID
    public long getUserId() {
        return sp.getLong(KEY_USER_ID, -1);
    }

    // 获取用户名
    public String getUsername() {
        return sp.getString(KEY_USERNAME, "");
    }

    // 邮箱
    public void saveEmail(String email) {
        sp.edit().putString(KEY_EMAIL, email).apply();
    }

    public String getEmail() {
        return sp.getString(KEY_EMAIL, "");
    }

    // 个性签名
    public void saveSignature(String signature) {
        sp.edit().putString(KEY_SIGNATURE, signature).apply();
    }

    public String getSignature() {
        return sp.getString(KEY_SIGNATURE, "记录生活的每一刻");
    }

    // 头像URI
    public void saveAvatarUri(String uri) {
        sp.edit().putString(KEY_AVATAR_URI, uri).apply();
    }

    public String getAvatarUri() {
        return sp.getString(KEY_AVATAR_URI, "");
    }

    // === Supabase 云端认证信息 ===

    /** 保存 Supabase Access Token */
    public void saveSupabaseToken(String token) {
        sp.edit().putString(KEY_SUPABASE_TOKEN, token).apply();
    }

    /** 获取 Supabase Access Token */
    public String getSupabaseToken() {
        return sp.getString(KEY_SUPABASE_TOKEN, "");
    }

    /** 保存 Supabase 用户 UUID */
    public void saveSupabaseUserUuid(String uuid) {
        sp.edit().putString(KEY_SUPABASE_USER_UUID, uuid).apply();
    }

    /** 获取 Supabase 用户 UUID */
    public String getSupabaseUserUuid() {
        return sp.getString(KEY_SUPABASE_USER_UUID, "");
    }

    /** 是否已登录 Supabase（有 token 且有效） */
    public boolean isSupabaseLoggedIn() {
        return !getSupabaseToken().isEmpty() && !getSupabaseUserUuid().isEmpty();
    }

    // 清除登录（退出用）
    public void clearLogin() {
        sp.edit().clear().apply();
    }

    // 是否已经登录
    public boolean isLoggedIn() {
        return isSupabaseLoggedIn() || getUserId() != -1;
    }

    public void clearUserData() {
        sp.edit().clear().apply();
    }
}