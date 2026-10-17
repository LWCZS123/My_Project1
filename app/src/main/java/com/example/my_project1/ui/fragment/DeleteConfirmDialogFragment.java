package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.my_project1.R;

public class DeleteConfirmDialogFragment extends DialogFragment {

    private OnDeleteConfirmListener listener;

    public interface OnDeleteConfirmListener {
        void onConfirm();
    }

    public static DeleteConfirmDialogFragment newInstance(String title, String message) {
        DeleteConfirmDialogFragment fragment = new DeleteConfirmDialogFragment();
        Bundle args = new Bundle();
        args.putString("arg_title", title);
        args.putString("arg_message", message);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDeleteConfirmListener(OnDeleteConfirmListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_delete_confirm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_title);
        TextView tvMessage = view.findViewById(R.id.tv_message);

        if (getArguments() != null) {
            String title = getArguments().getString("arg_title");
            String message = getArguments().getString("arg_message");
            if (title != null && tvTitle != null) tvTitle.setText(title);
            if (message != null && tvMessage != null) tvMessage.setText(message);
        }

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            if (listener != null) listener.onConfirm();
            dismiss();
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
