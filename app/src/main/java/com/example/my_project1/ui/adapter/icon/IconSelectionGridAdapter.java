package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;

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
    private String selectedIconUrl;

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

    public void setSelectedIcon(IconItem icon) {
        selectedIconUrl = icon == null ? null : icon.getUrl();
        notifyDataSetChanged();
    }

    public void setSelectedIconUrl(String iconUrl) {
        selectedIconUrl = iconUrl;
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
        boolean selected = selectedIconUrl != null && selectedIconUrl.equals(item.getUrl());
        holder.iconContainer.setBackgroundResource(selected
                ? R.drawable.bg_circle_icon_selected : R.drawable.bg_circle_grey);
        holder.ivSelected.setVisibility(selected ? View.VISIBLE : View.GONE);

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
        ImageView ivSelected;
        FrameLayout iconContainer;
        TextView tvName;

        public VH(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            ivSelected = itemView.findViewById(R.id.iv_selected);
            iconContainer = itemView.findViewById(R.id.icon_container);
            tvName = itemView.findViewById(R.id.tvIconName);
        }
    }
}
