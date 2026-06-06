package com.hearttrace.mooddiary.supabase;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase 数据库操作客户端（PostgREST API）
 * 负责日记和用户资料的云端 CRUD
 */
public class SupabaseDataClient {

    private static final String TAG = "SupabaseData";
    private static final MediaType JSON = MediaType.parse("application/json");

    private final String baseUrl;
    private final String anonKey;
    private final String accessToken;

    public SupabaseDataClient(String baseUrl, String anonKey, String accessToken) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
        this.accessToken = accessToken;
    }

    // ==================== 日记 CRUD ====================

    /**
     * 向云端插入一条日记
     * @return 返回 Supabase 生成的记录 ID
     */
    public long insertMoodEntry(SupabaseMoodEntry entry) throws Exception {
        JSONObject body = new JSONObject();
        body.put("user_id", entry.userId);
        body.put("timestamp", entry.timestamp);
        body.put("update_time", entry.updateTime);
        body.put("text", entry.text);
        body.put("emotion_label", entry.emotionLabel);
        body.put("image_path", entry.imagePath);
        body.put("is_favorite", entry.isFavorite);
        body.put("ai_image_path", entry.aiImagePath);
        body.put("ai_quote", entry.aiQuote);

        String url = baseUrl + "/rest/v1/mood_entries";
        Response response = post(url, body.toString(), "return=representation");

        String respStr = response.body() != null ? response.body().string() : "";
        response.close();
        Log.d(TAG, "插入日记返回: " + respStr);

        if (!response.isSuccessful()) {
            throw new Exception("云端插入日记失败: " + response.code());
        }

        JSONArray arr = new JSONArray(respStr);
        if (arr.length() > 0) {
            return arr.getJSONObject(0).getLong("id");
        }
        throw new Exception("云端未返回日记 ID");
    }

    /**
     * 更新云端日记
     */
    public void updateMoodEntry(long remoteId, SupabaseMoodEntry entry) throws Exception {
        JSONObject body = new JSONObject();
        body.put("text", entry.text);
        body.put("emotion_label", entry.emotionLabel);
        body.put("update_time", entry.updateTime);
        body.put("image_path", entry.imagePath);
        body.put("is_favorite", entry.isFavorite);
        body.put("ai_image_path", entry.aiImagePath);
        body.put("ai_quote", entry.aiQuote);

        String url = baseUrl + "/rest/v1/mood_entries?id=eq." + remoteId;
        Response response = patch(url, body.toString());
        response.close();
        Log.d(TAG, "更新日记: remoteId=" + remoteId + " code=" + response.code());

        if (!response.isSuccessful()) {
            throw new Exception("云端更新日记失败: " + response.code());
        }
    }

    /**
     * 删除云端日记
     */
    public void deleteMoodEntry(long remoteId) throws Exception {
        String url = baseUrl + "/rest/v1/mood_entries?id=eq." + remoteId;
        Response response = delete(url);
        response.close();
        Log.d(TAG, "删除日记: remoteId=" + remoteId + " code=" + response.code());

        if (!response.isSuccessful()) {
            throw new Exception("云端删除日记失败: " + response.code());
        }
    }

    /**
     * 获取云端该用户的所有日记
     */
    public List<SupabaseMoodEntry> getAllMoodEntries(String userId) throws Exception {
        String url = baseUrl + "/rest/v1/mood_entries"
                + "?user_id=eq." + userId
                + "&order=timestamp.desc"
                + "&limit=500";

        Response response = get(url);
        String respStr = response.body() != null ? response.body().string() : "";
        response.close();

        if (!response.isSuccessful()) {
            throw new Exception("获取云端日记失败: " + response.code());
        }

        JSONArray arr = new JSONArray(respStr);
        List<SupabaseMoodEntry> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(SupabaseMoodEntry.fromJson(arr.getJSONObject(i)));
        }
        return list;
    }

    // ==================== 用户资料 CRUD ====================

    /**
     * 创建/更新用户资料（upsert）
     */
    public void upsertUserProfile(String userId, String username, String signature, String avatarUrl) throws Exception {
        JSONObject body = new JSONObject();
        body.put("id", userId);
        body.put("username", username);
        body.put("signature", signature != null ? signature : "记录生活的每一刻");
        body.put("avatar_url", avatarUrl != null ? avatarUrl : "");

        String url = baseUrl + "/rest/v1/user_profiles";
        // 使用 upsert: 有则更新，无则插入
        Response response = post(url, body.toString(),
                "resolution=merge-duplicates");

        String respStr = response.body() != null ? response.body().string() : "";
        response.close();
        Log.d(TAG, "upsert 用户资料: " + respStr);

        if (!response.isSuccessful()) {
            throw new Exception("保存用户资料失败: " + response.code());
        }
    }

    /**
     * 获取用户资料
     */
    public JSONObject getUserProfile(String userId) throws Exception {
        String url = baseUrl + "/rest/v1/user_profiles?id=eq." + userId;
        Response response = get(url);

        String respStr = response.body() != null ? response.body().string() : "";
        response.close();

        JSONArray arr = new JSONArray(respStr);
        if (arr.length() > 0) {
            return arr.getJSONObject(0);
        }
        return null;
    }

    // ==================== HTTP 请求方法 ====================

    private OkHttpClient getClient() {
        return DoubaoHttpHelper.getClient();
    }

    private Response post(String url, String jsonBody, String preferHeader) throws Exception {
        OkHttpClient client = getClient();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(jsonBody, JSON));
        if (preferHeader != null) {
            builder.addHeader("Prefer", preferHeader);
        }
        return client.newCall(builder.build()).execute();
    }

    private Response patch(String url, String jsonBody) throws Exception {
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .patch(RequestBody.create(jsonBody, JSON))
                .build();
        return client.newCall(request).execute();
    }

    private Response delete(String url) throws Exception {
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .delete()
                .build();
        return client.newCall(request).execute();
    }

    private Response get(String url) throws Exception {
        OkHttpClient client = getClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();
        return client.newCall(request).execute();
    }

    // ==================== 数据模型 ====================

    /**
     * Supabase 日记条目结构
     */
    public static class SupabaseMoodEntry {
        public long id;              // Supabase 自增 ID
        public String userId;        // Supabase 用户 UUID
        public long timestamp;
        public long updateTime;
        public String text;
        public String emotionLabel;
        public String imagePath;
        public boolean isFavorite;
        public String aiImagePath;
        public String aiQuote;

        public static SupabaseMoodEntry fromJson(JSONObject json) {
            SupabaseMoodEntry e = new SupabaseMoodEntry();
            e.id = json.optLong("id", 0);
            e.userId = json.optString("user_id", "");
            e.timestamp = json.optLong("timestamp", 0);
            e.updateTime = json.optLong("update_time", 0);
            e.text = json.optString("text", "");
            e.emotionLabel = json.optString("emotion_label", "");
            e.imagePath = json.optString("image_path", "");
            e.isFavorite = json.optBoolean("is_favorite", false);
            e.aiImagePath = json.optString("ai_image_path", null);
            e.aiQuote = json.optString("ai_quote", null);
            return e;
        }
    }
}
