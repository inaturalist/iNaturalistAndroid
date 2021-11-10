package org.inaturalist.android;
import android.content.Context;
import android.util.AttributeSet;

import com.viewpagerindicator.CirclePageIndicator;

public class FixedCirclePageIndicator extends CirclePageIndicator {
    public FixedCirclePageIndicator(Context context) {
        super(context);
    }

    public FixedCirclePageIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FixedCirclePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean onTouchEvent(android.view.MotionEvent ev) {
        try {
            return super.onTouchEvent(ev);
        } catch (Exception exc) {
            exc.printStackTrace();
            return false;
        }
    }
}
