/**
 * BetterCursor
 * 
 * Wraps Cursor with simpler getters
 */

package org.inaturalist.android;

import java.io.Serializable;
import java.sql.Timestamp;

import android.database.Cursor;

public class BetterCursor implements Serializable {
	public final static String TAG = "BetterCursor";
	private Cursor mCursor;
	private Integer mPosition;
	
	public BetterCursor(Cursor c) {
		mCursor = c;
		if (mCursor.getPosition() == -1) mCursor.moveToFirst();
		mPosition = mCursor.getPosition();
	}
	
	
	public Object get(String name) {
	    mCursor.moveToPosition(mPosition);
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
	    mCursor.moveToPosition(mPosition);
		return (1 == mCursor.getInt(mCursor.getColumnIndexOrThrow(name)));
	}
	
	public Integer getInt(String name) {
	    mCursor.moveToPosition(mPosition);
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
	    mCursor.moveToPosition(mPosition);
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return mCursor.getFloat(mCursor.getColumnIndexOrThrow(name));
	}
	
	public Timestamp getTimestamp(String name) {
	    mCursor.moveToPosition(mPosition);
		if (mCursor.isNull(mCursor.getColumnIndexOrThrow(name))) {
			return null;
		}
		return new Timestamp(mCursor.getLong(mCursor.getColumnIndexOrThrow(name)));
	}
	
	public int getCount() {
	    return mCursor.getCount();
	}
}
