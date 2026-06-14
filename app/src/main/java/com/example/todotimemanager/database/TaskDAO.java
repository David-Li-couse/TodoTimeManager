package com.example.todotimemanager.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDAO {
    @Insert
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("DELETE FROM tasks")
    void deleteAllTasks();

    @Query("SELECT * FROM tasks WHERE deletedAt = 0 ORDER BY priority DESC, reminderTime ASC")
    LiveData<List<Task>> getAllTasks();

    @Query("SELECT * FROM tasks WHERE deletedAt = 0 AND isCompleted = 0 ORDER BY priority DESC, reminderTime ASC")
    LiveData<List<Task>> getPendingTasks();

    @Query("SELECT * FROM tasks WHERE deletedAt = 0 AND isCompleted = 1 ORDER BY priority DESC, reminderTime ASC")
    LiveData<List<Task>> getCompletedTasks();

    @Query("SELECT * FROM tasks WHERE deletedAt > 0 ORDER BY deletedAt DESC LIMIT 10")
    LiveData<List<Task>> getDeletedTasks();

    @Query("SELECT * FROM tasks WHERE reminderTime > 0 AND reminderTime <= :currentTime AND isCompleted = 0 AND deletedAt = 0")
    LiveData<List<Task>> getTasksDueForReminder(long currentTime);

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    LiveData<Task> getTaskById(long taskId);

    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :taskId")
    void setDeletedAt(long taskId, long deletedAt);

    @Query("DELETE FROM tasks WHERE deletedAt > 0 AND deletedAt < :beforeTime")
    void deleteOldSoftDeleted(long beforeTime);

    // 同步查询（非LiveData），用于BootReceiver等在后台线程调用的场景
    @Query("SELECT * FROM tasks WHERE deletedAt = 0 AND isCompleted = 0 AND reminderTime > 0")
    List<Task> getTasksWithRemindersSync();
}