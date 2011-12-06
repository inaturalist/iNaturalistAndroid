/**
 * BetterCursor
 * 
 * Wraps Cursor with simpler getters
 */

package org.inaturalist.android;

import java.sql.Timestamp;
import android.database.Cursor;
import android.util.Log;

public class BetterCursor {
	public final static String TAG = "BetterCursor";
	private Cursor mCursor;
	
	public BetterCursor(Cursor c) {
		mCursor = c;
	}
	
	
	public Object get(String name) {
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return mCursor.getString(mCursor.getColumnIndexOrThrow(name));
	}
	
	public String getString(String name) {
		Object value = get(name);
		return value == null ? null : value.toString();
	}
	
	public Boolean getBoolean(String name) {
		return (1 == mCursor.getInt(mCursor.getColumnIndexOrThrow(name)));
	}
	
	public Integer getInt(String name) {
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return mCursor.getInt(mCursor.getColumnIndexOrThrow(name));
	}
	
	public Integer getInteger(String name) {
		return getInt(name);
	}
	
	public Double getDouble(String name) {
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return mCursor.getDouble(mCursor.getColumnIndexOrThrow(name));
	}
	
	public Float getFloat(String name) {
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return mCursor.getFloat(mCursor.getColumnIndexOrThrow(name));
	}
	
	public Timestamp getTimestamp(String name) {
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return new Timestamp(mCursor.getLong(mCursor.getColumnIndexOrThrow(name)));
	}
}
