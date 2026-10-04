package com.example.my_project1.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.DownloadRecord;
import com.example.my_project1.databinding.ItemCollectionDownloadBinding;
import com.example.my_project1.databinding.ItemIconDownloadBinding;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class IconDownloadAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_COLLECTION = 0;
    private static final int TYPE_SINGLE_ICON = 1;

    private final AsyncListDiffer<Object> differ =
            new AsyncListDiffer<>(this, IconDownloadAdapter.ITEM_CALLBACK);
    private OnItemActionListener listener;
    private int currentFilterType = 0; // 0 for Collection, 1 for Single Icon
    private List<DownloadRecord> lastRecords = new ArrayList<>();
    private final AtomicInteger rebuildGeneration = new AtomicInteger();

    public IconDownloadAdapter() {
        setHasStableIds(true);
    }

    private static final DiffUtil.ItemCallback<Object> ITEM_CALLBACK =
            new DiffUtil.ItemCallback<Object>() {
                @Override
                public boolean areItemsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                    if (oldItem instanceof CollectionItem && newItem instanceof CollectionItem) {
                        return Objects.equals(((CollectionItem) oldItem).batchId,
                                ((CollectionItem) newItem).batchId);
                    }
                    if (oldItem instanceof DownloadRecord && newItem instanceof DownloadRecord) {
                        DownloadRecord oldRecord = (DownloadRecord) oldItem;
                        DownloadRecord newRecord = (DownloadRecord) newItem;
                        return Objects.equals(oldRecord.getIconId(), newRecord.getIconId())
                                && Objects.equals(oldRecord.getBatchId(), newRecord.getBatchId());
                    }
                    return false;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Object oldItem, @NonNull Object newItem) {
                    if (oldItem instanceof CollectionItem && newItem instanceof CollectionItem) {
                        return ((CollectionItem) oldItem).contentSignature
                                .equals(((CollectionItem) newItem).contentSignature);
                    }
                    if (oldItem instanceof DownloadRecord && newItem instanceof DownloadRecord) {
                        DownloadRecord a = (DownloadRecord) oldItem;
                        DownloadRecord b = (DownloadRecord) newItem;
                        return Objects.equals(a.getStatus(), b.getStatus())
                                && a.getProgress() == b.getProgress()
                                && a.getDownloadedBytes() == b.getDownloadedBytes()
                                && a.getTotalBytes() == b.getTotalBytes()
                                && Objects.equals(a.getLocalPath(), b.getLocalPath())
                                && Objects.equals(a.getErrorMessage(), b.getErrorMessage())
                                && Objects.equals(a.getThumbUrl(), b.getThumbUrl());
                    }
                    return false;
                }
            };

    public interface OnItemActionListener {
        void onRetryIcon(String iconId);
        void onRetryCollection(String batchId);
        void onOpenFolder(String subDir, String treeUri);
    }

    public void setListener(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void setFilterType(int filterType) {
        if (currentFilterType == filterType) return;
        this.currentFilterType = filterType;
        rebuildDisplayItems();
    }

    public void setItems(List<DownloadRecord> records) {
        this.lastRecords = records == null ? new ArrayList<>() : new ArrayList<>(records);
        rebuildDisplayItems();
    }

    private void rebuildDisplayItems() {
        final int generation = rebuildGeneration.incrementAndGet();
        final int filterType = currentFilterType;
        final List<DownloadRecord> records = new ArrayList<>(lastRecords);
        AppExecutors.get().computation().execute(() -> {
            Map<String, List<DownloadRecord>> grouped = new LinkedHashMap<>();
            for (DownloadRecord r : records) {
                if (r.getBatchId() != null && !r.getBatchId().startsWith("single_")) {
                    if (!grouped.containsKey(r.getBatchId())) {
                        grouped.put(r.getBatchId(), new ArrayList<>());
                    }
                    grouped.get(r.getBatchId()).add(r);
                }
            }

            List<Object> newList = new ArrayList<>();
            if (filterType == 0) {
                // 合集页签只展示聚合后的合集，避免重复平铺合集中的每个图标。
                for (Map.Entry<String, List<DownloadRecord>> entry : grouped.entrySet()) {
                    newList.add(new CollectionItem(entry.getKey(), entry.getValue()));
                }
            } else {
                // 单图标页签只展示独立下载记录，不展开合集的全部图标。
                for (DownloadRecord r : records) {
                    if (r.getBatchId() == null || r.getBatchId().startsWith("single_")) {
                        newList.add(r);
                    }
                }
            }
            AppExecutors.get().mainThread().execute(() -> {
                if (generation == rebuildGeneration.get()) differ.submitList(newList);
            });
        });
    }

    @Override
    public int getItemViewType(int position) {
        return differ.getCurrentList().get(position) instanceof CollectionItem
                ? TYPE_COLLECTION : TYPE_SINGLE_ICON;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_COLLECTION) {
            ItemCollectionDownloadBinding binding = ItemCollectionDownloadBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new CollectionViewHolder(binding);
        } else {
            ItemIconDownloadBinding binding = ItemIconDownloadBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new IconViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CollectionViewHolder) {
            ((CollectionViewHolder) holder).bind((CollectionItem) differ.getCurrentList().get(position));
        } else {
            ((IconViewHolder) holder).bind((DownloadRecord) differ.getCurrentList().get(position));
        }
    }

    @Override
    public long getItemId(int position) {
        Object item = differ.getCurrentList().get(position);
        if (item instanceof CollectionItem) {
            return ((CollectionItem) item).batchId.hashCode();
        }
        DownloadRecord record = (DownloadRecord) item;
        return (String.valueOf(record.getBatchId()) + '|' + record.getIconId()).hashCode();
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    static class CollectionItem {
        String batchId;
        List<DownloadRecord> records;
        String contentSignature;
        CollectionItem(String batchId, List<DownloadRecord> records) {
            this.batchId = batchId;
            this.records = records;
            StringBuilder signature = new StringBuilder();
            for (DownloadRecord record : records) {
                signature.append(record.getIconId()).append(':')
                        .append(record.getStatus()).append(':')
                        .append(record.getProgress()).append(':')
                        .append(record.getDownloadedBytes()).append(';');
            }
            this.contentSignature = signature.toString();
        }
    }

    class CollectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemCollectionDownloadBinding binding;
        CollectionViewHolder(ItemCollectionDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        void bind(CollectionItem item) {
            DownloadRecord first = item.records.get(0);
            binding.tvCollectionName.setText(first.getCategoryName());
            
            int total = item.records.size();
            int success = 0;
            int failed = 0;
            long totalBytes = 0;
            for (DownloadRecord r : item.records) {
                if (DownloadRecord.STATUS_SUCCESS.equals(r.getStatus())) success++;
                else if (DownloadRecord.STATUS_FAILED.equals(r.getStatus())) failed++;
                totalBytes += r.getTotalBytes();
            }
            
            int progress = (success * 100) / total;
            binding.tvCollectionInfo.setText("共 " + total + " 枚 | " + formatSize(totalBytes));
            binding.progressCollection.setProgress(progress);
            
            String status = "正在下载";
            if (success == total) status = "已完成";
            else if (failed > 0 && (success + failed) == total) status = "下载结束 (含失败)";
            binding.tvStatus.setText(status);
            
            binding.tvPath.setText("路径: " + first.getLocalPath());
            binding.btnRetryCollection.setVisibility(failed > 0 ? View.VISIBLE : View.GONE);
            binding.btnRetryCollection.setOnClickListener(v -> {
                if (listener != null) listener.onRetryCollection(item.batchId);
            });
            
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onOpenFolder(first.getCategoryName(), first.getTreeUri());
            });
        }
    }

    class IconViewHolder extends RecyclerView.ViewHolder {
        private final ItemIconDownloadBinding binding;
        IconViewHolder(ItemIconDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        void bind(DownloadRecord record) {
            binding.tvIconName.setText(record.getName());
            binding.tvIconStatus.setText(record.getStatus());
            
            if (DownloadRecord.STATUS_DOWNLOADING.equals(record.getStatus())) {
                binding.progressIcon.setVisibility(View.VISIBLE);
                binding.progressIcon.setProgress(record.getProgress());
            } else {
                binding.progressIcon.setVisibility(View.GONE);
            }
            
            if (DownloadRecord.STATUS_SUCCESS.equals(record.getStatus())) {
                binding.ivStatusIcon.setVisibility(View.VISIBLE);
                binding.ivStatusIcon.setImageResource(R.drawable.ic_accept);
                binding.ivStatusIcon.setBackgroundResource(R.drawable.bg_circle_success);
            } else if (DownloadRecord.STATUS_FAILED.equals(record.getStatus())) {
                binding.ivStatusIcon.setVisibility(View.VISIBLE);
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cancel);
                binding.ivStatusIcon.setBackgroundResource(R.drawable.bg_circle_red);
            } else {
                binding.ivStatusIcon.setVisibility(View.GONE);
            }
            
            binding.btnRetry.setVisibility(DownloadRecord.STATUS_FAILED.equals(record.getStatus()) ? View.VISIBLE : View.GONE);
            binding.btnRetry.setOnClickListener(v -> {
                if (listener != null) listener.onRetryIcon(record.getIconId());
            });

            Glide.with(binding.ivIcon.getContext())
                    .load(record.getThumbUrl())
                    .placeholder(R.drawable.ic_placeholder_camera)
                    .dontAnimate()
                    .override(96, 96)
                    .into(binding.ivIcon);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
