package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.databinding.ItemAccountDetailHeaderBinding;
import com.example.my_project1.ui.adapter.ChartLegendAdapter;
import com.example.my_project1.ui.view.RoundedPieChartRenderer;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AccountHeaderAdapter extends RecyclerView.Adapter<AccountHeaderAdapter.HeaderViewHolder> {

    private final Context context;
    private Account account;
    private SearchSummary stats;
    private List<BillDao.CategorySummary> categorySummaries;
    private boolean showingExpense = true;
    private OnHeaderActionListener listener;

    private static final int[] CHART_COLORS = {0xFFFFB726, 0xFF2196F3, 0xFFFFCC80, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4, 0xFFF48FB1, 0xFFA1E59C};

    public interface OnHeaderActionListener {
        void onEditBalance();
        void onMore();
        void onRepayAction();
        void onRepayNow();
        void onToggleChart();
    }

    public AccountHeaderAdapter(Context context) {
        this.context = context;
    }

    public void setAccount(Account account) {
        this.account = account;
        notifyItemChanged(0);
    }

    public void setStats(SearchSummary stats) {
        this.stats = stats;
        notifyItemChanged(0);
    }

    public void setCategorySummaries(List<BillDao.CategorySummary> summaries, boolean showingExpense) {
        this.categorySummaries = summaries;
        this.showingExpense = showingExpense;
        notifyItemChanged(0);
    }

    public void setOnHeaderActionListener(OnHeaderActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAccountDetailHeaderBinding binding = ItemAccountDetailHeaderBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new HeaderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
        holder.bind();
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountDetailHeaderBinding binding;
        private final DecimalFormat df = new DecimalFormat("#,##0.00");
        private final ChartLegendAdapter legendAdapter;

        HeaderViewHolder(ItemAccountDetailHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.legendAdapter = new ChartLegendAdapter(context);
            binding.legendRecyclerView.setLayoutManager(new LinearLayoutManager(context));
            binding.legendRecyclerView.setAdapter(legendAdapter);

            binding.btnEditBalance.setOnClickListener(v -> { if (listener != null) listener.onEditBalance(); });
            binding.btnMore.setOnClickListener(v -> { if (listener != null) listener.onMore(); });
            binding.btnRepayAction.setOnClickListener(v -> { if (listener != null) listener.onRepayAction(); });
            binding.btnRepayNow.setOnClickListener(v -> { if (listener != null) listener.onRepayNow(); });
            binding.ivToggleChart.setOnClickListener(v -> { if (listener != null) listener.onToggleChart(); });
        }

        void bind() {
            if (account == null) return;

            binding.tvAccountName.setText(account.getName());
            if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, account.getIconUrl(), binding.ivAccountIcon);
            } else {
                binding.ivAccountIcon.setImageResource(R.drawable.ic_wallet);
            }

            if (account.isCredit()) {
                binding.layoutCreditInfo.setVisibility(View.VISIBLE);
                binding.cardCreditBill.setVisibility(View.VISIBLE);
                binding.btnRepayAction.setVisibility(View.VISIBLE);
                binding.tvBalanceLabel.setText("当前欠款 (CNY)");
                binding.tvBalanceAmount.setText(String.format("¥%s", df.format(Math.abs(account.getBalance()))));
                binding.tvAvailableLimit.setText(String.format("可用额度 ¥%s", df.format(account.getCreditLimit() + account.getBalance())));
                binding.tvBillingStatus.setText(String.format("出账日 %s", account.getBillingDay() > 0 ? account.getBillingDay() + "日" : "--"));
                binding.tvRepaymentStatus.setText(String.format("还款日 %s", account.getRepaymentDay() > 0 ? account.getRepaymentDay() + "日" : "--"));

                Calendar cal = Calendar.getInstance();
                binding.tvBillMonthLabel.setText(String.format(Locale.getDefault(), "%d年%d月账单（剩余应还）", 
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1));
                binding.tvBillAmount.setText(String.format(Locale.getDefault(), "¥%s", df.format(Math.abs(account.getBalance()))));
            } else {
                binding.layoutCreditInfo.setVisibility(View.GONE);
                binding.cardCreditBill.setVisibility(View.GONE);
                binding.btnRepayAction.setVisibility(View.GONE);
                binding.tvBalanceLabel.setText("账户余额 (CNY)");
                binding.tvBalanceAmount.setText(String.format("¥%s", df.format(account.getBalance())));
            }
            
            renderChart();
        }

        private void renderChart() {
            PieChart pieChart = binding.pieChart;
            if (categorySummaries == null || categorySummaries.isEmpty()) {
                pieChart.clear();
                pieChart.setNoDataText(showingExpense ? "暂无支出数据" : "暂无收入数据");
                binding.legendRecyclerView.setVisibility(View.GONE);
                return;
            }

            binding.legendRecyclerView.setVisibility(View.VISIBLE);
            List<PieEntry> entries = new ArrayList<>();
            double total = 0;
            for (BillDao.CategorySummary summary : categorySummaries) {
                entries.add(new PieEntry((float) summary.totalAmount, summary.categoryName));
                total += summary.totalAmount;
            }

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setColors(CHART_COLORS);
            dataSet.setSliceSpace(3f);
            dataSet.setSelectionShift(0f);
            dataSet.setDrawValues(false);

            pieChart.setData(new PieData(dataSet));
            pieChart.setUsePercentValues(false);
            pieChart.getDescription().setEnabled(false);
            pieChart.setDrawHoleEnabled(true);
            pieChart.setHoleColor(Color.TRANSPARENT);
            pieChart.setHoleRadius(72f);
            pieChart.getLegend().setEnabled(false);
            pieChart.setDrawEntryLabels(false);
            pieChart.setRenderer(new RoundedPieChartRenderer(pieChart, pieChart.getAnimator(), pieChart.getViewPortHandler()));

            String topText = showingExpense ? "支出类别" : "收入类别";
            String bottomText = "¥" + df.format(total);
            SpannableString centerText = new SpannableString(topText + "\n" + bottomText);
            centerText.setSpan(new RelativeSizeSpan(0.85f), 0, topText.length(), 0);
            centerText.setSpan(new ForegroundColorSpan(0xFF999999), 0, topText.length(), 0);
            centerText.setSpan(new RelativeSizeSpan(1.3f), topText.length() + 1, centerText.length(), 0);
            centerText.setSpan(new ForegroundColorSpan(0xFF333333), topText.length() + 1, centerText.length(), 0);
            centerText.setSpan(new StyleSpan(Typeface.BOLD), topText.length() + 1, centerText.length(), 0);
            pieChart.setCenterText(centerText);
            pieChart.invalidate();

            List<ChartLegendAdapter.LegendItem> legendItems = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                PieEntry entry = entries.get(i);
                float percentage = (total > 0) ? (entry.getValue() / (float)total) * 100 : 0;
                legendItems.add(new ChartLegendAdapter.LegendItem(
                        entry.getLabel(),
                        String.format(Locale.getDefault(), "%.0f%%", percentage),
                        "¥" + df.format(entry.getValue()),
                        CHART_COLORS[i % CHART_COLORS.length]
                ));
            }
            legendAdapter.setLegendItems(legendItems);
        }
    }
}
