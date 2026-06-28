package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentBillChooseAccountBinding;
import com.example.my_project1.ui.adapter.account.AssetsGroupAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

public class BillChooseAccountFragment extends BottomSheetDialogFragment {

    private static final String TAG ="BillChooseAccountFragment" ;
    private static final String ARG_EXCLUDE_ACCOUNT_ID = "exclude_account_id";

    private FragmentBillChooseAccountBinding binding;
    private AssetsGroupAdapter adapter;
    private AccountViewModel viewModel;

    private Account selectedAccount = null;

    // 要排除的账户ID（用于删除账户时不显示自己）
    private String excludeAccountId = null;

    // 保存观察者 (防止重复监听)
    private Map<String, Observer<List<Account>>> accountObservers = new HashMap<>();

    /**
     * 创建实例 - 普通模式（显示所有账户）
     */
    public static BillChooseAccountFragment newInstance() {
        return new BillChooseAccountFragment();
    }

    /**
     * 创建实例 - 排除模式（隐藏指定账户，用于删除账户时的迁移）
     * @param excludeAccountId 要排除的账户ID
     */
    public static BillChooseAccountFragment newInstance(String excludeAccountId) {
        BillChooseAccountFragment fragment = new BillChooseAccountFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXCLUDE_ACCOUNT_ID, excludeAccountId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        binding = FragmentBillChooseAccountBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        // 获取要排除的账户ID
        if (getArguments() != null) {
            excludeAccountId = getArguments().getString(ARG_EXCLUDE_ACCOUNT_ID);
            if (excludeAccountId != null) {
                Log.d(TAG, "排除账户模式，排除账户ID: " + excludeAccountId);
            }
        }

        setupRecyclerView();
        observeGroupData();
        loadAccountGroupsFromCloud();
        setupNoAccountOption(); // 默认“无账户”

        return binding.getRoot();
    }

    /** ================== RecyclerView 显示账户组 ================== **/
    private void setupRecyclerView() {
        adapter = new AssetsGroupAdapter();

        binding.rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvGroups.setNestedScrollingEnabled(true); // 保持可滑动
        binding.rvGroups.setOverScrollMode(View.OVER_SCROLL_NEVER);
        binding.rvGroups.setAdapter(adapter);

        adapter.setOnGroupActionClickListener(new AssetsGroupAdapter.OnGroupActionClickListener() {
            @Override public void onEditGroup(AccountGroup group) {}

            // 组展开 → 加载账户
            @Override public void onGroupExpand(String groupId) {}

            // 点击账户 → 返回 AddBillActivity
            @Override public void onAccount(Account account) {
                // 🔴 防止选择要删除的账户（迁移模式）
                if (excludeAccountId != null && account.getObjectId() != null
                        && account.getObjectId().equals(excludeAccountId)) {
                    SnackbarUtils.showWarning(binding.getRoot(), "不能选择要删除的账户");
                    return;
                }

                // 🔴 防止选择已删除的账户
                if (account.getSyncState() == SyncState.TO_DELETE) {
                    SnackbarUtils.showWarning(binding.getRoot(), "该账户已被删除,无法选择");
                    return;
                }

                selectedAccount = account;
                dismiss();
                if (listener != null) {
                    listener.onChoose(account, account.getIconUrl(), account.getName());
                }
            }
        });
    }

    /** ================== 监听账户组变化 ================== **/
    private void observeGroupData() {
        viewModel.getAccountGroups().observe(getViewLifecycleOwner(), groups -> {
            // 打印调试信息
            if (groups != null) {
                Log.d(TAG, "收到账户组数量 = "+ groups.size());
                for (AccountGroup group : groups) {
                    Log.d(TAG, "Group: " + group.getName() + ", 账户数量 = " + group.getAccountCount());
                }
            } else {
                Log.d(TAG, "没有账户组数据");
            }

            // 🔴 先观察所有账户组的账户数据，然后再过滤
            observeGroupAccounts(groups);
        });
    }

