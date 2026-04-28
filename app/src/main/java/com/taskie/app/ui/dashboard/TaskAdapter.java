package com.taskie.app.ui.dashboard;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.taskie.app.R;
import com.taskie.app.data.model.Task;
import com.taskie.app.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-type RecyclerView adapter.
 * Handles two view types: SECTION HEADERS and TASK CARDS.
 * Supports smooth animations on bind and swipe-to-complete visuals.
 */
public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_TASK   = 1;

    private List<Object>       items    = new ArrayList<>();
    private final TaskClickListener listener;

    public TaskAdapter(TaskClickListener listener) {
        this.listener = listener;
//        setHasStableIds(true);
    }

    // ── Data submission ───────────────────────────────────────────────────────

    public void submitList(List<Object> newItems) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newItems.size(); }

            @Override
            public boolean areItemsTheSame(int o, int n) {
                Object oldItem = items.get(o);
                Object newItem = newItems.get(n);
                if (oldItem instanceof Task && newItem instanceof Task) {
                    return ((Task) oldItem).getId() == ((Task) newItem).getId();
                }
                if (oldItem instanceof DashboardFragment.SectionHeader
                        && newItem instanceof DashboardFragment.SectionHeader) {
                    return ((DashboardFragment.SectionHeader) oldItem).title
                            .equals(((DashboardFragment.SectionHeader) newItem).title);
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(int o, int n) {
                return items.get(o).equals(newItems.get(n));
            }
        });
        items = new ArrayList<>(newItems);
        result.dispatchUpdatesTo(this);
    }

    /** Flat search results with a single section header. */
    public void submitFlatList(List<Task> tasks, String sectionTitle) {
        List<Object> flat = new ArrayList<>();
        if (!tasks.isEmpty()) {
            flat.add(new DashboardFragment.SectionHeader(sectionTitle, tasks.size()));
            flat.addAll(tasks);
        }
        submitList(flat);
    }

    public Object getItemAt(int position) {
        if (position >= 0 && position < items.size()) return items.get(position);
        return null;
    }

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof Task ? VIEW_TYPE_TASK : VIEW_TYPE_HEADER;
    }

//    @Override
//    public long getItemId(int position) {
//        Object item = items.get(position);
//        if (item instanceof Task) return ((Task) item).getId();
//        if (item instanceof DashboardFragment.SectionHeader) {
//            return ((DashboardFragment.SectionHeader) item).title.hashCode();
//        }
//        return RecyclerView.NO_ID;
//    }




    @Override
    public int getItemCount() { return items.size(); }

    @Override @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_section_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_task, parent, false);
            return new TaskViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((DashboardFragment.SectionHeader) items.get(position));
        } else if (holder instanceof TaskViewHolder) {
            ((TaskViewHolder) holder).bind((Task) items.get(position));
        }
    }

    // ── ViewHolder: Section Header ────────────────────────────────────────────

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvCount;

        HeaderViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_section_title);
            tvCount = itemView.findViewById(R.id.tv_section_count);
        }

        void bind(DashboardFragment.SectionHeader header) {
            tvTitle.setText(header.title);
            tvCount.setText(String.valueOf(header.count));
        }
    }

    // ── ViewHolder: Task Card ─────────────────────────────────────────────────

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final CheckBox   cbComplete;
        private final TextView   tvTitle;
        private final TextView   tvDueDate;
        private final TextView   tvPriority;
        private final ChipGroup  chipGroup;
        private final ImageButton btnDelete;
        private final View       priorityBar;

        TaskViewHolder(View itemView) {
            super(itemView);
            cardView    = itemView.findViewById(R.id.card_task);
            cbComplete  = itemView.findViewById(R.id.cb_complete);
            tvTitle     = itemView.findViewById(R.id.tv_task_title);
            tvDueDate   = itemView.findViewById(R.id.tv_due_date);
            tvPriority  = itemView.findViewById(R.id.tv_priority);
            chipGroup   = itemView.findViewById(R.id.chip_group_tags);
            btnDelete   = itemView.findViewById(R.id.btn_delete);
            priorityBar = itemView.findViewById(R.id.view_priority_bar);
        }

        void bind(Task task) {
            tvTitle.setText(task.getTitle());

            // Strike-through and fade for completed tasks
            if (task.isCompleted()) {
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                cardView.setAlpha(0.5f);
                cbComplete.setChecked(true);
            } else {
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                cardView.setAlpha(1f);
                cbComplete.setChecked(false);
            }

            // Due date
            if (task.getDueDate() > 0) {
                tvDueDate.setVisibility(View.VISIBLE);
                tvDueDate.setText(DateUtils.formatRelative(task.getDueDate()));
                if (task.isOverdue()) {
                    tvDueDate.setTextColor(itemView.getContext().getColor(R.color.overdue));
                } else {
                    tvDueDate.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
                }
            } else {
                tvDueDate.setVisibility(View.GONE);
            }

            // Priority badge
            tvPriority.setText(task.getPriorityLabel());
            int priorityColor;
            switch (task.getPriority()) {
                case Task.PRIORITY_HIGH:
                    priorityColor = itemView.getContext().getColor(R.color.priority_high);
                    break;
                case Task.PRIORITY_MEDIUM:
                    priorityColor = itemView.getContext().getColor(R.color.priority_medium);
                    break;
                default:
                    priorityColor = itemView.getContext().getColor(R.color.priority_low);
            }
            tvPriority.setTextColor(priorityColor);
            priorityBar.setBackgroundColor(priorityColor);

            // Tags as chips
            chipGroup.removeAllViews();
            if (task.getTags() != null && !task.getTags().isEmpty()) {
                for (String tag : task.getTags().split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) {
                        Chip chip = new Chip(itemView.getContext());
                        chip.setText(trimmed);
                        chip.setTextSize(10f);
                        chip.setChipBackgroundColorResource(R.color.chip_background);
                        chip.setTextColor(itemView.getContext().getColor(R.color.accent));
                        chip.setClickable(false);
                        chip.setMinHeight(0);
                        chipGroup.addView(chip);
                    }
                }
            }

            // Entry animation
            cardView.startAnimation(
                AnimationUtils.loadAnimation(itemView.getContext(), R.anim.item_fade_in));

            // Listeners
//            cardView.setOnClickListener(v -> listener.onTaskClick(task));
//
//            // CRASH FIX: Clear listener before setting checked state to avoid recursion/recycling issues
//            cbComplete.setOnCheckedChangeListener(null);
//            cbComplete.setChecked(task.isCompleted());
//            cbComplete.setOnCheckedChangeListener((btn, checked) -> {
//                // Ensure only user-initiated clicks trigger the callback
//                if (btn.isPressed() && checked != task.isCompleted()) {
//                    listener.onTaskChecked(task, checked);
//                }
//            });

            // 🔥 FINAL SAFE CHECKBOX HANDLING

            cbComplete.setOnCheckedChangeListener(null);
            cbComplete.setChecked(task.isCompleted());

            cbComplete.setOnClickListener(v -> {
                boolean checked = cbComplete.isChecked();

                if (checked != task.isCompleted()) {
                    try {
                        listener.onTaskChecked(task, checked);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });


            btnDelete.setOnClickListener(v -> listener.onTaskDelete(task));
        }
    }

    // ── Click interface ───────────────────────────────────────────────────────

    public interface TaskClickListener {
        void onTaskClick(Task task);
        void onTaskChecked(Task task, boolean isChecked);
        void onTaskDelete(Task task);
    }
}
