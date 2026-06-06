package com.hearttrace.mooddiary.utils;

import android.os.Handler;
import android.os.Looper;

import com.hearttrace.mooddiary.model.MoodEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 日记场景的 AI 治愈回复（与 AiChatActivity 聊天场景分离，避免混用 prompt 与消息结构）。
 */
public final class AiHealingHelper {

    /** 日记输入文本最大长度（截断过长日记，减少 prompt 处理延迟） */
    private static final int MAX_DIARY_INPUT_CHARS = 500;

    /** 首页点击日期弹窗等场景 */
    private static final String SYSTEM_PROMPT_HOME =
            "用温柔共情的语气，根据用户日记生成100字以内的治愈回复。";

    /** 历史日记列表卡片 */
    private static final String SYSTEM_PROMPT_HISTORY =
            "用诗意温柔的语言，根据用户日记写一句50字以内的治愈回应。";

    public interface Callback {
        void onSuccess(String reply);

        void onError(String message);
    }

    private AiHealingHelper() {
    }

    public static void requestHealingForDiary(
            ExecutorService executor,
            DoubaoApiClient apiClient,
            List<MoodEntry> entries,
            Callback callback) {
        requestHealingForDiary(executor, apiClient, entries, SYSTEM_PROMPT_HOME, callback);
    }

    /** 历史日记页：诗意治愈回应，与首页弹窗提示词区分 */
    public static void requestHistoryDiaryHealing(
            ExecutorService executor,
            DoubaoApiClient apiClient,
            List<MoodEntry> entries,
            Callback callback) {
        requestHealingForDiary(executor, apiClient, entries, SYSTEM_PROMPT_HISTORY, callback);
    }

    public static void requestHealingForDiary(
            ExecutorService executor,
            DoubaoApiClient apiClient,
            List<MoodEntry> entries,
            String systemPrompt,
            Callback callback) {

        StringBuilder diaryContent = new StringBuilder();
        for (MoodEntry entry : entries) {
            if (entry.getText() != null) {
                diaryContent.append(entry.getText()).append("\n");
            }
        }
        String content = diaryContent.toString().trim();
        if (content.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onError("日记内容为空"));
            return;
        }

        // 截断过长的日记输入，减少 prompt 处理时间
        if (content.length() > MAX_DIARY_INPUT_CHARS) {
            content = content.substring(0, MAX_DIARY_INPUT_CHARS) + "…";
        }

        List<DoubaoApiClient.Message> messages = new ArrayList<>();
        messages.add(new DoubaoApiClient.Message("system", systemPrompt));
        messages.add(new DoubaoApiClient.Message("user", "日记内容：" + content));

        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                // 不传 max_tokens：推理模型的 max_tokens 包含思考过程(CoT)，
                // 设太小会导致模型思考阶段卡死超时。靠 system prompt 控制回复长度。
                String aiReply = apiClient.sendChatRequest(messages);
                mainHandler.post(() -> callback.onSuccess(aiReply));
            } catch (Exception e) {
                e.printStackTrace();
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "AI 回复失败，请稍后重试";
                }
                String finalMessage = message;
                mainHandler.post(() -> callback.onError(finalMessage));
            }
        });
    }
}
