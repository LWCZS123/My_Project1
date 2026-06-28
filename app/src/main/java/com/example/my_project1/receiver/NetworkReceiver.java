package com.example.my_project1.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.content.SharedPreferences;

import com.example.my_project1.data.repository.SyncRepository;

/**
 * NetworkReceiver
 * ------------------------------------------------------
 * 监听网络变化，当网络恢复时触发同步。
 * 通过 WorkManager 执行后台任务，防止主线程阻塞。
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";
    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    private static final long MIN_INTERVAL_MS = 60_000; // 最小同步间隔：1分钟

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "📴 网络断开，等待恢复后自动同步");
            return;
        }

        // 防止频繁触发
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
        long now = System.currentTimeMillis();

        if (now - lastSync < MIN_INTERVAL_MS) {
            Log.d(TAG, "⏳ 网络刚恢复，但距离上次同步不足 1 分钟，跳过本次自动同步");
            return;
        }

        // 记录本次触发时间
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply();

        // 触发后台同步任务
        Log.d(TAG, "🌐 网络已恢复，触发 WorkManager 同步任务");
        new SyncRepository(context).syncAll();
    }

    /**
     * 判断当前网络是否可用
     */
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}
