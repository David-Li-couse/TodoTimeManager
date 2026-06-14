package com.example.todotimemanager;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todotimemanager.adapter.TaskAdapter;
import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.viewmodel.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CalendarFragment extends Fragment implements SearchableFragment {
    private TaskViewModel viewModel;
    private RecyclerView taskList;
    private TaskAdapter adapter;
    private GridLayout gridCalendar;
    private TextView tvMonthYear;
    private Calendar currentCalendar = Calendar.getInstance();

    // 预计算：日期字符串 -> 该日期的任务列表（只包含未完成、未删除的任务）
    private Map<String, List<Task>> tasksByDate = new HashMap<>();
    // 有任务的日期集合（用于显示圆点）
    private Set<String> datesWithTasks = new HashSet<>();
    // 当天的任务列表
    private List<Task> currentDayTasks = new ArrayList<>();
    private String currentQuery = "";
    private Calendar selectedDate = null;

    // 重用对象避免GC
    private final SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy年MM月", Locale.getDefault());
    private GradientDrawable selectedBgDrawable; // 复用的选中背景

    // 缓存的日历单元格视图（View Pool）
    private final View[] cellContainers = new View[42];
    private final TextView[] dayTextViews = new TextView[42];
    private final View[] dotViews = new View[42];
    private boolean cellsInitialized = false;

    // 之前选中的日期索引，用于快速清除选中背景
    private int previousSelectedIndex = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvMonthYear = view.findViewById(R.id.tv_month_year);
        gridCalendar = view.findViewById(R.id.grid_calendar);
        taskList = view.findViewById(R.id.calendar_task_list);
        taskList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter((TaskAdapter.OnTaskClickListener) requireActivity());
        taskList.setAdapter(adapter);

        // 预创建复用对象
        selectedBgDrawable = new GradientDrawable();
        selectedBgDrawable.setCornerRadius(8);

        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        // 只观察一次所有任务，预建索引
        viewModel.getAllTasks().observe(getViewLifecycleOwner(), tasks -> {
            if (tasks == null) return;
            buildTaskIndex(tasks);
            refreshCalendar();
            // 如果之前已选中日期，刷新当天任务列表
            if (selectedDate != null) {
                updateCurrentDayTasks();
            }
        });

        setupWeekLabels(view);
        initCalendarCells();
        refreshCalendar();

        ImageButton btnPrev = view.findViewById(R.id.btn_prev_month);
        ImageButton btnNext = view.findViewById(R.id.btn_next_month);
        btnPrev.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            refreshCalendar();
        });
        btnNext.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            refreshCalendar();
        });
    }

    /**
     * 预建任务日期索引，将O(n)扫描优化为O(1)查找
     */
    private void buildTaskIndex(List<Task> tasks) {
        tasksByDate.clear();
        datesWithTasks.clear();
        for (Task t : tasks) {
            if (t.getDeletedAt() > 0) continue;
            if (t.isCompleted()) continue;
            long reminder = t.getReminderTime();
            if (reminder > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(reminder);
                String dateStr = sdfKey.format(cal.getTime());
                datesWithTasks.add(dateStr);
                List<Task> list = tasksByDate.get(dateStr);
                if (list == null) {
                    list = new ArrayList<>();
                    tasksByDate.put(dateStr, list);
                }
                list.add(t);
            }
        }
    }

    /**
     * 初始化日历单元格（只创建一次，后续只更新内容）
     */
    private void initCalendarCells() {
        if (cellsInitialized) return;
        Context ctx = getContext();
        if (ctx == null) return; // Fragment已detach，安全返回

        gridCalendar.removeAllViews();

        for (int i = 0; i < 42; i++) {
            FrameLayout container = new FrameLayout(ctx);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            container.setLayoutParams(params);
            container.setPadding(4, 4, 4, 4);

            TextView dayView = new TextView(ctx);
            dayView.setGravity(Gravity.CENTER);
            dayView.setPadding(8, 16, 8, 16);
            dayView.setTextSize(14);
            FrameLayout.LayoutParams tvParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dayView.setLayoutParams(tvParams);

            View dot = new View(ctx);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(12, 12);
            dotParams.gravity = Gravity.BOTTOM | Gravity.CENTER;
            dotParams.bottomMargin = 4;
            dot.setLayoutParams(dotParams);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(ContextCompat.getColor(ctx, R.color.primary));
            dot.setBackground(dotBg);
            dot.setVisibility(View.GONE);

            container.addView(dayView);
            container.addView(dot);
            gridCalendar.addView(container);

            cellContainers[i] = container;
            dayTextViews[i] = dayView;
            dotViews[i] = dot;

            final int index = i;
            dayView.setOnClickListener(v -> onDateClicked(index));
        }
        cellsInitialized = true;
    }

    /**
     * 处理日期点击 - 复用GradientDrawable
     */
    private void onDateClicked(int index) {
        String dateStr = (String) dayTextViews[index].getTag();
        if (dateStr == null) return; // 非当月日期

        // 清除之前的选中背景
        clearSelectedBackground();

        // 设置新的选中背景（复用drawable）
        if (getContext() != null) {
            selectedBgDrawable.setColor(ContextCompat.getColor(getContext(), R.color.selectedDateBg));
        }
        dayTextViews[index].setBackground(selectedBgDrawable);
        previousSelectedIndex = index;

        // 更新选中日期
        if (selectedDate == null) selectedDate = Calendar.getInstance();
        try {
            java.util.Date parsed = sdfKey.parse(dateStr);
            if (parsed != null) {
                selectedDate.setTime(parsed);
            }
        } catch (Exception e) {
            // ignore parse error
        }

        updateCurrentDayTasks();
    }

    /**
     * 使用预建索引快速获取当天任务
     */
    private void updateCurrentDayTasks() {
        if (selectedDate == null) return;
        String key = sdfKey.format(selectedDate.getTime());
        List<Task> tasks = tasksByDate.get(key);
        currentDayTasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
        applyFilter();
    }

    private String getMonthYearString() {
        return monthFormat.format(currentCalendar.getTime());
    }

    private void setupWeekLabels(View view) {
        Context ctx = getContext();
        if (ctx == null) return;

        GridLayout weekGrid = view.findViewById(R.id.grid_week);
        weekGrid.removeAllViews();
        String[] weekDays = {"一", "二", "三", "四", "五", "六", "日"};
        for (String day : weekDays) {
            TextView tv = new TextView(ctx);
            tv.setText(day);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(8, 8, 8, 8);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
            tv.setTextSize(14);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            tv.setLayoutParams(params);
            weekGrid.addView(tv);
        }
    }

    /**
     * 刷新日历显示 - 不再重建视图，只更新内容
     */
    private void refreshCalendar() {
        if (!cellsInitialized) return;
        Context ctx = getContext();
        if (ctx == null) return;

        String monthYear = getMonthYearString();
        tvMonthYear.setText(monthYear);

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (firstDayOfWeek - Calendar.MONDAY + 7) % 7;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar dateCal = (Calendar) cal.clone();
        boolean foundSelected = false;

        for (int i = 0; i < 42; i++) {
            int dayNumber = i - offset + 1;
            boolean isInMonth = (dayNumber >= 1 && dayNumber <= daysInMonth);

            if (isInMonth) {
                dayTextViews[i].setText(String.valueOf(dayNumber));
                dayTextViews[i].setEnabled(true);
                dateCal.set(Calendar.DAY_OF_MONTH, dayNumber);
                String dateStr = sdfKey.format(dateCal.getTime());
                dayTextViews[i].setTag(dateStr);

                dotViews[i].setVisibility(datesWithTasks.contains(dateStr) ? View.VISIBLE : View.GONE);

                if (selectedDate != null) {
                    String selectedStr = sdfKey.format(selectedDate.getTime());
                    if (dateStr.equals(selectedStr)) {
                        selectedBgDrawable.setColor(ContextCompat.getColor(ctx, R.color.selectedDateBg));
                        dayTextViews[i].setBackground(selectedBgDrawable);
                        previousSelectedIndex = i;
                        foundSelected = true;
                    } else {
                        dayTextViews[i].setBackground(null);
                    }
                } else {
                    dayTextViews[i].setBackground(null);
                }
            } else {
                dayTextViews[i].setText("");
                dayTextViews[i].setEnabled(false);
                dayTextViews[i].setTag(null);
                dayTextViews[i].setBackground(null);
                dotViews[i].setVisibility(View.GONE);
            }
        }

        if (!foundSelected) {
            previousSelectedIndex = -1;
        }
    }

    private void clearSelectedBackground() {
        if (previousSelectedIndex >= 0 && previousSelectedIndex < 42) {
            dayTextViews[previousSelectedIndex].setBackground(null);
        }
        // 兼容旧逻辑：遍历清除所有背景（兜底）
        if (previousSelectedIndex < 0) {
            for (int i = 0; i < 42; i++) {
                dayTextViews[i].setBackground(null);
            }
        }
    }

    private void applyFilter() {
        if (currentQuery.isEmpty()) {
            adapter.setTasks(currentDayTasks);
        } else {
            List<Task> filtered = new ArrayList<>();
            String lowerQuery = currentQuery.toLowerCase();
            for (Task t : currentDayTasks) {
                if ((t.getTitle() != null && t.getTitle().toLowerCase().contains(lowerQuery)) ||
                        (t.getDescription() != null && t.getDescription().toLowerCase().contains(lowerQuery))) {
                    filtered.add(t);
                }
            }
            adapter.setTasks(filtered);
        }
    }

    private long getStartOfDay(Calendar cal) {
        Calendar clone = (Calendar) cal.clone();
        clone.set(Calendar.HOUR_OF_DAY, 0);
        clone.set(Calendar.MINUTE, 0);
        clone.set(Calendar.SECOND, 0);
        clone.set(Calendar.MILLISECOND, 0);
        return clone.getTimeInMillis();
    }

    private long getEndOfDay(Calendar cal) {
        Calendar clone = (Calendar) cal.clone();
        clone.set(Calendar.HOUR_OF_DAY, 23);
        clone.set(Calendar.MINUTE, 59);
        clone.set(Calendar.SECOND, 59);
        clone.set(Calendar.MILLISECOND, 999);
        return clone.getTimeInMillis();
    }

    @Override
    public void onSearch(String query) {
        this.currentQuery = query;
        applyFilter();
    }
}
