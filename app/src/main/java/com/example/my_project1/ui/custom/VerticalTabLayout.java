package com.example.my_project1.ui.custom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.my_project1.R;

import java.util.ArrayList;
import java.util.List;

public class VerticalTabLayout extends LinearLayout {

    private OnTabSelectedListener listener;
    private List<TabView> tabViews = new ArrayList<>();
    private int selectedPosition = 0;

    public interface OnTabSelectedListener {
        void onTabSelected(int position, String text);
    }

    public VerticalTabLayout(Context context) {
        super(context);
        init();
    }

    public VerticalTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
    }

    /** 直接设置所有标签 */
    public void setTabs(List<String> tabNames, OnTabSelectedListener listener) {
        this.listener = listener;
        removeAllViews();
        tabViews.clear();

        for (String name : tabNames) {
            addTab(name);
        }

        // 默认选中第一个
        if (!tabViews.isEmpty()) {
            tabViews.get(0).setSelected(true);
            selectedPosition = 0;
        }
    }

    public void addTab(String text) {
        TabView tabView = new TabView(getContext(), text, tabViews.size());
        tabView.setOnClickListener(v -> selectTab(tabView.getPosition()));
        tabViews.add(tabView);
        addView(tabView);

        if (tabViews.size() == 1) {
            selectTab(0);
        }
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    private void selectTab(int position) {
        if (position == selectedPosition) return;

        // 取消之前选中的
        tabViews.get(selectedPosition).setSelected(false);

        // 选中新的
        selectedPosition = position;
        TabView selectedTab = tabViews.get(position);
        selectedTab.setSelected(true);

        if (listener != null) {
            listener.onTabSelected(position, selectedTab.getText());
        }
    }

    // 内部 TabView 类
    private static class TabView extends LinearLayout {
        private TextView textView;
        private View indicator;
        private int position;
        private String text;
        private Drawable selectedBackground;
        private Drawable rippleDrawable;

        public TabView(Context context, String text, int position) {
            super(context);
            this.text = text;
            this.position = position;
            init(context);
        }

        private void init(Context context) {
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setClickable(true);
            setFocusable(true);

            // 固定高度，防止布局跳动
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48));
            setLayoutParams(params);

            // 固定内边距
            setPadding(dpToPx(4), 0, dpToPx(4), 0);

            // 预加载背景资源
            selectedBackground = context.getDrawable(R.drawable.bg_button_left_round);
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            rippleDrawable = context.getDrawable(outValue.resourceId);

            // 添加选中指示器
            indicator = new View(context);
            indicator.setBackgroundColor(Color.parseColor("#2962FF"));
            LayoutParams indicatorParams = new LayoutParams(dpToPx(3), dpToPx(20));
            indicatorParams.leftMargin = dpToPx(4);
            indicator.setLayoutParams(indicatorParams);
            indicator.setVisibility(INVISIBLE);
            addView(indicator);

            // 添加文字
            textView = new TextView(context);
            textView.setText(text);
            textView.setTextSize(14);
            textView.setTextColor(Color.parseColor("#666666"));
            textView.setGravity(Gravity.CENTER);
            textView.setMaxLines(2);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT);
            textParams.weight = 1;
            textParams.leftMargin = dpToPx(6);
            textParams.rightMargin = dpToPx(4);
            textView.setLayoutParams(textParams);
            addView(textView);

            // 默认使用透明背景
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            if (selected) {
                // 选中状态
                setBackground(selectedBackground);
                indicator.setVisibility(VISIBLE);
                textView.setTextColor(Color.parseColor("#2962FF"));
                textView.setTypeface(null, Typeface.BOLD);
            } else {
                // 未选中状态
                setBackground(rippleDrawable);
                indicator.setVisibility(INVISIBLE);
                textView.setTextColor(Color.parseColor("#666666"));
                textView.setTypeface(null, Typeface.NORMAL);
            }
        }

        public int getPosition() {
            return position;
        }

        public String getText() {
            return text;
        }

        private int dpToPx(int dp) {
            return (int) (dp * getResources().getDisplayMetrics().density);
        }
    }
}