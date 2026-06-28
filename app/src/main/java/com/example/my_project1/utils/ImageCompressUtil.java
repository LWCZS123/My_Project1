package com.example.my_project1.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 智能图片压缩工具（优化版）
 *
 * 核心改进：
 * 1. 根据原图大小智能选择压缩策略
 * 2. 小图片直接上传，避免过度压缩
 * 3. 分级压缩策略，平衡质量和大小
 * 4. 保留 EXIF 方向信息
 * 5. 动态调整压缩参数
 */
public class ImageCompressUtil {

    private static final String TAG = "ImageCompressUtil";

    // 文件大小阈值
    private static final int SIZE_DIRECT_UPLOAD = 150 * 1024;     // 150KB 以下直接上传
    private static final int SIZE_LIGHT_COMPRESS = 1024 * 1024;   // 1MB 以下轻度压缩
    private static final int SIZE_HEAVY_COMPRESS = 5 * 1024 * 1024; // 5MB 以下重度压缩

    // 压缩目标大小
    private static final int TARGET_SIZE_SMALL = 200 * 1024;      // 小文件目标：200KB
    private static final int TARGET_SIZE_MEDIUM = 500 * 1024;     // 中文件目标：500KB
    private static final int TARGET_SIZE_LARGE = 800 * 1024;      // 大文件目标：800KB

    // 分辨率限制
    private static final int MAX_WIDTH_HIGH = 1920;               // 高清：1920px
    private static final int MAX_WIDTH_MEDIUM = 1280;             // 中清：1280px
    private static final int MAX_WIDTH_LOW = 720;                 // 低清：720px
    private static final int MIN_WIDTH = 480;                     // 最小宽度

