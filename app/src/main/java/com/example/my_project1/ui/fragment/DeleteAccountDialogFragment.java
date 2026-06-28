package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.FragmentDeleteAccountDialogBinding;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * DeleteAccountDialogFragment
 * ----------------------------------------------------------------
 * 功能：删除账户对话框
 *   - 展示要删除的账户信息
 *   - 提示用户选择迁移账单到新账户
 *   - 如果不迁移，需要输入验证码确认
 *
 *
 */
public class DeleteAccountDialogFragment extends BottomSheetDialogFragment {

    private static final String TAG = "DeleteAccountDialog";
    private static final String ARG_ACCOUNT_NAME = "account_name";
    private static final String ARG_ACCOUNT_ICON = "account_icon";
    private static final String ARG_HAS_BILLS = "has_bills";
    private static final String ARG_BILL_COUNT = "bill_count";

    // 🔑 ViewBinding
    private FragmentDeleteAccountDialogBinding binding;

    // 参数
    private String accountName;
    private String accountIcon;
    private boolean hasBills;
    private int billCount;

    // 数据
    private Account selectedTargetAccount = null;

    // 回调
    private OnDeleteActionListener listener;

    // ==================== 静态工厂方法 ====================

    public static DeleteAccountDialogFragment newInstance(String accountName, String accountIcon,
                                                          boolean hasBills, int billCount) {
        DeleteAccountDialogFragment fragment = new DeleteAccountDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_ACCOUNT_ICON, accountIcon);
        args.putBoolean(ARG_HAS_BILLS, hasBills);
        args.putInt(ARG_BILL_COUNT, billCount);
        fragment.setArguments(args);
        return fragment;
    }

    // ==================== 生命周期 ====================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            accountName = getArguments().getString(ARG_ACCOUNT_NAME);
            accountIcon = getArguments().getString(ARG_ACCOUNT_ICON);
            hasBills = getArguments().getBoolean(ARG_HAS_BILLS);
            billCount = getArguments().getInt(ARG_BILL_COUNT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDeleteAccountDialogBinding.inflate(inflater, container, false);

        setupUI();
        setupListeners();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_bottom_sheet));

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                bottomSheet.setLayoutParams(params);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setDraggable(true);
            }
        });

        return dialog;
    }

    // ==================== 初始化 ====================

    private void setupUI() {
        // 设置标题
        binding.tvTitle.setText("删除账户");

        // 设置账户名称
        binding.tvAccountName.setText(accountName);

        // 加载账户图标
        if (accountIcon != null && !accountIcon.isEmpty()) {
            ImageLoaderUtils.load(requireContext(), accountIcon, binding.ivAccountIcon);
        }

        // 根据是否有账单显示不同的提示信息
        if (hasBills) {
            binding.viewInfoCard.setVisibility(View.VISIBLE);
            binding.tvInfo.setText("发现该账户下有账单，请先迁移。点击迁入账户选择（若不选择这些账单将被设置为无账户）");
            binding.layoutTargetAccount.setVisibility(View.VISIBLE);
            binding.btnMigrate.setText("迁移并删除");
        } else {
            binding.viewInfoCard.setVisibility(View.GONE);
            binding.layoutTargetAccount.setVisibility(View.GONE);
            binding.ivArrow.setVisibility(View.GONE);
            binding.btnMigrate.setText("确认删除");
        }
    }

    private void setupListeners() {
        // 取消按钮
        binding.btnCancel.setOnClickListener(v -> dismiss());

        // 点击选择目标账户
        binding.layoutTargetAccount.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChooseTargetAccount();
            }
        });

        // 迁移并删除 / 确认删除按钮
        binding.btnMigrate.setOnClickListener(v -> handleMigrateOrDelete());
    }

    // ==================== 业务逻辑 ====================

    /**
     * 处理迁移或删除操作
     */
    private void handleMigrateOrDelete() {
        if (hasBills) {
            // 有账单的情况
            if (selectedTargetAccount != null) {
                // 用户选择了迁移账户
                if (listener != null) {
                    listener.onMigrateAndDelete(selectedTargetAccount);
                }
                dismiss();
            } else {
                // 用户没有选择迁移账户，弹出验证码对话框
                showVerificationDialog();
            }
        } else {
            // 没有账单，直接删除
            if (listener != null) {
                listener.onDirectDelete();
            }
            dismiss();
        }
    }

    /**
     * 显示验证码对话框
     */
    private void showVerificationDialog() {
        VerificationCodeDialog dialog = VerificationCodeDialog.newInstance();
        dialog.setOnVerificationListener(new VerificationCodeDialog.OnVerificationListener() {
            @Override
            public void onVerified() {
                // 验证通过，删除账户并将账单设置为无账户
                if (listener != null) {
                    listener.onDeleteWithoutMigration();
                }
                dismiss();
            }

            @Override
            public void onCancel() {
                // 用户取消验证，不做任何操作
                Log.d(TAG, "用户取消验证");
            }
        });
        dialog.show(getParentFragmentManager(), "VerificationDialog");
    }

    /**
     * 更新选中的目标账户
     */
    public void setSelectedTargetAccount(Account account) {
        if (binding == null) {
            return;
        }

        this.selectedTargetAccount = account;

        if (account != null) {
            binding.tvTargetName.setText(account.getName());
            if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
                binding.ivTargetIcon.setVisibility(View.VISIBLE);
                ImageLoaderUtils.load(requireContext(), account.getIconUrl(), binding.ivTargetIcon);
            } else {
                binding.ivTargetIcon.setVisibility(View.GONE);
            }
        } else {
            binding.tvTargetName.setText("迁入账户");
            binding.ivTargetIcon.setVisibility(View.GONE);
        }
    }

    // ==================== 回调接口 ====================

    public void setOnDeleteActionListener(OnDeleteActionListener listener) {
        this.listener = listener;
    }

    public interface OnDeleteActionListener {
        /**
         * 选择目标账户（迁移账单）
         */
        void onChooseTargetAccount();

        /**
         * 迁移账单并删除账户
         */
        void onMigrateAndDelete(Account targetAccount);

        /**
         * 不迁移，直接删除（账单设置为无账户）
         */
        void onDeleteWithoutMigration();

        /**
         * 直接删除（无账单的情况）
         */
        void onDirectDelete();
    }
}