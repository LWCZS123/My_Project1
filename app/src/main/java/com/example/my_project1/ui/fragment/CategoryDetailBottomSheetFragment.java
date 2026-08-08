package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.databinding.FragmentCategoryDetailBottomSheetBinding;
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.adapter.SubCategoryGridAdapter;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class CategoryDetailBottomSheetFragment extends BottomSheetDialogFragment {

    private FragmentCategoryDetailBottomSheetBinding binding;
    private SubCategoryViewModel subCategoryViewModel;
    private Category category;
    private SubCategoryGridAdapter adapter;
    private boolean isSorting = false;

    public static CategoryDetailBottomSheetFragment newInstance(Category category) {
        CategoryDetailBottomSheetFragment fragment = new CategoryDetailBottomSheetFragment();
        fragment.category = category;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subCategoryViewModel = new ViewModelProvider(this).get(SubCategoryViewModel.class);
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = (com.google.android.material.bottomsheet.BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialogInterface;
            android.view.View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryDetailBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (category == null) {
            dismiss();
            return;
        }

        binding.textViewCategoryName.setText(category.getName());

        binding.textViewCategoryName.setOnClickListener(v -> viewBills());

        initRecyclerView();
        observeSubCategories();
        initTabs();
    }

    private void viewBills() {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.my_project1.ui.activity.CategoryBillsActivity.class);
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_CATEGORY_NAME, category.getName());
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_CATEGORY_ICON, category.getIconUri());
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_CATEGORY_ID, category.getCloudId());
        int billType = "expense".equals(category.getType()) ? 0 : 1;
        intent.putExtra(com.example.my_project1.ui.activity.CategoryBillsActivity.EXTRA_BILL_TYPE, billType);
        startActivity(intent);
        dismiss();
    }

    private void initRecyclerView() {
        adapter = new SubCategoryGridAdapter(requireContext());
        binding.recyclerViewSubGrid.setAdapter(adapter);

        // 设置 ItemTouchHelper 实现拖拽
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();

                List<SubCategory> list = adapter.getCurrentList();
                // 不允许拖动到 "添加" 按钮，也不允许拖动 "添加" 按钮
                if (fromPos >= list.size() - 1 || toPos >= list.size() - 1) {
                    return false;
                }

                // 直接同步交换适配器内部数据，杜绝回弹和重复
                adapter.moveItem(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    isSorting = true;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 停止拖拽时保存新排序
                saveOrder();
                
                // 延长锁定时间，防止数据库异步回调刷新回旧顺序
                recyclerView.postDelayed(() -> isSorting = false, 1000);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        touchHelper.attachToRecyclerView(binding.recyclerViewSubGrid);

        adapter.setOnSubCategoryClickListener(new SubCategoryGridAdapter.OnSubCategoryClickListener() {
            @Override
            public void onSubCategoryClick(SubCategory subCategory) {
                CategoryMoreBottomSheetFragment dialog = new CategoryMoreBottomSheetFragment();
                Bundle args = new Bundle();
                args.putString("title","二级分类");
                args.putString("categoryName",subCategory.getName());
                args.putString("categoryIconUrl",subCategory.getIconUri());
                args.putLong("subcategoryId",subCategory.getId());
                args.putString("type","subcategory");
                args.putString("categoryCloudId", subCategory.getCloudId());
                args.putString("categoryType", category.getType());
                dialog.setArguments(args);
                dialog.show(getParentFragmentManager(), "subcategory_more");
            }

            @Override
            public void onAddSubCategoryClick() {
                android.content.Intent intent = new android.content.Intent(requireContext(), IconSelectionActivity.class);
                intent.putExtra(IconSelectionActivity.EXTRA_MODE, "add");
                intent.putExtra(IconSelectionActivity.EXTRA_TYPE, "subcategory");
                intent.putExtra(IconSelectionActivity.EXTRA_TITLE, "新建二级分类");
                intent.putExtra(IconSelectionActivity.EXTRA_PARENT_ID, category.getId());
                intent.putExtra(IconSelectionActivity.EXTRA_PARENT_CLOUD_ID, category.getCloudId());
                startActivity(intent);
            }

            @Override
            public void onSubCategoryLongClick(SubCategory subCategory) {
                // TODO: 开启排序
            }
        });
    }

    private void observeSubCategories() {
        subCategoryViewModel.getSubCategoriesByParent(category.getId())
                .observe(getViewLifecycleOwner(), subCategories -> {
                    if (isSorting || subCategories == null) return;
                    List<SubCategory> activeList = new ArrayList<>();
                    for (SubCategory sub : subCategories) {
                        if ((sub.getArchiveStatus() == null || sub.getArchiveStatus() == 0)
                                && sub.getSyncState() != 3) {
                            activeList.add(sub);
                        }
                    }
                    activeList.add(SubCategory.createAddButton());
                    adapter.submitList(activeList);
                });
    }

    private void saveOrder() {
        List<SubCategory> currentList = adapter.getCurrentList();
        // 去掉最后的 "添加" 按钮
        List<SubCategory> toSave = new ArrayList<>();
        for (SubCategory sub : currentList) {
            if (!sub.isAddButton()) {
                toSave.add(sub);
            }
        }

        // 更新 sortIndex 字段并保存到数据库
        for (int i = 0; i < toSave.size(); i++) {
            SubCategory sub = toSave.get(i);
            if (sub.getSortIndex() != i) {
                subCategoryViewModel.updateSortIndex(sub.getId(), i);
            }
        }
    }

    private void initTabs() {
        binding.tabExpense.setOnClickListener(v -> updateTabSelection(0));
        binding.tabIncome.setOnClickListener(v -> updateTabSelection(1));
        binding.tabRules.setOnClickListener(v -> updateTabSelection(2));
    }

    private void updateTabSelection(int index) {
        binding.tabExpense.setSelected(index == 0);
        binding.tabIncome.setSelected(index == 1);
        binding.tabRules.setSelected(index == 2);

        // 更新样式 (这里简单处理，实际应使用 ColorStateList 或更优雅的方案)
        binding.tabExpense.setBackgroundResource(index == 0 ? R.drawable.tab_selected_bg : 0);
        binding.tabExpense.setTextColor(index == 0 ? 0xFFFFFFFF : 0xFF666666);
        
        binding.tabIncome.setBackgroundResource(index == 1 ? R.drawable.tab_selected_bg : 0);
        binding.tabIncome.setTextColor(index == 1 ? 0xFFFFFFFF : 0xFF666666);
        
        binding.tabRules.setBackgroundResource(index == 2 ? R.drawable.tab_selected_bg : 0);
        binding.tabRules.setTextColor(index == 2 ? 0xFFFFFFFF : 0xFF666666);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
