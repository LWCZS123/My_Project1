package com.example.my_project1.ui.adapter.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.databinding.ItemSearchSummaryCardBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * SearchSummaryAdapter - 搜索汇总卡片适配器 (优化版：ListAdapter + DiffUtil)
 */
public class SearchSummaryAdapter extends ListAdapter<SearchSummaryAdapter.SummaryItem, SearchSummaryAdapter.ViewHolder> {

    public SearchSummaryAdapter() {
        super(new DiffUtil.ItemCallback<SummaryItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull SummaryItem oldItem, @NonNull SummaryItem newItem) {
                return oldItem.label.equals(newItem.label);
            }

            @Override
            public boolean areContentsTheSame(@NonNull SummaryItem oldItem, @NonNull SummaryItem newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    public void setSummary(SearchSummary summary) {
        List<SummaryItem> items = new ArrayList<>();
        if (summary != null) {
            // 计算结余 (Room 返回的结果可能没计算 netAmount)
            double net = summary.getIncomeTotal() - summary.getExpenseTotal();
            items.add(new SummaryItem("支出", String.format(Locale.getDefault(), "¥ %.2f", summary.getExpenseTotal())));
            items.add(new SummaryItem("收入", String.format(Locale.getDefault(), "¥ %.2f", summary.getIncomeTotal())));
            items.add(new SummaryItem("结余", String.format(Locale.getDefault(), "¥ %.2f", net)));
            items.add(new SummaryItem("账单数", String.valueOf(summary.getBillCount())));
            items.add(new SummaryItem("涉及天数", String.valueOf(summary.getBillDays())));
        }
        submitList(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSearchSummaryCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSummaryCardBinding binding;
        ViewHolder(ItemSearchSummaryCardBinding binding) { super(binding.getRoot()); this.binding = binding; }
        void bind(SummaryItem item) {
            binding.tvLabel.setText(item.label);
            binding.tvValue.setText(item.value);
        }
    }

    /**
     * 不可变汇总项模型
     */
    public static class SummaryItem {
        public final String label;
        public final String value;

        public SummaryItem(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SummaryItem that = (SummaryItem) o;
            return Objects.equals(label, that.label) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, value);
        }
    }
}
