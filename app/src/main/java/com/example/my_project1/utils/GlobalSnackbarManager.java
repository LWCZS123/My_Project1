package com.example.my_project1.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;

public class GlobalSnackbarManager {

    private static Activity currentActivity;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void register(Activity activity) {
        currentActivity = activity;
    }

    public static void unregister(Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    public static void showMessage(String message) {
        if (currentActivity != null) {
            mainHandler.post(() -> {
                View rootView = currentActivity.findViewById(android.R.id.content);
                Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
            });
        } else {
            // 没有活动窗口时输出日志
            System.out.println("[Snackbar] " + message);
        }
    }
}
