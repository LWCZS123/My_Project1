package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.paging.AsyncPagingDataDiffer;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * AccountBillPagingAdapter - 高性能卡片拼接版 (全功能版)
 * -------------------------------------------------------
 * 🚀 优化内容：
 * 1. 胶囊风格侧滑菜单。
 * 2. 动态余额/欠款标签。
 * 3. 完美对齐的加深分割线。
 */
public class AccountBillPagingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_DAY_HEADER = 1;
    private static final int TYPE_BILL_MIDDLE = 2;
    private static final int TYPE_BILL_BOTTOM = 3;

    private static final DiffUtil.ItemCallback<AccountBillUiModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AccountBillUiModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull AccountBillUiModel oldItem,
                                               @NonNull AccountBillUiModel newItem) {
                    if (oldItem.type != newItem.type) return false;
                    if (oldItem.type == AccountBillUiModel.TYPE_BILL_ITEM) {
                        if (oldItem.id > 0 && newItem.id > 0) return oldItem.id == newItem.id;
                        return oldItem.objectId != null && !oldItem.objectId.isEmpty()
                                && oldItem.objectId.equals(newItem.objectId);
                    }
                    if (oldItem.type == AccountBillUiModel.TYPE_DAY_HEADER) {
                        return Objects.equals(oldItem.key, newItem.key)
                                && Objects.equals(oldItem.title, newItem.title);
                    }
                    return Objects.equals(oldItem.key, newItem.key);
                }

                @Override
                public boolean areContentsTheSame(@NonNull AccountBillUiModel oldItem,
                                                  @NonNull AccountBillUiModel newItem) {
                    return oldItem.equals(newItem);
                }
            };

    private static class Row {
        final int rawPosition;
        final AccountBillUiModel item;

        Row(int rawPosition, AccountBillUiModel item) {
            this.rawPosition = rawPosition;
            this.item = item;
        }
    }

    private final Context context;
    private final AsyncPagingDataDiffer<AccountBillUiModel> differ;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean rebuildPending;
    private final Runnable rebuildRunnable = () -> {
        rebuildPending = false;
        rebuildVisibleItems();
    };
    private final List<Row> visibleRows = new ArrayList<>();
    private final Set<String> collapsedMonths = new HashSet<>();
    private OnBillClickListener listener;
    private OnMonthToggleListener monthToggleListener;

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
        void onBillDelete(Bill bill);
        void onBillRefund(Bill bill);
        void onBillEdit(Bill bill);
    }

    public interface OnMonthToggleListener {
        void onMonthToggle(String monthKey, boolean collapsed);
    }

    public AccountBillPagingAdapter(Context context) {
        this.context = context;
        differ = new AsyncPagingDataDiffer<>(DIFF_CALLBACK, new ListUpdateCallback() {
            @Override public void onInserted(int position, int count) { scheduleRebuild(); }
            @Override public void onRemoved(int position, int count) { scheduleRebuild(); }
            @Override public void onMoved(int fromPosition, int toPosition) { scheduleRebuild(); }
            @Override public void onChanged(int position, int count, Object payload) { scheduleRebuild(); }
        });
    }

    public void setOnBillClickListener(OnBillClickListener listener) {
        this.listener = listener;
    }

    public void setOnMonthToggleListener(OnMonthToggleListener listener) {
        this.monthToggleListener = listener;
    }

    public void setCollapsedMonths(Set<String> monthKeys) {
        collapsedMonths.clear();
        collapsedMonths.addAll(monthKeys);
        rebuildVisibleItems();
    }

    public void submitData(Lifecycle lifecycle, PagingData<AccountBillUiModel> pagingData) {
        differ.submitData(lifecycle, pagingData);
    }

    private void scheduleRebuild() {
        rebuildPending = true;
        mainHandler.removeCallbacks(rebuildRunnable);
        mainHandler.post(rebuildRunnable);
    }

    private void rebuildVisibleItems() {
        List<Row> oldRows = new ArrayList<>(visibleRows);
        List<Row> newRows = new ArrayList<>();
        for (int i = 0; i < differ.getItemCount(); i++) {
            AccountBillUiModel item = differ.peek(i);
            if (item != null && (item.type == AccountBillUiModel.TYPE_MONTH_HEADER
                    || !collapsedMonths.contains(item.key))) {
                newRows.add(new Row(i, item));
            }
        }
        boolean appended = newRows.size() >= oldRows.size();
        for (int i = 0; appended && i < oldRows.size(); i++) {
            Row oldRow = oldRows.get(i);
            Row newRow = newRows.get(i);
            appended = oldRow.rawPosition == newRow.rawPosition
                    && DIFF_CALLBACK.areItemsTheSame(oldRow.item, newRow.item)
                    && DIFF_CALLBACK.areContentsTheSame(oldRow.item, newRow.item);
        }
        if (appended) {
            int oldCount = oldRows.size();
            boolean lastTypeChanged = oldCount > 0
                    && viewTypeAt(oldRows, oldCount - 1) != viewTypeAt(newRows, oldCount - 1);
            visibleRows.clear();
            visibleRows.addAll(newRows);
            if (lastTypeChanged) notifyItemChanged(oldCount - 1);
            if (newRows.size() > oldCount) notifyItemRangeInserted(oldCount, newRows.size() - oldCount);
            requestMoreIfHiddenTail();
            return;
        }
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldRows.size(); }
            @Override public int getNewListSize() { return newRows.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return DIFF_CALLBACK.areItemsTheSame(oldRows.get(oldPosition).item,
                        newRows.get(newPosition).item);
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return DIFF_CALLBACK.areContentsTheSame(oldRows.get(oldPosition).item,
                        newRows.get(newPosition).item)
                        && viewTypeAt(oldRows, oldPosition) == viewTypeAt(newRows, newPosition);
            }
        });
        visibleRows.clear();
        visibleRows.addAll(newRows);
        changes.dispatchUpdatesTo(this);
        requestMoreIfHiddenTail();
    }

    private void requestMoreIfHiddenTail() {
        int rawCount = differ.getItemCount();
        if (!visibleRows.isEmpty() && visibleRows.get(visibleRows.size() - 1).rawPosition < rawCount - 1) {
            differ.getItem(rawCount - 1);
        }
    }

    private void toggleMonth(String monthKey, MonthViewHolder holder) {
        boolean collapsed = !collapsedMonths.contains(monthKey);
        if (collapsed) collapsedMonths.add(monthKey);
        else collapsedMonths.remove(monthKey);
        holder.showCollapsed(collapsed);

        for (int header = 0; header < visibleRows.size(); header++) {
            Row row = visibleRows.get(header);
            if (row.item.type != AccountBillUiModel.TYPE_MONTH_HEADER
                    || !Objects.equals(row.item.key, monthKey)) continue;

            if (collapsed) {
                int end = header + 1;
                while (end < visibleRows.size()
                        && visibleRows.get(end).item.type != AccountBillUiModel.TYPE_MONTH_HEADER) end++;
                int count = end - header - 1;
                if (count > 0) {
                    visibleRows.subList(header + 1, end).clear();
                    notifyItemRangeRemoved(header + 1, count);
                }
            } else {
                List<Row> added = new ArrayList<>();
                int rawHeader = row.rawPosition;
                if (rebuildPending) {
                    rawHeader = -1;
                    for (int raw = 0; raw < differ.getItemCount(); raw++) {
                        AccountBillUiModel item = differ.peek(raw);
                        if (item != null && item.type == AccountBillUiModel.TYPE_MONTH_HEADER
                                && Objects.equals(item.key, monthKey)) {
                            rawHeader = raw;
                            break;
                        }
                    }
                }
                for (int raw = rawHeader + 1; rawHeader >= 0 && raw < differ.getItemCount(); raw++) {
                    AccountBillUiModel item = differ.peek(raw);
                    if (item == null || item.type == AccountBillUiModel.TYPE_MONTH_HEADER) break;
                    added.add(new Row(raw, item));
                }
                if (!added.isEmpty()) {
                    visibleRows.addAll(header + 1, added);
                    notifyItemRangeInserted(header + 1, added.size());
                }
            }
            break;
        }
        if (collapsed) requestMoreIfHiddenTail();
        if (monthToggleListener != null) monthToggleListener.onMonthToggle(monthKey, collapsed);
    }

    @Override
    public int getItemViewType(int position) {
        return viewTypeAt(visibleRows, position);
    }

    private static int viewTypeAt(List<Row> rows, int position) {
        AccountBillUiModel item = rows.get(position).item;
        
        if (item.type == AccountBillUiModel.TYPE_MONTH_HEADER) return TYPE_MONTH_HEADER;
        if (item.type == AccountBillUiModel.TYPE_DAY_HEADER) return TYPE_DAY_HEADER;
        
        boolean isLast = true;
        if (position + 1 < rows.size()) {
            AccountBillUiModel next = rows.get(position + 1).item;
            if (next.type != AccountBillUiModel.TYPE_MONTH_HEADER) isLast = false;
        }
        return isLast ? TYPE_BILL_BOTTOM : TYPE_BILL_MIDDLE;
    }

    @Override
    public int getItemCount() {
        return visibleRows.size();
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
        Row row = visibleRows.get(position);
        if (row.rawPosition < differ.getItemCount()) differ.getItem(row.rawPosition);
        AccountBillUiModel item = row.item;

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

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        scheduleRebuild();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        mainHandler.removeCallbacks(rebuildRunnable);
        rebuildPending = false;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    // --- ViewHolders ---

    class MonthViewHolder extends RecyclerView.ViewHolder {
        private final ItemMonthHeaderBinding b;
        private AccountBillUiModel currentItem;

        MonthViewHolder(ItemMonthHeaderBinding binding) {
            super(binding.getRoot());
            this.b = binding;
            b.getRoot().setOnClickListener(v -> {
                if (currentItem != null) toggleMonth(currentItem.key, this);
            });
        }

        void bind(AccountBillUiModel m) {
            this.currentItem = m;
            b.tvMonthTitle.setText(m.title);
            b.tvMonthRange.setText(m.subtitle);
            b.tvBillAmountLabel.setText(m.billAmountText);
            b.tvInflow.setText(m.inflowText);
            b.tvOutflow.setText(m.outflowText);
            
            showCollapsed(collapsedMonths.contains(m.key));
        }

        private void showCollapsed(boolean collapsed) {
            b.ivArrow.animate().cancel();
            b.ivArrow.setRotation(collapsed ? 0f : 180f);
            if (collapsed) {
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
            bindData(m, b.ivCategoryIcon, b.tvCategoryName, b.tvTransactionTime, b.tvAmount, b.tvOriginalAmount, b.tvDiscount, b.tvBalanceAfter, b.contentView, b.btnDelete, b.btnRefund, b.btnEdit, b.swipeLayout, b.flTags); 
        }
    }

    class BillBottomViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountBillBottomBinding b;
        BillBottomViewHolder(ItemAccountBillBottomBinding binding) { super(binding.getRoot()); this.b = binding; }
        void bind(AccountBillUiModel m) { 
            bindData(m, b.ivCategoryIcon, b.tvCategoryName, b.tvTransactionTime, b.tvAmount, b.tvOriginalAmount, b.tvDiscount, b.tvBalanceAfter, b.contentView, b.btnDelete, b.btnRefund, b.btnEdit, b.swipeLayout, b.flTags); 
        }
    }

    private void bindData(AccountBillUiModel m, ImageView ivIcon, TextView tvName, TextView tvTime, 
                          TextView tvAmount, TextView tvOriginalAmount, TextView tvDiscount, TextView tvBalance, View content, View btnDel, 
                          View btnRef, View btnEdit, SwipeMenuLayout swipe, ViewGroup flTags) {
        
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

        // 🏷️ 账户详情页展示全部标签
        com.example.my_project1.utils.TagViewUtils.renderTags(flTags, m.tags, Integer.MAX_VALUE);

        if (m.hasDiscount) {
            tvOriginalAmount.setVisibility(View.VISIBLE);
            tvOriginalAmount.setText(m.originalAmountText);
            tvOriginalAmount.setPaintFlags(tvOriginalAmount.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);

            tvDiscount.setVisibility(View.VISIBLE);
            tvDiscount.setText(m.discountText);
        } else {
            tvOriginalAmount.setVisibility(View.GONE);
            tvOriginalAmount.setPaintFlags(tvOriginalAmount.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            tvDiscount.setVisibility(View.GONE);
        }

        tvBalance.setText(m.balanceText);

        content.setOnClickListener(v -> { if (listener != null) listener.onBillClick(m.originalBill); });
        btnDel.setOnClickListener(v -> { if (listener != null) { listener.onBillDelete(m.originalBill); swipe.quickClose(); } });
        btnRef.setOnClickListener(v -> { if (listener != null) { listener.onBillRefund(m.originalBill); swipe.quickClose(); } });
        btnEdit.setOnClickListener(v -> { if (listener != null) { listener.onBillEdit(m.originalBill); swipe.quickClose(); } });
    }
}
