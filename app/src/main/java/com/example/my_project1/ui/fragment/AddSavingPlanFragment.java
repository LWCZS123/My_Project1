package com.example.my_project1.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.databinding.FragmentAddSavingPlanBinding;
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.viewmodel.saving.SavingViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 添加/编辑存钱计划底栏片段
 */
public class AddSavingPlanFragment extends BottomSheetDialogFragment {

    private static final String ARG_TYPE = "arg_type";
    private static final String ARG_PLAN_ID = "arg_plan_id";
    
    private FragmentAddSavingPlanBinding binding;
    private SavingViewModel viewModel;
    private int type;
    private long editPlanId;
    private SavingPlan loadedPlan;
    
    private Date startDate = new Date();
    private Date endDate = new Date();
    private String selectedIconUrl;
    private String selectedIconBackgroundColor;

    private final ActivityResultLauncher<Intent> iconPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                selectedIconUrl = result.getData().getStringExtra(IconSelectionActivity.EXTRA_ICON_URI);
                selectedIconBackgroundColor = result.getData().getStringExtra(IconSelectionActivity.EXTRA_ICON_BG_COLOR);
                renderSelectedIcon();
            });

    public static AddSavingPlanFragment newInstance(int type) {
        AddSavingPlanFragment fragment = new AddSavingPlanFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    public static AddSavingPlanFragment newInstanceForEdit(long planId) {
        AddSavingPlanFragment fragment = new AddSavingPlanFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAN_ID, planId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            type = args.getInt(ARG_TYPE, 0);
            editPlanId = args.getLong(ARG_PLAN_ID, 0);
        }
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);
        endDate = cal.getTime();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddSavingPlanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SavingViewModel.class);
        
        setupUI();
        setupListeners();
        
        if (editPlanId > 0) {
            viewModel.getPlanById(editPlanId).observe(getViewLifecycleOwner(), this::populate);
        } else {
            renderSelectedIcon();
        }
        
        observe();
        binding.etName.post(() -> showKeyboard(binding.etName));
    }

    private void populate(SavingPlan plan) {
        if (plan == null || loadedPlan != null) return;
        loadedPlan = plan;
        type = plan.getType();
        
        binding.etName.setText(plan.getName());
        startDate = plan.getStartDate();
        if (plan.getEndDate() != null) endDate = plan.getEndDate();
        
        selectedIconUrl = plan.getIconUrl();
        selectedIconBackgroundColor = plan.getIconColor();
        
        binding.etInitialAmount.setText(String.valueOf(plan.getInitialAmount()));
        binding.swTransfer.setChecked(plan.isEnableTransfer());
        
        if (type == SavingPlan.TYPE_FIXED) {
            binding.etMainAmount.setText(String.valueOf(plan.getPeriodAmount()));
            binding.tvCycle.setText(plan.getPeriodType());
            binding.tvDuration.setText(plan.getDuration() + " " + (plan.getPeriodType() != null ? plan.getPeriodType().substring(1) : "周"));
        } else if (type == SavingPlan.TYPE_FLEXIBLE) {
            binding.etTarget.setText(String.valueOf(plan.getTargetAmount()));
        } else {
            binding.etMainAmount.setText(String.valueOf(plan.getFirstPeriodAmount()));
            binding.etIncrement.setText(String.valueOf(plan.getIncrementAmount()));
        }
        
        setupUI(); // 重新刷新显示逻辑
        updateDateText();
        renderSelectedIcon();
    }

    private void setupUI() {
        binding.vLineEndDate.setVisibility(View.GONE);
        binding.llEndDate.setVisibility(View.GONE);
        binding.llCycle.setVisibility(View.GONE);
        binding.vLineCycle.setVisibility(View.GONE);
        binding.llAmountRow.setVisibility(View.GONE);
        binding.vLineAmount.setVisibility(View.GONE);
        binding.llDuration.setVisibility(View.GONE);
        binding.llIncrement.setVisibility(View.GONE);
        binding.llTarget.setVisibility(View.GONE);
        
        // 只有在初始创建且未加载数据时才设置默认值
        if (loadedPlan == null && editPlanId == 0) {
            binding.swTransfer.setChecked(true); // 默认开启
        }

        switch (type) {
            case SavingPlan.TYPE_FIXED:
                binding.tvTitle.setText("定额存钱法");
                binding.llCycle.setVisibility(View.VISIBLE);
                binding.vLineCycle.setVisibility(View.VISIBLE);
                binding.llAmountRow.setVisibility(View.VISIBLE);
                binding.vLineAmount.setVisibility(View.VISIBLE);
                binding.llDuration.setVisibility(View.VISIBLE);
                binding.tvAmountLabel.setText(binding.tvCycle.getText().toString() + "存钱");
                break;
            case SavingPlan.TYPE_FLEXIBLE:
                binding.tvTitle.setText("灵活存钱法");
                binding.vLineEndDate.setVisibility(View.VISIBLE);
                binding.llEndDate.setVisibility(View.VISIBLE);
                binding.llTarget.setVisibility(View.VISIBLE);
                break;
            case SavingPlan.TYPE_52WEEK:
                binding.tvTitle.setText("52周存钱法");
                binding.llAmountRow.setVisibility(View.VISIBLE);
                binding.vLineAmount.setVisibility(View.VISIBLE);
                binding.llIncrement.setVisibility(View.VISIBLE);
                binding.tvAmountLabel.setText("首期金额");
                binding.tvAmountSubLabel.setVisibility(View.VISIBLE);
                break;
            case SavingPlan.TYPE_365DAY:
                binding.tvTitle.setText("365存钱法");
                binding.llAmountRow.setVisibility(View.VISIBLE);
                binding.vLineAmount.setVisibility(View.VISIBLE);
                binding.llIncrement.setVisibility(View.VISIBLE);
                binding.tvAmountLabel.setText("首期金额");
                binding.tvAmountSubLabel.setVisibility(View.VISIBLE);
                break;
        }
        updateDateText();
    }

    private void updateDateText() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        binding.tvStartDate.setText(String.format(Locale.CHINA, "%d-%02d-%02d", 
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)));
        cal.setTime(endDate);
        binding.tvEndDate.setText(String.format(Locale.CHINA, "%d-%02d-%02d", 
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)));
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> save());
        binding.iconPicker.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), IconSelectionActivity.class);
            intent.putExtra(IconSelectionActivity.EXTRA_PICK_ONLY, true);
            intent.putExtra(IconSelectionActivity.EXTRA_TITLE, "选择计划图标");
            intent.putExtra(IconSelectionActivity.EXTRA_ICON_URI, selectedIconUrl);
            intent.putExtra(IconSelectionActivity.EXTRA_ICON_BG_COLOR, selectedIconBackgroundColor);
            iconPickerLauncher.launch(intent);
        });
        
        binding.llStartDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            CustomDateTimePickerFragment.show(getChildFragmentManager(), calendar, selected -> {
                startDate = selected.getTime();
                updateDateText();
            });
        });

        binding.llEndDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            CustomDateTimePickerFragment.show(getChildFragmentManager(), calendar, selected -> {
                endDate = selected.getTime();
                updateDateText();
            });
        });

        binding.llCycle.setOnClickListener(v -> {
            ArrayList<String> cycles = new ArrayList<>(Arrays.asList("每天", "每周", "每月", "每年"));
            SimpleSelectBottomSheet sheet = SimpleSelectBottomSheet.newInstance("选择计划周期", cycles);
            sheet.setOnItemSelectedListener(item -> {
                binding.tvCycle.setText(item);
                binding.tvAmountLabel.setText(item + "存钱");
                String unit = item.substring(1);
                String currentNum = binding.tvDuration.getText().toString().replaceAll("[^0-9]", "");
                if (currentNum.isEmpty()) currentNum = "1";
                binding.tvDuration.setText(currentNum + " " + unit);
            });
            sheet.show(getChildFragmentManager(), "select_cycle");
        });

        binding.llDuration.setOnClickListener(v -> {
            String currentVal = binding.tvDuration.getText().toString();
            DurationInputBottomSheet sheet = DurationInputBottomSheet.newInstance(currentVal);
            sheet.setOnDurationInputListener(val -> {
                if (!TextUtils.isEmpty(val)) {
                    String unit = binding.tvCycle.getText().toString().substring(1);
                    binding.tvDuration.setText(val + " " + unit);
                }
            });
            sheet.show(getChildFragmentManager(), "input_duration");
        });

        binding.llInitialAmount.setOnClickListener(v -> showKeyboard(binding.etInitialAmount));
        binding.llAmountRow.setOnClickListener(v -> showKeyboard(binding.etMainAmount));
        binding.llIncrement.setOnClickListener(v -> showKeyboard(binding.etIncrement));
        binding.llTarget.setOnClickListener(v -> showKeyboard(binding.etTarget));
    }

    private void showKeyboard(View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_FORCED);
    }

    private void save() {
        String name = binding.etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(getContext(), "请输入计划名称", Toast.LENGTH_SHORT).show();
            return;
        }

        SavingPlan plan = (loadedPlan != null) ? loadedPlan : new SavingPlan();
        plan.setName(name);
        plan.setType(type);
        plan.setStartDate(startDate);
        plan.setIconUrl(selectedIconUrl);
        plan.setIconColor(selectedIconBackgroundColor);
        plan.setEnableTransfer(binding.swTransfer.isChecked());

        String initialStr = binding.etInitialAmount.getText().toString();
        double initialAmount = initialStr.isEmpty() ? 0 : Double.parseDouble(initialStr);
        plan.setInitialAmount(initialAmount);
        plan.setCurrentAmount(initialAmount);

        try {
            if (type == SavingPlan.TYPE_FIXED) {
                String amountStr = binding.etMainAmount.getText().toString();
                if (amountStr.isEmpty()) throw new Exception("请输入存钱金额");
                double periodAmount = Double.parseDouble(amountStr);
                plan.setPeriodAmount(periodAmount);
                plan.setPeriodType(binding.tvCycle.getText().toString());
                String durationVal = binding.tvDuration.getText().toString().replaceAll("[^0-9]", "");
                int duration = Integer.parseInt(durationVal);
                plan.setDuration(duration);
                plan.setTargetAmount(periodAmount * duration + initialAmount); 
            } else if (type == SavingPlan.TYPE_FLEXIBLE) {
                String targetStr = binding.etTarget.getText().toString();
                if (targetStr.isEmpty()) throw new Exception("请输入目标金额");
                plan.setTargetAmount(Double.parseDouble(targetStr));
                plan.setEndDate(endDate);
            } else {
                String firstStr = binding.etMainAmount.getText().toString();
                String incStr = binding.etIncrement.getText().toString();
                if (firstStr.isEmpty() || incStr.isEmpty()) throw new Exception("请输入金额配置");
                double first = Double.parseDouble(firstStr);
                double inc = Double.parseDouble(incStr);
                plan.setFirstPeriodAmount(first);
                plan.setIncrementAmount(inc);
                int count = (type == SavingPlan.TYPE_52WEEK) ? 52 : 365;
                plan.setTargetAmount(count / 2.0 * (2.0 * first + (count - 1.0) * inc) + initialAmount);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.savePlan(plan);
    }

    private void observe() {
        viewModel.getOperationState().observe(getViewLifecycleOwner(), state -> {
            if (state.isSuccess()) {
                viewModel.resetOperationState();
                dismiss();
            } else if (state.isError()) {
                Toast.makeText(getContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
                viewModel.resetOperationState();
            }
        });
    }

    private void renderSelectedIcon() {
        if (binding == null) return;
        if (TextUtils.isEmpty(selectedIconUrl)) {
            binding.ivIcon.setImageResource(R.drawable.ic_piggy_bank);
            binding.iconPicker.setBackgroundResource(R.drawable.bg_wish_icon_picker);
        } else {
            ImageLoaderUtils.load(requireContext(), selectedIconUrl, binding.ivIcon,
                    R.drawable.ic_piggy_bank, R.drawable.ic_piggy_bank);
            if (!TextUtils.isEmpty(selectedIconBackgroundColor)) {
                try {
                    GradientDrawable background = new GradientDrawable();
                    background.setShape(GradientDrawable.OVAL);
                    background.setColor(Color.parseColor(selectedIconBackgroundColor));
                    binding.iconPicker.setBackground(background);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            View sheet = ((BottomSheetDialog) getDialog()).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                applySystemBarColors();
                sheet.setBackgroundResource(android.R.color.transparent);
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                sheet.requestLayout();
                sheet.post(() -> applyAdaptiveSheetHeight(sheet));
            }
        }
    }

    private void applySystemBarColors() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        android.view.Window window = getDialog().getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(Color.parseColor("#F4F4F4"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
    }

    private void applyAdaptiveSheetHeight(View sheet) {
        if (binding == null) return;
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.92f);
        View content = binding.formScroll.getChildAt(0);
        int contentHeight = content == null ? binding.formScroll.getMeasuredHeight() : content.getMeasuredHeight();
        int desiredHeight = Math.min(Math.max(contentHeight, dpToPx(420)), maxHeight);
        ViewGroup.LayoutParams layoutParams = sheet.getLayoutParams();
        layoutParams.height = desiredHeight;
        sheet.setLayoutParams(layoutParams);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setPeekHeight(desiredHeight);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogStyle;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
