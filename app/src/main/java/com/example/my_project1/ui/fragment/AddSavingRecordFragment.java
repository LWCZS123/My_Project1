package com.example.my_project1.ui.fragment;

import android.app.DatePickerDialog;
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

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.FragmentAddSavingRecordBinding;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.my_project1.data.model.common.ApiResponse;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * AddSavingRecordFragment - 添加存钱记录底部弹窗（优化版）
 * -------------------------------------------------------
 * 优化内容：
 *  1. ✅ 强化账户关联：确保账户 ID 正确传递并能查到
 *  2. ✅ 异步操作优化：账单和记录操作均在后台执行
 *  3. ✅ 错误处理增强：提供具体的错误信息，防止静默失败
 *  4. ✅ UI 防重复：防止快速重复点击保存按钮
 *  5. ✅ 数据完整性：确保三层关联（愿望-记录-账单）稳定
 */
public class AddSavingRecordFragment extends BottomSheetDialogFragment {

    private static final String TAG = "AddSavingRecordFragment";
    private static final String ARG_WISH_ID   = "wish_id";
    private static final String ARG_WISH_NAME = "wish_name";
    private static final String ARG_ICON_URL  = "icon_url";

    private FragmentAddSavingRecordBinding binding;
    private WishViewModel wishViewModel;
    private BillViewModel billViewModel;

    // 从 args 还原
    private long    wishId;
    private String  wishName;
    private String  iconUrl;

    // 选中账户（保存完整的 Account 对象，而非仅 ID）
    private Account selectedAccount = null;

