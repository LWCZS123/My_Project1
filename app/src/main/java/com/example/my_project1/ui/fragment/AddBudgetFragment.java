package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

import java.util.Calendar;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * AddBudgetFragment — Elegant BottomSheet
 */
public class AddBudgetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AddBudgetFragment";

    private static final String ARG_AMOUNT      = "arg_amount";
    private static final String ARG_BUDGET_TYPE = "arg_budget_type";
    private static final String ARG_IS_EDIT     = "arg_is_edit";

    private FragmentAddBudgetBinding binding;
    private BudgetViewModel          vm;

    private String selectedBudgetType = Budget.TYPE_MONTH;
    private boolean isEditMode = false;
    
    private int targetYear, targetMonth;

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

        restoreArgs();
        initUI();
        loadSuggestion();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    private void restoreArgs() {
        Bundle args = getArguments();
        Calendar cal = Calendar.getInstance();
        targetYear = cal.get(Calendar.YEAR);
        targetMonth = cal.get(Calendar.MONTH) + 1;

        if (args == null) return;
        isEditMode = args.getBoolean(ARG_IS_EDIT, false);
        if (isEditMode) {
            double amount = args.getDouble(ARG_AMOUNT, 0);
            if (amount > 0) binding.etAmount.setText(String.format(Locale.getDefault(), "%.2f", amount));
            selectedBudgetType = args.getString(ARG_BUDGET_TYPE, Budget.TYPE_MONTH);
        } else {
            selectedBudgetType = vm.getBudgetType();
            targetYear = vm.getCurrentYear();
            targetMonth = vm.getCurrentMonth();
        }
    }

    private void initUI() {
        // Period Selection
        if (Budget.TYPE_YEAR.equals(selectedBudgetType)) {
            binding.rbYear.setChecked(true);
        } else if (Budget.TYPE_WEEK.equals(selectedBudgetType)) {
            binding.rbWeek.setChecked(true);
        } else {
            binding.rbMonth.setChecked(true);
        }

        binding.rgPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_week) {
                selectedBudgetType = Budget.TYPE_WEEK;
            } else if (checkedId == R.id.rb_month) {
                selectedBudgetType = Budget.TYPE_MONTH;
            } else if (checkedId == R.id.rb_year) {
                selectedBudgetType = Budget.TYPE_YEAR;
            }
            updateDateDisplayText();
            binding.switchCarryOver.setChecked(false); // Reset switch when type changes
        });

        // Date Selector
        updateDateDisplayText();
        binding.llDateSelector.setOnClickListener(v -> showDateSelector());

        // Carry Over Switch
        binding.switchCarryOver.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                vm.getPreviousPeriodBudget(selectedBudgetType, targetYear, targetMonth, prev -> {
                    if (prev != null) {
                        setAmount(String.format(Locale.getDefault(), "%.2f", prev.getAmount()));
                        Toast.makeText(requireContext(), "已沿用上期预算 ¥" + prev.getAmount(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "未找到上期预算记录", Toast.LENGTH_SHORT).show();
                        binding.switchCarryOver.setChecked(false);
                    }
                });
            }
        });

        // Quick Amount Buttons
        binding.btn3000.setOnClickListener(v -> setAmount("3000"));
        binding.btn5000.setOnClickListener(v -> setAmount("5000"));
        binding.btn8000.setOnClickListener(v -> setAmount("8000"));
        binding.btn10000.setOnClickListener(v -> setAmount("10000"));

        // Confirm Button
        binding.btnConfirm.setOnClickListener(v -> onConfirm());
    }

    private void updateDateDisplayText() {
        if (Budget.TYPE_YEAR.equals(selectedBudgetType)) {
            binding.tvSelectedDate.setText(String.format(Locale.getDefault(), "%d年", targetYear));
        } else {
            binding.tvSelectedDate.setText(String.format(Locale.getDefault(), "%d年%d月", targetYear, targetMonth));
        }
    }

    private void showDateSelector() {
        BudgetDateSelectorFragment selector = BudgetDateSelectorFragment.newInstance(
                selectedBudgetType, targetYear, targetMonth, (type, year, month) -> {
                    this.targetYear = year;
                    this.targetMonth = month;
                    updateDateDisplayText();
                    binding.switchCarryOver.setChecked(false);
                }
        );
        selector.show(getChildFragmentManager(), "DateSelector");
    }

    private void setAmount(String amount) {
        binding.etAmount.setText(amount);
        binding.etAmount.setSelection(amount.length());
    }

    private void loadSuggestion() {
        vm.getMonthlyStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null && stats.totalExpense > 0) {
                double lastMonthExp = stats.totalExpense;
                double suggestMin = Math.floor(lastMonthExp / 100) * 100;
                double suggestMax = Math.ceil(lastMonthExp * 1.1 / 100) * 100;
                binding.tvSuggestionContent.setText(String.format(Locale.getDefault(),
                        "根据近期支出 ¥%.2f，建议预算 ¥%.0f ~ ¥%.0f", 
                        lastMonthExp, suggestMin, suggestMax));
            }
        });
    }

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

        final double finalAmount = Math.round(amount * 100.0) / 100.0;

        if (isEditMode) {
            doSave(finalAmount);
        } else {
            vm.checkDuplicate(selectedBudgetType, targetYear, targetMonth, duplicateMsg -> {
                if (duplicateMsg != null) {
                    Toast.makeText(requireContext(), duplicateMsg, Toast.LENGTH_LONG).show();
                } else {
                    doSave(finalAmount);
                }
            });
        }
    }

    private void doSave(double amount) {
        vm.saveTotalBudget(amount, selectedBudgetType, targetYear, targetMonth);
        Toast.makeText(requireContext(), "总预算已保存", Toast.LENGTH_SHORT).show();
        dismiss();
    }
}
