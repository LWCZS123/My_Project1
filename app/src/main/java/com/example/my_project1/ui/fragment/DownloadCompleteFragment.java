package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.example.my_project1.databinding.FragmentDownloadCompleteBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class DownloadCompleteFragment extends BottomSheetDialogFragment {

    private FragmentDownloadCompleteBinding binding;

    public static DownloadCompleteFragment newInstance(int total, int success, int failed, String path, String collectionName, String treeUri) {
        DownloadCompleteFragment f = new DownloadCompleteFragment();
        Bundle args = new Bundle();
        args.putInt("total", total);
        args.putInt("success", success);
        args.putInt("failed", failed);
        args.putString("path", path);
        args.putString("collectionName", collectionName);
        args.putString("treeUri", treeUri);
        f.setArguments(args);
        return f;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogStyle;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            View sheet = ((com.google.android.material.bottomsheet.BottomSheetDialog) getDialog()).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent);
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                sheet.requestLayout();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDownloadCompleteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    // ======================== 监听结果（只用来提示，不控制关闭） ========================
    // 上面这个注释可能属于其他文件或粘贴错误，保持 Fragment 逻辑纯粹

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            int total = args.getInt("total");
            int success = args.getInt("success");
            int failed = args.getInt("failed");
            String path = args.getString("path");

            binding.tvIconCount.setText(total + " 枚");
            binding.tvFilePath.setText(path);
            binding.tvSubtitle.setText("已完成下载 " + success + " 个，失败 " + failed + " 个");
            
            if (failed == 0) {
                binding.tvTitle.setText("下载完成！");
            } else {
                binding.tvTitle.setText("下载结束");
            }
        }

        binding.btnOpenFile.setOnClickListener(v -> {
            dismiss();
            Bundle args2 = getArguments();
            String subDir = args2 != null ? args2.getString("collectionName") : null;
            String treeUri = args2 != null ? args2.getString("treeUri") : null;
            com.example.my_project1.utils.DownloadPathManager.getInstance(requireContext())
                    .openDownloadFolder(subDir, treeUri);
        });

        binding.btnContinue.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
