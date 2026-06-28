package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.databinding.FragmentRemarkBottomSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * 备注输入底部弹窗 - 使用ViewBinding
 * 支持长文本输入和滑动
 */
public class RemarkBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentRemarkBottomSheetBinding binding;

    private String currentRemark = "";
    private static final int MAX_LENGTH = 500;

    private OnRemarkConfirmListener listener;

    public interface OnRemarkConfirmListener {
        void onRemarkConfirm(String remark);
    }

    public static RemarkBottomSheetFragment newInstance(String currentRemark) {
        RemarkBottomSheetFragment fragment = new RemarkBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("remark", currentRemark);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentRemark = getArguments().getString("remark", "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRemarkBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupListeners();
        loadCurrentRemark();
    }

    private void setupListeners() {
        // 输入监听
        binding.etRemark.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCharCount(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 确认按钮
        binding.btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                String remark = binding.etRemark.getText().toString().trim();
                listener.onRemarkConfirm(remark);
            }
            dismiss();
        });

        // 取消按钮
        binding.btnCancel.setOnClickListener(v -> dismiss());
    }

    private void loadCurrentRemark() {
        if (currentRemark != null && !currentRemark.isEmpty()) {
            binding.etRemark.setText(currentRemark);
            binding.etRemark.setSelection(currentRemark.length());
        }
        updateCharCount(currentRemark.length());
    }

    private void updateCharCount(int length) {
        binding.tvCharCount.setText(length + "/" + MAX_LENGTH);

        if (length > MAX_LENGTH) {
            binding.tvCharCount.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        } else {
            binding.tvCharCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    public void setOnRemarkConfirmListener(OnRemarkConfirmListener listener) {
        this.listener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(dlg -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {

                // 设置圆角背景
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_background)
                );

                // 一打开就全展开
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return dialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}