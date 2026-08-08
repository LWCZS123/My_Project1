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

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.utils.ImageLoaderUtils;

public class ArchivedCategoryAdapter extends ListAdapter<Object, ArchivedCategoryAdapter.ViewHolder> {

    private final Context context;
    private OnRestoreClickListener listener;

    public interface OnRestoreClickListener {
        void onRestoreClick(Object item);
    }

    public void setOnRestoreClickListener(OnRestoreClickListener listener) {
        this.listener = listener;
    }

    public ArchivedCategoryAdapter(Context context) {
        super(new DiffUtil.ItemCallback<Object>() {
            @Override
            public boolean areItemsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                if (oldItem instanceof Category && newItem instanceof Category) {
                    return ((Category) oldItem).getId() == ((Category) newItem).getId();
                }
                if (oldItem instanceof SubCategory && newItem instanceof SubCategory) {
                    return ((SubCategory) oldItem).getId() == ((SubCategory) newItem).getId();
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                if (oldItem instanceof Category && newItem instanceof Category) {
                    return oldItem.equals(newItem);
                }
                if (oldItem instanceof SubCategory && newItem instanceof SubCategory) {
                    return oldItem.equals(newItem);
                }
                return false;
            }
        });
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_archived_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object item = getItem(position);
        if (item instanceof Category) {
            Category cat = (Category) item;
            holder.tvName.setText(cat.getName());
            String type = "expense".equals(cat.getType()) ? "支出" : "收入";
            holder.tvInfo.setText(type + " | 一级分类");
            
            Object source = ImageLoaderUtils.getGlideSource(context, cat.getIconUri());
            Glide.with(context).load(source).placeholder(R.drawable.ic_default_category).into(holder.ivIcon);
        } else if (item instanceof SubCategory) {
            SubCategory sub = (SubCategory) item;
            holder.tvName.setText(sub.getName());
            // 子分类信息比较难获取父分类名，除非传入包装对象。
            // 这里我们展示 "二级分类"
            holder.tvInfo.setText("二级分类");
            
            Object source = ImageLoaderUtils.getGlideSource(context, sub.getIconUri());
            Glide.with(context).load(source).placeholder(R.drawable.ic_default_category).into(holder.ivIcon);
        }

        holder.btnRestore.setOnClickListener(v -> {
            if (listener != null) listener.onRestoreClick(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvInfo;
        View btnRestore;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
            tvInfo = itemView.findViewById(R.id.tvInfo);
            btnRestore = itemView.findViewById(R.id.btnRestore);
        }
    }
}
