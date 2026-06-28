package com.example.my_project1.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.my_project1.R;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * RelativeLayout 背景图加载工具类（基于 Glide）
 * -------------------------------------------------------
 * 🚀 功能：
 * 1. 支持网络 / 本地背景图
 * 2. 支持占位图 & 错误图
 * 3. 支持高 / 普通优先级
 * 4. 安全设置背景（避免内存泄漏）
 *
 * 适用场景：
 * - 个人中心背景
 * - 卡片背景
 * - 页面头图
 */
public class ImageBackgroundUtils {

    private static final String TAG = "ImageBackgroundUtils";

    /**
     * 普通加载背景（默认占位图）
     */
    public static void load(Context context, String url, RelativeLayout layout) {
        load(context, url, layout,
                R.drawable.ic_placeholder,
                R.drawable.ic_load_error,
                Priority.NORMAL);
    }

    /**
     * 🚀 高优先级加载背景（用于首页、个人页头图）
     */
    public static void loadHighPriority(Context context, String url,
                                        RelativeLayout layout) {
        load(context, url, layout,
                R.drawable.ic_placeholder,
                R.drawable.ic_load_error,
                Priority.HIGH);
    }

    /**
     * 自定义背景加载（核心方法）
     */
    public static void load(Context context,
                            String url,
                            RelativeLayout layout,
                            int placeholder,
                            int errorDrawable,
                            Priority priority) {

        if (context == null || layout == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(placeholder)
                .error(errorDrawable)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(priority)
                .centerCrop();

        Glide.with(context.getApplicationContext())
                .load(url)
                .apply(options)
                .into(new CustomTarget<Drawable>() {

                    @Override
                    public void onResourceReady(
                            @NonNull Drawable resource,
                            @Nullable Transition<? super Drawable> transition) {

                        layout.setBackground(resource);
                        Log.d(TAG, "✅ 背景加载成功");
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // View 被回收时设置占位，防止背景丢失
                        layout.setBackground(placeholder);
                        Log.d(TAG, "🧹 背景被清除");
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        layout.setBackground(errorDrawable);
                        Log.e(TAG, "❌ 背景加载失败");
                    }
                });
    }

    /**
     * 🚀 强制刷新背景（跳过缓存）
     * 适用场景：背景图更新后立即刷新
     */
    public static void loadFresh(Context context,
                                 String url,
                                 RelativeLayout layout) {

        if (context == null || layout == null) return;

        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop();

        Glide.with(context.getApplicationContext())
                .load(url + "?t=" + System.currentTimeMillis())
                .apply(options)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(
                            @NonNull Drawable resource,
                            @Nullable Transition<? super Drawable> transition) {
                        layout.setBackground(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        layout.setBackground(placeholder);
                    }
                });
    }

    /**
     * 清除背景（释放引用）
     * 建议在 Fragment onDestroyView 调用
     */
    public static void clear(RelativeLayout layout) {
        if (layout != null) {
            layout.setBackground(null);
            Log.d(TAG, "🧹 背景已清除");
        }
    }
}
