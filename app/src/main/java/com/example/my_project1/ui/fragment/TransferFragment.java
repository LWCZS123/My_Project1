package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.FragmentTransferBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

public class TransferFragment extends Fragment {

    private FragmentTransferBinding binding;
    private OnTransferAccountSelectedListener listener;

    private Account sourceAccount;
    private Account targetAccount;

    public interface OnTransferAccountSelectedListener {
        void onAccountSelected(Account account, boolean isTarget);
        void onSwapRequested();
    }

    public static TransferFragment newInstance() {
        return new TransferFragment();
    }

    public void setOnTransferAccountSelectedListener(OnTransferAccountSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTransferBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.cardSource.setOnClickListener(v -> showAccountPicker(false));
        binding.cardTarget.setOnClickListener(v -> showAccountPicker(true));
        binding.btnSwap.setOnClickListener(v -> {
            if (listener != null) listener.onSwapRequested();
        });
    }

    private void showAccountPicker(boolean isTarget) {
        BillChooseAccountFragment picker = new BillChooseAccountFragment();
        picker.setOnAccountChooseListener((account, iconUrl, name) -> {
            if (isTarget) {
                updateTargetUi(account, iconUrl, name);
            } else {
                updateSourceUi(account, iconUrl, name);
            }
            if (listener != null) {
                listener.onAccountSelected(account, isTarget);
            }
        });
        picker.show(getChildFragmentManager(), "AccountPicker");
    }

    public void updateSourceUi(Account account, String iconUrl, String name) {
        this.sourceAccount = account;
        if (binding == null) return;
        binding.tvSourceName.setText(name != null ? name : "请选择转出账户");
        if (iconUrl != null) {
            binding.ivSourceIcon.setVisibility(View.VISIBLE);
            ImageLoaderUtils.load(requireContext(), iconUrl, binding.ivSourceIcon);
        } else {
            binding.ivSourceIcon.setVisibility(View.GONE);
        }
    }

    public void updateTargetUi(Account account, String iconUrl, String name) {
        this.targetAccount = account;
        if (binding == null) return;
        binding.tvTargetName.setText(name != null ? name : "请选择转入账户");
        if (iconUrl != null) {
            binding.ivTargetIcon.setVisibility(View.VISIBLE);
            ImageLoaderUtils.load(requireContext(), iconUrl, binding.ivTargetIcon);
        } else {
            binding.ivTargetIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
