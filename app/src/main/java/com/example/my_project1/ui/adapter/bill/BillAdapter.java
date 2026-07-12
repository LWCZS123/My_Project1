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
 * BillAdapter (聚合卡片版)
 * -------------------------------------------------------
 * ✅ 每个 Item 代表一天的账单组，由一个大卡片包裹
 * ✅ 内部动态添加账单项，实现完美的阴影和分割线效果
 * ✅ 修复：点击事件无效、账单图片不显示的问题
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
                    if (!oldItem.header.dateText.equals(newItem.header.dateText)
                            || !oldItem.header.expenseText.equals(newItem.header.expenseText)
                            || !oldItem.header.incomeText.equals(newItem.header.incomeText)) {
                        return false;
                    }
                    if (oldItem.bills.size() != newItem.bills.size()) return false;
                    for (int i = 0; i < oldItem.bills.size(); i++) {
                        if (!oldItem.bills.get(i).diffKey.equals(newItem.bills.get(i).diffKey)) return false;
                        if (!oldItem.bills.get(i).amountText.equals(newItem.bills.get(i).amountText)) return false;
                    }
                    return true;
                }
            };

    private final AsyncListDiffer<BillGroup> differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);

    // ──────────── 数据结构 ──────────────
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
    }

    public static class BillGroup {
        public final DateHeader header;
        public final List<BillUiModel> bills;

        public BillGroup(DateHeader header, List<BillUiModel> bills) {
            this.header = header;
            this.bills = bills;
        }
    }

    public interface OnBillClickListener {
        void onBillClick(long localId, String objectId, View itemView);
        void onPhotoClick(String imageUrl, int position);
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
            // 1. 设置头部数据
            b.tvDate.setText(group.header.dateText);
            b.tvDaySummary.setText(group.header.expenseText + " | " + group.header.incomeText);

            // 2. 清空并填充账单项
            b.llBillContainer.removeAllViews();
            for (int i = 0; i < group.bills.size(); i++) {
                BillUiModel bill = group.bills.get(i);
                ItemBillBinding ib = ItemBillBinding.inflate(
                        LayoutInflater.from(context), b.llBillContainer, false);

                // 数据绑定
                ib.tvCategory.setText(bill.categoryName);
                ib.tvAmount.setText(bill.amountText);
                ib.tvAmount.setTextColor(bill.amountColor);
                
                String subInfo = bill.timeText;
                if (bill.remarkText != null && !bill.remarkText.isEmpty()) {
                    subInfo += " | " + bill.remarkText;
                }
                ib.tvSubInfo.setText(subInfo);
                ib.tvAccount.setText(bill.accountName);

                ImageLoaderUtils.loadThumbnail(context, bill.categoryIconUrl, ib.ivCategoryIcon);

                // 图片列表处理
                if (bill.imageUrls != null && !bill.imageUrls.isEmpty()) {
                    ib.ivBillImage.setVisibility(View.VISIBLE);
                    ib.ivBillImage.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                    ib.ivBillImage.setRecycledViewPool(sharedPhotoPool);
                    
                    PhotoAdapter photoAdapter = new PhotoAdapter(context, new ArrayList<>(), false,
                            new PhotoAdapter.OnPhotoClickListener() {
                                @Override
                                public void onPhotoClick(String url, int pos) {
                                    if (listener != null) listener.onPhotoClick(url, pos);
                                }
                                @Override public void onDeleteClick(int pos) { }
                            });
                    ib.ivBillImage.setAdapter(photoAdapter);
                    photoAdapter.setPhotos(bill.imageUrls);
                } else {
                    ib.ivBillImage.setVisibility(View.GONE);
                }

                // 分割线处理
                ib.billDivider.setVisibility(i == group.bills.size() - 1 ? View.GONE : View.VISIBLE);

                // 点击事件 (设置在 layoutMain 上)
                ib.layoutMain.setOnClickListener(v -> {
                    if (listener != null)
                        listener.onBillClick(bill.localId, bill.objectId, ib.getRoot());
                });

                b.llBillContainer.addView(ib.getRoot());
            }
        }
    }
}