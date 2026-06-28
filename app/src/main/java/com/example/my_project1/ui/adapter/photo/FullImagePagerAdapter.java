package com.example.my_project1.ui.adapter.photo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * FullImagePagerAdapter - 全屏图片查看适配器
 * -------------------------------------------------------
 * ✅ 使用PhotoView支持缩放手势
 * ✅ 点击图片切换工具栏显示/隐藏
 * ✅ 加载高清原图
 */
public class FullImagePagerAdapter extends RecyclerView.Adapter<FullImagePagerAdapter.ImageViewHolder> {

    private final Context context;
    private final List<String> imageUrls;
    private final OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick();
    }

    public FullImagePagerAdapter(Context context, List<String> imageUrls) {
        this(context, imageUrls, null);
    }

    public FullImagePagerAdapter(Context context, List<String> imageUrls, OnImageClickListener listener) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_full_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);

        // 加载高清原图
        ImageLoaderUtils.loadFullImage(context, imageUrl, holder.photoView);

        // 设置点击事件 - 切换工具栏显示
        holder.photoView.setOnPhotoTapListener((view, x, y) -> {
            if (listener != null) {
                listener.onImageClick();
            }
        });

        // 同时保留普通点击事件作为备用
        holder.photoView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onImageClick();
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photo_view);
        }
    }
}