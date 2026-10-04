package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.model.icon.DownloadRecord;
import com.example.my_project1.databinding.ItemIconRowBinding;
import com.example.my_project1.utils.GlideImageLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class IconRowAdapter extends RecyclerView.Adapter<IconRowAdapter.ViewHolder> {

    private final List<IconItem> originalList = new ArrayList<>();
    private final List<IconItem> filteredList = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final Map<String, String> downloadStates = new HashMap<>();
    private final Map<String, List<Integer>> positionsByIconId = new HashMap<>();
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
        rebuildPositionIndex();
        notifyDataSetChanged();
    }

    public void updateDownloadRecords(List<DownloadRecord> records) {
        Map<String, String> nextStates = new HashMap<>();
        if (records != null) {
            // DAO rows are timestamp-descending; keep the newest state for duplicate legacy rows.
            for (DownloadRecord record : records) {
                if (record.getIconId() != null && !nextStates.containsKey(record.getIconId())) {
                    nextStates.put(record.getIconId(), record.getStatus());
                }
            }
        }
        Set<String> allIds = new HashSet<>(downloadStates.keySet());
        allIds.addAll(nextStates.keySet());
        Set<String> changedIds = new HashSet<>();
        for (String iconId : allIds) {
            if (!Objects.equals(downloadStates.get(iconId), nextStates.get(iconId))) {
                changedIds.add(iconId);
            }
        }
        downloadStates.clear();
        downloadStates.putAll(nextStates);
        for (String iconId : changedIds) {
            List<Integer> positions = positionsByIconId.get(iconId);
            if (positions == null) continue;
            for (Integer position : positions) {
                if (position >= 0 && position < getItemCount()) {
                    notifyItemChanged(position, "download_state");
                }
            }
        }
    }

    public void markDownloadQueued(String iconId) {
        if (iconId == null) return;
        downloadStates.put(iconId, DownloadRecord.STATUS_PENDING);
        List<Integer> positions = positionsByIconId.get(iconId);
        if (positions != null) {
            for (Integer position : positions) notifyItemChanged(position, "download_state");
        }
    }

    private void rebuildPositionIndex() {
        positionsByIconId.clear();
        for (int i = 0; i < filteredList.size(); i++) {
            String id = filteredList.get(i).getId();
            if (id == null) continue;
            List<Integer> positions = positionsByIconId.get(id);
            if (positions == null) {
                positions = new ArrayList<>();
                positionsByIconId.put(id, positions);
            }
            positions.add(i);
        }
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains("download_state")) {
            IconItem item = filteredList.get(position);
            holder.bindStyle(item.getStyle());
            holder.bindDownloadState(item);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
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
            bindStyle(item.getStyle());
            bindDownloadState(item);

            binding.cbSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            binding.cbSelect.setChecked(selectedIds.contains(item.getId()));

            binding.btnRowDownload.setOnClickListener(v -> {
                String status = downloadStates.get(item.getId());
                boolean blocked = DownloadRecord.STATUS_SUCCESS.equals(status)
                        || DownloadRecord.STATUS_PENDING.equals(status)
                        || DownloadRecord.STATUS_DOWNLOADING.equals(status);
                if (!blocked && actionListener != null) actionListener.onDownloadClick(item);
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

        private void bindStyle(String style) {
            int colorRes;
            if ("line".equals(style)) {
                binding.tvRowTag.setText("线性");
                colorRes = com.example.my_project1.R.color.icon_style_line;
            } else if ("lineal-color".equals(style)) {
                binding.tvRowTag.setText("彩色");
                colorRes = com.example.my_project1.R.color.icon_style_color;
            } else {
                binding.tvRowTag.setText("填充");
                colorRes = com.example.my_project1.R.color.icon_style_filled;
            }
            binding.tvRowTag.setTextColor(ContextCompat.getColor(itemView.getContext(), colorRes));
            binding.btnRowDownload.setIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), colorRes)));
            binding.btnRowDownload.setTextColor(ContextCompat.getColor(itemView.getContext(), colorRes));
        }

        private void bindDownloadState(IconItem item) {
            String status = downloadStates.get(item.getId());
            if (DownloadRecord.STATUS_SUCCESS.equals(status)) {
                binding.btnRowDownload.setText("已下载");
                binding.btnRowDownload.setEnabled(false);
                binding.btnRowDownload.setAlpha(0.62f);
                binding.btnRowDownload.setIconResource(com.example.my_project1.R.drawable.ic_accept);
            } else if (DownloadRecord.STATUS_PENDING.equals(status)
                    || DownloadRecord.STATUS_DOWNLOADING.equals(status)) {
                binding.btnRowDownload.setText("下载中");
                binding.btnRowDownload.setEnabled(false);
                binding.btnRowDownload.setAlpha(0.62f);
                binding.btnRowDownload.setIconResource(com.example.my_project1.R.drawable.ic_download);
            } else {
                binding.btnRowDownload.setText(DownloadRecord.STATUS_FAILED.equals(status)
                        ? "重试" : "下载");
                binding.btnRowDownload.setEnabled(true);
                binding.btnRowDownload.setAlpha(1f);
                binding.btnRowDownload.setIconResource(com.example.my_project1.R.drawable.ic_download);
            }
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
