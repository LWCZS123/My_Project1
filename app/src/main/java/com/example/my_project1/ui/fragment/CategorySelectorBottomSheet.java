package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategorySelectItem;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.ui.adapter.CategorySelectAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * CategorySelectorBottomSheet - 通用分类选择器 (复用迁移界面的 UI)
 */
public class CategorySelectorBottomSheet extends BottomSheetDialogFragment {

    private CategoryViewModel viewModel;
    private CategorySelectAdapter adapter;
    private OnCategorySelectedListener listener;

    public interface OnCategorySelectedListener {
        void onSelected(CategorySelectItem item);
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category_migration, container, false);
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        tvTitle.setText("选择分类");
        
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        adapter = new CategorySelectAdapter(requireContext());
        recyclerView.setAdapter(adapter);
        
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        adapter.setOnItemClickListener(item -> {
            if (listener != null) listener.onSelected(item);
            dismiss();
        });

        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
        loadData();
        
        return view;
    }

    private void loadData() {
        BmobUser user = BmobUser.getCurrentUser();
        if (user == null) return;
        String userId = user.getObjectId();
        
        viewModel.getExpenseCategories(userId).observe(getViewLifecycleOwner(), list -> {
            if (list == null) return;
            List<CategorySelectItem> selectItems = new ArrayList<>();
            for (CategoryWithSubCategories cw : list) {
                Category cat = cw.category;
                if (cat.getArchiveStatus() != null && cat.getArchiveStatus() == 1) continue;
                if (cat.getSyncState() == 3) continue;
                
                // 显示一级分类
                selectItems.add(new CategorySelectItem(cat.getCloudId(), cat.getName(), cat.getIconUri(), 1, cat.getIconBackgroundColor(), cat));
                
                // 显示二级分类
                if (cw.subCategories != null) {
                    for (SubCategory sub : cw.subCategories) {
                        if (sub.getArchiveStatus() != null && sub.getArchiveStatus() == 1) continue;
                        if (sub.getSyncState() == 3) continue;
                        selectItems.add(new CategorySelectItem(sub.getCloudId(), sub.getName(), sub.getIconUri(), 2, sub.getIconBackgroundColor(), sub));
                    }
                }
            }
            adapter.submitList(selectItems);
        });
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                
                // 优化高度：从 0.8 降低到 0.65，避免过高
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.65);
                bottomSheet.setLayoutParams(layoutParams);

                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }
}
