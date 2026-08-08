package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.util.Log;
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

public class IncomeCategoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearProgressIndicator progressBar;
    private View emptyLayout;
    private CategoryAdapter adapter;
    private CategoryViewModel categoryViewModel;
    private SubCategoryViewModel subCategoryViewModel;
    private String userId;
    private boolean isFirstLoad = true;
    private List<Category> currentList = new ArrayList<>();
    private boolean isSorting = false;

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

        categoryViewModel = new ViewModelProvider(requireActivity())
                .get(CategoryViewModel.class);
        subCategoryViewModel = new ViewModelProvider(requireActivity())
                .get(SubCategoryViewModel.class);

        // 获取当前用户 ID
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getObjectId();
        } else {
            userId = null;
        }

        progressBar.setVisibility(View.VISIBLE);

        // 监听收入分类数据
        categoryViewModel.getIncomeCategories(userId).observe(getViewLifecycleOwner(), categoryWithSubsList -> {
            if (isSorting) {
                Log.d("IncomeFragment", "正在排序中，忽略回调更新");
                return;
            }
            Log.d("IncomeFragment", "收到分类数量：" + (categoryWithSubsList == null ? 0 : categoryWithSubsList.size()));
            if (categoryWithSubsList != null) {
                for (CategoryWithSubCategories cw : categoryWithSubsList) {
                    Category c = cw.category;
                    Log.d("IncomeFragment", "分类: " + c.getName() + " | userId: " + c.getOwnerId() +
                            " | 子分类数量: " + (cw.subCategories != null ? cw.subCategories.size() : 0));
                }
            }
            progressBar.setVisibility(View.GONE);
            updateUI(categoryWithSubsList);
            Log.d("IncomeCategoryFragment", "当前用户: " + (userId != null ? userId : "未登录"));
        });

        // 点击事件监听
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

    private void updateUI(List<CategoryWithSubCategories> categoriesWithSubs) {
        if (categoriesWithSubs == null || categoriesWithSubs.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            // 转换成 Category 列表，带上子分类
            List<Category> newList = new ArrayList<>();
            for (CategoryWithSubCategories cw : categoriesWithSubs) {
                Category c = cw.category;
                // 过滤已归档一级分类
                if (c.getArchiveStatus() != null && c.getArchiveStatus() == 1) continue;

                List<SubCategory> subs = cw.subCategories;
                List<SubCategory> activeSubs = new ArrayList<>();
                if (subs != null) {
                    for (SubCategory sub : subs) {
                        // 过滤已归档或待删除二级分类
                        if ((sub.getArchiveStatus() == null || sub.getArchiveStatus() == 0)
                                && sub.getSyncState() != 3) {
                            activeSubs.add(sub);
                        }
                    }
                    // 手动按 sortIndex 排序子分类
                    Collections.sort(activeSubs, (o1, o2) -> Integer.compare(o1.getSortIndex(), o2.getSortIndex()));
                }
                c.setSubCategories(activeSubs);
                newList.add(c);
            }
            adapter.submitList(newList);
        }
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

                // 同步移动数据源
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
                
                // 松手即定，持久化
                saveOrder();
                
                // 延长屏蔽时间
                recyclerView.postDelayed(() -> isSorting = false, 1000);
            }
        });
        touchHelper.attachToRecyclerView(recyclerView);
    }

    private void saveOrder() {
        List<Category> list = adapter.getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortIndex(i);
        }
        categoryViewModel.updateCategoryOrder(new ArrayList<>(list));
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
        args.putString("categoryType", "income");
        args.putBoolean("excludeBudget", subCategory.isExcludeBudget());

        Log.d("IncomeCategoryFragment", subCategory.getIconUri());
        Log.d("IncomeCategoryFragment", subCategory.getName());

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
