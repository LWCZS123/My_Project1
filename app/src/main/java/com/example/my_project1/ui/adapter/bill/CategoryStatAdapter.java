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
import com.example.my_project1.utils.GlideImageLoader;

import java.util.Objects;

import io.reactivex.annotations.NonNull;

/**
 * 饼图分类列表 Adapter
 * ...
 */
public class CategoryStatAdapter extends ListAdapter<CategoryStatAdapter.CategoryStatItem, CategoryStatAdapter.VH> {

    // ================================================================
    //  数据模型
    // ================================================================

    public static class CategoryStatItem {
        public final String categoryName;
        public final String categoryIconUrl;
        public final float  amount;
        public final float  percent;      // 0 ~ 100
        public final int    color;
        public final int    billCount;

        public CategoryStatItem(String name, String iconUrl, float amount,
                                float percent, int color, int count) {
            this.categoryName    = name;
            this.categoryIconUrl = iconUrl;
            this.amount          = amount;
            this.percent         = percent;
            this.color           = color;
            this.billCount       = count;
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
                    Objects.equals(categoryName, that.categoryName) &&
                    Objects.equals(categoryIconUrl, that.categoryIconUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryName, categoryIconUrl, amount, percent, color, billCount);
        }
    }

    public interface OnItemClickListener {
        void onItemClicked(CategoryStatItem item, int position);
    }

    // ================================================================
    //  成员
    // ================================================================

    private OnItemClickListener    listener;
    private int                    selectedPosition = -1;

    public CategoryStatAdapter() {
        super(new DiffUtil.ItemCallback<CategoryStatItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull CategoryStatItem oldItem, @NonNull CategoryStatItem newItem) {
                return oldItem.categoryName.equals(newItem.categoryName);
            }

            @Override
            public boolean areContentsTheSame(@NonNull CategoryStatItem oldItem, @NonNull CategoryStatItem newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    public void setSelectedPosition(int position) {
        selectedPosition = (selectedPosition == position) ? -1 : position;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    // ================================================================
    //  RecyclerView.Adapter
    // ================================================================

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryStatBinding b = ItemCategoryStatBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        CategoryStatItem item = getItem(pos);
        ItemCategoryStatBinding b = h.binding;

        // ── 文字 ──────────────────────────────────────────────────────
        b.tvCategoryName.setText(item.categoryName);
        b.tvCategoryPercent.setText(String.format("%.2f%%", item.percent));
        b.tvCategoryAmount.setText(String.format("¥%.2f", item.amount));
        b.tvBillCount.setText(item.billCount + "笔");

        // ── 图标圆背景（颜色变化才重建 Drawable）─────────────────────
        if (h.lastIconColor != item.color) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(withAlpha(item.color, 38));
            b.vIconBg.setBackground(circle);
            h.lastIconColor = item.color;
        }

        // ── Glide 加载图标 ────────────────────────────────────────────
        if (item.categoryIconUrl != null && !item.categoryIconUrl.isEmpty()) {
            GlideImageLoader.load(
                    b.ivCategoryIcon.getContext(),
                    item.categoryIconUrl,
                    b.ivCategoryIcon,
                    android.R.color.transparent,
                    android.R.color.transparent);
        }

        // ── 胶囊进度条 ────────────────────────────────────────────────
        updateProgressBar(h.binding.progressBar, item.percent, item.color, h);

        // ── 点击 ──────────────────────────────────────────────────────
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item, pos);
        });
    }

    private void updateProgressBar(ProgressBar bar, float percent, int color, VH h) {
        // percent [0,100] → progress [0,10000]，保留两位小数精度
        int progress = Math.round(percent * 100f);
        // 直接赋值，ProgressBar 内部做了值相等的短路判断，无额外开销
        bar.setProgress(progress);

        // 仅颜色变化时更新 ColorFilter，避免重复操作
        if (h.lastProgressColor != color) {
            Drawable progressDrawable = bar.getProgressDrawable();
            if (progressDrawable instanceof LayerDrawable) {
                // 找到填充层（android:id/progress）并应用颜色
                Drawable fillLayer = ((LayerDrawable) progressDrawable)
                        .findDrawableByLayerId(android.R.id.progress);
                if (fillLayer != null) {
                    fillLayer.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                }
            } else if (progressDrawable != null) {
                // 兜底：直接对整个 drawable 上色
                progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            h.lastProgressColor = color;
        }
    }

    // ================================================================
    //  ViewHolder
    // ================================================================

    static class VH extends RecyclerView.ViewHolder {
        final ItemCategoryStatBinding binding;

        // 缓存上次颜色，颜色不变时跳过 setColorFilter
        int lastIconColor     = Integer.MIN_VALUE;
        int lastProgressColor = Integer.MIN_VALUE;

        VH(ItemCategoryStatBinding b) {
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