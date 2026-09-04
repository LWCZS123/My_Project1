package com.example.my_project1.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * 持续时长输入底栏
 */
public class DurationInputBottomSheet extends BottomSheetDialogFragment {

    private String initialValue;
    private OnDurationInputListener listener;

    public interface OnDurationInputListener {
        void onInput(String value);
    }

    public static DurationInputBottomSheet newInstance(String initialValue) {
        DurationInputBottomSheet fragment = new DurationInputBottomSheet();
        Bundle args = new Bundle();
        args.putString("initial", initialValue);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnDurationInputListener(OnDurationInputListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialValue = getArguments().getString("initial");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input_duration, container, false);
        
        EditText etInput = view.findViewById(R.id.et_input);
        TextView btnDone = view.findViewById(R.id.btn_done);
        ImageView btnClose = view.findViewById(R.id.btn_close);
        ImageView btnClear = view.findViewById(R.id.btn_clear);

        if (initialValue != null) {
            etInput.setText(initialValue.replaceAll("[^0-9]", ""));
            etInput.setSelection(etInput.getText().length());
        }

        btnDone.setOnClickListener(v -> {
            if (listener != null) listener.onInput(etInput.getText().toString());
            dismiss();
        });

        btnClose.setOnClickListener(v -> dismiss());
        btnClear.setOnClickListener(v -> etInput.setText(""));

        // 自动弹出键盘
        etInput.post(() -> {
            etInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        return view;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogStyle;
    }
}
