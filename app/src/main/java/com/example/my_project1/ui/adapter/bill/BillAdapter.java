package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ItemBillBinding;
import com.example.my_project1.databinding.ItemBillDateHeaderBinding;
import com.example.my_project1.ui.adapter.photo.PhotoAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * BillAdapter (聚合卡片版) - 修复向左滑动菜单
 * -------------------------------------------------------
 * ✅ 每个 Item 代表一天的账单组，由一个大卡片包裹
 * ✅ 内部项支持向左滑动显示：退款、编辑、删除
 */
public class BillAdapter extends RecyclerView.Adapter<BillAdapter.DayGroupVH> {

    private final Context context;
    private OnBillClickListener listener;

    private static final RecyclerView.RecycledViewPool sharedPhotoPool =
            new RecyclerView.RecycledViewPool();
    static { sharedPhotoPool.setMaxRecycledViews(0, 20); }

    private static final DiffUtil.ItemCallback<BillGroup> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BillGroup>() {
                @Override
                public boolean areItemsTheSame(@NonNull BillGroup oldItem, @NonNull BillGroup newItem) {
                    return oldItem.header.dateKey.equals(newItem.header.dateKey);
                }

                @Override
                public boolean areContentsTheSame(@NonNull BillGroup oldItem, @NonNull BillGroup newItem) {
                    return oldItem.equals(newItem);
                }
            };

    private final AsyncListDiffer<BillGroup> differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);

    public interface OnBillClickListener {
        void onBillClick(long localId, String objectId, View itemView);
        void onPhotoClick(String imageUrl, int position);
        void onBillDelete(BillUiModel bill);
        void onBillEdit(BillUiModel bill);
        void onBillRefund(BillUiModel bill);
    }

    public BillAdapter(Context context, OnBillClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<BillGroup> items) {
        differ.submitList(items);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public DayGroupVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DayGroupVH(ItemBillDateHeaderBinding.inflate(
                LayoutInflater.from(context), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DayGroupVH holder, int position) {
        holder.bind(differ.getCurrentList().get(position));
    }

    class DayGroupVH extends RecyclerView.ViewHolder {
        private final ItemBillDateHeaderBinding b;

        DayGroupVH(ItemBillDateHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(BillGroup group) {
            b.tvDate.setText(group.header.dateText);
            b.tvDaySummary.setText(group.header.expenseText + " | " + group.header.incomeText);

            // 使用内嵌 RecyclerView 替代 manualaddView，支持更好的滑动效果和性能
            if (b.rvBills.getAdapter() == null) {
                b.rvBills.setLayoutManager(new LinearLayoutManager(context));
                b.rvBills.setAdapter(new BillRowAdapter(context, listener, sharedPhotoPool));
            }
            
            BillRowAdapter rowAdapter = (BillRowAdapter) b.rvBills.getAdapter();
            if (rowAdapter != null) {
                rowAdapter.setBills(group.bills);
            }
        }
    }

    public static class DateHeader {
        public final String dateKey;
        public final String dateText;
        public final String expenseText;
        public final String incomeText;

        public DateHeader(String dateKey, String dateText, String expenseText, String incomeText) {
            this.dateKey = dateKey;
            this.dateText = dateText;
            this.expenseText = expenseText;
            this.incomeText = incomeText;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DateHeader that = (DateHeader) o;
            return java.util.Objects.equals(dateKey, that.dateKey) &&
                    java.util.Objects.equals(dateText, that.dateText) &&
                    java.util.Objects.equals(expenseText, that.expenseText) &&
                    java.util.Objects.equals(incomeText, that.incomeText);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(dateKey, dateText, expenseText, incomeText);
        }
    }

    public static class BillGroup {
        public final DateHeader header;
        public final List<BillUiModel> bills;

        public BillGroup(DateHeader header, List<BillUiModel> bills) {
            this.header = header;
            this.bills = bills;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BillGroup billGroup = (BillGroup) o;
            return java.util.Objects.equals(header, billGroup.header) &&
                    java.util.Objects.equals(bills, billGroup.bills);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(header, bills);
        }
    }
}
