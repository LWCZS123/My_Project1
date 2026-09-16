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
import com.example.my_project1.utils.GlideImageLoader;
import java.util.ArrayList;
import java.util.List;

public class IconRowAdapter extends RecyclerView.Adapter<IconRowAdapter.ViewHolder> {

    private final List<IconItem> originalList = new ArrayList<>();
    private final List<IconItem> filteredList = new ArrayList<>();
    private final OnItemClickListener downloadClickListener;

    public interface OnItemClickListener {
        void onItemClick(IconItem item);
    }

    public IconRowAdapter(OnItemClickListener downloadClickListener) {
        this.downloadClickListener = downloadClickListener;
    }

    public void setData(List<IconItem> list) {
        originalList.clear();
        if (list != null) originalList.addAll(list);
        filter("");
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (IconItem item : originalList) {
                if ((item.getName() != null && item.getName().toLowerCase().contains(lowerQuery)) || 
                    (item.getPinyin() != null && item.getPinyin().toLowerCase().contains(lowerQuery))) {
                    filteredList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getFilteredCount() {
        return filteredList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IconItem item = filteredList.get(position);
        holder.tvNameCn.setText(item.getName());
        holder.tvNameEn.setText(item.getCategory() != null ? item.getCategory() : "");
        
        GlideImageLoader.loadThumbnail(holder.itemView.getContext(), item.getThumbUrl(), holder.ivPreview);

        holder.btnDownload.setOnClickListener(v -> {
            if (downloadClickListener != null) {
                downloadClickListener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNameCn;
        TextView tvNameEn;
        ImageView ivPreview;
        View btnDownload;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNameCn = itemView.findViewById(R.id.tv_name_cn);
            tvNameEn = itemView.findViewById(R.id.tv_name_en);
            ivPreview = itemView.findViewById(R.id.iv_icon_preview);
            btnDownload = itemView.findViewById(R.id.btn_row_download);
        }
    }
}
