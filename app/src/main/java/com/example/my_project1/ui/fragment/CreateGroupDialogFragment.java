package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.my_project1.R;

public class CreateGroupDialogFragment extends DialogFragment {

    private OnGroupCreateListener listener;
    private String editGroupId;
    private String initialName;

    public interface OnGroupCreateListener {
        void onCreate(String name);
        void onUpdate(String id, String newName);
    }

    public static CreateGroupDialogFragment newInstance(String id, String name) {
        CreateGroupDialogFragment fragment = new CreateGroupDialogFragment();
        fragment.editGroupId = id;
        fragment.initialName = name;
        return fragment;
    }

    public void setOnGroupCreateListener(OnGroupCreateListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_create_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        ImageView ivClose = view.findViewById(R.id.iv_close);
        ImageView ivClear = view.findViewById(R.id.iv_clear);
        EditText etName = view.findViewById(R.id.et_name);
        TextView tvDone = view.findViewById(R.id.tv_done);
        TextView tvTitle = view.findViewById(R.id.tv_title);

        if (editGroupId != null) {
            tvTitle.setText("修改分组名称");
            etName.setText(initialName);
            if (initialName != null) etName.setSelection(initialName.length());
            ivClear.setVisibility(View.VISIBLE);
        }

        ivClose.setOnClickListener(v -> dismiss());
        
        ivClear.setOnClickListener(v -> etName.setText(""));
        
        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                ivClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        tvDone.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                if (listener != null) {
                    if (editGroupId != null) {
                        listener.onUpdate(editGroupId, name);
                    } else {
                        listener.onCreate(name);
                    }
                }
                dismiss();
            }
        });

        // 🔑 自动弹出软键盘逻辑优化
        etName.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
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
            Window window = getDialog().getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            // 🔑 关键修复：设置 Gravity 为底部，使其贴近键盘
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = android.view.Gravity.BOTTOM;
            params.y = 10; // 稍微向上偏移一点点，美观
            window.setAttributes(params);
        }
    }
}
