package com.example.todotimemanager.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.repository.TaskRepository;
import java.util.List;

public class TaskViewModel extends AndroidViewModel {
    private TaskRepository repository;
    private LiveData<List<Task>> allTasks;
    private LiveData<List<Task>> pendingTasks;
    private LiveData<List<Task>> completedTasks;
    private LiveData<List<Task>> deletedTasks;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);
        allTasks = repository.getAllTasks();
        pendingTasks = repository.getPendingTasks();
        completedTasks = repository.getCompletedTasks();
        deletedTasks = repository.getDeletedTasks();
    }

    public void insert(Task task, TaskRepository.OnInsertCallback callback) {
        repository.insert(task, callback);
    }

    public void update(Task task) {
        repository.update(task);
    }

    public void delete(Task task) {
        repository.delete(task);
    }

    public void deleteAllTasks() {
        repository.deleteAllTasks();
    }

    public void softDelete(Task task) {
        repository.softDelete(task);
    }

    public void restore(Task task) {
        repository.restore(task);
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
        return repository.getTaskById(taskId);
    }

    public LiveData<List<Task>> getTasksDueForReminder(long currentTime) {
        return repository.getTasksDueForReminder(currentTime);
    }
}