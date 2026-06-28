package com.example.my_project1.ui.activity;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ActivityBillStatisticsBinding;
import com.example.my_project1.ui.adapter.bill.CategoryStatAdapter;
import com.example.my_project1.ui.fragment.DateTimePickerFragment;
import com.example.my_project1.ui.viewmodel.billvm.BillStatisticsViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 账单统计页面（ViewBinding 版）
 *
 * 分类列表点击：跳转 CategoryBillsActivity，
 * 使用 overridePendingTransition(slide_in_right, slide_out_left)。
 */
public class BillStatisticsActivity extends AppCompatActivity {

    private ActivityBillStatisticsBinding binding;
    private BillStatisticsViewModel       viewModel;
    private CategoryStatAdapter           categoryAdapter;

    private boolean isCustomMode  = false;
    private long    customStartTs = 0L;
    private long    customEndTs   = 0L;

    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy.M.d", Locale.CHINESE);

    // ================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        binding   = ActivityBillStatisticsBinding.inflate(getLayoutInflater());
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

        viewModel = new ViewModelProvider(this).get(BillStatisticsViewModel.class);

        initCustomDateDefaults();
        setupPeriodTabs();
        setupDateNavigation();
        setupPieTabs();
        setupCategoryList();
        observeViewModel();

        activatePeriodTab(binding.tabMonth, BillStatisticsViewModel.Period.MONTH);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ================================================================
    //  自定义日期默认值（最近 7 天）
    // ================================================================

    private void initCustomDateDefaults() {
        Calendar cal = Calendar.getInstance();
        customEndTs = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        customStartTs = cal.getTimeInMillis();
    }

    // ================================================================
    //  周 / 月 / 年 / 自定义 Tab
    // ================================================================

    private void setupPeriodTabs() {
        binding.tabWeek.setOnClickListener(v ->
                activatePeriodTab(binding.tabWeek, BillStatisticsViewModel.Period.WEEK));
        binding.tabMonth.setOnClickListener(v ->
                activatePeriodTab(binding.tabMonth, BillStatisticsViewModel.Period.MONTH));
        binding.tabYear.setOnClickListener(v ->
                activatePeriodTab(binding.tabYear, BillStatisticsViewModel.Period.YEAR));
        binding.tabCustom.setOnClickListener(v -> activateCustomTab());
    }

    private void activatePeriodTab(TextView selected, BillStatisticsViewModel.Period period) {
        isCustomMode = false;
        applyTabStyle(selected);
        setDateTextClickable(false);
        viewModel.setPeriod(period);
        binding.ivLeft.setVisibility(View.VISIBLE);
        binding.ivRight.setVisibility(View.VISIBLE);
    }

    private void activateCustomTab() {
        isCustomMode = true;
        applyTabStyle(binding.tabCustom);
        setDateTextClickable(true);
        binding.ivLeft.setVisibility(View.INVISIBLE);
        binding.ivRight.setVisibility(View.INVISIBLE);
        binding.tvStartDate.setText(dateFmt.format(new Date(customStartTs)));
        binding.tvEndDate.setText(dateFmt.format(new Date(customEndTs)));
        applyCustomRange();
    }

    private void applyTabStyle(TextView selected) {
        for (TextView tv : new TextView[]{
                binding.tabWeek, binding.tabMonth,
                binding.tabYear, binding.tabCustom}) {
            boolean sel = (tv == selected);
            tv.setBackgroundResource(sel ? R.drawable.bg_tab_selected1 : 0);
            tv.setTextColor(sel ? 0xFF333333 : 0xFF888888);
        }
    }

