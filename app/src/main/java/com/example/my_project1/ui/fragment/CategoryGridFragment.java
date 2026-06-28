package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.my_project1.databinding.FragmentCategoryGridBinding;
import com.example.my_project1.ui.adapter.CategoryGridAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import io.reactivex.annotations.NonNull;

/**
 * 分类网格 Fragment（ViewBinding版 + 编辑模式支持）
 * -------------------------------------------------------
 * 用于在 ViewPager2 中显示支出或收入分类
 * ✅ ⭐ 新增：支持设置选中分类（编辑模式使用）
 */
public class CategoryGridFragment extends Fragment {

    private static final String TAG = "CategoryGridFragment";
    private static final String ARG_TYPE = "type";
    private static final String ARG_USER_ID = "user_id";

    private String categoryType; // "expense" 或 "income"
    private String userId;
    private FragmentCategoryGridBinding binding;
    private CategoryGridAdapter adapter;
    private CategoryViewModel viewModel;
    private OnCategorySelectedListener listener;

    // ⭐ 新增：预选中的分类ID（用于编辑模式）
    private String preSelectedCategoryId = null;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String displayName, String categoryCloudId, String categoryImageUrl);
    }

    public static CategoryGridFragment newInstance(String type, String userId) {
        CategoryGridFragment fragment = new CategoryGridFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    /**
     * ⭐ 新增：设置选中的分类（供AddBillActivity编辑模式调用）
     */
    public void setSelectedCategory(String categoryId) {
        Log.d(TAG, "⭐ CategoryGridFragment.setSelectedCategory: " + categoryId +
                " (type=" + categoryType + ")");
        this.preSelectedCategoryId = categoryId;

        // 如果adapter已经初始化，立即设置
        if (adapter != null) {
            adapter.setSelectedCategory(categoryId);
            Log.d(TAG, "✅ Adapter已存在，直接设置选中");
        } else {
            Log.d(TAG, "⚠️ Adapter未初始化，保存待设置的值");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoryType = getArguments().getString(ARG_TYPE);
            userId = getArguments().getString(ARG_USER_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryGridBinding.inflate(inflater, container, false);

        // 设置网格布局，每行5个
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 5);
        binding.recyclerCategory.setLayoutManager(layoutManager);

        // 初始化适配器
        adapter = new CategoryGridAdapter(new ArrayList<>(), getContext());

        // 设置分类选择监听器
        adapter.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl) -> {
            // 通知Activity分类已选择
            if (listener != null) {
                listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl);
            }
        });

        // ⭐ 关键：如果有预选中的分类ID，设置给adapter
        if (preSelectedCategoryId != null) {
            adapter.setSelectedCategory(preSelectedCategoryId);
            Log.d(TAG, "⭐ onCreateView中设置预选中分类: " + preSelectedCategoryId);
        }

        binding.recyclerCategory.setAdapter(adapter);

        // 初始化 ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        // 观察数据变化
        observeCategories();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void observeCategories() {
        if ("expense".equals(categoryType)) {
            viewModel.getExpenseCategories(userId).observe(getViewLifecycleOwner(), categories -> {
                if (categories != null) {
                    adapter.updateData(categories);
                    Log.d(TAG, "✅ 支出分类数据已更新: " + categories.size() + " 条");
                }
            });
        } else {
            viewModel.getIncomeCategories(userId).observe(getViewLifecycleOwner(), categories -> {
                if (categories != null) {
                    adapter.updateData(categories);
                    Log.d(TAG, "✅ 收入分类数据已更新: " + categories.size() + " 条");
                }
            });
        }
    }
}