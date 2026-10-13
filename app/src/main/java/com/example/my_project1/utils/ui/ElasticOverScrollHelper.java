package com.example.my_project1.utils.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * ElasticOverScrollHelper - 弹性上下滑动与回弹效果
 * -----------------------------------------------------------
 * 当滑动到列表/滚动视图最顶端或最底端时，继续拖动可向上/向下延伸（带阻尼效果），
 * 松开手指后平滑动画弹回原位。
 */
public class ElasticOverScrollHelper implements View.OnTouchListener {

    private final View targetView;
    private float lastY = 0f;
    private float currentTranslationY = 0f;
    private boolean isOverScrolling = false;
    private ValueAnimator bounceAnimator;

    public static ElasticOverScrollHelper attach(View view) {
        ElasticOverScrollHelper helper = new ElasticOverScrollHelper(view);
        view.setOnTouchListener(helper);
        return helper;
    }

    public ElasticOverScrollHelper(View targetView) {
        this.targetView = targetView;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastY = event.getRawY();
                currentTranslationY = targetView.getTranslationY();
                if (bounceAnimator != null && bounceAnimator.isRunning()) {
                    bounceAnimator.cancel();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float currentY = event.getRawY();
                float deltaY = currentY - lastY;
                lastY = currentY;

                boolean canScrollUp = targetView.canScrollVertically(-1);
                boolean canScrollDown = targetView.canScrollVertically(1);

                // 判断是否处于边界并继续同向拖动
                boolean isTopBoundary = !canScrollUp && deltaY > 0;
                boolean isBottomBoundary = !canScrollDown && deltaY < 0;

                if (isTopBoundary || isBottomBoundary || isOverScrolling) {
                    if (!isOverScrolling) {
                        isOverScrolling = true;
                        currentTranslationY = targetView.getTranslationY();
                    }

                    // 阻尼系数 (随着位移增大阻力变大)
                    float damping = 0.45f;
                    float maxDistance = targetView.getResources().getDisplayMetrics().density * 160f; // 最大拉伸 160dp

                    currentTranslationY += deltaY * damping;

                    if (currentTranslationY > maxDistance) currentTranslationY = maxDistance;
                    if (currentTranslationY < -maxDistance) currentTranslationY = -maxDistance;

                    targetView.setTranslationY(currentTranslationY);

                    // 越界回退归零逻辑
                    if ((currentTranslationY > 0 && deltaY < 0 && targetView.getTranslationY() <= 0) ||
                        (currentTranslationY < 0 && deltaY > 0 && targetView.getTranslationY() >= 0)) {
                        targetView.setTranslationY(0f);
                        isOverScrolling = false;
                        currentTranslationY = 0f;
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isOverScrolling || targetView.getTranslationY() != 0) {
                    startBounceAnimation();
                    isOverScrolling = false;
                    currentTranslationY = 0f;
                }
                break;
        }
        return false;
    }

    private void startBounceAnimation() {
        if (bounceAnimator != null && bounceAnimator.isRunning()) {
            bounceAnimator.cancel();
        }

        float startVal = targetView.getTranslationY();
        bounceAnimator = ValueAnimator.ofFloat(startVal, 0f);
        bounceAnimator.setDuration(300);
        bounceAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        bounceAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            targetView.setTranslationY(val);
        });
        bounceAnimator.start();
    }
}
