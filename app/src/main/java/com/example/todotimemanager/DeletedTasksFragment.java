package com.example.todotimemanager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

import java.util.ArrayList;
import java.util.List;

public class DeletedTasksFragment extends Fragment implements SearchableFragment {
    private TaskViewModel viewModel;
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private LinearLayout llEmpty;
    private TextView tvEmpty;
    private List<Task> originalTasks = new ArrayList<>();
    private String currentQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_deleted, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.recycler_view_deleted);
        llEmpty = view.findViewById(R.id.ll_empty_deleted);
        tvEmpty = view.findViewById(R.id.tv_empty_deleted);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter((TaskAdapter.OnTaskClickListener) requireActivity());
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        viewModel.getDeletedTasks().observe(getViewLifecycleOwner(), tasks -> {
            originalTasks = tasks != null ? tasks : new ArrayList<>();
            applyFilter();
        });
    }

    private void applyFilter() {
        if (!isAdded()) return;

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
            tvEmpty.setText("回收站为空");
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
