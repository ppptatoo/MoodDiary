package com.hearttrace.mooddiary.supabase;

import android.util.Log;

import com.hearttrace.mooddiary.utils.DoubaoApiClient;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase Storage 客户端：上传文件到云端存储桶，获取公开访问 URL。
 *
 * 使用说明：
 * 1. 在 Supabase 控制台 → Storage → 新建 bucket 名为 "mood-images"
 * 2. 将该 bucket 设置为 Public（公开访问）
 * 3. 在 Policies 中添加允许上传的策略（anon role 可上传）
 */
public class SupabaseStorageClient {

    private static final String TAG = "SupabaseStorage";
    private static final String BUCKET_NAME = "mood-images";
    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    private final String baseUrl;
    private final String anonKey;
    private final String accessToken;

    public SupabaseStorageClient(String baseUrl, String anonKey, String accessToken) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
        this.accessToken = accessToken;
    }

    /**
     * 上传图片文件到 Supabase Storage，返回公开访问 URL。
     *
     * @param localFile 本地图片文件
     * @param remotePath Storage 中的路径，如 "user_123/entry_456.png"
     * @return 公开访问 URL，可直接用于 Glide 加载
     */
    public String uploadImage(File localFile, String remotePath) throws Exception {
        if (localFile == null || !localFile.exists() || localFile.length() == 0) {
            throw new IllegalArgumentException("本地图片文件无效");
        }

        String url = baseUrl + "/storage/v1/object/" + BUCKET_NAME + "/" + remotePath;

        // 根据文件扩展名推断 Content-Type
        String contentType = guessContentType(localFile.getName());
        MediaType mediaType = MediaType.parse(contentType);
        if (mediaType == null) mediaType = OCTET_STREAM;

        RequestBody body = RequestBody.create(localFile, mediaType);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "true") // 允许覆盖同名文件
                .post(body)
                .build();

        OkHttpClient client = DoubaoApiClient.newHttpClient();
        try (Response response = client.newCall(request).execute()) {
            String respStr = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "上传图片返回: code=" + response.code() + " body=" + respStr);

            if (!response.isSuccessful()) {
                throw new IllegalStateException("上传图片失败 code=" + response.code() + " body=" + respStr);
            }

            // 返回公开访问 URL
            return baseUrl + "/storage/v1/object/public/" + BUCKET_NAME + "/" + remotePath;
        }
    }

    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }
}
