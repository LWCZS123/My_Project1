package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ActivityAccountGroupBinding;
import com.example.my_project1.ui.adapter.account.AccountGroupManageAdapter;
import com.example.my_project1.ui.fragment.AccountActionsBottomSheet;
import com.example.my_project1.ui.fragment.AccountGroupActionsBottomSheet;
import com.example.my_project1.ui.fragment.CreateGroupDialogFragment;
import com.example.my_project1.ui.fragment.DeleteConfirmDialogFragment;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.Map;

import cn.bmob.v3.BmobUser;

public class AccountGroupActivity extends AppCompatActivity {

    private ActivityAccountGroupBinding binding;
    private AccountViewModel viewModel;
    private AccountGroupManageAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        setupUI();
        observeData();
    }

    private void setupUI() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.ivAdd.setOnClickListener(v -> showCreateGroupDialog());

        adapter = new AccountGroupManageAdapter();
        binding.rvGroups.setLayoutManager(new LinearLayoutManager(this));
        binding.rvGroups.setAdapter(adapter);

        adapter.setOnItemClickListener(new AccountGroupManageAdapter.OnItemClickListener() {
            @Override
            public void onEdit(AccountGroup group) {
                showModifyGroupBottomSheet(group);
            }

            @Override
            public void onDelete(AccountGroup group) {
                handleDeleteGroup(group);
            }

            @Override
            public void onAddAccount(AccountGroup group) {
                Intent intent = new Intent(AccountGroupActivity.this, ChooseAccountTypeActivity.class);
                intent.putExtra("groupId", group.getObjectId());
                startActivity(intent);
            }

            @Override
            public void onAccountClick(Account account) {
                showAccountActionsBottomSheet(account);
            }
        });
    }

    private void showCreateGroupDialog() {
        showCreateGroupDialog(null);
    }

    private void showCreateGroupDialog(AccountGroup editGroup) {
        CreateGroupDialogFragment dialog;
        if (editGroup != null) {
            dialog = CreateGroupDialogFragment.newInstance(editGroup.getObjectId(), editGroup.getName());
        } else {
            dialog = new CreateGroupDialogFragment();
        }

        dialog.setOnGroupCreateListener(new CreateGroupDialogFragment.OnGroupCreateListener() {
            @Override
            public void onCreate(String name) {
                AccountGroup group = new AccountGroup();
                group.setName(name);
                group.setUserId(BmobUser.getCurrentUser().getObjectId());
                group.setIconUrl("ic_category");
                viewModel.insertAccountGroup(group);
            }

            @Override
            public void onUpdate(String id, String newName) {
                if (editGroup != null) {
                    editGroup.setName(newName);
                    viewModel.updateAccountGroup(editGroup);
                }
            }
        });
        dialog.show(getSupportFragmentManager(), "CreateGroup");
    }

    private void showModifyGroupBottomSheet(AccountGroup group) {
        boolean isDefault = isDefaultGroup(group.getName());
        AccountGroupActionsBottomSheet bottomSheet = AccountGroupActionsBottomSheet.newInstance(group, isDefault);
        bottomSheet.setOnActionClickListener(new AccountGroupActionsBottomSheet.OnActionClickListener() {
            @Override
            public void onModify() {
                showCreateGroupDialog(group);
            }

            @Override
            public void onSort() {
                SnackbarUtils.showInfo(binding.getRoot(), "排序功能开发中");
            }

            @Override
            public void onDelete() {
                handleDeleteGroup(group);
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "GroupActions");
    }

    private boolean isDefaultGroup(String name) {
        return "资金账户".equals(name) || "信用账户".equals(name) || "充值账户".equals(name);
    }

    private void showAccountActionsBottomSheet(Account account) {
        AccountActionsBottomSheet bottomSheet = AccountActionsBottomSheet.newInstance(account);
        bottomSheet.setOnActionClickListener(new AccountActionsBottomSheet.OnActionClickListener() {
            @Override
            public void onMoveToGroup() {
                // Fix multiple popups by getting current groups without a persistent observer
                java.util.List<AccountGroup> groups = viewModel.getAccountGroups().getValue();
                if (groups != null) {
                    // 🔴 过滤逻辑：只能迁移到自定义分组，排除默认分组
                    com.example.my_project1.ui.fragment.SelectGroupBottomSheetFragment fragment = 
                            com.example.my_project1.ui.fragment.SelectGroupBottomSheetFragment.newInstance(
                                    groups, account.getGroupId(), false, true);
                    fragment.setOnGroupSelectedListener(targetGroup -> {
                        viewModel.moveSingleAccount(account.getObjectId(), account.getGroupId(), targetGroup.getObjectId(), (success, message) -> {
                            if (success) SnackbarUtils.showSuccess(binding.getRoot(), "移动成功");
                        });
                    });
                    fragment.show(getSupportFragmentManager(), "MoveGroup");
                }
            }

            @Override
            public void onDetails() {
                Intent intent = new Intent(AccountGroupActivity.this, AccountDetailActivity.class);
                intent.putExtra(AccountDetailActivity.EXTRA_ACCOUNT_ID, account.getObjectId());
                intent.putExtra(AccountDetailActivity.EXTRA_ACCOUNT_LOCAL_ID, account.getId());
                startActivity(intent);
            }

            @Override
            public void onSort() {
                SnackbarUtils.showInfo(binding.getRoot(), "排序功能开发中");
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "AccountActions");
    }

    private void handleDeleteGroup(AccountGroup group) {
        if (isDefaultGroup(group.getName())) {
            SnackbarUtils.showWarning(binding.getRoot(), "默认分组不可删除");
            return;
        }

        DeleteConfirmDialogFragment dialog = new DeleteConfirmDialogFragment();
        dialog.setOnDeleteConfirmListener(() -> {
            deleteGroupAndMoveAccounts(group);
        });
        dialog.show(getSupportFragmentManager(), "DeleteGroupConfirm");
    }

    private void deleteGroupAndMoveAccounts(AccountGroup group) {
        Log.d("AccountGroupActivity", "🗑️ 发起增强型分组删除: " + group.getName());
        
        // 🚀 使用 ViewModel 的自动迁移删除逻辑，彻底解决 Activity 侧 getValue() 为 null 导致的卡死问题
        viewModel.deleteGroupWithAutoMigration(group, (success, message) -> {
            if (success) {
                SnackbarUtils.showSuccess(binding.getRoot(), "删除成功");
            } else {
                SnackbarUtils.showError(binding.getRoot(), "删除失败: " + message);
            }
        });
    }

    private void observeData() {
        viewModel.getAccountGroups().observe(this, groups -> {
            if (groups != null) {
                adapter.setGroups(groups);
            }
        });

        viewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts != null) {
                Map<String, java.util.List<Account>> groupMap = new java.util.HashMap<>();
                for (Account acc : accounts) {
                    String gid = acc.getGroupId();
                    if (gid != null) {
                        if (!groupMap.containsKey(gid)) groupMap.put(gid, new java.util.ArrayList<>());
                        groupMap.get(gid).add(acc);
                    }
                }
                for (Map.Entry<String, java.util.List<Account>> entry : groupMap.entrySet()) {
                    adapter.setGroupAccounts(entry.getKey(), entry.getValue());
                }
            }
        });
    }
}
