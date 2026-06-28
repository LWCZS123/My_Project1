package com.example.my_project1.ui.adapter.photo;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.my_project1.databinding.ItemPhotoBinding;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * PhotoAdapterForUpload - 上传场景专用
 * ✅ 处理本地 Uri（相册选择/拍照）
 * ✅ 显示删除按钮
 * ✅ 用于添加账单时选择图片
 */
public class PhotoAdapterForUpload extends RecyclerView.Adapter<PhotoAdapterForUpload.PhotoViewHolder> {

    private final List<Uri> photoList;
    private final Context appContext;
    private final OnPhotoClickListener listener;

    public interface OnPhotoClickListener {
        void onPhotoClick(Uri uri, int position);
        void onDeleteClick(int position);
    }

    public PhotoAdapterForUpload(Context context, List<Uri> photoList, OnPhotoClickListener listener) {
        this.appContext = context.getApplicationContext();
        this.photoList = photoList != null ? photoList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

    public void setPhotos(List<Uri> uris) {
        photoList.clear();
        if (uris != null) {
            photoList.addAll(uris);
        }
        notifyDataSetChanged();
    }

    public void addPhoto(Uri uri) {
        photoList.add(uri);
        notifyItemInserted(photoList.size() - 1);
    }

    public void addPhotos(List<Uri> uris) {
        if (uris != null && !uris.isEmpty()) {
            int startPosition = photoList.size();
            photoList.addAll(uris);
            notifyItemRangeInserted(startPosition, uris.size());
        }
    }

    public void removePhoto(int position) {
        if (position >= 0 && position < photoList.size()) {
            photoList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public List<Uri> getPhotos() {
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

        void bind(Uri uri, int position) {
            // 加载本地 Uri
            Glide.with(appContext)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(binding.ivPhoto);

            // 显示删除按钮（上传场景需要删除功能）
            binding.ivDelete.setVisibility(View.VISIBLE);
            binding.ivDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(position);
                }
            });

            // 点击查看大图
            binding.ivPhoto.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPhotoClick(uri, position);
                }
            });
        }
    }
}