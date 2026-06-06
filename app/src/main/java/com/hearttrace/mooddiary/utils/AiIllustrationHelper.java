package com.hearttrace.mooddiary.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.supabase.SupabaseStorageClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 历史日记：无用户配图时，根据日记生成 AI 治愈插画并缓存到本地。
 */
public final class AiIllustrationHelper {

    private static final String STYLE_PROMPT =
            "根据用户日记内容创作一幅日记配图，画面风格为简约手绘治愈插画，"
                    + "色调柔和温暖，光线舒缓明亮，构图简洁干净，氛围静谧温柔，"
                    + "整体画面清新素雅，适配移动端日记页面展示。";

    public interface Callback {
        void onSuccess(String localImagePath);

        void onError(String message);
    }

    private AiIllustrationHelper() {
    }

    public static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), "ai_illustrations");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File cacheFile(Context context, long entryId) {
        return new File(cacheDir(context), entryId + ".png");
    }

    public static String getCachedPath(Context context, long entryId) {
        File f = cacheFile(context, entryId);
        return f.exists() && f.length() > 0 ? f.getAbsolutePath() : null;
    }

    /**
     * 为日记生成 AI 插画。
     * 如果提供了 storageClient，会将图片上传到 Supabase Storage 并返回公开 URL，
     * 这样重装 APK 后图片不会丢失。
     */
    public static void requestIllustration(
            ExecutorService executor,
            DoubaoApiClient apiClient,
            Context context,
            MoodEntry entry,
            SupabaseStorageClient storageClient,
            Callback callback) {

        String cached = getCachedPath(context, entry.getId());
        if (cached != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(cached));
            return;
        }

        String mood = MoodUiHelper.normalizeMood(entry.getUserEmotionLabel());
        String text = entry.getText() != null ? entry.getText().trim() : "";
        if (text.isEmpty()) {
            text = "平静的日常片刻";
        }
        if (text.length() > 200) {
            text = text.substring(0, 200) + "…";
        }

        String prompt = STYLE_PROMPT
                + "\n日记心情：" + mood
                + "\n日记内容为：" + text;

        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                String imageUrl = apiClient.generateImageUrl(prompt);
                String localPath = downloadToCache(context, imageUrl, entry.getId());

                // 如果提供了 Storage 客户端，上传到云端获取长期公开 URL
                if (storageClient != null) {
                    try {
                        String remotePath = "ai_illustrations/entry_" + entry.getId() + "_" + entry.getTimestamp() + ".png";
                        String publicUrl = storageClient.uploadImage(new File(localPath), remotePath);
                        mainHandler.post(() -> callback.onSuccess(publicUrl));
                        return;
                    } catch (Exception uploadEx) {
                        android.util.Log.w("AiIllustration", "上传 Storage 失败，降级使用本地路径", uploadEx);
                        // 上传失败时降级使用本地路径
                    }
                }

                mainHandler.post(() -> callback.onSuccess(localPath));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError("配图生成失败"));
            }
        });
    }

    private static String downloadToCache(Context context, String imageUrl, long entryId)
            throws Exception {
        OkHttpClient client = DoubaoApiClient.newHttpClient();
        Request request = new Request.Builder().url(imageUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("下载插画失败");
            }
            File out = cacheFile(context, entryId);
            try (InputStream in = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            }
            return out.getAbsolutePath();
        }
    }
}
