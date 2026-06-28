package com.example.my_project1.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * 网络工具类
 * 用于检测网络连接状态
 */
public class NetworkUtil {

    /**
     * 检查网络是否可用
     * @param context 上下文
     * @return true表示网络可用，false表示不可用
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0及以上版本
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);

            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            // Android 6.0以下版本
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    /**
     * 检查是否连接WiFi
     */
    public static boolean isWifiConnected(Context context) {
        if (context == null) {
            return false;
        }

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);

            return capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null &&
                    networkInfo.getType() == ConnectivityManager.TYPE_WIFI &&
                    networkInfo.isConnected();
        }
    }

    /**
     * 检查是否连接移动数据
     */
    public static boolean isMobileConnected(Context context) {
        if (context == null) {
            return false;
        }

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);

            return capabilities != null &&
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null &&
                    networkInfo.getType() == ConnectivityManager.TYPE_MOBILE &&
                    networkInfo.isConnected();
        }
    }

    /**
     * 获取网络类型名称
     */
    public static String getNetworkTypeName(Context context) {
        if (!isNetworkAvailable(context)) {
            return "无网络";
        }

        if (isWifiConnected(context)) {
            return "WiFi";
        } else if (isMobileConnected(context)) {
            return "移动数据";
        } else {
            return "其他网络";
        }
    }
}