package com.hearttrace.mooddiary.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Entity(tableName = "mood_entries")
public class MoodEntry {

    @PrimaryKey(autoGenerate = true)
    private long id;
    private long userId;
    private long timestamp;    // 创建时间
    private long updateTime;   // 修改时间
    private String text;
    private String userEmotionLabel;
    private String imagePath;
    private boolean isFavorite;
    private String aiImagePath;
    private String aiQuote;

    // === Supabase 同步字段 ===
    private long remoteId;       // Supabase 云端记录的 ID，0 表示未同步
    private int syncStatus;      // 0=已同步, 1=待创建, 2=待更新, 3=待删除

    // 构造方法：新建日记调用，创建时间=修改时间
    public MoodEntry(long userId, long timestamp, String text) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.updateTime = timestamp;
        this.text = text;
    }

    // ====== Getter & Setter 全部补齐 ======
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }
    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getUpdateTime() {
        return updateTime;
    }
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    public String getUserEmotionLabel() {
        return userEmotionLabel;
    }
    public void setUserEmotionLabel(String userEmotionLabel) {
        this.userEmotionLabel = userEmotionLabel;
    }

    // 时间备注方法
    public String getTimeNote() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String create = sdf.format(new Date(timestamp));
        String update = sdf.format(new Date(updateTime));
        if (timestamp == updateTime) {
            return "创建：" + create;
        } else {
            return "创建：" + create + "  |  修改：" + update;
        }
    }

    // 日记预览
    public String getPreviewText() {
        if (text == null || text.isEmpty()) return "无内容";
        return text.length() > 20 ? text.substring(0,20) + "..." : text;
    }

    // 格式化日期（保留原有方法，兼容旧代码）
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getAiImagePath() {
        return aiImagePath;
    }

    public void setAiImagePath(String aiImagePath) {
        this.aiImagePath = aiImagePath;
    }

    public String getAiQuote() {
        return aiQuote;
    }

    public void setAiQuote(String aiQuote) {
        this.aiQuote = aiQuote;
    }

    // === Supabase 同步字段的 getter/setter ===
    public long getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(long remoteId) {
        this.remoteId = remoteId;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(int syncStatus) {
        this.syncStatus = syncStatus;
    }
}