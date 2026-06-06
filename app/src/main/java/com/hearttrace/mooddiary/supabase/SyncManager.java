package com.hearttrace.mooddiary.supabase;

import android.util.Log;

import com.hearttrace.mooddiary.database.AppDatabase;
import com.hearttrace.mooddiary.model.MoodEntry;

import java.util.List;

/**
 * 同步管理器：负责 Room 本地缓存 ↔ Supabase 云端的双向同步
 * 
 * 同步策略：
 * - 在线时：写入云端成功后同步到本地 Room（标记 syncStatus=0）
 * - 离线时：写入本地 Room（标记 syncStatus=1），联网后自动推送
 * - 读取时：优先从云端拉取最新数据，同步到 Room 缓存
 */
public class SyncManager {

    private static final String TAG = "SyncManager";

    private final AppDatabase roomDb;
    private final SupabaseDataClient cloudClient;
    private final String supabaseUserId;   // Supabase 用户 UUID

    public SyncManager(AppDatabase roomDb, String supabaseUserId, SupabaseDataClient cloudClient) {
        this.roomDb = roomDb;
        this.supabaseUserId = supabaseUserId;
        this.cloudClient = cloudClient;
    }

    // ==================== 创建日记（本地优先模式） ====================

    /**
     * 创建日记：先写本地 Room → 立即返回 → 后台异步推云端。
     * 不再阻塞等待网络，用户点击保存立刻得到反馈。
     * @return 新日记的 Room 本地 ID
     */
    public long createMoodEntry(MoodEntry localEntry) {
        // 1️⃣ 立即写入本地 Room（标记待同步）
        localEntry.setSyncStatus(1); // pending_create
        long localId = roomDb.moodEntryDao().insertMoodEntry(localEntry);
        localEntry.setId(localId);   // 显式赋值，确保后续 update 能命中正确记录
        Log.d(TAG, "日记本地保存成功: localId=" + localId);

        // 2️⃣ 后台异步推送到云端
        asyncPushToCloud(localEntry);

        return localId;
    }

    /**
     * 异步推送单条日记到云端（不阻塞调用方）
     */
    private void asyncPushToCloud(MoodEntry entry) {
        new Thread(() -> {
            try {
                SupabaseDataClient.SupabaseMoodEntry cloudEntry = buildCloudEntry(entry);
                long remoteId = cloudClient.insertMoodEntry(cloudEntry);
                // 推送成功，更新本地记录的 remoteId 和同步状态

                // 防止重复：如果 pullFromCloud 已经先插入了一条同 remoteId 的记录，
                // 则删除原始未同步记录（避免出现两条）
                MoodEntry dup = roomDb.moodEntryDao().getByRemoteId(remoteId);
                if (dup != null && dup.getId() != entry.getId()) {
                    // pullFromCloud 已插入了一条，删除原始未同步的记录
                    Log.w(TAG, "检测到重复，删除本地未同步记录: localId=" + entry.getId()
                            + " (已有 remoteId=" + remoteId + " 的记录 localId=" + dup.getId() + ")");
                    roomDb.moodEntryDao().deleteMoodEntry(entry.getId());
                    return;
                }

                entry.setRemoteId(remoteId);
                entry.setSyncStatus(0);
                roomDb.moodEntryDao().updateMoodEntry(entry);
                Log.d(TAG, "日记后台同步成功: localId=" + entry.getId() + " → remoteId=" + remoteId);
            } catch (Exception e) {
                Log.w(TAG, "后台同步失败，稍后重试: localId=" + entry.getId() + " " + e.getMessage());
                // syncStatus 保持为 1 (pending_create)，在 pushPendingEntries 时会重试
            }
        }, "sync-create-" + entry.getId()).start();
    }

    // ==================== 更新日记（在线模式） ====================

