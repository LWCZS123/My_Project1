package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.SearchFilter;
import com.example.my_project1.databinding.FragmentSearchFilterBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * SearchFilterBottomSheet - 搜索筛选底部弹窗 (截图风格重构版)
 */
public class SearchFilterBottomSheet extends BottomSheetDialogFragment {

    private FragmentSearchFilterBottomSheetBinding binding;
    private SearchFilter filter;
    private OnFilterConfirmListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());

    public interface OnFilterConfirmListener {
        void onFilterConfirm(SearchFilter filter);
    }

    public static SearchFilterBottomSheet newInstance(SearchFilter currentFilter) {
        SearchFilterBottomSheet fragment = new SearchFilterBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable("filter", currentFilter);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchFilterBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        filter = (SearchFilter) getArguments().getSerializable("filter");
        if (filter == null) filter = new SearchFilter();

        initViews();
        setupListeners();
    }

    private void initViews() {
        // Init Type
        updateTypeUI(filter.getBillType());

        // Init Dates
        updateDateDisplay();

        // Init Switches
        binding.switchBudget.setChecked(filter.getIncludeBudget() != null && filter.getIncludeBudget());
        binding.switchExclude.setChecked(filter.getIncludeIncomeExpense() != null && filter.getIncludeIncomeExpense());

        // Init Labels
        if (filter.getCategoryId() != null && filter.getCategoryName() != null) {
            binding.tvSelectedCategory.setText(filter.getCategoryName() + " >"); 
            binding.tvSelectedCategory.setTextColor(Color.parseColor("#3B82F6"));
        } else {
            binding.tvSelectedCategory.setText("请选择分类 (单选) >");
            binding.tvSelectedCategory.setTextColor(Color.parseColor("#94A3B8"));
        }

        if (filter.getAccountIds() != null && !filter.getAccountIds().isEmpty()) {
            String summary = filter.getAccountSummary() != null ? filter.getAccountSummary() : "已选择 >";
            binding.tvSelectedAccount.setText(summary + " >");
            binding.tvSelectedAccount.setTextColor(Color.parseColor("#3B82F6"));
        } else {
            binding.tvSelectedAccount.setText("请选择账户 >");
            binding.tvSelectedAccount.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    private void setupListeners() {
        // Date Presets
        binding.btnYear.setOnClickListener(v -> selectDatePreset(1));
        binding.btnWeek.setOnClickListener(v -> selectDatePreset(2));
        binding.btnMonth.setOnClickListener(v -> selectDatePreset(3));

        // Date Range
        binding.tvStartDate.setOnClickListener(v -> showCustomDatePicker(true));
        binding.tvEndDate.setOnClickListener(v -> showCustomDatePicker(false));

        // Type Presets
        binding.btnTypeAll.setOnClickListener(v -> { filter.setBillType(-1); updateTypeUI(-1); });
        binding.btnTypeExpense.setOnClickListener(v -> { filter.setBillType(0); updateTypeUI(0); });
        binding.btnTypeIncome.setOnClickListener(v -> { filter.setBillType(1); updateTypeUI(1); });

        // Selectors
        binding.btnSelectBook.setOnClickListener(v -> { /* Select Book */ });
        binding.btnSelectCategory.setOnClickListener(v -> showCategorySelector());
        binding.btnSelectAccount.setOnClickListener(v -> showAccountSelector());

        binding.btnReset.setOnClickListener(v -> {
            filter.clear();
            initViews();
            
            // 修复重置 bug：手动恢复文字和颜色
            binding.tvSelectedCategory.setText("请选择分类 (单选) >");
            binding.tvSelectedCategory.setTextColor(Color.parseColor("#94A3B8"));
            binding.tvSelectedAccount.setText("请选择账户 >");
            binding.tvSelectedAccount.setTextColor(Color.parseColor("#94A3B8"));
            
            // 恢复日期预设 UI
            updateDatePresetUI(0);
        });

        binding.btnConfirm.setOnClickListener(v -> {
            // Type
            if (binding.btnTypeExpense.getCurrentTextColor() == Color.parseColor("#3B82F6")) filter.setBillType(0);
            else if (binding.btnTypeIncome.getCurrentTextColor() == Color.parseColor("#3B82F6")) filter.setBillType(1);
            else filter.setBillType(-1);

            // Switches
            filter.setIncludeBudget(binding.switchBudget.isChecked());
            filter.setIncludeIncomeExpense(binding.switchExclude.isChecked());

            if (listener != null) listener.onFilterConfirm(filter);
            dismiss();
        });
    }

    private void showCategorySelector() {
        CategorySelectorBottomSheet fragment = new CategorySelectorBottomSheet();
        fragment.setOnCategorySelectedListener(item -> {
            filter.setCategoryId(item.getId());
            filter.setCategoryName(item.getName());
            binding.tvSelectedCategory.setText(item.getName() + " >");
            binding.tvSelectedCategory.setTextColor(Color.parseColor("#3B82F6"));
        });
        fragment.show(getChildFragmentManager(), "CategorySelector");
    }

    private void showAccountSelector() {
        MultiAccountSelectBottomSheet fragment = new MultiAccountSelectBottomSheet();
        fragment.setInitialSelectedIds(filter.getAccountIds());
        fragment.setOnAccountsSelectedListener(accounts -> {
            if (accounts != null && !accounts.isEmpty()) {
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                for (com.example.my_project1.data.model.account.Account a : accounts) {
                    ids.add(a.getObjectId());
                }
                filter.setAccountIds(ids);
                
                String summary;
                if (accounts.size() == 1) {
                    summary = accounts.get(0).getName();
                } else {
                    summary = "已选" + accounts.size() + "个账户";
                }
                filter.setAccountSummary(summary);
                binding.tvSelectedAccount.setText(summary + " >");
                binding.tvSelectedAccount.setTextColor(Color.parseColor("#3B82F6"));
            } else {
                filter.setAccountIds(new java.util.ArrayList<>());
                filter.setAccountSummary(null);
                binding.tvSelectedAccount.setText("请选择账户 >");
                binding.tvSelectedAccount.setTextColor(Color.parseColor("#94A3B8"));
            }
        });
        fragment.show(getChildFragmentManager(), "AccountSelector");
    }

    private void selectDatePreset(int type) {
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        if (type == 1) { // Year
            cal.add(Calendar.YEAR, -1);
        } else if (type == 2) { // Week
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        } else if (type == 3) { // Month
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }
        filter.setStartDate(cal.getTime());
        filter.setEndDate(end);
        
        updateDateDisplay();
        updateDatePresetUI(type);
    }

    private void updateDatePresetUI(int selected) {
        binding.btnYear.setBackgroundResource(selected == 1 ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        binding.btnYear.setTextColor(selected == 1 ? Color.parseColor("#3B82F6") : Color.parseColor("#64748B"));
        
        binding.btnWeek.setBackgroundResource(selected == 2 ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        binding.btnWeek.setTextColor(selected == 2 ? Color.parseColor("#3B82F6") : Color.parseColor("#64748B"));
        
        binding.btnMonth.setBackgroundResource(selected == 3 ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        binding.btnMonth.setTextColor(selected == 3 ? Color.parseColor("#3B82F6") : Color.parseColor("#64748B"));
    }

    private void updateTypeUI(int type) {
        binding.btnTypeAll.setBackgroundResource(type == -1 ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        binding.btnTypeAll.setTextColor(type == -1 ? Color.parseColor("#3B82F6") : Color.parseColor("#64748B"));
        
        binding.btnTypeExpense.setBackgroundResource(type == 0 ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        binding.btnTypeExpense.setTextColor(type == 0 ? Color.parseColor("#3B82F6") : Color.parseColor("#64748B"));
        
        binding.btnTypeIncome.setBackgroundResource(type == 1 ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        binding.btnTypeIncome.setTextColor(type == 1 ? Color.parseColor("#3B82F6") : Color.parseColor("#64748B"));
    }

    private void showCustomDatePicker(boolean isStart) {
        Calendar initial = Calendar.getInstance();
        Date current = isStart ? filter.getStartDate() : filter.getEndDate();
        if (current != null) initial.setTime(current);

        CustomDateTimePickerFragment.show(getChildFragmentManager(), initial, calendar -> {
            if (isStart) filter.setStartDate(calendar.getTime());
            else filter.setEndDate(calendar.getTime());
            updateDateDisplay();
            updateDatePresetUI(0); // Clear presets
        });
    }

    private void updateDateDisplay() {
        binding.tvStartDate.setText(filter.getStartDate() != null ? dateFormat.format(filter.getStartDate()) : "开始时间");
        binding.tvEndDate.setText(filter.getEndDate() != null ? dateFormat.format(filter.getEndDate()) : "结束时间");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior behavior = BottomSheetBehavior.from(bottomSheet);
                
                // 固定高度为屏幕的 0.75
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.75);
                bottomSheet.setLayoutParams(layoutParams);

                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    public void setOnFilterConfirmListener(OnFilterConfirmListener listener) {
        this.listener = listener;
    }
}
