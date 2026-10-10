package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.databinding.FragmentMoreSettingsBottomSheetBinding;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class MoreSettingsBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnSettingsConfirmedListener {
        void onConfirmed(boolean excludeBudget, boolean excludeStat);
    }

    private FragmentMoreSettingsBottomSheetBinding binding;
    private boolean excludeBudget = false;
    private boolean excludeStat = false;
    private OnSettingsConfirmedListener listener;

    public static MoreSettingsBottomSheetFragment newInstance(boolean excludeBudget, boolean excludeStat) {
        MoreSettingsBottomSheetFragment fragment = new MoreSettingsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putBoolean("exclude_budget", excludeBudget);
        args.putBoolean("exclude_stat", excludeStat);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnSettingsConfirmedListener(OnSettingsConfirmedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            excludeBudget = getArguments().getBoolean("exclude_budget", false);
            excludeStat = getArguments().getBoolean("exclude_stat", false);
        }
        setStyle(STYLE_NORMAL, R.style.WhiteNavigationBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            BottomSheetNavigationBarUtils.setup(dialog);
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            BottomSheetNavigationBarUtils.applyLightAppearance(window);
            window.getDecorView().post(() -> BottomSheetNavigationBarUtils.applyLightAppearance(window));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMoreSettingsBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updateCheckboxViews();

        binding.cardExcludeBudget.setOnClickListener(v -> {
            excludeBudget = !excludeBudget;
            updateCheckboxViews();
        });

        binding.cardExcludeStat.setOnClickListener(v -> {
            excludeStat = !excludeStat;
            updateCheckboxViews();
        });

        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.btnDone.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConfirmed(excludeBudget, excludeStat);
            }
            dismiss();
        });
    }

    private void updateCheckboxViews() {
        binding.ivExcludeBudgetCheck.setImageResource(
                excludeBudget ? R.drawable.ic_checkbox_checked : R.drawable.ic_checkbox_unchecked
        );
        binding.ivExcludeStatCheck.setImageResource(
                excludeStat ? R.drawable.ic_checkbox_checked : R.drawable.ic_checkbox_unchecked
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