    /**
     * 更新日记：乐观更新策略 —— 先写 Room 保证本地立即可见，再异步推云端。
     * 这样用户切页面再返回时，Room 里已经是新状态，不会出现"闪现回旧状态"的问题。
     */
    public void updateMoodEntry(MoodEntry localEntry) {
        long remoteId = localEntry.getRemoteId();
        int syncStatus = localEntry.getSyncStatus();

        // ===== 第一步：乐观写入 Room（确保本地立即可见）=====
        // 同时更新时间戳，防止 pullFromCloud 用云端旧数据覆盖本地新状态
        localEntry.setUpdateTime(System.currentTimeMillis());
        roomDb.moodEntryDao().updateMoodEntry(localEntry);

        // ===== 第二步：异步推送云端（失败时标记 pending_update，下次 pushPendingEntries 重试）=====
        try {
            if (remoteId > 0 && syncStatus == 0) {
                // 已同步过的，推送到云端更新
                SupabaseDataClient.SupabaseMoodEntry cloudEntry = buildCloudEntry(localEntry);
                cloudClient.updateMoodEntry(remoteId, cloudEntry);
                localEntry.setSyncStatus(0);
            } else {
                // 还没同步过的，尝试首次推送
                SupabaseDataClient.SupabaseMoodEntry cloudEntry = buildCloudEntry(localEntry);
                long newRemoteId = cloudClient.insertMoodEntry(cloudEntry);
                localEntry.setRemoteId(newRemoteId);
                localEntry.setSyncStatus(0);
            }
        } catch (Exception e) {
            // 云端失败，标记待同步
            Log.w(TAG, "云端更新失败，标记待同步: " + e.getMessage());
            localEntry.setSyncStatus(2); // pending_update
        }

        // 第三次写入：更新 syncStatus 和可能的 remoteId
        roomDb.moodEntryDao().updateMoodEntry(localEntry);
    }

    // ==================== 删除日记 ====================

    /**
     * 删除日记：从云端和本地同时删除
     */
    public void deleteMoodEntry(MoodEntry localEntry) {
        long remoteId = localEntry.getRemoteId();

        try {
            if (remoteId > 0) {
                cloudClient.deleteMoodEntry(remoteId);
            }
        } catch (Exception e) {
            Log.w(TAG, "云端删除失败: " + e.getMessage());
        }

        roomDb.moodEntryDao().deleteMoodEntry(localEntry.getId());
    }

    // ==================== 初始化同步：从云端拉取全部数据 ====================

    /**
     * 用户登录后首次拉取云端全部日记到本地缓存
     * 覆盖策略：云端数据优先，本地未同步的保留
     */
    public void pullFromCloud(long localUserId) {
        try {
            List<SupabaseDataClient.SupabaseMoodEntry> cloudEntries =
                    cloudClient.getAllMoodEntries(supabaseUserId);

            Log.d(TAG, "从云端拉取到 " + cloudEntries.size() + " 条日记");

            for (SupabaseDataClient.SupabaseMoodEntry cloud : cloudEntries) {
                // 检查本地是否已有此 remoteId 的记录
                MoodEntry existing = roomDb.moodEntryDao().getByRemoteId(cloud.id);
                if (existing != null) {
                    // 已存在：比较更新时间，云端更新则覆盖
                    if (cloud.updateTime > existing.getUpdateTime()) {
                        updateLocalFromCloud(existing, cloud);
                        roomDb.moodEntryDao().updateMoodEntry(existing);
                    }
                    continue;
                }

                // 兜底：检查本地是否有 timestamp+text 相同、remoteId=0 的未同步记录
                // 防止 asyncPushToCloud 和 pullFromCloud 并发时产生重复
                MoodEntry pendingMatch = roomDb.moodEntryDao().getByTimestampAndText(
                        localUserId, cloud.timestamp, cloud.text);
                if (pendingMatch != null && pendingMatch.getRemoteId() == 0) {
                    // 找到对应的本地未同步记录，绑定 remoteId 并同步状态
                    pendingMatch.setRemoteId(cloud.id);
                    pendingMatch.setSyncStatus(0);
                    updateLocalFromCloud(pendingMatch, cloud);
                    roomDb.moodEntryDao().updateMoodEntry(pendingMatch);
                    Log.d(TAG, "pullFromCloud 绑定本地未同步记录: localId=" + pendingMatch.getId()
                            + " → remoteId=" + cloud.id);
                    continue;
                }

                // 确实不存在：新建到 Room
                MoodEntry local = new MoodEntry(localUserId, cloud.timestamp, cloud.text);
                local.setUserEmotionLabel(cloud.emotionLabel);
                local.setUpdateTime(cloud.updateTime);
                local.setImagePath(cloud.imagePath);
                local.setFavorite(cloud.isFavorite);
                local.setAiImagePath(cloud.aiImagePath);
                local.setAiQuote(cloud.aiQuote);
                local.setRemoteId(cloud.id);
                local.setSyncStatus(0);
                roomDb.moodEntryDao().insertMoodEntry(local);
            }

        } catch (Exception e) {
            Log.e(TAG, "云端拉取失败: " + e.getMessage());
        }
    }

