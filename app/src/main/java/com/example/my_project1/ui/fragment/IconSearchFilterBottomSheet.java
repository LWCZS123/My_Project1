package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.ui.viewmodel.icon.IconMarketViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class IconSearchFilterBottomSheet extends BottomSheetDialogFragment {

    private IconMarketViewModel viewModel;
    private int currentScope = 0; // 0-图标, 1-合集
    
    private TextView btnScopeIcon;
    private TextView btnScopeCollection;
    private View btnReset;
    private View btnConfirm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_icon_search_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(IconMarketViewModel.class);

        btnScopeIcon = view.findViewById(R.id.btn_scope_icon);
        btnScopeCollection = view.findViewById(R.id.btn_scope_collection);
        btnReset = view.findViewById(R.id.btn_reset);
        btnConfirm = view.findViewById(R.id.btn_confirm);

        if (viewModel.searchScope.getValue() != null) {
            currentScope = viewModel.searchScope.getValue();
        }

        updateScopeUI();
        setupListeners();
    }

    private void setupListeners() {
        btnScopeIcon.setOnClickListener(v -> {
            currentScope = 0;
            updateScopeUI();
        });

        btnScopeCollection.setOnClickListener(v -> {
            currentScope = 1;
            updateScopeUI();
        });

        btnReset.setOnClickListener(v -> {
            currentScope = 0;
            updateScopeUI();
        });

        btnConfirm.setOnClickListener(v -> {
            viewModel.setSearchScope(currentScope);
            // 触发重新搜索
            String keyword = viewModel.currentKeyword.getValue();
            if (keyword != null && !keyword.isEmpty()) {
                viewModel.search(keyword);
            }
            dismiss();
        });
    }

    private void updateScopeUI() {
        if (currentScope == 0) {
            btnScopeIcon.setBackgroundResource(R.drawable.bg_pill_selected_market);
            btnScopeIcon.setTextColor(Color.WHITE);
            btnScopeCollection.setBackgroundResource(R.drawable.bg_pill_unselected_market);
            btnScopeCollection.setTextColor(Color.parseColor("#64748B"));
        } else {
            btnScopeIcon.setBackgroundResource(R.drawable.bg_pill_unselected_market);
            btnScopeIcon.setTextColor(Color.parseColor("#64748B"));
            btnScopeCollection.setBackgroundResource(R.drawable.bg_pill_selected_market);
            btnScopeCollection.setTextColor(Color.WHITE);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }
}
