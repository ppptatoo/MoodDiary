package com.hearttrace.mooddiary.supabase;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * 共享的 OkHttpClient，供 Supabase 和 DoubaoApi 共用
 * 复用项目已有的 SSL 信任配置
 */
public class DoubaoHttpHelper {

    private static volatile OkHttpClient INSTANCE;

    public static OkHttpClient getClient() {
        if (INSTANCE == null) {
            synchronized (DoubaoHttpHelper.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildClient();
                }
            }
        }
        return INSTANCE;
    }

    private static OkHttpClient buildClient() {
        try {
            X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new X509TrustManager[]{trustManager}, new SecureRandom());
            SSLSocketFactory sslFactory = sslContext.getSocketFactory();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslFactory, trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
