package com.example.my_project1.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ItemIconDownloadBinding;
import java.util.ArrayList;
import java.util.List;

public class IconDownloadAdapter extends RecyclerView.Adapter<IconDownloadAdapter.ViewHolder> {

    private List<IconItem> items = new ArrayList<>();

    public void setItems(List<IconItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIconDownloadBinding binding = ItemIconDownloadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemIconDownloadBinding binding;

        public ViewHolder(ItemIconDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(IconItem item) {
            binding.tvIconName.setText(item.getName());
            // 模拟下载完成状态
            binding.tvIconStatus.setText("已完成");
            binding.ivStatusIcon.setVisibility(View.VISIBLE);
            
            Glide.with(binding.ivIcon.getContext())
                    .load(item.getThumbUrl())
                    .placeholder(R.drawable.ic_placeholder_camera)
                    .into(binding.ivIcon);
        }
    }
}