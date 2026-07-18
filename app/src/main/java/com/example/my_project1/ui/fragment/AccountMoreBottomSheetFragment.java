package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.LayoutAccountMoreBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AccountMoreBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnOptionClickListener {
        void onEdit();
        void onHide();
        void onArchive();
        void onAccountInfo();
        void onConsumerInstallment();
        void onBillInstallment();
        void onDelete();
    }

    private LayoutAccountMoreBottomSheetBinding binding;
    private OnOptionClickListener listener;
    private Account account;

    public static AccountMoreBottomSheetFragment newInstance(Account account) {
        AccountMoreBottomSheetFragment fragment = new AccountMoreBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable("account", account);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnOptionClickListener(OnOptionClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            account = (Account) getArguments().getSerializable("account");
        }
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAccountMoreBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (account != null && !account.isCredit()) {
            binding.tvConsumerInstallment.setVisibility(View.GONE);
            binding.tvBillInstallment.setVisibility(View.GONE);
            binding.tvDividerInstallment1.setVisibility(View.GONE);
            binding.tvDividerInstallment2.setVisibility(View.GONE);
        }

        binding.tvEditAccount.setOnClickListener(v -> {
            if (listener != null) listener.onEdit();
            dismiss();
        });

        binding.tvHideAccount.setOnClickListener(v -> {
            if (listener != null) listener.onHide();
            dismiss();
        });

        binding.tvArchiveAccount.setOnClickListener(v -> {
            if (listener != null) listener.onArchive();
            dismiss();
        });

        binding.tvAccountInfo.setOnClickListener(v -> {
            if (listener != null) listener.onAccountInfo();
            dismiss();
        });

        binding.tvConsumerInstallment.setOnClickListener(v -> {
            if (listener != null) listener.onConsumerInstallment();
            dismiss();
        });

        binding.tvBillInstallment.setOnClickListener(v -> {
            if (listener != null) listener.onBillInstallment();
            dismiss();
        });

        binding.tvDeleteAccount.setOnClickListener(v -> {
            if (listener != null) listener.onDelete();
            dismiss();
        });

        binding.tvCancel.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
