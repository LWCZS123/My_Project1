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

    private List<String> months = new ArrayList<>();
    private int selectedPosition = -1;
    private OnMonthClickListener listener;

    public interface OnMonthClickListener {
        void onMonthClick(int month);
    }

    public MonthAdapter(OnMonthClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<String> newItems, int selectedIndex) {
        this.months = newItems;
        this.selectedPosition = selectedIndex;
        notifyDataSetChanged();
    }

    public void setSelectedMonth(int month) {
        int old = selectedPosition;
        selectedPosition = month - 1;
        if (old >= 0 && old < months.size()) notifyItemChanged(old);
        if (selectedPosition >= 0 && selectedPosition < months.size()) notifyItemChanged(selectedPosition);
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
        holder.textView.setText(months.get(position));
        boolean selected = position == selectedPosition;
        
        if (selected) {
            holder.textView.setBackgroundResource(R.drawable.bg_tab_selected_white);
            holder.textView.setTextColor(0xFF333333);
        } else {
            holder.textView.setBackgroundResource(R.drawable.bg_capsule_gray);
            holder.textView.setTextColor(0xFF999999);
            holder.textView.setElevation(0f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMonthClick(position + 1);
        });
    }

    @Override
    public int getItemCount() {
        return months.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }
}
