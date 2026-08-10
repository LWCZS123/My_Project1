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

public class CategoryMigrationBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String ARG_TYPE = "type"; // "migrate" or "change_parent"
    public static final String ARG_SOURCE_ID = "source_id";
    public static final String ARG_SOURCE_NAME = "source_name";
    public static final String ARG_CATEGORY_TYPE = "category_type"; // expense/income

    private String type, sourceId, sourceName, categoryType;
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
            type = getArguments().getString(ARG_TYPE);
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
                
                // 增加高度，保持 80% 屏幕高度
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

        if ("migrate".equals(type)) {
            tvTitle.setText("将「" + sourceName + "」的数据迁移至");
        } else {
            tvTitle.setText("将「" + sourceName + "」归属到");
        }

        setupRecyclerView();
        loadData();
        return view;
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        adapter = new CategorySelectAdapter(requireContext());
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            if ("migrate".equals(type)) {
                performMigration(item);
            } else {
                performChangeParent(item);
            }
        });

        // 观察迁移/晋升结果并给予反馈
        viewModel.operationState.observe(getViewLifecycleOwner(), response -> {
            if (response.isSuccess()) {
                Toast.makeText(requireContext(), response.getMessage(), Toast.LENGTH_SHORT).show();
                dismiss();
            } else if (response.isError()) {
                Toast.makeText(requireContext(), response.getMessage(), Toast.LENGTH_SHORT).show();
                // 如果是因为没有账单，不需要 dismiss，或者根据需求决定
            }
        });
    }

    private void loadData() {
        String userId = BmobUser.getCurrentUser().getObjectId();
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
            // 过滤掉已归档的
            if (cat.getArchiveStatus() != null && cat.getArchiveStatus() == 1) continue;

            if ("migrate".equals(type)) {
                // 迁移数据逻辑：
                // 如果一级分类没有二级分类，显示一级分类
                // 如果一级分类有二级分类，不显示一级分类，显示所有二级分类
                if (cw.subCategories == null || cw.subCategories.isEmpty()) {
                    // 排除自身
                    if (!cat.getCloudId().equals(sourceId)) {
                        selectItems.add(new CategorySelectItem(cat.getCloudId(), cat.getName(), cat.getIconUri(), 1, cat.getIconBackgroundColor(), cat));
                    }
                } else {
                    for (SubCategory sub : cw.subCategories) {
                        // 过滤已归档或待删除二级分类
                        if (sub.getArchiveStatus() != null && sub.getArchiveStatus() == 1) continue;
                        if (sub.getSyncState() == 3) continue;

                        // 排除自身
                        if (!sub.getCloudId().equals(sourceId)) {
                            selectItems.add(new CategorySelectItem(sub.getCloudId(), sub.getName(), sub.getIconUri(), 2, sub.getIconBackgroundColor(), sub));
                        }
                    }
                }
            } else {
                // 调整归属逻辑：只能选一级分类
                // 排除当前父级
                long currentParentId = getArguments() != null ? getArguments().getLong("currentParentId", -1) : -1;
                if (cat.getId() != currentParentId) {
                    selectItems.add(new CategorySelectItem(cat.getCloudId(), cat.getName(), cat.getIconUri(), 1, cat.getIconBackgroundColor(), cat));
                }
            }
        }
        adapter.submitList(selectItems);
    }

    private void performMigration(CategorySelectItem targetItem) {
        String userId = BmobUser.getCurrentUser().getObjectId();
        Category target = null;
        if (targetItem.getOriginalData() instanceof Category) {
            target = (Category) targetItem.getOriginalData();
        } else if (targetItem.getOriginalData() instanceof SubCategory) {
            SubCategory sub = (SubCategory) targetItem.getOriginalData();
            // 构造一个简单的 Category 对象供 Repository 使用冗余字段
            target = new Category();
            target.setCloudId(sub.getCloudId());
            target.setName(sub.getName());
            target.setIconUri(sub.getIconUri());
            target.setIconBackgroundColor(sub.getIconBackgroundColor());
        }

        if (target != null) {
            viewModel.migrateCategoryData(userId, sourceId, target);
            Toast.makeText(requireContext(), "正在迁移账单数据...", Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private void performChangeParent(CategorySelectItem targetItem) {
        if (!(targetItem.getOriginalData() instanceof Category)) {
            Toast.makeText(requireContext(), "请选择一级分类作为归属目标", Toast.LENGTH_SHORT).show();
            return;
        }

        Category newParent = (Category) targetItem.getOriginalData();
        // 这里 sourceId 是子分类的本地 ID（Repository 需要的是 long id，或者我们统一用 cloudId）
        // Repository changeSubCategoryParent 目前用的是 long id。
        // 我们需要确保传递过来的是 long id 字符串。
        try {
            long subId = Long.parseLong(sourceId);
            viewModel.changeParentCategory(subId, newParent);
            Toast.makeText(requireContext(), "归属调整成功", Toast.LENGTH_SHORT).show();
            dismiss();
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "无效的分类 ID", Toast.LENGTH_SHORT).show();
        }
    }
}
