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
import com.example.my_project1.ui.adapter.account.AccountSubAdapter;
import com.example.my_project1.ui.adapter.account.AssetsGroupAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountUiModel;
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
    private AccountSubAdapter adapter;
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

    /** ================== RecyclerView 显示账户列表 ================== **/
    private void setupRecyclerView() {
        adapter = new AccountSubAdapter();
        adapter.setSwipeEnabled(false); // 选择账户时不需要侧滑

        binding.rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvGroups.setNestedScrollingEnabled(false); // 交给外部 NestedScrollView
        binding.rvGroups.setAdapter(adapter);

        adapter.setOnAccountClickListener(new AccountSubAdapter.OnAccountClickListener() {
            @Override
            public void onAccountClick(Account account) {
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

            @Override public void onAccountDelete(Account account) {}
            @Override public void onAccountHide(Account account) {}
            @Override public void onAccountArchive(Account account) {}
            @Override public void onAccountEdit(Account account) {}
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

        List<AccountUiModel> uiModels = new ArrayList<>();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        
        // 创建 ID 到组名的映射，用于设置副标题
        Map<String, String> groupIdToNameMap = new HashMap<>();
        for (AccountGroup group : groups) {
            groupIdToNameMap.put(group.getObjectId(), group.getName());
        }

        for (Account acc : allAccounts) {
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

            // 设置副标题为 [账户组] 备注 或 账户名
            String groupName = groupIdToNameMap.get(acc.getGroupId());
            if (groupName == null || groupName.isEmpty()) {
                groupName = acc.getCategory(); // 如果没有组，使用大类名
            }
            
            String subtitle = "[" + (groupName != null ? groupName : "未分类") + "] " + 
                    (acc.getRemark() != null && !acc.getRemark().isEmpty() ? acc.getRemark() : acc.getName());

            uiModels.add(new AccountUiModel(
                    acc.getId(),
                    acc.getObjectId(),
                    acc.getName(),
                    subtitle,
                    "¥" + df.format(acc.getBalance()),
                    acc.getBalance() < 0 ? 0xFFFF6B6B : 0xFF333333,
                    "可用额度 ¥" + df.format(acc.getCreditLimit() + acc.getBalance()),
                    acc.isCredit(),
                    acc.getIconUrl(),
                    false, // 选择界面不隐藏金额
                    false, // 选择界面不需要侧滑
                    acc
            ));
        }

        adapter.submitList(uiModels);
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