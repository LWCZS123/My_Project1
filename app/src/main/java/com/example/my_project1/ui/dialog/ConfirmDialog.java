package com.example.my_project1.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;

import com.example.my_project1.databinding.DialogDeleteConfirmBinding;

import io.reactivex.annotations.NonNull;

/**
 * 高性能、链式调用、可复用的确认弹窗
 * 优化：默认圆角、宽度85%、透明背景、居中显示
 */
public class ConfirmDialog {

    private final Dialog dialog;
    private final DialogDeleteConfirmBinding binding;

    public ConfirmDialog(@NonNull Context context) {
        // 使用 ViewBinding 加载布局
        binding = DialogDeleteConfirmBinding.inflate(LayoutInflater.from(context));

        // 创建 Dialog
        dialog = new Dialog(context);
        dialog.setContentView(binding.getRoot());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        // 设置圆角 + 宽度 + 居中 + 透明背景
        Window window = dialog.getWindow();
        if (window != null) {
            // 背景透明
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // 屏幕宽度85%
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.85);
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }

    /**
     * 设置标题
     */
    public ConfirmDialog setTitle(String title) {
        binding.tvTitle.setText(title);
        return this;
    }

    /**
     * 设置内容
     */
    public ConfirmDialog setMessage(String message) {
        binding.tvMessage.setText(message);
        return this;
    }

    /**
     * 设置确认按钮点击事件
     */
    public ConfirmDialog setConfirmListener(Runnable confirmAction) {
        binding.btnDelete.setOnClickListener(v -> {
            if (confirmAction != null) confirmAction.run();
            dialog.dismiss();
        });
        return this;
    }

    /**
     * 设置取消按钮点击事件
     */
    public ConfirmDialog setCancelListener(Runnable cancelAction) {
        binding.btnCancel.setOnClickListener(v -> {
            if (cancelAction != null) cancelAction.run();
            dialog.dismiss();
        });
        return this;
    }

    /**
     * 显示弹窗
     */
    public void show() {
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    /**
     * 关闭弹窗
     */
    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
