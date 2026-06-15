package com.example.todotimemanager;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.viewmodel.TaskViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddTaskActivity extends AppCompatActivity {

    private EditText etTitle;
    private EditText etDescription;
    private TextView tvTitleCounter;
    private Chip chipHigh, chipMedium, chipLow, chipNone;
    private Chip chipCatWork, chipCatPersonal, chipCatStudy, chipCatOther;
    private TextView tvReminder;
    private MaterialButton btnSave, btnCancel;
    private MaterialCardView cardReminder;
    private ImageButton btnClearReminder;

    private TaskViewModel taskViewModel;

    // 编辑模式
    private Task existingTask = null;
    private boolean isEditMode = false;
    private boolean dataLoaded = false;
    private LiveData<Task> taskLiveData;
    private Observer<Task> editModeObserver;

    // 表单数据
    private long deadlineTime = 0;
    private int selectedPriority = Task.PRIORITY_HIGH;
    private String selectedCategory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        tvTitleCounter = findViewById(R.id.tv_title_counter);
        chipHigh = findViewById(R.id.chip_high);
        chipMedium = findViewById(R.id.chip_medium);
        chipLow = findViewById(R.id.chip_low);
        chipNone = findViewById(R.id.chip_none);
        chipCatWork = findViewById(R.id.chip_category_work);
        chipCatPersonal = findViewById(R.id.chip_category_personal);
        chipCatStudy = findViewById(R.id.chip_category_study);
        chipCatOther = findViewById(R.id.chip_category_other);
        tvReminder = findViewById(R.id.tv_reminder);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        cardReminder = findViewById(R.id.card_reminder);
        btnClearReminder = findViewById(R.id.btn_clear_reminder);

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvTitleCounter.setText((s != null ? s.length() : 0) + "/100");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        taskViewModel = new TaskViewModel(getApplication());

        setupChipGroup();
        setupCategoryChips();

        Intent intent = getIntent();
        if (intent.hasExtra("task_id")) {
            long taskId = intent.getLongExtra("task_id", -1);
            if (taskId != -1) {
                isEditMode = true;
                dataLoaded = false;
                taskLiveData = taskViewModel.getTaskById(taskId);
                editModeObserver = task -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (task != null) {
                        existingTask = task;
                        populateExistingTask();
                        dataLoaded = true;
                    }
                    if (taskLiveData != null && editModeObserver != null) {
                        taskLiveData.removeObserver(editModeObserver);
                    }
                };
                taskLiveData.observe(this, editModeObserver);
            }
        } else {
            dataLoaded = true;
        }

        cardReminder.setOnClickListener(v -> showDateTimePicker());
        tvReminder.setOnClickListener(v -> showDateTimePicker());
        btnClearReminder.setOnClickListener(v -> {
            deadlineTime = 0;
            tvReminder.setText("点击选择截止时间（截止前48h/24h及截止时提醒）");
            btnClearReminder.setVisibility(View.GONE);
        });

        btnSave.setOnClickListener(v -> saveTask());
        btnCancel.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (taskLiveData != null && editModeObserver != null) {
            taskLiveData.removeObserver(editModeObserver);
        }
    }

    // ==================== Chip Group ====================

    private void setupChipGroup() {
        chipHigh.setOnClickListener(v -> selectPriority(Task.PRIORITY_HIGH));
        chipMedium.setOnClickListener(v -> selectPriority(Task.PRIORITY_MEDIUM));
        chipLow.setOnClickListener(v -> selectPriority(Task.PRIORITY_LOW));
        chipNone.setOnClickListener(v -> selectPriority(Task.PRIORITY_NONE));
    }

    private void setupCategoryChips() {
        chipCatWork.setOnClickListener(v -> selectCategory("Work"));
        chipCatPersonal.setOnClickListener(v -> selectCategory("Personal"));
        chipCatStudy.setOnClickListener(v -> selectCategory("Study"));
        chipCatOther.setOnClickListener(v -> selectCategory("Other"));
    }

    private void selectPriority(int priority) {
        selectedPriority = priority;
        chipHigh.setChecked(priority == Task.PRIORITY_HIGH);
        chipMedium.setChecked(priority == Task.PRIORITY_MEDIUM);
        chipLow.setChecked(priority == Task.PRIORITY_LOW);
        chipNone.setChecked(priority == Task.PRIORITY_NONE);
    }

    private void selectCategory(String category) {
        if (category.equals(selectedCategory)) {
            selectedCategory = null;
        } else {
            selectedCategory = category;
        }
        updateCategoryChipSelection();
    }

    // ==================== 编辑模式数据填充 ====================

    private void populateExistingTask() {
        if (existingTask == null) return;
        String title = existingTask.getTitle();
        etTitle.setText(title != null ? title : "");
        String desc = existingTask.getDescription();
        if (desc != null && !desc.isEmpty()) etDescription.setText(desc);
        selectedPriority = existingTask.getPriority();
        updateChipSelection();
        selectedCategory = existingTask.getCategory();
        updateCategoryChipSelection();
        deadlineTime = existingTask.getReminderTime();
        if (deadlineTime > 0) {
            tvReminder.setText(formatDateTime(deadlineTime));
            btnClearReminder.setVisibility(View.VISIBLE);
        }
    }

    private void updateChipSelection() {
        chipHigh.setChecked(selectedPriority == Task.PRIORITY_HIGH);
        chipMedium.setChecked(selectedPriority == Task.PRIORITY_MEDIUM);
        chipLow.setChecked(selectedPriority == Task.PRIORITY_LOW);
        chipNone.setChecked(selectedPriority == Task.PRIORITY_NONE);
    }

    private void updateCategoryChipSelection() {
        chipCatWork.setChecked("Work".equals(selectedCategory));
        chipCatPersonal.setChecked("Personal".equals(selectedCategory));
        chipCatStudy.setChecked("Study".equals(selectedCategory));
        chipCatOther.setChecked("Other".equals(selectedCategory));
    }

    // ==================== 日期时间选择 ====================

    private void showDateTimePicker() {
        if (isFinishing() || isDestroyed()) return;
        Calendar calendar = Calendar.getInstance();
        if (deadlineTime > 0) calendar.setTimeInMillis(deadlineTime);
        try {
            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);
                        showTimePicker(calendar);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "无法打开日期选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTimePicker(Calendar calendar) {
        if (isFinishing() || isDestroyed()) return;
        try {
            new TimePickerDialog(this,
                    (view1, hourOfDay, minute) -> {
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        calendar.set(Calendar.MINUTE, minute);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 0);
                        deadlineTime = calendar.getTimeInMillis();
                        if (deadlineTime <= System.currentTimeMillis()) {
                            Toast.makeText(AddTaskActivity.this,
                                    "截止时间必须是将来的时间", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        tvReminder.setText(formatDateTime(deadlineTime));
                        btnClearReminder.setVisibility(View.VISIBLE);
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true).show();
        } catch (Exception e) {
            Toast.makeText(this, "无法打开时间选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatDateTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    // ==================== 保存任务 ====================

    private void saveTask() {
        if (isFinishing() || isDestroyed()) return;

        if (isEditMode && !dataLoaded) {
            Toast.makeText(this, "任务数据加载中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入任务标题", Toast.LENGTH_SHORT).show();
            return;
        }

        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";

        final Task task;
        if (existingTask != null) {
            task = existingTask;
        } else {
            task = new Task();
        }

        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(selectedPriority);
        task.setCategory(selectedCategory);
        task.setReminderTime(deadlineTime);

        if (existingTask != null) {
            // ===== 编辑模式 =====
            try { MainActivity.cancelAlarmForTask(this, task.getId()); }
            catch (Exception e) { e.printStackTrace(); }

            taskViewModel.update(task);

            boolean alarmOk = deadlineTime > 0 && scheduleDeadlineReminders(task);
            String msg = alarmOk ? "任务已更新，提醒已设置" : "任务已更新";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // ===== 新建模式：插入后回主线程设置闹钟 =====
            final long deadline = deadlineTime;
            taskViewModel.insert(task, newId -> {
                if (isFinishing() || isDestroyed()) return;
                task.setId(newId);

                // 必须回主线程操作 AlarmManager
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    boolean alarmOk = false;
                    if (deadline > 0) {
                        alarmOk = scheduleDeadlineReminders(task);
                    }
                    String msg = alarmOk ? "任务已保存，提醒已设置"
                            : (deadline > 0 ? "任务已保存（提醒设置失败，请检查权限）" : "任务已保存");
                    Toast.makeText(AddTaskActivity.this, msg, Toast.LENGTH_SHORT).show();
                    finish();
                });
            });
        }
    }

    // ==================== 闹钟调度 ====================

    /**
     * 调度截止前48h、24h以及截止时的提醒闹钟。返回是否成功。
     * 必须在主线程调用。
     */
    private boolean scheduleDeadlineReminders(Task task) {
        long deadline = task.getReminderTime();
        if (deadline <= System.currentTimeMillis()) return false;

        final long hourMs = MainActivity.DEBUG_FAST_REMINDERS
                ? 1000L : 60 * 60 * 1000L;

        long reminder48h = deadline - 48 * hourMs;
        long reminder24h = deadline - 24 * hourMs;

        boolean ok48 = false, ok24 = false, okDeadline = false;

        if (reminder48h > System.currentTimeMillis()) {
            ok48 = MainActivity.setExactAlarm(this, task, reminder48h, 48);
        }
        if (reminder24h > System.currentTimeMillis()) {
            ok24 = MainActivity.setExactAlarm(this, task, reminder24h, 24);
        }
        // 截止时刻提醒
        if (deadline > System.currentTimeMillis()) {
            okDeadline = MainActivity.setExactAlarm(this, task, deadline, 0);
        }

        // 只要能设置至少一个就算成功
        return ok48 || ok24 || okDeadline;
    }
}
