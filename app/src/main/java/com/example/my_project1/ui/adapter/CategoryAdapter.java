package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一级分类列表适配器
 * 使用 ListAdapter 确保 UI 更新的性能和线程安全
 */
public class CategoryAdapter extends ListAdapter<Category, CategoryAdapter.CategoryViewHolder> {

    private final Context context;
    private OnCategoryClickListener listener;

    /**
     * 分类点击事件监听器
     */
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
        super(DIFF_CALLBACK);
        this.context = context;
    }

    /**
     * 处理拖拽排序时的逻辑位移
     * 核心：必须创建新列表并提交，以符合 ListAdapter 的差异计算机制
     */
    public void moveItem(int fromPosition, int toPosition) {
        List<Category> list = new ArrayList<>(getCurrentList());
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= list.size() || toPosition >= list.size()) {
            return;
        }
        Collections.swap(list, fromPosition, toPosition);
        submitList(list);
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = getItem(position);
        if (category == null) return;
        
        holder.tvName.setText(category.getName());

        Glide.with(context)
                .load(category.getIconUri())
                .placeholder(R.drawable.ic_default_category)
                .error(R.drawable.ic_default_category)
                .into(holder.ivIcon);

        int subCount = category.getSubCategories() != null ? category.getSubCategories().size() : 0;
        String type = category.getType();
        String typePrefix = "expense".equals(type) ? "支出" : "收入";
        int indicatorColor = "expense".equals(type) ? 0xFF4169E1 : 0xFFFF8C00;
        
        holder.tvSubCount.setText(String.format("%s%d类", typePrefix, subCount));
        holder.viewIndicator.setBackgroundColor(indicatorColor);

        // 处理子分类预览
        if (subCount == 0) {
            holder.layoutPreviewContainer.setVisibility(View.GONE);
            holder.dividerTop.setVisibility(View.GONE);
            holder.dividerBottom.setVisibility(View.GONE);
        } else {
            holder.layoutPreviewContainer.setVisibility(View.VISIBLE);
            holder.dividerTop.setVisibility(View.VISIBLE);
            holder.dividerBottom.setVisibility(View.VISIBLE);

            if (holder.rvSubPreview.getLayoutManager() == null) {
                holder.rvSubPreview.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            }
            
            SubCategoryAdapter subAdapter;
            if (holder.rvSubPreview.getAdapter() == null) {
                subAdapter = new SubCategoryAdapter(context);
                holder.rvSubPreview.setAdapter(subAdapter);
            } else {
                subAdapter = (SubCategoryAdapter) holder.rvSubPreview.getAdapter();
            }

            subAdapter.setOnSubCategoryClickListener(new SubCategoryAdapter.OnSubCategoryClickListener() {
                @Override
                public void onSubCategoryClick(SubCategory subCategory) {
                    if (listener != null) listener.onSubCategoryClick(subCategory);
                }

                @Override
                public void onAddSubCategoryClick() {
                    if (listener != null) listener.onAddSubCategoryClick(category);
                }
            });

            // 仅展示前 4 个子分类
            List<SubCategory> previewList = new ArrayList<>();
            if (category.getSubCategories() != null) {
                int count = Math.min(category.getSubCategories().size(), 4);
                for (int i = 0; i < count; i++) {
                    previewList.add(category.getSubCategories().get(i));
                }
            }
            subAdapter.submitList(previewList);
        }

        // 处理备注显示
        String note = category.getNote();
        if (note != null && !note.isEmpty()) {
            holder.tvNote.setText(note);
            holder.tvNote.setVisibility(View.VISIBLE);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
        });

        holder.layoutMore.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(category);
        });

        holder.ivMore.setOnClickListener(v -> {
            if (listener != null) listener.onMoreOptionsClick(category, v);
        });
    }

    private static final DiffUtil.ItemCallback<Category> DIFF_CALLBACK = new DiffUtil.ItemCallback<Category>() {
        @Override
        public boolean areItemsTheSame(@NonNull Category oldItem, @NonNull Category newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Category oldItem, @NonNull Category newItem) {
            return oldItem.equals(newItem);
        }
    };

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon, ivMore;
        TextView tvName, tvSubCount, tvNote;
        RecyclerView rvSubPreview;
        View layoutMore, layoutPreviewContainer, dividerTop, dividerBottom, layoutStat, viewIndicator;

        CategoryViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.imageViewCategoryIcon);
            ivMore = itemView.findViewById(R.id.imageViewMoreOptions);
            tvName = itemView.findViewById(R.id.textViewCategoryName);
            tvSubCount = itemView.findViewById(R.id.textViewSubCategoryCount);
            tvNote = itemView.findViewById(R.id.textViewCategoryNote);
            rvSubPreview = itemView.findViewById(R.id.recyclerViewSubPreview);
            layoutMore = itemView.findViewById(R.id.layoutMore);
            layoutPreviewContainer = itemView.findViewById(R.id.layoutSubPreviewContainer);
            dividerTop = itemView.findViewById(R.id.dividerSubPreviewTop);
            dividerBottom = itemView.findViewById(R.id.dividerSubPreviewBottom);
            layoutStat = itemView.findViewById(R.id.layoutStatContainer);
            viewIndicator = itemView.findViewById(R.id.viewStatIndicator);
        }
    }
}
