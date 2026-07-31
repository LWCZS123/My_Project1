package com.example.my_project1.ui.adapter.photo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemPhotoSmallBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * PhotoAdapter - 优化版（使用分级图片加载）
 * -------------------------------------------------------
 * 🚀 优化内容:
 * 1. 使用 ImageLoaderUtils.loadBillImage() 加载图片（带缩略图）
 * 2. 使用 ApplicationContext 避免内存泄漏
 * 3. 先显示缩略图，再加载完整图
 */
public class PhotoAdapter extends ListAdapter<String, PhotoAdapter.PhotoViewHolder> {

    private final Context appContext;
    private final OnPhotoClickListener listener;
    private final boolean showDelete;

    public interface OnPhotoClickListener {
        void onPhotoClick(String imageUrl, int position);
        void onDeleteClick(int position);
    }

    /**
     * 构造函数 - 接收URL字符串列表
     */
    public PhotoAdapter(Context context, List<String> photoList, boolean showDelete,
                        OnPhotoClickListener listener) {
        super(new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        });
        // 🔑 使用ApplicationContext避免内存泄漏
        this.appContext = context.getApplicationContext();
        this.showDelete = showDelete;
        this.listener = listener;
        if (photoList != null) {
            submitList(new ArrayList<>(photoList));
        }
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 🔑 使用parent.getContext()保留主题
        ItemPhotoSmallBinding binding = ItemPhotoSmallBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PhotoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        holder.bind(getItem(position), position);
    }

    public void setPhotos(List<String> urls) {
        submitList(urls != null ? new ArrayList<>(urls) : null);
    }

    public void addPhoto(String url) {
        List<String> currentList = new ArrayList<>(getCurrentList());
        currentList.add(url);
        submitList(currentList);
    }

    public void addPhotos(List<String> urls) {
        if (urls != null && !urls.isEmpty()) {
            List<String> currentList = new ArrayList<>(getCurrentList());
            currentList.addAll(urls);
            submitList(currentList);
        }
    }

    public void removePhoto(int position) {
        if (position >= 0 && position < getItemCount()) {
            List<String> currentList = new ArrayList<>(getCurrentList());
            currentList.remove(position);
            submitList(currentList);
        }
    }

    public List<String> getPhotos() {
        return new ArrayList<>(getCurrentList());
    }

    public void clear() {
        submitList(null);
    }

    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private final ItemPhotoSmallBinding binding;

        PhotoViewHolder(ItemPhotoSmallBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(String imageUrl, int position) {
            // 🚀 优化：使用 loadBillImage 加载账单图片（带缩略图 + 普通优先级）
            ImageLoaderUtils.loadBillImage(appContext, imageUrl, binding.ivPhoto);

            // 删除按钮
            if (showDelete) {
                binding.ivDelete.setVisibility(View.VISIBLE);
                binding.ivDelete.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDeleteClick(position);
                    }
                });
            } else {
                binding.ivDelete.setVisibility(View.GONE);
            }

            // 点击查看大图
            binding.ivPhoto.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPhotoClick(imageUrl, position);
                }
            });
        }
    }
}
