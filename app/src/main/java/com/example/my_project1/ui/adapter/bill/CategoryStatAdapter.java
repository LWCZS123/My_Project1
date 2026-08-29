package com.example.my_project1.ui.adapter.bill;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemCategoryStatBinding;
import com.example.my_project1.databinding.ItemSubCategoryStatBinding;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.reactivex.annotations.NonNull;

/**
 * 饼图分类列表 Adapter (支持层级展开)
 */
public class CategoryStatAdapter extends ListAdapter<CategoryStatAdapter.CategoryStatItem, RecyclerView.ViewHolder> {

    private static final int TYPE_PARENT = 1;
    private static final int TYPE_CHILD  = 2;

    // ================================================================
    //  数据模型
    // ================================================================

    public static class CategoryStatItem {
        public final String categoryId;
        public final String categoryName;
        public final String categoryIconUrl;
        public final float  amount;
        public final float  percent;      // 对父级或总额的百分比
        public final int    color;
        public final int    billCount;
        public final int    level;        // 1=一级, 2=二级
        
        public boolean isExpanded = false;
        public List<CategoryStatItem> subItems = new ArrayList<>();

        public CategoryStatItem(String id, String name, String iconUrl, float amount,
                                float percent, int color, int count, int level) {
            this.categoryId      = id;
            this.categoryName    = name;
            this.categoryIconUrl = iconUrl;
            this.amount          = amount;
            this.percent         = percent;
            this.color           = color;
            this.billCount       = count;
            this.level           = level;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CategoryStatItem that = (CategoryStatItem) o;
            return Float.compare(that.amount, amount) == 0 &&
                    Float.compare(that.percent, percent) == 0 &&
                    color == that.color &&
                    billCount == that.billCount &&
                    level == that.level &&
                    isExpanded == that.isExpanded &&
                    Objects.equals(categoryId, that.categoryId) &&
                    Objects.equals(categoryName, that.categoryName) &&
                    Objects.equals(categoryIconUrl, that.categoryIconUrl) &&
                    Objects.equals(subItems, that.subItems);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryId, categoryName, categoryIconUrl, amount, percent, color, billCount, level, isExpanded, subItems);
        }
    }

    public interface OnItemClickListener {
        void onItemClicked(CategoryStatItem item, int position);
    }

    // ================================================================
    //  成员
    // ================================================================

    private OnItemClickListener    listener;

    public CategoryStatAdapter() {
        super(new DiffUtil.ItemCallback<CategoryStatItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull CategoryStatItem oldItem, @NonNull CategoryStatItem newItem) {
                return Objects.equals(oldItem.categoryId, newItem.categoryId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull CategoryStatItem oldItem, @NonNull CategoryStatItem newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).level == 1 ? TYPE_PARENT : TYPE_CHILD;
    }

    // ================================================================
    //  RecyclerView.Adapter
    // ================================================================

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_PARENT) {
            ItemCategoryStatBinding b = ItemCategoryStatBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ParentVH(b);
        } else {
            ItemSubCategoryStatBinding b = ItemSubCategoryStatBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ChildVH(b);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        CategoryStatItem item = getItem(pos);
        if (holder instanceof ParentVH) {
            bindParent((ParentVH) holder, item, pos);
        } else if (holder instanceof ChildVH) {
            bindChild((ChildVH) holder, item, pos);
        }
    }

    private void bindParent(ParentVH h, CategoryStatItem item, int pos) {
        ItemCategoryStatBinding b = h.binding;

        b.tvCategoryName.setText(item.categoryName);
        b.tvCategoryPercent.setText(String.format("%.2f%%", item.percent));
        b.tvCategoryAmount.setText(String.format("¥%.2f", item.amount));
        b.tvBillCount.setText(item.billCount + "笔");

        // 箭头指向与可见性
        b.ivArrow.setRotation(item.isExpanded ? 0f : -90f);
        b.ivArrow.setVisibility(item.subItems.isEmpty() ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        // 整个右侧感应区始终显示（展示金额和笔数）
        b.llExpandArea.setVisibility(android.view.View.VISIBLE);

        if (h.lastIconColor != item.color) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(withAlpha(item.color, 38));
            b.vIconBg.setBackground(circle);
            h.lastIconColor = item.color;
        }

        if (item.categoryIconUrl != null && !item.categoryIconUrl.isEmpty()) {
            GlideImageLoader.load(b.ivCategoryIcon.getContext(), item.categoryIconUrl, b.ivCategoryIcon,
                    android.R.color.transparent, android.R.color.transparent);
        }

        updateProgressBar(b.progressBar, item.percent, item.color, h);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item, pos);
        });

        // 整个进度条后方的区域专门处理展开/收起
        b.llExpandArea.setOnClickListener(v -> {
            if (!item.subItems.isEmpty() && expandListener != null) {
                expandListener.onExpandClicked(item);
            }
        });
    }

    public interface OnExpandClickListener {
        void onExpandClicked(CategoryStatItem item);
    }

    private OnExpandClickListener expandListener;

    public void setOnExpandClickListener(OnExpandClickListener l) {
        this.expandListener = l;
    }

    private void bindChild(ChildVH h, CategoryStatItem item, int pos) {
        ItemSubCategoryStatBinding b = h.binding;
        b.tvSubName.setText(item.categoryName);
        b.tvSubInfo.setText(String.format("¥%.2f [%d笔/%.2f%%]", item.amount, item.billCount, item.percent));

        if (item.categoryIconUrl != null && !item.categoryIconUrl.isEmpty()) {
            GlideImageLoader.load(b.ivSubIcon.getContext(), item.categoryIconUrl, b.ivSubIcon,
                    android.R.color.transparent, android.R.color.transparent);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item, pos);
        });
    }

    private void updateProgressBar(ProgressBar bar, float percent, int color, ParentVH h) {
        int progress = Math.round(percent * 100f);
        bar.setProgress(progress);

        if (h.lastProgressColor != color) {
            Drawable progressDrawable = bar.getProgressDrawable();
            if (progressDrawable instanceof LayerDrawable) {
                Drawable fillLayer = ((LayerDrawable) progressDrawable).findDrawableByLayerId(android.R.id.progress);
                if (fillLayer != null) fillLayer.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            } else if (progressDrawable != null) {
                progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            h.lastProgressColor = color;
        }
    }

    // ================================================================
    //  ViewHolder
    // ================================================================

    static class ParentVH extends RecyclerView.ViewHolder {
        final ItemCategoryStatBinding binding;
        int lastIconColor     = Integer.MIN_VALUE;
        int lastProgressColor = Integer.MIN_VALUE;

        ParentVH(ItemCategoryStatBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }

    static class ChildVH extends RecyclerView.ViewHolder {
        final ItemSubCategoryStatBinding binding;
        ChildVH(ItemSubCategoryStatBinding b) {
            super(b.getRoot());
            binding = b;
        }
    }

    // ================================================================
    //  工具
    // ================================================================

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}