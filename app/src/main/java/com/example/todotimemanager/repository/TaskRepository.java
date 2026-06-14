package com.example.todotimemanager.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.database.TaskDAO;
import com.example.todotimemanager.database.TaskDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TaskRepository {
    private final TaskDAO taskDAO;
    private final LiveData<List<Task>> allTasks;
    private final LiveData<List<Task>> pendingTasks;
    private final LiveData<List<Task>> completedTasks;
    private final LiveData<List<Task>> deletedTasks;
    private final ExecutorService executorService;

    public TaskRepository(Application application) {
        TaskDatabase database = TaskDatabase.getInstance(application);
        taskDAO = database.taskDAO();
        allTasks = taskDAO.getAllTasks();
        pendingTasks = taskDAO.getPendingTasks();
        completedTasks = taskDAO.getCompletedTasks();
        deletedTasks = taskDAO.getDeletedTasks();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(Task task, OnInsertCallback callback) {
        executorService.execute(() -> {
            try {
                long id = taskDAO.insert(task);
                task.setId(id);
                if (callback != null) {
                    callback.onInserted(id);
                }
            } catch (Exception e) {
                // 防止数据库操作异常导致线程静默终止
                e.printStackTrace();
            }
        });
    }

    public void update(Task task) {
        executorService.execute(() -> {
            try {
                taskDAO.update(task);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void delete(Task task) {
        executorService.execute(() -> {
            try {
                taskDAO.delete(task);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void deleteAllTasks() {
        executorService.execute(() -> {
            try {
                taskDAO.deleteAllTasks();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void softDelete(Task task) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                task.setDeletedAt(now);
                taskDAO.setDeletedAt(task.getId(), now);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void restore(Task task) {
        executorService.execute(() -> {
            try {
                task.setDeletedAt(0);
                taskDAO.setDeletedAt(task.getId(), 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 清理旧软删除任务（保留7天），在应用启动时调用
     */
    public void cleanupOldDeletedTasks() {
        executorService.execute(() -> {
            try {
                long sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
                taskDAO.deleteOldSoftDeleted(sevenDaysAgo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 安全关闭executor，应在Application.onTerminate或合适时机调用
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public LiveData<List<Task>> getAllTasks() {
        return allTasks;
    }

    public LiveData<List<Task>> getPendingTasks() {
        return pendingTasks;
    }

    public LiveData<List<Task>> getCompletedTasks() {
        return completedTasks;
    }

    public LiveData<List<Task>> getDeletedTasks() {
        return deletedTasks;
    }

    public LiveData<Task> getTaskById(long taskId) {
        return taskDAO.getTaskById(taskId);
    }

    public LiveData<List<Task>> getTasksDueForReminder(long currentTime) {
        return taskDAO.getTasksDueForReminder(currentTime);
    }

    public interface OnInsertCallback {
        void onInserted(long taskId);
    }
}
