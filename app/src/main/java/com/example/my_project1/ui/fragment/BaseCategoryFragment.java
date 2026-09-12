package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.adapter.CategoryAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * 分类列表基础 Fragment
 * 提取支出和收入分类片段的共有逻辑
 */
public abstract class BaseCategoryFragment extends Fragment {

    protected RecyclerView recyclerView;
    protected LinearProgressIndicator progressBar;
    protected View emptyLayout;
    protected CategoryAdapter adapter;
    protected CategoryViewModel categoryViewModel;
    protected SubCategoryViewModel subCategoryViewModel;
    protected String userId;
    protected boolean isFirstLoad = true;
    protected boolean isSorting = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category_list, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewCategories);
        progressBar = view.findViewById(R.id.progressBar);
        emptyLayout = view.findViewById(R.id.layoutEmptyState);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CategoryAdapter(requireContext());
        recyclerView.setAdapter(adapter);

        setupDragToSort();

        categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
        subCategoryViewModel = new ViewModelProvider(requireActivity()).get(SubCategoryViewModel.class);

        BmobUser currentUser = BmobUser.getCurrentUser();
        userId = (currentUser != null) ? currentUser.getObjectId() : null;

        progressBar.setVisibility(View.VISIBLE);
        observeCategories();

        adapter.setOnCategoryClickListener(new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onCategoryClick(Category category) {
                CategoryDetailBottomSheetFragment dialog = CategoryDetailBottomSheetFragment.newInstance(category);
                dialog.show(getParentFragmentManager(), "category_detail");
            }

            @Override
            public void onSubCategoryClick(SubCategory subCategory) {
                showMoreSubCategoryDialog(subCategory);
            }

            @Override
            public void onAddSubCategoryClick(Category category) {
                showAddSubCategoryDialog(category);
            }

            @Override
            public void onMoreOptionsClick(Category category, View anchor) {
                showMoreCategoryDialog(category);
            }
        });

        return view;
    }

    protected abstract void observeCategories();

    protected abstract String getCategoryType();

    protected void updateUI(List<CategoryWithSubCategories> categoriesWithSubs) {
        if (categoriesWithSubs == null || categoriesWithSubs.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            List<Category> newList = new ArrayList<>();
            for (CategoryWithSubCategories cw : categoriesWithSubs) {
                Category c = cw.category;
                if (c == null) continue;
                
                // 过滤已归档的一级分类
                if (c.getArchiveStatus() != null && c.getArchiveStatus() == 1) continue;

                List<SubCategory> subs = cw.subCategories;
                List<SubCategory> activeSubs = new ArrayList<>();
                if (subs != null) {
                    for (SubCategory sub : subs) {
                        // 过滤已归档或待删除的二级分类
                        if ((sub.getArchiveStatus() == null || sub.getArchiveStatus() == 0)
                                && sub.getSyncState() != 3) {
                            activeSubs.add(sub);
                        }
                    }
                    Collections.sort(activeSubs, (o1, o2) -> Integer.compare(o1.getSortIndex(), o2.getSortIndex()));
                }
                
                // 必须创建新对象或谨慎设置，因为 c 是来自 Room 的缓存引用
                // 这里我们假设 adapter 内部的 DiffUtil 会处理。但为了安全，手动回填子分类
                c.setSubCategories(activeSubs);
                newList.add(c);
            }
            adapter.submitList(newList);
        }
        progressBar.setVisibility(View.GONE);
    }

    private void setupDragToSort() {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();

                if (fromPos < 0 || toPos < 0 || fromPos >= adapter.getItemCount() || toPos >= adapter.getItemCount()) {
                    return false;
                }

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
                saveOrder();
                // 延迟重置排序标记，等待数据库异步写入
                recyclerView.postDelayed(() -> isSorting = false, 800);
            }
        });
        touchHelper.attachToRecyclerView(recyclerView);
    }

    private void saveOrder() {
        List<Category> list = adapter.getCurrentList();
        List<Category> updateList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Category c = list.get(i);
            c.setSortIndex(i);
            updateList.add(c);
        }
        categoryViewModel.updateCategoryOrder(updateList);
    }

    private void showAddSubCategoryDialog(Category category) {
        android.content.Intent intent = new android.content.Intent(getActivity(), IconSelectionActivity.class);
        intent.putExtra(IconSelectionActivity.EXTRA_MODE, "add");
        intent.putExtra(IconSelectionActivity.EXTRA_TYPE, "subcategory");
        intent.putExtra(IconSelectionActivity.EXTRA_TITLE, "新建二级分类");
        intent.putExtra(IconSelectionActivity.EXTRA_PARENT_ID, category.getId());
        intent.putExtra(IconSelectionActivity.EXTRA_PARENT_CLOUD_ID, category.getCloudId());
        startActivity(intent);
    }

    private void showMoreSubCategoryDialog(SubCategory subCategory) {
        CategoryMoreBottomSheetFragment dialog = new CategoryMoreBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("title", "二级分类");
        args.putString("categoryName", subCategory.getName());
        args.putString("categoryIconUrl", subCategory.getIconUri());
        args.putString("categoryIconBg", subCategory.getIconBackgroundColor());
        args.putLong("subcategoryId", subCategory.getId());
        args.putLong("parentCategoryId", subCategory.getParentCategoryId());
        args.putString("type", "subcategory");
        args.putString("categoryCloudId", subCategory.getCloudId());
        args.putString("categoryType", getCategoryType());
        args.putBoolean("excludeBudget", subCategory.isExcludeBudget());
        dialog.setArguments(args);
        dialog.show(getParentFragmentManager(), "subcategory_more");
    }

    private void showMoreCategoryDialog(Category category) {
        CategoryMoreBottomSheetFragment dialog = new CategoryMoreBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("title", "一级分类");
        args.putString("categoryName", category.getName());
        args.putString("categoryIconUrl", category.getIconUri());
        args.putString("categoryIconBg", category.getIconBackgroundColor());
        args.putString("type", "category");
        args.putLong("categoryId", category.getId());
        args.putString("categoryCloudId", category.getCloudId());
        args.putString("categoryType", category.getType());
        args.putBoolean("excludeBudget", category.isExcludeBudget());
        args.putBoolean("hasChildren", category.getSubCategories() != null && !category.getSubCategories().isEmpty());
        dialog.setArguments(args);
        dialog.show(getParentFragmentManager(), "category_more");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFirstLoad) {
            categoryViewModel.syncFromCloud();
            subCategoryViewModel.syncFromCloud();
            isFirstLoad = false;
        }
    }
}
