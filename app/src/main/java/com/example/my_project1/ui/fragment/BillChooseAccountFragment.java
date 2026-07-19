package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private String currentSearchKeyword = "";

    // 保存所有获取到的原始数据
    private List<AccountGroup> originalGroups = new ArrayList<>();
    private List<Account> originalAccounts = new ArrayList<>();

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
        setupSearch();

        return binding.getRoot();
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchKeyword = s.toString().trim();
                processAndFilterData(originalGroups, originalAccounts);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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

            @Override
            public void onAccountDelete(Account account) {}

            @Override
            public void onAccountHide(Account account) {}

            @Override
            public void onAccountArchive(Account account) {}

            @Override
            public void onAccountEdit(Account account) {}

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
        androidx.lifecycle.MediatorLiveData<CombinedData> combinedLiveData = new androidx.lifecycle.MediatorLiveData<>();

        combinedLiveData.addSource(viewModel.getAccountGroups(), groups -> {
            CombinedData current = combinedLiveData.getValue();
            if (current == null) current = new CombinedData();
            current.groups = groups;
            combinedLiveData.setValue(current);
        });

        combinedLiveData.addSource(viewModel.getAllAccounts(), accounts -> {
            CombinedData current = combinedLiveData.getValue();
            if (current == null) current = new CombinedData();
            current.accounts = accounts;
            combinedLiveData.setValue(current);
        });

        combinedLiveData.observe(getViewLifecycleOwner(), data -> {
            if (data.groups != null && data.accounts != null) {
                processAndFilterData(data.groups, data.accounts);
            }
        });
    }

    private static class CombinedData {
        List<AccountGroup> groups;
        List<Account> accounts;
    }

    private void processAndFilterData(List<AccountGroup> groups, List<Account> allAccounts) {
        this.originalGroups = groups;
        this.originalAccounts = allAccounts;

        List<AccountGroup> displayGroups = new java.util.ArrayList<>();
        Map<String, List<Account>> groupToAccountsMap = new HashMap<>();

        // 1. 处理真实的账户组
        for (AccountGroup group : groups) {
            List<Account> activeAccounts = new java.util.ArrayList<>();
            for (Account acc : allAccounts) {
                if (group.getObjectId().equals(acc.getGroupId())) {
                    // 过滤逻辑
                    if (acc.getSyncState() == SyncState.TO_DELETE) continue;
                    if (!acc.isCanBeSelected()) continue;
                    if (excludeAccountId != null && acc.getObjectId() != null && acc.getObjectId().equals(excludeAccountId)) continue;
                    
                    // 搜索过滤
                    if (!currentSearchKeyword.isEmpty()) {
                        if (!acc.getName().toLowerCase().contains(currentSearchKeyword.toLowerCase())) {
                            continue;
                        }
                    }

                    activeAccounts.add(acc);
                }
            }
            if (!activeAccounts.isEmpty()) {
                displayGroups.add(group);
                groupToAccountsMap.put(group.getObjectId(), activeAccounts);
            }
        }

        // 2. 处理虚拟账户组 (按大类)
        String[] categories = {"资金账户", "信用账户", "充值账户"};
        for (String category : categories) {
            List<Account> activeAccounts = new java.util.ArrayList<>();
            for (Account acc : allAccounts) {
                if ((acc.getGroupId() == null || acc.getGroupId().isEmpty()) && category.equals(acc.getCategory())) {
                    if (acc.getSyncState() == SyncState.TO_DELETE) continue;
                    if (!acc.isCanBeSelected()) continue;
                    if (excludeAccountId != null && acc.getObjectId() != null && acc.getObjectId().equals(excludeAccountId)) continue;

                    // 搜索过滤
                    if (!currentSearchKeyword.isEmpty()) {
                        if (!acc.getName().toLowerCase().contains(currentSearchKeyword.toLowerCase())) {
                            continue;
                        }
                    }

                    activeAccounts.add(acc);
                }
            }
            if (!activeAccounts.isEmpty()) {
                AccountGroup virtualGroup = new AccountGroup();
                virtualGroup.setObjectId("CATEGORY_" + category);
                virtualGroup.setName(category);
                virtualGroup.setAccountCount(activeAccounts.size());
                
                displayGroups.add(virtualGroup);
                groupToAccountsMap.put(virtualGroup.getObjectId(), activeAccounts);
            }
        }

        adapter.setGroups(displayGroups);
        for (Map.Entry<String, List<Account>> entry : groupToAccountsMap.entrySet()) {
            adapter.updateAccountsForExpandedGroup(entry.getKey(), entry.getValue());
        }
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
                        R.drawable.bg_bottom_sheet1));

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