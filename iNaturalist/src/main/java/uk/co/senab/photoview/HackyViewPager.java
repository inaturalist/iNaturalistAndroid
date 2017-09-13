package uk.co.senab.photoview;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Found at http://stackoverflow.com/questions/7814017/is-it-possible-to-disable-scrolling-on-a-viewpager.
 * Convenient way to temporarily disable ViewPager navigation while interacting with ImageView.
 * 
 * Julia Zudikova
 */

/**
 * Hacky fix for Issue #4 and
 * http://code.google.com/p/android/issues/detail?id=18990
 * <p/>
 * ScaleGestureDetector seems to mess up the touch events, which means that
 * ViewGroups which make use of onInterceptTouchEvent throw a lot of
 * IllegalArgumentException: pointerIndex out of range.
 * <p/>
 * There's not much I can do in my code for now, but we can mask the result by
 * just catching the problem and ignoring it.
 *
 * @author Chris Banes
 */
public class HackyViewPager extends ViewPager {

	private boolean isLocked;

	float mStartDragX;
	OnSwipeOutListener mOnSwipeOutListener;

	public interface OnSwipeOutListener {
		void onSwipeOutAtStart();
		void onSwipeOutAtEnd();
	}

	public void setOnSwipeOutListener(OnSwipeOutListener listener) {
		mOnSwipeOutListener = listener;
	}

	private boolean onSwipeOutAtStart() {
		if (mOnSwipeOutListener != null) {
			mOnSwipeOutListener.onSwipeOutAtStart();
            return false;
		}
		return true;
	}

	private boolean onSwipeOutAtEnd() {
		if (mOnSwipeOutListener != null) {
			mOnSwipeOutListener.onSwipeOutAtEnd();
            return false;
		}
		return true;
	}


	public HackyViewPager(Context context) {
        super(context);
        isLocked = false;
    }

    public HackyViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        isLocked = false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (!isLocked) {
			switch (ev.getAction() & MotionEventCompat.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:
					mStartDragX = ev.getX();
					break;
			}

	        try {
	            return super.onInterceptTouchEvent(ev);
	        } catch (IllegalArgumentException e) {
	            e.printStackTrace();
	            return false;
	        }
    	}
    	return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isLocked) {
			if ((getCurrentItem() == 0) || (getCurrentItem() == getAdapter().getCount() - 1)){
				final int action = event.getAction();
				float x = event.getX();
				switch (action & MotionEventCompat.ACTION_MASK) {
					case MotionEvent.ACTION_MOVE:
						break;
					case MotionEvent.ACTION_UP:
					    boolean value = true;
						if ((getCurrentItem() == 0) && (x > mStartDragX)) {
							value = onSwipeOutAtStart();
						}
						if ((getCurrentItem() == getAdapter().getCount() - 1) && (x < mStartDragX)) {
							value = onSwipeOutAtEnd();
						}

						if (!value) {
                            return false;
                        }

						break;
				}
			} else {
				mStartDragX = 0;
			}

            return super.onTouchEvent(event);
        }
        return false;
    }
    
	public void toggleLock() {
		isLocked = !isLocked;
	}

	public void setLocked(boolean isLocked) {
		this.isLocked = isLocked;
	}

	public boolean isLocked() {
		return isLocked;
	}
	
}
