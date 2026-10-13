package com.example.my_project1.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.LayoutReimburseBottomSheetBinding;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.example.my_project1.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * ReimburseBottomSheetFragment - 账单报销弹窗
 */
public class ReimburseBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnReimburseCompletedListener {
        void onCompleted(Bill updatedBill);
    }

    private LayoutReimburseBottomSheetBinding binding;
    private BillViewModel billViewModel;
    private Bill bill;
    private OnReimburseCompletedListener listener;

    private double reimburseAmount = 0.0;
    private Date reimburseTime = new Date();
    private String reimburseAccountId = null;
    private String reimburseAccountName = null;
    private Account selectedReimburseAccount = null;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public static ReimburseBottomSheetFragment newInstance(Bill bill) {
        ReimburseBottomSheetFragment fragment = new ReimburseBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable("bill", bill);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnReimburseCompletedListener(OnReimburseCompletedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bill = (Bill) getArguments().getSerializable("bill");
        }
        setStyle(STYLE_NORMAL, R.style.WhiteNavigationBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 🔑 确保 design_bottom_sheet 及其父容器背景透明，以展示根布局的圆角背景
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                if (bottomSheet.getParent() instanceof View) {
                    ((View) bottomSheet.getParent()).setBackgroundColor(Color.TRANSPARENT);
                }
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            BottomSheetNavigationBarUtils.setup(dialog);
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            BottomSheetNavigationBarUtils.applyLightAppearance(window);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutReimburseBottomSheetBinding.inflate(inflater, container, false);
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);

        if (bill != null) {
            initData();
            bindBillInfo();
            bindFormValues();
        }

        setupClickListeners();

        return binding.getRoot();
    }

    private void initData() {
        reimburseAmount = bill.getReimburseAmount() > 0 ? bill.getReimburseAmount() : bill.getAmount();
        reimburseTime = bill.getReimburseTime() != null ? bill.getReimburseTime() : new Date();
        reimburseAccountId = bill.getReimburseAccountId();
        reimburseAccountName = bill.getReimburseAccountName();
    }

    private void bindBillInfo() {
        binding.tvCategoryName.setText(bill.getCategoryName() != null ? bill.getCategoryName() : "支出");
        ImageLoaderUtils.loadHomeBillCategoryIcon(requireContext(), bill.getCategoryIconUrl(), binding.ivCategoryIcon);
        binding.ivCategoryIcon.setBackground(null);

        if (bill.getCategoryIconBackgroundColor() != null && !bill.getCategoryIconBackgroundColor().trim().isEmpty()) {
            try {
                int color = Color.parseColor(bill.getCategoryIconBackgroundColor().trim());
                binding.cardCategoryIcon.setCardBackgroundColor(color);
            } catch (Exception e) {
                binding.cardCategoryIcon.setCardBackgroundColor(Color.parseColor("#FBBF24"));
            }
        } else {
            binding.cardCategoryIcon.setCardBackgroundColor(Color.parseColor("#FBBF24"));
        }

        if (bill.getBillTime() != null) {
            binding.tvBillTime.setText(timeFormat.format(bill.getBillTime()));
        } else {
            binding.tvBillTime.setText("");
        }

        if (bill.isReimbursed()) {
            binding.tvStatusTag.setText("已报销");
            binding.tvStatusTag.setBackgroundResource(R.drawable.bg_tag_reimbursed);
        } else {
            binding.tvStatusTag.setText("待报销");
            binding.tvStatusTag.setBackgroundResource(R.drawable.bg_tag_pending_reimburse);
        }

        binding.tvBillAmount.setText(String.format(Locale.getDefault(), "¥%.2f", bill.getAmount()));
        
        if (bill.getAccountId() != null || bill.getLocalAccountId() > 0) {
            com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel accountViewModel =
                    new ViewModelProvider(requireActivity()).get(com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel.class);
            accountViewModel.getAccountLive(bill.getAccountId(), bill.getLocalAccountId()).observe(getViewLifecycleOwner(), account -> {
                if (account != null && account.getName() != null && !account.getName().isEmpty()) {
                    binding.tvBillAccount.setText(account.getName());
                } else {
                    binding.tvBillAccount.setText("原付款账户");
                }
            });
        } else {
            binding.tvBillAccount.setText("原付款账户");
        }
    }

    private void bindFormValues() {
        binding.tvReimburseAmount.setText(formatAmountString(reimburseAmount));
        binding.tvReimburseTime.setText(dateFormat.format(reimburseTime));

        if (reimburseAccountName != null && !reimburseAccountName.trim().isEmpty()) {
            binding.tvReimburseAccount.setText(reimburseAccountName);
            binding.tvReimburseAccount.setTextColor(Color.parseColor("#1E293B"));
        } else {
            binding.tvReimburseAccount.setText("未选择");
            binding.tvReimburseAccount.setTextColor(Color.parseColor("#64748B"));
        }

        if (bill.getReimburseRemark() != null) {
            binding.etReimburseRemark.setText(bill.getReimburseRemark());
        }
    }

    private void setupClickListeners() {
        binding.cardBack.setOnClickListener(v -> dismiss());

        // 确认保存报销
        binding.cardConfirm.setOnClickListener(v -> performSaveReimburse());

        // 修改报销金额
        binding.rowReimburseAmount.setOnClickListener(v -> showAmountDialog());

        // 修改报销时间
        binding.rowReimburseTime.setOnClickListener(v -> showDatePicker());

        // 选择入账账户
        binding.rowReimburseAccount.setOnClickListener(v -> showAccountPicker());
    }

    private String formatAmountString(double amount) {
        if (amount % 1 == 0) {
            return String.format(Locale.getDefault(), "%.0f", amount);
        } else {
            return String.format(Locale.getDefault(), "%.2f", amount);
        }
    }

    private void showAmountDialog() {
        BalanceAdjustmentBottomSheetFragment fragment = BalanceAdjustmentBottomSheetFragment.newInstance(reimburseAmount, "报销金额");
        fragment.setOnBalanceAdjustedListener((newAmount, recordAsTransaction) -> {
            if (newAmount < 0) {
                SnackbarUtils.showWarning(binding.getRoot(), "报销金额不能为负数");
                return;
            }
            reimburseAmount = newAmount;
            binding.tvReimburseAmount.setText(formatAmountString(reimburseAmount));
        });
        fragment.show(getChildFragmentManager(), "reimburse_amount_dialog");
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (reimburseTime != null) {
            cal.setTime(reimburseTime);
        }
        CustomDateTimePickerFragment.show(
                getChildFragmentManager(),
                cal,
                selectedCal -> {
                    reimburseTime = selectedCal.getTime();
                    binding.tvReimburseTime.setText(dateFormat.format(reimburseTime));
                }
        );
    }

    private void showAccountPicker() {
        BillChooseAccountFragment fragment = BillChooseAccountFragment.newInstance();
        fragment.setOnAccountChooseListener((account, iconUrl, accountName) -> {
            selectedReimburseAccount = account;
            if (account != null) {
                reimburseAccountId = account.getObjectId() != null ? account.getObjectId() : String.valueOf(account.getId());
                reimburseAccountName = accountName;
            } else {
                reimburseAccountId = null;
                reimburseAccountName = accountName;
            }
            binding.tvReimburseAccount.setText(reimburseAccountName != null ? reimburseAccountName : "未选择");
            binding.tvReimburseAccount.setTextColor(Color.parseColor("#1E293B"));
        });
        fragment.show(getChildFragmentManager(), "choose_account");
    }

    private void performSaveReimburse() {
        if (bill == null) return;

        bill.setReimbursable(true);
        bill.setReimbursed(true);
        bill.setReimburseAmount(reimburseAmount);
        bill.setReimburseTime(reimburseTime);
        bill.setReimburseAccountId(reimburseAccountId);
        bill.setReimburseAccountName(reimburseAccountName);
        bill.setReimburseRemark(binding.etReimburseRemark.getText().toString().trim());
        bill.setSyncState(com.example.my_project1.data.model.SyncState.TO_UPDATE);

        billViewModel.updateBill(bill);

        // 🌟 新增：创建名为 "报销入账" 的入账账单（不能编辑，具备完整云端同步状态）
        Bill reimburseIncomeBill = new Bill();
        reimburseIncomeBill.setType(1); // 1 = 收入
        reimburseIncomeBill.setCategoryName("报销入账");
        reimburseIncomeBill.setCategoryId("reimburse_income");
        reimburseIncomeBill.setCategoryIconUrl("ic_cashback");
        reimburseIncomeBill.setAmount(reimburseAmount);
        reimburseIncomeBill.setBillTime(reimburseTime != null ? reimburseTime : new Date());

        String userId = bill.getUserId() != null ? bill.getUserId() : billViewModel.getCurrentUserId();
        reimburseIncomeBill.setUserId(userId);
        reimburseIncomeBill.setBookId(bill.getBookId());
        reimburseIncomeBill.setSyncState(com.example.my_project1.data.model.SyncState.TO_CREATE);
        reimburseIncomeBill.setReimburseRemark("REF_BILL_ID:" + bill.getId());

        if (selectedReimburseAccount != null) {
            if (selectedReimburseAccount.getObjectId() != null && !selectedReimburseAccount.getObjectId().isEmpty()) {
                reimburseIncomeBill.setAccountId(selectedReimburseAccount.getObjectId());
            }
            reimburseIncomeBill.setLocalAccountId(selectedReimburseAccount.getId());
        } else if (reimburseAccountId != null && !reimburseAccountId.trim().isEmpty()) {
            try {
                long localId = Long.parseLong(reimburseAccountId.trim());
                reimburseIncomeBill.setLocalAccountId(localId);
            } catch (NumberFormatException e) {
                reimburseIncomeBill.setAccountId(reimburseAccountId.trim());
            }
        } else if (bill.getAccountId() != null || bill.getLocalAccountId() > 0) {
            reimburseIncomeBill.setAccountId(bill.getAccountId());
            reimburseIncomeBill.setLocalAccountId(bill.getLocalAccountId());
        }

        String customRemark = binding.etReimburseRemark.getText().toString().trim();
        if (!customRemark.isEmpty()) {
            reimburseIncomeBill.setRemark("报销入账: " + customRemark);
        } else if (bill.getRemark() != null && !bill.getRemark().trim().isEmpty()) {
            reimburseIncomeBill.setRemark("报销入账: " + bill.getRemark());
        } else {
            reimburseIncomeBill.setRemark("账单报销入账");
        }

        reimburseIncomeBill.setCategoryIconBackgroundColor("#2ECC71");
        billViewModel.insertBill(reimburseIncomeBill);

        View rootView = requireActivity().findViewById(android.R.id.content);
        if (rootView != null) {
            SnackbarUtils.showSuccess(rootView, "报销成功并生成报销入账账单!");
        }

        if (listener != null) {
            listener.onCompleted(bill);
        }

        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
