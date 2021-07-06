package org.inaturalist.android;

import android.graphics.Rect;
import android.util.Log;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

import org.tinylog.Logger;

public class BindingAdapterUtils {
    @BindingAdapter("bind:increaseTouch")
    public static void increaseTouch(ImageView view, double value) {

        ActivityHelper helper = new ActivityHelper(view.getContext());

        View parent = (View) view.getParent();
        parent.post(new Runnable() {
            @Override
            public void run() {
                Rect rect = new Rect();
                view.getHitRect(rect);
                int intValue = (int) helper.dpToPx((int) value);
                rect.top -= intValue;
                rect.left -= intValue;
                rect.bottom += intValue;
                rect.right += intValue;
                parent.setTouchDelegate(new TouchDelegate(rect, view));
            }
        });
    }
}
