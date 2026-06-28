package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentBottomSheetAccountEditBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * 账户详情底部弹窗
 *
 * 功能：
 * - 显示账户完整信息
 * - 包含图标、账户组、创建日期
 * - 优化备注显示区域
 * - 信用额度动态显示/隐藏
 *
 *
 */
public class BottomSheetAccountEditFragment extends BottomSheetDialogFragment {

    private static final String ARG_ACCOUNT = "account";

    private FragmentBottomSheetAccountEditBinding binding;
    private Account account;
    private AccountViewModel accountViewModel;

    /**
     * 创建实例
     */
    public static BottomSheetAccountEditFragment newInstance(Account account) {
        BottomSheetAccountEditFragment fragment = new BottomSheetAccountEditFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ACCOUNT, account);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            account = (Account) getArguments().getSerializable(ARG_ACCOUNT);
        }

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBottomSheetAccountEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (account != null) {
            populateAccountData();
            //只有在需要加载账户组时才初始化 ViewModel
            loadAccountGroupIfNeeded();
        }

        setupListeners();
    }

    /**
     * 填充账户数据
     */
    private void populateAccountData() {
        // 1. 设置账户图标
        if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
            ImageLoaderUtils.load(requireContext(), account.getIconUrl(), binding.ivAccountIcon);
        } else {
            binding.ivAccountIcon.setImageResource(R.drawable.ic_wallet);
        }

        // 2. 设置账户名称
        binding.tvAccountName.setText(account.getName());

        // 3. 设置账户类型并处理信用额度显示
        if (account.isCredit()) {
            binding.tvAccountType.setText("信用账户");
            // 显示信用额度相关控件
            binding.ivCreditLimitIcon.setVisibility(View.VISIBLE);
            binding.tvCreditLimitLabel.setVisibility(View.VISIBLE);
            binding.tvCreditLimitValue.setVisibility(View.VISIBLE);
            binding.divider2.setVisibility(View.VISIBLE);
            binding.tvCreditLimitValue.setText(String.format(Locale.getDefault(), "¥%.2f", account.getCreditLimit()));
        } else {
            binding.tvAccountType.setText("储蓄账户");
            // 隐藏信用额度相关控件
            binding.ivCreditLimitIcon.setVisibility(View.GONE);
            binding.tvCreditLimitLabel.setVisibility(View.GONE);
            binding.tvCreditLimitValue.setVisibility(View.GONE);
            binding.divider2.setVisibility(View.GONE);
        }

        // 4. 设置余额
        double balance = account.getBalance();
        String balanceText;
        if (balance < 0) {
            balanceText = String.format(Locale.getDefault(), "-¥%.2f", Math.abs(balance));
        } else {
            balanceText = String.format(Locale.getDefault(), "¥%.2f", balance);
        }
        binding.tvBalanceValue.setText(balanceText);

        // 5. 设置创建日期
        if (account.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            binding.tvDateValue.setText(sdf.format(account.getCreatedAt()));
        } else {
            binding.tvDateValue.setText("未知");
        }

        // 6. 设置卡号
        if (account.getCardNumber() != null && !account.getCardNumber().isEmpty()) {
            binding.tvCardNumberValue.setText(account.getCardNumber());
        } else {
            binding.tvCardNumberValue.setText("未设置");
        }

        // 7. 设置备注
        if (account.getRemark() != null && !account.getRemark().isEmpty()) {
            binding.tvRemarkValue.setText(account.getRemark());
        } else {
            binding.tvRemarkValue.setText("无");
        }
    }

    /**
     *  只有在账户有 groupId 时才加载账户组信息
     */
    private void loadAccountGroupIfNeeded() {
        if (account.getGroupId() == null || account.getGroupId().isEmpty()) {
            binding.tvGroupValue.setText("未分组");
            return;
        }

        //延迟初始化 ViewModel
        if (accountViewModel == null) {
            accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        }

        // 异步加载账户组信息
        AppExecutors.get().diskIO().execute(() -> {
            try {
                AccountGroup group = accountViewModel.getAccountGroupByIdSync(account.getGroupId());

                AppExecutors.get().mainThread().execute(() -> {
                    if (group != null) {
                        binding.tvGroupValue.setText(group.getName());
                    } else {
                        binding.tvGroupValue.setText("未知分组");
                    }
                });
            } catch (Exception e) {
                AppExecutors.get().mainThread().execute(() -> {
                    binding.tvGroupValue.setText("加载失败");
                });
            }
        });
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 关闭按钮
        binding.ivClose.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}