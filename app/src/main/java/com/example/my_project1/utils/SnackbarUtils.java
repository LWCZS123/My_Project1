package com.example.my_project1.utils;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.LayoutCustomSnackbarBinding;
import com.google.android.material.snackbar.Snackbar;

public class SnackbarUtils {

    public enum Type {
        SUCCESS, ERROR, WARNING, INFO
    }

    public static void show(View view, String message) {
        show(view, message, Type.INFO, 500);
    }

    public static void show(View view, String message, Type type) {
        show(view, message, type, 500);
    }

    public static void show(View view, String message, Type type, int duration) {
        if (view == null || message == null || message.isEmpty()) return;

        Snackbar snackbar = Snackbar.make(view, "", duration);
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();

        TextView text = layout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (text != null) text.setVisibility(View.INVISIBLE);

        LayoutCustomSnackbarBinding binding = LayoutCustomSnackbarBinding.inflate(
                LayoutInflater.from(view.getContext()), null, false);

        binding.tvSnackbarMessage.setText(message);

        int icon;
        int color;

        switch (type) {
            case SUCCESS:
                icon = R.drawable.ic_snackbar_success;
                color = view.getContext().getColor(R.color.snackbar_success);
                break;
            case ERROR:
                icon = R.drawable.ic_snackbar_error;
                color = view.getContext().getColor(R.color.snackbar_error);
                break;
            case WARNING:
                icon = R.drawable.ic_snackbar_warning;
                color = view.getContext().getColor(R.color.snackbar_warning);
                break;
            default:
                icon = R.drawable.ic_snackbar_info;
                color = view.getContext().getColor(R.color.snackbar_info);
                break;
        }

        binding.ivSnackbarIcon.setImageResource(icon);

        GradientDrawable bg = (GradientDrawable) binding.getRoot().getBackground();
        bg.setColor(color);

        layout.setBackground(null);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);

        // 🔑 使用 padding 代替 margin 来向下偏移，因为 Snackbar 内部逻辑经常会重置 margin
        int topOffset = DisplayUtils.dp2px(view.getContext(), 40);
        layout.setPadding(0, topOffset, 0, 0);

        // 🔑 将 Snackbar 整体移动到顶部
        android.view.ViewGroup.LayoutParams lp = layout.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            flp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        } else if (lp != null && lp.getClass().getName().contains("CoordinatorLayout$LayoutParams")) {
            try {
                java.lang.reflect.Field gravityField = lp.getClass().getField("gravity");
                gravityField.set(lp, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            } catch (Exception ignored) {}
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, 0);

        layout.addView(binding.getRoot(), 0, params);

        snackbar.show();
    }

    public static void showSuccess(View v, String m) { show(v, m, Type.SUCCESS); }
    public static void showError(View v, String m)   { show(v, m, Type.ERROR); }
    public static void showWarning(View v, String m) { show(v, m, Type.WARNING); }
    public static void showInfo(View v, String m)    { show(v, m, Type.INFO); }
}
