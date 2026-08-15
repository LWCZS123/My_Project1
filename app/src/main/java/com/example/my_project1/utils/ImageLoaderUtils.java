package com.example.my_project1.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.FutureTarget;
import com.example.my_project1.R;

/**
 * 通用图片加载工具类（基于 Glide）- 增强版
 * -------------------------------------------------------
 * 🚀 新增优化:
 * 1. 分级加载：高优先级（图标）、普通优先级（图片）
 * 2. 缩略图支持：先显示缩略图，再加载完整图
 * 3. 预加载优化：区分高低优先级
 * 4. 性能监控：加载时间日志
 *
 * 原有功能保持不变:
 * - 自动缓存（内存 + 磁盘）
 * - 支持强制刷新（跳过缓存）
 * - 支持清理缓存
 * - 防止内存泄漏
 */
public class ImageLoaderUtils {

    private static final String TAG = "ImageLoaderUtils";

    /**
     * 普通加载（带缓存）- 原有方法保持不变
     */
    public static void load(Context context, String url, ImageView imageView) {
        load(context, url, imageView, R.drawable.ic_placeholder,
                R.drawable.ic_load_error);
    }

    /**
     * 自定义占位图和错误图加载 - 原有方法保持不变
     */
    public static void load(Context context, String url, ImageView imageView,
                            int placeholder, int errorImage) {

        if (context == null || imageView == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(placeholder)
                .error(errorImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .dontAnimate(); // 🔑 统一关闭动画，防止UI图标闪烁

        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 🚀 新增：加载缩略图（用于列表中的小图标）
     * 优化：移除 thumbnail(0.1f) 因为对于小图它会造成二次闪烁；增加 dontAnimate()。
     */
    public static void loadThumbnail(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_load_error)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(Priority.HIGH)
                .dontAnimate() // 🔑 关闭动画，防止闪烁
                .centerCrop();

        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 首页账单分类图标。
     *
     * 列表重绑时，Glide 的 placeholder 会立即覆盖磁盘缓存中的真实图标，造成
     * "占位图 -> 真实图标" 的可见闪烁。首次绑定保留布局现有 drawable，复用到
     * 另一条账单时才切换为默认分类图标，避免短暂显示上一条账单的图标。
     */
    public static void loadHomeBillCategoryIcon(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        String normalizedUrl = url == null ? "" : url;
        Object previousUrl = imageView.getTag(R.id.home_bill_category_icon_url);
        if (normalizedUrl.equals(previousUrl)) return;

        imageView.setTag(R.id.home_bill_category_icon_url, normalizedUrl);
        if (previousUrl != null) {
            imageView.setImageResource(R.drawable.ic_default_category);
        }

        if (normalizedUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_default_category);
            return;
        }

        int iconSize = dpToPx(context, 44);
        RequestOptions options = new RequestOptions()
                .error(R.drawable.ic_default_category)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(Priority.IMMEDIATE)
                .dontAnimate()
                .centerInside()
                .override(iconSize, iconSize);

        Glide.with(imageView)
                .load(getGlideSource(context, normalizedUrl))
                .apply(options)
                .into(imageView);
    }

    /** 异步预加载首页首屏图标，参数与 ImageView 请求完全一致。 */
    public static void preloadHomeBillCategoryIcons(Context context, java.util.Collection<String> urls) {
        if (context == null || urls == null || urls.isEmpty()) return;
        int iconSize = dpToPx(context, 44);
        for (String url : firstUniqueUrls(urls, 12)) {
            Glide.with(context.getApplicationContext())
                    .load(getGlideSource(context, url))
                    .apply(new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .priority(Priority.IMMEDIATE)
                            .dontAnimate()
                            .centerInside()
                            .override(iconSize, iconSize))
                    .preload(iconSize, iconSize);
        }
    }

    /**
     * 启动页后台线程专用：从 Glide 磁盘缓存解码首屏图标到内存缓存。
     * onlyRetrieveFromCache 保证不会等待网络。
     */
    public static void warmHomeBillCategoryIconsFromDisk(Context context,
                                                          java.util.Collection<String> urls) {
        if (context == null || urls == null || urls.isEmpty()) return;
        int iconSize = dpToPx(context, 44);
        for (String url : firstUniqueUrls(urls, 12)) {
            FutureTarget<Drawable> target = Glide.with(context.getApplicationContext())
                    .asDrawable()
                    .load(getGlideSource(context, url))
                    .apply(new RequestOptions()
                            .onlyRetrieveFromCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .priority(Priority.IMMEDIATE)
                            .dontAnimate()
                            .centerInside()
                            .override(iconSize, iconSize))
                    .submit(iconSize, iconSize);
            try {
                target.get();
            } catch (Exception ignored) {
                // 未缓存的图标由首页正常的异步请求处理。
            } finally {
                Glide.with(context.getApplicationContext()).clear(target);
            }
        }
    }

    private static java.util.Set<String> firstUniqueUrls(java.util.Collection<String> urls, int limit) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            result.add(url);
            if (result.size() == limit) break;
        }
        return result;
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 判断字符串是否为Uri格式（resource/content/file等）
     */
    private static boolean isUriString(String str) {
        return str.startsWith("android.resource://")
                || str.startsWith("content://")
                || str.startsWith("file://");
    }

    /**
     * 🚀 新增：加载账单图片（用于账单列表）
     * 适用场景：账单附带的照片
     * 优化：普通优先级，带缩略图
     */
    public static void loadBillImage(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_load_error)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(Priority.NORMAL)  // 普通优先级
                .centerCrop();

        Glide.with(context.getApplicationContext())
                .load(url)
                .apply(options)
                .thumbnail(0.1f)
                .into(imageView);
    }

