package com.example.my_project1.ui.adapter.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.databinding.ItemSearchSummaryCardBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SearchSummaryAdapter - 搜索汇总卡片适配器
 */
public class SearchSummaryAdapter extends RecyclerView.Adapter<SearchSummaryAdapter.ViewHolder> {

    private final List<SummaryItem> items = new ArrayList<>();

    public void setSummary(SearchSummary summary) {
        items.clear();
        if (summary != null) {
            items.add(new SummaryItem("支出", String.format(Locale.getDefault(), "¥ %.2f", summary.getExpenseTotal())));
            items.add(new SummaryItem("收入", String.format(Locale.getDefault(), "¥ %.2f", summary.getIncomeTotal())));
            items.add(new SummaryItem("结余", String.format(Locale.getDefault(), "¥ %.2f", summary.getNetAmount())));
            items.add(new SummaryItem("账单数", String.valueOf(summary.getBillCount())));
            items.add(new SummaryItem("涉及天数", String.valueOf(summary.getBillDays())));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSearchSummaryCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSummaryCardBinding binding;
        ViewHolder(ItemSearchSummaryCardBinding binding) { super(binding.getRoot()); this.binding = binding; }
        void bind(SummaryItem item) {
            binding.tvLabel.setText(item.label);
            binding.tvValue.setText(item.value);
        }
    }

    static class SummaryItem {
        String label;
        String value;
        SummaryItem(String label, String value) { this.label = label; this.value = value; }
    }
}
