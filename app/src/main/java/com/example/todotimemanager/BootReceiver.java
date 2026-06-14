package com.example.todotimemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.database.TaskDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备重启后恢复所有未完成任务的截止提醒闹钟。
 * 因为 AlarmManager 的闹钟在重启后会被系统清除。
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Log.d(TAG, "设备启动完成，恢复任务提醒闹钟...");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                TaskDatabase db = TaskDatabase.getInstance(context);
                // 查询所有未完成、未删除、有截止时间的任务
                List<Task> tasks = db.taskDAO().getTasksWithRemindersSync();
                int count = 0;
                final long hourMs = MainActivity.DEBUG_FAST_REMINDERS
                        ? 1000L : 60 * 60 * 1000L;

                for (Task task : tasks) {
                    long deadline = task.getReminderTime();
                    if (deadline <= System.currentTimeMillis()) continue;

                    long reminder48h = deadline - 48 * hourMs;
                    long reminder24h = deadline - 24 * hourMs;

                    if (reminder48h > System.currentTimeMillis()) {
                        MainActivity.setExactAlarm(context, task, reminder48h, 48);
                        count++;
                    }
                    if (reminder24h > System.currentTimeMillis()) {
                        MainActivity.setExactAlarm(context, task, reminder24h, 24);
                        count++;
                    }
                }
                Log.d(TAG, "已恢复 " + count + " 个提醒闹钟");
            } catch (Exception e) {
                Log.e(TAG, "恢复闹钟失败", e);
            } finally {
                executor.shutdown();
            }
        });
    }
}
