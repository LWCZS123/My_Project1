package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.CategorySelectItem;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class CategorySelectAdapter extends RecyclerView.Adapter<CategorySelectAdapter.ViewHolder> {

    private List<CategorySelectItem> items = new ArrayList<>();
    private final Context context;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(CategorySelectItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public CategorySelectAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<CategorySelectItem> newList) {
        this.items = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategorySelectItem item = items.get(position);
        holder.tvName.setText(item.getName());
        
        Object source = ImageLoaderUtils.getGlideSource(context, item.getIcon());
        Glide.with(context)
                .load(source)
                .placeholder(R.drawable.ic_default_category)
                .into(holder.ivIcon);

        if (item.getIconBgColor() != null) {
            try {
                holder.cardIcon.setCardBackgroundColor(Color.parseColor(item.getIconBgColor()));
            } catch (Exception e) {
                holder.cardIcon.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
            }
        } else {
            holder.cardIcon.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        MaterialCardView cardIcon;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
            cardIcon = itemView.findViewById(R.id.cardIcon);
        }
    }
}
