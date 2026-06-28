package com.example.my_project1.ui.adapter.photo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * ImagePagerAdapter - 图片轮播适配器
 * -------------------------------------------------------
 * 用于ViewPager2显示图片列表
 * 支持点击事件
 */
public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {

    private final Context context;
    private final List<String> imageUrls;
    private final OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick(int position);
    }

    public ImagePagerAdapter(Context context, List<String> imageUrls, OnImageClickListener listener) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image_pager, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);

        // 使用ImageLoaderUtils加载图片
        ImageLoaderUtils.loadBillImage(context, imageUrl, holder.imageView);

        // 设置点击事件
        holder.imageView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onImageClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_image);
        }
    }
}