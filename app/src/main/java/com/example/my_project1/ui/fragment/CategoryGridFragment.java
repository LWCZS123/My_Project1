package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import java.util.List;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.databinding.FragmentCategoryGridBinding;
import com.example.my_project1.ui.adapter.CategoryGridAdapter;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;

import org.jetbrains.annotations.Nullable;

import io.reactivex.annotations.NonNull;

/**
 * 分类网格 Fragment（ViewBinding版 + 编辑模式支持）
 * -------------------------------------------------------
 * ⭐ 修复闪烁：
 * newInstance() 新增 preSelectedCategoryId 参数，通过 Bundle 带入。
 * onCreateView() 创建 Adapter 时直接用 preSelectedCategoryId 构造
 * （见 CategoryGridAdapter 的双参数构造函数），这样 Adapter 从第一次
 * onBindViewHolder 开始就知道该选中谁，不再需要"创建后setSelectedCategory
 * 再notify一次"的两阶段流程。
 */
public class CategoryGridFragment extends Fragment {

    private static final String TAG = "CategoryGridFragment";
    private static final String ARG_TYPE = "type";
    private static final String ARG_USER_ID = "user_id";
    private static final String ARG_PRESELECTED_CATEGORY_ID = "preselected_category_id"; // ⭐ 新增

    private String categoryType;
    private String userId;
    private FragmentCategoryGridBinding binding;
    private CategoryGridAdapter adapter;
    private CategoryViewModel viewModel;
    private OnCategorySelectedListener listener;

    // ⭐ 预选中的分类ID：优先来自newInstance的构造参数（编辑模式打开页面时的首选路径），
    // 也允许运行时通过setSelectedCategory()动态覆盖（比如滑动切换类型时保持选择）。
    private String preSelectedCategoryId = null;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String displayName, String categoryCloudId, String categoryImageUrl, String backgroundColor, boolean excludeBudget);
    }

    /** 保留原有2参数签名，非编辑模式或无需预选中时使用 */
    public static CategoryGridFragment newInstance(String type, String userId) {
        return newInstance(type, userId, null);
    }

    /**
     * ⭐ 新增：创建Fragment时就带上预选中分类ID。
     * 编辑模式下，CategoryIconPagerAdapter.createFragment() 应调用此重载，
     * 从源头保证Fragment/Adapter一创建出来就知道正确的选中态。
     */
    public static CategoryGridFragment newInstance(String type, String userId, @Nullable String preSelectedCategoryId) {
        CategoryGridFragment fragment = new CategoryGridFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_USER_ID, userId);
        args.putString(ARG_PRESELECTED_CATEGORY_ID, preSelectedCategoryId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 设置选中的分类（供AddBillActivity在Fragment已存在时调用，例如
     * 滑动切换tab后想强制回到原分类）。若adapter已创建，同步下发；
     * 若尚未创建，记录下来，onCreateView时会用它构造Adapter。
     */
    public void setSelectedCategory(String categoryId) {
        this.preSelectedCategoryId = categoryId;
        if (adapter != null) {
            adapter.setSelectedCategory(categoryId);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoryType = getArguments().getString(ARG_TYPE);
            userId = getArguments().getString(ARG_USER_ID);
            // ⭐ 从Bundle恢复预选中ID，构造Adapter时直接使用
            preSelectedCategoryId = getArguments().getString(ARG_PRESELECTED_CATEGORY_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryGridBinding.inflate(inflater, container, false);

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 5);
        binding.recyclerCategory.setLayoutManager(layoutManager);
        // ⭐ 彻底关闭 ItemAnimator，防止数据加载时的闪烁/入场动画
        binding.recyclerCategory.setItemAnimator(null);

        // ⭐ 核心修复：用带preSelectedCategoryId的构造函数创建Adapter，
        // 而不是先new CategoryGridAdapter(context)再事后setSelectedCategory。
        adapter = new CategoryGridAdapter(getContext(), preSelectedCategoryId);

        adapter.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl, backgroundColor, excludeBudget) -> {
            if (listener != null) {
                listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl, backgroundColor, excludeBudget);
            }
        });

        binding.recyclerCategory.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        // ⚡ 核心：立即从 Repository 获取内存/磁盘快照，消除首屏白屏和闪烁
        List<CategoryWithSubCategories> snapshot =
                "expense".equals(categoryType) ? viewModel.getExpenseSnapshot() : viewModel.getIncomeSnapshot();
        
        if (snapshot != null && !snapshot.isEmpty()) {
            sortSubCategories(snapshot);
            adapter.updateData(snapshot);
            Log.d(TAG, "⚡ 已应用快照数据，消除闪烁: " + snapshot.size() + " 条");
        }

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
                    sortSubCategories(categories);
                    // ⭐ adapter.updateData 内部通过 submitList 的 commitCallback
                    // 同步应用 preSelectedCategoryId，不再有额外的postDelayed。
                    adapter.updateData(categories);
                    Log.d(TAG, "支出分类数据已更新: " + categories.size() + " 条");
                }
            });
        } else if ("income".equals(categoryType)) {
            viewModel.getIncomeCategories(userId).observe(getViewLifecycleOwner(), categories -> {
                if (categories != null) {
                    sortSubCategories(categories);
                    adapter.updateData(categories);
                    Log.d(TAG, "收入分类数据已更新: " + categories.size() + " 条");
                }
            });
        } else if ("transfer".equals(categoryType)) {
            viewModel.getTransferCategories(userId).observe(getViewLifecycleOwner(), categories -> {
                if (categories != null) {
                    sortSubCategories(categories);
                    adapter.updateData(categories);
                }
            });
        }
    }

    private void sortSubCategories(java.util.List<com.example.my_project1.data.model.CategoryWithSubCategories> list) {
        for (com.example.my_project1.data.model.CategoryWithSubCategories cw : list) {
            if (cw.subCategories != null) {
                java.util.Collections.sort(cw.subCategories, (o1, o2) -> Integer.compare(o1.getSortIndex(), o2.getSortIndex()));
            }
        }
    }
}