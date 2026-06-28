package com.example.my_project1.ui.dialog;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;

import com.airbnb.lottie.LottieAnimationView;
import com.example.my_project1.R;

import io.reactivex.annotations.NonNull;

/**
 * 自定义上传进度对话框（Lottie动画随进度变化+炫酷特效）
 */
public class UploadProgressDialog extends Dialog {

    private LottieAnimationView lottieAnimationView;

    public UploadProgressDialog(@NonNull Context context) {
        super(context, R.style.CustomDialog);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_upload_progress, null);
        setContentView(view);

        // 初始化 Lottie
        lottieAnimationView = view.findViewById(R.id.lottie_animation);

        // 对话框属性
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
    }

    /**
     * 更新动画进度
     * @param percentage 上传百分比 0~100
     */
    public void updateProgress(int percentage) {
        if (lottieAnimationView == null) return;

        float progress = Math.max(0f, Math.min(1f, percentage / 100f));

        // 进度 < 80%，动画正常播放
        if (percentage <= 80) {
            lottieAnimationView.setProgress(progress);
        }
        // 进度 80~99%，动画加速
        else if (percentage < 100) {
            float acceleratedProgress = 0.8f + (progress - 0.8f) * 3; // 3倍加速
            lottieAnimationView.setProgress(Math.min(acceleratedProgress, 0.99f));
        }
        // 100%，播放完整动画并闪烁
        else {
            lottieAnimationView.setProgress(1f);
            playCompletionEffect();
        }
    }

    /**
     * 上传完成时播放闪烁特效
     */
    private void playCompletionEffect() {
        if (lottieAnimationView == null) return;

        // 使用 ObjectAnimator 闪烁透明度
        ObjectAnimator animator = ObjectAnimator.ofFloat(lottieAnimationView, "alpha", 1f, 0.5f, 1f, 0.7f, 1f);
        animator.setDuration(600);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    /**
     * 上传失败时停止动画（可扩展）
     */
    public void showError() {
        if (lottieAnimationView != null) {
            lottieAnimationView.cancelAnimation();
        }
    }
}
