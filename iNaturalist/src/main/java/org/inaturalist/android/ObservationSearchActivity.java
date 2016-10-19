package org.inaturalist.android;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.flurry.android.FlurryAgent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ObservationSearchActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String LOG_TAG = "ObervationSearchActivity";

    private ListAdapter mObservationsAdapter;

    private ProgressBar mProgress;
    
    private INaturalistApp mApp;

    private String mCurrentSearchString = "";

    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}
    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }
    }
 
	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}	


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

   @Override
   public void onBackPressed() {
       finish();
   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.observation_search_action_bar, null);
        actionBar.setCustomView(customView);
        actionBar.setLogo(R.drawable.ic_arrow_back);
       
        setContentView(R.layout.taxon_search);
        
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);


        String login = mApp.currentUserLogin();

        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

        final Cursor cursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                conditions, null, Observation.DEFAULT_SORT_ORDER);

        final EditText autoCompView = (EditText) customView.findViewById(R.id.search_text);
        
        autoCompView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence s, int start, int before, int count) {
                if (s.length() == 0) {
                    getListView().setVisibility(View.GONE);
                    mProgress.setVisibility(View.GONE);
                    return;
                } else {
                    getListView().setVisibility(View.VISIBLE);
                }

                if (!isNetworkAvailable() || !mApp.loggedIn()) {
                    // Offline search (no network or user not logged-in)
                    if ((mObservationsAdapter == null) || !(mObservationsAdapter instanceof ObservationCursorAdapter)) {
                        mObservationsAdapter = new ObservationCursorAdapter(ObservationSearchActivity.this, cursor);
                        setListAdapter(mObservationsAdapter);
                    }

                    ((ObservationCursorAdapter)mObservationsAdapter).refreshCursor(s.toString().trim());

                } else {
                    // Online search
                    performOnlineSearch(s.toString());
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                autoCompView.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(autoCompView, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);

        getListView().setVisibility(View.GONE);
        getListView().setOnItemClickListener(this);
    }

    private void performOnlineSearch(final String query) {
        if (mProgress.getVisibility() == View.GONE) {
            mProgress.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        }

        mCurrentSearchString = query;

        // Run on background thread (not to block UI)
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<JSONObject> results = searchObservations(mCurrentSearchString);

                if (!mCurrentSearchString.equals(query)) {
                    // While the search was running, the user entered new characters into the search bar - these
                    // will be old results for the previous search string - don't return these
                    return;
                }

                mObservationsAdapter = new UserObservationAdapter(ObservationSearchActivity.this, results);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setListAdapter(mObservationsAdapter);
                        mProgress.setVisibility(View.GONE);
                        mListView.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();

    }

    public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
        if (mObservationsAdapter instanceof ObservationCursorAdapter) {
            // Offline result
            Uri uri = ContentUris.withAppendedId(Observation.CONTENT_URI, id);
            if ((!((ObservationCursorAdapter)mObservationsAdapter).isLocked(uri)) || (((ObservationCursorAdapter)mObservationsAdapter).isLocked(uri) && !mApp.getIsSyncing())) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri, this, ObservationViewerActivity.class));
            }
        } else {
            // Online result
            JSONObject item = (JSONObject) v.getTag();
            Intent intent = new Intent(ObservationSearchActivity.this, ObservationViewerActivity.class);
            intent.putExtra("observation", item.toString());
            intent.putExtra("read_only", true);
            intent.putExtra("reload", true);
            startActivity(intent);
        }
    }

    private boolean isNetworkAvailable() {
         ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
         NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
         return activeNetworkInfo != null && activeNetworkInfo.isConnected();
     }


    private ListView mListView;

    protected ListView getListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(android.R.id.list);
        }
        return mListView;
    }

    protected void setListAdapter(ListAdapter adapter) {
        getListView().setAdapter(adapter);
    }

    // Performs an online search of observations
    private ArrayList<JSONObject> searchObservations(String input) {
        ArrayList<JSONObject> resultList = null;

        if (!isNetworkAvailable()) {
            return new ArrayList<JSONObject>();
        }

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(INaturalistService.HOST + "/observations/" + mApp.currentUserLogin() + ".json");
            sb.append("?per_page=100");
            sb.append("&q=");
            sb.append(URLEncoder.encode(input, "utf8"));

            Locale deviceLocale = getResources().getConfiguration().locale;
            String deviceLexicon = deviceLocale.getLanguage();
            sb.append("&locale=");
            sb.append(deviceLexicon);

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error connecting to observation search API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            JSONArray predsJsonArray = new JSONArray(jsonResults.toString());

            // Extract the Place descriptions from the results
            resultList = new ArrayList<JSONObject>(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                resultList.add(predsJsonArray.getJSONObject(i));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Cannot process JSON results", e);
        }

        return resultList;
    }
}
