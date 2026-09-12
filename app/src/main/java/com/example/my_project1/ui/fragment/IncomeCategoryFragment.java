package com.example.my_project1.ui.fragment;

/**
 * 收入分类列表片段
 */
public class IncomeCategoryFragment extends BaseCategoryFragment {

    @Override
    protected void observeCategories() {
        categoryViewModel.getIncomeCategories(userId).observe(getViewLifecycleOwner(), categoriesWithSubs -> {
            if (isSorting) return;
            updateUI(categoriesWithSubs);
        });
    }

    @Override
    protected String getCategoryType() {
        return "income";
    }
}
