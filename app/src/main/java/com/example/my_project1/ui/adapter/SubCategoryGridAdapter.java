package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.SubCategory;

import java.util.ArrayList;
import java.util.List;

public class SubCategoryGridAdapter extends RecyclerView.Adapter<SubCategoryGridAdapter.GridViewHolder> {

    private final Context context;
    private final List<SubCategory> mList = new ArrayList<>();
    private OnSubCategoryClickListener listener;

    public interface OnSubCategoryClickListener {
        void onSubCategoryClick(SubCategory subCategory);
        void onAddSubCategoryClick();
        void onSubCategoryLongClick(SubCategory subCategory);
    }

    public void setOnSubCategoryClickListener(OnSubCategoryClickListener listener) {
        this.listener = listener;
    }

    public SubCategoryGridAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<SubCategory> newList) {
        mList.clear();
        if (newList != null) {
            mList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public List<SubCategory> getCurrentList() {
        return mList;
    }

    /**
     * 拖拽期间同步交换数据源，避免 DiffUtil 异步导致的回弹和重复
     */
    public void moveItem(int fromPos, int toPos) {
        if (fromPos < 0 || toPos < 0 || fromPos >= mList.size() || toPos >= mList.size()) {
            return;
        }
        
        // 特殊逻辑：如果是拖动到最后一位（添加按钮），或者从最后一位拖动，则禁止
        if (mList.get(fromPos).isAddButton() || mList.get(toPos).isAddButton()) {
            return;
        }

        SubCategory item = mList.remove(fromPos);
        mList.add(toPos, item);
        notifyItemMoved(fromPos, toPos);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subcategory_grid, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        SubCategory sub = mList.get(position);

        if (sub.isAddButton()) {
            holder.tvName.setText("添加");
            Glide.with(context)
                    .load(R.drawable.ic_add)
                    .into(holder.ivIcon);
            holder.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round);
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAddSubCategoryClick();
            });
            holder.itemView.setOnLongClickListener(null);
        } else {
            holder.tvName.setText(sub.getName());
            Glide.with(context)
                    .load(sub.getIconUri())
                    .error(R.drawable.ic_default_category)
                    .into(holder.ivIcon);

            // 🎨 设置图标背景色
            if (sub.getIconBackgroundColor() != null && !sub.getIconBackgroundColor().isEmpty()) {
                try {
                    int color = Color.parseColor(sub.getIconBackgroundColor());
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(color);
                    holder.rlIconContainer.setBackground(gd);
                } catch (Exception e) {
                    holder.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round);
                }
            } else {
                holder.rlIconContainer.setBackgroundResource(R.drawable.bg_category_round);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onSubCategoryClick(sub);
            });
            
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onSubCategoryLongClick(sub);
                return true;
            });
        }
    }

    static class GridViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        View rlIconContainer;

        GridViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_subcategory_icon);
            tvName = itemView.findViewById(R.id.tv_subcategory_name);
            rlIconContainer = itemView.findViewById(R.id.rl_icon_container);
        }
    }
}