    /**
     * 压缩图片（主入口 - 智能策略）
     * @param context 上下文
     * @param sourceUri 原图URI
     * @return 压缩后的文件（或原图临时拷贝）
     */
    public static File compressImage(Context context, Uri sourceUri) throws IOException {
        long startTime = System.currentTimeMillis();

        // 1. 获取原图信息
        long originalSize = getFileSize(context, sourceUri);
        int orientation = getOrientation(context, sourceUri);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri)) {
            BitmapFactory.decodeStream(is, null, options);
        }

        int originalWidth = options.outWidth;
        int originalHeight = options.outHeight;

        Log.d(TAG, String.format("原图信息: %dx%d, %.2fKB",
                originalWidth, originalHeight, originalSize / 1024.0));

        // 2. 根据文件大小选择压缩策略
        File resultFile;
        CompressionStrategy strategy = determineStrategy(originalSize, originalWidth, originalHeight);

        switch (strategy) {
            case DIRECT_UPLOAD:
                // 直接上传（复制到临时文件）
                resultFile = copyToTempFile(context, sourceUri);
                Log.d(TAG, "策略：直接上传（文件已足够小）");
                break;

            case LIGHT_COMPRESS:
                // 轻度压缩（保持高质量）
                resultFile = compressWithStrategy(context, sourceUri, orientation,
                        originalWidth, originalHeight, MAX_WIDTH_HIGH, TARGET_SIZE_MEDIUM, 85);
                Log.d(TAG, "策略：轻度压缩（保持高清）");
                break;

            case MEDIUM_COMPRESS:
                // 中度压缩（平衡质量）
                resultFile = compressWithStrategy(context, sourceUri, orientation,
                        originalWidth, originalHeight, MAX_WIDTH_MEDIUM, TARGET_SIZE_MEDIUM, 80);
                Log.d(TAG, "策略：中度压缩（平衡质量）");
                break;

            case HEAVY_COMPRESS:
                // 重度压缩（优先减小体积）
                resultFile = compressWithStrategy(context, sourceUri, orientation,
                        originalWidth, originalHeight, MAX_WIDTH_MEDIUM, TARGET_SIZE_LARGE, 75);
                Log.d(TAG, "策略：重度压缩（大幅减小体积）");
                break;

            case EXTREME_COMPRESS:
                // 极限压缩（超大图片）
                resultFile = compressWithStrategy(context, sourceUri, orientation,
                        originalWidth, originalHeight, MAX_WIDTH_LOW, TARGET_SIZE_LARGE, 70);
                Log.d(TAG, "策略：极限压缩（超大图片）");
                break;

            default:
                resultFile = compressWithStrategy(context, sourceUri, orientation,
                        originalWidth, originalHeight, MAX_WIDTH_MEDIUM, TARGET_SIZE_MEDIUM, 80);
                break;
        }

        long endTime = System.currentTimeMillis();
        long finalSize = resultFile.length();

        Log.d(TAG, String.format("压缩完成: %.2fKB → %.2fKB, 耗时%dms, 压缩率%.1f%%",
                originalSize / 1024.0, finalSize / 1024.0,
                endTime - startTime,
                (1 - finalSize * 1.0 / originalSize) * 100));

        return resultFile;
    }

    /**
     * 压缩策略枚举
     */
    private enum CompressionStrategy {
        DIRECT_UPLOAD,      // 直接上传
        LIGHT_COMPRESS,     // 轻度压缩
        MEDIUM_COMPRESS,    // 中度压缩
        HEAVY_COMPRESS,     // 重度压缩
        EXTREME_COMPRESS    // 极限压缩
    }

    /**
     * 确定压缩策略
     */
    private static CompressionStrategy determineStrategy(long fileSize, int width, int height) {
        // 1. 小于 150KB 直接上传
        if (fileSize < SIZE_DIRECT_UPLOAD) {
            return CompressionStrategy.DIRECT_UPLOAD;
        }

        // 2. 150KB ~ 1MB 轻度压缩
        if (fileSize < SIZE_LIGHT_COMPRESS) {
            // 但如果分辨率过高，仍需压缩
            if (width > MAX_WIDTH_HIGH || height > MAX_WIDTH_HIGH) {
                return CompressionStrategy.LIGHT_COMPRESS;
            }
            return CompressionStrategy.DIRECT_UPLOAD;
        }

        // 3. 1MB ~ 5MB 根据分辨率决定
        if (fileSize < SIZE_HEAVY_COMPRESS) {
            if (width > 2560 || height > 2560) {
                return CompressionStrategy.HEAVY_COMPRESS;
            }
            return CompressionStrategy.MEDIUM_COMPRESS;
        }

        // 4. 5MB 以上重度/极限压缩
        if (fileSize < 10 * 1024 * 1024) {
            return CompressionStrategy.HEAVY_COMPRESS;
        }

        return CompressionStrategy.EXTREME_COMPRESS;
    }

    /**
     * 执行压缩（带策略参数）
     */
    private static File compressWithStrategy(Context context, Uri sourceUri, int orientation,
                                             int originalWidth, int originalHeight,
                                             int maxResolution, int targetSize, int initialQuality)
            throws IOException {

        // 1. 计算目标尺寸
        int[] targetSize2D = calculateTargetSize(originalWidth, originalHeight, maxResolution);
        int targetWidth = targetSize2D[0];
        int targetHeight = targetSize2D[1];

        // 2. 计算采样率
        int inSampleSize = calculateInSampleSize(originalWidth, originalHeight,
                targetWidth, targetHeight);

        // 3. 采样解码
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // 减少内存占用

        Bitmap sampledBitmap;
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri)) {
            sampledBitmap = BitmapFactory.decodeStream(is, null, options);
        }

        if (sampledBitmap == null) {
            throw new IOException("无法解码图片");
        }

        try {
            // 4. 精确缩放
            Bitmap scaledBitmap = scaleBitmap(sampledBitmap, targetWidth, targetHeight);
            if (scaledBitmap != sampledBitmap) {
                sampledBitmap.recycle();
            }

            // 5. 修正方向
            Bitmap rotatedBitmap = rotateBitmap(scaledBitmap, orientation);
            if (rotatedBitmap != scaledBitmap) {
                scaledBitmap.recycle();
            }

            // 6. 质量压缩
            File compressedFile = compressToTargetSize(context, rotatedBitmap,
                    targetSize, initialQuality);

            rotatedBitmap.recycle();
            return compressedFile;

        } catch (Exception e) {
            if (sampledBitmap != null && !sampledBitmap.isRecycled()) {
                sampledBitmap.recycle();
            }
            throw e;
        }
    }

    /**
     * 计算目标尺寸（保持宽高比）
     */
    private static int[] calculateTargetSize(int width, int height, int maxResolution) {
        // 1. 如果图片已经很小，不放大
        if (width <= maxResolution && height <= maxResolution) {
            // 但确保最小边不低于 MIN_WIDTH
            int minSide = Math.min(width, height);
            if (minSide < MIN_WIDTH && minSide > 0) {
                float ratio = MIN_WIDTH * 1.0f / minSide;
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
            }
            return new int[]{width, height};
        }

        // 2. 按比例缩小
        float ratio = Math.min(
                maxResolution * 1.0f / width,
                maxResolution * 1.0f / height
        );

        int targetWidth = Math.round(width * ratio);
        int targetHeight = Math.round(height * ratio);

        // 3. 确保是偶数（某些编码器要求）
        targetWidth = targetWidth / 2 * 2;
        targetHeight = targetHeight / 2 * 2;

        return new int[]{targetWidth, targetHeight};
    }

    /**
     * 计算采样率
     */
    private static int calculateInSampleSize(int width, int height,
                                             int targetWidth, int targetHeight) {
        int inSampleSize = 1;

        if (height > targetHeight || width > targetWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= targetHeight
                    && (halfWidth / inSampleSize) >= targetWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * 精确缩放 Bitmap
     */
    private static Bitmap scaleBitmap(Bitmap source, int targetWidth, int targetHeight) {
        if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
            return source;
        }

        try {
            return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "缩放图片内存不足", e);
            return source;
        }
    }

    /**
     * 旋转图片（修正 EXIF 方向）
     */
    private static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == -1) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            default:
                return bitmap;
        }

        try {
            return Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "旋转图片内存不足", e);
            return bitmap;
        }
    }

    /**
     * 动态质量压缩（二分法快速逼近目标大小）
     */
    private static File compressToTargetSize(Context context, Bitmap bitmap,
                                             int targetSize, int initialQuality) throws IOException {
        File outputFile = new File(context.getCacheDir(),
                "compressed_" + System.currentTimeMillis() + ".jpg");

        // 第一次尝试
        int quality = initialQuality;
        byte[] data = compressToBytes(bitmap, quality);

        Log.d(TAG, String.format("初始压缩: 质量%d%%, 大小%.2fKB",
                quality, data.length / 1024.0));

        // 如果第一次就满足要求，直接返回
        int minAcceptableSize = (int) (targetSize * 0.7); // 允许比目标小 30%
        int maxAcceptableSize = (int) (targetSize * 1.3); // 允许比目标大 30%

        if (data.length >= minAcceptableSize && data.length <= maxAcceptableSize) {
            saveToFile(data, outputFile);
            return outputFile;
        }

        // 二分法调整质量
        int minQuality = 40;  // 最低质量
        int maxQuality = 95;  // 最高质量
        byte[] bestData = data;
        int bestQuality = quality;

        for (int i = 0; i < 7; i++) {  // 最多 7 次迭代
            if (data.length > maxAcceptableSize) {
                // 文件太大，降低质量
                maxQuality = quality - 1;
                quality = (minQuality + quality) / 2;
            } else if (data.length < minAcceptableSize) {
                // 文件太小，提高质量
                minQuality = quality + 1;
                quality = (quality + maxQuality) / 2;
            } else {
                // 在可接受范围内
                bestData = data;
                break;
            }

            if (minQuality >= maxQuality) {
                break;
            }

            data = compressToBytes(bitmap, quality);

            Log.d(TAG, String.format("迭代%d: 质量%d%%, 大小%.2fKB",
                    i + 1, quality, data.length / 1024.0));

            // 更新最佳结果（选择更接近目标的）
            int currentDiff = Math.abs(data.length - targetSize);
            int bestDiff = Math.abs(bestData.length - targetSize);

            if (currentDiff < bestDiff) {
                bestData = data;
                bestQuality = quality;
            }

            // 如果已经满足要求，提前退出
            if (data.length >= minAcceptableSize && data.length <= maxAcceptableSize) {
                bestData = data;
                break;
            }
        }

        Log.d(TAG, String.format("最终结果: 质量%d%%, 大小%.2fKB",
                bestQuality, bestData.length / 1024.0));

        saveToFile(bestData, outputFile);
        return outputFile;
    }

    /**
     * 压缩 Bitmap 到字节数组
     */
    private static byte[] compressToBytes(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] data = baos.toByteArray();

        try {
            baos.close();
        } catch (IOException e) {
            Log.e(TAG, "关闭流失败", e);
        }

        return data;
    }

    /**
     * 保存字节数组到文件
     */
    private static void saveToFile(byte[] data, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            fos.flush();
        }
    }

    /**
     * 复制文件到临时目录（不压缩）
     */
    private static File copyToTempFile(Context context, Uri uri) throws IOException {
        File tempFile = new File(context.getCacheDir(),
                "direct_upload_" + System.currentTimeMillis() + ".jpg");

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            if (is == null) {
                throw new IOException("无法打开输入流");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
        }

        return tempFile;
    }

    /**
     * 获取图片方向
     */
    private static int getOrientation(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }

            ExifInterface exif;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                exif = new ExifInterface(is);
                return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception e) {
            Log.e(TAG, "无法读取 EXIF", e);
        }
        return ExifInterface.ORIENTATION_NORMAL;
    }

    /**
     * 获取文件大小
     */
    private static long getFileSize(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return 0;
            }

            long size = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                size += bytesRead;
            }
            return size;
        } catch (Exception e) {
            Log.e(TAG, "无法获取文件大小", e);
            return 0;
        }
    }

    /**
     * 批量压缩图片
     */
    public static java.util.List<File> compressImages(Context context,
                                                      java.util.List<Uri> uris) {
        java.util.List<File> compressedFiles = new java.util.ArrayList<>();

        for (Uri uri : uris) {
            try {
                File compressed = compressImage(context, uri);
                compressedFiles.add(compressed);
            } catch (IOException e) {
                Log.e(TAG, "压缩失败: " + uri, e);
            }
        }

        return compressedFiles;
    }
}