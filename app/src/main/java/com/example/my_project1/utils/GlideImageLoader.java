package com.example.my_project1.utils;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.example.my_project1.R;

/**
 * GlideImageLoader - 统一图片加载工具
 * ✅ 优化配置
 * ✅ 性能提升
 * ✅ 统一管理
 */
public class GlideImageLoader {

    // 🔑 通用配置
    private static final RequestOptions DEFAULT_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)  // 缓存所有版本
            .placeholder(android.R.color.darker_gray)  // 占位符
            .error(android.R.drawable.ic_menu_report_image)  // 错误图
            .centerCrop()
            .dontAnimate();  // 禁用动画，提升性能

    // 🔑 缩略图配置（用于列表）
    private static final RequestOptions THUMBNAIL_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(android.R.color.darker_gray)
            .error(android.R.drawable.ic_menu_report_image)
            .centerCrop()
            .dontAnimate()
            .override(300, 300);  // 限制尺寸

    /**
     * 加载图片（默认配置）
     */
    public static void load(Context context, String url, ImageView imageView) {
        Glide.with(context.getApplicationContext())
                .load(url)
                .apply(DEFAULT_OPTIONS)
                .into(imageView);
    }
    public static void load1(Context context, ImageView imageView, int drawableResId) {
        // 🔹 如果传入的 drawableResId <= 0，则使用默认图标
        int finalResId = (drawableResId > 0 ? drawableResId : R.drawable.ic_category_default);

        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.color.transparent)
                .error(android.R.color.transparent)
                .centerCrop()
                .dontAnimate();

        Glide.with(context.getApplicationContext())
                .load(finalResId)
                .apply(options)
                .into(imageView);
    }

//    /**
//     * 加载缩略图（用于列表场景）
//     */
//    public static void loadThumbnail(Context context, String url, ImageView imageView) {
//        Glide.with(context.getApplicationContext())
//                .load(url)
//                .apply(THUMBNAIL_OPTIONS)
//                .thumbnail(0.1f)  // 10% 缩略图
//                .into(imageView);
//    }

    /**
     * 加载图片（自定义占位符）
     */
    public static void load(Context context, String url, ImageView imageView,
                            int placeholder, int error) {
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(placeholder)
                .error(error)
                .centerCrop()
                .dontAnimate();

        Glide.with(context.getApplicationContext())
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 预加载图片（提前缓存）
     */
    public static void preload(Context context, String url) {
        Glide.with(context.getApplicationContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload();
    }

    /**
     * 清除内存缓存（在内存紧张时调用）
     */
    public static void clearMemory(Context context) {
        Glide.get(context).clearMemory();
    }

    /**
     * 清除磁盘缓存（需在后台线程调用）
     */
    public static void clearDiskCache(Context context) {
        new Thread(() -> Glide.get(context).clearDiskCache()).start();
    }

    /**
     * 加载缩略图（带淡入动画，用于首次展示）
     * 原有代码保持不变，此处仅占位提示。
     */
    public static void loadThumbnail(Context context, String url, ImageView imageView) {
        // 保留你原有的实现，不改动
        Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imageView);
    }

    // ─────────────────────────────────────────────────────────
    // ★ 新增方法：关闭动画，用于多选/频繁刷新场景
    // ─────────────────────────────────────────────────────────

    /**
     * 加载缩略图（无淡入动画）
     *
     * 关键点：
     * ① .dontAnimate() 关闭 Glide 默认的 CrossFade 动画
     * ② 命中缓存时直接展示，无任何过渡效果
     * ③ 占位图与错误图使用同一个资源，避免占位图切换时的跳变
     *
     * 适用场景：
     * - RecyclerView 快速滑动时 ViewHolder 复用
     * - 多选模式下 payload 刷新触发的 onBindViewHolder
     * - DiffUtil submitList 后的局部刷新
     *
     * @param context   Context
     * @param url       图标缩略图 URL（OSS + ?x-oss-process=image/resize,w_100）
     * @param imageView 目标 ImageView
     */
    public static void loadThumbnailNoAnim(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        Glide.with(context)
                .load(url)
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)       // 磁盘缓存全开，减少网络请求
                        .placeholder(android.R.drawable.ic_menu_report_image)    // 占位图（浅灰圆形）
                        .error(android.R.drawable.ic_menu_report_image)          // 加载失败兜底
                        .dontAnimate()                                   // ★ 关闭淡入动画
                        .override(100, 100)                             // 固定解码尺寸，避免内存抖动
                )
                .into(imageView);
    }


    private static final RequestOptions ICON_OPTIONS = new RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(100, 100);

    /** 缩略图选项：ARGB_8888（九宫格需要更高质量），含淡入 */
    private static final RequestOptions THUMB_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(100, 100)
            .placeholder(android.R.color.darker_gray);

    public static void loadIconRgb565(Context context, String url, ImageView target) {
        if (context == null || target == null) return;
        Glide.with(context)
                .asBitmap()
                .load(url)
                .apply(ICON_OPTIONS)
                .transition(BitmapTransitionOptions.withCrossFade(0)) // 彻底无动画
                .into(target);
    }
}