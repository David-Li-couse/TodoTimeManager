package com.example.todotimemanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "task_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String taskTitle = intent.getStringExtra("task_title");
        long taskId = intent.getLongExtra("task_id", -1);
        int hoursBefore = intent.getIntExtra("hours_before", 0);

        // 防御：title为空时使用默认值
        if (taskTitle == null) {
            taskTitle = "未命名任务";
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        createNotificationChannel(notificationManager);

        String contentText;
        if (hoursBefore == 48) {
            contentText = "任务 \"" + taskTitle + "\" 将在48小时后截止，请尽快完成。";
        } else if (hoursBefore == 24) {
            contentText = "任务 \"" + taskTitle + "\" 将在24小时后截止，请尽快完成。";
        } else if (hoursBefore == 0) {
            contentText = "任务 \"" + taskTitle + "\" 已截止，请及时处理。";
        } else {
            contentText = "任务 \"" + taskTitle + "\" 即将截止。";
        }

        try {
            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("⏰ 任务截止提醒")
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build();

            // 48h/24h/截止时使用不同的通知ID，避免后触发的通知覆盖先触发的
            int offset = hoursBefore == 48 ? 0 : (hoursBefore == 24 ? 1 : 2);
            int notifyId = (int) ((taskId * 10 + offset) % Integer.MAX_VALUE);
            notificationManager.notify(notifyId, notification);
        } catch (Exception e) {
            // 防止SecurityException(权限未授予)等异常导致崩溃
            e.printStackTrace();
        }
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel existingChannel =
                    notificationManager.getNotificationChannel(CHANNEL_ID);
            if (existingChannel != null) return; // 已创建，跳过

            CharSequence name = "任务提醒";
            String description = "用于提醒任务截止";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            try {
                notificationManager.createNotificationChannel(channel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
