package com.example.my_project1.ui.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.SubCategory;

public class SubCategoryAdapter extends ListAdapter<SubCategory, SubCategoryAdapter.SubViewHolder> {

    private final Context context;
    private OnSubCategoryClickListener listener;

    public interface OnSubCategoryClickListener {
        void onSubCategoryClick(SubCategory subCategory);
        void onAddSubCategoryClick();
    }

    public void setOnSubCategoryClickListener(OnSubCategoryClickListener listener) {
        this.listener = listener;
    }

    public SubCategoryAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
    }

    @Override
    public SubViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sub_category, parent, false);
        return new SubViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SubViewHolder holder, int position) {
        SubCategory sub = getItem(position);

        if (sub.isAddButton()) {
            holder.tvName.setText("添加");
            Glide.with(context)
                    .load(R.drawable.ic_add_circle)
                    .into(holder.ivIcon);
            holder.cardView.setOnClickListener(v -> {
                if (listener != null) listener.onAddSubCategoryClick();
            });
        } else {
            holder.tvName.setText(sub.getName());
            // 加载图标，优先本地资源
            Glide.with(context)
                    .load(sub.getIconUri())
                    .error(R.drawable.ic_default_category)
                    .into(holder.ivIcon);

            holder.cardView.setOnClickListener(v -> {
                if (listener != null) listener.onSubCategoryClick(sub);
            });
        }
    }

    static class SubViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        CardView cardView;

        SubViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            cardView = itemView.findViewById(R.id.cardView);
        }
    }

    public static final DiffUtil.ItemCallback<SubCategory> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SubCategory>() {
                @Override
                public boolean areItemsTheSame(SubCategory oldItem, SubCategory newItem) {
                    return oldItem.isAddButton() && newItem.isAddButton()
                            || oldItem.getId() == newItem.getId();
                }

                @SuppressLint("DiffUtilEquals")
                @Override
                public boolean areContentsTheSame(SubCategory oldItem, SubCategory newItem) {
                    return oldItem.equals(newItem);
                }
            };
}
