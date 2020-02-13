package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;

public class PlaceSearchActivity extends AppCompatActivity {
    private static final String TAG = "PlaceSearchActivity";

    public static final String PLACE_ID = "place_id";
    public static final String PLACE_DISPLAY_NAME = "place_display_name";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    // Current search results
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mResults = null;

    @State public String mLastSearch = "";
    private Handler mHandler;
    private boolean mIsSearching = false;
    private boolean mRefreshingUi = false;

    private ImageView mLocationIcon;
    private ImageView mClearLocation;
    private EditText mLocationEditText;
    private ListView mResultsList;
    private ProgressBar mLoadingResults;
    private TextView mNoResultsFound;
    private PlaceSearchReceiver mPlaceResultsReceiver;


    @Override
	protected void onStop()
	{
		super.onStop();		
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        setTitle(R.string.search_places);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mHelper = new ActivityHelper(this);

        setContentView(R.layout.place_search);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            mResults = null;
        }

        mHandler = new Handler();

        mLocationIcon = (ImageView) findViewById(R.id.location_icon);
        mClearLocation = (ImageView) findViewById(R.id.clear_location);
        mLocationEditText = (EditText) findViewById(R.id.location_edit_text);
        
        mResultsList = (ListView) findViewById(R.id.search_results);
        mLoadingResults = (ProgressBar) findViewById(R.id.loading_results);
        mNoResultsFound = (TextView) findViewById(R.id.no_results_found);

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (mRefreshingUi) return;

                final String query = s.toString();

                mLocationEditText.setTextColor(Color.parseColor("#000000"));
                mLocationIcon.setColorFilter(Color.parseColor("#646464"));

                mClearLocation.setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);

                mLastSearch = query;

                mHandler.postDelayed(() -> performSearch(query), 200);
            }
        };

        mLocationEditText.addTextChangedListener(textWatcher);

        TextView.OnEditorActionListener onEditorAction = new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    return true;
                }
                return false;
            }
        };

        mLocationEditText.setOnEditorActionListener(onEditorAction);

        View.OnClickListener onClear = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLocationEditText.setText("");
                mLocationEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mLocationEditText.requestFocus();
                    }
                }, 50);

                refreshViewState(true);
            }
        };


        View.OnFocusChangeListener onFocus = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v, boolean hasFocus) {
                if (hasFocus) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ((EditText)v).setSelection(0, ((EditText)v).getText().toString().length());
                        }
                    }, 50);

                    refreshViewState(false);
                }
            }
        };

        mLocationEditText.setOnFocusChangeListener(onFocus);
        mClearLocation.setOnClickListener(onClear);
    }

    private void setResultPlace(JSONObject place) {
        Intent data = new Intent();
        data.putExtra(PLACE_ID, place.optInt("id"));
        data.putExtra(PLACE_DISPLAY_NAME, place.optString("display_name"));
        setResult(RESULT_OK, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        BaseFragmentActivity.safeUnregisterReceiver(mPlaceResultsReceiver, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mPlaceResultsReceiver = new PlaceSearchReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.SEARCH_PLACES_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mPlaceResultsReceiver, filter, this);

        refreshViewState(true);
    }

    private void performSearch(String query) {
        if (!query.equals(mLastSearch)) {
            // User typed in more characters since then
            return;
        } else if (query.length() == 0) {
            // No search
            mResults = null;
            refreshViewState(false);
            return;
        }

        mIsSearching = true;
        mResults = null;

        Intent serviceIntent = new Intent(INaturalistService.ACTION_SEARCH_PLACES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.QUERY, query);
        serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, 1);
        ContextCompat.startForegroundService(this, serviceIntent);

        refreshViewState(false);
    }


    private void refreshViewState(boolean refreshSearchFields) {
        mRefreshingUi = true;

        if (refreshSearchFields) {
            // Refresh location search field
            if (mLastSearch.length() > 0) {
                mLocationEditText.setText(mLastSearch);
                mLocationEditText.setTextColor(Color.parseColor("#000000"));
                mLocationIcon.setColorFilter(Color.parseColor("#646464"));
                mClearLocation.setVisibility(View.GONE);
            } else {
                // No location at all (global search)
                mLocationEditText.setText("");
                mLocationIcon.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                mClearLocation.setVisibility(View.GONE);
            }

            mLocationEditText.requestFocus();
        }

        boolean dontShowNoResultsFound = false;

        if (mLastSearch.length() == 0) {
            mResults = new ArrayList<>();

            // add a "Global" result to the beginning of the search history
            mResults.add(0, getGlobalResult());

            // Don't show the "no results found" message
            dontShowNoResultsFound = true;
        }

        if (mResults == null) {
            mResultsList.setVisibility(View.GONE);
            mLoadingResults.setVisibility(mIsSearching ? View.VISIBLE : View.GONE);
            mNoResultsFound.setVisibility(View.GONE);
        } else {
            mLoadingResults.setVisibility(View.GONE);
            mResultsList.setVisibility(View.VISIBLE);

            mResultsList.setAdapter(new PlaceAdapter(this, mResults));

            if ((mResults.size() == 0) && (!dontShowNoResultsFound)) {
                mResultsList.setVisibility(View.GONE);
                mNoResultsFound.setVisibility(View.VISIBLE);
            } else {
                mResultsList.setVisibility(View.VISIBLE);
                mNoResultsFound.setVisibility(View.GONE);
            }

            mResultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    JSONObject result = (JSONObject) view.getTag();

                    mLastSearch = "";

                    setResultPlace(result);
                    finish();
                }
            });
        }

        mRefreshingUi = false;
    }


    private class PlaceSearchReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            mIsSearching = false;

            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_results), error));
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject resultsObject;
            SerializableJSONArray resultsJSON;

            if (isSharedOnApp) {
                resultsObject = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.RESULTS);
            }

            JSONArray results = null;

            if (resultsObject != null) {
                resultsJSON = resultsObject.getJSONArray("results");
                Integer count = resultsObject.getInt("total_results");
                if (count != null) {
                    results = resultsJSON.getJSONArray();
                }
            }

            if (results == null) {
                refreshViewState(false);
                return;
            }

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					Logger.tag(TAG).error(e);
				}
            }

            mResults = resultsArray;

            refreshViewState(false);
        }
    }

    private JSONObject getGlobalResult() {
        try {
            JSONObject globalResult = new JSONObject();
            globalResult.put("display_name", getString(R.string.global));
            globalResult.put("no_place_type", true);
            globalResult.put("id", -1);
            return globalResult;
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

}

