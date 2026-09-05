package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ItemAccountBillBottomBinding;
import com.example.my_project1.databinding.ItemAccountBillMiddleBinding;
import com.example.my_project1.databinding.ItemDayHeaderBinding;
import com.example.my_project1.databinding.ItemMonthHeaderBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountBillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.ui.SwipeMenuLayout;

import java.util.Objects;

/**
 * AccountBillPagingAdapter - 高性能卡片拼接版 (全功能版)
 * -------------------------------------------------------
 * 🚀 优化内容：
 * 1. 胶囊风格侧滑菜单。
 * 2. 动态余额/欠款标签。
 * 3. 完美对齐的加深分割线。
 */
public class AccountBillPagingAdapter extends PagingDataAdapter<AccountBillUiModel, RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_DAY_HEADER = 1;
    private static final int TYPE_BILL_MIDDLE = 2;
    private static final int TYPE_BILL_BOTTOM = 3;

    private final Context context;
    private OnBillClickListener listener;
    private OnMonthToggleListener monthToggleListener;

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
        void onBillDelete(Bill bill);
        void onBillRefund(Bill bill);
        void onBillEdit(Bill bill);
    }

    public interface OnMonthToggleListener {
        void onMonthToggle(String monthKey);
    }

    public AccountBillPagingAdapter(Context context) {
        super(new DiffUtil.ItemCallback<AccountBillUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountBillUiModel oldItem, @NonNull AccountBillUiModel newItem) {
                if (oldItem.type != newItem.type) return false;
                if (oldItem.type == AccountBillUiModel.TYPE_BILL_ITEM) {
                    return oldItem.id == newItem.id || Objects.equals(oldItem.objectId, newItem.objectId);
                }
                return Objects.equals(oldItem.key, newItem.key);
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountBillUiModel oldItem, @NonNull AccountBillUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.context = context;
    }

    public void setOnBillClickListener(OnBillClickListener listener) {
        this.listener = listener;
    }

    public void setOnMonthToggleListener(OnMonthToggleListener listener) {
        this.monthToggleListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        AccountBillUiModel item = getItem(position);
        if (item == null) return TYPE_BILL_MIDDLE;
        
        if (item.type == AccountBillUiModel.TYPE_MONTH_HEADER) return TYPE_MONTH_HEADER;
        if (item.type == AccountBillUiModel.TYPE_DAY_HEADER) return TYPE_DAY_HEADER;
        
        boolean isLast = true;
        if (position + 1 < getItemCount()) {
            AccountBillUiModel next = getItem(position + 1);
            if (next != null && next.type != AccountBillUiModel.TYPE_MONTH_HEADER) {
                isLast = false;
            }
        }
        return isLast ? TYPE_BILL_BOTTOM : TYPE_BILL_MIDDLE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_MONTH_HEADER:
                return new MonthViewHolder(ItemMonthHeaderBinding.inflate(inflater, parent, false));
            case TYPE_DAY_HEADER:
                return new DayViewHolder(ItemDayHeaderBinding.inflate(inflater, parent, false));
            case TYPE_BILL_BOTTOM:
                return new BillBottomViewHolder(ItemAccountBillBottomBinding.inflate(inflater, parent, false));
            default:
                return new BillMiddleViewHolder(ItemAccountBillMiddleBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AccountBillUiModel item = getItem(position);
        if (item == null) return;

        if (holder instanceof MonthViewHolder) {
            ((MonthViewHolder) holder).bind(item);
        } else if (holder instanceof DayViewHolder) {
            ((DayViewHolder) holder).bind(item);
        } else if (holder instanceof BillMiddleViewHolder) {
            ((BillMiddleViewHolder) holder).bind(item);
        } else if (holder instanceof BillBottomViewHolder) {
            ((BillBottomViewHolder) holder).bind(item);
        }
    }

    // --- ViewHolders ---

    class MonthViewHolder extends RecyclerView.ViewHolder {
        private final ItemMonthHeaderBinding b;
        private AccountBillUiModel currentItem;

        MonthViewHolder(ItemMonthHeaderBinding binding) {
            super(binding.getRoot());
            this.b = binding;
            b.getRoot().setOnClickListener(v -> {
                if (currentItem != null && monthToggleListener != null) {
                    monthToggleListener.onMonthToggle(currentItem.key);
                }
            });
        }

        void bind(AccountBillUiModel m) {
            this.currentItem = m;
            b.tvMonthTitle.setText(m.title);
            b.tvMonthRange.setText(m.subtitle);
            b.tvBillAmountLabel.setText(m.billAmountText);
            b.tvInflow.setText(m.inflowText);
            b.tvOutflow.setText(m.outflowText);
            b.ivArrow.setRotation(m.isCollapsed ? 0 : 180);

            if (m.isCollapsed) {
                b.getRoot().setBackgroundResource(R.drawable.bg_item_single);
                b.divider.setVisibility(View.INVISIBLE);
            } else {
                b.getRoot().setBackgroundResource(R.drawable.bg_item_top);
                b.divider.setVisibility(View.VISIBLE);
            }
        }
    }

    class DayViewHolder extends RecyclerView.ViewHolder {
        private final ItemDayHeaderBinding b;
        DayViewHolder(ItemDayHeaderBinding binding) { super(binding.getRoot()); this.b = binding; }
        void bind(AccountBillUiModel m) {
            b.tvDayTitle.setText(m.title);
            b.tvDaySummary.setText(m.subtitle);
        }
    }

    class BillMiddleViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountBillMiddleBinding b;
        BillMiddleViewHolder(ItemAccountBillMiddleBinding binding) { super(binding.getRoot()); this.b = binding; }
        void bind(AccountBillUiModel m) { 
            bindData(m, b.ivCategoryIcon, b.tvCategoryName, b.tvTransactionTime, b.tvAmount, b.tvBalanceAfter, b.contentView, b.btnDelete, b.btnRefund, b.btnEdit, b.swipeLayout); 
        }
    }

    class BillBottomViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountBillBottomBinding b;
        BillBottomViewHolder(ItemAccountBillBottomBinding binding) { super(binding.getRoot()); this.b = binding; }
        void bind(AccountBillUiModel m) { 
            bindData(m, b.ivCategoryIcon, b.tvCategoryName, b.tvTransactionTime, b.tvAmount, b.tvBalanceAfter, b.contentView, b.btnDelete, b.btnRefund, b.btnEdit, b.swipeLayout); 
        }
    }

    private void bindData(AccountBillUiModel m, ImageView ivIcon, TextView tvName, TextView tvTime, 
                          TextView tvAmount, TextView tvBalance, View content, View btnDel, 
                          View btnRef, View btnEdit, SwipeMenuLayout swipe) {
        
        ImageLoaderUtils.loadThumbnail(context, m.categoryIconUrl, ivIcon);
        
        if (m.originalBill != null && m.originalBill.getCategoryIconBackgroundColor() != null) {
            try {
                int color = Color.parseColor(m.originalBill.getCategoryIconBackgroundColor());
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(color);
                ivIcon.setBackground(gd);
            } catch (Exception e) { ivIcon.setBackgroundResource(R.drawable.bg_circle_grey); }
        } else { ivIcon.setBackgroundResource(R.drawable.bg_circle_grey); }

        tvName.setText(m.categoryName);
        tvTime.setText(m.timeNote);
        tvAmount.setText(m.amountText);
        tvAmount.setTextColor(m.amountColor);
        tvBalance.setText(m.balanceText);

        content.setOnClickListener(v -> { if (listener != null) listener.onBillClick(m.originalBill); });
        btnDel.setOnClickListener(v -> { if (listener != null) { listener.onBillDelete(m.originalBill); swipe.quickClose(); } });
        btnRef.setOnClickListener(v -> { if (listener != null) { listener.onBillRefund(m.originalBill); swipe.quickClose(); } });
        btnEdit.setOnClickListener(v -> { if (listener != null) { listener.onBillEdit(m.originalBill); swipe.quickClose(); } });
    }
}