    private void setDateTextClickable(boolean enable) {
        binding.tvStartDate.setClickable(enable);
        binding.tvStartDate.setFocusable(enable);
        binding.tvEndDate.setClickable(enable);
        binding.tvEndDate.setFocusable(enable);

        int color = enable ? 0xFF2F5FFF : 0xFF1A1A1A;
        binding.tvStartDate.setTextColor(color);
        binding.tvEndDate.setTextColor(color);

        int underline = android.graphics.Paint.UNDERLINE_TEXT_FLAG;
        if (enable) {
            binding.tvStartDate.setPaintFlags(binding.tvStartDate.getPaintFlags() | underline);
            binding.tvEndDate.setPaintFlags(binding.tvEndDate.getPaintFlags() | underline);
        } else {
            binding.tvStartDate.setPaintFlags(binding.tvStartDate.getPaintFlags() & ~underline);
            binding.tvEndDate.setPaintFlags(binding.tvEndDate.getPaintFlags() & ~underline);
        }
    }

    // ================================================================
    //  日期导航
    // ================================================================

    private void setupDateNavigation() {
        binding.ivLeft.setOnClickListener(v -> viewModel.navigatePrevious());
        binding.ivRight.setOnClickListener(v -> viewModel.navigateNext());
        binding.ivBack.setOnClickListener(v -> finish());

        binding.tvStartDate.setOnClickListener(v -> {
            if (!isCustomMode) return;
            DateTimePickerFragment picker = new DateTimePickerFragment();
            picker.setOnDateTimeSelectedListener((ts, formatted) -> {
                if (customEndTs > 0 && ts > customEndTs) {
                    shakeView(binding.tvStartDate);
                    showToast("开始日期不能晚于结束日期");
                    return;
                }
                customStartTs = ts;
                binding.tvStartDate.setText(dateFmt.format(new Date(ts)));
                applyCustomRange();
            });
            picker.show(getSupportFragmentManager(), "pick_start");
        });

        binding.tvEndDate.setOnClickListener(v -> {
            if (!isCustomMode) return;
            DateTimePickerFragment picker = new DateTimePickerFragment();
            picker.setOnDateTimeSelectedListener((ts, formatted) -> {
                if (customStartTs > 0 && ts < customStartTs) {
                    shakeView(binding.tvEndDate);
                    showToast("结束日期不能早于开始日期");
                    return;
                }
                customEndTs = ts;
                binding.tvEndDate.setText(dateFmt.format(new Date(ts)));
                applyCustomRange();
            });
            picker.show(getSupportFragmentManager(), "pick_end");
        });
    }

    private void applyCustomRange() {
        if (customStartTs > 0 && customEndTs > 0)
            viewModel.setCustomRange(new Date(customStartTs), new Date(customEndTs));
    }

