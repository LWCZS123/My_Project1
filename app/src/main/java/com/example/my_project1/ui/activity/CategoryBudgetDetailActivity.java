package com.example.my_project1.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.ActivityCategoryBudgetDetailBinding;
import com.example.my_project1.ui.viewmodel.budget.CategoryBudgetDetailViewModel;
import com.example.my_project1.utils.GlideImageLoader;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * CategoryBudgetDetailActivity
 *
 * 展示某条分类预算的详情，包括：
 *   - 预算金额 / 已用 / 剩余（超支）汇总卡片
 *   - 周期内关联账单列表
 *   - 消费趋势折线图（周 / 月 / 年视图）
 *
 * 账单和已用金额的时间范围由 CategoryBudgetDetailViewModel 统一计算，
 * 天/周预算以当前时间实时计算窗口，保证与列表进度条数据的统计口径一致。
 */
public class CategoryBudgetDetailActivity extends AppCompatActivity {

    // ── Intent Extras ────────────────────────────────────────
    public static final String EXTRA_BUDGET_ID     = "extra_budget_id";
    public static final String EXTRA_CAT_NAME      = "extra_cat_name";
    public static final String EXTRA_CAT_ICON      = "extra_cat_icon";
    public static final String EXTRA_BUDGET_AMT    = "extra_budget_amt";
    public static final String EXTRA_BUDGET_TYPE   = "extra_budget_type";
    public static final String EXTRA_CAT_CLOUD_ID  = "extra_cat_cloud_id";
    public static final String EXTRA_PERIOD        = "extra_period";
    public static final String EXTRA_START_TIME    = "extra_start_time";
    public static final String EXTRA_END_TIME      = "extra_end_time";

    private ActivityCategoryBudgetDetailBinding binding;
    private CategoryBudgetDetailViewModel vm;
    private BillListAdapter billAdapter;

