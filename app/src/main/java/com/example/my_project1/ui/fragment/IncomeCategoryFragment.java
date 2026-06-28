package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.ui.adapter.CategoryAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.SubCategoryViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

public class IncomeCategoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearProgressIndicator progressBar;
    private View emptyLayout;
    private CategoryAdapter adapter;
    private CategoryViewModel categoryViewModel;
    private SubCategoryViewModel subCategoryViewModel;
    private String userId;
    private boolean isFirstLoad = true;

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
                // 可根据需要实现
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
            List<Category> categories = new ArrayList<>();
            for (CategoryWithSubCategories cw : categoriesWithSubs) {
                Category c = cw.category;
                c.setSubCategories(cw.subCategories);
                categories.add(c);
            }
            adapter.submitList(categories);
        }
    }

    private void showAddSubCategoryDialog(Category category) {
        CategoryAddBottomSheetFragment dialog = new CategoryAddBottomSheetFragment();
        // 设置标题为二级分类标题
        Bundle args = new Bundle();
        args.putString("title", "新建二级分类");
        dialog.setArguments(args);

        // 设置子分类回调
        dialog.setOnCategoryAddedListener((name, excludeBudget, iconUrl) -> {
            long parentId = category.getId();
            String parentCloudId = category.getCloudId();
            SubCategory subCategory = new SubCategory(parentId, userId, name, iconUrl,parentCloudId);
            subCategoryViewModel.insert(subCategory);
            Toast.makeText(getActivity(), "已添加子分类，后台自动同步中...", Toast.LENGTH_SHORT).show();
        });

        dialog.show(getParentFragmentManager(), "sub_category_add");
    }

    private void showMoreSubCategoryDialog(SubCategory subCategory) {
        CategoryMoreBottomSheetFragment dialog = new CategoryMoreBottomSheetFragment();

        Bundle args = new Bundle();
        args.putString("title", "二级分类");
        args.putString("categoryName", subCategory.getName());
        args.putString("categoryIconUrl", subCategory.getIconUri());
        args.putLong("subcategoryId", subCategory.getId());
        args.putString("type", "subcategory");

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
        args.putString("type", "category");
        args.putLong("categoryId", category.getId());

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
