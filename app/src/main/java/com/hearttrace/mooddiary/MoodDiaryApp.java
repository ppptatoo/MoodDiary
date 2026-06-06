package com.hearttrace.mooddiary;

import android.app.Application;
import android.util.Log;

import com.hearttrace.mooddiary.utils.ThemeHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MoodDiaryApp extends Application {

    private static final String TAG = "MoodDiaryApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // 设置全局未捕获异常处理器，记录崩溃日志到文件
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "!!! UNCAUGHT EXCEPTION in thread: " + thread.getName(), throwable);
            writeCrashLog(throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        // 在创建任何 Activity 之前恢复用户的主题偏好
        ThemeHelper.applyTheme(this);
    }

    private void writeCrashLog(Throwable throwable) {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                dir = getFilesDir();
            }
            File logFile = new File(dir, "crash_log.txt");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("=== Crash Time: " + 
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date()));
            pw.println("=== Thread: " + Thread.currentThread().getName());
            throwable.printStackTrace(pw);
            pw.flush();

            FileWriter fw = new FileWriter(logFile, true);
            fw.write(sw.toString());
            fw.write("\n");
            fw.close();

            Log.e(TAG, "Crash log written to: " + logFile.getAbsolutePath());
            Log.e(TAG, sw.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash log", e);
        }
    }
}
