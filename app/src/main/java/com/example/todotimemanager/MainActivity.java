package com.example.todotimemanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
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
import androidx.lifecycle.ViewModelProvider;

import com.example.todotimemanager.adapter.TaskAdapter;
import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.viewmodel.TaskViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

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
            int requestCode = (int) ((taskId % 100000000) * 10 + (hoursBefore == 48 ? 0 : 1));

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

            // 取消两个闹钟（Intent需与设置时一致以确保PendingIntent匹配）
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
        } catch (Exception e) {
            Log.w(TAG, "取消闹钟失败", e);
        }
    }
}