    // ── 便捷启动 ─────────────────────────────────────────────
    public static void start(Context ctx, Budget budget,
                             String catName, String catIcon, String catCloudId) {
        Intent i = new Intent(ctx, CategoryBudgetDetailActivity.class);
        i.putExtra(EXTRA_BUDGET_ID,    budget.getId());
        i.putExtra(EXTRA_CAT_NAME,     catName   != null ? catName   : "未知分类");
        i.putExtra(EXTRA_CAT_ICON,     catIcon);
        i.putExtra(EXTRA_BUDGET_AMT,   budget.getAmount());
        i.putExtra(EXTRA_BUDGET_TYPE,  budget.getBudgetType());
        i.putExtra(EXTRA_CAT_CLOUD_ID, catCloudId != null ? catCloudId : "");
        i.putExtra(EXTRA_PERIOD,       budget.getPeriod());
        i.putExtra(EXTRA_START_TIME,   budget.getStartTime());
        i.putExtra(EXTRA_END_TIME,     budget.getEndTime());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBudgetDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(0, top, 0, 0);

            return insets;
        });

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);


        vm = new ViewModelProvider(this).get(CategoryBudgetDetailViewModel.class);

        // 从 Intent 还原参数
        Intent intent    = getIntent();
        String catName   = intent.getStringExtra(EXTRA_CAT_NAME);
        String catIcon   = intent.getStringExtra(EXTRA_CAT_ICON);
        String catCloudId= intent.getStringExtra(EXTRA_CAT_CLOUD_ID);
        double budgetAmt = intent.getDoubleExtra(EXTRA_BUDGET_AMT, 0);
        int    period    = intent.getIntExtra(EXTRA_PERIOD, Budget.PERIOD_MONTH);
        long   startTime = intent.getLongExtra(EXTRA_START_TIME, 0);
        long   endTime   = intent.getLongExtra(EXTRA_END_TIME, 0);
        String budgetType= intent.getStringExtra(EXTRA_BUDGET_TYPE);

        // 构造轻量 Budget 对象
        Budget budget = new Budget();
        budget.setId(intent.getIntExtra(EXTRA_BUDGET_ID, 0));
        budget.setAmount(budgetAmt);
        budget.setPeriod(period);
        budget.setBudgetType(budgetType);
        budget.setStartTime(startTime);
        budget.setEndTime(endTime);
        budget.setTargetId(catCloudId);

        initTopBar(catName);
        initCategoryHeader(catName, catIcon, period, startTime, endTime);
        initTrendTabs();
        initChart();
        initRecyclerView();
        observeViewModel(budget, catCloudId);

        vm.init(budget, catCloudId);
    }

    private void initTopBar(String catName) {
        binding.tvTitle.setText(catName != null ? catName + " 预算详情" : "预算详情");
        binding.ivBack.setOnClickListener(v -> finish());
    }

    private void initCategoryHeader(String catName, String catIcon, int period,
                                    long startTime, long endTime) {
        binding.tvCategoryName.setText(catName != null ? catName : "未知分类");

        // 对天/周预算，显示的周期标签附带实际日期范围，便于用户理解当前统计窗口
        String periodLabel = Budget.getPeriodLabel(period) + "预算";
        if (period == Budget.PERIOD_DAY || period == Budget.PERIOD_WEEK) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd", Locale.getDefault());
            periodLabel += "（" + sdf.format(new java.util.Date(startTime))
                    + " - " + sdf.format(new java.util.Date(endTime)) + "）";
        }
        binding.tvPeriodLabel.setText(periodLabel);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            if (catIcon != null && !catIcon.isEmpty()) {
                try {
                    int resId = Integer.parseInt(catIcon);
                    GlideImageLoader.load1(this, binding.ivCategoryIcon, resId);
                } catch (NumberFormatException e) {
                    GlideImageLoader.load(this, catIcon, binding.ivCategoryIcon);
                }
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);
            }
        }
    }

    private void initTrendTabs() {
        binding.tabTrendWeek.setOnClickListener(v -> {
            vm.switchTrend(CategoryBudgetDetailViewModel.TREND_WEEK);
            applyTrendTab(CategoryBudgetDetailViewModel.TREND_WEEK);
        });
        binding.tabTrendMonth.setOnClickListener(v -> {
            vm.switchTrend(CategoryBudgetDetailViewModel.TREND_MONTH);
            applyTrendTab(CategoryBudgetDetailViewModel.TREND_MONTH);
        });
        binding.tabTrendYear.setOnClickListener(v -> {
            vm.switchTrend(CategoryBudgetDetailViewModel.TREND_YEAR);
            applyTrendTab(CategoryBudgetDetailViewModel.TREND_YEAR);
        });
        applyTrendTab(CategoryBudgetDetailViewModel.TREND_MONTH);
    }

    private void applyTrendTab(String activeType) {
        applyTab(binding.tabTrendWeek,  CategoryBudgetDetailViewModel.TREND_WEEK.equals(activeType));
        applyTab(binding.tabTrendMonth, CategoryBudgetDetailViewModel.TREND_MONTH.equals(activeType));
        applyTab(binding.tabTrendYear,  CategoryBudgetDetailViewModel.TREND_YEAR.equals(activeType));
    }

    private void applyTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            tab.setTextColor(0xFFFFFFFF);
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF888888);
        }
    }

    private void initChart() {
        LineChart chart = binding.lineChartTrend;
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.getLegend().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(0xFF888888);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(0xFF888888);
        left.setTextSize(10f);
        left.setDrawGridLines(true);
        left.setGridColor(0xFFEEEEEE);

        chart.getAxisRight().setEnabled(false);
    }

    private void initRecyclerView() {
        billAdapter = new BillListAdapter();
        binding.rvBills.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBills.setAdapter(billAdapter);
        binding.rvBills.setNestedScrollingEnabled(false);
    }

    private void observeViewModel(Budget budget, String catCloudId) {
        // 预算金额或已用金额任一更新时刷新汇总卡片
        vm.budgetAmountLive.observe(this, amt -> refreshSummaryCard());
        vm.spentLive.observe(this, spent -> refreshSummaryCard());

        vm.trendDataLive.observe(this, data -> {
            String[] labels = vm.trendLabelsLive.getValue();
            updateChart(data, labels);
        });
        vm.trendLabelsLive.observe(this, labels -> {
            double[] data = vm.trendDataLive.getValue();
            if (data != null) updateChart(data, labels);
        });

        vm.billsLive.observe(this, bills -> {
            // 账单列表由 ViewModel 根据 effectiveStartTime/endTime 查询
            // 与列表页进度条使用相同的时间范围，数据一致
            binding.tvBillCount.setText("共 " + (bills != null ? bills.size() : 0) + " 笔消费");
            billAdapter.submitList(bills != null ? bills : new ArrayList<>());
            binding.tvEmptyBills.setVisibility(
                    (bills == null || bills.isEmpty()) ? View.VISIBLE : View.GONE);
        });
    }

    private void refreshSummaryCard() {
        Double amt   = vm.budgetAmountLive.getValue();
        Double spent = vm.spentLive.getValue();
        if (amt == null) amt = 0.0;
        if (spent == null) spent = 0.0;

        double remaining = amt - spent;
        boolean over     = remaining < 0;

        binding.tvBudgetAmt.setText(String.format(Locale.getDefault(), "¥%.2f", amt));
        binding.tvSpentAmt.setText(String.format(Locale.getDefault(), "¥%.2f", spent));
        if (!over) {
            binding.tvRemainingAmt.setText(String.format(Locale.getDefault(), "¥%.2f", remaining));
            binding.tvRemainingLabel.setText("剩余");
            binding.tvRemainingAmt.setTextColor(0xFF4CAF50);
        } else {
            binding.tvRemainingAmt.setText(String.format(Locale.getDefault(), "¥%.2f", Math.abs(remaining)));
            binding.tvRemainingLabel.setText("超支");
            binding.tvRemainingAmt.setTextColor(0xFFFF5252);
        }

        int progress = amt > 0 ? (int) Math.min(spent / amt * 100, 100) : 0;
        binding.progressDetail.setProgress(progress);
        if (progress < 100 && !over) {
            binding.progressDetail.setProgressDrawable(
                    getResources().getDrawable(R.drawable.bg_progress_budget_blue));
        } else {
            binding.progressDetail.setProgressDrawable(
                    getResources().getDrawable(R.drawable.progress_budget_red));
        }
    }

    private void updateChart(double[] data, String[] labels) {
        if (data == null || data.length == 0) return;

        LineChart chart = binding.lineChartTrend;
        List<Entry> entries = new ArrayList<>();

        boolean isYear = data.length == 12 && (labels != null && labels.length == 12);
        int pointCount = isYear ? 12 : data.length;
        for (int i = 0; i < Math.min(pointCount, data.length); i++) {
            entries.add(new Entry(i, (float) data[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "支出");
        dataSet.setColor(0xFF5B8DEF);
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(0xFF5B8DEF);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(0xFF5B8DEF);
        dataSet.setFillAlpha(30);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        if (labels != null && labels.length > 0) {
            chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            chart.getXAxis().setLabelCount(Math.min(labels.length, isYear ? 12 : 6), false);
        }

        chart.invalidate();
        chart.animateX(400);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ==============================
    // 适配器：已完成 太阳/月亮 + 点击跳转详情
    // ==============================
    static class BillListAdapter extends RecyclerView.Adapter<BillListAdapter.VH> {

        private List<Bill> list = new ArrayList<>();
        private static final SimpleDateFormat SDF_TIME =
                new SimpleDateFormat("HH:mm", Locale.getDefault());

        void submitList(List<Bill> newList) {
            list = newList;
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            Bill b = list.get(pos);

            // 1. 时间显示
            if (b.getBillTime() != null) {
                h.tvTime.setText(SDF_TIME.format(b.getBillTime()));

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(b.getBillTime());
                int hour = calendar.get(Calendar.HOUR_OF_DAY);

                if (hour >= 6 && hour < 18) {
                    h.ivTimeIcon.setImageResource(R.drawable.ic_sun);
                } else {
                    h.ivTimeIcon.setImageResource(R.drawable.ic_moon);
                }

            } else {
                h.tvTime.setText("--:--");
                h.ivTimeIcon.setImageResource(R.drawable.ic_sun);
            }

            // 2. 分类名称
            h.tvCategoryName.setText(b.getCategoryName() != null ? b.getCategoryName() : "未知分类");

            // 3. 金额
            h.tvAmount.setText(String.format(Locale.getDefault(), "¥%.2f", b.getAmount()));
            h.tvAmount.setTextColor(0xFFFF5252);

            // 4. 分类图标
            if (b.getCategoryIconUrl() != null && !b.getCategoryIconUrl().isEmpty()) {
                try {
                    int resId = Integer.parseInt(b.getCategoryIconUrl());
                    GlideImageLoader.load1(h.itemView.getContext(), h.ivCategoryIcon, resId);
                } catch (Exception e) {
                    GlideImageLoader.load(h.itemView.getContext(), b.getCategoryIconUrl(), h.ivCategoryIcon);
                }
            } else {
                h.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);
            }

            // ======================
            // 点击条目跳转到详情页
            // ======================
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), BillDetailActivity.class);
                intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, b.getObjectId());
                intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, b.getId());
                v.getContext().startActivity(intent);
                ((CategoryBudgetDetailActivity) v.getContext()).overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                );
            });
        }

        @Override
        public int getItemCount() {
            return list == null ? 0 : list.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivTimeIcon, ivCategoryIcon;
            TextView tvTime, tvCategoryName, tvAmount;

            VH(View v) {
                super(v);
                ivTimeIcon      = v.findViewById(R.id.iv_time_icon);
                tvTime          = v.findViewById(R.id.tv_time);
                ivCategoryIcon  = v.findViewById(R.id.iv_category_icon);
                tvCategoryName  = v.findViewById(R.id.tv_category_name);
                tvAmount        = v.findViewById(R.id.tv_amount);
            }
        }
    }
}