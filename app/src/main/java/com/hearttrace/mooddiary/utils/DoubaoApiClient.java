package com.hearttrace.mooddiary.utils;

import android.util.Log;

import com.hearttrace.mooddiary.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DoubaoApiClient {
    private static final String TAG = "AI_API_LOG";
    /** 密钥通过 BuildConfig 从 local.properties 注入，不提交 Git */
    private static final String API_KEY = BuildConfig.ARK_API_KEY;
    /** glm-4-7-251222：用于首页日期文字回复、历史日记文字回复 */
    private static final String CHAT_MODEL_ID = BuildConfig.ARK_CHAT_MODEL_ID;
    /** doubao-seedream-5-0-260128：仅用于历史日记配图 */
    private static final String IMAGE_MODEL_ID = BuildConfig.ARK_IMAGE_MODEL_ID;
    private static final String API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final String IMAGE_API_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json");

    // 优化：复用 OkHttpClient 单例，避免每次请求都新建连接+TLS握手
    private static volatile OkHttpClient sharedClient;

    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static OkHttpClient newHttpClient() {
        return getSharedHttpClient();
    }

    private static OkHttpClient getSharedHttpClient() {
        if (sharedClient == null) {
            synchronized (DoubaoApiClient.class) {
                if (sharedClient == null) {
                    sharedClient = buildHttpClient();
                }
            }
        }
        return sharedClient;
    }

    private static OkHttpClient buildHttpClient() {
        try {
            final X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new X509TrustManager[]{trustManager}, new java.security.SecureRandom());
            SSLSocketFactory sslFactory = sslContext.getSocketFactory();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslFactory, trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static OkHttpClient getImageHttpClient() {
        return getSharedHttpClient();
    }

    /**
     * 发送文字对话请求。
     * 使用独立 OkHttpClient（非共享单例），避免连接池复用导致的超时问题。
     * 参数精简到最简：只传 model + messages，让模型以默认配置快速响应。
     */
    public String sendChatRequest(List<Message> msgList) throws Exception {
        Log.d(TAG, "开始组装文字请求体");
        JSONObject root = new JSONObject();
        root.put("model", CHAT_MODEL_ID);
        JSONArray messages = new JSONArray();

        for (Message item : msgList) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", item.role);
            msgObj.put("content", item.content);
            messages.put(msgObj);
        }
        root.put("messages", messages);
        String postBody = root.toString();
        Log.d(TAG, "文字请求体：" + postBody);

        // 为每次请求创建独立 OkHttpClient，不复用共享单例
        // 原因：共享单例的连接池可能在跨请求复用时产生超时
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // 0 = 不限制读超时
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)  // 整体调用最长 5 分钟
                .build();

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .post(RequestBody.create(postBody, JSON_TYPE))
                .build();

        Log.d(TAG, "开始发起文字网络请求");
        try (Response response = client.newCall(request).execute()) {
            String respStr = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "文字原始返回数据：" + respStr);

            if (!response.isSuccessful()) {
                String errBody = respStr.length() > 500 ? respStr.substring(0, 500) : respStr;
                throw new IllegalStateException("文字请求失败 code=" + response.code() + " body=" + errBody);
            }

            JSONObject respJson = new JSONObject(respStr);
            if (respJson.has("error")) {
                JSONObject err = respJson.getJSONObject("error");
                throw new IllegalStateException(err.optString("message", "文字请求失败"));
            }

            String aiReply = respJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            Log.d(TAG, "AI文字回复内容：" + aiReply);
            return aiReply;
        }
    }

    public String generateImageUrl(String prompt) throws Exception {
        JSONObject root = new JSONObject();
        root.put("model", IMAGE_MODEL_ID);
        root.put("output_format", "png");
        root.put("prompt", prompt);
        root.put("size", "2048x2048");
        root.put("watermark", false);

        String postBody = root.toString();
        Log.d(TAG, "文生图请求：" + postBody);

        OkHttpClient client = getSharedHttpClient();
        Request request = new Request.Builder()
                .url(IMAGE_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .post(RequestBody.create(postBody, JSON_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String respStr = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "文生图返回：" + respStr);

            if (!response.isSuccessful()) {
                throw new IllegalStateException("文生图失败：" + response.code());
            }

            JSONObject respJson = new JSONObject(respStr);
            if (respJson.has("error")) {
                JSONObject err = respJson.getJSONObject("error");
                throw new IllegalStateException(err.optString("message", "文生图错误"));
            }

            JSONArray data = respJson.getJSONArray("data");
            JSONObject first = data.getJSONObject(0);
            String url = first.optString("url", "");
            if (url.isEmpty()) {
                throw new IllegalStateException("文生图未返回图片地址");
            }

            Log.d(TAG, "插画 URL：" + url);
            return url;
        }
    }
}
