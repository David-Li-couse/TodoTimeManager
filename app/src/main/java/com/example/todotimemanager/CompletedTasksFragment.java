package com.example.todotimemanager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import java.util.List;

public class CompletedTasksFragment extends Fragment implements SearchableFragment {
    private TaskViewModel viewModel;
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private LinearLayout llEmpty;
    private TextView tvEmpty;
    private List<Task> originalTasks = new ArrayList<>();
    private String currentQuery = "";

    private MaterialCardView cardStats;
    private TextView tvStatsTotal;
    private TextView tvStatsPercent;
    private ProgressBar progressBar;
    private TextView tvStatsPending;
    private TextView tvStatsCompleted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_completed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.recycler_view_completed);
        llEmpty = view.findViewById(R.id.ll_empty_completed);
        tvEmpty = view.findViewById(R.id.tv_empty_completed);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter((TaskAdapter.OnTaskClickListener) requireActivity());
        recyclerView.setAdapter(adapter);

        cardStats = view.findViewById(R.id.card_stats);
        tvStatsTotal = view.findViewById(R.id.tv_stats_total);
        tvStatsPercent = view.findViewById(R.id.tv_stats_percent);
        progressBar = view.findViewById(R.id.progress_bar);
        tvStatsPending = view.findViewById(R.id.tv_stats_pending);
        tvStatsCompleted = view.findViewById(R.id.tv_stats_completed);

        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        viewModel.getCompletedTasks().observe(getViewLifecycleOwner(), tasks -> {
            originalTasks = tasks != null ? tasks : new ArrayList<>();
            applyFilter();
            updateStatistics();
        });

        viewModel.getAllTasks().observe(getViewLifecycleOwner(), tasks -> {
            updateStatistics();
        });
    }

    private void updateStatistics() {
        // 双重检查，防止Fragment detach后访问View导致崩溃
        if (getContext() == null || !isAdded() || cardStats == null) return;

        List<Task> allTasks = viewModel.getAllTasks().getValue();
        if (allTasks == null) allTasks = new ArrayList<>();
        int total = allTasks.size();

        if (total == 0) {
            cardStats.setVisibility(View.GONE);
            return;
        }
        cardStats.setVisibility(View.VISIBLE);

        List<Task> pendingTasks = viewModel.getPendingTasks().getValue();
        if (pendingTasks == null) pendingTasks = new ArrayList<>();
        int pendingCount = pendingTasks.size();
        int completedCount = total - pendingCount;
        int percent = (completedCount * 100) / total;

        tvStatsTotal.setText(getString(R.string.stats_total_format, total));
        tvStatsPercent.setText(getString(R.string.stats_percent_format, percent));
        progressBar.setProgress(percent);
        tvStatsPending.setText(getString(R.string.stats_pending_format, pendingCount));
        tvStatsCompleted.setText(getString(R.string.stats_completed_format, completedCount));
    }

    private void applyFilter() {
        List<Task> filtered;
        if (currentQuery.isEmpty()) {
            filtered = new ArrayList<>(originalTasks);
        } else {
            filtered = new ArrayList<>();
            String lowerQuery = currentQuery.toLowerCase();
            for (Task t : originalTasks) {
                if ((t.getTitle() != null && t.getTitle().toLowerCase().contains(lowerQuery)) ||
                        (t.getDescription() != null && t.getDescription().toLowerCase().contains(lowerQuery))) {
                    filtered.add(t);
                }
            }
        }
        adapter.setTasks(filtered);
        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            llEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("暂无已完成任务");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            llEmpty.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSearch(String query) {
        this.currentQuery = query;
        applyFilter();
    }
}
