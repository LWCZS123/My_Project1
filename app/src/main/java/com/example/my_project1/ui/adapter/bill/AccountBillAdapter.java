package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ItemAccountTransactionBinding;
import com.example.my_project1.databinding.ItemDayHeaderBinding;
import com.example.my_project1.databinding.ItemMonthHeaderBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AccountBillAdapter - 使用 View Binding 的账户账单适配器
 */
public class AccountBillAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MONTH_HEADER = 0;
    private static final int TYPE_DAY_HEADER = 1;
    private static final int TYPE_BILL_ITEM = 2;

    private final Context context;
    private final List<Object> displayItems = new ArrayList<>();
    
    // 数据缓存
    private final Map<String, MonthGroup> monthGroupsMap = new LinkedHashMap<>();
    private final Set<String> collapsedMonths = new HashSet<>();
    private final Map<Long, Double> billBalanceMap = new HashMap<>();
    
    private boolean isCredit = false;
    private OnBillClickListener listener;

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
        void onBillDelete(Bill bill);
        void onBillRefund(Bill bill);
        void onBillEdit(Bill bill);
    }

    public AccountBillAdapter(Context context) {
        this.context = context;
    }

    public void setOnBillClickListener(OnBillClickListener listener) {
        this.listener = listener;
    }

    public Object getItem(int position) {
        if (position >= 0 && position < displayItems.size()) {
            return displayItems.get(position);
        }
        return null;
    }

    /**
     * 设置数据并根据月份、日期进行分组，同时计算每笔交易后的余额
     */
    public void setData(List<Bill> bills, double accountBalance, boolean isCredit) {
        this.isCredit = isCredit;
        
        monthGroupsMap.clear();
        billBalanceMap.clear();
        
        if (bills == null || bills.isEmpty()) {
            displayItems.clear();
            notifyDataSetChanged();
            return;
        }

        // 1. 计算每笔交易发生后的余额 (从当前余额倒推)
        double runningBalance = accountBalance;
        for (Bill bill : bills) {
            billBalanceMap.put(bill.getId(), runningBalance);
            double impact = (bill.getType() == 1) ? bill.getAmount() : -bill.getAmount();
            runningBalance -= impact;
        }

        // 2. 按月和日进行分组
        SimpleDateFormat monthKeyFormat = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Bill bill : bills) {
            String monthKey = monthKeyFormat.format(bill.getBillTime());
            String dayKey = dayKeyFormat.format(bill.getBillTime());

            MonthGroup mGroup = monthGroupsMap.get(monthKey);
            if (mGroup == null) {
                mGroup = new MonthGroup(monthKey, bill.getBillTime());
                monthGroupsMap.put(monthKey, mGroup);
            }
            
            mGroup.addBill(bill, dayKey);
        }

        // 3. 构建显示列表
        updateDisplayItems();
    }

    /**
     * 根据折叠状态更新显示项列表
     */
    private void updateDisplayItems() {
        displayItems.clear();
        for (MonthGroup mGroup : monthGroupsMap.values()) {
            displayItems.add(mGroup);
            if (!collapsedMonths.contains(mGroup.key)) {
                for (DayGroup dGroup : mGroup.dayGroups.values()) {
                    displayItems.add(dGroup);
                    displayItems.addAll(dGroup.bills);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayItems.get(position);
        if (item instanceof MonthGroup) return TYPE_MONTH_HEADER;
        if (item instanceof DayGroup) return TYPE_DAY_HEADER;
        return TYPE_BILL_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_MONTH_HEADER) {
            ItemMonthHeaderBinding binding = ItemMonthHeaderBinding.inflate(inflater, parent, false);
            return new MonthViewHolder(binding);
        } else if (viewType == TYPE_DAY_HEADER) {
            ItemDayHeaderBinding binding = ItemDayHeaderBinding.inflate(inflater, parent, false);
            return new DayViewHolder(binding);
        } else {
            ItemAccountTransactionBinding binding = ItemAccountTransactionBinding.inflate(inflater, parent, false);
            return new BillViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayItems.get(position);
        if (holder instanceof MonthViewHolder) {
            ((MonthViewHolder) holder).bind((MonthGroup) item);
        } else if (holder instanceof DayViewHolder) {
            ((DayViewHolder) holder).bind((DayGroup) item);
        } else if (holder instanceof BillViewHolder) {
            ((BillViewHolder) holder).bind((Bill) item);
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    // ==================== ViewHolders ====================

    class MonthViewHolder extends RecyclerView.ViewHolder {
        private final ItemMonthHeaderBinding binding;

        MonthViewHolder(ItemMonthHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    MonthGroup group = (MonthGroup) displayItems.get(pos);
                    if (collapsedMonths.contains(group.key)) {
                        collapsedMonths.remove(group.key);
                    } else {
                        collapsedMonths.add(group.key);
                    }
                    updateDisplayItems();
                }
            });
        }

        void bind(MonthGroup group) {
            SimpleDateFormat titleFmt = new SimpleDateFormat("yyyy年MM月", Locale.getDefault());
            binding.tvMonthTitle.setText(titleFmt.format(group.date));
            
            // 计算月份范围
            Calendar cal = Calendar.getInstance();
            cal.setTime(group.date);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            Date start = cal.getTime();
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            Date end = cal.getTime();
            
            SimpleDateFormat rangeFmt = new SimpleDateFormat("MM月dd日", Locale.getDefault());
            binding.tvMonthRange.setText(rangeFmt.format(start) + " - " + rangeFmt.format(end));

            DecimalFormat df = new DecimalFormat("#,##0.00");
            binding.tvInflow.setText("流入: ¥" + df.format(group.totalInflow));
            binding.tvOutflow.setText("流出: ¥" + df.format(group.totalOutflow));
            
            // 折叠箭头动画/状态
            binding.ivArrow.setRotation(collapsedMonths.contains(group.key) ? 0 : 180);
        }
    }

    class DayViewHolder extends RecyclerView.ViewHolder {
        private final ItemDayHeaderBinding binding;

        DayViewHolder(ItemDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DayGroup group) {
            SimpleDateFormat dayFmt = new SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault());
            binding.tvDayTitle.setText(dayFmt.format(group.date));
            
            DecimalFormat df = new DecimalFormat("#,##0.00");
            binding.tvDaySummary.setText("流出: ¥" + df.format(group.totalOutflow) + " 流入: ¥" + df.format(group.totalInflow));
        }
    }

    public class BillViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountTransactionBinding binding;

        BillViewHolder(ItemAccountTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            
            binding.contentView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBillClick((Bill) displayItems.get(pos));
                }
            });

            binding.btnDelete.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBillDelete((Bill) displayItems.get(pos));
                    binding.swipeLayout.quickClose();
                }
            });

            binding.btnRefund.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBillRefund((Bill) displayItems.get(pos));
                    binding.swipeLayout.quickClose();
                }
            });

            binding.btnEdit.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBillEdit((Bill) displayItems.get(pos));
                    binding.swipeLayout.quickClose();
                }
            });
        }

        void bind(Bill bill) {
            // 设置分类图标
            if (bill.getCategoryIconUrl() != null && !bill.getCategoryIconUrl().isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, bill.getCategoryIconUrl(), binding.ivCategoryIcon);
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_category);
            }
            
            // 分类名
            binding.tvCategoryName.setText(bill.getCategoryName());
            
            // 时间和备注
            SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            String timeNote = timeFmt.format(bill.getBillTime());
            if (bill.getRemark() != null && !bill.getRemark().isEmpty()) {
                timeNote += " · " + bill.getRemark();
            }
            binding.tvTransactionTime.setText(timeNote);

            // 金额
            DecimalFormat df = new DecimalFormat("#,##0.00");
            String prefix = (bill.getType() == 1) ? "+" : "-";
            binding.tvAmount.setText(prefix + "¥" + df.format(bill.getAmount()));
            binding.tvAmount.setTextColor((bill.getType() == 1) ? Color.parseColor("#00C48C") : Color.parseColor("#FF6B6B"));

            // 交易后余额/欠款
            Double balanceAfter = billBalanceMap.get(bill.getId());
            if (balanceAfter == null) balanceAfter = 0.0;
            
            String balanceLabel = isCredit ? "欠款: " : "余额: ";
            double displayBalance = isCredit ? Math.abs(balanceAfter) : balanceAfter;
            binding.tvBalanceAfter.setText(balanceLabel + "¥" + df.format(displayBalance));

            // 侧滑菜单点击
            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillDelete(bill);
                    binding.swipeLayout.quickClose();
                }
            });
            binding.btnRefund.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillRefund(bill);
                    binding.swipeLayout.quickClose();
                }
            });
            binding.btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillEdit(bill);
                    binding.swipeLayout.quickClose();
                }
            });
        }
    }

    // ==================== 数据结构 ====================

    private static class MonthGroup {
        String key;
        Date date;
        double totalInflow = 0;
        double totalOutflow = 0;
        Map<String, DayGroup> dayGroups = new LinkedHashMap<>();

        MonthGroup(String key, Date date) {
            this.key = key;
            this.date = date;
        }

        void addBill(Bill bill, String dayKey) {
            if (bill.getType() == 1) totalInflow += bill.getAmount();
            else totalOutflow += bill.getAmount();

            DayGroup dGroup = dayGroups.get(dayKey);
            if (dGroup == null) {
                dGroup = new DayGroup(dayKey, bill.getBillTime());
                dayGroups.put(dayKey, dGroup);
            }
            dGroup.addBill(bill);
        }
    }

    private static class DayGroup {
        String key;
        Date date;
        double totalInflow = 0;
        double totalOutflow = 0;
        List<Bill> bills = new ArrayList<>();

        DayGroup(String key, Date date) {
            this.key = key;
            this.date = date;
        }

        void addBill(Bill bill) {
            if (bill.getType() == 1) totalInflow += bill.getAmount();
            else totalOutflow += bill.getAmount();
            bills.add(bill);
        }
    }
}
