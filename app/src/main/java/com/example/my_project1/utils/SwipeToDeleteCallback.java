package com.example.my_project1.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;

public abstract class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {

    private final Paint mClearPaint;
    private final ColorDrawable mBackground;
    private final int mBackgroundColor;
    private final Drawable mDeleteDrawable;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;

    protected SwipeToDeleteCallback(Context context) {
        super(0, ItemTouchHelper.LEFT);
        mBackground = new ColorDrawable();
        mBackgroundColor = Color.parseColor("#FF6B6B"); // 柔和的红色
        mClearPaint = new Paint();
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mDeleteDrawable = ContextCompat.getDrawable(context, R.drawable.ic_trash);
        if (mDeleteDrawable != null) {
            mIntrinsicWidth = mDeleteDrawable.getIntrinsicWidth();
            mIntrinsicHeight = mDeleteDrawable.getIntrinsicHeight();
        } else {
            mIntrinsicWidth = 0;
            mIntrinsicHeight = 0;
        }
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();

        boolean isCancelled = dX == 0 && !isCurrentlyActive;

        if (isCancelled) {
            clearCanvas(c, itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom());
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        mBackground.setColor(mBackgroundColor);
        mBackground.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
        mBackground.draw(c);

        if (mDeleteDrawable != null) {
            int deleteIconTop = itemView.getTop() + (itemHeight - mIntrinsicHeight) / 2;
            int deleteIconMargin = (itemHeight - mIntrinsicHeight) / 2;
            int deleteIconLeft = itemView.getRight() - deleteIconMargin - mIntrinsicWidth;
            int deleteIconRight = itemView.getRight() - deleteIconMargin;
            int deleteIconBottom = deleteIconTop + mIntrinsicHeight;

            mDeleteDrawable.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom);
            mDeleteDrawable.draw(c);
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    private void clearCanvas(Canvas c, Float left, Float top, Float right, Float bottom) {
        c.drawRect(left, top, right, bottom, mClearPaint);
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.7f;
    }
}
