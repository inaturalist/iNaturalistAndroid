package org.inaturalist.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

public class DividerItemDecorationPreferences extends RecyclerView.ItemDecoration {

    private Drawable mDivider;
    private int paddingLeft = 0;
    private int paddingRight = 0;

    public DividerItemDecorationPreferences(Context context, int paddingLeft, int paddingRight) {
        mDivider = ContextCompat.getDrawable(context, R.drawable.divider_recycler_view);
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int left = paddingLeft;
        int right = parent.getWidth() - paddingRight;
        int childCount = parent.getChildCount();
        boolean lastIteration = false;
        for (int i = 0; i < childCount; i++) {
            if (i == childCount - 1)
                lastIteration = true;
            View child = parent.getChildAt(i);
            if (!lastIteration) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                int top = child.getBottom() + params.bottomMargin;
                int bottom = top + mDivider.getIntrinsicHeight();
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }
    }

}
