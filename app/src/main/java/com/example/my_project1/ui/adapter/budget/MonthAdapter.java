package com.example.my_project1.ui.adapter.budget;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;

import java.util.ArrayList;
import java.util.List;

public class MonthAdapter extends RecyclerView.Adapter<MonthAdapter.ViewHolder> {

    public static class PeriodItem {
        public String label;
        public long startTime;
        public long endTime;
        public boolean selected;
        
        public PeriodItem(String label, long startTime, long endTime) {
            this.label = label;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private List<PeriodItem> items = new ArrayList<>();
    private OnPeriodClickListener listener;

    public interface OnPeriodClickListener {
        void onPeriodClick(PeriodItem item);
    }

    public MonthAdapter(OnPeriodClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<PeriodItem> newItems) {
        List<PeriodItem> oldItems = this.items;
        List<PeriodItem> replacement = new ArrayList<>(newItems);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldItems.size(); }
            @Override public int getNewListSize() { return replacement.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos).startTime == replacement.get(newPos).startTime;
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                PeriodItem oldItem = oldItems.get(oldPos);
                PeriodItem newItem = replacement.get(newPos);
                return oldItem.endTime == newItem.endTime
                        && oldItem.selected == newItem.selected
                        && oldItem.label.equals(newItem.label);
            }
        });
        this.items = replacement;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget_month, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PeriodItem item = items.get(position);
        holder.textView.setText(item.label);
        
        if (item.selected) {
            holder.textView.setBackgroundResource(R.drawable.bg_tab_selected_white);
            holder.textView.setTextColor(0xFF222222);
        } else {
            holder.textView.setBackgroundResource(R.drawable.bg_capsule_gray);
            holder.textView.setTextColor(0xFF999999);
            holder.textView.setElevation(0f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPeriodClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }
}
