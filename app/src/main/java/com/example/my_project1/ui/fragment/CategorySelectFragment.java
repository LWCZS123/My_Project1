package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.databinding.FragmentCategorySelectBinding;
import com.example.my_project1.ui.adapter.budget.CategorySelectorAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;
import com.example.my_project1.ui.viewmodel.budget.BudgetViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CategorySelectFragment extends BottomSheetDialogFragment {

    private FragmentCategorySelectBinding binding;
    private CategoryViewModel categoryVm;
    private BudgetViewModel budgetVm;
    
    private final CategorySelectorAdapter adapter = new CategorySelectorAdapter();
    private boolean showMainCat = true;
    private List<CategoryWithSubCategories> allCategories = new ArrayList<>();
    private final Map<String, Budget> existingBudgets = new HashMap<>();
    
    private OnCategorySelectedListener listener;
    private CategorySelectorAdapter.Item tempSelectedItem;

    public interface OnCategorySelectedListener {
        void onCategorySelected(CategorySelectorAdapter.Item item);
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet =
                    dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1)
                );

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCategorySelectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        categoryVm = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);
        budgetVm = new ViewModelProvider(requireActivity()).get(BudgetViewModel.class);

        initRecyclerView();
        initTabs();
        initButtons();
        observeData();
    }

    private void initRecyclerView() {
        binding.rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        binding.rvCategories.setAdapter(adapter);
        adapter.setOnItemClickListener(item -> {
            tempSelectedItem = item;
            adapter.setSelected(item.cloudId);
        });
    }

    private void initTabs() {
        binding.tabLevelMain.setOnClickListener(v -> {
            if (showMainCat) return;
            showMainCat = true;
            updateTabStyle();
            refreshList();
        });
        binding.tabLevelSub.setOnClickListener(v -> {
            if (!showMainCat) return;
            showMainCat = false;
            updateTabStyle();
            refreshList();
        });
    }

    private void initButtons() {
        binding.btnConfirm.setOnClickListener(v -> {
            if (tempSelectedItem == null) {
                Toast.makeText(requireContext(), "请选择一个分类", Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onCategorySelected(tempSelectedItem);
            }
            dismiss();
        });
    }

    private void updateTabStyle() {
        if (showMainCat) {
            binding.tabLevelMain.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            binding.tabLevelMain.setTextColor(0xFFFFFFFF);
            binding.tabLevelSub.setBackground(null);
            binding.tabLevelSub.setTextColor(0xFF888888);
        } else {
            binding.tabLevelSub.setBackgroundResource(R.drawable.bg_tab_selected_blue);
            binding.tabLevelSub.setTextColor(0xFFFFFFFF);
            binding.tabLevelMain.setBackground(null);
            binding.tabLevelMain.setTextColor(0xFF888888);
        }
    }

    private void observeData() {
        // 获取当前用户ID，确保数据隔离
        cn.bmob.v3.BmobUser currentUser = cn.bmob.v3.BmobUser.getCurrentUser(cn.bmob.v3.BmobUser.class);
        String userId = (currentUser != null) ? currentUser.getObjectId() : null;

        // 观察分类
        categoryVm.getExpenseCategories(userId).observe(getViewLifecycleOwner(), cats -> {
            if (cats != null) {
                allCategories = cats;
                refreshList();
            }
        });
        
        // 观察预算，用于标记已设置的分类
        budgetVm.getCategoryBudgets().observe(getViewLifecycleOwner(), budgets -> {
            existingBudgets.clear();
            if (budgets != null) {
                for (Budget b : budgets) {
                    if (b.getTargetId() != null) {
                        existingBudgets.put(b.getTargetId(), b);
                    }
                }
            }
            refreshList();
        });
    }

    private void refreshList() {
        List<CategorySelectorAdapter.Item> items = new ArrayList<>();
        if (showMainCat) {
            for (CategoryWithSubCategories cat : allCategories) {
                if (cat.category != null) {
                    String cloudId = cat.category.getCloudId();
                    Budget b = existingBudgets.get(cloudId);
                    String budgetTag = (b != null) ? String.format(Locale.getDefault(), "¥%.0f", b.getAmount()) : null;
                    boolean isSet = (b != null);
                    
                    // 如果该一级分类下有二级分类，则不允许直接为该一级分类设置预算
                    if (cat.subCategories != null && !cat.subCategories.isEmpty()) {
                        continue;
                    }
                    
                    items.add(new CategorySelectorAdapter.Item(
                            cloudId,
                            cat.category.getName(),
                            cat.category.getIconUri(),
                            budgetTag,
                            isSet
                    ));
                }
            }
        } else {
            for (CategoryWithSubCategories cat : allCategories) {
                if (cat.subCategories != null) {
                    for (SubCategory sub : cat.subCategories) {
                        String cloudId = sub.getCloudId();
                        Budget b = existingBudgets.get(cloudId);
                        String budgetTag = (b != null) ? String.format(Locale.getDefault(), "¥%.0f", b.getAmount()) : null;
                        boolean isSet = (b != null);

                        items.add(new CategorySelectorAdapter.Item(
                                cloudId,
                                sub.getName(),
                                sub.getIconUri(),
                                budgetTag,
                                isSet
                        ));
                    }
                }
            }
        }
        adapter.submitList(items);
        if (tempSelectedItem != null) {
            adapter.setSelected(tempSelectedItem.cloudId);
        }
    }

    public void setSelected(String cloudId) {
        adapter.setSelected(cloudId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
