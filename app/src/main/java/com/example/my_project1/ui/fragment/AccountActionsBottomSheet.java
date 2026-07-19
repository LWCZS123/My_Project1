package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AccountActionsBottomSheet extends BottomSheetDialogFragment {

    private Account account;
    private OnActionClickListener listener;

    public interface OnActionClickListener {
        void onMoveToGroup();
        void onDetails();
        void onSort();
    }

    public static AccountActionsBottomSheet newInstance(Account account) {
        AccountActionsBottomSheet fragment = new AccountActionsBottomSheet();
        fragment.account = account;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_actions, container, false);
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                if (bottomSheet.getParent() instanceof View) {
                    ((View) bottomSheet.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
                }
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView tvTitle = view.findViewById(R.id.tv_header_title);
        TextView btn1 = view.findViewById(R.id.btn_action_1);
        TextView btn2 = view.findViewById(R.id.btn_action_2);
        TextView btn3 = view.findViewById(R.id.btn_action_3);
        TextView btnCancel = view.findViewById(R.id.btn_cancel);

        tvTitle.setText(account.getName());
        
        btn1.setText("移动到其他自定义分组");
        btn2.setText("账户详情");
        btn3.setText("排序");

        btn1.setOnClickListener(v -> {
            if (listener != null) listener.onMoveToGroup();
            dismiss();
        });

        btn2.setOnClickListener(v -> {
            if (listener != null) listener.onDetails();
            dismiss();
        });

        btn3.setOnClickListener(v -> {
            if (listener != null) listener.onSort();
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());
    }
}
