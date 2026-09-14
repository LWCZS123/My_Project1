package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.my_project1.databinding.FragmentDownloadCompleteBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class DownloadCompleteFragment extends BottomSheetDialogFragment {

    private FragmentDownloadCompleteBinding binding;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public int getTheme() {
        return com.example.my_project1.R.style.BottomSheetDialogStyle;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            android.view.View sheet = ((com.google.android.material.bottomsheet.BottomSheetDialog) getDialog()).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent);
                sheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                sheet.requestLayout();
            }
        }
    }

    @Nullable
    @Override
    public android.view.View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDownloadCompleteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.btnOpenFile.setOnClickListener(v -> {
            // 处理打开文件逻辑
            dismiss();
        });
        
        binding.btnContinue.setOnClickListener(v -> {
            // 处理继续下载逻辑
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}