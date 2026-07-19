package com.example.my_project1.ui.activity;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityAccountDetailBinding;
import com.example.my_project1.ui.adapter.ChartLegendAdapter;
import com.example.my_project1.ui.adapter.bill.AccountBillAdapter;
import com.example.my_project1.ui.fragment.AccountMoreBottomSheetFragment;
import com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment;
import com.example.my_project1.ui.fragment.BillChooseAccountFragment;
import com.example.my_project1.ui.fragment.BottomSheetAccountEditFragment;
import com.example.my_project1.ui.fragment.DateRangePickerFragment;
import com.example.my_project1.ui.fragment.DeleteAccountDialogFragment;
import com.example.my_project1.ui.fragment.VerificationCodeDialog;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    public static final String EXTRA_ACCOUNT_LOCAL_ID = "account_local_id";

    // ViewBinding
    private ActivityAccountDetailBinding binding;

    // ViewModels
    private AccountViewModel accountViewModel;
    private BillViewModel billViewModel;

    // Adapter
    private AccountBillAdapter billAdapter;

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

    // 🔴 删除对话框
    private DeleteAccountDialogFragment deleteDialog;

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
        
        // 设置状态栏图标为深色
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(true);
        }


        // 初始化ViewModel
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);

        // 获取账户ID
        String accountId = getIntent().getStringExtra(EXTRA_ACCOUNT_ID);
        long localId = getIntent().getLongExtra(EXTRA_ACCOUNT_LOCAL_ID, -1);

        if ((accountId == null || accountId.isEmpty()) && localId == -1) {
            SnackbarUtils.showError(binding.getRoot(), "账户ID为空");
            finish();
            return;
        }

        // 加载账户数据
        observeAccountData(accountId, localId);

        // 设置RecyclerView
        setupRecyclerView();

        // 设置监听器
        setupListeners();

        // 观察数据变化
        observeData(accountId, localId);

        // 🔑 观察 ViewModel 状态
        observeViewModelStates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ==================== 初始化 ====================

    private void observeAccountData(String accountId, long localId) {
        LiveData<Account> accountLiveData;
        if (accountId != null && !accountId.isEmpty()) {
            accountLiveData = accountViewModel.getAccountById(accountId);
        } else {
            accountLiveData = accountViewModel.getAccountByLocalId(localId);
        }

        accountLiveData.observe(this, account -> {
            if (account != null) {
                currentAccount = account;
                updateAccountInfo(account);
                
                if (!filteredBills.isEmpty() && billAdapter != null) {
                    billAdapter.setData(filteredBills, currentAccount.getBalance(), currentAccount.isCredit());
                }
            } else {
                if (currentAccount == null) {
                    SnackbarUtils.showError(binding.getRoot(), "账户不存在");
                    finish();
                }
            }
        });
    }

    private void setupRecyclerView() {
        billAdapter = new AccountBillAdapter(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvTransactions.setLayoutManager(layoutManager);
        binding.rvTransactions.setAdapter(billAdapter);
        binding.rvTransactions.setNestedScrollingEnabled(false);

        // 点击账单跳转到详情页
        billAdapter.setOnBillClickListener(new AccountBillAdapter.OnBillClickListener() {
            @Override
            public void onBillClick(Bill bill) {
                Intent intent = new Intent(AccountDetailActivity.this, BillDetailActivity.class);
                if (bill.getId() < 0) {
                    intent.putExtra("bill_object", bill);
                } else {
                    if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
                        intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, bill.getObjectId());
                    } else {
                        intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, bill.getId());
                    }
                }
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }

            @Override
            public void onBillDelete(Bill bill) {
                handleBillDelete(bill);
            }

            @Override
            public void onBillRefund(Bill bill) {
                // TODO: 实现退款逻辑
                SnackbarUtils.showInfo(binding.getRoot(), "已触发退款申请");
            }

            @Override
            public void onBillEdit(Bill bill) {
                Intent intent = new Intent(AccountDetailActivity.this, com.example.my_project1.ui.activity.AddBillActivity.class);
                intent.putExtra("editBill", bill);
                startActivity(intent);
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnMore.setOnClickListener(v -> showMoreOptions());

        binding.btnEditBalance.setOnClickListener(v -> showBalanceAdjustmentBottomSheet());
        
        binding.btnAddTransaction.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBillActivity.class);
            intent.putExtra("account_id", currentAccount.getObjectId());
            intent.putExtra("account_name", currentAccount.getName());
            startActivity(intent);
        });

        binding.btnRepayAction.setOnClickListener(v -> startRepaymentFlow());
        binding.btnRepayNow.setOnClickListener(v -> startRepaymentFlow());

        // 图表切换按钮
        binding.ivToggleChart.setOnClickListener(v -> {
            showingExpense = !showingExpense;
            updateChart();
        });

        // 日期筛选按钮 (改为点击标题或者添加一个按钮)
        binding.tvToolbarTitle.setOnClickListener(v -> showDateRangePicker());
    }

    private void showMoreOptions() {
        if (currentAccount == null) return;
        AccountMoreBottomSheetFragment bottomSheet = AccountMoreBottomSheetFragment.newInstance(currentAccount);
        bottomSheet.setOnOptionClickListener(new AccountMoreBottomSheetFragment.OnOptionClickListener() {
            @Override
            public void onEdit() {
                // 直接跳转到编辑界面，不再弹出中间详情页
                if (currentAccount != null) {
                    Intent intent = new Intent(AccountDetailActivity.this, AddAccountActivity.class);
                    intent.putExtra("editAccount", currentAccount);
                    startActivity(intent);
                }
            }

            @Override
            public void onHide() {
                if (currentAccount != null) {
                    currentAccount.setIncludeInTotal(false);
                    accountViewModel.updateAccount(currentAccount);
                    // 🔕 移除提示
                }
            }

            @Override
            public void onArchive() {
                if (currentAccount != null) {
                    currentAccount.setCanBeSelected(false);
                    accountViewModel.updateAccount(currentAccount);
                    SnackbarUtils.showInfo(binding.getRoot(), "已归档账户（不可再记账）");
                }
            }

            @Override
            public void onAccountInfo() {
                // 点击账户信息：只弹出信息界面
                showAccountDetailBottomSheet(currentAccount);
            }

            @Override
            public void onConsumerInstallment() {
                // Implement consumer installment logic
            }

            @Override
            public void onBillInstallment() {
                // Implement bill installment logic
            }

            @Override
            public void onDelete() {
                showVerificationBeforeDelete();
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "AccountMore");
    }

    private void showBalanceAdjustmentBottomSheet() {
        if (currentAccount == null) return;
        BalanceAdjustmentBottomSheetFragment fragment = BalanceAdjustmentBottomSheetFragment.newInstance(currentAccount);
        fragment.setOnBalanceAdjustedListener((newBalance, recordAsTransaction) -> {
            if (recordAsTransaction) {
                double diff = newBalance - currentAccount.getBalance();
                if (Math.abs(diff) > 0.001) {
                    Bill adjustmentBill = new Bill();
                    adjustmentBill.setAmount(Math.abs(diff));
                    adjustmentBill.setType(diff > 0 ? 1 : 0);
                    adjustmentBill.setCategoryName("余额调整");
                    adjustmentBill.setBillTime(new Date());
                    adjustmentBill.setAccountId(currentAccount.getObjectId());
                    adjustmentBill.setLocalAccountId(currentAccount.getId());
                    adjustmentBill.setUserId(currentAccount.getUserId());
                    adjustmentBill.setCategoryName("余额调整");
                    adjustmentBill.setCategoryIconUrl(currentAccount.getIconUrl()); // 使用账户图标
                    adjustmentBill.setRemark("手动调整余额");
                    
                    // 🔑 立即更新本地 UI，提供即时反馈
                    currentAccount.setBalance(newBalance);
                    animateBalanceUpdate(newBalance);
                    
                    // 插入账单，Repository 会自动处理账户余额的增减
                    billViewModel.insertBill(adjustmentBill);
                }
            } else {
                // 如果不记为交易，直接更新账户余额
                currentAccount.setBalance(newBalance);
                accountViewModel.updateAccount(currentAccount);
                animateBalanceUpdate(newBalance);
            }
        });
        fragment.show(getSupportFragmentManager(), "BalanceAdjustment");
    }

    /**
     * 优雅地更新余额 UI（带淡入淡出动画）
     */
    private void animateBalanceUpdate(double newBalance) {
        binding.tvBalanceAmount.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    // 这里由于 currentAccount 已经在回调里设了新值，所以直接调 updateAccountInfo 即可
                    updateAccountInfo(currentAccount);
                    binding.tvBalanceAmount.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start();
                })
                .start();
        
        // 如果是信用账户，还涉及到还款进度等卡片刷新
        if (currentAccount.isCredit()) {
            binding.cardCreditBill.animate().alpha(0.5f).setDuration(200).withEndAction(() -> {
                binding.cardCreditBill.animate().alpha(1f).setDuration(300).start();
            }).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void showVerificationBeforeDelete() {
        if (allBills == null || allBills.isEmpty() || (allBills.size() == 1 && "账户创建".equals(allBills.get(0).getCategoryName()))) {
            // 如果没有账单（或者只有一条初始化的“账户创建”虚拟账单），直接弹出删除确认，跳过验证码
            showDeleteDialog();
            return;
        }

        VerificationCodeDialog dialog = VerificationCodeDialog.newInstance();
        dialog.setOnVerificationListener(new VerificationCodeDialog.OnVerificationListener() {
            @Override
            public void onVerified() {
                showDeleteDialog();
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "删除验证取消");
            }
        });
        dialog.show(getSupportFragmentManager(), "VerificationDialog");
    }

    private void startRepaymentFlow() {
        if (currentAccount == null) return;
        // 跳转到转账/还款页面，或者打开还款对话框
        Intent intent = new Intent(this, AddBillActivity.class);
        intent.putExtra("account_id", currentAccount.getObjectId());
        intent.putExtra("account_name", currentAccount.getName());
        intent.putExtra("bill_type", 3); // 假设 3 是转账/还款
        intent.putExtra("is_repayment", true);
        startActivity(intent);
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

    private void observeData(String accountId, long localId) {
        billViewModel.getBillsByAccount(accountId, localId).observe(this, bills -> {
            // 先处理数据
            if (bills != null && !bills.isEmpty()) {
                allBills = new ArrayList<>(bills);
            } else {
                allBills = new ArrayList<>();
            }

            // 始终添加/更新系统级账单 (账户创建等)
            addSystemBills(allBills);
            filteredBills = new ArrayList<>(allBills);

            // 无论数据是否为空，都尝试刷新 UI (addSystemBills 保证了 allBills 不为空)
            if (!allBills.isEmpty()) {
                binding.cardTransactions.setVisibility(View.VISIBLE);
                binding.rvTransactions.setVisibility(View.VISIBLE);
                binding.cardOverview.setVisibility(View.VISIBLE);

                calculateStatistics(filteredBills);
                updateStatisticsUI();

                // 🔑 核心修复：只有在 currentAccount 已经加载的情况下才调用 setData
                // 如果还没加载，loadAccountData 结束后会补刷。
                if (currentAccount != null) {
                    billAdapter.setData(filteredBills, currentAccount.getBalance(), currentAccount.isCredit());
                }

                updateChart();
            } else {
                // 这种情况理论上由于 addSystemBills 不会发生，除非 account 也没加载
                binding.cardTransactions.setVisibility(View.GONE);
                binding.cardOverview.setVisibility(View.GONE);
            }
        });
    }

    private void addSystemBills(List<Bill> bills) {
        if (currentAccount == null) return;

        // 1. 查找是否存在"账户创建"
        boolean hasCreation = false;
        for (Bill b : bills) {
            if ("账户创建".equals(b.getCategoryName())) {
                hasCreation = true;
                break;
            }
        }

        if (!hasCreation) {
            // 计算初始金额：当前余额 - 所有账单影响
            double runningImpact = 0;
            for (Bill b : bills) {
                runningImpact += (b.getType() == 1 ? b.getAmount() : -b.getAmount());
            }
            double initialBalance = currentAccount.getBalance() - runningImpact;

            Bill creationBill = new Bill();
            creationBill.setId(-999); // 虚拟ID
            creationBill.setCategoryName("账户创建");
            creationBill.setCategoryIconUrl(currentAccount.getIconUrl()); // 使用账户图标
            creationBill.setBillTime(currentAccount.getCreatedAt() != null ? currentAccount.getCreatedAt() : new Date());
            creationBill.setAmount(Math.abs(initialBalance));
            creationBill.setType(initialBalance >= 0 ? 1 : 0);
            creationBill.setRemark("账户初始创建，余额为 ¥" + formatMoney(initialBalance));
            creationBill.setAccountId(currentAccount.getObjectId());

            bills.add(creationBill);
        }

        // 排序确保时间正确 (倒序)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bills.sort((b1, b2) -> b2.getBillTime().compareTo(b1.getBillTime()));
        }
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
        binding.tvAccountName.setText(account.getName());
        binding.tvToolbarTitle.setText("账户详情");

        boolean isCreditAccount = account.isCredit();

        if (isCreditAccount) {
            binding.tvBalanceLabel.setText("当前欠款 (CNY)");
            // 信用账户通常余额存为负数，UI显示正数（欠款额）
            animateNumber(binding.tvBalanceAmount, Math.abs(account.getBalance()));

            binding.btnRepayAction.setVisibility(View.VISIBLE);
            binding.layoutCreditInfo.setVisibility(View.VISIBLE);
            binding.cardCreditBill.setVisibility(View.VISIBLE);

            double creditLimit = account.getCreditLimit();
            double balance = account.getBalance(); // 负数
            double usedAmount = Math.abs(balance);
            double availableCredit = creditLimit - usedAmount;
            
            binding.tvAvailableLimit.setText(String.format("可用额度 ¥%s", formatMoney(Math.max(0, availableCredit))));

            updateCreditBillingInfo(account);
            
            // 账单卡片信息
            binding.tvBillAmount.setText(String.format("¥%s", formatMoney(usedAmount)));
            
        } else {
            binding.tvBalanceLabel.setText("账户余额 (CNY)");
            animateNumber(binding.tvBalanceAmount, account.getBalance());
            
            binding.btnRepayAction.setVisibility(View.GONE);
            binding.layoutCreditInfo.setVisibility(View.GONE);
            binding.cardCreditBill.setVisibility(View.GONE);
        }

        if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
            ImageLoaderUtils.loadThumbnail(this, account.getIconUrl(), binding.ivAccountIcon);
        } else {
            binding.ivAccountIcon.setImageResource(R.drawable.ic_wallet);
        }
    }

    private void updateCreditBillingInfo(Account account) {
        int billingDay = account.getBillingDay();
        int repaymentDay = account.getRepaymentDay();
        if (billingDay <= 0) {
            binding.tvBillingStatus.setText("出账日 未设置");
            return;
        }

        Calendar now = Calendar.getInstance();
        int currentDay = now.get(Calendar.DAY_OF_MONTH);
        
        Calendar billingDate = (Calendar) now.clone();
        billingDate.set(Calendar.DAY_OF_MONTH, billingDay);
        
        if (currentDay > billingDay) {
            billingDate.add(Calendar.MONTH, 1);
        }
        
        long diffMillis = billingDate.getTimeInMillis() - now.getTimeInMillis();
        long daysUntilBilling = TimeUnit.MILLISECONDS.toDays(diffMillis);
        
        if (daysUntilBilling == 0) {
            binding.tvBillingStatus.setText("今日出账");
        } else {
            binding.tvBillingStatus.setText(String.format("%d天后出账", daysUntilBilling));
        }
        
        if (repaymentDay > 0) {
            binding.tvRepaymentStatus.setText(String.format("还款日 每月%d日", repaymentDay));
        } else {
            binding.tvRepaymentStatus.setText("还款日 未设置");
        }

        // 计算账单周期 (假设账单日是周期的结束)
        Calendar periodStart = (Calendar) billingDate.clone();
        periodStart.add(Calendar.MONTH, -1);
        periodStart.add(Calendar.DAY_OF_MONTH, 1);
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM月dd日", Locale.getDefault());
        String periodStr = sdf.format(periodStart.getTime()) + " - " + sdf.format(billingDate.getTime());
        
        // 更新账单月份标签
        int billMonth = billingDate.get(Calendar.MONTH) + 1;
        int billYear = billingDate.get(Calendar.YEAR);
        binding.tvBillMonthLabel.setText(String.format("%d年%02d月账单 (未出账)", billYear, billMonth));
    }

    private void updateStatisticsUI() {
        // 由于现在使用月度折叠列表，全局统计可以仅记录 Log 或者更新其他通用 UI
        
        // 如果有特定的全局统计 View 可以这里更新
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
                Color.parseColor("#F47670"), // 珊瑚红
                Color.parseColor("#FBA24F"), // 橙色
                Color.parseColor("#FFD05B"), // 黄色
                Color.parseColor("#4DBBDD"), // 青蓝色
                Color.parseColor("#6B76F1"), // 蓝紫色
                Color.parseColor("#BC76F4"), // 紫罗兰
                Color.parseColor("#F48FB1"), // 粉色
                Color.parseColor("#A1E59C")  // 浅绿色
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
                    String.format(Locale.getDefault(), "%.1f%%", percentage),
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

    private void handleBillDelete(Bill bill) {
        if (bill == null || bill.getId() < 0) return; // 排除系统虚拟账单
        
        billViewModel.deleteBill(bill);
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

        if (currentAccount != null) {
            billAdapter.setData(filteredBills, currentAccount.getBalance(), currentAccount.isCredit());
        }
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
            public void onDeleteAll() {
                deleteAccountAndBills();
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
            Log.e(TAG, "❌ 迁移中止：目标账户或当前账户为空");
            SnackbarUtils.showError(binding.getRoot(), "目标账户或当前账户为空");
            return;
        }

        Log.d(TAG, "🔄 开始迁移账单: " + currentAccount.getName() +
                " -> " + targetAccount.getName());

        //  设置标记，等待迁移完成
        isWaitingForMigration = true;

        billViewModel.migrateBillsToAccount(
                currentAccount.getObjectId(),
                currentAccount.getId(),
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
                SnackbarUtils.showSuccess(binding.getRoot(), "删除成功");
                Log.d(TAG, "✅ 账户删除成功");
                binding.getRoot().postDelayed(this::finish, 500);
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
            }
        });
    }

    /**
     * 🔑 不迁移账单，将账单设置为无账户（遵循BillViewModel风格 - 无回调）
     */
    private void deleteAccountWithoutMigration() {
        if (currentAccount == null) {
            Log.e(TAG, "❌ 删除中止：当前账户为空");
            SnackbarUtils.showError(binding.getRoot(), "当前账户为空");
            return;
        }

        Log.d(TAG, "🔄 开始设置账单为无账户: " + currentAccount.getName());

        // 🔑 设置标记，等待设置完成
        isWaitingForSetNoAccount = true;

        // 🔑 调用 ViewModel 方法 (传入 objectId 和 localId)
        billViewModel.setBillsToNoAccount(currentAccount.getObjectId(), currentAccount.getId());
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
                SnackbarUtils.showSuccess(binding.getRoot(), "删除成功");
                Log.d(TAG, "✅ 账户删除成功");
                binding.getRoot().postDelayed(this::finish, 500);
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
            }
        });
    }

    /**
     * 🔑 删除账户及所有账单
     */
    private void deleteAccountAndBills() {
        if (currentAccount == null) return;
        Log.d(TAG, "🗑️ 开始删除账户及所有账单: " + currentAccount.getName());
        billViewModel.deleteAllBillsByAccount(currentAccount.getObjectId(), currentAccount.getId());
        isWaitingForSetNoAccount = true; 
    }

    private void directDeleteAccount() {
        if (currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "当前账户为空");
            return;
        }
        Log.d(TAG, "🗑️ 直接删除账户: " + currentAccount.getName());
        accountViewModel.deleteAccount(currentAccount, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "删除成功");
                binding.getRoot().postDelayed(this::finish, 500);
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
            }
        });
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
            textView.setText(String.format(Locale.CHINA, "¥%,.2f", value));
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