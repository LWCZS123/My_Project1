package com.example.my_project1.ui.adapter.photo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemPhotoBinding;
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
public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private final List<String> photoList;  // URL字符串列表
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
        // 🔑 使用ApplicationContext避免内存泄漏
        this.appContext = context.getApplicationContext();
        this.photoList = photoList != null ? photoList : new ArrayList<>();
        this.showDelete = showDelete;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 🔑 使用parent.getContext()保留主题
        ItemPhotoBinding binding = ItemPhotoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PhotoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        holder.bind(photoList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return photoList.size();
    }

    public void setPhotos(List<String> urls) {
        photoList.clear();
        if (urls != null) {
            photoList.addAll(urls);
        }
        notifyDataSetChanged();
    }

    public void addPhoto(String url) {
        photoList.add(url);
        notifyItemInserted(photoList.size() - 1);
    }

    public void addPhotos(List<String> urls) {
        if (urls != null && !urls.isEmpty()) {
            int startPosition = photoList.size();
            photoList.addAll(urls);
            notifyItemRangeInserted(startPosition, urls.size());
        }
    }

    public void removePhoto(int position) {
        if (position >= 0 && position < photoList.size()) {
            photoList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public List<String> getPhotos() {
        return new ArrayList<>(photoList);
    }

    public void clear() {
        photoList.clear();
        notifyDataSetChanged();
    }

    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private final ItemPhotoBinding binding;

        PhotoViewHolder(ItemPhotoBinding binding) {
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