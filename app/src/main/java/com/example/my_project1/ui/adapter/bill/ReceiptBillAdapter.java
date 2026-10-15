package com.example.my_project1.ui.adapter.bill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ReceiptBillAdapter
 * -----------------------------------------------------------
 * 今日小票专属 RecyclerView 适配器：支持相同分类自动合并与笔数累加
 */
public class ReceiptBillAdapter extends RecyclerView.Adapter<ReceiptBillAdapter.ViewHolder> {

    /**
     * 内部聚合数据模型：分类名、图标、类型、笔数、总金额
     */
    public static class GroupedReceiptItem {
        private final String categoryName;
        private final String categoryIconUrl;
        private final int type; // 0: 支出, 1: 收入
        private int count;
        private double totalAmount;

        public GroupedReceiptItem(String categoryName, String categoryIconUrl, int type, int count, double totalAmount) {
            this.categoryName = categoryName;
            this.categoryIconUrl = categoryIconUrl;
            this.type = type;
            this.count = count;
            this.totalAmount = totalAmount;
        }

        public String getCategoryName() { return categoryName; }
        public String getCategoryIconUrl() { return categoryIconUrl; }
        public int getType() { return type; }
        public int getCount() { return count; }
        public double getTotalAmount() { return totalAmount; }

        public void addAmount(double amount) {
            this.totalAmount += amount;
            this.count++;
        }
    }

    private final List<GroupedReceiptItem> groupedList = new ArrayList<>();

    public void setBills(List<Bill> bills) {
        groupedList.clear();
        if (bills != null && !bills.isEmpty()) {
            Map<String, GroupedReceiptItem> map = new LinkedHashMap<>();
            for (Bill b : bills) {
                if (b == null || b.isExcludeStat()) continue;

                String catName = b.getCategoryName();
                if (catName == null || catName.trim().isEmpty()) {
                    catName = "其他";
                }
                catName = catName.trim();

                int type = b.getType(); // 0: 支出, 1: 收入
                String key = type + "_" + catName;

                if (map.containsKey(key)) {
                    GroupedReceiptItem item = map.get(key);
                    if (item != null) {
                        item.addAmount(b.getAmount());
                    }
                } else {
                    map.put(key, new GroupedReceiptItem(
                            catName,
                            b.getCategoryIconUrl(),
                            type,
                            1,
                            b.getAmount()
                    ));
                }
            }
            groupedList.addAll(map.values());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_receipt_bill, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GroupedReceiptItem item = groupedList.get(position);

        holder.tvCategoryName.setText(item.getCategoryName());

        // 显示相同的分类包含的账单笔数 (例如 x 3)
        if (item.getCount() > 1) {
            holder.tvCategoryCount.setText("x " + item.getCount());
            holder.tvCategoryCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvCategoryCount.setText("x 1");
            holder.tvCategoryCount.setVisibility(View.VISIBLE);
        }

        // 加载分类图标
        String iconUrl = item.getCategoryIconUrl();
        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            ImageLoaderUtils.loadThumbnail(holder.itemView.getContext(), iconUrl, holder.ivCategoryIcon);
        } else {
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_default_category);
        }

        // 相同分类累加加算的总金额
        double amount = item.getTotalAmount();
        if (item.getType() == 1) { // 收入
            holder.tvAmount.setText(String.format(Locale.CHINA, "¥%.2f", amount));
            holder.tvAmount.setTextColor(0xFF1B5E20); // 深绿色
        } else { // 支出 (type == 0 或其他)
            holder.tvAmount.setText(String.format(Locale.CHINA, "-¥%.2f", amount));
            holder.tvAmount.setTextColor(0xFF222222); // 主黑字
        }
    }

    @Override
    public int getItemCount() {
        return groupedList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCategoryIcon;
        TextView tvCategoryName;
        TextView tvCategoryCount;
        TextView tvAmount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryCount = itemView.findViewById(R.id.tvCategoryCount);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
