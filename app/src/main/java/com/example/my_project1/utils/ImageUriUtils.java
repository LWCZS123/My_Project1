package com.example.my_project1.utils;

import android.content.Context;
import android.net.Uri;

/**
 * 用于将本地 PNG 图标转换为 URI（方便存入 Room 或上传 Bmob）
 */
public class ImageUriUtils {

    /**
     * 将 drawable 资源 ID 转换为 URI 格式：
     * 例如：android.resource://com.example.my_project1/drawable/ic_food
     */
    public static Uri getResourceUri(Context context, int resId) {
        String resName = context.getResources().getResourceEntryName(resId);
        return Uri.parse("android.resource://" + context.getPackageName() + "/drawable/" + resName);    }

    /**
     * 将 URI 转换回可用的资源 ID（如果是本地资源）
     */
    public static int getResIdFromUri(Context context, String uriString) {
        if (uriString != null && uriString.startsWith("android.resource://")) {
            String resName = uriString.substring(uriString.lastIndexOf("/") + 1);
            return context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
        }
        return 0;
    }
}