    private void shakeView(View v) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 12f, -12f, 8f, -8f, 4f, -4f, 0f);
        a.setDuration(500);
        a.addUpdateListener(va -> v.setTranslationX((float) va.getAnimatedValue()));
        a.start();
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ================================================================
    //  饼图 Tab
    // ================================================================

    private void setupPieTabs() {
        binding.tabPieExpense.setOnClickListener(v -> {
            viewModel.setPieType(0);
            applyPieTabStyle(true);
        });
        binding.tabPieIncome.setOnClickListener(v -> {
            viewModel.setPieType(1);
            applyPieTabStyle(false);
        });
    }

    private void applyPieTabStyle(boolean isExpense) {
        if (isExpense) {
            binding.tabPieExpense.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            binding.tabPieExpense.setTextColor(0xFFFFFFFF);
            binding.tabPieIncome.setBackgroundResource(0);
            binding.tabPieIncome.setTextColor(0xFF888888);
        } else {
            binding.tabPieIncome.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            binding.tabPieIncome.setTextColor(0xFFFFFFFF);
            binding.tabPieExpense.setBackgroundResource(0);
            binding.tabPieExpense.setTextColor(0xFF888888);
        }
    }

    // ================================================================
    //  分类列表
    // ================================================================

    private void setupCategoryList() {
        categoryAdapter = new CategoryStatAdapter();
        binding.rvCategoryList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategoryList.setAdapter(categoryAdapter);
        binding.rvCategoryList.setNestedScrollingEnabled(false);

        categoryAdapter.setOnItemClickListener((item, position) -> {
            // 高亮选中状态
            categoryAdapter.setSelectedPosition(position);

            // 跳转分类明细页
            Intent intent = new Intent(this, CategoryBillsActivity.class);
            intent.putExtra(CategoryBillsActivity.EXTRA_CATEGORY_NAME,   item.categoryName);
            intent.putExtra(CategoryBillsActivity.EXTRA_CATEGORY_ICON,   item.categoryIconUrl);
            intent.putExtra(CategoryBillsActivity.EXTRA_BILL_COUNT,      item.billCount);
            intent.putExtra(CategoryBillsActivity.EXTRA_PERIOD_START_MS, viewModel.getWindowStartMs());
            intent.putExtra(CategoryBillsActivity.EXTRA_PERIOD_END_MS,   viewModel.getWindowEndMs());
            intent.putExtra(CategoryBillsActivity.EXTRA_BILL_TYPE,       viewModel.getCurrentPieType());
            startActivity(intent);

            // 跳转动画：新页从右侧滑入，当前页向左滑出
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    // ================================================================
    //  ViewModel 观察
    // ================================================================

    private void observeViewModel() {
        viewModel.periodLabel.observe(this, label -> {
            if (label == null || !label.contains(" - ")) return;
            String[] parts = label.split(" - ");
            if (!isCustomMode) {
                binding.tvStartDate.setText(parts[0]);
                binding.tvEndDate.setText(parts.length > 1 ? parts[1] : "");
            }
        });

        viewModel.totalExpense.observe(this, v -> {
            binding.tvTotalExpense.setText("¥" + String.format("%.2f", v));
            if(viewModel.pieType.getValue() == 0){
                binding.tvPieCenterLabel.setText("总支出");
                binding.tvPieCenterAmount.setText("¥" + String.format("%.2f", v));
            }
        });

        viewModel.totalIncome.observe(this, v -> {
            binding.tvTotalIncome.setText("¥" + String.format("%.2f", v));
            if(viewModel.pieType.getValue() == 1){
                binding.tvPieCenterLabel.setText("总收入");
                binding.tvPieCenterAmount.setText("¥" + String.format("%.2f", v));
            }
        });

        viewModel.totalBalance.observe(this, v -> {
            binding.tvTotalBalance.setText((v < 0 ? "-¥" : "¥") + String.format("%.2f", Math.abs(v)));
            binding.tvTotalBalance.setTextColor(v >= 0 ? 0xFF2F5FFF : 0xFFFF4C5B);
        });

        viewModel.barEntries.observe(this, entries -> {
            boolean empty = (entries == null || entries.isEmpty());
            binding.barChart.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.barChartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty) binding.barChart.setData(entries);
        });

        viewModel.lineEntries.observe(this, entries -> {
            boolean empty = (entries == null || entries.isEmpty());
            binding.lineChart.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.lineChartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty) binding.lineChart.setData(entries);
        });

        viewModel.pieEntries.observe(this, entries -> {
            boolean empty = (entries == null || entries.isEmpty());
            binding.pieChart.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.pieChartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.tvPieCenterLabel.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.tvPieCenterAmount.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!empty) binding.pieChart.setData(entries);
        });

        viewModel.pieType.observe(this, type -> {
            if(type == 0){
                binding.tvPieCenterLabel.setText("总支出");
                binding.tvPieCenterAmount.setText("¥" + String.format("%.2f", viewModel.totalExpense.getValue()!=null?viewModel.totalExpense.getValue():0f));
            }else{
                binding.tvPieCenterLabel.setText("总收入");
                binding.tvPieCenterAmount.setText("¥" + String.format("%.2f", viewModel.totalIncome.getValue()!=null?viewModel.totalIncome.getValue():0f));
            }
        });

        viewModel.categoryItems.observe(this, items -> {
            if (items != null) categoryAdapter.setItems(items);
        });
    }

}