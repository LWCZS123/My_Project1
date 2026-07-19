package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentAccountEditBottomSheetBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * AccountEditBottomSheetFragment
 * ----------------------------------------------------------------
 * 功能：账户/账户组编辑底部弹窗
 *
 *
 * - 集成删除账户对话框（支持账单迁移）
 * - 使用 SnackbarUtils 替代 Toast
 * - 观察 BillViewModel 状态变化
 */
public class AccountEditBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String TAG = "AccountEditSheet";

    private String groupIconUrl, groupName, title;
    private String accountId, accountName, accountGroupId, accountIconUrl, remark, cardNumber;
    private long account_id;
    private String groupId;
    private double balance, creditLimit;
    private boolean isCredit;
    private boolean isAccountMode = false;

    private FragmentAccountEditBottomSheetBinding binding;

    private AccountViewModel accountViewModel;
    private BillViewModel billViewModel;

    //  删除账户相关
    private DeleteAccountDialogFragment deleteDialog;
    private Account selectedTargetAccount = null;
    private boolean isWaitingForMigration = false;
    private boolean isWaitingForSetNoAccount = false;

    public static AccountEditBottomSheetFragment newInstance() {
        return new AccountEditBottomSheetFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);
    }

    private <T> void observeOnce(androidx.lifecycle.LiveData<T> liveData,
                                 androidx.lifecycle.Observer<T> observer) {
        liveData.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<T>() {
            @Override
            public void onChanged(T t) {
                liveData.removeObserver(this);
                observer.onChanged(t);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAccountEditBottomSheetBinding.inflate(inflater, container, false);
        Bundle args = getArguments();

        if (args != null) {
            title = args.getString("title", "分类标题");
            groupName = args.getString("groupName");
            groupIconUrl = args.getString("groupIconUrl");
            groupId = args.getString("groupObjectId");

            title = args.getString("title");
            accountId = args.getString("accountObjectId");
            account_id = args.getLong("accountId");
            accountGroupId = args.getString("accountGroupId");
            accountName = args.getString("accountName");
            accountIconUrl = args.getString("accountIconUrl");
            balance = args.getDouble("balance");
            remark = args.getString("remark");
            isCredit = args.getBoolean("isCredit");
            cardNumber = args.getString("cardNumber");
            creditLimit = args.getDouble("creditLimit");

            if (accountId != null) {
                isAccountMode = true;
            }
        }

        setupUI();
        setupListeners();

        // 🔑 观察 BillViewModel 状态
        observeViewModelStates();

        return binding.getRoot();
    }

    private void setupUI() {
        if (isAccountMode) {
            binding.tvAccountGroupTitle.setText(title);
            binding.tvGroupName.setText(accountName);
            binding.ivMove.setImageResource(R.drawable.ic_move_account);
            binding.tvMove.setText("移至其他分组");
            binding.tvEdit.setText("修改账户");
            binding.tvDelete.setText("删除账户");
            if (accountIconUrl != null) {
                ImageLoaderUtils.load(requireContext(), accountIconUrl, binding.ivGroupIcon);
            } else {
                binding.ivGroupIcon.setVisibility(View.GONE);
            }
        } else {
            binding.tvAccountGroupTitle.setText(title);
            binding.tvGroupName.setText(groupName);
            if (groupIconUrl != null) {
                ImageLoaderUtils.load(requireContext(), groupIconUrl, binding.ivGroupIcon);
            } else {
                binding.ivGroupIcon.setVisibility(View.GONE);
            }
        }
    }

    private void setupListeners() {
        binding.btnAddAccount.setOnClickListener(v -> showAddAccount());
        binding.btnEditAccountGroup.setOnClickListener(v -> showEditAccountGroup());
        binding.btnDeleteAccountGroup.setOnClickListener(v -> showDeleteAccountGroup());
    }

    /**
     * 🔑 观察 ViewModel 状态（遵循 BillViewModel 风格）
     */
    private void observeViewModelStates() {
        // 观察 BillViewModel 的操作状态
        billViewModel.operationState.observe(getViewLifecycleOwner(), response -> {
            if (response.isLoading()) {
                Log.d(TAG, "🔄 " + response.message);
            } else if (response.isSuccess()) {
                Log.d(TAG, "✅ " + response.message);

                // 🔴 根据不同的操作阶段执行后续操作
                if (isWaitingForMigration) {
                    isWaitingForMigration = false;
                    deleteAccountAfterMigration();
                } else if (isWaitingForSetNoAccount) {
                    isWaitingForSetNoAccount = false;
                    deleteAccountAfterSetNoAccount();
                }
            } else if (response.isError()) {
                Log.e(TAG, "❌ " + response.message);
                if (binding != null) {
                    SnackbarUtils.showError(binding.getRoot(), response.message);
                }
                // 失败时重置状态
                isWaitingForMigration = false;
                isWaitingForSetNoAccount = false;
            }
        });

        // 观察 BillViewModel 的 Toast 消息
        billViewModel.toastMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty() && binding != null) {
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

    // ==================== 🔴 删除账户相关（新增）====================

    /**
     * 显示删除账户/账户组对话框
     */
    private void showDeleteAccountGroup() {
        if (isAccountMode) {
            // 🔴 账户模式：显示删除账户对话框（支持账单迁移）
            showDeleteAccountDialog();
        } else {
            // 账户组模式：原有逻辑
            if (groupId == null) {
                SnackbarUtils.showError(binding.getRoot(), "账户组ID为空");
                return;
            }

            observeOnce(accountViewModel.getAccountGroups(), groups -> {
                if (groups == null || groups.isEmpty()) return;

                AccountGroup targetGroup = null;
                for (AccountGroup g : groups) {
                    if (groupId.equals(g.getObjectId())) {
                        targetGroup = g;
                        break;
                    }
                }

                if (targetGroup == null) {
                    SnackbarUtils.showError(binding.getRoot(), "未找到该账户组");
                    return;
                }

                checkAndDeleteGroup(targetGroup, groups);
            });
        }
    }

    /**
     * 🔴 显示删除账户对话框（新增）
     */
    private void showDeleteAccountDialog() {
        if (accountId == null) {
            SnackbarUtils.showError(binding.getRoot(), "账户ID为空");
            return;
        }

        // 查询账户下的账单数量
        billViewModel.getBillsByAccount(accountId).observe(getViewLifecycleOwner(), bills -> {
            boolean hasBills = bills != null && !bills.isEmpty();
            int billCount = hasBills ? bills.size() : 0;

            Log.d(TAG, "🗑️ 准备删除账户: " + accountName +
                    ", 有账单: " + hasBills + ", 账单数: " + billCount);

            // 移除观察，避免重复触发
            billViewModel.getBillsByAccount(accountId).removeObservers(getViewLifecycleOwner());

            // 显示删除对话框
            deleteDialog = DeleteAccountDialogFragment.newInstance(
                    accountName,
                    accountIconUrl,
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
                    deleteAllBillsAndAccount();
                }

                @Override
                public void onDirectDelete() {
                    directDeleteAccount();
                }
            });

            deleteDialog.show(getParentFragmentManager(), "DeleteAccountDialog");
        });
    }

    /**
     * 🔴 显示账户选择对话框（新增）
     */
    private void showChooseAccountDialog() {
        // 🔴 使用排除模式创建对话框，隐藏当前要删除的账户
        BillChooseAccountFragment chooseFragment =
                BillChooseAccountFragment.newInstance(accountId);

        chooseFragment.setOnAccountChooseListener((account, iconUrl, accountName) -> {
            if (account != null) {
                selectedTargetAccount = account;

                if (deleteDialog != null) {
                    deleteDialog.setSelectedTargetAccount(account);
                }

                Log.d(TAG, "✅ 选择目标账户: " + accountName);
            }
        });
        chooseFragment.show(getParentFragmentManager(), "ChooseAccountDialog");
    }

    /**
     * 🔴 迁移账单并删除账户（新增）
     */
    private void migrateBillsAndDeleteAccount(Account targetAccount) {
        if (targetAccount == null || accountId == null) {
            SnackbarUtils.showError(binding.getRoot(), "目标账户或当前账户为空");
            return;
        }

        Log.d(TAG, "🔄 开始迁移账单: " + accountName + " -> " + targetAccount.getName());

        // 🔑 设置标记，等待迁移完成
        isWaitingForMigration = true;

        // 🔑 调用 ViewModel 方法
        billViewModel.migrateBillsToAccount(accountId, account_id, targetAccount.getObjectId());
    }

    /**
     * 🔴 迁移成功后删除账户（新增）
     */
    private void deleteAccountAfterMigration() {
        Account accountToDelete = buildAccountFromParams();
        if (accountToDelete == null) {
            return;
        }

        Log.d(TAG, "🔄 账单迁移成功，开始删除账户");

        accountViewModel.deleteAccount(accountToDelete, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "账单已迁移，账户已删除");
                Log.d(TAG, "✅ 账户删除成功");

                // 延迟关闭，让用户看到成功提示
                if (binding != null) {
                    binding.getRoot().postDelayed(() -> dismiss(), 1000);
                }
            } else {
                SnackbarUtils.showError(binding.getRoot(), "账户删除失败: " + message);
                Log.e(TAG, "❌ 账户删除失败: " + message);
            }
        });
    }

    /**
     * 🔴 不迁移账单，将账单设置为无账户（新增）
     */
    private void deleteAccountWithoutMigration() {
        if (accountId == null) {
            SnackbarUtils.showError(binding.getRoot(), "当前账户为空");
            return;
        }

        Log.d(TAG, "🔄 开始设置账单为无账户: " + accountName);

        // 🔑 设置标记，等待设置完成
        isWaitingForSetNoAccount = true;

        // 🔑 调用 ViewModel 方法 (传入 objectId 和 localId)
        billViewModel.setBillsToNoAccount(accountId, account_id);
    }

    /**
     * 🔴 删除账户及所有账单（新增）
     */
    private void deleteAllBillsAndAccount() {
        if (accountId == null && account_id <= 0) {
            SnackbarUtils.showError(binding.getRoot(), "当前账户为空");
            return;
        }

        Log.d(TAG, "🗑️ 开始删除账户及所有账单: " + accountName);
        
        // 🔑 设置标记，等待完成
        isWaitingForSetNoAccount = true;
        
        // 🔑 调用 ViewModel 方法删除所有账单
        billViewModel.deleteAllBillsByAccount(accountId, account_id);
    }

    /**
     * 🔴 设置无账户成功后删除账户（新增）
     */
    private void deleteAccountAfterSetNoAccount() {
        Account accountToDelete = buildAccountFromParams();
        if (accountToDelete == null) {
            return;
        }

        Log.d(TAG, "🔄 账单已设置为无账户，开始删除账户");

        accountViewModel.deleteAccount(accountToDelete, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "账单已设置为无账户，账户已删除");
                Log.d(TAG, "✅ 账户删除成功");

                // 延迟关闭
                if (binding != null) {
                    binding.getRoot().postDelayed(() -> dismiss(), 1000);
                }
            } else {
                SnackbarUtils.showError(binding.getRoot(), "账户删除失败: " + message);
                Log.e(TAG, "❌ 账户删除失败: " + message);
            }
        });
    }

    /**
     * 🔴 直接删除账户（无账单的情况）（新增）
     */
    private void directDeleteAccount() {
        Account accountToDelete = buildAccountFromParams();
        if (accountToDelete == null) {
            return;
        }

        Log.d(TAG, "🗑️ 直接删除账户: " + accountName);

        accountViewModel.deleteAccount(accountToDelete, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "账户已删除");
                Log.d(TAG, "✅ 账户删除成功");

                // 延迟关闭
                if (binding != null) {
                    binding.getRoot().postDelayed(() -> dismiss(), 1000);
                }
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
                Log.e(TAG, "❌ 删除失败: " + message);
            }
        });
    }

    /**
     * 🔴 从参数构建 Account 对象（新增）
     */
    private Account buildAccountFromParams() {
        if (accountId == null) {
            SnackbarUtils.showError(binding.getRoot(), "账户信息不完整");
            return null;
        }

        Account account = new Account();
        account.setId(account_id);
        account.setObjectId(accountId);
        account.setName(accountName);
        account.setGroupId(accountGroupId);
        account.setIconUrl(accountIconUrl);
        account.setBalance(balance);
        account.setCredit(isCredit);
        account.setRemark(remark);
        account.setCardNumber(cardNumber);
        account.setCreditLimit(creditLimit);

        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            account.setUserId(user.getObjectId());
        }

        return account;
    }

    // ==================== 账户组删除相关（原有逻辑）====================

    private void checkAndDeleteGroup(AccountGroup groupToDelete, List<AccountGroup> allGroups) {
        observeOnce(
                accountViewModel.getAccountsByGroupId(groupToDelete.getObjectId()),
                accounts -> {
                    int activeAccountCount = 0;
                    if (accounts != null) {
                        for (Account acc : accounts) {
                            if (acc.getSyncState() != SyncState.TO_DELETE) {
                                activeAccountCount++;
                            }
                        }
                    }

                    if (activeAccountCount > 0) {
                        showSelectNewGroupFragment(groupToDelete, allGroups);
                    } else {
                        confirmDeleteEmptyGroup(groupToDelete);
                    }
                }
        );
    }

    private void confirmDeleteEmptyGroup(AccountGroup group) {
        accountViewModel.deleteAccountGroup(group, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "删除账户组成功");
                dismiss();
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
            }
        });
    }

    private void showSelectNewGroupFragment(AccountGroup groupToDelete, List<AccountGroup> allGroups) {
        if (getParentFragmentManager() == null) return;

        List<AccountGroup> availableGroups = new ArrayList<>();
        for (AccountGroup group : allGroups) {
            if (!groupToDelete.getObjectId().equals(group.getObjectId())) {
                availableGroups.add(group);
            }
        }

        if (availableGroups.isEmpty()) {
            SnackbarUtils.showWarning(binding.getRoot(), "没有其他账户组可选，请先创建新的账户组");
            return;
        }

        SelectGroupBottomSheetFragment sheet = SelectGroupBottomSheetFragment.newInstance(
                availableGroups,
                null,
                true
        );

        sheet.setOnGroupSelectedListener(newGroup -> {
            moveAccountsAndDeleteGroup(groupToDelete, newGroup);
        });

        sheet.show(getParentFragmentManager(), "SelectGroup");
    }

    private void moveAccountsAndDeleteGroup(AccountGroup oldGroup, AccountGroup newGroup) {
        accountViewModel.moveAccountsToGroup(
                oldGroup.getObjectId(),
                newGroup.getObjectId(),
                (moveSuccess, moveMessage) -> {
                    if (moveSuccess) {
                        accountViewModel.deleteAccountGroup(
                                oldGroup,
                                (deleteSuccess, deleteMessage) -> {
                                    if (deleteSuccess) {
                                        SnackbarUtils.showSuccess(binding.getRoot(),
                                                "已将账户移至【" + newGroup.getName() + "】并删除原账户组");
                                        dismiss();
                                    } else {
                                        SnackbarUtils.showError(binding.getRoot(), deleteMessage);
                                    }
                                }
                        );
                    } else {
                        SnackbarUtils.showError(binding.getRoot(), "移动账户失败: " + moveMessage);
                    }
                }
        );
    }

    // ==================== 编辑相关（原有逻辑）====================

    private void showEditAccountGroup() {
        if (isAccountMode) {
            Account account = buildAccountFromParams();
            if (account == null) return;

            AddAccountFragment fragment = AddAccountFragment.newInstance(account);
            fragment.show(getParentFragmentManager(), "AddAccountFragment");
            dismiss();
        } else {
            AccountGroup group = new AccountGroup();
            group.setObjectId(groupId);
            group.setName(groupName);
            group.setIconUrl(groupIconUrl);
            BmobUser user = BmobUser.getCurrentUser();
            if (user != null) {
                group.setUserId(user.getObjectId());
            }
            AddAccountGroupFragment fragment = AddAccountGroupFragment.newInstance(group);
            fragment.show(getParentFragmentManager(), "AddAccountGroupFragment");
            dismiss();
        }
    }

    private void showAddAccount() {
        if (isAccountMode) {
            moveAccountToOtherGroup();
        } else {
            AddAccountFragment fragment = AddAccountFragment.newInstance();
            fragment.show(getParentFragmentManager(), "AddAccountFragment");
        }
    }

    private void moveAccountToOtherGroup() {
        if (accountId == null || accountGroupId == null) {
            SnackbarUtils.showError(binding.getRoot(), "账户信息不完整");
            return;
        }

        observeOnce(accountViewModel.getAccountGroups(), groups -> {
            if (groups == null || groups.isEmpty()) {
                SnackbarUtils.showInfo(binding.getRoot(), "没有可移动的账户组");
                return;
            }

            List<AccountGroup> availableGroups = new ArrayList<>();
            for (AccountGroup g : groups) {
                if (!g.getObjectId().equals(accountGroupId)) {
                    availableGroups.add(g);
                }
            }

            if (availableGroups.isEmpty()) {
                SnackbarUtils.showInfo(binding.getRoot(), "暂无其他可移动的账户组");
                return;
            }

            SelectGroupBottomSheetFragment sheet =
                    SelectGroupBottomSheetFragment.newInstance(
                            availableGroups,
                            null,
                            false
                    );

            sheet.setOnGroupSelectedListener(newGroup -> {
                performMoveAccount(accountId, newGroup);
            });

            sheet.show(getParentFragmentManager(), "MoveAccount");
        });
    }

    private void performMoveAccount(String accountId, AccountGroup newGroup) {
        accountViewModel.moveSingleAccount(accountId, accountGroupId, newGroup.getObjectId(),
                (moveSuccess, moveMessage) -> {
                    if (moveSuccess) {
                        SnackbarUtils.showSuccess(binding.getRoot(), moveMessage);
                        dismiss();
                    } else {
                        SnackbarUtils.showError(binding.getRoot(), moveMessage);
                    }
                });
    }

    // ==================== Dialog 设置 ====================

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );

            if (bottomSheet != null) {
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(),
                                R.drawable.bg_bottom_sheet1)
                );

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.6);
                bottomSheet.setLayoutParams(params);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return dialog;
    }

    private int getScreenHeight() {
        return requireContext().getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}