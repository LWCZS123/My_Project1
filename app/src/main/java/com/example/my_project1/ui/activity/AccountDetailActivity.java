package com.example.my_project1.ui.activity;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityAccountDetailBinding;
import com.example.my_project1.ui.adapter.ChartLegendAdapter;
import com.example.my_project1.ui.adapter.bill.BillGroupedAdapter;
import com.example.my_project1.ui.fragment.BillChooseAccountFragment;
import com.example.my_project1.ui.fragment.BottomSheetAccountEditFragment;
import com.example.my_project1.ui.fragment.DateRangePickerFragment;
import com.example.my_project1.ui.fragment.DeleteAccountDialogFragment;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AccountDetailActivity - 账户详情页（优化版）
 * ----------------------------------------------------------------
 * 功能：
 * 1. 展示账户信息和余额
 * 2. 展示账户下的账单列表
 * 3. 支持账单分类统计（饼图）
 * 4. 支持日期筛选
 * 5. 支持删除账户（含账单迁移功能）
 *
 * 🔑 优化：
 * - 使用 SnackbarUtils 替代 Toast
 * - 遵循 BillViewModel 的回调风格（无回调参数）
 * - 观察 ViewModel 的状态变化
 */
public class AccountDetailActivity extends AppCompatActivity {

    private static final String TAG = "AccountDetailActivity";
    public static final String EXTRA_ACCOUNT_ID = "account_id";

    // ViewBinding
    private ActivityAccountDetailBinding binding;

    // ViewModels
    private AccountViewModel accountViewModel;
    private BillViewModel billViewModel;

    // Adapter
    private BillGroupedAdapter billAdapter;

    // 数据
    private Account currentAccount;
    private List<Bill> allBills = new ArrayList<>();
    private List<Bill> filteredBills = new ArrayList<>();

    // 图表切换状态：true=支出，false=收入
    private boolean showingExpense = true;

    // 统计数据
    private double totalIncome = 0.0;
    private double totalExpense = 0.0;
    private double transferIn = 0.0;
    private double transferOut = 0.0;

    // 日期筛选
    private Long startTimestamp = null;
    private Long endTimestamp = null;

    // 🔴 删除对话框和选中的目标账户
    private DeleteAccountDialogFragment deleteDialog;
    private Account selectedTargetAccount = null;

    // 🔴 标记删除流程状态
    private boolean isWaitingForMigration = false;
    private boolean isWaitingForSetNoAccount = false;

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        binding = ActivityAccountDetailBinding.inflate(getLayoutInflater());
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


