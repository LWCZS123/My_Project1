package com.example.my_project1.utils.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/**
 * SwipeMenuLayout - 侧滑菜单布局
 * -------------------------------------------------------
 * 📌 功能：支持向左滑动显示菜单按钮，模仿截图效果
 */
public class SwipeMenuLayout extends ViewGroup {

    private int mScaledTouchSlop;
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private View mContentView;
    private View mMenuView;

    private float mLastX;
    private float mLastY;
    private boolean isSwipe = false;
    private boolean isSwipeEnable = true;

    public SwipeMenuLayout(Context context) {
        this(context, null);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScroller = new Scroller(context);
    }

    public void setSwipeEnable(boolean swipeEnable) {
        isSwipeEnable = swipeEnable;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() != 2) {
            throw new RuntimeException("SwipeMenuLayout must have exactly 2 children: Content and Menu");
        }
        mContentView = getChildAt(0);
        mMenuView = getChildAt(1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = 0;

        // Measure Content
        measureChild(mContentView, widthMeasureSpec, heightMeasureSpec);
        heightSize = Math.max(heightSize, mContentView.getMeasuredHeight());

        // Measure Menu (Width depends on its own content, Height matches Content)
        int menuWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int menuHeightSpec = MeasureSpec.makeMeasureSpec(mContentView.getMeasuredHeight(), MeasureSpec.EXACTLY);
        mMenuView.measure(menuWidthSpec, menuHeightSpec);

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Layout Content
        mContentView.layout(0, 0, mContentView.getMeasuredWidth(), mContentView.getMeasuredHeight());
        // Layout Menu (to the right of Content)
        mMenuView.layout(mContentView.getMeasuredWidth(), 0, 
                mContentView.getMeasuredWidth() + mMenuView.getMeasuredWidth(), mContentView.getMeasuredHeight());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isSwipeEnable) return false;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = ev.getX();
                mLastY = ev.getY();
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - mLastX;
                float dy = ev.getY() - mLastY;
                if (Math.abs(dx) > mScaledTouchSlop && Math.abs(dx) > Math.abs(dy)) {
                    isSwipe = true;
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isSwipeEnable) return super.onTouchEvent(ev);
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = ev.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - mLastX;
                int scrollX = getScrollX();
                int newScrollX = (int) (scrollX - dx);

                if (newScrollX < 0) {
                    newScrollX = 0;
                } else if (newScrollX > mMenuView.getMeasuredWidth()) {
                    newScrollX = mMenuView.getMeasuredWidth();
                }

                scrollTo(newScrollX, 0);
                mLastX = ev.getX();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mVelocityTracker.computeCurrentVelocity(1000);
                float xVelocity = mVelocityTracker.getXVelocity();
                int finalScrollX = getScrollX();

                if (xVelocity < -500) { // 向左快速滑动
                    smoothScrollTo(mMenuView.getMeasuredWidth());
                } else if (xVelocity > 500) { // 向右快速滑动
                    smoothScrollTo(0);
                } else {
                    if (finalScrollX > mMenuView.getMeasuredWidth() / 2) {
                        smoothScrollTo(mMenuView.getMeasuredWidth());
                    } else {
                        smoothScrollTo(0);
                    }
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                isSwipe = false;
                break;
        }
        return true;
    }

    private void smoothScrollTo(int destX) {
        int scrollX = getScrollX();
        int delta = destX - scrollX;
        mScroller.startScroll(scrollX, 0, delta, 0, 300);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), 0);
            postInvalidate();
        }
    }

    public void quickClose() {
        if (getScrollX() != 0) {
            scrollTo(0, 0);
        }
    }
}
