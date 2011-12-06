package org.inaturalist.android;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

public class INaturalistService extends IntentService {
	public static String TAG = "INaturalistService";
	public static String HOST = "http://192.168.1.12:3000";
	public static String ACTION_PASSIVE_SYNC = "passive_sync";
	public static String ACTION_SYNC = "sync";
	private String mLogin;
	private String mCredentials;
	private SharedPreferences mPreferences;
	private boolean mPassive;

	public INaturalistService() {
		super("INaturalistService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
		mLogin = mPreferences.getString("username", null);
		mCredentials = mPreferences.getString("credentials", null);
		String action = intent.getAction();

		// TODO dispatch intent actions
		// TODO postObservations()
		postObservations();
		// TODO postPhotos()
		if (action.equals(ACTION_PASSIVE_SYNC)) {
			mPassive = true;
		} else {
			mPassive = false;
		}
		getUserObservations();
		Toast.makeText(getApplicationContext(), "Observations synced", Toast.LENGTH_SHORT);
	}

	private void postObservations() {
		Observation observation;
		// query observations where _updated_at > updated_at
		Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
				Observation.PROJECTION, 
				"_updated_at > _synced_at AND _synced_at IS NOT NULL", null, Observation.DEFAULT_SORT_ORDER);
		
		// for each observation PUT to /observations/:id
		Log.d(TAG, "PUTing " + c.getCount() + " observations");
		c.moveToFirst();
		while (c.isAfterLast() == false) {
			observation = new Observation(c);
			JSONArray response = put(HOST + "/observations/" + observation.id + ".json", observation.getParams());
			try {
				Log.d(TAG, "response: " + response);
				if (response != null && response.length() == 1) {
					JSONObject json = response.getJSONObject(0);
					BetterJSONObject o = new BetterJSONObject(json);
					Observation jsonObservation = new Observation(o);
					observation.merge(jsonObservation);
					observation._synced_at = new Timestamp(Calendar.SECOND);
					observation._updated_at = observation._synced_at;
					Log.d(TAG, "updating observation " + observation + "");
					getContentResolver().update(observation.getUri(), observation.getContentValues(), null, null);
				}
			} catch (JSONException e) {
				Log.e(TAG, "JSONException: " + e.toString());
			}
			c.moveToNext();
		}
		c.close();
		
		// query observations where _synced_at IS NULL
		c = getContentResolver().query(Observation.CONTENT_URI, 
				Observation.PROJECTION, 
				"id IS NULL", null, Observation.DEFAULT_SORT_ORDER);
		
		// TODO for each observation POST to /observations/
		Log.d(TAG, "POSTing " + c.getCount() + " observations");
		c.moveToFirst();
		while (c.isAfterLast() == false) {
			observation = new Observation(c);
			JSONArray response = post(HOST + "/observations.json", observation.getParams());
			try {
				Log.d(TAG, "response: " + response);
				if (response != null && response.length() == 1) {
					JSONObject json = response.getJSONObject(0);
					BetterJSONObject o = new BetterJSONObject(json);
					Observation jsonObservation = new Observation(o);
					observation.merge(jsonObservation);
					observation._synced_at = new Timestamp(Calendar.SECOND);
					observation._updated_at = observation._synced_at;
					Log.d(TAG, "updating observation " + observation + "");
					getContentResolver().update(observation.getUri(), observation.getContentValues(), null, null);
				}
			} catch (JSONException e) {
				Log.e(TAG, "JSONException: " + e.toString());
			}
			c.moveToNext();
		}
		c.close();
	}

	private void getUserObservations() {
		if (ensureCredentials() == false) {
			return;
		}
		JSONArray json = get(HOST + "/observations/" + mLogin + ".json");
		Log.d(TAG, "json: " + json);
		if (json == null || json.length() == 0) { return; }
		syncJson(json);
	}

	private JSONArray put(String url, ArrayList<NameValuePair> params) {
		params.add(new BasicNameValuePair("_method", "PUT"));
		return request(url, "put", params, true);
	}

	private JSONArray post(String url, ArrayList<NameValuePair> params) {
		return request(url, "post", params, true);
	}

	private JSONArray get(String url) {
		return get(url, false);
	}

	private JSONArray get(String url, boolean authenticated) {
		return request(url, "get", null, authenticated);
	}

