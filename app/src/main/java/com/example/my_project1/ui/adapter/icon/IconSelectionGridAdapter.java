package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

public class IconSelectionGridAdapter extends RecyclerView.Adapter<IconSelectionGridAdapter.VH> {

    private List<IconItem> list = new ArrayList<>();
    private final OnIconClickListener listener;

    public interface OnIconClickListener {
        void onIconClick(IconItem icon);
    }

    public IconSelectionGridAdapter(OnIconClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<IconItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_selection_grid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        IconItem item = list.get(position);
        String name = item.getName();
        if (name != null && name.length() > 4) {
            name = name.substring(0, 4);
        }
        holder.tvName.setText(name);
        ImageLoaderUtils.load(holder.itemView.getContext(), item.getThumbUrl(), holder.ivIcon);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onIconClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;

        public VH(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvIconName);
        }
    }
}