        // 初始化ViewModel
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);

        // 获取账户ID
        String accountId = getIntent().getStringExtra(EXTRA_ACCOUNT_ID);
        if (accountId == null || accountId.isEmpty()) {
            SnackbarUtils.showError(binding.getRoot(), "账户ID为空");
            finish();
            return;
        }

        // 加载账户数据
        loadAccountData(accountId);

        // 设置RecyclerView
        setupRecyclerView();

        // 设置监听器
        setupListeners();

        // 观察数据变化
        observeData(accountId);

        // 🔑 观察 ViewModel 状态
        observeViewModelStates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ==================== 初始化 ====================

    private void loadAccountData(String accountId) {
        AppExecutors.get().diskIO().execute(() -> {
            Account account = accountViewModel.getAccountByIdSync(accountId);

            AppExecutors.get().mainThread().execute(() -> {
                if (account != null) {
                    currentAccount = account;
                    updateAccountInfo(account);
                } else {
                    SnackbarUtils.showError(binding.getRoot(), "账户不存在");
                    finish();
                }
            });
        });
    }

    private void setupRecyclerView() {
        billAdapter = new BillGroupedAdapter(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvTransactions.setLayoutManager(layoutManager);
        binding.rvTransactions.setAdapter(billAdapter);
        binding.rvTransactions.setNestedScrollingEnabled(false);

        // 点击账单跳转到详情页
        billAdapter.setOnBillClickListener(bill -> {
            Intent intent = new Intent(AccountDetailActivity.this, BillDetailActivity.class);
            if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
                intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.getObjectId());
            } else {
                intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, bill.getId());
            }
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void setupListeners() {
        //删除按钮 - 显示删除对话框
        binding.btnDelete.setOnClickListener(v -> showDeleteDialog());

        binding.btnEdit.setOnClickListener(v -> showAccountDetailBottomSheet(currentAccount));

        // 图表切换按钮
        binding.ivToggleChart.setOnClickListener(v -> {
            showingExpense = !showingExpense;
            updateChart();
        });

        // 添加交易按钮
        binding.fabAddTransaction.setOnClickListener(v -> {
            SnackbarUtils.showInfo(binding.getRoot(), "添加交易");
        });

        // 日期筛选按钮
        binding.ivFilter.setOnClickListener(v -> showDateRangePicker());
    }

    private void showAccountDetailBottomSheet(Account currentAccount) {
        if (currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "账户信息不存在");
            return;
        }

        BottomSheetAccountEditFragment bottomSheet =
                BottomSheetAccountEditFragment.newInstance(currentAccount);

        bottomSheet.show(getSupportFragmentManager(), "AccountDetail");
    }

    // ==================== 数据观察 ====================

    private void observeData(String accountId) {
        billViewModel.getBillsByAccount(accountId).observe(this, bills -> {
            if (bills != null) {
                Log.d(TAG, "📊 收到账单数据: " + bills.size() + " 条");
                allBills = bills;
                filteredBills = new ArrayList<>(bills);

                calculateStatistics(filteredBills);
                updateStatisticsUI();

                billAdapter.setBills(filteredBills);

                updateChart();
            } else {
                Log.d(TAG, "⚠️ 账单数据为空");
                allBills = new ArrayList<>();
                filteredBills = new ArrayList<>();
                billAdapter.setBills(filteredBills);
            }
        });
    }


    private void observeViewModelStates() {
        // 观察 BillViewModel 的操作状态
        billViewModel.operationState.observe(this, response -> {
            if (response.isLoading()) {
                Log.d(TAG, "🔄 " + response.message);
            } else if (response.isSuccess()) {
                Log.d(TAG, "✅ " + response.message);

                // 🔴 根据不同的操作阶段执行后续操作
                if (isWaitingForMigration) {
                    isWaitingForMigration = false;
                    // 迁移成功，删除账户
                    deleteAccountAfterMigration();
                } else if (isWaitingForSetNoAccount) {
                    isWaitingForSetNoAccount = false;
                    // 设置无账户成功，删除账户
                    deleteAccountAfterSetNoAccount();
                }
            } else if (response.isError()) {
                Log.e(TAG, "❌ " + response.message);
                SnackbarUtils.showError(binding.getRoot(), response.message);
                // 失败时重置状态
                isWaitingForMigration = false;
                isWaitingForSetNoAccount = false;
            }
        });

        // 观察 BillViewModel 的 Toast 消息
        billViewModel.toastMessage.observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                // 根据消息内容判断类型
                if (message.contains("成功")) {
                    SnackbarUtils.showSuccess(binding.getRoot(), message);
                } else if (message.contains("失败") || message.contains("错误")) {
                    SnackbarUtils.showError(binding.getRoot(), message);
                } else {
                    SnackbarUtils.showInfo(binding.getRoot(), message);
                }
            }
        });
    }

    // ==================== UI更新 ====================

    private void updateAccountInfo(Account account) {
        binding.tvTitle.setText(account.getName());

        boolean isCreditAccount = isCreditAccount(account);

        if (isCreditAccount) {
            binding.tvBalanceLabel.setText("你的可用余额");
            animateNumber(binding.tvBalanceAmount, account.getBalance());

            double creditLimit = account.getCreditLimit();
            binding.tvTotalTransferIn.setText("$" + formatMoney(creditLimit));

            double availableCredit = creditLimit - Math.abs(account.getBalance());
            if(availableCredit < 0){
                binding.tvTotalTransferOut.setText("$" + formatMoney(0));
            }else{
                binding.tvTotalTransferOut.setText("$" + formatMoney(availableCredit));
            }

            Log.d(TAG, String.format("💳 信贷账户: 信用额度=%.2f, 已用=%.2f, 可用=%.2f",
                    creditLimit, Math.abs(account.getBalance()), availableCredit));

        } else {
            binding.tvBalanceLabel.setText("你的可用余额");
            animateNumber(binding.tvBalanceAmount, account.getBalance());

            binding.tvTotalTransferIn.setText("$0");
            binding.tvTotalTransferOut.setText("$0");
        }

        if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
            binding.ivWalletIcon.setVisibility(View.VISIBLE);
            ImageLoaderUtils.load(this, account.getIconUrl(), binding.ivWalletIcon);
        }
    }

    private void updateStatisticsUI() {
        DecimalFormat df = new DecimalFormat("#,##0.00");

        binding.tvTotalIncome.setText("$" + df.format(totalIncome));
        binding.tvTotalExpense.setText("$" + df.format(totalExpense));

        if (currentAccount != null && !isCreditAccount(currentAccount)) {
            binding.tvTotalTransferIn.setText("$" + df.format(transferIn));
            binding.tvTotalTransferOut.setText("$" + df.format(transferOut));
        }
    }

    // ==================== 统计计算 ====================

    private void calculateStatistics(List<Bill> bills) {
        totalIncome = 0.0;
        totalExpense = 0.0;
        transferIn = 0.0;
        transferOut = 0.0;

        for (Bill bill : bills) {
            double amount = bill.getAmount();

            switch (bill.getType()) {
                case 0: // 支出
                    totalExpense += amount;
                    break;
                case 1: // 收入
                    totalIncome += amount;
                    break;
                case 3: // 转账
                    break;
            }
        }

        Log.d(TAG, String.format("💰 统计: 收入=%.2f, 支出=%.2f, 转入=%.2f, 转出=%.2f",
                totalIncome, totalExpense, transferIn, transferOut));
    }

    // ==================== 图表相关 ====================

    private void updateChart() {
        if (filteredBills == null || filteredBills.isEmpty()) {
            binding.pieChart.clear();
            binding.pieChart.setNoDataText("暂无数据");
            binding.legendRecyclerView.setVisibility(View.GONE);
            return;
        }

        Map<String, Double> categoryMap = new HashMap<>();

        for (Bill bill : filteredBills) {
            if ((showingExpense && bill.getType() == 0) ||
                    (!showingExpense && bill.getType() == 1)) {

                String categoryName = bill.getCategoryName();
                if (categoryName == null || categoryName.isEmpty()) {
                    categoryName = "其他";
                }

                double amount = bill.getAmount();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    categoryMap.put(categoryName, categoryMap.getOrDefault(categoryName, 0.0) + amount);
                }
            }
        }

        if (categoryMap.isEmpty()) {
            binding.pieChart.clear();
            binding.pieChart.setNoDataText(showingExpense ? "暂无支出数据" : "暂无收入数据");
            binding.legendRecyclerView.setVisibility(View.GONE);
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryMap.entrySet()) {
            entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");

        int[] colors = {
                Color.parseColor("#FF6B9D"), Color.parseColor("#C44569"),
                Color.parseColor("#FD79A8"), Color.parseColor("#A29BFE"),
                Color.parseColor("#6C5CE7"), Color.parseColor("#74B9FF"),
                Color.parseColor("#0984E3"), Color.parseColor("#00B894"),
                Color.parseColor("#00CEC9"), Color.parseColor("#FDCB6E"),
                Color.parseColor("#E17055"), Color.parseColor("#D63031"),
                Color.parseColor("#E84393"), Color.parseColor("#2D3436"),
        };
        dataSet.setColors(colors);

        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);

        setupPieChartEnhanced(binding.pieChart);

        binding.pieChart.setData(data);
        binding.pieChart.invalidate();

        binding.pieChart.animateY(1200, Easing.EaseInOutCubic);

        setupScrollableLegend(entries, colors);
    }

    private void setupPieChartEnhanced(PieChart chart) {
        chart.setUsePercentValues(false);
        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(5, 5, 5, 5);

        chart.setDragDecelerationFrictionCoef(0.95f);
        chart.setRotationEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setRotationAngle(0);

        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(58f);
        chart.setTransparentCircleRadius(61f);
        chart.setTransparentCircleColor(Color.WHITE);
        chart.setTransparentCircleAlpha(50);

        chart.setDrawCenterText(true);
        chart.setCenterText(showingExpense ? "总支出" : "总收入");
        chart.setCenterTextSize(14f);
        chart.setCenterTextColor(Color.parseColor("#2D3436"));

        chart.setDrawEntryLabels(false);

        Legend legend = chart.getLegend();
        legend.setEnabled(false);
    }

    private void setupScrollableLegend(List<PieEntry> entries, int[] colors) {
        binding.legendRecyclerView.setVisibility(View.VISIBLE);

        ChartLegendAdapter legendAdapter = new ChartLegendAdapter(this);

        List<ChartLegendAdapter.LegendItem> legendItems = new ArrayList<>();
        float total = 0;
        for (PieEntry entry : entries) {
            total += entry.getValue();
        }

        for (int i = 0; i < entries.size(); i++) {
            PieEntry entry = entries.get(i);
            float percentage = (entry.getValue() / total) * 100;

            ChartLegendAdapter.LegendItem item = new ChartLegendAdapter.LegendItem(
                    entry.getLabel(),
                    String.format("%.1f%%", percentage),
                    "$" + formatMoney(entry.getValue()),
                    colors[i % colors.length]
            );
            legendItems.add(item);
        }

        legendAdapter.setLegendItems(legendItems);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.legendRecyclerView.setLayoutManager(layoutManager);
        binding.legendRecyclerView.setAdapter(legendAdapter);
    }

    // ==================== 日期筛选 ====================

    private void showDateRangePicker() {
        DateRangePickerFragment picker = new DateRangePickerFragment();
        picker.setOnDateRangeSelectedListener((startTimestamp, endTimestamp, formattedStartDate, formattedEndDate) -> {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;

            filterBillsByDate();
            updateStatisticsUI();
            updateChart();

            String dateRange = formattedStartDate + " 至 " + formattedEndDate;
            SnackbarUtils.showSuccess(binding.getRoot(), "已筛选: " + dateRange);
        });
        picker.show(getSupportFragmentManager(), "DateRangePicker");
    }

    private void filterBillsByDate() {
        filteredBills = new ArrayList<>();

        Date startDate = startTimestamp != null ? new Date(startTimestamp) : null;
        Date endDate   = endTimestamp != null ? new Date(endTimestamp) : null;

        for (Bill bill : allBills) {
            Date billTime = bill.getBillTime();

            boolean inRange = true;

            if (startDate != null && billTime.before(startDate)) {
                inRange = false;
            }

            if (endDate != null && billTime.after(endDate)) {
                inRange = false;
            }

            if (inRange) {
                filteredBills.add(bill);
            }
        }

        billAdapter.setBills(filteredBills);
        calculateStatistics(filteredBills);
    }

    // ==================== 🔴 删除账户相关（遵循BillViewModel风格）====================

    /**
     * 显示删除账户对话框
     */
    private void showDeleteDialog() {
        if (currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "账户信息不存在");
            return;
        }

        boolean hasBills = allBills != null && !allBills.isEmpty();
        int billCount = hasBills ? allBills.size() : 0;

        Log.d(TAG, "🗑️ 准备删除账户: " + currentAccount.getName() +
                ", 有账单: " + hasBills + ", 账单数: " + billCount);

        deleteDialog = DeleteAccountDialogFragment.newInstance(
                currentAccount.getName(),
                currentAccount.getIconUrl(),
                hasBills,
                billCount
        );

        deleteDialog.setOnDeleteActionListener(new DeleteAccountDialogFragment.OnDeleteActionListener() {
            @Override
            public void onChooseTargetAccount() {
                showChooseAccountDialog();
            }

            @Override
            public void onMigrateAndDelete(Account targetAccount) {
                migrateBillsAndDeleteAccount(targetAccount);
            }

            @Override
            public void onDeleteWithoutMigration() {
                deleteAccountWithoutMigration();
            }

            @Override
            public void onDirectDelete() {
                directDeleteAccount();
            }
        });

        deleteDialog.show(getSupportFragmentManager(), "DeleteAccountDialog");
    }

    /**
     * 显示账户选择对话框
     */
    private void showChooseAccountDialog() {
        // 🔴 使用排除模式创建对话框，隐藏当前要删除的账户
        BillChooseAccountFragment chooseFragment =
                BillChooseAccountFragment.newInstance(currentAccount.getObjectId());

        chooseFragment.setOnAccountChooseListener((account, iconUrl, accountName) -> {
            if (account != null) {
                selectedTargetAccount = account;

                if (deleteDialog != null) {
                    deleteDialog.setSelectedTargetAccount(account);
                }

                Log.d(TAG, "✅ 选择目标账户: " + accountName);
            }
        });
        chooseFragment.show(getSupportFragmentManager(), "ChooseAccountDialog");
    }

    /**
     * 迁移账单并删除账户
     */
    private void migrateBillsAndDeleteAccount(Account targetAccount) {
        if (targetAccount == null || currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "目标账户或当前账户为空");
            return;
        }

        Log.d(TAG, "🔄 开始迁移账单: " + currentAccount.getName() +
                " -> " + targetAccount.getName());

        //  设置标记，等待迁移完成
        isWaitingForMigration = true;


        billViewModel.migrateBillsToAccount(
                currentAccount.getObjectId(),
                targetAccount.getObjectId()
        );


    }

    /**
     * 🔑 迁移成功后删除账户
     */
    private void deleteAccountAfterMigration() {
        if (currentAccount == null) {
            return;
        }

        Log.d(TAG, "🔄 账单迁移成功，开始删除账户");

        accountViewModel.deleteAccount(currentAccount, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "账单已迁移，账户已删除");
                Log.d(TAG, "✅ 账户删除成功");

                // 延迟关闭，让用户看到成功提示
                binding.getRoot().postDelayed(this::finish, 1000);
            } else {
                SnackbarUtils.showError(binding.getRoot(), "账户删除失败: " + message);
                Log.e(TAG, "❌ 账户删除失败: " + message);
            }
        });
    }

    /**
     * 🔑 不迁移账单，将账单设置为无账户（遵循BillViewModel风格 - 无回调）
     */
    private void deleteAccountWithoutMigration() {
        if (currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "当前账户为空");
            return;
        }

        Log.d(TAG, "🔄 开始设置账单为无账户: " + currentAccount.getName());

        // 🔑 设置标记，等待设置完成
        isWaitingForSetNoAccount = true;

        // 🔑 调用 ViewModel 方法（无回调）
        billViewModel.setBillsToNoAccount(currentAccount.getObjectId());

        // 🔑 ViewModel 会通过 operationState 和 toastMessage 通知结果
    }

    /**
     * 🔑 设置无账户成功后删除账户
     */
    private void deleteAccountAfterSetNoAccount() {
        if (currentAccount == null) {
            return;
        }

        Log.d(TAG, "🔄 账单已设置为无账户，开始删除账户");

        accountViewModel.deleteAccount(currentAccount, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "账单已设置为无账户，账户已删除");
                Log.d(TAG, "✅ 账户删除成功");

                // 延迟关闭，让用户看到成功提示
                binding.getRoot().postDelayed(this::finish, 1000);
            } else {
                SnackbarUtils.showError(binding.getRoot(), "账户删除失败: " + message);
                Log.e(TAG, "❌ 账户删除失败: " + message);
            }
        });
    }

    /**
     * 直接删除账户（无账单的情况）
     */
    private void directDeleteAccount() {

        if (currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "当前账户为空");
            return;
        }

        Log.d(TAG, "🗑️ 直接删除账户: " + currentAccount.getName());

        accountViewModel.deleteAccount(currentAccount, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "账户已删除");
                Log.d(TAG, "✅ 账户删除成功");

                // 延迟关闭，让用户看到成功提示
                binding.getRoot().postDelayed(this::finish, 1000);
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
                Log.e(TAG, "❌ 删除失败: " + message);
            }
        });
    }

    // ==================== 工具方法 ====================

    private boolean isCreditAccount(Account account) {
        return account.getBalance() < 0 && account.getCreditLimit() > 0;
    }

    private String formatMoney(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(amount);
    }

    private void animateNumber(android.widget.TextView textView, double targetValue) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) targetValue);
        animator.setDuration(800);
        animator.setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            textView.setText(String.format("$ %.2f", value));
        });
        animator.start();
    }

    @Override
    public void finish() {
        super.finish();
        // 左进右出的动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

}