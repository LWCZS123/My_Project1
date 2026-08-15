package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ItemAccountDetailHeaderBinding;
import com.example.my_project1.databinding.ItemAccountTransactionBinding;
import com.example.my_project1.databinding.ItemDayHeaderBinding;
import com.example.my_project1.databinding.ItemMonthHeaderBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountBillUiModel;
import com.example.my_project1.ui.viewmodel.accountvm.AccountDetailUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;

import com.github.mikephil.charting.data.PieEntry;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;

/**
 * AccountBillAdapter - 优化后的账户账单适配器，支持月份卡片化展示和图表渲染
 */
public class AccountBillAdapter extends ListAdapter<AccountDetailUiModel, RecyclerView.ViewHolder> {

    private final Context context;
    private OnBillClickListener listener;
    private OnMonthToggleListener monthToggleListener;
    private OnHeaderActionListener headerListener;

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
        void onBillDelete(Bill bill);
        void onBillRefund(Bill bill);
        void onBillEdit(Bill bill);
    }

    public interface OnMonthToggleListener {
        void onMonthToggle(String monthKey);
    }

    public interface OnHeaderActionListener {
        void onEditBalance();
        void onMore();
        void onRepayAction();
        void onRepayNow();
        void onToggleChart();
    }

    public AccountBillAdapter(Context context) {
        super(new DiffUtil.ItemCallback<AccountDetailUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountDetailUiModel oldItem, @NonNull AccountDetailUiModel newItem) {
                if (oldItem.type != newItem.type) return false;
                if (oldItem.type == AccountDetailUiModel.TYPE_HEADER) return true;
                if (oldItem.type == AccountDetailUiModel.TYPE_BILL_ITEM) {
                    return oldItem.billUiModel.id == newItem.billUiModel.id;
                }
                return Objects.equals(oldItem.billUiModel.key, newItem.billUiModel.key);
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountDetailUiModel oldItem, @NonNull AccountDetailUiModel newItem) {
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

    public void setOnHeaderActionListener(OnHeaderActionListener listener) {
        this.headerListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == AccountDetailUiModel.TYPE_HEADER) {
            ItemAccountDetailHeaderBinding binding = ItemAccountDetailHeaderBinding.inflate(inflater, parent, false);
            return new HeaderViewHolder(binding);
        } else if (viewType == AccountDetailUiModel.TYPE_MONTH_HEADER) {
            ItemMonthHeaderBinding binding = ItemMonthHeaderBinding.inflate(inflater, parent, false);
            return new MonthViewHolder(binding);
        } else if (viewType == AccountDetailUiModel.TYPE_DAY_HEADER) {
            ItemDayHeaderBinding binding = ItemDayHeaderBinding.inflate(inflater, parent, false);
            return new DayViewHolder(binding);
        } else {
            ItemAccountTransactionBinding binding = ItemAccountTransactionBinding.inflate(inflater, parent, false);
            return new BillViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AccountDetailUiModel item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof MonthViewHolder) {
            ((MonthViewHolder) holder).bind(item);
        } else if (holder instanceof DayViewHolder) {
            ((DayViewHolder) holder).bind(item);
        } else if (holder instanceof BillViewHolder) {
            ((BillViewHolder) holder).bind(item);
        }
    }

    // ==================== ViewHolders ====================

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountDetailHeaderBinding binding;
        private final DecimalFormat df = new DecimalFormat("#,##0.00");

        HeaderViewHolder(ItemAccountDetailHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            
            binding.btnEditBalance.setOnClickListener(v -> { if (headerListener != null) headerListener.onEditBalance(); });
            binding.btnMore.setOnClickListener(v -> { if (headerListener != null) headerListener.onMore(); });
            binding.btnRepayAction.setOnClickListener(v -> { if (headerListener != null) headerListener.onRepayAction(); });
            binding.btnRepayNow.setOnClickListener(v -> { if (headerListener != null) headerListener.onRepayNow(); });
            binding.ivToggleChart.setOnClickListener(v -> { if (headerListener != null) headerListener.onToggleChart(); });
        }

        void bind(AccountDetailUiModel item) {
            Account acc = item.account;
            if (acc == null) return;

            binding.tvAccountName.setText(acc.getName());
            
            if (acc.getIconUrl() != null && !acc.getIconUrl().isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, acc.getIconUrl(), binding.ivAccountIcon);
            } else {
                binding.ivAccountIcon.setImageResource(R.drawable.ic_wallet);
            }

            if (acc.isCredit()) {
                binding.layoutCreditInfo.setVisibility(View.VISIBLE);
                binding.cardCreditBill.setVisibility(View.VISIBLE);
                binding.btnRepayAction.setVisibility(View.VISIBLE);
                binding.tvBalanceLabel.setText("当前欠款 (CNY)");
                binding.tvBalanceAmount.setText(String.format("¥%s", df.format(Math.abs(acc.getBalance()))));
                
                binding.tvAvailableLimit.setText(String.format("可用额度 ¥%s", df.format(acc.getCreditLimit() + acc.getBalance())));
                binding.tvBillingStatus.setText(String.format("出账日 %s", acc.getBillingDay() > 0 ? acc.getBillingDay() + "日" : "--"));
                binding.tvRepaymentStatus.setText(String.format("还款日 %s", acc.getRepaymentDay() > 0 ? acc.getRepaymentDay() + "日" : "--"));

                // 更新本期还款卡片信息
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                int month = cal.get(Calendar.MONTH) + 1;
                binding.tvBillMonthLabel.setText(String.format(java.util.Locale.getDefault(), "%d年%d月账单（剩余应还）", year, month));
                // 信用账户余额通常为负值表示欠款，这里取绝对值展示
                binding.tvBillAmount.setText(String.format(java.util.Locale.getDefault(), "¥%s", df.format(Math.abs(acc.getBalance()))));
            } else {
                binding.layoutCreditInfo.setVisibility(View.GONE);
                binding.cardCreditBill.setVisibility(View.GONE);
                binding.btnRepayAction.setVisibility(View.GONE);
                binding.tvBalanceLabel.setText("账户余额 (CNY)");
                binding.tvBalanceAmount.setText(String.format("¥%s", df.format(acc.getBalance())));
            }
            
            // Render Chart
            renderChart(item);
        }

        private java.util.List<com.github.mikephil.charting.data.PieEntry> mLastPieEntries;
        private boolean mLastShowingExpense;

        private boolean isChartDataSame(java.util.List<PieEntry> oldList, java.util.List<PieEntry> newList) {
            if (oldList == newList) return true;
            if (oldList == null || newList == null) return false;
            if (oldList.size() != newList.size()) return false;
            for (int i = 0; i < oldList.size(); i++) {
                PieEntry e1 = oldList.get(i);
                PieEntry e2 = newList.get(i);
                if (e1.getValue() != e2.getValue() || !Objects.equals(e1.getLabel(), e2.getLabel())) {
                    return false;
                }
            }
            return true;
        }

        private void renderChart(AccountDetailUiModel item) {
            PieChart pieChart = binding.pieChart;
            
            // 💡 性能优化：深度比较数据，如果没变，不要重新渲染图表，防止跳动
            if (isChartDataSame(mLastPieEntries, item.pieEntries) && mLastShowingExpense == item.showingExpense) {
                return;
            }
            mLastPieEntries = item.pieEntries != null ? new ArrayList<>(item.pieEntries) : null;
            mLastShowingExpense = item.showingExpense;

            if (item.pieEntries == null || item.pieEntries.isEmpty()) {
                pieChart.clear();
                pieChart.setNoDataText(item.showingExpense ? "暂无支出数据" : "暂无收入数据");
                binding.legendRecyclerView.setVisibility(View.GONE);
                return;
            }

            binding.legendRecyclerView.setVisibility(View.VISIBLE);
            PieDataSet dataSet = new PieDataSet(item.pieEntries, "");
            dataSet.setColors(item.chartColors);
            dataSet.setSliceSpace(3f);
            dataSet.setSelectionShift(8f);
            dataSet.setDrawValues(false);

            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            
            // Basic Setup
            pieChart.setUsePercentValues(false);
            pieChart.getDescription().setEnabled(false);
            pieChart.setExtraOffsets(5, 5, 5, 5);
            pieChart.setDragDecelerationFrictionCoef(0.95f);
            pieChart.setDrawHoleEnabled(true);
            pieChart.setHoleColor(Color.TRANSPARENT);
            pieChart.setHoleRadius(58f);
            pieChart.setTransparentCircleRadius(61f);
            pieChart.setDrawCenterText(true);
            pieChart.setCenterText(item.showingExpense ? "总支出" : "总收入");
            pieChart.setCenterTextSize(14f);
            pieChart.setRotationAngle(0);
            pieChart.setRotationEnabled(true);
            pieChart.setHighlightPerTapEnabled(true);
            pieChart.getLegend().setEnabled(false);
            pieChart.setDrawEntryLabels(false);

            pieChart.invalidate();
            
            // Legend
            com.example.my_project1.ui.adapter.ChartLegendAdapter legendAdapter = new com.example.my_project1.ui.adapter.ChartLegendAdapter(context);
            java.util.List<com.example.my_project1.ui.adapter.ChartLegendAdapter.LegendItem> legendItems = new ArrayList<>();
            float total = 0;
            for (com.github.mikephil.charting.data.PieEntry entry : item.pieEntries) total += entry.getValue();

            for (int i = 0; i < item.pieEntries.size(); i++) {
                com.github.mikephil.charting.data.PieEntry entry = item.pieEntries.get(i);
                float percentage = (entry.getValue() / total) * 100;
                legendItems.add(new com.example.my_project1.ui.adapter.ChartLegendAdapter.LegendItem(
                        entry.getLabel(),
                        String.format(java.util.Locale.getDefault(), "%.1f%%", percentage),
                        "¥" + df.format(entry.getValue()),
                        item.chartColors[i % item.chartColors.length]
                ));
            }
            legendAdapter.setLegendItems(legendItems);
            binding.legendRecyclerView.setLayoutManager(new LinearLayoutManager(context));
            binding.legendRecyclerView.setAdapter(legendAdapter);
        }
    }

    public class MonthViewHolder extends RecyclerView.ViewHolder {
        private final ItemMonthHeaderBinding binding;

        MonthViewHolder(ItemMonthHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            
            binding.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && monthToggleListener != null) {
                    monthToggleListener.onMonthToggle(getItem(pos).billUiModel.key);
                }
            });
        }

        void bind(AccountDetailUiModel item) {
            AccountBillUiModel m = item.billUiModel;
            binding.tvMonthTitle.setText(m.title);
            binding.tvMonthRange.setText(m.subtitle);
            binding.tvInflow.setText(m.inflowText);
            binding.tvOutflow.setText(m.outflowText);
            binding.ivArrow.setRotation(m.isCollapsed ? 0 : 180);

            // Card Style
            if (m.isCollapsed) {
                binding.getRoot().setBackgroundResource(R.drawable.bg_item_single);
                setMargins(binding.getRoot(), 16, 16, 16, 0);
                // 💡 影藏分割线但保持占位，确保高度一致
                binding.divider.setVisibility(View.INVISIBLE);
            } else {
                binding.getRoot().setBackgroundResource(R.drawable.bg_item_top);
                setMargins(binding.getRoot(), 16, 16, 16, 0);
                binding.divider.setVisibility(View.VISIBLE);
            }
        }
    }

    public class DayViewHolder extends RecyclerView.ViewHolder {

        DayViewHolder(ItemDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountDetailUiModel item) {
            AccountBillUiModel m = item.billUiModel;
            binding.tvDayTitle.setText(m.title);
            binding.tvDaySummary.setText(m.subtitle);
            binding.getRoot().setBackgroundColor(Color.WHITE);
            setMargins(binding.getRoot(), 16, 0, 16, 0);
            binding.divider.setVisibility(View.VISIBLE);
            
            // Give day headers a bit more internal breathing room
            binding.getRoot().setPadding(0, dp2px(4), 0, dp2px(4));
        }

        private final ItemDayHeaderBinding binding;
    }

    public class BillViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountTransactionBinding binding;

        BillViewHolder(ItemAccountTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountDetailUiModel item) {
            AccountBillUiModel m = item.billUiModel;
            if (m.categoryIconUrl != null && !m.categoryIconUrl.isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, m.categoryIconUrl, binding.ivCategoryIcon);
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_category);
            }

            // 🎨 设置图标背景色
            if (m.originalBill != null && m.originalBill.getCategoryIconBackgroundColor() != null && !m.originalBill.getCategoryIconBackgroundColor().isEmpty()) {
                try {
                    int color = Color.parseColor(m.originalBill.getCategoryIconBackgroundColor());
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(color);
                    binding.ivCategoryIcon.setBackground(gd);
                } catch (Exception e) {
                    binding.ivCategoryIcon.setBackgroundResource(R.drawable.bg_circle_grey);
                }
            } else {
                binding.ivCategoryIcon.setBackgroundResource(R.drawable.bg_circle_grey);
            }
            
            binding.tvCategoryName.setText(m.categoryName);
            binding.tvTransactionTime.setText(m.timeNote);
            binding.tvAmount.setText(m.amountText);
            binding.tvAmount.setTextColor(m.amountColor);
            binding.tvBalanceAfter.setText(m.balanceText);

            binding.contentView.setOnClickListener(v -> {
                if (listener != null) listener.onBillClick(m.originalBill);
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillDelete(m.originalBill);
                    binding.swipeLayout.quickClose();
                }
            });
            binding.btnRefund.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillRefund(m.originalBill);
                    binding.swipeLayout.quickClose();
                }
            });
            binding.btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillEdit(m.originalBill);
                    binding.swipeLayout.quickClose();
                }
            });

            // Card Style
            setMargins(binding.swipeLayout, 16, 0, 16, 0);
            if (item.isLastInSection) {
                binding.contentView.setBackgroundResource(R.drawable.bg_item_bottom);
                binding.divider.setVisibility(View.GONE);
            } else {
                binding.contentView.setBackgroundColor(Color.WHITE);
                binding.divider.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setMargins(View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int ml = dp2px(l);
            int mt = dp2px(t);
            int mr = dp2px(r);
            int mb = dp2px(b);
            if (p.leftMargin != ml || p.topMargin != mt || p.rightMargin != mr || p.bottomMargin != mb) {
                p.setMargins(ml, mt, mr, mb);
                v.requestLayout();
            }
        }
    }

    private int dp2px(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
