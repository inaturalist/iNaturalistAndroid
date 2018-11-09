package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.Manifest;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class ExploreSearchActivity extends AppCompatActivity {
    public static final String SEARCH_FILTERS = "search_filters";

    private static final int SEARCH_TYPE_TAXON = 0;
    private static final int SEARCH_TYPE_LOCATION = 1;
    private static final int SEARCH_TYPE_NONE = 2;

    private static final int PLACE_SEARCH_HISTORY_SIZE = 10;

    private static ArrayList<JSONObject> DEFAULT_TAXON_RESULTS;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    @State public ExploreSearchFilters mSearchFilters;

    // Current search results
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mResults = null;

    private TaxonSearchReceiver mTaxonResultsReceiver;
    @State public String mLastSearch = "";
    private Handler mHandler;
    private boolean mIsSearching = false;
    private boolean mRefreshingUi = false;
    @State public int mActiveSearchType = SEARCH_TYPE_TAXON;


    private ImageView mTaxonIcon;
    private ImageView mClearTaxon;
    private EditText mTaxonEditText;
    private ImageView mLocationIcon;
    private ImageView mClearLocation;
    private EditText mLocationEditText;
    private ImageButton mSearchButton;
    private ListView mResultsList;
    private ProgressBar mLoadingResults;
    private TextView mNoResultsFound;


    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        getSupportActionBar().hide();

        mHelper = new ActivityHelper(this);

        try {
            DEFAULT_TAXON_RESULTS = new ArrayList<>();
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.plants) + "\", \"iconic_taxon_name\": \"Plantae\", \"id\": 47126 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.mammals) + "\", \"iconic_taxon_name\": \"Mammalia\", \"id\": 40151 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.insects) + "\", \"iconic_taxon_name\": \"Insecta\", \"id\": 47158 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.reptiles) + "\", \"iconic_taxon_name\": \"Reptilia\", \"id\": 26036 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.fish) + "\", \"iconic_taxon_name\": \"Actinopterygii\", \"id\": 47178 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.mollusks) + "\", \"iconic_taxon_name\": \"Mollusca\", \"id\": 47115 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.amphibians) + "\", \"iconic_taxon_name\": \"Amphibia\", \"id\": 20978 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.fungi) + "\", \"iconic_taxon_name\": \"Fungi\", \"id\": 47170 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.birds) + "\", \"iconic_taxon_name\": \"Aves\", \"id\": 3 }"));
            DEFAULT_TAXON_RESULTS.add(new JSONObject("{ \"name\": \"" + getString(R.string.arachnids) + "\", \"iconic_taxon_name\": \"Arachnida\", \"id\": 47119 }"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.explore_search);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            mResults = null;
            mSearchFilters = (ExploreSearchFilters) intent.getSerializableExtra(SEARCH_FILTERS);
            mActiveSearchType = SEARCH_TYPE_TAXON;
        }


        mHandler = new Handler();

        mTaxonIcon = (ImageView) findViewById(R.id.taxon_icon);
        mClearTaxon = (ImageView) findViewById(R.id.clear_taxon);
        mTaxonEditText = (EditText) findViewById(R.id.taxon_edit_text);
        mLocationIcon = (ImageView) findViewById(R.id.location_icon);
        mClearLocation = (ImageView) findViewById(R.id.clear_location);
        mLocationEditText = (EditText) findViewById(R.id.location_edit_text);
        
        mSearchButton = (ImageButton) findViewById(R.id.search_button);
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
                final int searchType;
                if (s == mTaxonEditText.getEditableText()) {
                    searchType = SEARCH_TYPE_TAXON;
                    mTaxonIcon.setImageResource(R.drawable.ic_search_black_24dp);
                    mTaxonIcon.setColorFilter(Color.parseColor("#646464"));
                } else {
                    searchType = SEARCH_TYPE_LOCATION;
                    mLocationEditText.setTextColor(Color.parseColor("#000000"));
                    mLocationIcon.setColorFilter(Color.parseColor("#646464"));
                    mSearchFilters.isCurrentLocation = false;
                }

                (searchType == SEARCH_TYPE_TAXON ? mClearTaxon : mClearLocation).setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);

                mLastSearch = query;

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        performSearch(query, searchType);
                    }
                }, 200);
            }
        };

        mTaxonEditText.addTextChangedListener(textWatcher);
        mLocationEditText.addTextChangedListener(textWatcher);

        TextView.OnEditorActionListener onEditorAction = new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    mSearchButton.performClick();
                    return true;
                }
                return false;
            }
        };

        mTaxonEditText.setOnEditorActionListener(onEditorAction);
        mLocationEditText.setOnEditorActionListener(onEditorAction);

        View.OnClickListener onClear = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText editText;

                if (v == mClearTaxon) {
                    mSearchFilters.taxon = null;
                    editText = mTaxonEditText;
                    mActiveSearchType = SEARCH_TYPE_TAXON;
                } else {
                    mSearchFilters.place = null;
                    mSearchFilters.mapBounds = null;
                    mSearchFilters.isCurrentLocation = false;
                    editText = mLocationEditText;
                    mActiveSearchType = SEARCH_TYPE_LOCATION;
                }

                editText.setText("");
                editText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        editText.requestFocus();
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

                    mActiveSearchType = v == mLocationEditText ? SEARCH_TYPE_LOCATION : SEARCH_TYPE_TAXON;
                    refreshViewState(false);
                }
            }
        };

        mLocationEditText.setOnFocusChangeListener(onFocus);
        mTaxonEditText.setOnFocusChangeListener(onFocus);

        mClearTaxon.setOnClickListener(onClear);
        mClearLocation.setOnClickListener(onClear);


        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check for errors

                if (mLastSearch.length() > 0) {
                    if (mActiveSearchType == SEARCH_TYPE_TAXON) {
                        mHelper.confirm(R.string.no_taxon, R.string.please_select_taxon, R.string.ok_got_it);
                    } else {
                        mHelper.confirm(R.string.sorry_no_location, R.string.location_not_found, R.string.ok_got_it);
                    }
                    return;
                }

                // All checked out - return the search filters
                if (mSearchFilters.taxon != null) {
                    // A Taxon has been chosen - clear out the iconic taxa
                    mSearchFilters.iconicTaxa.clear();
                }

                Intent data = new Intent();
                data.putExtra(SEARCH_FILTERS, mSearchFilters);
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        BaseFragmentActivity.safeUnregisterReceiver(mTaxonResultsReceiver, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mTaxonResultsReceiver = new TaxonSearchReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.SEARCH_TAXA_RESULT);
        filter.addAction(INaturalistService.SEARCH_PLACES_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mTaxonResultsReceiver, filter, this);

        refreshViewState(true);
    }

    private void performSearch(String query, int searchType) {
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
        mActiveSearchType = searchType;

        Intent serviceIntent = new Intent(searchType == SEARCH_TYPE_TAXON ? INaturalistService.ACTION_SEARCH_TAXA : INaturalistService.ACTION_SEARCH_PLACES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.QUERY, query);
        serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, 1);
        ContextCompat.startForegroundService(this, serviceIntent);

        refreshViewState(false);
    }


    private void refreshViewState(boolean refreshSearchFields) {
        mRefreshingUi = true;

        if (refreshSearchFields) {
            // Refresh taxon search field
            if ((mLastSearch.length() > 0) && (mActiveSearchType == SEARCH_TYPE_TAXON)) {
                mTaxonEditText.setText(mLastSearch);
                mTaxonIcon.setImageResource(R.drawable.ic_search_black_24dp);
                mTaxonIcon.setColorFilter(Color.parseColor("#646464"));
                mClearTaxon.setVisibility(View.GONE);
            } else if (mSearchFilters.taxon == null) {
                // No taxon
                mTaxonEditText.setText("");
                mTaxonIcon.setImageResource(R.drawable.ic_search_black_24dp);
                mTaxonIcon.setColorFilter(Color.parseColor("#646464"));
                mClearTaxon.setVisibility(View.GONE);
            } else {
                // Set taxon name + icon
                String taxonName = TaxonUtils.getTaxonName(this, mSearchFilters.taxon);
                mTaxonEditText.setText(taxonName);
                mTaxonEditText.setSelection(0, taxonName.length());
                mTaxonIcon.setColorFilter(null);
                if (mSearchFilters.taxon.has("default_photo") && !mSearchFilters.taxon.isNull("default_photo")) {
                    // Use taxon's default photo
                    Picasso.with(this).
                            load(mSearchFilters.taxon.optJSONObject("default_photo").optString("square_url")).
                            transform(new RoundedCornersTransformation(3, 0)).
                            placeholder(TaxonUtils.observationIcon(mSearchFilters.taxon)).
                            fit().
                            into(mTaxonIcon);
                } else {
                    // No taxon photo - use iconic_taxon
                    Picasso.with(this).load(TaxonUtils.observationIcon(mSearchFilters.taxon)).into(mTaxonIcon);
                }
                mClearTaxon.setVisibility(View.VISIBLE);
            }

            // Refresh location search field
            if ((mLastSearch.length() > 0) && (mActiveSearchType == SEARCH_TYPE_LOCATION)) {
                mLocationEditText.setText(mLastSearch);
                mLocationEditText.setTextColor(Color.parseColor("#000000"));
                mLocationIcon.setColorFilter(Color.parseColor("#646464"));
                mClearLocation.setVisibility(View.GONE);
             } else if (mSearchFilters.isCurrentLocation) {
                // Current location
                mLocationEditText.setText(R.string.my_location);
                mLocationEditText.setSelection(0, getString(R.string.my_location).length());
                mLocationEditText.setTextColor(getResources().getColor(R.color.inatapptheme_color));
                mLocationIcon.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                mClearLocation.setVisibility(View.VISIBLE);
            } else if (mSearchFilters.mapBounds != null) {
                mLocationEditText.setText(R.string.map_area);
                mLocationEditText.setSelection(0, getString(R.string.map_area).length());
                mLocationEditText.setTextColor(getResources().getColor(R.color.inatapptheme_color));
                mLocationIcon.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                mClearLocation.setVisibility(View.VISIBLE);
            } else if (mSearchFilters.place != null) {
                // Set place name
                String placeName = mSearchFilters.place.optString("display_name");
                mLocationEditText.setText(placeName);
                mLocationEditText.setSelection(0, placeName.length());
                mLocationEditText.setTextColor(getResources().getColor(R.color.inatapptheme_color));
                mLocationIcon.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                mClearLocation.setVisibility(View.VISIBLE);
            } else {
                // No location at all (global search)
                mLocationEditText.setText("");
                mLocationIcon.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                mClearLocation.setVisibility(View.GONE);
            }

            if (mActiveSearchType == SEARCH_TYPE_TAXON) {
                mTaxonEditText.requestFocus();
            } else if (mActiveSearchType == SEARCH_TYPE_LOCATION) {
                mLocationEditText.requestFocus();
            } else {
                // Lost focus from all search boxes
                mTaxonEditText.clearFocus();
                mLocationEditText.clearFocus();
                // Hide keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mLocationEditText.getWindowToken(), 0);
            }
        }

        boolean dontShowNoResultsFound = false;

        if (mLastSearch.length() == 0) {
            if (mActiveSearchType == SEARCH_TYPE_TAXON) {
                // Show default taxon results
                mResults = DEFAULT_TAXON_RESULTS;
            } else if (mActiveSearchType == SEARCH_TYPE_LOCATION) {
                // Show location search history
                mResults = loadPlaceSearchHistory();

                if (isLocationPermissionGranted()) {
                    // Always add a "My location" result to the beginning of the search history
                    mResults.add(0, getMyLocationResult());
                }

                if (mResults.size() == 0) {
                    // A special case - no place history results yet, don't show the "no results found" message
                    dontShowNoResultsFound = true;
                }
            } else {
                // User already selected a location - don't show anything in the result list
                dontShowNoResultsFound = true;
                mResults = new ArrayList<>();
            }
        }

        if (mResults == null) {
            mResultsList.setVisibility(View.GONE);
            mLoadingResults.setVisibility(mIsSearching ? View.VISIBLE : View.GONE);
            mNoResultsFound.setVisibility(View.GONE);
        } else {
            mLoadingResults.setVisibility(View.GONE);
            mResultsList.setVisibility(View.VISIBLE);

            if (mActiveSearchType == SEARCH_TYPE_TAXON) {
                mResultsList.setAdapter(new TaxonAdapter(this, mResults));
            } else {
                if ((mResults.size() == 0) && (mLastSearch.trim().equalsIgnoreCase(getString(R.string.my_location)))) {
                    // Special case - user searched for "my location" - add the default my location result
                    mResults.add(getMyLocationResult());
                }

                mResultsList.setAdapter(new PlaceAdapter(this, mResults));
            }

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

                    if (mActiveSearchType == SEARCH_TYPE_TAXON) {
                        mSearchFilters.taxon = result;
                        mActiveSearchType = SEARCH_TYPE_LOCATION;
                    } else {
                        if (result.optBoolean("is_my_location")) {
                            // My location
                            mSearchFilters.place = null;
                            mSearchFilters.isCurrentLocation = true;
                        } else {
                            // A specific place
                            mSearchFilters.place = result;
                            mSearchFilters.isCurrentLocation = false;
                            addPlaceToSearchHistory(result);
                        }

                        mSearchFilters.mapBounds = null;

                        mActiveSearchType = SEARCH_TYPE_NONE;
                    }

                    refreshViewState(true);
                }
            });
        }

        mRefreshingUi = false;
    }

    // Loads the user's recent place search history
    private ArrayList<JSONObject> loadPlaceSearchHistory() {
        SharedPreferences prefs = mApp.getPrefs();
        String searchHistoryJson = prefs.getString("place_search_history", "[]");
        try {
            JSONArray searchHistoryArray = new JSONArray(searchHistoryJson);
            ArrayList<JSONObject> results = new ArrayList<>();
            for (int i = 0; i < searchHistoryArray.length(); i++) {
                results.add(searchHistoryArray.getJSONObject(i));
            }

            return results;
        } catch (JSONException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Adds a place to the user's search history
    private void addPlaceToSearchHistory(JSONObject result) {
        // Add new result at front of the list and remove the last result if the list is too big
        ArrayList<JSONObject> currentResults = loadPlaceSearchHistory();


        // See if the result was already saved in the search history - so we'll
        // re-add it at the end only (so it'll appear only once in the history)
        int indexToRemove = -1;
        for (int i = 0; i < currentResults.size(); i++) {
            JSONObject place = currentResults.get(i);
            if (place.optInt("id", 0) == result.optInt("id", 0)) {
                indexToRemove = i;
                break;
            }
        }

        if (indexToRemove > -1) {
            currentResults.remove(indexToRemove);
        }

        try {
            result.put("is_recent_result", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        currentResults.add(0, result);

        if (currentResults.size() > PLACE_SEARCH_HISTORY_SIZE) {
            currentResults.remove(currentResults.size() - 1);
        }

        JSONArray jsonArray = new JSONArray();
        for (JSONObject place : currentResults) {
            jsonArray.put(place);
        }

        SharedPreferences prefs = mApp.getPrefs();
        prefs.edit().putString("place_search_history", jsonArray.toString()).commit();
    }


    private class TaxonSearchReceiver extends BroadcastReceiver {

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
					e.printStackTrace();
				}
            }

            mResults = resultsArray;

            refreshViewState(false);
        }
    }

    private JSONObject getMyLocationResult() {
        try {
            JSONObject myLocation = new JSONObject();
            myLocation.put("is_my_location", true);
            return myLocation;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isLocationPermissionGranted() {
        return (
                (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                        (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        );
    }

}
