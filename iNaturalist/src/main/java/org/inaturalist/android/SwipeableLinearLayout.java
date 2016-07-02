package org.inaturalist.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.LinearLayout;

// Linear layout that intercepts all swiping gestures (so it won't reach its child views)
public class SwipeableLinearLayout extends LinearLayout {

    public SwipeableLinearLayout(Context context) {
        super(context);
    }

    public SwipeableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public SwipeableLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SwipeableLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private float mStartEventX = -1, mStartEventY = -1;
    private static final int SWIPE_THRESHOLD = 80;

    public interface SwipeListener {
        public void onSwipeRight();
        public void onSwipeLeft();
    }

    private SwipeListener mOnSwipeListener = null;

    public void setOnSwipeListener(SwipeListener listener) {
        mOnSwipeListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mOnSwipeListener == null) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mStartEventX = ev.getRawX();
            mStartEventY = ev.getRawY();
            return false;
        }

        if (((ev.getAction() == MotionEvent.ACTION_CANCEL) || (ev.getAction() == MotionEvent.ACTION_UP)) && (mStartEventX > -1)) {
            // See if the user swiped
            try {
                float diffY = ev.getRawY() - mStartEventY;
                float diffX = ev.getRawX() - mStartEventX;
                if ((Math.abs(diffX) - Math.abs(diffY)) > SWIPE_THRESHOLD) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD) {
                        if (diffX > 0) {
                            mOnSwipeListener.onSwipeRight();
                        } else {
                            mOnSwipeListener.onSwipeLeft();
                        }
                        mStartEventX = -1;
                        mStartEventY = -1;
                        return true;
                    }
                }
            } catch (Exception e) {
            }

            mStartEventX = -1;
            mStartEventY = -1;
        }

        return false;
    }


}
