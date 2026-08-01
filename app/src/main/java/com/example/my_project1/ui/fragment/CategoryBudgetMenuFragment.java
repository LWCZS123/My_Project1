package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.data.model.budget.Budget;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Category Budget Action Menu
 */
public class CategoryBudgetMenuFragment extends BottomSheetDialogFragment {

    public static final String TAG = "CategoryBudgetMenu";
    private Budget budget;
    private OnMenuActionListener listener;

    public interface OnMenuActionListener {
        void onEdit(Budget budget);
        void onDelete(Budget budget);
    }

    public static CategoryBudgetMenuFragment newInstance(Budget budget) {
        CategoryBudgetMenuFragment fragment = new CategoryBudgetMenuFragment();
        fragment.budget = budget;
        return fragment;
    }

    public void setOnMenuActionListener(OnMenuActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_category_budget_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (budget == null) {
            dismiss();
            return;
        }

        LinearLayout layoutEdit = view.findViewById(R.id.layout_edit);
        LinearLayout layoutDelete = view.findViewById(R.id.layout_delete);

        layoutEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(budget);
            dismiss();
        });

        layoutDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(budget);
            dismiss();
        });
    }
}
