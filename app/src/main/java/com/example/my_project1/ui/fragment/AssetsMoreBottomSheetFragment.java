package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.databinding.FragmentAssetsMoreBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AssetsMoreBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentAssetsMoreBottomSheetBinding binding;
    private OnOptionClickListener listener;

    public interface OnOptionClickListener {
        void onAssetGrouping();
        void onArchivedAccounts();
        void onHiddenAccounts();
    }

    public void setOnOptionClickListener(OnOptionClickListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAssetsMoreBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnAssetGroup.setOnClickListener(v -> {
            if (listener != null) listener.onAssetGrouping();
            dismiss();
        });

        binding.btnArchivedAccounts.setOnClickListener(v -> {
            if (listener != null) listener.onArchivedAccounts();
            dismiss();
        });

        binding.btnHiddenAccounts.setOnClickListener(v -> {
            if (listener != null) listener.onHiddenAccounts();
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
