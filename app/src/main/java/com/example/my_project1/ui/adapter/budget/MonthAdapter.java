package com.example.my_project1.ui.adapter.budget;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        this.items = newItems;
        notifyDataSetChanged();
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
            holder.textView.setTextColor(0xFF333333);
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
