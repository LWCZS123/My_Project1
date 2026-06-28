package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.FragmentAddBudgetBinding;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * AddBudgetFragment — BottomSheetDialogFragment
 *
 * 模式：
 *  - 新增（existingBudget = null）：先做重复校验，已存在则 Toast 提示，不写库。
 *  - 编辑（existingBudget != null）：跳过重复校验，直接更新。
 *
 * Tab 切换：月预算 / 年预算，与 BudgetViewModel.getBudgetType() 保持同步。
 */
public class AddBudgetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AddBudgetFragment";

    private static final String ARG_AMOUNT      = "arg_amount";
    private static final String ARG_BUDGET_TYPE = "arg_budget_type";
    private static final String ARG_IS_EDIT     = "arg_is_edit";

    private FragmentAddBudgetBinding binding;
    private BudgetViewModel          vm;

    /** 当前选中周期类型，默认跟随 ViewModel 当前 Tab */
    private String selectedBudgetType = Budget.TYPE_MONTH;
    /** 是否编辑模式 */
    private boolean isEditMode = false;

    // ════════════════════════════════════════════════════════
    //  工厂方法
    // ════════════════════════════════════════════════════════

    /**
     * @param existingBudget 已有总预算对象（编辑），null 表示新增
     */
    public static AddBudgetFragment newInstance(@Nullable Budget existingBudget) {
        AddBudgetFragment f = new AddBudgetFragment();
        Bundle args = new Bundle();
        if (existingBudget != null) {
            args.putDouble(ARG_AMOUNT, existingBudget.getAmount());
            args.putString(ARG_BUDGET_TYPE,
                    existingBudget.getBudgetType() != null
                            ? existingBudget.getBudgetType()
                            : Budget.TYPE_MONTH);
            args.putBoolean(ARG_IS_EDIT, true);
        } else {
            args.putBoolean(ARG_IS_EDIT, false);
        }
        f.setArguments(args);
        return f;
    }

    // ════════════════════════════════════════════════════════
    //  生命周期
    // ════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(BudgetViewModel.class);

        // 新增模式默认 Tab 与 Activity 当前卡片 Tab 保持一致
        selectedBudgetType = vm.getBudgetType();

        restoreArgs();
        initTabs();
        initConfirmBtn();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ════════════════════════════════════════════════════════
    //  BottomSheet 展开配置
    // ════════════════════════════════════════════════════════

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                ViewGroup.LayoutParams lp = sheet.getLayoutParams();
                lp.height = (int) (requireContext().getResources()
                        .getDisplayMetrics().heightPixels * 0.85);
                sheet.setLayoutParams(lp);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    // ════════════════════════════════════════════════════════
    //  初始化
    // ════════════════════════════════════════════════════════

    private void restoreArgs() {
        Bundle args = getArguments();
        if (args == null) return;
        isEditMode = args.getBoolean(ARG_IS_EDIT, false);
        if (isEditMode) {
            double amount = args.getDouble(ARG_AMOUNT, 0);
            if (amount > 0) binding.etAmount.setText(String.valueOf(amount));
            selectedBudgetType = args.getString(ARG_BUDGET_TYPE, Budget.TYPE_MONTH);
        }
    }

    private void initTabs() {
        refreshTabStyle();
        binding.tabMonth.setOnClickListener(v -> {
            selectedBudgetType = Budget.TYPE_MONTH;
            refreshTabStyle();
        });
        binding.tabYear.setOnClickListener(v -> {
            selectedBudgetType = Budget.TYPE_YEAR;
            refreshTabStyle();
        });
    }

    private void refreshTabStyle() {
        boolean isMonth = Budget.TYPE_MONTH.equals(selectedBudgetType);
        applyTab(binding.tabMonth, isMonth);
        applyTab(binding.tabYear, !isMonth);
    }

    private void applyTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected1);
            tab.setTextColor(getResources().getColor(R.color.black, null));
        } else {
            tab.setBackground(null);
            tab.setTextColor(0xFF888888);
        }
    }

    private void initConfirmBtn() {
        binding.btnConfirm.setOnClickListener(v -> onConfirm());
    }

    // ════════════════════════════════════════════════════════
    //  确认逻辑
    // ════════════════════════════════════════════════════════

    private void onConfirm() {
        String amtStr = binding.etAmount.getText().toString().trim();

        if (TextUtils.isEmpty(amtStr)) {
            Toast.makeText(requireContext(), "请输入预算金额", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "金额格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(requireContext(), "预算金额须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isEditMode) {
            // 编辑模式：直接保存，跳过重复校验
            doSave(amount);
        } else {
            // 新增模式：先做重复性校验
            vm.checkDuplicate(selectedBudgetType, duplicateMsg -> {
                if (duplicateMsg != null) {
                    // 已存在同年/月预算 → 提示，不写库
                    Toast.makeText(requireContext(), duplicateMsg, Toast.LENGTH_LONG).show();
                } else {
                    doSave(amount);
                }
            });
        }
    }

    /** 实际写入 ViewModel */
    private void doSave(double amount) {
        vm.saveTotalBudget(amount, selectedBudgetType);
        Toast.makeText(requireContext(), "总预算已保存", Toast.LENGTH_SHORT).show();
        dismiss();
    }
}