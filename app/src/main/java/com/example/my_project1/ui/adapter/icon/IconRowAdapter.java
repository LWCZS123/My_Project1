package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ItemIconRowBinding;
import com.example.my_project1.utils.GlideImageLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconRowAdapter extends RecyclerView.Adapter<IconRowAdapter.ViewHolder> {

    private final List<IconItem> originalList = new ArrayList<>();
    private final List<IconItem> filteredList = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;
    private final OnIconActionListener actionListener;

    public interface OnIconActionListener {
        void onDownloadClick(IconItem item);
        void onItemClick(IconItem item);
        void onSelectionChanged(int selectedCount);
    }

    public IconRowAdapter(OnIconActionListener actionListener) {
        this.actionListener = actionListener;
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

    public List<IconItem> getSelectedItems() {
        List<IconItem> selected = new ArrayList<>();
        for (IconItem item : originalList) {
            if (selectedIds.contains(item.getId())) {
                selected.add(item);
            }
        }
        return selected;
    }

    public List<IconItem> getAllItems() {
        return new ArrayList<>(originalList);
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void exitSelectionMode() {
        isSelectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (actionListener != null) actionListener.onSelectionChanged(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIconRowBinding binding = ItemIconRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IconItem item = filteredList.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemIconRowBinding binding;

        public ViewHolder(ItemIconRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(IconItem item) {
            binding.tvNameCn.setText(item.getName());
            binding.tvNameEn.setText(item.getCategory() != null ? item.getCategory() : "");
            GlideImageLoader.loadThumbnail(itemView.getContext(), item.getThumbUrl(), binding.ivIconPreview);

            binding.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            binding.cbSelect.setChecked(selectedIds.contains(item.getId()));

            binding.btnRowDownload.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onDownloadClick(item);
            });

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(item);
                } else if (actionListener != null) {
                    actionListener.onItemClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    isSelectionMode = true;
                    toggleSelection(item);
                    notifyDataSetChanged();
                    return true;
                }
                return false;
            });

            binding.cbSelect.setOnClickListener(v -> toggleSelection(item));
        }

        private void toggleSelection(IconItem item) {
            if (selectedIds.contains(item.getId())) {
                selectedIds.remove(item.getId());
            } else {
                selectedIds.add(item.getId());
            }
            binding.cbSelect.setChecked(selectedIds.contains(item.getId()));
            if (actionListener != null) {
                actionListener.onSelectionChanged(selectedIds.size());
            }
        }
    }
}
