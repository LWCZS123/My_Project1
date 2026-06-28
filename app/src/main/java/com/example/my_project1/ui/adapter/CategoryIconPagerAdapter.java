package com.example.my_project1.ui.adapter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.my_project1.ui.fragment.CategoryGridFragment;

import io.reactivex.annotations.NonNull;

/**
 * CategoryIconPagerAdapter - 分类图标ViewPager适配器（支持编辑模式）
 * -------------------------------------------------------
 * ✅ 原有功能：缓存支出和收入Fragment
 * ✅ ⭐ 新增功能：支持设置选中分类（编辑模式使用）
 */
public class CategoryIconPagerAdapter extends FragmentStateAdapter {

    private static final String TAG = "CategoryIconPagerAdapter";

    private String userId;
    private CategoryGridFragment expenseFragment;
    private CategoryGridFragment incomeFragment;
    private OnCategorySelectedListener listener;

    // ⭐ 新增：保存预选中的分类信息（用于Fragment还未创建的情况）
    private String pendingCategoryId = null;
    private int pendingType = -1;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String displayName, String categoryCloudId, String categoryImageUrl);
    }

    public CategoryIconPagerAdapter(@NonNull FragmentActivity fragmentActivity, String userId) {
        super(fragmentActivity);
        this.userId = userId;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
        // 如果Fragment已经创建，立即设置监听器
        if (expenseFragment != null) {
            expenseFragment.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl) -> {
                if (this.listener != null) {
                    this.listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl);
                }
            });
        }
        if (incomeFragment != null) {
            incomeFragment.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl) -> {
                if (this.listener != null) {
                    this.listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl);
                }
            });
        }
    }

    /**
     * ⭐ 新增：设置指定类型页面的选中分类
     * @param type 0=支出, 1=收入
     * @param categoryId 要选中的分类cloudId
     */
    public void setSelectedCategory(int type, String categoryId) {
        Log.d(TAG, "⭐ setSelectedCategory: type=" + type + ", categoryId=" + categoryId);

        CategoryGridFragment targetFragment = (type == 0) ? expenseFragment : incomeFragment;

        if (targetFragment != null) {
            // Fragment已经创建，直接设置
            Log.d(TAG, "✅ Fragment已存在，直接设置选中");
            targetFragment.setSelectedCategory(categoryId);
        } else {
            // Fragment还未创建，保存待设置的值
            Log.d(TAG, "⚠️ Fragment未创建，保存待设置的值");
            pendingCategoryId = categoryId;
            pendingType = type;
        }
    }

    /**
     * ⭐ 新增：获取指定类型的Fragment（如果存在）
     */
    public CategoryGridFragment getFragment(int type) {
        return (type == 0) ? expenseFragment : incomeFragment;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        CategoryGridFragment fragment;

        if (position == 0) {
            // 支出页面
            expenseFragment = CategoryGridFragment.newInstance("expense", userId);
            if (listener != null) {
                expenseFragment.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl) -> {
                    listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl);
                });
            }
            fragment = expenseFragment;

            // ⭐ 检查是否有待设置的选中分类
            if (pendingType == 0 && pendingCategoryId != null) {
                Log.d(TAG, "✅ 支出Fragment创建时应用待设置的选中: " + pendingCategoryId);
                // 延迟设置，确保Fragment完全初始化
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    expenseFragment.setSelectedCategory(pendingCategoryId);
                    // 清除待设置的值
                    pendingCategoryId = null;
                    pendingType = -1;
                }, 100);
            }

        } else {
            // 收入页面
            incomeFragment = CategoryGridFragment.newInstance("income", userId);
            if (listener != null) {
                incomeFragment.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl) -> {
                    listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl);
                });
            }
            fragment = incomeFragment;

            // ⭐ 检查是否有待设置的选中分类
            if (pendingType == 1 && pendingCategoryId != null) {
                Log.d(TAG, "✅ 收入Fragment创建时应用待设置的选中: " + pendingCategoryId);
                // 延迟设置，确保Fragment完全初始化
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    incomeFragment.setSelectedCategory(pendingCategoryId);
                    // 清除待设置的值
                    pendingCategoryId = null;
                    pendingType = -1;
                }, 100);
            }
        }

        return fragment;
    }

    @Override
    public int getItemCount() {
        return 2; // 支出和收入两页
    }

    /**
     * ⭐ 新增：清除待设置的选中状态
     */
    public void clearPendingSelection() {
        pendingCategoryId = null;
        pendingType = -1;
    }
}