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
    private com.example.my_project1.ui.fragment.TransferFragment transferFragment; // 🔑 替换为专门的转账 Fragment
    private OnCategorySelectedListener listener;
    private com.example.my_project1.ui.fragment.TransferFragment.OnTransferAccountSelectedListener transferListener;

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
        if (expenseFragment != null) setupFragmentListener(expenseFragment);
        if (incomeFragment != null) setupFragmentListener(incomeFragment);
    }

    public void setOnTransferAccountSelectedListener(com.example.my_project1.ui.fragment.TransferFragment.OnTransferAccountSelectedListener listener) {
        this.transferListener = listener;
        if (transferFragment != null) {
            transferFragment.setOnTransferAccountSelectedListener(transferListener);
        }
    }

    private void setupFragmentListener(CategoryGridFragment fragment) {
        fragment.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl) -> {
            if (this.listener != null) {
                this.listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl);
            }
        });
    }

    /**
     * ⭐ 新增：设置指定类型页面的选中分类
     * @param type 0=支出, 1=收入, 2=转账
     * @param categoryId 要选中的分类cloudId
     */
    public void setSelectedCategory(int type, String categoryId) {
        Log.d(TAG, "⭐ setSelectedCategory: type=" + type + ", categoryId=" + categoryId);

        if (type == 0 && expenseFragment != null) {
            expenseFragment.setSelectedCategory(categoryId);
        } else if (type == 1 && incomeFragment != null) {
            incomeFragment.setSelectedCategory(categoryId);
        } else if (type >= 2) {
            // 转账暂不支持通过此方式选中分类图标，因为已被卡片覆盖
            pendingCategoryId = categoryId;
            pendingType = type;
        } else {
            pendingCategoryId = categoryId;
            pendingType = type;
        }
    }

    /**
     * ⭐ 获取转账 Fragment 实例
     */
    public com.example.my_project1.ui.fragment.TransferFragment getTransferFragment() {
        return transferFragment;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            expenseFragment = CategoryGridFragment.newInstance("expense", userId);
            if (listener != null) setupFragmentListener(expenseFragment);
            applyPending(expenseFragment, 0);
            return expenseFragment;
        } else if (position == 1) {
            incomeFragment = CategoryGridFragment.newInstance("income", userId);
            if (listener != null) setupFragmentListener(incomeFragment);
            applyPending(incomeFragment, 1);
            return incomeFragment;
        } else {
            transferFragment = com.example.my_project1.ui.fragment.TransferFragment.newInstance();
            if (transferListener != null) {
                transferFragment.setOnTransferAccountSelectedListener(transferListener);
            }
            return transferFragment;
        }
    }

    private void applyPending(CategoryGridFragment fragment, int type) {
        if (pendingType == type && pendingCategoryId != null) {
            final String cid = pendingCategoryId;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                fragment.setSelectedCategory(cid);
                pendingCategoryId = null;
                pendingType = -1;
            }, 100);
        }
    }

    @Override
    public int getItemCount() {
        return 3; // 支出、收入、转账
    }

    /**
     * ⭐ 新增：清除待设置的选中状态
     */
    public void clearPendingSelection() {
        pendingCategoryId = null;
        pendingType = -1;
    }
}