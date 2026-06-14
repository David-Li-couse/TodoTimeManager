package com.example.todotimemanager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todotimemanager.adapter.TaskAdapter;
import com.example.todotimemanager.database.Task;
import com.example.todotimemanager.viewmodel.TaskViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuadrantFragment extends Fragment implements SearchableFragment {
    private TaskViewModel viewModel;
    private Map<Integer, List<Task>> originalQuadrantTasks = new HashMap<>();
    private Map<Integer, RecyclerView> recyclerMap = new HashMap<>();
    private Map<Integer, TextView> moreMap = new HashMap<>();
    private Map<Integer, TaskAdapter> adapterMap = new HashMap<>();
    private TaskAdapter.OnTaskClickListener activityListener;
    private String currentQuery = "";
    private View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_quadrant, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        if (requireActivity() instanceof TaskAdapter.OnTaskClickListener) {
            activityListener = (TaskAdapter.OnTaskClickListener) requireActivity();
        }

        setupQuadrant(view, R.id.card_important_urgent, "重要且紧急", "高优先级", Task.PRIORITY_HIGH);
        setupQuadrant(view, R.id.card_important_not_urgent, "重要不紧急", "中优先级", Task.PRIORITY_MEDIUM);
        setupQuadrant(view, R.id.card_not_important_urgent, "不重要但紧急", "低优先级", Task.PRIORITY_LOW);
        setupQuadrant(view, R.id.card_not_important_not_urgent, "不重要不紧急", "无优先级", Task.PRIORITY_NONE);

        viewModel.getPendingTasks().observe(getViewLifecycleOwner(), tasks -> {
            if (tasks == null) tasks = new ArrayList<>();
            originalQuadrantTasks.clear();
            for (Task t : tasks) {
                int priority = t.getPriority();
                if (!originalQuadrantTasks.containsKey(priority)) {
                    originalQuadrantTasks.put(priority, new ArrayList<>());
                }
                originalQuadrantTasks.get(priority).add(t);
            }
            applyFilter();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 修复首次显示空白问题：
        // 嵌套权重布局中，RecyclerView在onViewCreated时可能高度为0，
        // LiveData observer触发的applyFilter()此时数据虽已设置但不可见。
        // post确保在View完成measure/layout后重新刷数据。
        if (rootView != null) {
            rootView.post(() -> {
                rootView.requestLayout();
                applyFilter();
            });
        }
    }

    private void setupQuadrant(View parent, int cardId, String title, String subtitle, int priority) {
        MaterialCardView card = parent.findViewById(cardId);
        TextView tvTitle = card.findViewById(R.id.quadrant_title);
        TextView tvSub = card.findViewById(R.id.quadrant_subtitle);
        RecyclerView rv = card.findViewById(R.id.quadrant_recycler);
        TextView more = card.findViewById(R.id.quadrant_more);

        tvTitle.setText(title);
        switch (priority) {
            case Task.PRIORITY_HIGH:
                tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.priorityHigh));
                break;
            case Task.PRIORITY_MEDIUM:
                tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.priorityMedium));
                break;
            case Task.PRIORITY_LOW:
                tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.priorityLow));
                break;
            case Task.PRIORITY_NONE:
                tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.gray));
                break;
            default:
                tvTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.black));
                break;
        }
        tvSub.setText(subtitle);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setNestedScrollingEnabled(true);
        TaskAdapter adapter = new TaskAdapter(activityListener, R.layout.task_item_compact);
        adapterMap.put(priority, adapter);
        rv.setAdapter(adapter);
        recyclerMap.put(priority, rv);
        moreMap.put(priority, more);
    }

    private void applyFilter() {
        Map<Integer, List<Task>> filteredMap;
        if (currentQuery.isEmpty()) {
            filteredMap = originalQuadrantTasks;
        } else {
            filteredMap = new HashMap<>();
            String lowerQuery = currentQuery.toLowerCase();
            for (Map.Entry<Integer, List<Task>> entry : originalQuadrantTasks.entrySet()) {
                List<Task> filteredList = new ArrayList<>();
                for (Task t : entry.getValue()) {
                    if ((t.getTitle() != null && t.getTitle().toLowerCase().contains(lowerQuery)) ||
                            (t.getDescription() != null && t.getDescription().toLowerCase().contains(lowerQuery))) {
                        filteredList.add(t);
                    }
                }
                filteredMap.put(entry.getKey(), filteredList);
            }
        }

        for (int priority : new int[]{Task.PRIORITY_HIGH, Task.PRIORITY_MEDIUM, Task.PRIORITY_LOW, Task.PRIORITY_NONE}) {
            TaskAdapter adapter = adapterMap.get(priority);
            if (adapter != null) {
                List<Task> list = filteredMap.getOrDefault(priority, new ArrayList<>());
                adapter.setTasks(list);
                TextView more = moreMap.get(priority);
                if (more != null) {
                    more.setVisibility(list.size() > 3 ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    @Override
    public void onSearch(String query) {
        this.currentQuery = query;
        applyFilter();
    }
}
