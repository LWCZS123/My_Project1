package com.example.my_project1.ui.adapter;

import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.my_project1.ui.fragment.CategoryGridFragment;

import io.reactivex.annotations.NonNull;

/**
 * CategoryIconPagerAdapter - 分类图标ViewPager适配器（支持编辑模式）
 * -------------------------------------------------------
 * ⭐ 修复闪烁：
 * pendingCategoryId/pendingType 不再通过 postDelayed(100) 延迟下发，
 * 而是在 createFragment() 时，通过 CategoryGridFragment.newInstance()
 * 直接把预选中ID作为参数传给Fragment（Fragment内部会在adapter构造时
 * 就带上这个ID，见 CategoryGridFragment 的修改）。
 * 这样Fragment从创建的第一刻起就知道自己要选中谁，不需要"创建后再补一刀"。
 */
public class CategoryIconPagerAdapter extends FragmentStateAdapter {

    private static final String TAG = "CategoryIconPagerAdapter";

    private String userId;
    private CategoryGridFragment expenseFragment;
    private CategoryGridFragment incomeFragment;
    private com.example.my_project1.ui.fragment.TransferFragment transferFragment;
    private OnCategorySelectedListener listener;
    private com.example.my_project1.ui.fragment.TransferFragment.OnTransferAccountSelectedListener transferListener;

    // ⭐ 编辑模式下的预选中信息。必须在 setSelectedCategory() 被调用时就已经
    // 确定（即在 setupViewPager() 之后、Fragment 尚未创建之前），这样
    // createFragment() 才能把它一起传给 CategoryGridFragment.newInstance()。
    private String pendingCategoryId = null;
    private int pendingType = -1;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String displayName, String categoryCloudId, String categoryImageUrl, String backgroundColor);
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
        fragment.setOnCategorySelectedListener((displayName, categoryCloudId, categoryImageUrl, backgroundColor) -> {
            if (this.listener != null) {
                this.listener.onCategorySelected(displayName, categoryCloudId, categoryImageUrl, backgroundColor);
            }
        });
    }

    /**
     * ⭐ 设置指定类型页面的选中分类。
     * - 若对应Fragment已存在（比如 offscreenPageLimit 已经把它创建出来），
     *   直接同步调用 fragment.setSelectedCategory()，无延迟。
     * - 若Fragment还没创建，记录 pendingCategoryId/pendingType，
     *   等 createFragment() 时一起传入构造参数，而不是等创建完再补设置。
     */
    public void setSelectedCategory(int type, String categoryId) {
        Log.d(TAG, "setSelectedCategory: type=" + type + ", categoryId=" + categoryId);

        if (type == 0 && expenseFragment != null) {
            expenseFragment.setSelectedCategory(categoryId);
        } else if (type == 1 && incomeFragment != null) {
            incomeFragment.setSelectedCategory(categoryId);
        } else {
            // Fragment尚未创建（常见于ViewPager2 offscreenPageLimit还没
            // 触发到该position），记下来，createFragment()会直接用上。
            pendingCategoryId = categoryId;
            pendingType = type;
        }
    }

    public com.example.my_project1.ui.fragment.TransferFragment getTransferFragment() {
        return transferFragment;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            String presetId = (pendingType == 0) ? pendingCategoryId : null;
            expenseFragment = CategoryGridFragment.newInstance("expense", userId, presetId);
            if (listener != null) setupFragmentListener(expenseFragment);
            if (presetId != null) { pendingCategoryId = null; pendingType = -1; }
            return expenseFragment;
        } else if (position == 1) {
            String presetId = (pendingType == 1) ? pendingCategoryId : null;
            incomeFragment = CategoryGridFragment.newInstance("income", userId, presetId);
            if (listener != null) setupFragmentListener(incomeFragment);
            if (presetId != null) { pendingCategoryId = null; pendingType = -1; }
            return incomeFragment;
        } else {
            transferFragment = com.example.my_project1.ui.fragment.TransferFragment.newInstance();
            if (transferListener != null) {
                transferFragment.setOnTransferAccountSelectedListener(transferListener);
            }
            return transferFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 3; // 支出、收入、转账
    }

    public void clearPendingSelection() {
        pendingCategoryId = null;
        pendingType = -1;
    }
}