package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.AccountGroup;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AccountGroupActionsBottomSheet extends BottomSheetDialogFragment {

    private AccountGroup group;
    private boolean isDefault;
    private OnActionClickListener listener;

    public interface OnActionClickListener {
        void onModify();
        void onSort();
        void onDelete();
    }

    public static AccountGroupActionsBottomSheet newInstance(AccountGroup group, boolean isDefault) {
        AccountGroupActionsBottomSheet fragment = new AccountGroupActionsBottomSheet();
        fragment.group = group;
        fragment.isDefault = isDefault;
        return fragment;
    }

    public void setOnActionClickListener(OnActionClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = (com.google.android.material.bottomsheet.BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            android.widget.FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 🔑 修复圆角背景下的白色层：确保 design_bottom_sheet 及其父容器背景透明
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                if (bottomSheet.getParent() instanceof View) {
                    ((View) bottomSheet.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView tvTitle = view.findViewById(R.id.tv_header_title);
        TextView btn1 = view.findViewById(R.id.btn_action_1);
        TextView btn2 = view.findViewById(R.id.btn_action_2);
        TextView btn3 = view.findViewById(R.id.btn_action_3);
        View d1 = view.findViewById(R.id.divider_1);
        View d2 = view.findViewById(R.id.divider_2);
        View d3 = view.findViewById(R.id.divider_3);
        View d4 = view.findViewById(R.id.divider_4);
        if (d4 != null) {
            // Keep d4 for potential use
        }
        TextView btnCancel = view.findViewById(R.id.btn_cancel);

        tvTitle.setText(group.getName());
        
        btn1.setText("修改");
        btn2.setText("排序");
        btn3.setText("删除");
        btn3.setTextColor(0xFFFF5252); 
        
        if (isDefault) {
            btn1.setVisibility(View.GONE);
            if (d1 != null) d1.setVisibility(View.GONE);
            if (d2 != null) d2.setVisibility(View.GONE); // Default: hide everything except sort
            
            btn3.setVisibility(View.GONE);
            if (d3 != null) d3.setVisibility(View.GONE);
        }

        btn1.setOnClickListener(v -> {
            if (listener != null) listener.onModify();
            dismiss();
        });

        btn2.setOnClickListener(v -> {
            if (listener != null) listener.onSort();
            dismiss();
        });

        btn3.setOnClickListener(v -> {
            if (listener != null) listener.onDelete();
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());
    }
}
