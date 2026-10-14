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
                boolean hasTranslation = Math.abs(targetView.getTranslationY()) > 0.1f;

                if (isTopBoundary || isBottomBoundary || isOverScrolling || hasTranslation) {
                    if (!isOverScrolling) {
                        isOverScrolling = true;
                        currentTranslationY = targetView.getTranslationY();
                    }

                    // 调小阻尼阻力，基础系数提升至 0.6f，拉伸更轻盈顺滑
                    float density = targetView.getResources().getDisplayMetrics().density;
                    float maxDistance = density * 220f; // 最大拉伸距离 220dp
                    float progress = Math.min(Math.abs(currentTranslationY) / maxDistance, 1.0f);
                    float damping = 0.6f * (1.0f - progress * 0.5f);

                    currentTranslationY += deltaY * damping;

                    if (currentTranslationY > maxDistance) currentTranslationY = maxDistance;
                    if (currentTranslationY < -maxDistance) currentTranslationY = -maxDistance;

                    // 越界回退归零逻辑：当滑动回退至原点时无缝衔接正常滚动
                    if ((currentTranslationY <= 0 && deltaY < 0 && targetView.getTranslationY() > 0) ||
                        (currentTranslationY >= 0 && deltaY > 0 && targetView.getTranslationY() < 0)) {
                        targetView.setTranslationY(0f);
                        isOverScrolling = false;
                        currentTranslationY = 0f;
                        return false;
                    }

                    targetView.setTranslationY(currentTranslationY);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isOverScrolling || Math.abs(targetView.getTranslationY()) > 0.1f) {
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
        if (Math.abs(startVal) < 0.1f) {
            targetView.setTranslationY(0f);
            return;
        }

        bounceAnimator = ValueAnimator.ofFloat(startVal, 0f);
        bounceAnimator.setDuration(320);
        bounceAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        bounceAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            targetView.setTranslationY(val);
        });
        bounceAnimator.start();
    }
}