	private JSONArray request(String url, String method, ArrayList<NameValuePair> params, boolean authenticated) {
		Log.d(TAG, method.toUpperCase() + " " + url);
		DefaultHttpClient client = new DefaultHttpClient();

		HttpRequestBase request = method == "get" ? new HttpGet(url) : new HttpPost(url);

		// POST params
		if (params != null) {
			try {
				HttpEntity entity = new UrlEncodedFormEntity(params);
				((HttpPost) request).setEntity(entity);
			} catch (final UnsupportedEncodingException e) {
				// this should never happen.
				throw new AssertionError(e);
			}
		}

		// auth
		if (authenticated) {
			ensureCredentials();
			request.setHeader("Authorization", "Basic "+ mCredentials);
		}

		try {
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			String content = EntityUtils.toString(entity);
			switch (response.getStatusLine().getStatusCode()) {
			case HttpStatus.SC_OK:
				try {
					JSONArray json = new JSONArray(content);
					return json;
				} catch (JSONException e) {
					Log.e(TAG, "JSONException: " + e.toString());
				}
				break;
			case HttpStatus.SC_UNAUTHORIZED:
				requestCredentials();
				break;
			default:
				Log.e(TAG, response.getStatusLine().toString());
			}
		}
		catch (IOException e) {
			request.abort();
			Log.w(TAG, "Error for URL " + url, e);
		}
		return null;
	}

	private boolean ensureCredentials() {
		if (mCredentials != null) { return true; }

		// request login unless passive
		Log.d(TAG, "ensuring creds, mPassive: " + mPassive);
		if (!mPassive) {
			requestCredentials();
		}
		stopSelf();
		return false;
	}
	
	private void requestCredentials() {
		Intent intent = new Intent(INaturalistPrefsActivity.REAUTHENTICATE_ACTION, 
				null, getBaseContext(), INaturalistPrefsActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		getApplication().startActivity(intent);
	}

	public static boolean verifyCredentials(String credentials) {
		DefaultHttpClient client = new DefaultHttpClient();
		String url = HOST + "/observations/new.json";
		HttpRequestBase request = new HttpGet(url);
		request.setHeader("Authorization", "Basic "+credentials);
		request.setHeader("Content-Type", "application/json");

		try {
			HttpResponse response = client.execute(request);
			HttpEntity entity = response.getEntity();
			String content = EntityUtils.toString(entity);
			Log.d(TAG, "OK: " + content.toString());
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return true;
			} else {
				Log.e(TAG, "Authentication failed: " + content);
				return false;
			}
		}
		catch (IOException e) {
			request.abort();
			Log.w(TAG, "Error for URL " + url, e);
		}
		return false;
	}

	public static boolean verifyCredentials(String username, String password) {
		String credentials = Base64.encodeToString(
			(username + ":" + password).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
		);
		return verifyCredentials(credentials);
	}

	public void syncJson(JSONArray json) {
		ArrayList<Integer> ids = new ArrayList<Integer>();
		ArrayList<Integer> existingIds = new ArrayList<Integer>();
		ArrayList<Integer> newIds = new ArrayList<Integer>();
		HashMap<Integer,Observation> jsonObservationsById = new HashMap<Integer,Observation>();
		Observation observation;
		Observation jsonObservation;

		BetterJSONObject o;
		for (int i = 0; i < json.length(); i++) {
			try {
				o = new BetterJSONObject(json.getJSONObject(i));
				ids.add(o.getInt("id"));
				jsonObservationsById.put(o.getInt("id"), new Observation(o));
			} catch (JSONException e) {
				Log.e(TAG, "JSONException: " + e.toString());
			}
		}
		// find obs with existing ids
		String joinedIds = StringUtils.join(ids, ",");
		// TODO why doesn't selectionArgs work for id IN (?)
		Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
				Observation.PROJECTION, 
				"id IN ("+joinedIds+")", null, Observation.DEFAULT_SORT_ORDER);

		// update existing
		c.moveToFirst();
		while (c.isAfterLast() == false) {
			observation = new Observation(c);
			jsonObservation = jsonObservationsById.get(observation.id);
			observation.merge(jsonObservation); 
			observation._synced_at = new Timestamp(Calendar.getInstance().getTimeInMillis());
			observation._updated_at = observation._synced_at;
			getContentResolver().update(observation.getUri(), observation.getContentValues(), null, null);
			existingIds.add(observation.id);
			c.moveToNext();
		}
		c.close();

		// insert new
		newIds = (ArrayList<Integer>) CollectionUtils.subtract(ids, existingIds);
		Collections.sort(newIds);
		Log.d(TAG, "ids: " + ids);
		Log.d(TAG, "existingIds: " + existingIds);
		Log.d(TAG, "newIds: " + newIds);
		for (int i = 0; i < newIds.size(); i++) {			
			jsonObservation = jsonObservationsById.get(newIds.get(i));
			getContentResolver().insert(Observation.CONTENT_URI, jsonObservation.getContentValues());
		}
	}
}
