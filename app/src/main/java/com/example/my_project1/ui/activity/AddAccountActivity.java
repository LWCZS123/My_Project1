package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ActivityAddAccountBinding;
import com.example.my_project1.ui.adapter.account.AccountGroupAdapter;
import com.example.my_project1.ui.fragment.AccountEditBottomSheetFragment;
import com.example.my_project1.ui.fragment.AddAccountFragment;
import com.example.my_project1.ui.fragment.AddAccountGroupFragment;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;

import java.util.List;
import java.util.Map;

import cn.bmob.v3.BmobUser;

public class AddAccountActivity extends AppCompatActivity {

    private static final String TAG = "AddAccountActivity";

    private ActivityAddAccountBinding binding;
    private AccountGroupAdapter groupAdapter;
    private AccountViewModel accountViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        binding = ActivityAddAccountBinding.inflate(getLayoutInflater());
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

        accountViewModel = new ViewModelProvider(this)
                .get(AccountViewModel.class);

        groupAdapter = new AccountGroupAdapter();

        binding.btnBack.setOnClickListener(v ->
                        finish()

        );

        binding.btnCreateAccount.setOnClickListener(v->{
            AddAccountFragment fragment = AddAccountFragment.newInstance();
            fragment.show(getSupportFragmentManager(), "AddAccountFragment");
        });

        binding.btnAddGroup.setOnClickListener(v ->{
            AddAccountGroupFragment fragment = AddAccountGroupFragment.newInstance();
            fragment.show(getSupportFragmentManager(), "AddAccountGroupFragment");
        });

        initRecyclerView();
        observeViewModel();
        loadAccountGroups();
    }

    private void initRecyclerView() {
        groupAdapter = new AccountGroupAdapter();

        binding.rvAccountGroups.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAccountGroups.setAdapter(groupAdapter);

        groupAdapter.setOnGroupActionClickListener(new AccountGroupAdapter.OnGroupActionClickListener() {
            @Override
            public void onEditGroup(AccountGroup group) {
                showAccountGroupEditDialog(group);
            }

            @Override
            public void onAccount(Account account) {
                showAccountEditDialog(account);
            }

            @Override
            public void onGroupExpand(String groupId) {
                Log.d(TAG, "🔄 展开账户组，开始加载账户: " + groupId);
                observeGroupAccounts(groupId);
            }
        });
    }

    /**
     * 🔴 新增：为特定账户组订阅账户数据
     */
    private void observeGroupAccounts(String groupId) {
        Log.d(TAG, "📡 开始订阅组 " + groupId + " 的账户数据");

        // 获取该组的 LiveData 并观察
        accountViewModel.getAccountsByGroupId(groupId).observe(this, accounts -> {
            Log.d(TAG, "🔔 组 " + groupId + " 的账户数据回调触发");
            Log.d(TAG, "   - 账户数据: " + (accounts != null ? accounts.size() + " 条" : "null"));

            if (accounts != null) {
                for (int i = 0; i < accounts.size(); i++) {
                    Log.d(TAG, "     账户 " + (i+1) + ": " + accounts.get(i).getName());
                }
                groupAdapter.updateAccountsForExpandedGroup(groupId, accounts);
            } else {
                Log.d(TAG, "⚠️ 账户数据为null，更新为空列表");
                groupAdapter.updateAccountsForExpandedGroup(groupId, new java.util.ArrayList<>());
            }
        });
    }

    private void showAccountEditDialog(Account account) {
        AccountEditBottomSheetFragment fragment = AccountEditBottomSheetFragment.newInstance();
        Bundle args = new Bundle();
        args.putString("title","账户");
        args.putLong("accountId",account.getId());
        args.putString("accountGroupId",account.getGroupId());
        args.putString("accountObjectId",account.getObjectId());
        args.putString("accountName",account.getName());
        args.putString("accountIconUrl",account.getIconUrl());
        args.putDouble("balance",account.getBalance());
        args.putBoolean("isCredit",account.isCredit());
        args.putString("remark",account.getRemark());
        args.putString("cardNumber",account.getCardNumber());
        args.putDouble("creditLimit",account.getCreditLimit());

        fragment.setArguments(args);
        fragment.show(getSupportFragmentManager(), "AccountEditBottomSheetFragment");
    }

    private void showAccountGroupEditDialog(AccountGroup group) {
        AccountEditBottomSheetFragment fragment = AccountEditBottomSheetFragment.newInstance();
        Bundle args = new Bundle();
        args.putString("title","账户组");
        args.putString("groupObjectId",group.getObjectId());
        args.putString("groupName",group.getName());
        args.putString("groupIconUrl",group.getIconUrl());
        fragment.setArguments(args);
        fragment.show(getSupportFragmentManager(), "AccountEditBottomSheetFragment");
    }

    private void observeViewModel() {
        // 观察账户组列表
        accountViewModel.getAccountGroups().observe(this, groups -> {
            if (groups != null) {
                groupAdapter.setGroups(groups);
                Log.d(TAG, "✅ 账户组更新: " + groups.size());
            }
        });

        // 观察账户组更新通知（用于实时刷新）
        accountViewModel.groupAccountsUpdate.observe(this, updateMap -> {
            if (updateMap != null && !updateMap.isEmpty()) {
                for (Map.Entry<String, List<Account>> entry : updateMap.entrySet()) {
                    String groupId = entry.getKey();
                    List<Account> accounts = entry.getValue();

                    Log.d(TAG, "🔔 收到组 " + groupId + " 的更新通知");
                    Log.d(TAG, "   - 账户数量: " + (accounts != null ? accounts.size() : 0));

                    // 直接更新 Adapter
                    if (accounts != null) {
                        groupAdapter.updateAccountsForExpandedGroup(groupId, accounts);
                    }
                }
            }
        });
    }

    private void loadAccountGroups() {
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getObjectId();
            accountViewModel.loadAccountGroups(userId);
            Log.d(TAG, "开始加载用户账户组: " + userId);
        } else {
            Log.d(TAG, "用户未登录");
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - 触发云同步");
        accountViewModel.syncFromCloud();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public void finish() {
        super.finish();
        // 左进右出的动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

}