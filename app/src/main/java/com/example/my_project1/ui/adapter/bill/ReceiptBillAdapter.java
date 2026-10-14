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
import java.util.List;
import java.util.Locale;

/**
 * ReceiptBillAdapter
 * -----------------------------------------------------------
 * 今日小票专属 RecyclerView 适配器
 */
public class ReceiptBillAdapter extends RecyclerView.Adapter<ReceiptBillAdapter.ViewHolder> {

    private final List<Bill> billList = new ArrayList<>();

    public void setBills(List<Bill> bills) {
        billList.clear();
        if (bills != null) {
            billList.addAll(bills);
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
        Bill bill = billList.get(position);

        String catName = bill.getCategoryName();
        if (catName == null || catName.trim().isEmpty()) {
            catName = "其他";
        }
        holder.tvCategoryName.setText(catName);

        // 加载分类图标
        String iconUrl = bill.getCategoryIconUrl();
        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            ImageLoaderUtils.loadThumbnail(holder.itemView.getContext(), iconUrl, holder.ivCategoryIcon);
        } else {
            holder.ivCategoryIcon.setImageResource(R.drawable.ic_default_category);
        }

        double amount = bill.getAmount();
        if (bill.getType() == 1) { // 收入
            holder.tvAmount.setText(String.format(Locale.CHINA, "¥%.2f", amount));
            holder.tvAmount.setTextColor(0xFF1B5E20); // 深绿色
        } else { // 支出 (type == 0 或其他)
            holder.tvAmount.setText(String.format(Locale.CHINA, "-¥%.2f", amount));
            holder.tvAmount.setTextColor(0xFF222222); // 主黑字
        }
    }

    @Override
    public int getItemCount() {
        return billList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCategoryIcon;
        TextView tvCategoryName;
        TextView tvAmount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
