package org.inaturalist.android;

import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PinnedLocationSearchActivity extends AppCompatActivity {
    private static final String TAG = "PinnedLocationSearchActivity";

    public static final String ID = "id";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String TITLE = "title";
    public static final String ACCURACY = "accuracy";
    public static final String GEOPRIVACY = "geoprivacy";

    private LocationChooserPlaceAdapter mAdapter;

    private ProgressBar mProgress;
    
    private INaturalistApp mApp;

    private TextView mNoResults;
    private Handler mHandler;
    private EditText mSearchText;
    @State public String mCurrentSearch;
    private ListView mListView;
    private List<INatPlace> mPlaces;
    private ActivityHelper mHelper;


    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }
    }
 

    private List<INatPlace> autocomplete(String input) {
        List<INatPlace> resultList = new ArrayList<INatPlace>();

        if (!isNetworkAvailable()) {
            return resultList;
        }

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(INaturalistService.HOST + "/saved_locations.json");
            sb.append("?q=");
            sb.append(URLEncoder.encode(input, "utf8"));

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Locale deviceLocale = getResources().getConfiguration().locale;
            String deviceLexicon = deviceLocale.getLanguage();
            sb.append("&locale=");
            sb.append(deviceLexicon);

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            String jwtToken = mApp.getJWTToken();
            if (mApp.loggedIn() && (jwtToken != null)) conn.setRequestProperty ("Authorization", jwtToken);

            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }

        } catch (MalformedURLException e) {
            Logger.tag(TAG).error("Error processing Places API URL", e);
            return resultList;
        } catch (IOException e) {
            Logger.tag(TAG).error("Error connecting to Places API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            JSONObject resultsObject = new JSONObject(jsonResults.toString());
            JSONArray predsJsonArray = resultsObject.getJSONArray("results");

            resultList = new ArrayList<INatPlace>(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                resultList.add(new INatPlace(predsJsonArray.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error("Cannot process JSON results", e);
        }

        return resultList;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            setResult(RESULT_CANCELED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAfterTransition();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

   @Override
   public void onBackPressed() {
       setResult(RESULT_CANCELED);
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
           finishAfterTransition();
       } else {
           finish();
       }
   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.taxon_search_action_bar, null);
        actionBar.setCustomView(customView);
        actionBar.setLogo(R.drawable.ic_arrow_back);
       
        setContentView(R.layout.taxon_search);
        
        mHandler = new Handler();
        mHelper = new ActivityHelper(this);

        mSearchText = (EditText) customView.findViewById(R.id.search_text);
        mSearchText.setHint(R.string.search);

        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence s, int start, int before, int count) {
                loadResults();
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);

        mNoResults = (TextView) findViewById(android.R.id.empty);
        mNoResults.setVisibility(View.GONE);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setVisibility(View.VISIBLE);

        mListView.setOnItemClickListener((adapterView, view, index, l) -> {
            INatPlace place = mPlaces.get(index);
            Bundle bundle = new Bundle();

            bundle.putDouble(LATITUDE, place.latitude);
            bundle.putDouble(LONGITUDE, place.longitude);
            bundle.putDouble(ACCURACY, place.accuracy);
            bundle.putString(GEOPRIVACY, place.geoprivacy);
            bundle.putString(TITLE, place.title);

            Intent resultIntent = new Intent();
            resultIntent.putExtras(bundle);
            setResult(RESULT_OK, resultIntent);

            finish();
        });

        mListView.setOnItemLongClickListener((adapterView, view, index, l) -> {
            mHelper.confirm(getString(R.string.delete), getString(R.string.delete_this_pinned_location), (dialogInterface, i) -> {
                // Delete pinned location
                INatPlace place = mPlaces.get(index);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_PINNED_LOCATION, null, PinnedLocationSearchActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.ID, place.id);
                ContextCompat.startForegroundService(PinnedLocationSearchActivity.this, serviceIntent);

                // Refresh pinned locations
                mProgress.setVisibility(View.VISIBLE);
                mListView.setVisibility(View.GONE);
                mNoResults.setVisibility(View.GONE);

                mHandler.postDelayed(() -> {
                    loadResults();
                }, 1000);

            }, (dialogInterface, i) -> { }, getString(R.string.yes), getString(R.string.no));
            return true;
        });

        // Initially show all pinned locations (equal to having no query)
        loadResults();

        mHandler.postDelayed(() -> {
            mSearchText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mSearchText, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private void loadResults() {
        mProgress.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
        mNoResults.setVisibility(View.GONE);

        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(() -> {
            String query = mSearchText.getText().toString();
            mCurrentSearch = query;

            new Thread(() -> {
                List<INatPlace> places = autocomplete(query);

                if (!query.equals(mCurrentSearch)) {
                    return;
                }

                mPlaces = places;
                mAdapter = new LocationChooserPlaceAdapter(PinnedLocationSearchActivity.this, mPlaces);

                runOnUiThread(() -> {
                    mListView.setAdapter(mAdapter);

                    mProgress.setVisibility(View.GONE);

                    if (places.size() == 0) {
                        mListView.setVisibility(View.GONE);
                        mNoResults.setVisibility(View.VISIBLE);
                    } else {
                        mListView.setVisibility(View.VISIBLE);
                        mNoResults.setVisibility(View.GONE);
                    }
                });

            }).start();

        }, 500);
    }

    private boolean isNetworkAvailable() {
         ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
         NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
         return activeNetworkInfo != null && activeNetworkInfo.isConnected();
     }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

}
