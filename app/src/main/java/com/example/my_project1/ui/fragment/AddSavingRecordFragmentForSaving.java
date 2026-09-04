package com.example.my_project1.ui.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.saving.SavingRecord;
import com.example.my_project1.databinding.FragmentAddSavingRecordBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.saving.SavingViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 记一笔存钱底栏片段
 * 支持选择扣款和存入账户，实现完整的存钱记账流程
 */
public class AddSavingRecordFragmentForSaving extends BottomSheetDialogFragment {

    private static final String ARG_PLAN_ID = "arg_plan_id";
    private static final String ARG_STEP_INDEX = "arg_step_index";
    private static final String ARG_AMOUNT = "arg_amount";

    private FragmentAddSavingRecordBinding binding;
    private SavingViewModel viewModel;
    private AccountViewModel accountViewModel;
    
    private long planId;
    private int stepIndex;
    private double prefillAmount;
    
    private Account fromAccount;
    private Account toAccount;

    public static AddSavingRecordFragmentForSaving newInstance(long planId, int stepIndex, double amount) {
        AddSavingRecordFragmentForSaving fragment = new AddSavingRecordFragmentForSaving();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAN_ID, planId);
        args.putInt(ARG_STEP_INDEX, stepIndex);
        args.putDouble(ARG_AMOUNT, amount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            planId = getArguments().getLong(ARG_PLAN_ID);
            stepIndex = getArguments().getInt(ARG_STEP_INDEX);
            prefillAmount = getArguments().getDouble(ARG_AMOUNT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddSavingRecordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SavingViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        
        setupUI();
        setupListeners();
        
        // 观察计划详情，决定是否显示账户选择
        if (planId > 0) {
            viewModel.getPlanById(planId).observe(getViewLifecycleOwner(), plan -> {
                if (plan != null) {
                    boolean visible = plan.isEnableTransfer();
                    binding.llToAccount.setVisibility(visible ? View.VISIBLE : View.GONE);
                    binding.vLineToAccount.setVisibility(visible ? View.VISIBLE : View.GONE);
                    binding.llFromAccount.setVisibility(visible ? View.VISIBLE : View.GONE);
                    binding.vLineFromAccount.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

    private void setupUI() {
        if (prefillAmount > 0) {
            binding.etAmount.setText(String.format(Locale.CHINA, "%.2f", prefillAmount));
        }
        binding.etRecordDate.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date()));
        
        // 自动弹出键盘
        binding.etAmount.post(() -> showKeyboard(binding.etAmount));
    }

    private void setupListeners() {
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnClose.setOnClickListener(v -> dismiss());
        
        // 时间选择
        binding.llDateRow.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            CustomDateTimePickerFragment.show(getChildFragmentManager(), calendar, selected -> {
                binding.etRecordDate.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(selected.getTime()));
            });
        });

        // 账户选择
        binding.llFromAccount.setOnClickListener(v -> {
            BillChooseAccountFragment sheet = BillChooseAccountFragment.newInstance();
            sheet.setOnAccountChooseListener((account, iconUrl, accountName) -> {
                if (toAccount != null && account.getId() == toAccount.getId()) {
                    Toast.makeText(getContext(), "扣款账户和存入账户不能相同", Toast.LENGTH_SHORT).show();
                    return;
                }
                fromAccount = account;
                binding.tvFromAccount.setText(accountName);
                binding.tvFromAccount.setTextColor(Color.parseColor("#172B4D"));
            });
            sheet.show(getChildFragmentManager(), "choose_from_account");
        });

        binding.llToAccount.setOnClickListener(v -> {
            BillChooseAccountFragment sheet = BillChooseAccountFragment.newInstance();
            sheet.setOnAccountChooseListener((account, iconUrl, accountName) -> {
                if (fromAccount != null && account.getId() == fromAccount.getId()) {
                    Toast.makeText(getContext(), "存入账户和扣款账户不能相同", Toast.LENGTH_SHORT).show();
                    return;
                }
                toAccount = account;
                binding.tvToAccount.setText(accountName);
                binding.tvToAccount.setTextColor(Color.parseColor("#172B4D"));
            });
            sheet.show(getChildFragmentManager(), "choose_to_account");
        });

        // 点击空白行也弹出键盘
        binding.llRemark.setOnClickListener(v -> showKeyboard(binding.etNote));
    }

    private void showKeyboard(View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED);
        }
    }

    private void save() {
        String amountStr = binding.etAmount.getText().toString();
        if (amountStr.isEmpty()) {
            Toast.makeText(getContext(), "请输入金额", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(amountStr);
        SavingRecord record = new SavingRecord();
        record.setPlanId(planId);
        record.setAmount(amount);
        record.setRecordDate(new Date());
        record.setNote(binding.etNote.getText().toString());
        
        long fromId = fromAccount != null ? fromAccount.getId() : -1;
        long toId = toAccount != null ? toAccount.getId() : -1;
        
        viewModel.saveRecord(record, stepIndex, fromId, toId);
        dismiss();
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
        int desiredHeight = Math.min(Math.max(contentHeight, dpToPx(450)), maxHeight);
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
        return com.example.my_project1.R.style.BottomSheetDialogStyle;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
