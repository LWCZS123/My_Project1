package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

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
 * 分类数据迁移 / 归属调整 底部对话框
 * 修复了可能导致崩溃的 NPE 和 ID 转换问题
 */
public class CategoryMigrationBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String ARG_TYPE = "type"; // "migrate" (数据迁移) or "change_parent" (调整归属)
    public static final String ARG_SOURCE_ID = "source_id";
    public static final String ARG_SOURCE_NAME = "source_name";
    public static final String ARG_CATEGORY_TYPE = "category_type"; // expense/income

    private String migrationType, sourceId, sourceName, categoryType;
    private CategoryViewModel viewModel;
    private CategorySelectAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvTitle;

    public static CategoryMigrationBottomSheetFragment newInstance(String type, String sourceId, String sourceName, String categoryType) {
        CategoryMigrationBottomSheetFragment fragment = new CategoryMigrationBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_SOURCE_ID, sourceId);
        args.putString(ARG_SOURCE_NAME, sourceName);
        args.putString(ARG_CATEGORY_TYPE, categoryType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            migrationType = getArguments().getString(ARG_TYPE);
            sourceId = getArguments().getString(ARG_SOURCE_ID);
            sourceName = getArguments().getString(ARG_SOURCE_NAME);
            categoryType = getArguments().getString(ARG_CATEGORY_TYPE);
        }
        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
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
                
                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
                bottomSheet.setLayoutParams(layoutParams);
                
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category_migration, container, false);
        tvTitle = view.findViewById(R.id.tvTitle);
        recyclerView = view.findViewById(R.id.recyclerView);
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        String titlePrefix = "migrate".equals(migrationType) ? "将「" + sourceName + "」的数据迁移至" : "将「" + sourceName + "」归属到";
        tvTitle.setText(titlePrefix);

        setupRecyclerView();
        loadData();
        return view;
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        adapter = new CategorySelectAdapter(requireContext());
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            if ("migrate".equals(migrationType)) {
                performMigration(item);
            } else {
                performChangeParent(item);
            }
        });

        viewModel.operationState.observe(getViewLifecycleOwner(), response -> {
            if (response == null) return;
            if (response.isSuccess()) {
                showToast(response.getMessage());
                dismiss();
            } else if (response.isError()) {
                showToast(response.getMessage());
            }
        });
    }

    private void loadData() {
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser == null) {
            showToast("请先登录");
            dismiss();
            return;
        }
        String userId = currentUser.getObjectId();
        if ("expense".equals(categoryType)) {
            viewModel.getExpenseCategories(userId).observe(getViewLifecycleOwner(), this::processCategories);
        } else {
            viewModel.getIncomeCategories(userId).observe(getViewLifecycleOwner(), this::processCategories);
        }
    }

    private void processCategories(List<CategoryWithSubCategories> list) {
        if (list == null) return;
        List<CategorySelectItem> selectItems = new ArrayList<>();
        for (CategoryWithSubCategories cw : list) {
            Category cat = cw.category;
            if (cat == null) continue;
            
            // 过滤已归档分类
            if (cat.getArchiveStatus() != null && cat.getArchiveStatus() == 1) continue;

            if ("migrate".equals(migrationType)) {
                // 迁移数据逻辑
                if (cw.subCategories == null || cw.subCategories.isEmpty()) {
                    if (cat.getCloudId() != null && !cat.getCloudId().equals(sourceId)) {
                        selectItems.add(new CategorySelectItem(cat.getCloudId(), cat.getName(), cat.getIconUri(), 1, cat.getIconBackgroundColor(), cat));
                    }
                } else {
                    for (SubCategory sub : cw.subCategories) {
                        if (sub.getArchiveStatus() != null && sub.getArchiveStatus() == 1) continue;
                        if (sub.getSyncState() == 3) continue;

                        if (sub.getCloudId() != null && !sub.getCloudId().equals(sourceId)) {
                            selectItems.add(new CategorySelectItem(sub.getCloudId(), sub.getName(), sub.getIconUri(), 2, sub.getIconBackgroundColor(), sub));
                        }
                    }
                }
            } else {
                // 调整归属逻辑：仅限一级分类
                long currentParentId = getArguments() != null ? getArguments().getLong("currentParentId", -1) : -1;
                if (cat.getId() != currentParentId) {
                    selectItems.add(new CategorySelectItem(cat.getCloudId(), cat.getName(), cat.getIconUri(), 1, cat.getIconBackgroundColor(), cat));
                }
            }
        }
        adapter.submitList(selectItems);
    }

    private void performMigration(CategorySelectItem targetItem) {
        BmobUser user = BmobUser.getCurrentUser();
        if (user == null) return;
        
        String userId = user.getObjectId();
        Category target = null;
        Object original = targetItem.getOriginalData();
        
        if (original instanceof Category) {
            target = (Category) original;
        } else if (original instanceof SubCategory) {
            SubCategory sub = (SubCategory) original;
            target = new Category();
            target.setCloudId(sub.getCloudId());
            target.setName(sub.getName());
            target.setIconUri(sub.getIconUri());
            target.setIconBackgroundColor(sub.getIconBackgroundColor());
        }

        if (target != null && target.getCloudId() != null) {
            viewModel.migrateCategoryData(userId, sourceId, target);
            showToast("正在迁移账单数据...");
            dismiss();
        } else {
            showToast("无效的目标分类");
        }
    }

    private void performChangeParent(CategorySelectItem targetItem) {
        if (!(targetItem.getOriginalData() instanceof Category)) {
            showToast("请选择一级分类作为归属目标");
            return;
        }

        Category newParent = (Category) targetItem.getOriginalData();
        try {
            if (sourceId == null || sourceId.isEmpty()) {
                showToast("分类 ID 缺失");
                return;
            }
            long subId = Long.parseLong(sourceId);
            viewModel.changeParentCategory(subId, newParent);
            showToast("归属调整成功");
            dismiss();
        } catch (NumberFormatException e) {
            showToast("无效的分类 ID 格式");
        }
    }

    private void showToast(String message) {
        if (getContext() != null && message != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
