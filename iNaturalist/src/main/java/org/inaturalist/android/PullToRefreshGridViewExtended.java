package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.GridView;

import com.handmark.pulltorefresh.library.PullToRefreshGridView;

import java.lang.reflect.Field;

/** A GridView extension that supports getColumnWidth for pre-16 API */
public class PullToRefreshGridViewExtended extends PullToRefreshGridView {

	public PullToRefreshGridViewExtended(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PullToRefreshGridViewExtended(Context context) {
		super(context);
	}
	
	
	@SuppressLint("NewApi")
	public int getColumnWidth() {
        GridView grid = this.getRefreshableView();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			return grid.getColumnWidth();
		else {
			try {
				Field field = GridView.class.getDeclaredField("mColumnWidth");
				field.setAccessible(true);
				Integer value = (Integer) field.get(grid);
				field.setAccessible(false);

				return value.intValue();
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}	

}
