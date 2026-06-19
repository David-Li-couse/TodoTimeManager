package com.example.todotimemanager;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.todotimemanager.adapter.TaskAdapter;
import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.viewmodel.TaskViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements TaskAdapter.OnTaskClickListener {

    private static final String TAG = "MainActivity";

    /**
     * 测试模式：设为 true 时提醒间隔从"小时"变为"秒"。
     * 48小时→48秒, 24小时→24秒。测试完毕后改回 false 重新构建。
     */
    public static final boolean DEBUG_FAST_REMINDERS = false;

    private TaskViewModel taskViewModel;
    private TabLayout tabLayout;
    private FloatingActionButton fab;

    private Fragment quadrantFragment;
    private Fragment calendarFragment;
    private Fragment completedFragment;
    private Fragment deletedFragment;
    private Fragment currentFragment;

    // 用于通知跳转的临时变量
    private long notificationTaskId = -1;
    private String notificationTaskTitle = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tabLayout = findViewById(R.id.tab_layout);
        setupTabs();

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddTaskActivity.class);
            startActivity(intent);
        });

        requestNotificationPermissionIfNeeded();

        // 处理从通知跳转过来的Intent
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    /**
     * 处理从通知跳转来的Intent，弹出任务操作对话框
     */
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;
        boolean fromNotification = intent.getBooleanExtra("from_notification", false);
        if (!fromNotification) return;
        long taskId = intent.getLongExtra("task_id", -1);
        String title = intent.getStringExtra("task_title");
        if (taskId == -1) return;
        notificationTaskId = taskId;
        notificationTaskTitle = title != null ? title : "任务";
        // 延迟一下，确保Activity完全显示
        findViewById(android.R.id.content).post(() -> showNotificationDialog(taskId));
    }

    /**
     * 显示通知跳转后的操作对话框
     */
    private void showNotificationDialog(long taskId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("任务处理")
                .setMessage("请选择对任务 \"" + notificationTaskTitle + "\" 的操作")
                .setPositiveButton("已完成", (dialog, which) -> markTaskAsCompleted(taskId))
                .setNeutralButton("延长截止时间", (dialog, which) -> extendDeadline(taskId))
                .setNegativeButton("取消", null)
                .setCancelable(true)
                .show();
    }

    /**
     * 标记任务为已完成
     */
    private void markTaskAsCompleted(long taskId) {
        final LiveData<Task> taskLiveData = taskViewModel.getTaskById(taskId);
        Observer<Task> observer = new Observer<Task>() {
            @Override
            public void onChanged(Task task) {
                if (task != null) {
                    task.setCompleted(true);
                    taskViewModel.update(task);
                    MainActivity.cancelAlarmForTask(MainActivity.this, taskId);
                    Toast.makeText(MainActivity.this, "任务已标记为完成", Toast.LENGTH_SHORT).show();
                }
                taskLiveData.removeObserver(this);  // 用同一个实例移除
            }
        };
        taskLiveData.observe(this, observer);
    }

    /**
     * 延长截止时间（弹出日期时间选择器）
     */
    private void extendDeadline(long taskId) {
        final LiveData<Task> taskLiveData = taskViewModel.getTaskById(taskId);
        Observer<Task> observer = new Observer<Task>() {
            @Override
            public void onChanged(Task task) {
                if (task == null) {
                    taskLiveData.removeObserver(this);
                    return;
                }
                taskLiveData.removeObserver(this);  // 提前移除，防止后续 update 导致二次触发
                Calendar calendar = Calendar.getInstance();
                long currentDeadline = task.getReminderTime();
                if (currentDeadline > 0) {
                    calendar.setTimeInMillis(currentDeadline);
                }
                showDateTimePickerForExtension(task, calendar);
            }
        };
        taskLiveData.observe(this, observer);
    }

    /**
     * 显示日期选择器（延长截止时间）
     */
    private void showDateTimePickerForExtension(Task task, Calendar calendar) {
        DatePickerDialog datePicker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    showTimePickerForExtension(task, calendar);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePicker.show();
    }

    /**
     * 显示时间选择器（延长截止时间）
     */
    private void showTimePickerForExtension(Task task, Calendar calendar) {
        new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    long newDeadline = calendar.getTimeInMillis();
                    if (newDeadline <= System.currentTimeMillis()) {
                        Toast.makeText(MainActivity.this, "截止时间必须是将来的时间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 更新任务
                    task.setReminderTime(newDeadline);
                    taskViewModel.update(task);
                    // 取消旧的闹钟，设置新的
                    MainActivity.cancelAlarmForTask(MainActivity.this, task.getId());
                    boolean ok = scheduleDeadlineReminders(task);
                    String msg = ok ? "截止时间已延长，提醒已更新" : "截止时间已延长，但提醒设置失败";
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true).show();
    }

    /**
     * 调度截止提醒闹钟（复用现有逻辑）
     */
    private boolean scheduleDeadlineReminders(Task task) {
        long deadline = task.getReminderTime();
        if (deadline <= System.currentTimeMillis()) return false;
        final long hourMs = DEBUG_FAST_REMINDERS ? 1000L : 60 * 60 * 1000L;
        long reminder48h = deadline - 48 * hourMs;
        long reminder24h = deadline - 24 * hourMs;
        boolean ok48 = false, ok24 = false, okDeadline = false;
        if (reminder48h > System.currentTimeMillis()) {
            ok48 = setExactAlarm(this, task, reminder48h, 48);
        }
        if (reminder24h > System.currentTimeMillis()) {
            ok24 = setExactAlarm(this, task, reminder24h, 24);
        }
        if (deadline > System.currentTimeMillis()) {
            okDeadline = setExactAlarm(this, task, deadline, 0);
        }
        return ok48 || ok24 || okDeadline;
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("四象限"));
        tabLayout.addTab(tabLayout.newTab().setText("日历"));
        tabLayout.addTab(tabLayout.newTab().setText("已完成"));
        tabLayout.addTab(tabLayout.newTab().setText("回收站"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                switchFragment(position);
                if (position == 2 || position == 3) {
                    fab.setVisibility(View.GONE);
                } else {
                    fab.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        switchFragment(0);
    }

    private void switchFragment(int position) {
        Fragment targetFragment;
        String tag;

        switch (position) {
            case 0:
                if (quadrantFragment == null) quadrantFragment = new QuadrantFragment();
                targetFragment = quadrantFragment;
                tag = "quadrant";
                break;
            case 1:
                if (calendarFragment == null) calendarFragment = new CalendarFragment();
                targetFragment = calendarFragment;
                tag = "calendar";
                break;
            case 2:
                if (completedFragment == null) completedFragment = new CompletedTasksFragment();
                targetFragment = completedFragment;
                tag = "completed";
                break;
            case 3:
                if (deletedFragment == null) deletedFragment = new DeletedTasksFragment();
                targetFragment = deletedFragment;
                tag = "deleted";
                break;
            default:
                if (quadrantFragment == null) quadrantFragment = new QuadrantFragment();
                targetFragment = quadrantFragment;
                tag = "quadrant";
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (currentFragment != null) transaction.hide(currentFragment);
        if (!targetFragment.isAdded()) {
            transaction.add(R.id.fragment_container, targetFragment, tag);
        } else {
            transaction.show(targetFragment);
        }
        transaction.commitAllowingStateLoss();
        currentFragment = targetFragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("搜索任务...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String q) { return false; }
                    @Override
                    public boolean onQueryTextChange(String newText) {
                        Fragment frag = currentFragment;
                        if (frag instanceof SearchableFragment) {
                            ((SearchableFragment) frag).onSearch(newText);
                        }
                        return true;
                    }
                });
                searchView.setOnCloseListener(() -> {
                    Fragment frag = currentFragment;
                    if (frag instanceof SearchableFragment) ((SearchableFragment) frag).onSearch("");
                    return false;
                });
            }
        }
        MenuItem sortPriority = menu.findItem(R.id.action_sort_priority);
        if (sortPriority != null) sortPriority.setVisible(false);
        MenuItem sortTime = menu.findItem(R.id.action_sort_time);
        if (sortTime != null) sortTime.setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.delete_all) {
            showDeleteAllConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private void showDeleteAllConfirmation() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除所有任务吗？此操作不可撤销。")
                .setPositiveButton("删除", (dialog, which) -> {
                    taskViewModel.deleteAllTasks();
                    Toast.makeText(this, "所有任务已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== TaskAdapter 回调 ====================

    @Override
    public void onTaskClick(Task task) {
        if (task.getDeletedAt() > 0) { onTaskRestore(task); return; }
        Intent intent = new Intent(this, AddTaskActivity.class);
        intent.putExtra("task_id", task.getId());
        startActivity(intent);
    }

    @Override
    public void onTaskChecked(Task task, boolean isChecked) {
        if (task.getDeletedAt() > 0) return;
        task.setCompleted(isChecked);
        taskViewModel.update(task);
        if (isChecked && task.getReminderTime() > 0) {
            cancelAlarmForTask(this, task.getId());
        }
    }

    @Override
    public void onTaskDelete(Task task) {
        taskViewModel.softDelete(task);
        cancelAlarmForTask(this, task.getId());
        Toast.makeText(this, "任务已删除", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTaskRestore(Task task) {
        taskViewModel.restore(task);
        Toast.makeText(this, "任务已恢复", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTaskDeletePermanent(Task task) {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("永久删除")
                .setMessage("确定要永久删除此任务吗？此操作不可撤销。")
                .setPositiveButton("删除", (dialog, which) -> {
                    taskViewModel.delete(task);
                    cancelAlarmForTask(this, task.getId());
                    Toast.makeText(this, "任务已永久删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 闹钟管理 ====================

    /**
     * 设置截止提醒闹钟。
     * 三层回退策略，覆盖所有设备/ROM的权限限制：
     * 1. setExactAndAllowWhileIdle - 最精确，需要SCHEDULE_EXACT_ALARM权限(API31+)
     * 2. setExact - 精确，不需要特殊权限
     * 3. set - 最后兜底，所有设备都支持
     */
    public static boolean setExactAlarm(Context context, Task task, long triggerTime, int hoursBefore) {
        AlarmManager alarmManager = null;
        PendingIntent pendingIntent = null;

        try {
            alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.w(TAG, "AlarmManager is null");
                return false;
            }

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("task_title", task.getTitle());
            intent.putExtra("task_id", task.getId());
            intent.putExtra("hours_before", hoursBefore);

            long taskId = task.getId();
            int offset = hoursBefore == 48 ? 0 : (hoursBefore == 24 ? 1 : 2);
            int requestCode = (int) ((taskId % 100000000) * 10 + offset);

            pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            long triggerInSec = (triggerTime - System.currentTimeMillis()) / 1000;
            String label = hoursBefore + "h前," + triggerInSec + "秒后";

            // 方案1：setExactAndAllowWhileIdle
            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "闹钟OK(setExactAndAllowWhileIdle): " + label);
                return true;
            } catch (SecurityException e) {
                Log.d(TAG, "无EXACT_ALARM权限，回退");
            } catch (Exception e) {
                Log.d(TAG, "setExactAndAllowWhileIdle失败: " + e.getMessage() + "，回退");
            }

            // 方案2：setExact
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                Log.d(TAG, "闹钟OK(setExact): " + label);
                return true;
            } catch (SecurityException e) {
                Log.d(TAG, "setExact也受限制，使用set兜底");
            } catch (Exception e) {
                Log.d(TAG, "setExact失败: " + e.getMessage() + "，使用set兜底");
            }

            // 方案3：set（所有设备/ROM都支持）
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            Log.d(TAG, "闹钟OK(set): " + label);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "闹钟全部失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 取消任务的所有闹钟。
     */
    public static void cancelAlarmForTask(Context context, long taskId) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            long safeId = taskId % 100000000;
            int requestCode48 = (int) (safeId * 10);
            int requestCode24 = (int) (safeId * 10 + 1);
            int requestCode0 = (int) (safeId * 10 + 2);

            // 取消三个闹钟（Intent需与设置时一致以确保PendingIntent匹配）
            Intent intent48 = new Intent(context, AlarmReceiver.class);
            intent48.putExtra("task_title", "");
            intent48.putExtra("task_id", taskId);
            intent48.putExtra("hours_before", 48);
            PendingIntent pi48 = PendingIntent.getBroadcast(
                    context, requestCode48, intent48,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pi48);

            Intent intent24 = new Intent(context, AlarmReceiver.class);
            intent24.putExtra("task_title", "");
            intent24.putExtra("task_id", taskId);
            intent24.putExtra("hours_before", 24);
            PendingIntent pi24 = PendingIntent.getBroadcast(
                    context, requestCode24, intent24,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pi24);

            Intent intent0 = new Intent(context, AlarmReceiver.class);
            intent0.putExtra("task_title", "");
            intent0.putExtra("task_id", taskId);
            intent0.putExtra("hours_before", 0);
            PendingIntent pi0 = PendingIntent.getBroadcast(
                    context, requestCode0, intent0,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pi0);
        } catch (Exception e) {
            Log.w(TAG, "取消闹钟失败", e);
        }
    }
}