    /** ================== 每个账户组监听账户变化 ================== **/
    private void observeGroupAccounts(List<AccountGroup> groups){
        // 🔴 用于统计每个账户组的可见账户数量
        Map<String, Integer> visibleAccountCounts = new HashMap<>();

        for(AccountGroup group : groups){
            if(group.getAccountCount() == 0) continue;

            String groupId = group.getObjectId();

            // 清除旧监听
            if(accountObservers.containsKey(groupId)){
                viewModel.getAccountsByGroupId(groupId).removeObserver(accountObservers.get(groupId));
            }

            // 创建监听 → 展开后实时更新账户列表
            Observer<List<Account>> observer = accounts -> {
                // 过滤账户：排除已删除的账户 + 要删除的账户（迁移模式）
                if (accounts != null) {
                    List<Account> activeAccounts = new ArrayList<>();
                    for (Account account : accounts) {
                        // 跳过已删除的账户
                        if (account.getSyncState() == SyncState.TO_DELETE) {
                            continue;
                        }
                        // 跳过要删除的账户（迁移模式）
                        if (excludeAccountId != null && account.getObjectId() != null
                                && account.getObjectId().equals(excludeAccountId)) {
                            Log.d(TAG, "过滤掉要删除的账户: " + account.getName());
                            continue;
                        }
                        activeAccounts.add(account);
                    }

                    // 🔴 记录该组的可见账户数量
                    visibleAccountCounts.put(groupId, activeAccounts.size());

                    adapter.updateAccountsForExpandedGroup(groupId, activeAccounts);
                } else {
                    visibleAccountCounts.put(groupId, 0);
                    adapter.updateAccountsForExpandedGroup(groupId, accounts);
                }

                // 🔴 每次账户数据更新后，重新过滤账户组
                filterAndSetGroups(groups, visibleAccountCounts);
            };

            viewModel.getAccountsByGroupId(groupId).observe(getViewLifecycleOwner(), observer);
            accountObservers.put(groupId, observer);
        }
    }

    /**
     * 🔴 新增：过滤并设置账户组（只显示有可见账户的组）
     */
    private void filterAndSetGroups(List<AccountGroup> allGroups, Map<String, Integer> visibleAccountCounts) {
        List<AccountGroup> filteredGroups = new ArrayList<>();

        for (AccountGroup group : allGroups) {
            String groupId = group.getObjectId();
            Integer visibleCount = visibleAccountCounts.get(groupId);

            // 如果该组有可见账户，或者还没有加载账户数据（null），则显示该组
            if (visibleCount == null || visibleCount > 0) {
                filteredGroups.add(group);
                Log.d(TAG, "✅ 显示账户组: " + group.getName() + ", 可见账户: " + visibleCount);
            } else {
                Log.d(TAG, "❌ 隐藏账户组: " + group.getName() + ", 可见账户: 0");
            }
        }

        adapter.setGroups(filteredGroups);
    }

    /** ================== 默认“无账户”按钮 ================== **/
    private void setupNoAccountOption() {
        binding.layoutNoAccount.setOnClickListener(v -> {
            selectedAccount = null;
            dismiss();
            if (listener != null) listener.onChoose(null, null, "无账户");
        });
    }

    /** ================== 拉取云端数据 ================== **/
    private void loadAccountGroupsFromCloud(){
        viewModel.syncFromCloud();
        BmobUser user = BmobUser.getCurrentUser();
        if(user != null){
            viewModel.loadAccountGroups(user.getObjectId());
        }
    }

    /** ===================== 回调回 AddBillActivity ===================== **/
    public interface OnAccountChooseListener {
        void onChoose(Account account, String iconUrl, String accountName);
    }
    private OnAccountChooseListener listener;
    public void setOnAccountChooseListener(OnAccountChooseListener l) { this.listener = l; }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_bottom_sheet));

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.65);
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
}