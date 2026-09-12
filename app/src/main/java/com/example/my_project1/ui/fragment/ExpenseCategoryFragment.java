package com.example.my_project1.ui.fragment;

/**
 * 支出分类列表片段
 */
public class ExpenseCategoryFragment extends BaseCategoryFragment {

    @Override
    protected void observeCategories() {
        categoryViewModel.getExpenseCategories(userId).observe(getViewLifecycleOwner(), categoriesWithSubs -> {
            if (isSorting) return;
            updateUI(categoriesWithSubs);
        });
    }

    @Override
    protected String getCategoryType() {
        return "expense";
    }
}
