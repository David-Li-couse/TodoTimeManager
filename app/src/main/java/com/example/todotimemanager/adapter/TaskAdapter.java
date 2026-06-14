package com.example.todotimemanager.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todotimemanager.R;
import com.example.todotimemanager.database.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private final OnTaskClickListener listener;
    private final int layoutResId;
    // SimpleDateFormat 实例按需创建，避免静态持有的线程安全问题
    private final ThreadLocal<SimpleDateFormat> reminderFormat =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()));
    private final ThreadLocal<SimpleDateFormat> deletedFormat =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()));

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
        void onTaskChecked(Task task, boolean isChecked);
        void onTaskDelete(Task task);
        void onTaskRestore(Task task);
        void onTaskDeletePermanent(Task task);
    }

    public TaskAdapter(OnTaskClickListener listener) {
        this(listener, R.layout.task_item);
    }

    public TaskAdapter(OnTaskClickListener listener, @LayoutRes int layoutResId) {
        this.listener = listener;
        this.layoutResId = layoutResId;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(layoutResId, parent, false);
        return new TaskViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task currentTask = tasks.get(position);
        holder.bind(currentTask);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void setTasks(List<Task> newTasks) {
        if (newTasks == null) {
            newTasks = new ArrayList<>();
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TaskDiffCallback(this.tasks, newTasks));
        this.tasks = new ArrayList<>(newTasks);
        diffResult.dispatchUpdatesTo(this);
    }

    public Task getTaskAt(int position) {
        if (position >= 0 && position < tasks.size()) {
            return tasks.get(position);
        }
        return null;
    }

    private int getPriorityColorResource(int priority) {
        switch (priority) {
            case Task.PRIORITY_HIGH: return R.color.priorityHigh;
            case Task.PRIORITY_MEDIUM: return R.color.priorityMedium;
            case Task.PRIORITY_LOW: return R.color.priorityLow;
            default: return R.color.gray;
        }
    }

    private int getPriorityBgColorResource(int priority) {
        switch (priority) {
            case Task.PRIORITY_HIGH: return R.color.priorityHighBg;
            case Task.PRIORITY_MEDIUM: return R.color.priorityMediumBg;
            case Task.PRIORITY_LOW: return R.color.priorityLowBg;
            default: return R.color.lightGray;
        }
    }

    private String getPriorityText(int priority) {
        switch (priority) {
            case Task.PRIORITY_HIGH: return "高";
            case Task.PRIORITY_MEDIUM: return "中";
            case Task.PRIORITY_LOW: return "低";
            case Task.PRIORITY_NONE: return "无";
            default: return "";
        }
    }

    private String getCategoryText(String category) {
        if (category == null || category.isEmpty()) return null;
        switch (category) {
            case "Work": return "工作";
            case "Personal": return "个人";
            case "Study": return "学习";
            case "Other": return "其他";
            default: return category;
        }
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvDescription;
        private final TextView tvPriority;
        private final TextView tvReminder;
        private final ImageView ivCalendar;
        private final CheckBox cbCompleted;
        private final View priorityIndicator;
        private final ImageView btnDelete;
        private final TextView tvCategory;
        private final TextView tvDeletedLabel;
        private final Context context;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            context = itemView.getContext();
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvPriority = itemView.findViewById(R.id.tv_priority);
            tvReminder = itemView.findViewById(R.id.tv_reminder);
            ivCalendar = itemView.findViewById(R.id.iv_calendar);
            cbCompleted = itemView.findViewById(R.id.cb_completed);
            priorityIndicator = itemView.findViewById(R.id.priority_indicator);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvDeletedLabel = itemView.findViewById(R.id.tv_deleted_label);
        }

        void bind(Task task) {
            boolean isDeleted = task.getDeletedAt() > 0;

            tvTitle.setText(task.getTitle());

            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                tvDescription.setText(task.getDescription());
                tvDescription.setVisibility(View.VISIBLE);
            } else {
                tvDescription.setVisibility(View.GONE);
            }

            if (isDeleted) {
                cbCompleted.setVisibility(View.GONE);
            } else {
                cbCompleted.setVisibility(View.VISIBLE);
                cbCompleted.setOnCheckedChangeListener(null);
                cbCompleted.setChecked(task.isCompleted());

                int priorityColor = ContextCompat.getColor(context, getPriorityColorResource(task.getPriority()));
                cbCompleted.setButtonTintList(ColorStateList.valueOf(priorityColor));

                if (task.isCompleted()) {
                    cbCompleted.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray)));
                }

                cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && pos < tasks.size() && listener != null) {
                        listener.onTaskChecked(tasks.get(pos), isChecked);
                    }
                });
            }

            if (isDeleted) {
                tvTitle.setAlpha(0.6f);
                tvDescription.setAlpha(0.6f);
            } else {
                float alpha = task.isCompleted() ? 0.5f : 1.0f;
                tvTitle.setAlpha(alpha);
                tvDescription.setAlpha(alpha);
            }

            int priority = task.getPriority();
            tvPriority.setText(getPriorityText(priority));
            int prioBgColor = ContextCompat.getColor(context, getPriorityBgColorResource(priority));
            int prioColor = ContextCompat.getColor(context, getPriorityColorResource(priority));

            // 使用 setBackgroundTintList 替代 mutate().setTint()，避免创建Drawable副本
            tvPriority.setBackgroundTintList(ColorStateList.valueOf(prioBgColor));
            tvPriority.setTextColor(prioColor);
            priorityIndicator.setBackgroundColor(prioColor);

            String category = task.getCategory();
            if (category != null && !category.isEmpty()) {
                String categoryText = getCategoryText(category);
                tvCategory.setText(categoryText);
                tvCategory.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.primaryContainer)));
                tvCategory.setTextColor(ContextCompat.getColor(context, R.color.primary));
                tvCategory.setVisibility(View.VISIBLE);
            } else {
                tvCategory.setVisibility(View.GONE);
            }

            if (isDeleted) {
                tvReminder.setVisibility(View.GONE);
                ivCalendar.setVisibility(View.GONE);
                if (tvDeletedLabel != null) {
                    String deletedText = context.getString(R.string.deleted_at,
                            deletedFormat.get().format(new Date(task.getDeletedAt())));
                    tvDeletedLabel.setText(deletedText);
                    tvDeletedLabel.setVisibility(View.VISIBLE);
                }
            } else {
                if (tvDeletedLabel != null) {
                    tvDeletedLabel.setVisibility(View.GONE);
                }
                if (task.getReminderTime() > 0) {
                    Date date = new Date(task.getReminderTime());
                    tvReminder.setText(reminderFormat.get().format(date));
                    tvReminder.setVisibility(View.VISIBLE);
                    ivCalendar.setVisibility(View.VISIBLE);
                } else {
                    tvReminder.setVisibility(View.GONE);
                    ivCalendar.setVisibility(View.GONE);
                }
            }

            btnDelete.setImageResource(R.drawable.ic_delete);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < tasks.size() && listener != null) {
                    listener.onTaskClick(tasks.get(pos));
                }
            });

            btnDelete.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < tasks.size() && listener != null) {
                    Task t = tasks.get(pos);
                    if (t.getDeletedAt() > 0) {
                        listener.onTaskDeletePermanent(t);
                    } else {
                        listener.onTaskDelete(t);
                    }
                }
            });
        }
    }

    static class TaskDiffCallback extends DiffUtil.Callback {
        private final List<Task> oldList;
        private final List<Task> newList;

        TaskDiffCallback(List<Task> oldList, List<Task> newList) {
            this.oldList = oldList != null ? oldList : new ArrayList<>();
            this.newList = newList != null ? newList : new ArrayList<>();
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (oldItemPosition < 0 || oldItemPosition >= oldList.size()) return false;
            if (newItemPosition < 0 || newItemPosition >= newList.size()) return false;
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (oldItemPosition < 0 || oldItemPosition >= oldList.size()) return false;
            if (newItemPosition < 0 || newItemPosition >= newList.size()) return false;
            Task oldTask = oldList.get(oldItemPosition);
            Task newTask = newList.get(newItemPosition);
            String oldCat = oldTask.getCategory();
            String newCat = newTask.getCategory();
            boolean catSame = (oldCat == null) ? (newCat == null) : oldCat.equals(newCat);
            return oldTask.getTitle().equals(newTask.getTitle())
                    && oldTask.isCompleted() == newTask.isCompleted()
                    && oldTask.getPriority() == newTask.getPriority()
                    && oldTask.getReminderTime() == newTask.getReminderTime()
                    && oldTask.getDeletedAt() == newTask.getDeletedAt()
                    && catSame
                    && (oldTask.getDescription() == null
                    ? newTask.getDescription() == null
                    : oldTask.getDescription().equals(newTask.getDescription()));
        }
    }
}
