/**
 * BetterJSONObject
 * 
 * Wraps JSONObject with simpler getters.  I'm sure there some smarter way to delegate calls than this...
 */

package org.inaturalist.shedd.android;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class BetterJSONObject implements Serializable {
	public final static String TAG = "BetterJSONObject";
	private transient JSONObject mJSONObject;
	private transient DateFormat mDateTimeFormat; 
	private transient DateFormat mDateFormat;
	

	public BetterJSONObject() {
	    this(new JSONObject());
	}
	
	public BetterJSONObject(JSONObject o) {
		mJSONObject = o;
		mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
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
