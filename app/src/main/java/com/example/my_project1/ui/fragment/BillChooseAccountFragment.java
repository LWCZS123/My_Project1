package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentBillChooseAccountBinding;
import com.example.my_project1.ui.adapter.account.AccountSubAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountUiModel;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.annotations.NonNull;

/**
 * 账户选择底部弹窗 - 纯UI选择，不涉及云同步等耗时操作
 * 数据完全依赖于共享的 AccountViewModel，确保响应速度
 */
public class BillChooseAccountFragment extends BottomSheetDialogFragment {

    private static final String ARG_EXCLUDE_ACCOUNT_ID = "exclude_account_id";

    private FragmentBillChooseAccountBinding binding;
    private AccountSubAdapter adapter;
    private AccountViewModel viewModel;

    // 搜索与过滤参数
    private String excludeAccountId = null;
    private String currentSearchKeyword = "";

    // 原始数据缓存
    private List<AccountGroup> originalGroups = new ArrayList<>();
    private List<Account> originalAccounts = new ArrayList<>();
    
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.00");

    public static BillChooseAccountFragment newInstance() {
        return new BillChooseAccountFragment();
    }

    public static BillChooseAccountFragment newInstance(String excludeAccountId) {
        BillChooseAccountFragment fragment = new BillChooseAccountFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXCLUDE_ACCOUNT_ID, excludeAccountId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBillChooseAccountBinding.inflate(inflater, container, false);
        // 使用 requireActivity() 确保与 Activity 共享同一个 ViewModel，利用已加载的数据
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        if (getArguments() != null) {
            excludeAccountId = getArguments().getString(ARG_EXCLUDE_ACCOUNT_ID);
        }

        setupUI();
        observeData();
        
        return binding.getRoot();
    }

    private void setupUI() {
        setupRecyclerView();
        setupNoAccountOption();
        setupSearch();
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchKeyword = s.toString().trim();
                updateList();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new AccountSubAdapter();
        adapter.setSwipeEnabled(false); // 选择模式禁用侧滑

        binding.rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvGroups.setAdapter(adapter);

        adapter.setOnAccountClickListener(new AccountSubAdapter.OnAccountClickListener() {
            @Override
            public void onAccountClick(Account account) {
                // 排除模式检查
                if (excludeAccountId != null && account.getObjectId() != null && account.getObjectId().equals(excludeAccountId)) {
                    SnackbarUtils.showWarning(binding.getRoot(), "不能选择当前账户");
                    return;
                }
                
                // 状态检查
                if (account.getSyncState() == SyncState.TO_DELETE) {
                    SnackbarUtils.showWarning(binding.getRoot(), "该账户已被删除");
                    return;
                }

                if (listener != null) {
                    listener.onChoose(account, account.getIconUrl(), account.getName());
                }
                dismiss();
            }

            @Override public void onAccountDelete(Account account) {}
            @Override public void onAccountHide(Account account) {}
            @Override public void onAccountArchive(Account account) {}
            @Override public void onAccountEdit(Account account) {}
        });
    }

    /**
     * 监听数据变化，实现响应式 UI
     */
    private void observeData() {
        // 观察账户列表
        viewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            this.originalAccounts = accounts != null ? accounts : new ArrayList<>();
            updateList();
        });

        // 观察账户组列表（用于匹配组名）
        viewModel.getAccountGroups().observe(getViewLifecycleOwner(), groups -> {
            this.originalGroups = groups != null ? groups : new ArrayList<>();
            updateList();
        });
    }

    /**
     * 处理数据过滤与转换逻辑
     */
    private void updateList() {
        if (originalAccounts == null) return;

        List<AccountUiModel> uiModels = new ArrayList<>();
        
        // 预处理组名映射
        Map<String, String> groupMap = new HashMap<>();
        for (AccountGroup g : originalGroups) {
            if (g.getObjectId() != null) groupMap.put(g.getObjectId(), g.getName());
        }

        for (Account acc : originalAccounts) {
            // 1. 状态过滤
            if (acc.getSyncState() == SyncState.TO_DELETE) continue;
            if (!acc.isCanBeSelected()) continue;
            if (excludeAccountId != null && acc.getObjectId() != null && acc.getObjectId().equals(excludeAccountId)) continue;

            // 2. 搜索过滤
            if (!currentSearchKeyword.isEmpty()) {
                if (!acc.getName().toLowerCase().contains(currentSearchKeyword.toLowerCase())) {
                    continue;
                }
            }

            // 3. 构建 UI 模型
            String groupName = groupMap.get(acc.getGroupId());
            if (groupName == null) groupName = acc.getCategory();
            if (groupName == null) groupName = "未分类";
            
            String remark = acc.getRemark();
            String subtitle = "[" + groupName + "] " + (remark != null && !remark.isEmpty() ? remark : acc.getName());

            uiModels.add(new AccountUiModel(
                    acc.getId(),
                    acc.getObjectId(),
                    acc.getName(),
                    subtitle,
                    "¥" + balanceFormat.format(acc.getBalance()),
                    acc.getBalance() < 0 ? 0xFFFF6B6B : 0xFF333333,
                    "可用额度 ¥" + balanceFormat.format(acc.getCreditLimit() + acc.getBalance()),
                    acc.isCredit(),
                    acc.getIconUrl(),
                    false, 
                    false,
                    acc
            ));
        }

        // ListAdapter 的 submitList 会在后台线程计算 Diff
        adapter.submitList(uiModels);
    }

    private void setupNoAccountOption() {
        binding.layoutNoAccount.setOnClickListener(v -> {
            if (listener != null) listener.onChoose(null, null, "无账户");
            dismiss();
        });
    }

    public interface OnAccountChooseListener {
        void onChoose(Account account, String iconUrl, String accountName);
    }
    
    private OnAccountChooseListener listener;
    
    public void setOnAccountChooseListener(OnAccountChooseListener l) { this.listener = l; }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }
}
