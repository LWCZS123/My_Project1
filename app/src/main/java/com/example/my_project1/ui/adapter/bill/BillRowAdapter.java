package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ItemBillBinding;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import io.reactivex.annotations.NonNull;

public class BillRowAdapter extends ListAdapter<BillUiModel, BillRowAdapter.RowVH> {

    private final Context context;
    private final BillAdapter.OnBillClickListener listener;

    public BillRowAdapter(Context context, BillAdapter.OnBillClickListener listener, RecyclerView.RecycledViewPool photoPool) {
        super(new DiffUtil.ItemCallback<BillUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull BillUiModel oldItem, @NonNull BillUiModel newItem) {
                return oldItem.localId == newItem.localId || 
                       (oldItem.objectId != null && oldItem.objectId.equals(newItem.objectId));
            }

            @Override
            public boolean areContentsTheSame(@NonNull BillUiModel oldItem, @NonNull BillUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RowVH(ItemBillBinding.inflate(LayoutInflater.from(context), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowVH holder, int position) {
        holder.bind(getItem(position), position == getItemCount() - 1);
    }

    class RowVH extends RecyclerView.ViewHolder {
        private final ItemBillBinding b;

        RowVH(ItemBillBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(BillUiModel bill, boolean isLast) {
            b.tvCategory.setText(bill.categoryName);
            b.tvAmount.setText(bill.amountText);
            b.tvAmount.setTextColor(bill.amountColor);
            
            String subInfo = bill.timeText;
            if (bill.remarkText != null && !bill.remarkText.isEmpty()) {
                subInfo += " | " + bill.remarkText;
            }
            b.tvSubInfo.setText(subInfo);
            b.tvAccount.setText(bill.accountName);

            // 🔑 处理转账显示
            if (bill.billType == 2 || bill.billType == 3) {
                b.ivTransferArrow.setVisibility(View.VISIBLE);
                b.tvToAccount.setVisibility(View.VISIBLE);
                b.tvToAccount.setText(bill.toAccountName);
            } else {
                b.ivTransferArrow.setVisibility(View.GONE);
                b.tvToAccount.setVisibility(View.GONE);
            }

            ImageLoaderUtils.loadThumbnail(context, bill.categoryIconUrl, b.ivCategoryIcon);

            // 🎨 设置图标背景色
            if (bill.categoryIconBackgroundColor != null && !bill.categoryIconBackgroundColor.isEmpty()) {
                try {
                    int color = Color.parseColor(bill.categoryIconBackgroundColor);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(color);
                    b.ivCategoryIcon.setBackground(gd);
                } catch (Exception e) {
                    b.ivCategoryIcon.setBackgroundResource(R.drawable.bg_circle_grey);
                }
            } else {
                b.ivCategoryIcon.setBackgroundResource(R.drawable.bg_circle_grey);
            }

            // 🚀 优化图片展示：使用 LinearLayout + 3 ImageViews 替代嵌套 RecyclerView
            if (bill.imageUrls != null && !bill.imageUrls.isEmpty()) {
                b.llBillImages.setVisibility(View.VISIBLE);
                int size = bill.imageUrls.size();

                // 图片 1
                b.ivBillImage1.setVisibility(View.VISIBLE);
                ImageLoaderUtils.loadThumbnail(context, bill.imageUrls.get(0), b.ivBillImage1);
                b.ivBillImage1.setOnClickListener(v -> {
                    if (listener != null) listener.onPhotoClick(bill.imageUrls.get(0), 0);
                });

                // 图片 2
                if (size >= 2) {
                    b.ivBillImage2.setVisibility(View.VISIBLE);
                    ImageLoaderUtils.loadThumbnail(context, bill.imageUrls.get(1), b.ivBillImage2);
                    b.ivBillImage2.setOnClickListener(v -> {
                        if (listener != null) listener.onPhotoClick(bill.imageUrls.get(1), 1);
                    });
                } else {
                    b.ivBillImage2.setVisibility(View.GONE);
                }

                // 图片 3
                if (size >= 3) {
                    b.ivBillImage3.setVisibility(View.VISIBLE);
                    ImageLoaderUtils.loadThumbnail(context, bill.imageUrls.get(2), b.ivBillImage3);
                    b.ivBillImage3.setOnClickListener(v -> {
                        if (listener != null) listener.onPhotoClick(bill.imageUrls.get(2), 2);
                    });
                } else {
                    b.ivBillImage3.setVisibility(View.GONE);
                }
            } else {
                b.llBillImages.setVisibility(View.GONE);
            }

            b.billDivider.setVisibility(isLast ? View.GONE : View.VISIBLE);

            b.contentView.setOnClickListener(v -> {
                if (listener != null) listener.onBillClick(bill.localId, bill.objectId, b.getRoot());
            });

            b.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onBillDelete(bill);
                b.swipeLayout.quickClose();
            });
            b.btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onBillEdit(bill);
                b.swipeLayout.quickClose();
            });
            b.btnRefund.setOnClickListener(v -> {
                if (listener != null) listener.onBillRefund(bill);
                b.swipeLayout.quickClose();
            });
        }
    }
}
