package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final Context context;
    private final List<Category> mList = new ArrayList<>();
    private OnCategoryClickListener listener;

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

    /**
     * 更新数据源。
     * 使用同步更新，确保数据一致性。
     */
    public void submitList(List<Category> newList) {
        mList.clear();
        if (newList != null) {
            mList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    /**
     * 拖拽排序位移。
     * 核心：必须同步修改数据源 mList，否则会出现 item 重复或错乱。
     */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= mList.size() || toPosition >= mList.size()) {
            return;
        }
        Category item = mList.remove(fromPosition);
        mList.add(toPosition, item);
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<Category> getCurrentList() {
        return mList;
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @Override
    public CategoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = mList.get(position);
        
        holder.tvName.setText(category.getName());

        Glide.with(context)
                .load(category.getIconUri())
                .placeholder(R.drawable.ic_default_category)
                .error(R.drawable.ic_default_category)
                .into(holder.ivIcon);

        int subCount = category.getSubCategories() != null ? category.getSubCategories().size() : 0;
        String typePrefix = "expense".equals(category.getType()) ? "支出" : "收入";
        int indicatorColor = "expense".equals(category.getType()) ? 0xFF4169E1 : 0xFFFF8C00;
        
        holder.tvSubCount.setText(String.format("%s%d类", typePrefix, subCount));
        holder.viewIndicator.setBackgroundColor(indicatorColor);

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

            List<SubCategory> previewList = new ArrayList<>();
            if (category.getSubCategories() != null) {
                int count = Math.min(category.getSubCategories().size(), 4);
                for (int i = 0; i < count; i++) {
                    previewList.add(category.getSubCategories().get(i));
                }
            }
            subAdapter.submitList(previewList);
        }

        String note = category.getNote();
        if (note != null && !note.isEmpty()) {
            holder.tvNote.setText(note);
            holder.tvNote.setVisibility(View.VISIBLE);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

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
