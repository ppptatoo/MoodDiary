package com.hearttrace.mooddiary.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.hearttrace.mooddiary.dao.MoodEntryDao;
import com.hearttrace.mooddiary.dao.UserDao;
import com.hearttrace.mooddiary.model.MoodEntry;
import com.hearttrace.mooddiary.model.User;

@Database(entities = {User.class, MoodEntry.class}, version = 7, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract MoodEntryDao moodEntryDao();

    private static volatile AppDatabase INSTANCE;

    // 迁移规则：版本2 -> 3
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // 这里写你从版本2升级到3时的改动
            // 如果你没改结构，可以留空，Room会自动处理
        }
    };

    // 迁移规则：版本3 -> 4（新增imagePath字段）
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE mood_entries ADD COLUMN imagePath TEXT;");
        }
    };

    // 迁移规则：版本4 -> 5（新增isFavorite字段）
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE mood_entries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0;");
        }
    };

    // 迁移规则：版本5 -> 6（新增aiImagePath和aiQuote字段）
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE mood_entries ADD COLUMN aiImagePath TEXT;");
            db.execSQL("ALTER TABLE mood_entries ADD COLUMN aiQuote TEXT;");
        }
    };

    // 迁移规则：版本6 -> 7（新增 Supabase 同步字段）
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE mood_entries ADD COLUMN remoteId INTEGER NOT NULL DEFAULT 0;");
            db.execSQL("ALTER TABLE mood_entries ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0;");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "mood_diary_db"
                            )
                            // 加上完整的迁移链：2→3→4→5→6
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                            // 兜底：如果迁移失败，就重建数据库（不会闪退）
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}