    // ==================== 推送本地未同步的数据 ====================

    /**
     * 将本地标记为待同步的日记推送到云端
     */
    public void pushPendingEntries(long localUserId) {
        List<MoodEntry> pending = roomDb.moodEntryDao().getPendingEntries(localUserId);

        for (MoodEntry entry : pending) {
            try {
                SupabaseDataClient.SupabaseMoodEntry cloudEntry = buildCloudEntry(entry);

                if (entry.getSyncStatus() == 1) {
                    // pending_create：首次推送到云端
                    long remoteId = cloudClient.insertMoodEntry(cloudEntry);
                    entry.setRemoteId(remoteId);
                    entry.setSyncStatus(0);
                    roomDb.moodEntryDao().updateMoodEntry(entry);
                    Log.d(TAG, "推送创建: localId=" + entry.getId() + " → remoteId=" + remoteId);
                } else if (entry.getSyncStatus() == 2 && entry.getRemoteId() > 0) {
                    // pending_update：更新云端
                    cloudClient.updateMoodEntry(entry.getRemoteId(), cloudEntry);
                    entry.setSyncStatus(0);
                    roomDb.moodEntryDao().updateMoodEntry(entry);
                    Log.d(TAG, "推送更新: remoteId=" + entry.getRemoteId());
                } else if (entry.getSyncStatus() == 3 && entry.getRemoteId() > 0) {
                    // pending_delete：从云端删除
                    cloudClient.deleteMoodEntry(entry.getRemoteId());
                    roomDb.moodEntryDao().deleteMoodEntry(entry.getId());
                    Log.d(TAG, "推送删除: remoteId=" + entry.getRemoteId());
                }

            } catch (Exception e) {
                Log.w(TAG, "推送失败: localId=" + entry.getId() + " " + e.getMessage());
            }
        }
    }

    // ==================== 辅助方法 ====================

    private SupabaseDataClient.SupabaseMoodEntry buildCloudEntry(MoodEntry local) {
        SupabaseDataClient.SupabaseMoodEntry cloud = new SupabaseDataClient.SupabaseMoodEntry();
        cloud.userId = supabaseUserId;
        cloud.timestamp = local.getTimestamp();
        cloud.updateTime = local.getUpdateTime();
        cloud.text = local.getText();
        cloud.emotionLabel = local.getUserEmotionLabel();
        cloud.imagePath = local.getImagePath();
        cloud.isFavorite = local.isFavorite();
        cloud.aiImagePath = local.getAiImagePath();
        cloud.aiQuote = local.getAiQuote();
        return cloud;
    }

    private void updateLocalFromCloud(MoodEntry local, SupabaseDataClient.SupabaseMoodEntry cloud) {
        local.setTimestamp(cloud.timestamp);
        local.setUpdateTime(cloud.updateTime);
        local.setText(cloud.text);
        local.setUserEmotionLabel(cloud.emotionLabel);
        local.setImagePath(cloud.imagePath);
        local.setFavorite(cloud.isFavorite);
        local.setAiImagePath(cloud.aiImagePath);
        local.setAiQuote(cloud.aiQuote);
        local.setSyncStatus(0);
    }
}
