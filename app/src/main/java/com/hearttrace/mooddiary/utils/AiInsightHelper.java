package com.hearttrace.mooddiary.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 统计页情绪洞察（周/月），与日记治愈、聊天场景分离。
 */
public final class AiInsightHelper {

    private static final String SYSTEM_PROMPT =
            "你是温柔专业的情绪数据分析助手。根据用户提供的情绪统计数据，"
                    + "生成约100字的情绪洞察：包含整体趋势、积极发现与一条可行建议，语气温暖简洁，不要列表编号。";

    public interface Callback {
        void onSuccess(String insight);

        void onError(String message);
    }

    private AiInsightHelper() {
    }

    public static void requestInsight(
            ExecutorService executor,
            DoubaoApiClient apiClient,
            String periodLabel,
            String statsSummary,
            Callback callback) {

        if (statsSummary == null || statsSummary.trim().isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onError("暂无" + periodLabel + "记录，无法生成洞察"));
            return;
        }

        List<DoubaoApiClient.Message> messages = new ArrayList<>();
        messages.add(new DoubaoApiClient.Message("system", SYSTEM_PROMPT));
        messages.add(new DoubaoApiClient.Message("user",
                periodLabel + "情绪数据：\n" + statsSummary + "\n请生成情绪洞察。"));

        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                String reply = apiClient.sendChatRequest(messages);
                mainHandler.post(() -> callback.onSuccess(reply));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError("洞察生成失败，请稍后重试"));
            }
        });
    }
}
