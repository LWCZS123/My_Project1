package com.example.my_project1.ui.adapter.icon;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconCategory;

import java.util.ArrayList;
import java.util.List;

public class IconSelectionCategoryAdapter extends RecyclerView.Adapter<IconSelectionCategoryAdapter.VH> {

    private List<IconCategory> list = new ArrayList<>();
    private IconCategory selectedCategory;
    private final OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(IconCategory category);
    }

    public IconSelectionCategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<IconCategory> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public void setSelectedCategory(IconCategory category) {
        this.selectedCategory = category;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_selection_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        IconCategory item = list.get(position);
        String name = item.getCategory();
        // 限制 5 个字以内
        if (name != null && name.length() > 5) {
            name = name.substring(0, 5);
        }
        holder.tvName.setText(name);

        boolean isSelected = selectedCategory != null && selectedCategory.getCategory().equals(item.getCategory());
        
        if (isSelected) {
            holder.tvName.setBackgroundResource(R.drawable.bg_capsule_blue);
            holder.tvName.setTextColor(Color.WHITE);
            holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.tvName.setBackgroundResource(R.drawable.bg_pill_gray);
            holder.tvName.setTextColor(Color.parseColor("#999999"));
            holder.tvName.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        holder.itemView.setBackgroundColor(Color.WHITE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCategoryClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvName;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCategoryName);
        }
    }
}
