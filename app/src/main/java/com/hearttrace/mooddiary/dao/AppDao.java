package com.hearttrace.mooddiary.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;


import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.model.User;

import java.util.List;

@Dao
public interface AppDao {
    // User相关操作
    @Insert
    long insertUser(User user);

    @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    User login(String username, String password);

    @Query("SELECT * FROM users WHERE username = :username")
    User findUserByUsername(String username);

    // MoodEntry相关操作
    @Insert
    long insertMoodEntry(MoodEntry moodEntry);

    @Query("SELECT * FROM mood_entries WHERE userId = :userId ORDER BY timestamp DESC")
    List<MoodEntry> getMoodEntriesByUserId(long userId);

    @Update
    void updateMoodEntry(MoodEntry moodEntry);

    @Delete
    void deleteMoodEntry(MoodEntry moodEntry);
    // 统计各类情绪日记数量
    // 统计各类情绪日记数量
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
    @Query("SELECT * FROM mood_entries WHERE id = :id")
    MoodEntry getMoodEntryById(long id);
}