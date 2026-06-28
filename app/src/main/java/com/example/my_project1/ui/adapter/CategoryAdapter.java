package com.example.my_project1.ui.adapter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final Context context;
    private List<Category> categories = new ArrayList<>();
    private OnCategoryClickListener listener;
    // 用于保存每个分类的展开状态
    private Map<String, Boolean> expandedStates = new HashMap<>();

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
        void onSubCategoryClick(SubCategory subCategory);
        void onAddSubCategoryClick(Category category);
        void onMoreOptionsClick(Category category, View anchor);
    }

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public CategoryAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<Category> list) {
        categories = list;
        notifyDataSetChanged();
    }

    @Override
    public CategoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        Log.d("CategoryAdapter", "绑定分类: " + category.getName());
        Log.d("CategoryAdapter", "绑定分类图标1: " + category.getIconUri());
        Log.d("CategoryAdapter", "分类: " + category.getName()
                + " → 子分类数量: " + (category.getSubCategories() == null ? "null" : category.getSubCategories().size()));

        // 分类名
        holder.tvName.setText(category.getName());

        // 分类图标
        Glide.with(context)
                .load(category.getIconUri())
                .error(R.drawable.ic_default_category)
                .into(holder.ivIcon);

        // 更多按钮
        holder.ivMore.setOnClickListener(v -> {
            if (listener != null) listener.onMoreOptionsClick(category, v);
        });

        // 二级分类 RecyclerView - 只在第一次创建时设置 LayoutManager
        if (holder.rvSubCategory.getLayoutManager() == null) {
            androidx.recyclerview.widget.GridLayoutManager gridLayoutManager =
                    new androidx.recyclerview.widget.GridLayoutManager(context, 6);
            holder.rvSubCategory.setLayoutManager(gridLayoutManager);
        }

        // 获取或创建 SubCategoryAdapter
        SubCategoryAdapter subAdapter;
        if (holder.rvSubCategory.getAdapter() == null) {
            subAdapter = new SubCategoryAdapter(context);
            holder.rvSubCategory.setAdapter(subAdapter);
        } else {
            subAdapter = (SubCategoryAdapter) holder.rvSubCategory.getAdapter();
        }

        // 给子分类列表加默认"添加"按钮
        List<SubCategory> subList = new ArrayList<>();
        if (category.getSubCategories() != null && !category.getSubCategories().isEmpty()) {
            subList.addAll(category.getSubCategories());
        }

        subList.add(SubCategory.createAddButton());
        subAdapter.submitList(subList);

        // 子分类点击回调
        subAdapter.setOnSubCategoryClickListener(new SubCategoryAdapter.OnSubCategoryClickListener() {
            @Override
            public void onSubCategoryClick(SubCategory subCategory) {
                if(listener != null) listener.onSubCategoryClick(subCategory);
            }

            @Override
            public void onAddSubCategoryClick() {
                if (listener != null) listener.onAddSubCategoryClick(category);
            }
        });

        // 获取当前分类的展开状态（默认展开）
        String categoryId = String.valueOf(category.getId());
        boolean isExpanded = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            isExpanded = expandedStates.getOrDefault(categoryId, false);
        }

        // 设置初始状态（不使用动画）
        if (isExpanded) {
            holder.layoutSubContainer.setVisibility(View.VISIBLE);
            holder.layoutSubContainer.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.ivArrow.setRotation(90f);
        } else {
            holder.layoutSubContainer.setVisibility(View.GONE);
            holder.layoutSubContainer.getLayoutParams().height = 0;
            holder.ivArrow.setRotation(0f);
        }

        // 一级分类点击事件
        holder.layoutMain.setOnClickListener(v -> {
            boolean currentState = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                currentState = expandedStates.getOrDefault(categoryId, false);
            }
            boolean newState = !currentState;
            expandedStates.put(categoryId, newState);

            // 执行动画
            toggleSection(holder.layoutSubContainer, newState);
            holder.ivArrow.animate()
                    .rotation(newState ? 90f : 0f)
                    .setDuration(250)
                    .start();

            if (listener != null) listener.onCategoryClick(category);
        });
    }

    /** 平滑展开/折叠动画 */
    private void toggleSection(View section, boolean expand) {
        if (expand) {
            // 先让它可见，才能正确测量内容高度
            section.setVisibility(View.VISIBLE);
            section.measure(
                    View.MeasureSpec.makeMeasureSpec(((View) section.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED
            );
        }

        int startHeight = section.getHeight();
        int targetHeight = expand ? section.getMeasuredHeight() : 0;

        ValueAnimator animator = ValueAnimator.ofInt(startHeight, targetHeight);
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            section.getLayoutParams().height = value;
            section.requestLayout();
        });

        animator.setDuration(250);
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!expand) {
                    section.setVisibility(View.GONE);
                } else {
                    // 展开完成后，设置为 WRAP_CONTENT 并强制刷新
                    section.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    section.requestLayout();
                    // 确保子 RecyclerView 也重新布局
                    if (section instanceof ViewGroup) {
                        ViewGroup container = (ViewGroup) section;
                        for (int i = 0; i < container.getChildCount(); i++) {
                            View child = container.getChildAt(i);
                            if (child instanceof RecyclerView) {
                                child.requestLayout();
                            }
                        }
                    }
                }
            }
        });
        animator.start();
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon, ivMore, ivArrow;
        TextView tvName;
        LinearLayout layoutMain;
        RecyclerView rvSubCategory;
        View layoutSubContainer;

        CategoryViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.imageViewCategoryIcon);
            ivMore = itemView.findViewById(R.id.imageViewMoreOptions);
            ivArrow = itemView.findViewById(R.id.imageViewExpandArrow);
            tvName = itemView.findViewById(R.id.textViewCategoryName);
            layoutMain = itemView.findViewById(R.id.layoutCategoryMain);
            rvSubCategory = itemView.findViewById(R.id.recyclerViewSubCategories);
            layoutSubContainer = itemView.findViewById(R.id.layoutSubCategoriesContainer);
        }
    }
}