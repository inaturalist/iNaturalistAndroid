/**
 * BetterJSONObject
 * 
 * Wraps JSONObject with simpler getters.  I'm sure there some smarter way to delegate calls than this...
 */

package org.inaturalist.android;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class BetterJSONObject implements Serializable {
    public final static String TAG = "BetterJSONObject";
    private transient JSONObject mJSONObject;
	private transient List<DateFormat> mDateFormats = null;

	public BetterJSONObject() {
	    this(new JSONObject());
	}

	public BetterJSONObject(String json) {
		try {
			mJSONObject = new JSONObject(json);
		} catch (JSONException e) {
			e.printStackTrace();
			mJSONObject = new JSONObject();
		}
		initRegExIfNeeded();
	}

	public BetterJSONObject(JSONObject o) {
		mJSONObject = o;
		initRegExIfNeeded();
	}

	private void initRegExIfNeeded() {
	    if (mDateFormats == null) {
	    	mDateFormats = new ArrayList<>();

			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZ");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			mDateFormats.add(df);
			df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
	    	mDateFormats.add(df);
			mDateFormats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZZZ", Locale.US));
			mDateFormats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssSSSz", Locale.US));
			mDateFormats.add(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US));
			mDateFormats.add(new SimpleDateFormat("yyyy-MM-dd", Locale.US));
		}
	}
	
	public JSONObject getJSONObject() {
	    return mJSONObject;
	}
	
	public SerializableJSONArray getJSONArray(String name) {
	    try {
	        return new SerializableJSONArray(mJSONObject.getJSONArray(name));
	    } catch (JSONException e) {
	        return null;
	    }
	}
	
	
	public boolean has(String name) {
	    return mJSONObject.has(name);
	}

	public boolean isNull(String name) {
	    return mJSONObject.isNull(name);
	}

	public JSONObject getJSONObject(String name) {
	    try {
	        return mJSONObject.getJSONObject(name);
	    } catch (JSONException e) {
	        return null;
	    }
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
		return value == null ? null : Float.parseFloat(value.toString());
	}
	
	public Timestamp getTimestamp(String name) {
		initRegExIfNeeded();
		String value = getString(name);
		if (value == null) { return null; }
		Date date = new Date();

		// Try different methods of date format parsing until one works
		for (DateFormat format : mDateFormats) {
			try {
				date = format.parse(value);
				break;
			} catch (ParseException e) {
			}
		}

		return new Timestamp(date.getTime());
	}
	
	public void put(String name, Object value) {
	    try {
	        mJSONObject.put(name, value);
	    } catch (JSONException e1) {
	        try {
	            mJSONObject.put(name, value.toString());
	        } catch (JSONException e2) {
	            Log.e(TAG, "Failed to put " + name + ", " + value + ": " + e2);
	        }
	    }
	}
	
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeObject(mJSONObject.toString());
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException, JSONException {
        ois.defaultReadObject();
        mJSONObject = new JSONObject((String) ois.readObject());
    }	
}
