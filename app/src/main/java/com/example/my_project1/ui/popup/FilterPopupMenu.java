package com.example.my_project1.ui.popup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.databinding.PopupCalendarMenuBinding;
import com.example.my_project1.utils.DisplayUtils;


public class FilterPopupMenu {

    private Context context;
    private PopupWindow popupWindow;
    private PopupCalendarMenuBinding binding;
    private OnFilterClickListener listener;

    private int WIDTH;

    // 记录当前选中的过滤类型
    private FilterType currentFilter = FilterType.ALL;

    public enum FilterType {
        ALL,
        EXPENSE,
        INCOME
    }

    public interface OnFilterClickListener {
        void onFilterSelected(FilterType type);
    }

    public FilterPopupMenu(Context context, OnFilterClickListener listener) {
        this.context = context;
        this.listener = listener;
        initPopup();
    }

    private void initPopup() {
        binding = PopupCalendarMenuBinding.inflate(LayoutInflater.from(context));
        WIDTH = DisplayUtils.dp2px(context, 120);

        popupWindow = new PopupWindow(
                binding.getRoot(),
                WIDTH,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(
                ContextCompat.getDrawable(context, android.R.color.transparent)
        );

        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setAnimationStyle(R.style.PopupMenuAnimation);

        // 默认隐藏所有图标
        hideAllIcons();

        initClick();
    }

    private void initClick() {
        // 获取菜单项的父容器
        ConstraintLayout containerAll = (ConstraintLayout) binding.tvAll.getParent();
        ConstraintLayout containerExpense = (ConstraintLayout) binding.tvExpense.getParent();
        ConstraintLayout containerIncome = (ConstraintLayout) binding.tvIncome.getParent();

        // 全部选项 - 为整个容器设置点击事件
        containerAll.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFilterSelected(FilterType.ALL);
            }
            dismiss();
        });

        // 支出选项 - 为整个容器设置点击事件
        containerExpense.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFilterSelected(FilterType.EXPENSE);
            }
            dismiss();
        });

        // 收入选项 - 为整个容器设置点击事件
        containerIncome.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFilterSelected(FilterType.INCOME);
            }
            dismiss();
        });
    }

    /**
     * 隐藏所有图标
     */
    public void hideAllIcons() {
        binding.ivAll.setVisibility(View.GONE);
        binding.ivExpense.setVisibility(View.GONE);
        binding.ivIncome.setVisibility(View.GONE);

        // 文字居中
        centerText(binding.tvAll);
        centerText(binding.tvExpense);
        centerText(binding.tvIncome);
    }

    /**
     * 显示所有图标
     */
    public void showAllIcons() {
        binding.ivAll.setVisibility(View.VISIBLE);
        binding.ivExpense.setVisibility(View.VISIBLE);
        binding.ivIncome.setVisibility(View.VISIBLE);

        // 文字靠右
        alignTextEnd(binding.tvAll);
        alignTextEnd(binding.tvExpense);
        alignTextEnd(binding.tvIncome);
    }

    /**
     * 设置图标显示/隐藏
     */
    public void setIconVisibility(FilterType type, boolean visible) {
        TextView textView = null;

        switch (type) {
            case ALL:
                binding.ivAll.setVisibility(visible ? View.VISIBLE : View.GONE);
                textView = binding.tvAll;
                break;
            case EXPENSE:
                binding.ivExpense.setVisibility(visible ? View.VISIBLE : View.GONE);
                textView = binding.tvExpense;
                break;
            case INCOME:
                binding.ivIncome.setVisibility(visible ? View.VISIBLE : View.GONE);
                textView = binding.tvIncome;
                break;
        }

        // 根据图标显示状态调整文字位置
        if (textView != null) {
            if (visible) {
                alignTextEnd(textView);
            } else {
                centerText(textView);
            }
        }
    }

    /**
     * 设置菜单项文本
     */
    public void setMenuText(FilterType type, String text) {
        switch (type) {
            case ALL:
                binding.tvAll.setText(text);
                break;
            case EXPENSE:
                binding.tvExpense.setText(text);
                break;
            case INCOME:
                binding.tvIncome.setText(text);
                break;
        }
    }

    /**
     * 设置菜单项图标
     */
    public void setMenuIcon(FilterType type, int iconResId) {
        switch (type) {
            case ALL:
                binding.ivAll.setImageResource(iconResId);
                break;
            case EXPENSE:
                binding.ivExpense.setImageResource(iconResId);
                break;
            case INCOME:
                binding.ivIncome.setImageResource(iconResId);
                break;
        }
    }

    /**
     * 隐藏第三项
     */
    public void hideThirdItem() {
        View container = (View) binding.tvIncome.getParent();
        container.setVisibility(View.GONE);
    }

    /**
     * 显示第三项
     */
    public void showThirdItem() {
        View container = (View) binding.tvIncome.getParent();
        container.setVisibility(View.VISIBLE);
    }

    /**
     * 文字居中（隐藏图标时）
     */
    private void centerText(TextView textView) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) textView.getLayoutParams();

        // 清除原有约束
        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = ConstraintLayout.LayoutParams.UNSET;

        // 设置新约束：水平居中
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

        textView.setLayoutParams(params);
    }

    /**
     * 文字靠右（显示图标时）
     */
    private void alignTextEnd(TextView textView) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) textView.getLayoutParams();

        // 清除原有约束
        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = ConstraintLayout.LayoutParams.UNSET;

        // 设置新约束：靠右对齐
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

        textView.setLayoutParams(params);
    }

    /**
     * 切换显示/隐藏菜单
     */
    public void toggle(View anchor) {
        if (isShowing()) {
            dismiss();
        } else {
            show(anchor);
        }
    }

    public void show(View anchor) {
        if (isShowing()) {
            return;
        }

        anchor.post(() -> showInternal(anchor));
    }

    private void showInternal(View anchor) {
        binding.getRoot().measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
        );

        int popupWidth = WIDTH;
        int popupHeight = binding.getRoot().getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();

        int x = location[0] + anchor.getWidth() - popupWidth;
        int y = location[1] + anchor.getHeight();

        if (x < 16) x = 16;
        if (x + popupWidth > dm.widthPixels)
            x = dm.widthPixels - popupWidth - 16;

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        playEnterAnim();
    }

    public void show(View anchor, int offsetX, int offsetY) {
        if (isShowing()) {
            return;
        }

        binding.getRoot().measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
        );

        int popupWidth = WIDTH;
        int popupHeight = binding.getRoot().getMeasuredHeight();

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();

        int x = location[0] + anchor.getWidth() - popupWidth + offsetX;
        int y = location[1] + anchor.getHeight() + offsetY;

        if (x < 16) x = 16;
        if (x + popupWidth > dm.widthPixels)
            x = dm.widthPixels - popupWidth - 16;

        if (y + popupHeight > dm.heightPixels)
            y = location[1] - popupHeight;

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        playEnterAnim();
    }

    private void playEnterAnim() {
        View v = binding.getRoot();
        v.setAlpha(0f);
        v.setScaleX(0.9f);
        v.setScaleY(0.9f);

        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .start();
    }

    public void dismiss() {
        if (!popupWindow.isShowing()) return;

        View v = binding.getRoot();
        v.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        popupWindow.dismiss();
                        v.animate().setListener(null); // 清理监听器
                    }
                })
                .start();
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    /**
     * 设置当前选中的过滤类型
     */
    public void setCurrentFilter(FilterType type) {
        this.currentFilter = type;
    }

    /**
     * 获取当前选中的过滤类型
     */
    public FilterType getCurrentFilter() {
        return currentFilter;
    }

    /**
     * 设置宽度
     */
    public void setWidth(int widthDp) {
        this.WIDTH = DisplayUtils.dp2px(context, widthDp);
        if (popupWindow != null) {
            popupWindow.setWidth(WIDTH);
        }
    }
}