    // 选中日期（默认今天）
    private Date selectedDate = new Date();

    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());

    // ✅ 防重复提交标志
    private boolean isSaving = false;

    // ======================== 工厂方法 ========================

    public static AddSavingRecordFragment newInstance(Wish wish) {
        AddSavingRecordFragment f = new AddSavingRecordFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WISH_ID,   wish.getId());
        args.putString(ARG_WISH_NAME, wish.getWishName());
        args.putString(ARG_ICON_URL,  wish.getIconUrl());
        f.setArguments(args);
        return f;
    }

    // ======================== 生命周期 ========================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            wishId   = getArguments().getLong(ARG_WISH_ID, -1);
            wishName = getArguments().getString(ARG_WISH_NAME, "");
            iconUrl  = getArguments().getString(ARG_ICON_URL, null);
        }
        Log.d(TAG, "Fragment 创建: wishId=" + wishId + ", wishName=" + wishName);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddSavingRecordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 获取共享 ViewModel（必须和 SavingsActivity 同一作用域）
        wishViewModel = new ViewModelProvider(requireActivity()).get(WishViewModel.class);
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);

        initUI();
        setupClickListeners();
        observeViewModels();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ======================== 初始化 ========================

    private void initUI() {
        // 愿望图标（只展示，不可更改）
        if (!TextUtils.isEmpty(iconUrl)) {
            Glide.with(this)
                    .load(iconUrl)
                    .placeholder(R.drawable.ic_face)
                    .into(binding.ivWishIcon);
        }
        binding.tvWishName.setText(wishName);

        // 默认日期为今天
        binding.tvDate.setText(dateFormatter.format(selectedDate));

        // 默认账户文本
        binding.tvAccountName.setText("选择账户");
        binding.tvAccountName.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray));

        Log.d(TAG, "UI 初始化完成");
    }

    private void setupClickListeners() {
        binding.tvCancel.setOnClickListener(v -> dismiss());

        // 日期选择
        binding.layoutDate.setOnClickListener(v -> {
            if (!isSaving) {
                openDatePicker();
            }
        });

        // 账户选择
        binding.layoutAccount.setOnClickListener(v -> {
            if (!isSaving) {
                selectAccount();
            }
        });

        // 保存（防重复点击）
        binding.btnSave.setOnClickListener(v -> {
            if (!isSaving) {
                onSaveClicked();
            }
        });
    }

    /**
     * ✅ 监听 ViewModel 的操作状态
     */
    private void observeViewModels() {
        // 监听 WishViewModel 操作结果
        wishViewModel.operationState.observe(getViewLifecycleOwner(), response -> {
            if (response == null) return;

            switch (response.getStatus()) {
                case LOADING:
                    binding.btnSave.setEnabled(false);
                    binding.btnSave.setText("保存中...");
                    Log.d(TAG, "WishViewModel: 加载中");
                    break;

                case SUCCESS:
                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText("确认存入");
                    Log.d(TAG, "WishViewModel: 操作成功");
                    isSaving = false;
                    wishViewModel.resetOperationState();
                    dismiss(); // ✅ 自动关闭弹窗
                    break;

                case ERROR:
                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText("确认存入");
                    isSaving = false;
                    Log.e(TAG, "WishViewModel: 操作失败 - " + response.getMessage());
                    Toast.makeText(requireContext(),
                            "保存失败: " + response.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    wishViewModel.resetOperationState();
                    break;

                default:
                    binding.btnSave.setEnabled(true);
                    break;
            }
        });

        // 监听 BillViewModel 操作结果
        billViewModel.operationState.observe(getViewLifecycleOwner(), response -> {
            if (response == null) return;

            if (!response.isSuccess()) {
                Log.e(TAG, "BillViewModel: 账单操作失败 - " + response.getMessage());
                Toast.makeText(requireContext(),
                        "账单创建失败: " + response.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ======================== 交互 ========================

    /**
     * ✅ 日期选择对话框
     */
    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);

        new DatePickerDialog(
                requireContext(),
                (picker, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    selectedDate = selected.getTime();
                    binding.tvDate.setText(dateFormatter.format(selectedDate));
                    Log.d(TAG, "日期已选择: " + dateFormatter.format(selectedDate));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    /**
     * ✅ 账户选择（强化版）
     * 确保获取完整的 Account 对象，包括所有必要的字段
     */
    private void selectAccount() {
        BillChooseAccountFragment fragment = new BillChooseAccountFragment();

        fragment.setOnAccountChooseListener((account, accountIconUrl, accountName) -> {
            // ✅ 数据验证
            if (account == null) {
                Log.w(TAG, "账户对象为 null");
                Toast.makeText(requireContext(), "账户数据异常", Toast.LENGTH_SHORT).show();
                return;
            }

            if (account.getId() <= 0) {
                Log.w(TAG, "账户 ID 无效: " + account.getId());
                Toast.makeText(requireContext(), "账户 ID 异常", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ 保存完整的账户对象
            selectedAccount = account;


            // ✅ 更新 UI
            binding.tvAccountName.setText(accountName);
            binding.tvAccountName.setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.black));

            // 加载账户图标
            if (!TextUtils.isEmpty(accountIconUrl)) {
                Glide.with(this)
                        .load(accountIconUrl)
                        .placeholder(R.drawable.ic_unselect_account)
                        .into(binding.ivAccountIcon);
            } else {
                binding.ivAccountIcon.setImageResource(R.drawable.ic_unselect_account);
            }
        });

        fragment.show(getChildFragmentManager(), "choose_account");
    }

    /**
     * ✅ 保存按钮点击处理（优化版）
     */
    private void onSaveClicked() {
        if (!validateInput()) return;

        // ✅ 防重复提交
        if (isSaving) {
            Log.w(TAG, "正在保存中，忽略重复点击");
            return;
        }

        double amount = Double.parseDouble(
                binding.etAmount.getText().toString().trim());
        String note = binding.etNote.getText().toString().trim();

        // 按钮防抖
        isSaving = true;
        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("保存中...");

        Log.d(TAG, "开始保存: amount=" + amount + ", note=" + note
                + ", accountId=" + (selectedAccount != null ? selectedAccount.getId() : "null"));

        // ✅ 异步执行：先创建账单，再添加记录
        new Thread(() -> {
            try {
                writeBillForSavingRecord(amount, note, response -> {
                    if (response.isSuccess() && response.getData() != null) {
                        // 账单创建成功
                        long billId = response.getData();
                        Log.d(TAG, "账单已创建: billId=" + billId);

                        // 在主线程中添加记录
                        requireActivity().runOnUiThread(() -> {
                            wishViewModel.addSavingRecord(wishId, amount, note, selectedDate, billId);
                        });
                    } else {
                        // 账单创建失败
                        requireActivity().runOnUiThread(() -> {
                            isSaving = false;
                            binding.btnSave.setEnabled(true);
                            binding.btnSave.setText("确认存入");
                            Toast.makeText(requireContext(),
                                    "账单创建失败: " + response.getMessage(),
                                    Toast.LENGTH_SHORT).show();

                            Log.e(TAG, "账单创建失败: " + response.getMessage());
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "保存异常", e);
                requireActivity().runOnUiThread(() -> {
                    isSaving = false;
                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText("确认存入");
                    Toast.makeText(requireContext(), "保存异常: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * ✅ 创建账单（强化版）
     * - 完整的数据关联
     * - 准确的账户 ID
     * - 清晰的错误处理
     */
    private void writeBillForSavingRecord(double amount, String note,
                                          ApiResponse.Callback<Long> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            if (callback != null) {
                callback.onComplete(ApiResponse.error("用户未登录"));
            }
            return;
        }

        Bill bill = new Bill();
        bill.setUserId(userId);
        bill.setType(0);                           // 支出
        bill.setAmount(amount);
        bill.setCategoryName(wishName);            // 分类名 = 愿望名
        bill.setCategoryIconUrl(iconUrl);          // 分类图标 = 愿望图标
        bill.setBillTime(selectedDate);
        bill.setRemark(TextUtils.isEmpty(note) ? wishName + " 存钱" : note);
        bill.setSourceWishId(wishId);              // 来源愿望 ID，供级联删除使用

        // ✅ 强化账户关联
        if (selectedAccount != null && selectedAccount.getId() > 0) {
            String accountId = String.valueOf(selectedAccount.getId());
            bill.setAccountId(accountId);

            Log.d(TAG, "账户已关联: accountId=" + accountId
                    + ", accountName=" + selectedAccount.getName());
        } else {
            Log.w(TAG, "未选择账户，账单将不关联具体账户");
            // 不强制账户，允许不选账户保存
        }

        // ✅ 调用 BillViewModel 保存账单
        billViewModel.insertBillWithCallback(bill, callback);
    }

    /**
     * ✅ 输入验证
     */
    private boolean validateInput() {
        String amountStr = binding.etAmount.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            binding.etAmount.setError("请输入存入金额");
            return false;
        }

        try {
            double v = Double.parseDouble(amountStr);
            if (v <= 0) {
                binding.etAmount.setError("金额必须大于 0");
                return false;
            }
            if (v > 999999) {
                binding.etAmount.setError("金额过大");
                return false;
            }
        } catch (NumberFormatException e) {
            binding.etAmount.setError("金额格式错误");
            return false;
        }

        return true;
    }

    private String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }

    // ======================== BottomSheet 样式 ========================

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            if (!isAdded()) return;
            if (getContext() == null) return;

            FrameLayout sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(
                        requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }
}