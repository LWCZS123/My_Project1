package com.example.my_project1.utils;

import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class BottomSheetNavigationBarUtils {

    private static final String BACKGROUND_TAG = "white_navigation_bar_background";

    private BottomSheetNavigationBarUtils() {
    }

    public static void setup(@NonNull BottomSheetDialog dialog) {
        addWhiteBackground(dialog);
        applyLightAppearance(dialog.getWindow());
    }

    public static void applyLightAppearance(@Nullable Window window) {
        if (window == null) return;

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                decorView.getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
        if (controller != null) {
            controller.setAppearanceLightNavigationBars(true);
        }
    }

    private static void addWhiteBackground(@NonNull BottomSheetDialog dialog) {
        FrameLayout windowContent = dialog.findViewById(android.R.id.content);
        if (windowContent == null || windowContent.findViewWithTag(BACKGROUND_TAG) != null) return;

        View background = new View(dialog.getContext());
        background.setTag(BACKGROUND_TAG);
        background.setBackgroundColor(Color.WHITE);
        background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        int fallbackHeight = Math.round(24 * dialog.getContext().getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                fallbackHeight,
                Gravity.BOTTOM
        );
        windowContent.addView(background, params);

        windowContent.post(() -> {
            WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(windowContent);
            if (windowInsets == null) return;

            Insets navigationInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            if (navigationInsets.bottom <= 0) return;

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) background.getLayoutParams();
            layoutParams.height = navigationInsets.bottom;
            background.setLayoutParams(layoutParams);
            background.bringToFront();
        });
    }
}
