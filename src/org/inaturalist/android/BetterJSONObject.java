/**
 * BetterJSONObject
 * 
 * Wraps JSONObject with simpler getters
 */

package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class BetterJSONObject {
	public final static String TAG = "BetterJSONObject";
	private JSONObject mJSONObject;
	private DateFormat mDateTimeFormat; 
	private DateFormat mDateFormat;
	
	public BetterJSONObject(JSONObject o) {
		mJSONObject = o;
		mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	}
	
	
	public Object get(String name) {
		if (mJSONObject.isNull(name)) {
			return null;
		}
		try {
			return mJSONObject.get(name);
		} catch (JSONException e) {
			return null;
		}
	}
	
	public String getString(String name) {
		Object value = get(name);
		return value == null ? null : value.toString();
	}
	
	public Boolean getBoolean(String name) {
		Object value = get(name);
		return value == null ? null : (Boolean) value;
	}
	
	public Integer getInt(String name) {
		Object value = get(name);
		return value == null ? null : (Integer) value;
	}
	
	public Integer getInteger(String name) {
		return getInt(name);
	}
	
	public Double getDouble(String name) {
		Object value = get(name);
		return (value == null ? null : Double.parseDouble(value.toString()));
	}
	
	public Float getFloat(String name) {
		Object value = get(name);
		return value == null ? null : (Float) value;
	}
	
	public Timestamp getTimestamp(String name) {
		String value = getString(name);
		if (value == null) { return null; }
		Date date;
		try {
			date = mDateTimeFormat.parse(value);
		} catch (ParseException e) {
			try {
				date =  mDateFormat.parse(value);
			} catch (ParseException e2) {
				return null;
			}
		}
		return new Timestamp(date.getTime());
	}
}
