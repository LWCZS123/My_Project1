package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.FragmentAddCategoryBudgetBinding;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class AddCategoryBudgetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AddCategoryBudgetFrag";

    private static final String ARG_AMOUNT = "arg_amount";
    private static final String ARG_PERIOD = "arg_period";
    private static final String ARG_TARGET_ID = "arg_target_id";
    private static final String ARG_BUDGET_ID = "arg_budget_id";
    private static final String ARG_IS_EDIT = "arg_is_edit";
    private static final String ARG_SIMPLIFIED = "arg_simplified";
    private static final String ARG_CAT_NAME = "arg_cat_name";
    private static final String ARG_CAT_ICON = "arg_cat_icon";

    private FragmentAddCategoryBudgetBinding binding;
    private BudgetViewModel budgetVm;

    private int selectedPeriod = Budget.PERIOD_MONTH;
    private boolean isEditMode = false;
    private boolean isSimplifiedMode = false;
    private int editBudgetId = -1;

    private String selectedCatCloudId = null;
    private String selectedCatName = null;
    private String selectedCatIconUri = null;

    public static AddCategoryBudgetFragment newInstance(Budget budget, boolean simplified) {
        AddCategoryBudgetFragment fragment = new AddCategoryBudgetFragment();
        Bundle args = new Bundle();
        if (budget != null) {
            args.putDouble(ARG_AMOUNT, budget.getAmount());
            args.putInt(ARG_PERIOD, budget.getPeriod());
            args.putString(ARG_TARGET_ID, budget.getTargetId());
            args.putInt(ARG_BUDGET_ID, budget.getId());
            args.putBoolean(ARG_IS_EDIT, true);
            args.putBoolean(ARG_SIMPLIFIED, simplified);
            args.putString(ARG_CAT_NAME, budget.getCategoryName());
            args.putString(ARG_CAT_ICON, budget.getCategoryIconUrl());
        } else {
            args.putBoolean(ARG_IS_EDIT, false);
            args.putBoolean(ARG_SIMPLIFIED, false);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public static AddCategoryBudgetFragment newInstance(Budget budget) {
        return newInstance(budget, false);
    }

    // ---------- BottomSheet 圆角 + 高度处理 (参考 AddAccountFragment) ----------
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet =
                    dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                // 1. 设置自定义圆角背景 (必须设置到 design_bottom_sheet 才能消除白底)
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1)
                );

                // 3. 设置行为：禁止折叠并展开
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddCategoryBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        budgetVm = new ViewModelProvider(requireActivity()).get(BudgetViewModel.class);

        budgetVm.refreshRemainingAllocationFromCurrentTotal();
        restoreArgs();
        initCategorySelection();
        initAmountInput();
        initSmartAllocation();
        initConfirmBtn();
        initClearBtn();
        observeRemainingAllocation();
        updateSelectedHeader();
    }

    private void initClearBtn() {
        binding.ivClear.setOnClickListener(v -> binding.etAmount.setText(""));
    }

    private void restoreArgs() {
        Bundle args = getArguments();
        if (args != null) {
            isEditMode = args.getBoolean(ARG_IS_EDIT, false);
            isSimplifiedMode = args.getBoolean(ARG_SIMPLIFIED, false);
            if (isEditMode) {
                editBudgetId = args.getInt(ARG_BUDGET_ID, -1);
                selectedCatCloudId = args.getString(ARG_TARGET_ID);
                selectedPeriod = args.getInt(ARG_PERIOD, Budget.PERIOD_MONTH);
                selectedCatName = args.getString(ARG_CAT_NAME);
                selectedCatIconUri = args.getString(ARG_CAT_ICON);
                
                double amount = args.getDouble(ARG_AMOUNT, 0);
                binding.etAmount.setText(String.format(Locale.getDefault(), "%.2f", amount));
                binding.etAmount.setSelection(binding.etAmount.getText().length());
                
                if (isSimplifiedMode) {
                    binding.tvTitle.setText("编辑分类预算");
                    binding.cardSelectedCategory.setClickable(false);
                    binding.btnSmartAllocate.setVisibility(View.GONE);
                    binding.btnConfirm.setText("确 认");
                }
            }
        }
    }

    private void initCategorySelection() {
        binding.cardSelectedCategory.setOnClickListener(v -> showCategorySelectPopup());
    }

    private void showCategorySelectPopup() {
        CategorySelectFragment popup = new CategorySelectFragment();
        popup.setOnCategorySelectedListener(item -> {
            selectedCatCloudId = item.cloudId;
            selectedCatName = item.name;
            selectedCatIconUri = item.iconUri;
            updateSelectedHeader();
            updateRemainingHintFromInput();
        });
        popup.show(getChildFragmentManager(), "category_select");
    }

    private void initAmountInput() {
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                binding.ivClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                updateRemainingHintFromInput();
            }
        });
    }

    private void initSmartAllocation() {
        binding.btnSmartAllocate.setOnClickListener(v -> {
            if (selectedCatCloudId == null) {
                Toast.makeText(requireContext(), "请先选择分类", Toast.LENGTH_SHORT).show();
                return;
            }
            
            AppExecutors.get().diskIO().execute(() -> {
                double spent = budgetVm.getSpentByCategorySync(selectedCatCloudId);
                AppExecutors.get().mainThread().execute(() -> {
                    if (spent > 0) {
                        binding.etAmount.setText(String.format(Locale.getDefault(), "%.2f", spent));
                        Toast.makeText(requireContext(), "已根据消费历史智能分配", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "暂无消费历史，建议手动设置", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    private void initConfirmBtn() {
        binding.btnConfirm.setOnClickListener(v -> onConfirm());
    }

    private void observeRemainingAllocation() {
        budgetVm.getRemainingAllocation().observe(getViewLifecycleOwner(), remaining -> {
            updateRemainingHintFromInput();
        });
        
        // 同步周期与总预算一致
        budgetVm.getTotalBudget().observe(getViewLifecycleOwner(), total -> {
            if (total != null) {
                selectedPeriod = total.getPeriod();
            }
        });
    }

    private void updateRemainingHint(double remaining) {
        if (remaining >= 0) {
            binding.tvRemainingHint.setText(String.format(Locale.getDefault(), "未分类预算 ¥%.2f", remaining));
            binding.tvRemainingHint.setTextColor(Color.parseColor("#999999"));
            binding.llOverWarning.setVisibility(View.GONE);
        } else {
            binding.tvRemainingHint.setText(String.format(Locale.getDefault(), "已超分配 ¥%.2f", Math.abs(remaining)));
            binding.tvRemainingHint.setTextColor(Color.parseColor("#FF5252"));
        }
    }

    private void updateRemainingHintFromInput() {
        String input = binding.etAmount.getText().toString();
        double inputVal = 0;
        try {
            inputVal = Double.parseDouble(input);
        } catch (NumberFormatException ignored) {}

        Double currentRemaining = budgetVm.getRemainingAllocation().getValue();
        double baseRemaining = currentRemaining != null ? currentRemaining : 0;
        
        if (isEditMode) {
            Bundle args = getArguments();
            if (args != null) {
                double originalAmount = args.getDouble(ARG_AMOUNT, 0);
                baseRemaining += originalAmount;
            }
        }

        double newRemaining = baseRemaining - inputVal;
        updateRemainingHint(newRemaining);
        updateOverWarning(newRemaining);
    }

    private void updateOverWarning(double newRemaining) {
        if (newRemaining < 0) {
            binding.llOverWarning.setVisibility(View.VISIBLE);
            binding.tvOverWarning.setText(String.format(Locale.getDefault(), "超过总预算 ¥%.2f", Math.abs(newRemaining)));
        } else {
            binding.llOverWarning.setVisibility(View.GONE);
        }
    }

    private void updateSelectedHeader() {
        if (selectedCatCloudId == null) {
            binding.tvSelectedName.setText("请选择分类");
            binding.ivSelectedIcon.setImageResource(R.drawable.ic_category_default);
            return;
        }

        binding.tvSelectedName.setText(selectedCatName);
        loadIcon(selectedCatIconUri, binding.ivSelectedIcon);
    }

    private void loadIcon(String uri, ImageView iv) {
        if (uri == null || uri.isEmpty()) {
            iv.setImageResource(R.drawable.ic_category_default);
            return;
        }
        try {
            int resId = Integer.parseInt(uri);
            iv.setImageResource(resId);
        } catch (NumberFormatException e) {
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_category_default)
                    .error(R.drawable.ic_category_default)
                    .into(iv);
        }
    }

    private void onConfirm() {
        String amountStr = binding.etAmount.getText().toString();
        if (amountStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入金额", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCatCloudId == null) {
            Toast.makeText(requireContext(), "请选择分类", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(amountStr);
        if (isEditMode) {
            Budget b = new Budget();
            b.setId(editBudgetId);
            b.setTargetId(selectedCatCloudId);
            budgetVm.updateCategoryBudget(b, amount, selectedPeriod);
        } else {
            budgetVm.addCategoryBudget(selectedCatCloudId, amount, selectedPeriod, selectedCatName, selectedCatIconUri);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
