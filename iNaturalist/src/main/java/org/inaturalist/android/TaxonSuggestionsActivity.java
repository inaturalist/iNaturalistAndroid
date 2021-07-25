package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.livefront.bridge.Bridge;
import com.viewpagerindicator.CirclePageIndicator;

import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class TaxonSuggestionsActivity extends AppCompatActivity {
    private static String TAG = "TaxonSuggestionsActivity";

    private static final int FILTERS_REQUEST_CODE = 0x1000;
    public static final String OBS_PHOTO_FILENAME = "obs_photo_filename";
    public static final String OBS_PHOTO_URL = "obs_photo_url";
    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String OBSERVED_ON = "observed_on";
    public static final String OBSERVATION = "observation";
    public static final String OBSERVATION_JSON = "observation_json";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_UUID = "observation_uuid";
    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
    public static final String FROM_SUGGESTION = "from_suggestion";

    private static final int TAXON_SEARCH_REQUEST_CODE = 302;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	@State(AndroidStateBundlers.BetterJSONListBundler.class) public List<BetterJSONObject> mTaxonSuggestions;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mTaxonCommonAncestor;
    private TaxonSuggestionsReceiver mTaxonSuggestionsReceiver;
    @State public String mObsPhotoFilename;
    @State public String mObsPhotoUrl;
    @State public double mLatitude;
    @State public double mLongitude;
    @State public Timestamp mObservedOn;

    private TaxonReceiver mTaxonReceiver;

    private View mBackButton;
    private ViewGroup mSpeciesSearch;
    private TextView mSuggestionsDescription;
    private ListView mSuggestionsList;
    private ProgressBar mLoadingSuggestions;
    private ViewGroup mSuggestionsContainer;
    private TextView mNoNetwork;
    private TextView mCommonAncestorDescription;
    private ListView mCommonAncestorList;
    private ViewPager mPhotosViewPager;
    private CirclePageIndicator mIndicator;

    @State public int mObsId;
    @State public String mObsUUID;
    @State public int mObsIdInternal;
    @State public String mObservationJson;
    @State public int mLastTaxonPosition;
    @State public boolean mShowSuggestionsNotNearBy = false;
    private Button mViewSuggestionsNotNearByButton;
    private PhotosViewPagerAdapter mPhotosAdapter;
    @State public Observation mObservation;
    @State public int mPhotoPosition;
    @State public String mSuggestionSource = INaturalistService.SUGGESTION_SOURCE_VISUAL;
    private ImageView mSuggestionSourceButton;
    private RadioGroup mSuggestionSources;
    private ViewGroup mFiltersButton;
    private List<RadioButton> mSuggestionSourceRadioButtons;
    @State public ExploreSearchFilters mSearchFilters;
    private ViewGroup mActiveFilters;
    private TextView mFilterTaxonName;
    private TextView mFilterPlaceName;
    private ViewGroup mFilterPlaceContainer;
    private View mClearFilters;
    private ViewGroup mFilterTaxonContainer;
    private View mClearFiltersText;
    private View mClearFiltersImage;
    private TextView mSuggestionsBasedOn;
    @State public BetterJSONObject mTaxon;
    @State(AndroidStateBundlers.BetterJSONListBundler.class) public List<BetterJSONObject> mDisplayedSuggestions;
    @State public HashMap<Integer, BetterJSONObject> mTaxonResultsByIndex = new HashMap<>();
    @State public BetterJSONObject mInitialQueryTaxon = null;
    @State public BetterJSONObject mInitialQueryPlace = null;
    @State public String mTopResultsUUID;
    private TopResultsReceiver mTopResultsReceiver;
    @State public ArrayList<String> mUsernames;


    @Override
    protected void onStart()
    {
        super.onStart();


    }

    @Override
    protected void onStop()
    {
        super.onStop();

    }


    private class TopResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.tag(TAG).debug(String.format("TopResultsReceiver %s", intent.getAction()));

            Bundle extras = intent.getExtras();
            String uuid = intent.getStringExtra(INaturalistService.UUID);

            if ((uuid == null) || (mTopResultsUUID == null)) {
                Logger.tag(TAG).debug("Null UUID or latest search UUID");
                return;
            }

            if (!mTopResultsUUID.equals(uuid)) {
                Logger.tag(TAG).debug(String.format("UUID Mismatch %s - %s", uuid, mTopResultsUUID));
                return;
            }

            String error = extras.getString("error");
            if (error != null) {
                return;
            }

            BetterJSONObject resultsObject;
            SerializableJSONArray resultsJSON;

            resultsObject = (BetterJSONObject) mApp.getServiceResult(intent.getAction());

            JSONArray results = null;
            int totalResults = 0;

            if (resultsObject != null) {
                resultsJSON = resultsObject.getJSONArray("results");
                Integer count = resultsObject.getInt("total_results");
                if (count != null) {
                    totalResults = count;
                    results = resultsJSON.getJSONArray();
                }
            }

            if (results == null) {
                return;
            }

            mUsernames = new ArrayList<>();

            for (int i = 0; i < results.length(); i++) {
                try {
                    JSONObject item = results.getJSONObject(i);
                    JSONObject user = item.getJSONObject("user");
                    String name = user.optString("name");
                    if (name == null || name.length() == 0) name = user.optString("login");
                    mUsernames.add(name);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }

            // In case there are less than 3 names
            int size = mUsernames.size();
            for (int i = 3; i > size; i--) {
                mUsernames.add("");
            }

            if ((mDisplayedSuggestions != null) && (mDisplayedSuggestions.size() > 0)) {
                mSuggestionsBasedOn.setVisibility(View.VISIBLE);
                String message = getString(R.string.suggestions_based_on);
                mSuggestionsBasedOn.setText(String.format(message, mUsernames.get(0), mUsernames.get(1), mUsernames.get(2)));
            }
        }
    }

    private class TaxonSuggestionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            BaseFragmentActivity.safeUnregisterReceiver(mTaxonSuggestionsReceiver, TaxonSuggestionsActivity.this);

            String obsUrl = intent.getStringExtra(INaturalistService.OBS_PHOTO_URL);
            String obsFilename = intent.getStringExtra(INaturalistService.OBS_PHOTO_FILENAME);

            if ((obsUrl != null && mObsPhotoUrl != null && !obsUrl.equals(mObsPhotoUrl)) || (obsFilename != null && mObsPhotoFilename != null && !obsFilename.equals(mObsPhotoFilename))) {
                return;
            }

            BetterJSONObject resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_SUGGESTIONS);

            if (resultsObject == null) {
                return;
            }

            mTaxonResultsByIndex.put(mPhotosViewPager.getCurrentItem(), resultsObject);

            loadTaxonSuggestionsFromResultsObject(resultsObject);
        }
    }

    private void loadTaxonSuggestionsFromResultsObject(BetterJSONObject resultsObject) {
        Logger.tag(TAG).debug("Query: " + resultsObject.getJSONObject("query"));
        Logger.tag(TAG).debug("queryTaxon: " + resultsObject.getJSONObject("queryTaxon"));
        Logger.tag(TAG).debug("queryPlace: " + resultsObject.getJSONObject("queryPlace"));

        if ((resultsObject == null) || (!resultsObject.has("results"))) {
            // Connection error
            mNoNetwork.setVisibility(View.VISIBLE);
            mLoadingSuggestions.setVisibility(View.GONE);
            return;
        }


        JSONObject taxon = resultsObject.getJSONObject("queryTaxon");
        if ((mInitialQueryTaxon == null) && (taxon != null)) {
            mInitialQueryTaxon = new BetterJSONObject(taxon);
        }

        if (mSearchFilters.taxon == null) {
            if (taxon != null) {
                mSearchFilters.taxon = taxon;
            }
        }

        JSONObject place = resultsObject.getJSONObject("queryPlace");

        if ((mInitialQueryPlace == null) && (place != null)) {
            mInitialQueryPlace = new BetterJSONObject(place);
        }

        if (mSearchFilters.place == null) {
            if (place != null) {
                mSearchFilters.place = place;
                mSearchFilters.isCurrentLocation = false;
            }
        }

        refreshFilters();

        mTaxonSuggestions = new ArrayList<>();
        mDisplayedSuggestions = new ArrayList<>();

        JSONArray suggestions = resultsObject.getJSONArray("results").getJSONArray();

        for (int i = 0; i < suggestions.length(); i++) {
            mTaxonSuggestions.add(new BetterJSONObject(suggestions.optJSONObject(i)));
        }

        mTaxonCommonAncestor = null;
        if (resultsObject.has("common_ancestor")) {
            JSONObject commonAncestor = resultsObject.getJSONObject("common_ancestor");
            if ((commonAncestor != null) && (commonAncestor.has("taxon"))) {
                mTaxonCommonAncestor = new BetterJSONObject(commonAncestor);
            }
        }

        loadSuggestions();

        // Also get new top identifiers/observers for the top taxa
        String action = (new Random()).nextInt(2) == 0 ? INaturalistService.ACTION_GET_TOP_IDENTIFIERS : INaturalistService.ACTION_GET_TOP_OBSERVERS;
        Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.FILTERS, mSearchFilters);
        serviceIntent.putExtra(INaturalistService.PAGE_SIZE, 3);
        JSONArray taxonIds = new JSONArray();
        for (BetterJSONObject t : mTaxonSuggestions) {
            taxonIds.put(t.getJSONObject("taxon").optInt("id"));
        }
        serviceIntent.putExtra(INaturalistService.TAXON_IDS, new SerializableJSONArray(taxonIds));
        mTopResultsUUID = UUID.randomUUID().toString();
        serviceIntent.putExtra(INaturalistService.UUID, mTopResultsUUID);
        ContextCompat.startForegroundService(this, serviceIntent);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();

        actionBar.hide();

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mObsPhotoFilename = intent.getStringExtra(OBS_PHOTO_FILENAME);
            mObsPhotoUrl = intent.getStringExtra(OBS_PHOTO_URL);
            mLongitude = intent.getDoubleExtra(LONGITUDE, 0);
            mLatitude = intent.getDoubleExtra(LATITUDE, 0);
            mObservedOn = (Timestamp) intent.getSerializableExtra(OBSERVED_ON);
            mObsId = intent.getIntExtra(OBSERVATION_ID, 0);
            mObsIdInternal = intent.getIntExtra(OBSERVATION_ID_INTERNAL, -1);
            mObsUUID = intent.getStringExtra(OBSERVATION_UUID);
            mObservationJson = intent.getStringExtra(OBSERVATION_JSON);
            mObservation = (Observation) intent.getSerializableExtra(OBSERVATION);
            mSearchFilters = new ExploreSearchFilters();
            mSearchFilters.isCurrentLocation = true;
        }

        setContentView(R.layout.taxon_suggestions);

        mSuggestionsBasedOn = (TextView) findViewById(R.id.suggestions_based_on_users);
        mPhotosViewPager = (ViewPager) findViewById(R.id.photos);
        mIndicator = (CirclePageIndicator)findViewById(R.id.photos_indicator);
        mBackButton = findViewById(R.id.back);
        mSpeciesSearch = (ViewGroup) findViewById(R.id.species_search);
        mSuggestionsDescription = (TextView) findViewById(R.id.suggestions_description);
        mSuggestionsList = (ListView) findViewById(R.id.suggestions_list);
        mCommonAncestorDescription = (TextView) findViewById(R.id.common_ancestor_description);
        mCommonAncestorList = (ListView) findViewById(R.id.common_ancestor_list);
        mLoadingSuggestions = (ProgressBar) findViewById(R.id.loading_suggestions);
        mSuggestionsContainer = (ViewGroup) findViewById(R.id.suggestions_container);
        mNoNetwork = (TextView) findViewById(R.id.no_network);
        mViewSuggestionsNotNearByButton = (Button) findViewById(R.id.include_suggestions_not_near_by);
        mSuggestionSourceButton = (ImageView) findViewById(R.id.suggestion_source);
        mFiltersButton = findViewById(R.id.filters);
        mActiveFilters = (ViewGroup) findViewById(R.id.active_filters);
        mFilterTaxonName = (TextView) findViewById(R.id.filter_taxon_name);
        mFilterTaxonContainer = (ViewGroup) findViewById(R.id.filters_taxon_container);
        mFilterPlaceName = (TextView) findViewById(R.id.filter_place_name);
        mFilterPlaceContainer = (ViewGroup) findViewById(R.id.filters_place_container);
        mClearFilters = (View) findViewById(R.id.clear_filters);
        mClearFiltersText = (View) findViewById(R.id.clear_filters_text);
        mClearFiltersImage = (View) findViewById(R.id.clear_filters_image);

        mClearFilters.setOnClickListener(v -> {
            // Clear the filters
            mSearchFilters.place = null;
            mSearchFilters.taxon = null;
            mSearchFilters.isCurrentLocation = true;

            mTaxonResultsByIndex = new HashMap<>(); // Clear results cache
            mTaxonSuggestions = null;
            refreshFilters();
            getTaxonSuggestions();
        });

        View.OnClickListener showFilters = v -> {
            Intent intent2 = new Intent(TaxonSuggestionsActivity.this, ExploreSearchActivity.class);
            intent2.putExtra(ExploreSearchActivity.SEARCH_FILTERS, mSearchFilters);
            intent2.putExtra(ExploreSearchActivity.TAXON_SUGGESTIONS, true);
            intent2.putExtra(ExploreSearchActivity.DISABLE_PLACE, mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_VISUAL));
            startActivityForResult(intent2, FILTERS_REQUEST_CODE);
        };

        mActiveFilters.setOnClickListener(showFilters);
        mFiltersButton.setOnClickListener(showFilters);

        mSuggestionSourceButton.setOnClickListener(v -> {
            showSuggestionSourceDialog();
        });

        mViewSuggestionsNotNearByButton.setOnClickListener(v -> {
            mShowSuggestionsNotNearBy = !mShowSuggestionsNotNearBy;
            loadSuggestions();
        });

        mNoNetwork.setVisibility(View.GONE);

        mLoadingSuggestions.setVisibility(View.VISIBLE);
        mSuggestionsContainer.setVisibility(View.GONE);
        mSuggestionsBasedOn.setVisibility(View.GONE);

        mSpeciesSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TaxonSuggestionsActivity.this, TaxonSearchActivity.class);
                intent.putExtra(TaxonSearchActivity.SPECIES_GUESS, "");
                intent.putExtra(TaxonSearchActivity.SHOW_UNKNOWN, true);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_ID, mObsId);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_ID_INTERNAL, mObsIdInternal);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_UUID, mObsUUID);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_JSON, mObservationJson);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        });

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }

    private void showSuggestionSourceDialog() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup dialogContent = (ViewGroup) inflater.inflate(R.layout.suggestion_source, null, false);

        mSuggestionSources = (RadioGroup) dialogContent.findViewById(R.id.suggestion_sources);

        mSuggestionSourceRadioButtons = new ArrayList<>();

        Pair<RadioButton, ViewGroup> res = createDialogRadioButton(0, R.string.visually_similar, R.string.visually_similar_description);
        mSuggestionSourceRadioButtons.add(res.first);
        mSuggestionSources.addView(res.second);

        res = createDialogRadioButton(1, R.string.research_grade_observations, R.string.research_grade_observations_description);
        mSuggestionSourceRadioButtons.add(res.first);
        mSuggestionSources.addView(res.second);

        mSuggestionSourceRadioButtons.get(mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_VISUAL) ? 0 : 1).setChecked(true);

        mHelper.confirm(getString(R.string.suggestions_source), dialogContent, (dialog, which) -> {
            mSuggestionSource = mSuggestionSourceRadioButtons.get(0).isChecked() ?
                    INaturalistService.SUGGESTION_SOURCE_VISUAL : INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS;
            mTaxonResultsByIndex = new HashMap<>(); // Clear results cache

            refreshSuggestionSource();
            getTaxonSuggestions();
        }, (dialog, which) -> { }, R.string.continue_text, R.string.cancel);
    }

    private Pair<RadioButton, ViewGroup> createDialogRadioButton(int index, @StringRes int titleText, @StringRes int subtitleText) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ViewGroup customNetworkOption = (ViewGroup) inflater.inflate(R.layout.network_option, null, false);

        TextView name = (TextView) customNetworkOption.findViewById(R.id.title);
        TextView subtitle = (TextView) customNetworkOption.findViewById(R.id.sub_title);
        final AppCompatRadioButton radioButton = (AppCompatRadioButton) customNetworkOption.findViewById(R.id.radio_button);

        name.setText(titleText);
        subtitle.setText(subtitleText);

        // Set radio button color
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_checked},
                        new int[]{android.R.attr.state_checked}
                },
                new int[]{
                        Color.DKGRAY, getResources().getColor(R.color.inatapptheme_color)
                }
        );
        radioButton.setSupportButtonTintList(colorStateList);

        customNetworkOption.setOnClickListener(v -> {
            // Uncheck all other radio buttons
            for (int c = 0; c <  mSuggestionSourceRadioButtons.size(); c++) {
                if (c == index) {
                    radioButton.setChecked(true);
                    continue;
                }
                RadioButton r = mSuggestionSourceRadioButtons.get(c);
                r.setChecked(false);
            }
        });

        return new Pair<>(radioButton, customNetworkOption);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mTaxonSuggestionsReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mTopResultsReceiver, this);
    }

    private void getTaxonSuggestions() {
        if (mSearchFilters == null) return;

        // Get taxon suggestions
        BetterJSONObject cachedResults = null;

        if (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS)) {
            // In RG obs source mode - it's the same results for all photos - so we need just one valid result from any photo index
            if (mTaxonResultsByIndex.values().size() > 0) {
                cachedResults = mTaxonResultsByIndex.values().iterator().next();
            }
        } else {
            cachedResults = mTaxonResultsByIndex.get(mPhotosViewPager.getCurrentItem());
        }


        if (cachedResults != null) {
            // Load cached results instead
            loadTaxonSuggestionsFromResultsObject(cachedResults);
            return;
        }

        mTaxonSuggestionsReceiver = new TaxonSuggestionsReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_SUGGESTIONS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mTaxonSuggestionsReceiver, filter, this);

        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_SUGGESTIONS, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBS_PHOTO_FILENAME, mObsPhotoFilename);
        serviceIntent.putExtra(INaturalistService.OBS_PHOTO_URL, mObsPhotoUrl);
        serviceIntent.putExtra(INaturalistService.LONGITUDE, mLongitude);
        serviceIntent.putExtra(INaturalistService.LATITUDE, mLatitude);
        serviceIntent.putExtra(INaturalistService.OBSERVED_ON, mObservedOn);
        serviceIntent.putExtra(INaturalistService.SUGGESTION_SOURCE, mSuggestionSource);

        if (mSearchFilters.taxon != null) {
            serviceIntent.putExtra(INaturalistService.TAXON_ID, mSearchFilters.taxon.optInt("id"));
        }

        if ((mSearchFilters.place != null) && (!mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_VISUAL))) {
            serviceIntent.putExtra(INaturalistService.PLACE_ID, mSearchFilters.place.optInt("id"));
        }

        if (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS)) {
            if ((mSearchFilters.taxon == null) && (mSearchFilters.place == null)) {
                // No filter set by user - use current observation details, if available
                Observation obs = mObservation != null ? mObservation : new Observation(new BetterJSONObject(mObservationJson));

                serviceIntent.putExtra(INaturalistService.PLACE_LAT, obs.latitude);
                serviceIntent.putExtra(INaturalistService.PLACE_LNG, obs.longitude);

                Integer taxonId = null;

                if ((obs.rank_level != null) && (obs.rank_level <= 10) && (mTaxon != null)) {
                    // Observation taxon is species or below - use the parent taxon ID instead
                    JSONArray ancestors = mTaxon.getJSONArray("ancestor_ids").getJSONArray();
                    int ancestorsCount = ancestors.length();
                    taxonId = ancestors.optInt(ancestorsCount > 1 ? ancestorsCount - 1 : 0);
                } else {
                    taxonId = obs.taxon_id;
                }

                serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
            }
        }

        ContextCompat.startForegroundService(this, serviceIntent);

        mLoadingSuggestions.setVisibility(View.VISIBLE);
        mSuggestionsContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mTaxonSuggestions == null) {
            getTaxonSuggestions();
        } else {
            loadSuggestions();
        }

        if (mObservationJson == null) {
            return;
        }

        mPhotosAdapter = new PhotosViewPagerAdapter(this, new Observation(new BetterJSONObject(mObservationJson)), mObservationJson);
        mPhotosViewPager.setAdapter(mPhotosAdapter);
        mIndicator.setViewPager(mPhotosViewPager);
        mPhotosViewPager.setCurrentItem(mPhotoPosition);
        mPhotosViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (position == mPhotoPosition) return;

                // Get taxon suggestions for new photo
                mPhotoPosition = position;

                if (mObservation != null) {
                    Cursor cursor = null;

                    if (mObservation.uuid != null) {
                        cursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                                ObservationPhoto.PROJECTION,
                                "(observation_uuid=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                                new String[]{mObservation.uuid},
                                ObservationPhoto.DEFAULT_SORT_ORDER);
                    } else {
                        cursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                                ObservationPhoto.PROJECTION,
                                "(observation_id=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                                new String[]{String.valueOf(mObservation.id)},
                                ObservationPhoto.DEFAULT_SORT_ORDER);
                    }

                    cursor.moveToPosition(position);
                    mObsPhotoFilename = cursor.getString(cursor.getColumnIndex(ObservationPhoto.PHOTO_FILENAME));
                    mObsPhotoUrl = cursor.getString(cursor.getColumnIndex(ObservationPhoto.PHOTO_URL));
                    cursor.close();
                } else {
                    // External (not our own) observation
                    Observation obs = new Observation(new BetterJSONObject(mObservationJson));
                    mObsPhotoFilename = null;
                    mObsPhotoUrl = obs.photos.get(position).photo_url;
                }

                mTaxonSuggestions = null;
                getTaxonSuggestions();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        if (mPhotosAdapter.getCount() <= 1) {
            mIndicator.setVisibility(View.GONE);
        } else {
            mIndicator.setVisibility(View.VISIBLE);
        }

        refreshSuggestionSource();
        refreshFilters();

        if (mTaxon == null) {
            Integer taxonId = null;

            if (mObservation != null) {
                taxonId = mObservation.taxon_id;
            } else {
                BetterJSONObject obs = new BetterJSONObject(mObservationJson);
                JSONObject taxon = obs.getJSONObject("taxon");
                if (taxon != null) {
                    taxonId = taxon.optInt("id");
                }
            }

            if (taxonId != null) {
                // Taxon info not loaded - download it now
                mTaxonReceiver = new TaxonReceiver();
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_NEW_RESULT);
                Logger.tag(TAG).info("Registering ACTION_GET_TAXON_NEW_RESULT");
                BaseFragmentActivity.safeRegisterReceiver(mTaxonReceiver, filter, this);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_NEW, null, this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
                ContextCompat.startForegroundService(this, serviceIntent);
            }
        }

        mTopResultsReceiver = new TopResultsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.GET_TOP_IDENTIFIERS_RESULT);
        filter.addAction(INaturalistService.GET_TOP_OBSERVERS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mTopResultsReceiver, filter, this);

    }

    private void refreshFilters() {
        boolean filtersOn = mSearchFilters.place != null || mSearchFilters.taxon != null;

        mActiveFilters.setVisibility(filtersOn ? View.VISIBLE : View.GONE);
        mFiltersButton.setVisibility(filtersOn ? View.GONE : View.VISIBLE);

        if ((mSearchFilters.place != null) && (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS))) {
            mFilterPlaceContainer.setVisibility(View.VISIBLE);
            mFilterPlaceName.setText(mSearchFilters.place.optString("name"));
        } else {
            mFilterPlaceContainer.setVisibility(View.GONE);
        }

        if (mSearchFilters.taxon != null) {
            mFilterTaxonContainer.setVisibility(View.VISIBLE);

            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name first, before common name
                TaxonUtils.setTaxonScientificName(mApp, mFilterTaxonName, mSearchFilters.taxon, true);
            } else {
                mFilterTaxonName.setText(TaxonUtils.getTaxonName(this, mSearchFilters.taxon));
            }

        } else {
            mFilterTaxonContainer.setVisibility(View.GONE);
        }

        if (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS)) {
            // Reset only
            mClearFiltersImage.setVisibility(View.GONE);

            // Show reset button only if default filter was changed
            mClearFiltersText.setVisibility(View.VISIBLE);

            if (
                    ((mSearchFilters.taxon != null) && (mInitialQueryTaxon != null) && (mInitialQueryTaxon.getInt("id").equals(mSearchFilters.taxon.optInt("id")))) &&
                    ((mSearchFilters.place != null) && (mInitialQueryPlace != null) && (mInitialQueryPlace.getInt("id").equals(mSearchFilters.place.optInt("id"))))
            ) {
                mClearFilters.setVisibility(View.GONE);
            } else {
                mClearFilters.setVisibility(View.VISIBLE);
            }
        } else {
            // Clear only
            mClearFiltersImage.setVisibility(View.VISIBLE);
            mClearFiltersText.setVisibility(View.GONE);
        }

        if (mUsernames != null) {
            mSuggestionsBasedOn.setVisibility(View.VISIBLE);
            String message = getString(R.string.suggestions_based_on);
            mSuggestionsBasedOn.setText(String.format(message, mUsernames.get(0), mUsernames.get(1), mUsernames.get(2)));
        } else {
            mSuggestionsBasedOn.setVisibility(View.GONE);
        }
    }

    private void loadSuggestions() {
        mLoadingSuggestions.setVisibility(View.GONE);
        mSuggestionsContainer.setVisibility(View.VISIBLE);

        TaxonSuggestionAdapter.OnTaxonSuggestion onSuggestion = new TaxonSuggestionAdapter.OnTaxonSuggestion() {
            @Override
            public void onTaxonSelected(int position, JSONObject taxon) {
                // Taxon selected - return that taxon back
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putString(TaxonSearchActivity.ID_NAME, TaxonUtils.getTaxonName(TaxonSuggestionsActivity.this, taxon));
                bundle.putString(TaxonSearchActivity.TAXON_NAME, taxon.optString("name"));
                bundle.putString(TaxonSearchActivity.ICONIC_TAXON_NAME, taxon.optString("iconic_taxon_name"));
                if (taxon.has("default_photo") && !taxon.isNull("default_photo")) bundle.putString(TaxonSearchActivity.ID_PIC_URL, taxon.optJSONObject("default_photo").optString("square_url"));
                bundle.putBoolean(TaxonSearchActivity.IS_CUSTOM, false);
                bundle.putInt(TaxonSearchActivity.TAXON_ID, taxon.optInt("id"));
                bundle.putInt(TaxonSearchActivity.RANK_LEVEL, taxon.optInt("rank_level"));
                bundle.putBoolean(TaxonSuggestionsActivity.FROM_SUGGESTION, true);

                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onTaxonDetails(int position, JSONObject taxon) {
                // Show taxon details screen
                mLastTaxonPosition = position;
                Intent intent = new Intent(TaxonSuggestionsActivity.this, TaxonActivity.class);
                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(taxon));
                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                intent.putExtra(TaxonActivity.TAXON_SUGGESTION, TaxonActivity.TAXON_SUGGESTION_COMPARE_AND_SELECT);
                if (mObservationJson != null) {
                    intent.putExtra(TaxonActivity.OBSERVATION, new BetterJSONObject(mObservationJson));
                }
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }

            @Override
            public void onTaxonCompared(int position, JSONObject taxon) {
                // Show taxon comparison screen
                showTaxonComparison(position);
            }
        };

        if (mTaxonCommonAncestor == null) {
            // No common ancestor
            if (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS)) {
                mSuggestionsDescription.setText(R.string.most_observed_options_matching_filters);
            } else {
                mSuggestionsDescription.setText(R.string.were_not_confident_enough);
            }
            mCommonAncestorDescription.setVisibility(View.GONE);
            mCommonAncestorList.setVisibility(View.GONE);
        } else {
            // Show common ancestor
            mSuggestionsDescription.setText(R.string.top_suggestions);
            List<BetterJSONObject> commonAncestor = new ArrayList<>();
            commonAncestor.add(mTaxonCommonAncestor);
            mCommonAncestorList.setAdapter(new TaxonSuggestionAdapter(this, commonAncestor, onSuggestion, false));

            String rank = mTaxonCommonAncestor.getJSONObject("taxon").optString("rank");
            String translatedText = TaxonUtils.getStringWithRank(mApp, rank, "pretty_sure_");
            if (translatedText == null) {
                // No translation found - use default fallback
                translatedText = String.format(getString(R.string.pretty_sure_rank), TaxonUtils.getTranslatedRank(mApp, rank));
            }
            mCommonAncestorDescription.setText(translatedText);
            mCommonAncestorDescription.setVisibility(View.VISIBLE);
            mCommonAncestorList.setVisibility(View.VISIBLE);
        }

        List<BetterJSONObject> sortedSuggestions = new ArrayList<>(mTaxonSuggestions);
        Logger.tag(TAG).info("Before: ");
        for (BetterJSONObject s : sortedSuggestions) {
            Logger.tag(TAG).info(String.format("%s: vision: %f; frequency: %f; combined: %f", s.getJSONObject("taxon").optString("name"), s.getJSONObject("source_details").optDouble("vision_score"), s.getJSONObject("source_details").optDouble("frequency_score"), s.getJSONObject("source_details").optDouble("combined_score")));
        }

        mViewSuggestionsNotNearByButton.setVisibility(View.VISIBLE);

        if (mShowSuggestionsNotNearBy) {
            // Sort by vision_score
            CollectionUtils.filter(sortedSuggestions, suggestion -> suggestion.getJSONObject("source_details").optDouble("vision_score") > 0);
            Collections.sort(sortedSuggestions, (s1, s2) -> Double.compare(s2.getJSONObject("source_details").optDouble("vision_score"), s1.getJSONObject("source_details").optDouble("vision_score")));

            mViewSuggestionsNotNearByButton.setText(R.string.only_view_nearby_suggestions);
        } else {
            // Show only results both vision_score and frequency_score, then sort by combined_score
            CollectionUtils.filter(sortedSuggestions, suggestion -> (suggestion.getJSONObject("source_details").optDouble("frequency_score") > 0) && (suggestion.getJSONObject("source_details").optDouble("vision_score") > 0) );
            if (sortedSuggestions.size() == 0) {
                // Special case - no nearby results
                sortedSuggestions = new ArrayList<>(mTaxonSuggestions);
                Collections.sort(sortedSuggestions, (s1, s2) -> Double.compare(s2.getJSONObject("source_details").optDouble("vision_score"), s1.getJSONObject("source_details").optDouble("vision_score")));
                mViewSuggestionsNotNearByButton.setVisibility(View.GONE);
            } else {
                Collections.sort(sortedSuggestions, (s1, s2) -> Double.compare(s2.getJSONObject("source_details").optDouble("combined_score"), s1.getJSONObject("source_details").optDouble("combined_score")));
            }

            mViewSuggestionsNotNearByButton.setText(R.string.include_suggestions_not_seen_nearby);
        }

        Logger.tag(TAG).info("After: ");
        for (BetterJSONObject s : sortedSuggestions) {
            Logger.tag(TAG).info(String.format("%s: vision: %f; frequency: %f; combined: %f", s.getJSONObject("taxon").optString("name"), s.getJSONObject("source_details").optDouble("vision_score"), s.getJSONObject("source_details").optDouble("frequency_score"), s.getJSONObject("source_details").optDouble("combined_score")));
        }

        mSuggestionsList.setAdapter(new TaxonSuggestionAdapter(this, sortedSuggestions, onSuggestion, true));
        mDisplayedSuggestions = sortedSuggestions;

        if (sortedSuggestions.size() == 0) {
            // No results at all
            mSuggestionsDescription.setText(R.string.were_not_confident_enough_no_suggestions);
            mFiltersButton.setVisibility(View.GONE);
            mViewSuggestionsNotNearByButton.setVisibility(View.GONE);
        } else {
            mFiltersButton.setVisibility(View.VISIBLE);
            mViewSuggestionsNotNearByButton.setVisibility(View.VISIBLE);
        }

        resizeSuggestionsList();

        mSuggestionsBasedOn.setVisibility(View.GONE);
    }

    private void refreshSuggestionSource() {
        if (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_VISUAL)) {
            mSuggestionSourceButton.setImageResource(R.drawable.ic_empty_binoculars);
        } else if (mSuggestionSource.equals(INaturalistService.SUGGESTION_SOURCE_RESEARCH_GRADE_OBS)) {
            mSuggestionSourceButton.setImageResource(R.drawable.id_rg);
        }
    }

    private void showTaxonComparison(int position) {
        if (mTaxonSuggestions == null) return;

        Intent intent = new Intent(TaxonSuggestionsActivity.this, CompareSuggestionActivity.class);
        intent.putExtra(CompareSuggestionActivity.SUGGESTION_INDEX, position);
        if (mObservationJson != null) intent.putExtra(CompareSuggestionActivity.OBSERVATION_JSON, mObservationJson);
        if (mObsIdInternal > -1) intent.putExtra(CompareSuggestionActivity.OBSERVATION_ID_INTERNAL, mObsIdInternal);
        if (mObsId > -1) intent.putExtra(CompareSuggestionActivity.OBSERVATION_ID, mObsId);
        if (mObsUUID != null) intent.putExtra(CompareSuggestionActivity.OBSERVATION_UUID, mObsUUID);
        CompareSuggestionActivity.setTaxonSuggestions(mDisplayedSuggestions);
        startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon search directly back to the caller (e.g. observation editor)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                bundle.putBoolean(TaxonSuggestionsActivity.FROM_SUGGESTION, false);
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            } else if (resultCode == TaxonActivity.RESULT_COMPARE_TAXON) {
                // User chose to compare this specific taxon
                showTaxonComparison(mLastTaxonPosition);
            }
        } else if (requestCode == FILTERS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Update search filters and refresh results
                mSearchFilters = (ExploreSearchFilters) data.getSerializableExtra(ExploreSearchActivity.SEARCH_FILTERS);

                mTaxonResultsByIndex = new HashMap<>(); // Clear results cache
                mTaxonSuggestions = null;
                refreshFilters();
                getTaxonSuggestions();
            }
        }
    }

    private void resizeSuggestionsList() {
        final Handler handler = new Handler();
        if ((mSuggestionsList.getVisibility() == View.VISIBLE) && (mSuggestionsList.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeSuggestionsList();
                }
            }, 100);

            return;
        }

        mSuggestionsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                int height = setListViewHeightBasedOnItems(mSuggestionsList);
            }
        });
    }

    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param listView to be resized
     */
    public int setListViewHeightBasedOnItems(final ListView listView) {
    	ListAdapter listAdapter = listView.getAdapter();
    	if (listAdapter != null) {

            int numberOfItems = listAdapter.getCount();

            // Get total height of all items.
            int totalItemsHeight = 0;
            for (int itemPos = 0; itemPos < numberOfItems; itemPos++) {
                View item = listAdapter.getView(itemPos, null, listView);
                item.measure(MeasureSpec.makeMeasureSpec(listView.getWidth(), MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                totalItemsHeight += item.getMeasuredHeight();
            }

            // Get total height of all item dividers.
            int totalDividersHeight = listView.getDividerHeight() *
                    (numberOfItems - 1);

            // Set list height.
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            int paddingHeight = (int) getResources().getDimension(R.dimen.actionbar_height);
            int newHeight = totalItemsHeight + totalDividersHeight;
            if (params.height != newHeight) {
                params.height = totalItemsHeight + totalDividersHeight;
                listView.setLayoutParams(params);
                listView.requestLayout();
            }

    		return params.height;

    	} else {
    		return 0;
    	}
    }

    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, TaxonSuggestionsActivity.this);

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject taxon;

            if (isSharedOnApp) {
                taxon = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                taxon = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_RESULT);
            }

            if (taxon == null) return;

            mTaxon = taxon;
        }
    }
}

