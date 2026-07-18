package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.SearchFilter;
import com.example.my_project1.databinding.FragmentSearchFilterBottomSheetBinding;
import com.example.my_project1.ui.adapter.filter.SelectedAccountAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * SearchFilterBottomSheet - 搜索筛选底部弹窗（使用单选模式 - 修复版）
 * -------------------------------------------------------
 * ✅ 功能:
 * 1. 日期范围筛选 (使用DateTimePickerFragment)
 * 2. 金额范围筛选
 * 3. 备注关键词筛选
 * 4. 账户多选筛选 (使用BillChooseAccountFragment单选，每次选一个添加到列表)
 *
 * ✅ 修复:
 * 1. 确保选中账户显示在界面上
 * 2. 修复updateAccountDisplay方法
 */
public class SearchFilterBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "SearchFilterBottomSheet";

    private FragmentSearchFilterBottomSheetBinding binding;
    private SearchFilter filter;
    private OnFilterConfirmListener listener;

    // ViewModel
    private AccountViewModel accountViewModel;

    // 日期格式
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // 选中的日期
    private Date selectedStartDate;
    private Date selectedEndDate;

    // 🔥 选中的账户列表
    private List<Account> selectedAccounts = new ArrayList<>();

    // 🔥 已选中账户适配器 (瀑布流显示)
    private SelectedAccountAdapter selectedAccountAdapter;

    // ==================== 接口 ====================

    public interface OnFilterConfirmListener {
        void onFilterConfirm(SearchFilter filter);
    }

    // ==================== 静态工厂方法 ====================

    public static SearchFilterBottomSheet newInstance(SearchFilter currentFilter) {
        SearchFilterBottomSheet fragment = new SearchFilterBottomSheet();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    // ==================== 生命周期 ====================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchFilterBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViewModel();
        initFilter();
        initViews();
        setupListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ==================== 初始化 ====================

    /**
     * 初始化ViewModel
     */
    private void initViewModel() {
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
    }

    /**
     * 初始化筛选条件
     */
    private void initFilter() {
        filter = new SearchFilter();

        // 默认设置本月日期范围
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        selectedStartDate = calendar.getTime();

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        selectedEndDate = calendar.getTime();

        filter.setStartDate(selectedStartDate);
        filter.setEndDate(selectedEndDate);
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        // 设置日期显示
        updateDateDisplay();

        // 🔥 初始化已选中账户RecyclerView (瀑布流)
        selectedAccountAdapter = new SelectedAccountAdapter(requireContext(),
                (account, position) -> {
                    // 删除账户
                    selectedAccounts.remove(account);
                    selectedAccountAdapter.removeAccount(account);
                    updateAccountDisplay();

                    Log.d(TAG, "🗑️ 删除账户: " + account.getName());
                    Toast.makeText(requireContext(),
                            "已删除账户: " + account.getName(),
                            Toast.LENGTH_SHORT).show();
                });

        StaggeredGridLayoutManager selectedLayoutManager = new StaggeredGridLayoutManager(
                3, StaggeredGridLayoutManager.HORIZONTAL);
        selectedLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);

        binding.rvAccounts.setLayoutManager(selectedLayoutManager);
        binding.rvAccounts.setAdapter(selectedAccountAdapter);
        binding.rvAccounts.setNestedScrollingEnabled(false);

        // 🔥 初始化时隐藏（因为没有选中的账户）
        binding.rvAccounts.setVisibility(View.GONE);

        Log.d(TAG, "✅ RecyclerView 初始化完成");
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // ==================== 日期选择 ====================

        // 开始日期点击 - 使用DateTimePickerFragment
        binding.tvStartDate.setOnClickListener(v -> showDateTimePicker(true));

        // 结束日期点击
        binding.tvEndDate.setOnClickListener(v -> showDateTimePicker(false));

        // 日历按钮点击
        binding.btnCalendar.setOnClickListener(v -> showDateTimePicker(true));

        // 清除日期
        binding.tvClearDate.setOnClickListener(v -> {
            selectedStartDate = null;
            selectedEndDate = null;
            binding.tvStartDate.setText("");
            binding.tvEndDate.setText("");
            filter.setStartDate(null);
            filter.setEndDate(null);
            Toast.makeText(requireContext(), "已清除日期", Toast.LENGTH_SHORT).show();
        });

        // ==================== 账户选择 ====================

        // 🔥 添加账户按钮 - 显示账户选择弹窗（使用原有的单选模式）
        binding.tvAddAccount.setOnClickListener(v -> showAccountSelectorDialog());

        // ==================== 底部按钮 ====================

        // 重置按钮
        binding.btnReset.setOnClickListener(v -> resetFilter());

        // 确定按钮
        binding.btnConfirm.setOnClickListener(v -> confirmFilter());
    }

    // ==================== 日期选择 ====================

    /**
     * 显示日期时间选择器
     *
     * @param isStartDate true=选择开始日期, false=选择结束日期
     */
    private void showDateTimePicker(boolean isStartDate) {
        DateTimePickerFragment picker = new DateTimePickerFragment();

        picker.setOnDateTimeSelectedListener((timestamp, formattedDateTime) -> {
            Date selectedDate = new Date(timestamp);

            if (isStartDate) {
                selectedStartDate = selectedDate;
                filter.setStartDate(selectedDate);
            } else {
                selectedEndDate = selectedDate;
                filter.setEndDate(selectedDate);
            }

            updateDateDisplay();
            Log.d(TAG, "📅 选择日期: " + formattedDateTime);
        });

        picker.show(getChildFragmentManager(), "date_time_picker");
    }

    /**
     * 更新日期显示
     */
    private void updateDateDisplay() {
        if (selectedStartDate != null) {
            binding.tvStartDate.setText(dateFormat.format(selectedStartDate));
        } else {
            binding.tvStartDate.setText("");
            binding.tvStartDate.setHint("开始日期");
        }

        if (selectedEndDate != null) {
            binding.tvEndDate.setText(dateFormat.format(selectedEndDate));
        } else {
            binding.tvEndDate.setText("");
            binding.tvEndDate.setHint("结束日期");
        }
    }

    // ==================== 账户选择 ====================

    /**
     * 🔥 显示账户选择弹窗（使用原有的单选BillChooseAccountFragment）
     */
    private void showAccountSelectorDialog() {
        Log.d(TAG, "📱 打开账户选择器");

        // 创建原有的单选账户选择器
        BillChooseAccountFragment selectorSheet = new BillChooseAccountFragment();

        // 设置单选回调
        selectorSheet.setOnAccountChooseListener((account, iconUrl, accountName) -> {
            Log.d(TAG, "🎯 账户选择回调触发: " + (account != null ? account.getName() : "null"));

            // 每次选择一个账户
            if (account != null) {
                // 检查是否已经选择过该账户（避免重复）
                if (!containsAccount(selectedAccounts, account.getObjectId())) {
                    // 添加到列表
                    selectedAccounts.add(account);

                    // 🔥 关键：通知适配器添加账户
                    selectedAccountAdapter.addAccount(account);

                    // 更新显示
                    updateAccountDisplay();

                    Log.d(TAG, "✅ 成功添加账户: " + account.getName() + ", 当前数量: " + selectedAccounts.size());
                    Toast.makeText(requireContext(),
                            "已添加账户: " + account.getName(),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            "该账户已添加",
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "⚠️ 账户已存在: " + account.getName());
                }
            } else {
                Log.d(TAG, "⚠️ 账户为null");
            }
        });

        selectorSheet.show(getChildFragmentManager(), "account_selector");
    }

    /**
     * 检查账户是否已选择
     */
    private boolean containsAccount(List<Account> accounts, String accountId) {
        for (Account account : accounts) {
            if (account.getObjectId().equals(accountId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 🔥 更新账户显示（修复版）
     */
    private void updateAccountDisplay() {
        Log.d(TAG, "📊 更新账户显示: " + selectedAccounts.size() + " 个账户");

        if (selectedAccounts.isEmpty()) {
            binding.rvAccounts.setVisibility(View.GONE);
            Log.d(TAG, "   → 隐藏 RecyclerView");
        } else {
            binding.rvAccounts.setVisibility(View.VISIBLE);
            Log.d(TAG, "   → 显示 RecyclerView");

            // 🔥 确保RecyclerView重新布局
            binding.rvAccounts.post(() -> {
                binding.rvAccounts.requestLayout();
                Log.d(TAG, "   → RecyclerView 请求重新布局");
            });
        }
    }

    // ==================== 筛选操作 ====================

    /**
     * 重置筛选条件
     */
    private void resetFilter() {
        // 清空金额
        binding.etMinAmount.setText("");
        binding.etMaxAmount.setText("");

        // 清空备注
        binding.etRemark.setText("");

        // 重置日期为本月
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        selectedStartDate = calendar.getTime();

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        selectedEndDate = calendar.getTime();

        updateDateDisplay();

        // 🔥 清空账户
        selectedAccounts.clear();
        selectedAccountAdapter.clearAll();
        updateAccountDisplay();

        // 清空筛选对象
        filter.clear();
        filter.setStartDate(selectedStartDate);
        filter.setEndDate(selectedEndDate);

        Toast.makeText(requireContext(), "已重置筛选条件", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "🔄 重置筛选条件");
    }

    /**
     * 确认筛选
     */
    private void confirmFilter() {
        // ==================== 金额验证 ====================

        String minAmountStr = binding.etMinAmount.getText().toString().trim();
        String maxAmountStr = binding.etMaxAmount.getText().toString().trim();

        if (!TextUtils.isEmpty(minAmountStr)) {
            try {
                filter.setMinAmount(Double.parseDouble(minAmountStr));
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "最低金额格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            filter.setMinAmount(null);
        }

        if (!TextUtils.isEmpty(maxAmountStr)) {
            try {
                filter.setMaxAmount(Double.parseDouble(maxAmountStr));
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "最高金额格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            filter.setMaxAmount(null);
        }

        // 验证金额范围
        if (filter.getMinAmount() != null && filter.getMaxAmount() != null) {
            if (filter.getMinAmount() > filter.getMaxAmount()) {
                Toast.makeText(requireContext(), "最低金额不能大于最高金额", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // ==================== 日期验证 ====================

        if (selectedStartDate != null && selectedEndDate != null) {
            if (selectedStartDate.after(selectedEndDate)) {
                Toast.makeText(requireContext(), "开始日期不能晚于结束日期", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // ==================== 备注 ====================

        String remark = binding.etRemark.getText().toString().trim();
        if (!TextUtils.isEmpty(remark)) {
            filter.setRemarkKeyword(remark);
        } else {
            filter.setRemarkKeyword(null);
        }

        // 🔥 账户筛选 (支持多账户)
        if (!selectedAccounts.isEmpty()) {
            List<String> accountIds = new ArrayList<>();
            for (Account account : selectedAccounts) {
                accountIds.add(account.getObjectId());
            }
            filter.setAccountIds(accountIds);
            Log.d(TAG, "✅ 设置筛选账户: " + accountIds.size() + " 个");
        } else {
            filter.setAccountIds(new ArrayList<>());
        }

        // 回调监听器
        if (listener != null) {
            listener.onFilterConfirm(filter);
            Log.d(TAG, "✅ 确认筛选: " + filter.toString());
        }

        // 关闭底部弹窗
        dismiss();
    }

    // ==================== 公开方法 ====================

    /**
     * 设置监听器
     */
    public void setOnFilterConfirmListener(OnFilterConfirmListener listener) {
        this.listener = listener;
    }

    /**
     * 设置当前筛选条件
     */
    public void setCurrentFilter(SearchFilter currentFilter) {
        if (currentFilter != null) {
            this.filter = currentFilter;

            // 更新UI显示
            if (currentFilter.getStartDate() != null) {
                selectedStartDate = currentFilter.getStartDate();
            }
            if (currentFilter.getEndDate() != null) {
                selectedEndDate = currentFilter.getEndDate();
            }

            if (binding != null) {
                updateDateDisplay();

                if (currentFilter.getMinAmount() != null) {
                    binding.etMinAmount.setText(String.valueOf(currentFilter.getMinAmount()));
                }
                if (currentFilter.getMaxAmount() != null) {
                    binding.etMaxAmount.setText(String.valueOf(currentFilter.getMaxAmount()));
                }
                if (currentFilter.getRemarkKeyword() != null) {
                    binding.etRemark.setText(currentFilter.getRemarkKeyword());
                }
            }
        }
    }

    // ==================== Dialog配置 ====================

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_bottom_sheet1));

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.75);
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