package org.inaturalist.android;

import java.lang.reflect.Field;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.GridView;

/** A GridView extension that supports getColumnWidth for pre-16 API */
public class GridViewExtended extends GridView {

	public GridViewExtended(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public GridViewExtended(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public GridViewExtended(Context context) {
		super(context);
	}
	
	
	@SuppressLint("NewApi")
	@Override
	public int getColumnWidth() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			return super.getColumnWidth();
		else {
			try {
				Field field = GridView.class.getDeclaredField("mColumnWidth");
				field.setAccessible(true);
				Integer value = (Integer) field.get(this);
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
