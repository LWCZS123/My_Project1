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

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.databinding.FragmentAddSavingRecordBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.example.my_project1.ui.wish.WishUiFormatter;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;
import java.util.Date;

public class AddSavingRecordFragment extends BottomSheetDialogFragment {

    private static final String ARG_WISH_ID = "wish_id";
    private static final String ARG_RECORD_ID = "record_id";
    private static final String STATE_SELECTED_DATE = "selected_date";

    private FragmentAddSavingRecordBinding binding;
    private WishViewModel viewModel;
    private AccountViewModel accountViewModel;
    private long wishId;
    private long recordId;
    private WishRecord loadedRecord;
    private Wish loadedWish;
    private Date selectedDate = new Date();
    private boolean populated;
    private boolean saving;

    private Account fromAccount;
    private Account toAccount;

    public static AddSavingRecordFragment newInstance(long wishId) {
        AddSavingRecordFragment fragment = new AddSavingRecordFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WISH_ID, wishId);
        fragment.setArguments(args);
        return fragment;
    }

    public static AddSavingRecordFragment newInstance(Wish wish) {
        return newInstance(wish.getId());
    }

    public static AddSavingRecordFragment newInstanceForEdit(long wishId, long recordId) {
        AddSavingRecordFragment fragment = new AddSavingRecordFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WISH_ID, wishId);
        args.putLong(ARG_RECORD_ID, recordId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        wishId = args == null ? 0 : args.getLong(ARG_WISH_ID, 0);
        recordId = args == null ? 0 : args.getLong(ARG_RECORD_ID, 0);
        
        if (savedInstanceState != null) {
            selectedDate = new Date(savedInstanceState.getLong(
                    STATE_SELECTED_DATE, System.currentTimeMillis()));
        } else {
            // 初始化时即清除秒和毫秒，防止与当前时刻对比产生毫秒级误差
            Calendar c = Calendar.getInstance();
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            selectedDate = c.getTime();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddSavingRecordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(WishViewModel.class);
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        
        binding.etRecordDate.setText(WishUiFormatter.dateTime(selectedDate));
        binding.etRecordDate.setOnClickListener(v -> showDatePicker());
        binding.llDateRow.setOnClickListener(v -> showDatePicker()); // 同时支持点击行
        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> save());

        // 点击空白行也弹出键盘
        binding.llRemark.setOnClickListener(v -> {
            binding.etNote.requestFocus();
            showKeyboard(binding.etNote);
        });

        setupAccountSelection();
        
        // 隐藏账本选择（愿望模块暂不需要），显示愿望头部
        binding.llLedger.setVisibility(View.GONE);
        binding.llWishHeader.setVisibility(View.VISIBLE);
        
        viewModel.getWishById(wishId).observe(getViewLifecycleOwner(), wish -> {
            if (wish != null) {
                loadedWish = wish;
                binding.tvWishName.setText(wish.getWishName());
                // 加载愿望图标
                if (wish.getIconUrl() != null && !wish.getIconUrl().isEmpty()) {
                    ImageLoaderUtils.load(requireContext(), wish.getIconUrl(), binding.ivWishIcon,
                            R.drawable.ic_piggy_bank, R.drawable.ic_piggy_bank);
                } else {
                    binding.ivWishIcon.setImageResource(R.drawable.ic_piggy_bank);
                }
            }
        });
        
        if (recordId > 0) {
            binding.tvFormTitle.setText("编辑记录");
            viewModel.getRecord(recordId).observe(getViewLifecycleOwner(), this::populate);
        }
        
        viewModel.getOperationState().observe(getViewLifecycleOwner(), state -> {
            if (!saving) return;
            binding.btnSave.setEnabled(!state.isLoading());
            if (state.isSuccess()) {
                saving = false;
                dismiss();
            } else if (state.isError()) {
                saving = false;
            }
        });
    }

    private void setupAccountSelection() {
        View.OnClickListener fromAccountClick = v -> {
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
        };

        binding.llFromAccount.setOnClickListener(fromAccountClick);
        binding.tvFromAccount.setOnClickListener(fromAccountClick);

        View.OnClickListener toAccountClick = v -> {
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
        };

        binding.llToAccount.setOnClickListener(toAccountClick);
        binding.tvToAccount.setOnClickListener(toAccountClick);
        
        // 确保视图可见
        binding.llToAccount.setVisibility(View.VISIBLE);
        binding.vLineToAccount.setVisibility(View.VISIBLE);
        binding.llFromAccount.setVisibility(View.VISIBLE);
        binding.vLineFromAccount.setVisibility(View.VISIBLE);
    }

    private void populate(WishRecord record) {
        if (record == null || populated) return;
        loadedRecord = record;
        populated = true;
        
        Date date = record.getRecordDate();
        if (date == null) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            date = c.getTime();
        }
        selectedDate = date;
        
        binding.etAmount.setText(String.valueOf(record.getAmount()));
        binding.etRecordDate.setText(WishUiFormatter.dateTime(selectedDate));
        binding.etNote.setText(record.getNote());
    }

    private void save() {
        String amountText = textOf(binding.etAmount.getText());
        binding.etAmount.setError(null);
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException exception) {
            binding.etAmount.setError("请输入有效金额");
            return;
        }
        if (amount <= 0d || Double.isNaN(amount) || Double.isInfinite(amount)) {
            binding.etAmount.setError("存入金额必须大于 0");
            return;
        }



        WishRecord record = new WishRecord();
        record.setWishId(wishId);
        if (loadedRecord != null) record.setId(loadedRecord.getId());
        record.setAmount(amount);
        record.setRecordDate(selectedDate);
        record.setNote(textOf(binding.etNote.getText()));
        
        long fromId = fromAccount != null ? fromAccount.getId() : -1;
        long toId = toAccount != null ? toAccount.getId() : -1;
        
        saving = true;
        viewModel.saveRecord(record, fromId, toId);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(selectedDate);
        
        CustomDateTimePickerFragment.show(getChildFragmentManager(), calendar, selected -> {
            //清除选择器返回的秒和毫秒，确保与当前时间对比时不会因毫秒差报错
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);

            selectedDate = selected.getTime();
            binding.etRecordDate.setError(null);
            binding.etRecordDate.setText(WishUiFormatter.dateTime(selectedDate));
        });
    }

    private void showKeyboard(View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_SELECTED_DATE, selectedDate.getTime());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            View sheet = ((BottomSheetDialog) getDialog())
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                applySystemBarColors();
                sheet.setBackgroundResource(android.R.color.transparent);
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                sheet.requestLayout();
                sheet.post(() -> applyAdaptiveSheetHeight(sheet));
            }
        }
    }

    private void applyAdaptiveSheetHeight(View sheet) {
        if (binding == null) return;
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.92f);
        View content = binding.formScroll.getChildAt(0);
        int contentHeight = content == null ? binding.formScroll.getMeasuredHeight()
                : content.getMeasuredHeight();
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

    private void applySystemBarColors() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        android.view.Window window = getDialog().getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.parseColor("#EEF6FF"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                window, window.getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogStyle;
    }

    private String textOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