    /**
     * 🚀 新增：加载大图（用于查看大图）
     * 适用场景：点击查看原图
     */
    public static void loadFullImage(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_load_error)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
                .fitCenter();

        Glide.with(context.getApplicationContext())
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 强制刷新加载（绕过缓存）- 原有方法保持不变
     * 适合：图标更新、头像更新后立即刷新显示
     */
    public static void loadFresh(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_load_error)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true);

        Glide.with(context.getApplicationContext())
                .load(url + "?timestamp=" + System.currentTimeMillis()) // 防止命中缓存
                .apply(options)
                .into(imageView);
    }

    /**
     * 清理内存缓存（主线程）- 原有方法保持不变
     */
    public static void clearMemoryCache(Context context) {
        if (context != null) {
            Glide.get(context.getApplicationContext()).clearMemory();
            Log.d(TAG, "🧹 清除Glide内存缓存");
        }
    }

    /**
     * 清理磁盘缓存（需在子线程调用）- 原有方法保持不变
     */
    public static void clearDiskCache(Context context) {
        if (context != null) {
            AppExecutors.get().diskIO().execute(() -> {
                Glide.get(context.getApplicationContext()).clearDiskCache();
                Log.d(TAG, "🧹 清除Glide磁盘缓存");
            });
        }
    }

    /**
     * 预加载图片到缓存中 - 原有方法保持不变
     * 可提前预加载官方图标以节省外网请求
     */
    public static void preload(Context context, String url) {
        if (context != null && url != null) {
            Glide.with(context.getApplicationContext())
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload();
        }
    }

    /**
     * 🚀 新增：高优先级预加载（用于关键图标）
     * 适用场景：启动时预加载分类图标
     */
    public static void preloadHighPriority(Context context, String url) {
        if (context == null || url == null) return;

        Glide.with(context.getApplicationContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
                .preload();
    }

    /**
     * 🚀 新增：低优先级预加载（用于次要图片）
     * 适用场景：后台预加载账单图片
     */
    public static void preloadLowPriority(Context context, String url) {
        if (context == null || url == null) return;

        Glide.with(context.getApplicationContext())
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.LOW)
                .preload();
    }

    /**
     * 🚀 新增：批量预加载图片
     * @param highPriority true=高优先级，false=低优先级
     */
    public static void preloadBatch(Context context, java.util.List<String> urls,
                                    boolean highPriority) {
        if (context == null || urls == null || urls.isEmpty()) return;

        Log.d(TAG, "🚀 批量预加载 " + urls.size() + " 张图片 (优先级:" +
                (highPriority ? "HIGH" : "LOW") + ")");

        for (String url : urls) {
            if (highPriority) {
                preloadHighPriority(context, url);
            } else {
                preloadLowPriority(context, url);
            }
        }
    }

    /**
     * 自动识别 URI 来源 - 原有方法保持不变
     * - 网络 URL（http/https）
     * - 本地资源 URI（android.resource://...）
     * - 其他：返回默认图标
     */
    public static Object getGlideSource(Context context, String uri) {
        if (uri == null) {
            return R.drawable.ic_default_category;
        }

        if (uri.startsWith("http") || uri.startsWith("https")) {
            // ✅ 网络图片
            return uri;
        } else if (uri.startsWith("android.resource://")) {
            // ✅ 系统资源 URI —— 一定要返回 Uri 对象！
            return Uri.parse(uri);
        } else {
            // ✅ 默认占位图
            return R.drawable.ic_default_category;
        }
    }

    /**
     *  新增：获取缓存大小（调试用）
     */
    public static void getCacheSize(Context context) {
        if (context == null) return;

        AppExecutors.get().diskIO().execute(() -> {
            try {
                long size = Glide.getPhotoCacheDir(context.getApplicationContext()).length();
                long sizeInMB = size / (1024 * 1024);
                Log.d(TAG, "📊 Glide磁盘缓存大小: " + sizeInMB + "MB");
            } catch (Exception e) {
                Log.e(TAG, "获取缓存大小失败", e);
            }
        });
    }

    /**
     * 🚀 新增：加载用户头像（普通缓存）
     * 适用场景：个人资料页、列表头像
     */
    public static void loadAvatar(Context context, String url,
                                  de.hdodenhof.circleimageview.CircleImageView imageView) {

        if (context == null || imageView == null) return;

        int avatarSize = dpToPx(context, 32);
        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(Priority.HIGH)   // 👈 头像优先级高
                .centerCrop()
                .override(avatarSize, avatarSize);

        Glide.with(context.getApplicationContext())
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /** 启动页后台线程专用：把已缓存头像按首页尺寸恢复到内存缓存。 */
    public static void warmAvatarFromDisk(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        int avatarSize = dpToPx(context, 32);
        FutureTarget<Drawable> target = Glide.with(context.getApplicationContext())
                .asDrawable()
                .load(url)
                .apply(new RequestOptions()
                        .onlyRetrieveFromCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .priority(Priority.IMMEDIATE)
                        .centerCrop()
                        .override(avatarSize, avatarSize))
                .submit(avatarSize, avatarSize);
        try {
            target.get();
        } catch (Exception ignored) {
            // 未缓存头像继续由页面正常加载。
        } finally {
            Glide.with(context.getApplicationContext()).clear(target);
        }
    }


    // ==================== 便捷方法（向后兼容）====================

    /**
     * 🚀 新增：加载分类图标（优化版）
     * 1. dontAnimate(): 彻底关闭渐变动画，防止由于"占位图->目标图"切换导致的闪烁。
     * 2. diskCacheStrategy(ALL): 确保对于已下载图标能瞬间从磁盘/内存返回。
     * 3. 去掉 thumbnail(0.1f): 对于列表小图标，分级加载反而会造成肉眼可见的二次刷新（闪烁）。
     */
    public static void loadCategoryIcon(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_default_category)
                .error(R.drawable.ic_default_category)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .priority(Priority.HIGH)
                .dontAnimate() // 🔑 核心：关闭动画，防止闪烁
                .centerCrop();

        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }

    /**
     * 加载账户图标（便捷方法）
     */
    public static void loadAccountIcon(Context context, String iconUrl, ImageView imageView) {
        loadThumbnail(context, iconUrl, imageView);
    }
}
