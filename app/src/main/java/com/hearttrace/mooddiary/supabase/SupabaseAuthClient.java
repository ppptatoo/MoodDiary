package com.hearttrace.mooddiary.supabase;

import android.util.Log;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase 用户认证客户端
 * 负责处理：注册、登录、登出、获取当前用户
 */
public class SupabaseAuthClient {

    private static final String TAG = "SupabaseAuth";
    private static final MediaType JSON = MediaType.parse("application/json");

    private final String baseUrl;
    private final String anonKey;
    private String accessToken;

    public SupabaseAuthClient(String baseUrl, String anonKey) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
    }

    /** 设置已登录的 token（App 启动时从本地恢复会话） */
    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    // ==================== 注册 ====================

    /**
     * 注册新用户
     * @return AuthResult 包含用户 UUID 和 access token
     *         注意：如果 Supabase 开启了邮箱确认，accessToken 可能为 null
     */
    public AuthResult signUp(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);

        String url = baseUrl + "/auth/v1/signup";
        Response response = post(url, body.toString());

        String respStr = response.body() != null ? response.body().string() : "";
        response.close();
        Log.d(TAG, "注册返回: " + respStr);

        if (!response.isSuccessful()) {
            JSONObject errJson = new JSONObject(respStr);
            String msg = errJson.optString("msg", errJson.optString("message", "注册失败"));
            throw new Exception(msg);
        }

        // 兼容开启邮箱确认的场景：signup 成功时可能没有 access_token
        JSONObject json = new JSONObject(respStr);
        JSONObject user = json.optJSONObject("user");
        if (user == null) {
            throw new Exception("注册响应格式异常，缺少 user 字段");
        }

        AuthResult result = new AuthResult();
        result.userId = user.getString("id");
        result.accessToken = json.optString("access_token", null); // 开启确认邮箱时可能为 null
        result.email = user.optString("email", "");
        return result;
    }

    // ==================== 登录 ====================

    /**
     * 邮箱+密码登录
     * @return AuthResult 包含用户 UUID 和 access token
     */
    public AuthResult signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);

        String url = baseUrl + "/auth/v1/token?grant_type=password";
        Response response = post(url, body.toString());

        String respStr = response.body() != null ? response.body().string() : "";
        response.close();
        Log.d(TAG, "登录返回: " + respStr);

        if (!response.isSuccessful()) {
            JSONObject errJson = new JSONObject(respStr);
            String msg = errJson.optString("error_description",
                    errJson.optString("msg", "登录失败"));
            throw new Exception(msg);
        }

        AuthResult result = parseAuthResult(respStr);
        this.accessToken = result.accessToken;
        return result;
    }

    // ==================== 登出 ====================

    /**
     * 登出当前用户
     */
    public void signOut() throws Exception {
        if (accessToken == null) return;

        String url = baseUrl + "/auth/v1/logout";
        Response response = postWithAuth(url, "{}");
        response.close();

        this.accessToken = null;
        Log.d(TAG, "已登出");
    }

    // ==================== 修改密码 ====================

    /**
     * 修改当前用户密码
     * 注意：需要用户已登录且有有效的 accessToken
     */
    public void updatePassword(String newPassword) throws Exception {
        if (accessToken == null) throw new Exception("未登录");

        JSONObject body = new JSONObject();
        body.put("password", newPassword);

        String url = baseUrl + "/auth/v1/user";
        Response response = put(url, body.toString());
        response.close();
        Log.d(TAG, "修改密码: success");

        if (!response.isSuccessful()) {
            throw new Exception("修改密码失败: " + response.code());
        }
    }

    // ==================== 获取当前用户 ====================

    /**
     * 获取当前登录用户信息
     */
    public JSONObject getCurrentUser() throws Exception {
        if (accessToken == null) throw new Exception("未登录");

        String url = baseUrl + "/auth/v1/user";
        Response response = get(url);

        String respStr = response.body() != null ? response.body().string() : "";
        response.close();

        if (!response.isSuccessful()) {
            throw new Exception("获取用户信息失败");
        }

        return new JSONObject(respStr);
    }

    // ==================== 内部方法 ====================

    private OkHttpClient getClient() {
        return DoubaoHttpHelper.getClient();
    }

    private Response post(String url, String jsonBody) throws Exception {
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + (accessToken != null ? accessToken : anonKey))
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        return client.newCall(request).execute();
    }

    private Response postWithAuth(String url, String jsonBody) throws Exception {
        if (accessToken == null) throw new Exception("未登录");
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        return client.newCall(request).execute();
    }

    private Response get(String url) throws Exception {
        if (accessToken == null) throw new Exception("未登录");
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();
        return client.newCall(request).execute();
    }

    private Response put(String url, String jsonBody) throws Exception {
        if (accessToken == null) throw new Exception("未登录");
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .put(RequestBody.create(jsonBody, JSON))
                .build();
        return client.newCall(request).execute();
    }

    // ==================== 解析返回 ====================

    private AuthResult parseAuthResult(String jsonStr) throws Exception {
        JSONObject json = new JSONObject(jsonStr);
        String token = json.getString("access_token");
        String userId = json.getJSONObject("user").getString("id");

        AuthResult result = new AuthResult();
        result.userId = userId;
        result.accessToken = token;
        result.email = json.getJSONObject("user").optString("email", "");

        return result;
    }

    // ==================== 内部类 ====================

    public static class AuthResult {
        public String userId;       // Supabase UUID
        public String accessToken;  // JWT Token
        public String email;
    }
}
