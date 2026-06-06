package com.hearttrace.mooddiary.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.hearttrace.mooddiary.model.MoodEntry;
import java.util.List;

@Dao
public interface MoodEntryDao {

    @Insert
    long insertMoodEntry(MoodEntry entry);

    @Update
    void updateMoodEntry(MoodEntry entry);

    @Query("SELECT * FROM mood_entries WHERE userId = :userId ORDER BY timestamp DESC")
    List<MoodEntry> getMoodEntriesByUserId(long userId);

    @Query("SELECT * FROM mood_entries WHERE id = :id")
    MoodEntry getMoodEntryById(long id);

    @Query("DELETE FROM mood_entries WHERE id = :id")
    void deleteMoodEntry(long id);

    @Query("SELECT COUNT(*) FROM mood_entries WHERE userId = :userId AND userEmotionLabel = '开心'")
    int countHappy(long userId);

    @Query("SELECT COUNT(*) FROM mood_entries WHERE userId = :userId AND userEmotionLabel = '难过'")
    int countSad(long userId);

    @Query("SELECT COUNT(*) FROM mood_entries WHERE userId = :userId AND userEmotionLabel = '生气'")
    int countAngry(long userId);

    @Query("SELECT COUNT(*) FROM mood_entries WHERE userId = :userId AND userEmotionLabel = '平静'")
    int countCalm(long userId);

    @Query("SELECT COUNT(*) FROM mood_entries WHERE userId = :userId AND userEmotionLabel = '焦虑'")
    int countAnxious(long userId);

    @Query("SELECT COUNT(*) FROM mood_entries WHERE userId = :userId AND userEmotionLabel = '疲惫'")
    int countTired(long userId);

    @Query("SELECT COUNT(DISTINCT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch')) FROM mood_entries WHERE userId = :userId")
    int countDistinctDays(long userId);

    @Query("SELECT SUM(LENGTH(text)) FROM mood_entries WHERE userId = :userId")
    int sumTotalWords(long userId);

    @Query("SELECT timestamp FROM mood_entries WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    long getLatestTimestamp(long userId);

    @Query("SELECT * FROM mood_entries WHERE userId = :userId AND isFavorite = 1 ORDER BY timestamp DESC")
    List<MoodEntry> getFavoriteEntriesByUserId(long userId);

    // === Supabase 同步用查询 ===

    @Query("SELECT * FROM mood_entries WHERE remoteId = :remoteId LIMIT 1")
    MoodEntry getByRemoteId(long remoteId);

    @Query("SELECT * FROM mood_entries WHERE userId = :userId AND timestamp = :timestamp AND text = :text LIMIT 1")
    MoodEntry getByTimestampAndText(long userId, long timestamp, String text);

    @Query("SELECT * FROM mood_entries WHERE userId = :userId AND syncStatus > 0")
    List<MoodEntry> getPendingEntries(long userId);

}