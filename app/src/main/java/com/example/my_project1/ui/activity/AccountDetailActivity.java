package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ActivityAccountDetailBinding;
import com.example.my_project1.ui.adapter.bill.AccountBillPagingAdapter;
import com.example.my_project1.ui.adapter.bill.AccountHeaderAdapter;
import com.example.my_project1.ui.fragment.AccountMoreBottomSheetFragment;
import com.example.my_project1.ui.fragment.BalanceAdjustmentBottomSheetFragment;
import com.example.my_project1.ui.fragment.BillChooseAccountFragment;
import com.example.my_project1.ui.fragment.BottomSheetAccountEditFragment;
import com.example.my_project1.ui.fragment.DateRangePickerFragment;
import com.example.my_project1.ui.fragment.DeleteAccountDialogFragment;
import com.example.my_project1.ui.viewmodel.accountvm.AccountDetailViewModel;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.Date;

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
    private AccountDetailViewModel detailViewModel;

    // Adapters
    private AccountHeaderAdapter headerAdapter;
    private AccountBillPagingAdapter pagingAdapter;

    // 数据
    private Account currentAccount;
    
    // 图表切换状态
    private boolean showingExpense = true;

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
        insetsController.setAppearanceLightStatusBars(true);


        // 初始化ViewModel
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        billViewModel = new ViewModelProvider(this).get(BillViewModel.class);
        detailViewModel = new ViewModelProvider(this).get(AccountDetailViewModel.class);

        // 获取账户ID
        String accountId = getIntent().getStringExtra(EXTRA_ACCOUNT_ID);
        long localId = getIntent().getLongExtra(EXTRA_ACCOUNT_LOCAL_ID, -1);

        if ((accountId == null || accountId.isEmpty()) && localId == -1) {
            SnackbarUtils.showError(binding.getRoot(), "账户ID为空");
            finish();
            return;
        }

        detailViewModel.setAccount(accountId, localId);

        // 设置RecyclerView
        setupRecyclerView();

        // 设置监听器
        setupListeners();

        // 观察数据变化
        observeData();

        // 🔑 观察 ViewModel 状态
        observeViewModelStates();

        // 🚀 优化进入体验：显示 Lottie 动画，并行加载数据
        startEntryAnimation();
    }

    private void startEntryAnimation() {
        binding.loadingLayout.setVisibility(View.VISIBLE);
        binding.rvTransactions.setVisibility(View.INVISIBLE);
        
        // 播放动画至少 800ms，确保数据处理完成
        binding.lottieLoading.playAnimation();
        
        // 我们在 observeData 中处理显示逻辑
    }

    private void observeData() {
        detailViewModel.account.observe(this, account -> {
            if (account != null) {
                currentAccount = account;
                headerAdapter.setAccount(account);
            }
        });

        detailViewModel.stats.observe(this, stats -> {
            if (stats != null) {
                headerAdapter.setStats(stats);
            }
        });

        detailViewModel.expenseSummary.observe(this, summaries -> {
            if (showingExpense) {
                headerAdapter.setCategorySummaries(summaries, true);
            }
        });

        detailViewModel.incomeSummary.observe(this, summaries -> {
            if (!showingExpense) {
                headerAdapter.setCategorySummaries(summaries, false);
            }
        });

        detailViewModel.billPagingData.observe(this, pagingData -> {
            pagingAdapter.submitData(getLifecycle(), pagingData);
            
            // 数据加载后，延迟一点点关闭动画，确保渲染完成
            binding.getRoot().postDelayed(() -> {
                if (binding.loadingLayout.getVisibility() == View.VISIBLE) {
                    binding.loadingLayout.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(() -> {
                                binding.loadingLayout.setVisibility(View.GONE);
                                binding.rvTransactions.setVisibility(View.VISIBLE);
                                binding.rvTransactions.setAlpha(0f);
                                binding.rvTransactions.animate().alpha(1f).setDuration(200).start();
                            }).start();
                }
            }, 500);
        });
    }

    private void setupRecyclerView() {
        headerAdapter = new AccountHeaderAdapter(this);
        pagingAdapter = new AccountBillPagingAdapter(this);

        headerAdapter.setOnHeaderActionListener(new AccountHeaderAdapter.OnHeaderActionListener() {
            @Override public void onEditBalance() { showBalanceAdjustmentBottomSheet(); }
            @Override public void onMore() { showMoreOptions(); }
            @Override public void onRepayAction() { startRepaymentFlow(); }
            @Override public void onRepayNow() { startRepaymentFlow(); }
            @Override public void onToggleChart() {
                showingExpense = !showingExpense;
                if (showingExpense) {
                    headerAdapter.setCategorySummaries(detailViewModel.expenseSummary.getValue(), true);
                } else {
                    headerAdapter.setCategorySummaries(detailViewModel.incomeSummary.getValue(), false);
                }
            }
        });

        pagingAdapter.setOnMonthToggleListener(detailViewModel::toggleMonth);

        pagingAdapter.setOnBillClickListener(new AccountBillPagingAdapter.OnBillClickListener() {
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
                new com.example.my_project1.ui.dialog.ConfirmDialog(AccountDetailActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除这笔账单吗？删除后将无法恢复。")
                        .setConfirmText("删除")
                        .setConfirmListener(() -> handleBillDelete(bill))
                        .show();
            }

            @Override
            public void onBillRefund(Bill bill) {
                com.example.my_project1.utils.SnackbarUtils.showInfo(binding.getRoot(), "已触发退款申请");
            }

            @Override
            public void onBillEdit(Bill bill) {
                if (bill == null) return;
                Intent intent = new Intent(AccountDetailActivity.this, com.example.my_project1.ui.activity.AddBillActivity.class);
                intent.putExtra("mode", "edit");
                
                // 1. ID 处理
                if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
                    intent.putExtra("bill_id", bill.getObjectId());
                } else {
                    intent.putExtra("bill_local_id", bill.getId());
                }
                
                // 2. 基础字段
                intent.putExtra("bill_type", bill.getType());
                intent.putExtra("bill_amount", bill.getAmount());
                intent.putExtra("category_id", bill.getCategoryId());
                intent.putExtra("category_name", bill.getCategoryName());
                intent.putExtra("category_icon", bill.getCategoryIconUrl());
                intent.putExtra("category_icon_bg_color", bill.getCategoryIconBackgroundColor());
                
                // 3. 账户字段 (处理转账)
                intent.putExtra("account_id", bill.getAccountId());
                intent.putExtra("local_account_id", bill.getLocalAccountId());
                intent.putExtra("to_account_id", bill.getToAccountId());
                intent.putExtra("to_local_account_id", bill.getToLocalAccountId());
                
                // 4. 其他字段
                intent.putExtra("book_id", bill.getBookId());
                if (bill.getBillTime() != null) {
                    intent.putExtra("bill_time", bill.getBillTime().getTime());
                }
                intent.putExtra("remark", bill.getRemark());
                intent.putExtra("location", bill.getLocation());
                intent.putExtra("exclude_budget", bill.isExcludeBudget());
                
                if (bill.getImageUrls() != null && !bill.getImageUrls().isEmpty()) {
                    intent.putStringArrayListExtra("image_urls", new java.util.ArrayList<>(bill.getImageUrls()));
                }

                startActivity(intent);
            }
        });

        ConcatAdapter concatAdapter = new ConcatAdapter(headerAdapter, pagingAdapter);
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        
        // Optimize folding animation speed
        androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = binding.rvTransactions.getItemAnimator();
        if (animator instanceof androidx.recyclerview.widget.DefaultItemAnimator) {
            androidx.recyclerview.widget.DefaultItemAnimator defaultAnimator = (androidx.recyclerview.widget.DefaultItemAnimator) animator;
            defaultAnimator.setRemoveDuration(200);
            defaultAnimator.setAddDuration(200);
            defaultAnimator.setMoveDuration(200);
            defaultAnimator.setChangeDuration(200);
        }

        binding.rvTransactions.setAdapter(concatAdapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnAddTransaction.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBillActivity.class);
            if (currentAccount != null) {
                intent.putExtra("account_id", currentAccount.getObjectId());
                intent.putExtra("account_name", currentAccount.getName());
            }
            startActivity(intent);
        });

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
                    adjustmentBill.setCategoryIconUrl(currentAccount.getIconUrl()); // 使用账户图标
                    adjustmentBill.setRemark("手动调整余额");
                    
                    // 插入账单，Repository 会自动处理账户余额的增减
                    billViewModel.insertBill(adjustmentBill);
                }
            } else {
                // 如果不记为交易，直接更新账户余额
                currentAccount.setBalance(newBalance);
                accountViewModel.updateAccount(currentAccount);
            }
        });
        fragment.show(getSupportFragmentManager(), "BalanceAdjustment");
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void showVerificationBeforeDelete() {
        // Simple logic for delete verification
        showDeleteDialog();
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

    private void observeViewModelStates() {
        // 观察 BillViewModel 的操作状态
        billViewModel.operationState.observe(this, response -> {
            if (response.isLoading()) {
                Log.d(TAG, "🔄 " + response.message);
            } else if (response.isSuccess()) {
                Log.d(TAG, "✅ " + response.message);
                
                // 账单操作成功（如删除），通知 detailViewModel 刷新数据
                detailViewModel.refresh();

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

    private void showDeleteDialog() {
        if (currentAccount == null) {
            SnackbarUtils.showError(binding.getRoot(), "账户信息不存在");
            return;
        }

        int billCount = 0;
        if (detailViewModel.stats.getValue() != null) {
            billCount = detailViewModel.stats.getValue().getBillCount();
        }
        boolean hasBills = billCount > 0;

        Log.d(TAG, "🗑️ 准备删除账户: " + currentAccount.getName() +
                ", 有账单: " + hasBills + ", 账单数: " + billCount);

        deleteDialog = DeleteAccountDialogFragment.newInstance(
                currentAccount.getName(),
                currentAccount.getIconUrl(),
                hasBills,
                billCount
        );

        deleteDialog.setOnDeleteActionListener(new DeleteAccountDialogFragment.OnDeleteActionListener() {
            @Override public void onChooseTargetAccount() { showChooseAccountDialog(); }
            @Override public void onMigrateAndDelete(Account targetAccount) { migrateBillsAndDeleteAccount(targetAccount); }
            @Override public void onDeleteWithoutMigration() { deleteAccountWithoutMigration(); }
            @Override public void onDeleteAll() { deleteAccountAndBills(); }
            @Override public void onDirectDelete() { directDeleteAccount(); }
        });

        deleteDialog.show(getSupportFragmentManager(), "DeleteAccountDialog");
    }

    private void showDateRangePicker() {
        DateRangePickerFragment picker = new DateRangePickerFragment();
        picker.setOnDateRangeSelectedListener((start, end, formattedStart, formattedEnd) -> {
            detailViewModel.setDateRange(new Date(start), new Date(end));
            SnackbarUtils.showSuccess(binding.getRoot(), "已筛选: " + formattedStart + " 至 " + formattedEnd);
        });
        picker.show(getSupportFragmentManager(), "DateRangePicker");
    }

    private void handleBillDelete(Bill bill) {
        if (bill == null || bill.getId() < 0) return;
        billViewModel.deleteBill(bill);
    }

    // ==================== 🔴 删除账户相关 ====================

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

    @Override
    public void finish() {
        super.finish();
        // 左进右出的动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

}