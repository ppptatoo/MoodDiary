package com.hearttrace.mooddiary.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.hearttrace.mooddiary.model.User;

@Dao
public interface UserDao {

    @Insert
    long insertUser(User user);

    @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    User login(String username, String password);

    @Query("SELECT * FROM users WHERE username = :username")
    User findUserByUsername(String username);

    @Update
    void updateUser(User user);

    @Query("UPDATE users SET password = :newPassword WHERE id = :userId")
    void updatePassword(long userId, String newPassword);

    @Query("UPDATE users SET username = :newUsername WHERE id = :userId")
    void updateUsername(long userId, String newUsername);

    @Query("SELECT * FROM users WHERE id = :userId")
    User getUserById(long userId);
}