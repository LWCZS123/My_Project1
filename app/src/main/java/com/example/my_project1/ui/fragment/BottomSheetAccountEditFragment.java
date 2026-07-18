package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.text.SimpleDateFormat;
import java.util.Locale;

public class BottomSheetAccountEditFragment extends BottomSheetDialogFragment {

    private static final String ARG_ACCOUNT = "account";

    private FragmentBottomSheetAccountEditBinding binding;
    private Account account;
    private AccountViewModel accountViewModel;

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
        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
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
        }

        binding.ivClose.setOnClickListener(v -> dismiss());
    }

    private void populateAccountData() {
        binding.tvAccountName.setText(account.getName());
        binding.tvAccountType.setText(account.isCredit() ? "信用账户" : "储蓄账户");
        binding.tvBalanceValue.setText(String.format(Locale.CHINA, "¥%,.2f", account.getBalance()));
        
        if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
            ImageLoaderUtils.loadThumbnail(requireContext(), account.getIconUrl(), binding.ivAccountIcon);
        } else {
            binding.ivAccountIcon.setImageResource(R.drawable.ic_wallet);
        }

        if (account.getCreatedAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            binding.tvDateValue.setText(sdf.format(account.getCreatedAt()));
        } else {
            binding.tvDateValue.setText("未知");
        }

        binding.tvCardNumberValue.setText(TextUtils.isEmpty(account.getCardNumber()) ? "未设置" : account.getCardNumber());
        binding.tvRemarkValue.setText(TextUtils.isEmpty(account.getRemark()) ? "无" : account.getRemark());

        if (account.getGroupId() != null && !account.getGroupId().isEmpty()) {
            AppExecutors.get().diskIO().execute(() -> {
                AccountGroup group = accountViewModel.getAccountGroupByIdSync(account.getGroupId());
                AppExecutors.get().mainThread().execute(() -> {
                    if (group != null) {
                        binding.tvGroupValue.setText(group.getName());
                    }
                });
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
