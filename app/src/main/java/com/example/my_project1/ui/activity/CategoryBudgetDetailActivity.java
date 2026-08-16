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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.ActivityCategoryBudgetDetailBinding;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.fragment.AddCategoryBudgetFragment;
import com.example.my_project1.ui.fragment.CategoryBudgetMenuFragment;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.ui.viewmodel.budget.CategoryBudgetDetailViewModel;
import com.example.my_project1.utils.GlideImageLoader;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

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
    public static final String EXTRA_YEAR          = "extra_year";
    public static final String EXTRA_MONTH         = "extra_month";

    private ActivityCategoryBudgetDetailBinding binding;
    private CategoryBudgetDetailViewModel vm;
    private BillViewModel billViewModel;
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
        i.putExtra(EXTRA_YEAR,         budget.getYear());
        i.putExtra(EXTRA_MONTH,        budget.getMonth());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBudgetDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        // Set status bar icons to dark
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        // 设置底部导航栏为白色
        getWindow().setNavigationBarColor(android.graphics.Color.WHITE);
        insetsController.setAppearanceLightNavigationBars(true);

        // 设置导航栏为页面顶部背景色以保持一致
        getWindow().setNavigationBarColor(android.graphics.Color.WHITE);
        insetsController.setAppearanceLightNavigationBars(true);


        vm = new ViewModelProvider(this).get(CategoryBudgetDetailViewModel.class);
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);

        // Restore args from Intent
        Intent intent    = getIntent();
        String catName   = intent.getStringExtra(EXTRA_CAT_NAME);
        String catIcon   = intent.getStringExtra(EXTRA_CAT_ICON);
        String catCloudId= intent.getStringExtra(EXTRA_CAT_CLOUD_ID);
        double budgetAmt = intent.getDoubleExtra(EXTRA_BUDGET_AMT, 0);
        int    period    = intent.getIntExtra(EXTRA_PERIOD, Budget.PERIOD_MONTH);
        long   startTime = intent.getLongExtra(EXTRA_START_TIME, 0);
        long   endTime   = intent.getLongExtra(EXTRA_END_TIME, 0);
        String budgetType= intent.getStringExtra(EXTRA_BUDGET_TYPE);
        int    year      = intent.getIntExtra(EXTRA_YEAR, Calendar.getInstance().get(Calendar.YEAR));
        int    month     = intent.getIntExtra(EXTRA_MONTH, Calendar.getInstance().get(Calendar.MONTH) + 1);

        // Build lightweight Budget object
        Budget budget = new Budget();
        budget.setId(intent.getIntExtra(EXTRA_BUDGET_ID, 0));
        budget.setAmount(budgetAmt);
        budget.setPeriod(period);
        budget.setBudgetType(budgetType);
        budget.setStartTime(startTime);
        budget.setEndTime(endTime);
        budget.setYear(year);
        budget.setMonth(month);
        budget.setTargetId(catCloudId);
        budget.setCategoryName(catName);
        budget.setCategoryIconUrl(catIcon);

        initTopBar(catName);
        initCategoryHeader(catName, catIcon, period, startTime, endTime);
        
        // Initial trend type: week for week budget, month otherwise
        String initialTrend = (period == Budget.PERIOD_WEEK) 
                ? CategoryBudgetDetailViewModel.TREND_WEEK 
                : CategoryBudgetDetailViewModel.TREND_MONTH;
        
        initTrendTabs(initialTrend);
        initChart();
        initRecyclerView();
        vm.init(budget, catCloudId);
        observeViewModel(budget, catCloudId);
    }

    private void initTopBar(String catName) {
        binding.tvTitle.setText(catName != null ? catName + " 预算详情" : "预算详情");
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivMenu.setOnClickListener(v -> showMenu());
    }

    private void showMenu() {
        Budget current = vm.budgetLive.getValue();
        if (current == null) return;
        
        // Ensure metadata is carried over to the menu/edit dialog
        if (current.getCategoryName().isEmpty()) {
            current.setCategoryName(getIntent().getStringExtra(EXTRA_CAT_NAME));
        }
        if (current.getCategoryIconUrl() == null || current.getCategoryIconUrl().isEmpty()) {
            current.setCategoryIconUrl(getIntent().getStringExtra(EXTRA_CAT_ICON));
        }

        CategoryBudgetMenuFragment menu = CategoryBudgetMenuFragment.newInstance(current);
        menu.setOnMenuActionListener(new CategoryBudgetMenuFragment.OnMenuActionListener() {
            @Override
            public void onEdit(Budget budget) {
                AddCategoryBudgetFragment.newInstance(budget, true)
                        .show(getSupportFragmentManager(), AddCategoryBudgetFragment.TAG);
            }

            @Override
            public void onDelete(Budget budget) {
                new ConfirmDialog(CategoryBudgetDetailActivity.this)
                        .setTitle("删除预算")
                        .setMessage("确定要删除该分类预算吗？")
                        .setConfirmListener(() -> {
                            vm.deleteCategoryBudget();
                        }).show();
            }
        });
        menu.show(getSupportFragmentManager(), CategoryBudgetMenuFragment.TAG);
    }

    private void initCategoryHeader(String catName, String catIcon, int period,
                                    long startTime, long endTime) {
        binding.tvCategoryName.setText(catName != null ? catName : "未知分类");

        // Period label with actual date range for day/week budgets
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

    private void initTrendTabs(String defaultTrend) {
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
        
        vm.trendTypeLive.setValue(defaultTrend);
        applyTrendTab(defaultTrend);
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
        chart.setDrawBorders(false);
        
        // Match the clean look of the target image
        chart.setViewPortOffsets(80f, 40f, 40f, 60f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(0xFFBBBBBB);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        xAxis.setYOffset(10f);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(0xFFBBBBBB);
        left.setTextSize(10f);
        left.setDrawGridLines(true);
        left.setGridColor(0xFFEEEEEE);
        left.setDrawAxisLine(false);
        left.setLabelCount(4, true);
        left.setXOffset(10f);
        left.setAxisMinimum(0f); // Ensure it starts from 0.0M
        
        left.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value >= 1000000) return String.format(Locale.getDefault(), "%.1fM", value / 1000000f);
                if (value >= 1000) return String.format(Locale.getDefault(), "%.0fK", value / 1000f);
                return String.valueOf((int) value);
            }
        });

        chart.getAxisRight().setEnabled(false);
    }

    private void initRecyclerView() {
        billAdapter = new BillListAdapter();
        binding.rvBills.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBills.setAdapter(billAdapter);
        binding.rvBills.setNestedScrollingEnabled(false);
    }

    private void observeViewModel(Budget budget, String catCloudId) {
        // Refresh summary card when budget or spent amount updates
        vm.budgetAmountLive.observe(this, amt -> refreshSummaryCard());
        vm.spentLive.observe(this, spent -> refreshSummaryCard());

        // Sync with budget changes
        vm.budgetLive.observe(this, b -> {
            if (b == null) {
                finish();
                return;
            }
            if (vm.budgetAmountLive.getValue() == null || Double.compare(vm.budgetAmountLive.getValue(), b.getAmount()) != 0) {
                vm.budgetAmountLive.setValue(b.getAmount());
            }
        });

        vm.trendDataLive.observe(this, data -> {
            String[] labels = vm.trendLabelsLive.getValue();
            updateChart(data, labels);
        });
        vm.trendLabelsLive.observe(this, labels -> {
            double[] data = vm.trendDataLive.getValue();
            if (data != null) updateChart(data, labels);
        });

        vm.billsLive.observe(this, bills -> {
            // Bills are queried by ViewModel according to effectiveStartTime/endTime
            binding.tvBillCount.setText(String.format(Locale.getDefault(), "共 %d 笔消费", bills != null ? bills.size() : 0));
            List<BillUiModel> uiModels = billViewModel.mapBillsToUiModels(bills);
            billAdapter.submitList(uiModels);
            binding.tvEmptyBills.setVisibility(
                    (bills == null || bills.isEmpty()) ? View.VISIBLE : View.GONE);
        });

        // Trigger UI refresh when accounts are loaded to ensure correct account names
        billViewModel.getAllAccountsLive().observe(this, accounts -> {
            List<Bill> currentBills = vm.billsLive.getValue();
            if (currentBills != null && !currentBills.isEmpty()) {
                List<BillUiModel> uiModels = billViewModel.mapBillsToUiModels(currentBills);
                billAdapter.submitList(uiModels);
            }
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
                    AppCompatResources.getDrawable(this, R.drawable.bg_progress_budget_blue));
        } else {
            binding.progressDetail.setProgressDrawable(
                    AppCompatResources.getDrawable(this, R.drawable.progress_budget_red));
        }
    }

    private void updateChart(double[] data, String[] labels) {
        if (data == null || data.length == 0) return;

        LineChart chart = binding.lineChartTrend;
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            entries.add(new Entry(i, (float) data[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "支出");
        dataSet.setColor(0xFF444444); // Dark grey line
        dataSet.setLineWidth(2.5f);
        
        // Hide all data points (circles)
        dataSet.setDrawCircles(false);
        dataSet.setDrawCircleHole(false);
        
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.1f);
        
        // Setup gradient fill
        dataSet.setDrawFilled(true);
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_chart_gradient_blue);
        dataSet.setFillDrawable(drawable);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        if (labels != null && labels.length > 0) {
            chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            chart.getXAxis().setLabelCount(Math.min(labels.length, 5), false);
        }

        chart.invalidate();
        chart.animateX(800);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    /**
     * Helper to load icon from URI/String resource
     */
    private static void loadIcon(Context ctx, String uri, ImageView iv, int defaultRes) {
        if (uri == null || uri.isEmpty()) {
            iv.setImageResource(defaultRes);
            return;
        }
        try {
            int resId = Integer.parseInt(uri);
            if (resId > 0) GlideImageLoader.load1(ctx, iv, resId);
            else iv.setImageResource(defaultRes);
        } catch (NumberFormatException e) {
            GlideImageLoader.load(ctx, uri, iv);
        }
    }

    // ==============================
    // 适配器：已完成 太阳/月亮 + 点击跳转详情

    // ==============================
    static class BillListAdapter extends ListAdapter<BillUiModel, BillListAdapter.VH> {

        public BillListAdapter() {
            super(new DiffUtil.ItemCallback<BillUiModel>() {
                @Override
                public boolean areItemsTheSame(@androidx.annotation.NonNull BillUiModel oldItem, @androidx.annotation.NonNull BillUiModel newItem) {
                    return oldItem.localId == newItem.localId;
                }

                @Override
                public boolean areContentsTheSame(@androidx.annotation.NonNull BillUiModel oldItem, @androidx.annotation.NonNull BillUiModel newItem) {
                    return oldItem.equals(newItem);
                }
            });
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            BillUiModel b = getItem(pos);

            // 1. Time display
            h.tvTime.setText(b.timeText);
            
            // Time icon logic
            try {
                int hour = Integer.parseInt(b.timeText.split(":")[0]);
                h.ivTimeIcon.setImageResource(hour >= 6 && hour < 18 ? R.drawable.ic_sun : R.drawable.ic_moon);
            } catch (Exception e) {
                h.ivTimeIcon.setImageResource(R.drawable.ic_sun);
            }

            // 2. Category name
            h.tvCategoryName.setText(b.categoryName);

            // 3. Amount
            h.tvAmount.setText(b.amountText);
            h.tvAmount.setTextColor(b.amountColor);

            // 4. Account name
            h.tvAccount.setText(b.accountName != null && !b.accountName.isEmpty() ? b.accountName : "无账户");
            loadIcon(h.itemView.getContext(), b.accountIconUrl, h.ivAccountIcon, R.drawable.ic_wallet);

            // 5. Category icon
            if (b.categoryIconUrl != null && !b.categoryIconUrl.isEmpty()) {
                loadIcon(h.itemView.getContext(), b.categoryIconUrl, h.ivCategoryIcon, R.drawable.ic_category_default);
            } else {
                h.ivCategoryIcon.setImageResource(R.drawable.ic_category_default);
            }

            // Open detail on click
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), BillDetailActivity.class);
                intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, b.objectId);
                intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, b.localId);
                v.getContext().startActivity(intent);
                if (v.getContext() instanceof AppCompatActivity) {
                    ((AppCompatActivity) v.getContext()).overridePendingTransition(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                    );
                }
            });
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivTimeIcon, ivCategoryIcon, ivAccountIcon;
            TextView tvTime, tvCategoryName, tvAmount, tvAccount;

            VH(View v) {
                super(v);
                ivTimeIcon      = v.findViewById(R.id.iv_time_icon);
                tvTime          = v.findViewById(R.id.tv_time);
                ivCategoryIcon  = v.findViewById(R.id.iv_category_icon);
                ivAccountIcon   = v.findViewById(R.id.iv_account_icon);
                tvCategoryName  = v.findViewById(R.id.tv_category_name);
                tvAmount        = v.findViewById(R.id.tv_amount);
                tvAccount       = v.findViewById(R.id.tv_account);
            }
        }
    }
}