package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.Manifest;
import android.support.v4.content.PermissionChecker;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.data.Layer;
import com.google.maps.android.data.geojson.GeoJsonFeature;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonPolygon;
import com.livefront.bridge.Bridge;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ExploreActivity extends BaseFragmentActivity {
    private static final int NOT_LOADED = -1;

    private static final int VIEW_OBSERVATION_REQUEST_CODE = 0x100;
    private static final int SEARCH_REQUEST_CODE = 0x101;
    private static final int FILTERS_REQUEST_CODE = 0x102;

    public static final String SEARCH_FILTERS = "search_filters";
    public static final String ACTIVE_TAB = "active_tab";

    private static final float MY_LOCATION_ZOOM_LEVEL = 10;
    private static final String TAG = "ExploreActivity";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    public static final int VIEW_TYPE_OBSERVATIONS = 0;
    public static final int VIEW_TYPE_SPECIES = 1;
    public static final int VIEW_TYPE_OBSERVERS = 2;
    public static final int VIEW_TYPE_IDENTIFIERS = 3;

    @State public int mActiveViewType;

    @State public int[] mTotalResults = {NOT_LOADED, NOT_LOADED, NOT_LOADED, NOT_LOADED};

    @State public ExploreSearchFilters mSearchFilters;

    @State public VisibleRegion mMapRegion;

    @State(AndroidStateBundlers.ListBundler.class) public List<Integer> mListViewIndex = new ArrayList<>(Arrays.asList(0 ,0, 0, 0));
    @State(AndroidStateBundlers.ListBundler.class) public List<Integer> mListViewOffset = new ArrayList<>(Arrays.asList(0 ,0, 0, 0));

    // Current search results
    private List<JSONObject>[] mResults = (List<JSONObject>[]) new List[]{null, null, null, null};

    private ExploreResultsReceiver mExploreResultsReceiver;
    private LocationReceiver mLocationReceiver;

    private ProgressBar mLoadingObservationsGrid;
    private TextView mObservationsGridEmpty;
    private GridViewExtended mObservationsGrid;
    private ObservationGridAdapter mGridAdapter;
    private GoogleMap mObservationsMap;
    private ViewGroup mObservationsMapContainer;
    private View mMapHide;
    private TextView mObservationsGridFilterBar;
    private TextView mObservationsMapFilterBar;

    private ImageView mObservationsViewModeGrid;
    private ImageView mObservationsViewModeMap;
    private ImageView mObservationsChangeMapLayers;
    private ImageView mObservationsMapMyLocation;
    private ViewGroup mRedoObservationsSearch;
    private ProgressBar mPerformingSearch;
    private ImageView mRedoObservationSearchIcon;
    private TextView mRedoObservationsSearchText;

    private ListView[] mList = new ListView[]{null, null, null, null};
    private ArrayAdapter[] mListAdapter = new ArrayAdapter[]{null, null, null, null};
    private ProgressBar[] mLoadingList = new ProgressBar[]{null, null, null, null};
    private TextView[] mListEmpty = new TextView[]{null, null, null, null};
    private ViewGroup[] mListHeader = new ViewGroup[]{null, null, null, null};
    private TextView[] mFilterBar = new TextView[]{null, null, null, null};
    private ViewGroup[] mLoadingMoreResults = new ViewGroup[]{null, null, null, null};

    @State(AndroidStateBundlers.JSONListBundler.class) public List<JSONObject> mObservations;
    @State(AndroidStateBundlers.JSONListBundler.class) public List<JSONObject> mSpecies;
    @State(AndroidStateBundlers.JSONListBundler.class) public List<JSONObject> mObservers;
    @State(AndroidStateBundlers.JSONListBundler.class) public List<JSONObject> mIdentifiers;


    private static final int OBSERVATIONS_VIEW_MODE_GRID = 0;
    private static final int OBSERVATIONS_VIEW_MODE_MAP = 1;
    @State public int mObservationsViewMode = OBSERVATIONS_VIEW_MODE_GRID;


    @State public int[] mCurrentResultsPage = {0, 0, 0, 0};
    @State public boolean[] mLoadingNextResults = {false, false, false, false};
    @State public int mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
    private LatLngBounds mLastMapBounds = null;
    @State public boolean mMapMoved = false;
    private LatLngBounds mInitialLocationBounds;
    private int[] mLastTotalResults = {NOT_LOADED, NOT_LOADED, NOT_LOADED, NOT_LOADED};
    private boolean mMapReady = false;
    private boolean mShouldMoveMapAccordingToSearchFilters = false;
    @State public boolean mLocationPermissionRequested = false;
    @State public SerializableJSONArray mAllAnnotations;
    private AnnotationsReceiver mAnnotationsReceiver;

    @Override
    protected void onStart() {
        super.onStart();


    }

    @Override
    protected void onStop() {
        super.onStop();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.explore_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.search:
                intent = new Intent(ExploreActivity.this, ExploreSearchActivity.class);
                intent.putExtra(ExploreSearchActivity.SEARCH_FILTERS, mSearchFilters);
                startActivityForResult(intent, SEARCH_REQUEST_CODE);

                return true;

            case R.id.filters:
                intent = new Intent(ExploreActivity.this, ExploreFiltersActivity.class);
                intent.putExtra(ExploreFiltersActivity.SEARCH_FILTERS, mSearchFilters);
                intent.putExtra(ExploreFiltersActivity.ALL_ANNOTATIONS, mAllAnnotations);
                startActivityForResult(intent, FILTERS_REQUEST_CODE);

                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setElevation(0);

        actionBar.setCustomView(R.layout.explore_action_bar_new);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.getCustomView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ExploreActivity.this, ExploreSearchActivity.class);
                intent.putExtra(ExploreSearchActivity.SEARCH_FILTERS, mSearchFilters);
                startActivityForResult(intent, SEARCH_REQUEST_CODE);
            }
        });

        mHelper = new ActivityHelper(this);
        mApp = (INaturalistApp) getApplicationContext();

        setContentView(R.layout.explore);

        final Intent intent = getIntent();

        if (savedInstanceState == null) {
            mActiveViewType = VIEW_TYPE_OBSERVATIONS;

            mTotalResults = new int[]{NOT_LOADED, NOT_LOADED, NOT_LOADED, NOT_LOADED};
            mResults = (List<JSONObject>[]) new List[]{null, null, null, null};

            mLastMapBounds = null;

            if (intent.hasExtra(SEARCH_FILTERS)) {
                mSearchFilters = (ExploreSearchFilters) intent.getSerializableExtra(SEARCH_FILTERS);
            } else {
                mSearchFilters = new ExploreSearchFilters();
                mSearchFilters.isCurrentLocation = true;
            }


            if (intent.hasExtra(ACTIVE_TAB)) {
                mActiveViewType = intent.getIntExtra(ACTIVE_TAB, VIEW_TYPE_OBSERVATIONS);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mViewPager.setCurrentItem(mActiveViewType);
                    }
                }, 100);
            }

        } else {
            mResults = (List<JSONObject>[]) new List[]{null, null, null, null};
            mResults[VIEW_TYPE_OBSERVATIONS] = mObservations;
            mResults[VIEW_TYPE_SPECIES] = mSpecies;
            mResults[VIEW_TYPE_OBSERVERS] = mObservers;
            mResults[VIEW_TYPE_IDENTIFIERS] = mIdentifiers;

            VisibleRegion vr = mMapRegion;
            if (vr != null) {
                mLastMapBounds = new LatLngBounds(new LatLng(vr.nearLeft.latitude, vr.farLeft.longitude), new LatLng(vr.farRight.latitude, vr.farRight.longitude));
            } else {
                mLastMapBounds = null;
            }
        }

        onDrawerCreate(savedInstanceState);

        // Tab Initialization
        initializeTabs();


        if (!mApp.isLocationPermissionGranted()) {
            if (!mLocationPermissionRequested) {
                mLocationPermissionRequested = true;

                mApp.requestLocationPermission(ExploreActivity.this, new INaturalistApp.OnRequestPermissionResult() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onPermissionGranted() {
                        mLoadingNextResults = new boolean[]{false, false, false, false};
                        mObservationsMapMyLocation.setVisibility(View.VISIBLE);
                        mObservationsMapMyLocation.performClick();

                        if (mObservationsMap != null) {
                            mObservationsMap.setMyLocationEnabled(true);
                        }
                    }

                    @Override
                    public void onPermissionDenied() {

                    }
                });

            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // To handle memory issues - don't save any results that are not part of the current active tab
        for (int i = 0; i < mResults.length; i++) {
            if (mActiveViewType != i) {
                mResults[i] = null;
            }
        }

        mObservations = mResults[VIEW_TYPE_OBSERVATIONS];
        mSpecies = mResults[VIEW_TYPE_SPECIES];
        mObservers = mResults[VIEW_TYPE_OBSERVERS];
        mIdentifiers = mResults[VIEW_TYPE_IDENTIFIERS];

        saveListViewOffset(mObservationsGrid, VIEW_TYPE_OBSERVATIONS);
        saveListViewOffset(mList[VIEW_TYPE_SPECIES], VIEW_TYPE_SPECIES);
        saveListViewOffset(mList[VIEW_TYPE_OBSERVERS], VIEW_TYPE_OBSERVERS);
        saveListViewOffset(mList[VIEW_TYPE_IDENTIFIERS], VIEW_TYPE_IDENTIFIERS);

        if (mObservationsMap != null) {
            mMapRegion = mObservationsMap.getProjection().getVisibleRegion();
        }

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    private void loadListViewOffset(final AbsListView listView, int listIndex) {
        if (listView == null) return;

        Integer index, offset;

        index = mListViewIndex.get(listIndex);
        offset = mListViewOffset.get(listIndex);

        final Integer finalIndex = index, finalOffset = offset;
        listView.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (listView instanceof GridView) {
                        // Weird Android issue - if it's a grid view, setting the offset will reset the
                        // row number to zero (so we can only set the row number, but not the offset)
                        listView.setSelection(finalIndex);
                    } else {
                        listView.setSelectionFromTop(finalIndex, finalOffset);
                    }
                } else {
                    listView.setSelection(finalIndex);
                }
            }
        });

    }

    private void saveListViewOffset(AbsListView listView, int listIndex) {
        if (listView != null) {
            View firstVisibleRow = listView.getChildAt(0);

            if (firstVisibleRow != null) {
                Integer offset = firstVisibleRow.getTop() - listView.getPaddingTop();
                Integer index = listView.getFirstVisiblePosition();

                mListViewIndex.set(listIndex, index);
                mListViewOffset.set(listIndex, offset);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        BaseFragmentActivity.safeUnregisterReceiver(mExploreResultsReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mLocationReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mAnnotationsReceiver, this);

        mLoadingNextResults = new boolean[]{false, false, false, false};
    }

    @Override
    public void onResume() {
        super.onResume();

        mExploreResultsReceiver = new ExploreResultsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.EXPLORE_GET_OBSERVATIONS_RESULT);
        filter.addAction(INaturalistService.EXPLORE_GET_SPECIES_RESULT);
        filter.addAction(INaturalistService.EXPLORE_GET_IDENTIFIERS_RESULT);
        filter.addAction(INaturalistService.EXPLORE_GET_OBSERVERS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mExploreResultsReceiver, filter, this);

        mLocationReceiver = new LocationReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(INaturalistService.GET_CURRENT_LOCATION_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mLocationReceiver, filter2, this);

        mAnnotationsReceiver = new AnnotationsReceiver();
        IntentFilter filter3 = new IntentFilter();
        filter3.addAction(INaturalistService.GET_ALL_ATTRIBUTES_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mAnnotationsReceiver, filter3, this);

        if (mAllAnnotations == null) {
            Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_ALL_ATTRIBUTES, null, ExploreActivity.this, INaturalistService.class);
            ContextCompat.startForegroundService(this, serviceIntent2);
        }


        refreshViewState();

        if (!((mApp.isLocationPermissionGranted() && (mSearchFilters != null) && (mSearchFilters.isCurrentLocation) && (mLastMapBounds == null)))) {
            // When the activity is paused, we only save the results of the current tab (to conserve memory).
            // In this part we load the results of the rest of the tabs, if not already in the process of loading.
            for (int i = 0; i < mResults.length; i++) {
                if ((!mLoadingNextResults[i]) && (mResults[i] == null)) {
                    loadNextResultsPage(i, true);
                }
            }
        }
    }

    // Method to add a TabHost
    private void addTab(int position, View tabContent) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);
        tab.setCustomView(tabContent);
    }


    // Tabs Creation
    private void initializeTabs() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        mViewPager.setOffscreenPageLimit(3); // So we wouldn't have to recreate the views every time
        ExplorePagerAdapter adapter = new ExplorePagerAdapter(this);
        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);

        addTab(0, createTabContent(mApp.getStringResourceByName("observations_all_caps", "project_observations"), 1000));
        addTab(1, createTabContent(mApp.getStringResourceByName("species_all_caps", "project_species"), 2000));
        addTab(2, createTabContent(mApp.getStringResourceByName("observers_all_caps", "observers"), 3000));
        addTab(3, createTabContent(mApp.getStringResourceByName("identifiers_all_caps", "project_identifiers"), 4000));

        TabLayout.OnTabSelectedListener tabListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView tabNameText = (TextView) tab.getCustomView().findViewById(R.id.tab_name);

                tabNameText.setTypeface(null, Typeface.BOLD);
                tabNameText.setTextColor(Color.parseColor("#000000"));

                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View tabView = tab.getCustomView();
                TextView tabNameText = (TextView) tabView.findViewById(R.id.tab_name);

                tabNameText.setTypeface(null, Typeface.NORMAL);
                tabNameText.setTextColor(Color.parseColor("#ACACAC"));
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        };
        mTabLayout.setOnTabSelectedListener(tabListener);

        ViewPager.OnPageChangeListener pageListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mActiveViewType = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mViewPager.addOnPageChangeListener(pageListener);

        int width;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTabLayout.setTabMode(TabLayout.MODE_FIXED);

            width = ViewGroup.LayoutParams.MATCH_PARENT;

        } else {
            mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Point size = new Point();
                getWindowManager().getDefaultDisplay().getSize(size);
                width = size.x;
            } else {
                width = getWindowManager().getDefaultDisplay().getWidth();
            }
            width = (int) (width * 0.270);
        }

        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            ViewGroup view = (ViewGroup) mTabLayout.getTabAt(i).getCustomView();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(params);
        }

    }

    private View createTabContent(String tabName, int count) {
        ViewGroup view = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.explore_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        TextView tabNameText = (TextView) view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        countText.setVisibility(View.INVISIBLE);

        int width;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            width = size.x;
        } else {
            width = getWindowManager().getDefaultDisplay().getWidth();
        }
        width = (int) (width * 0.270);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);

        return view;
    }

    private class LocationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            Location location = extras.getParcelable(INaturalistService.LOCATION);

            if ((location == null) || (mObservationsMap == null)) {
                return;
            }

            // If it's the first time we're zooming in - this means we'll force a new search for nearby results
            final boolean shouldRedoSearch = mLastMapBounds == null;

            if (mObservationsMapContainer.getVisibility() != View.VISIBLE) {
                // We're in grid view (instead of map view) - we need to perform this "hack" to make the animateCamera method actually work
                mObservationsMapContainer.setVisibility(View.VISIBLE);
                mMapHide.setVisibility(View.VISIBLE);
            }

            mObservationsMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), MY_LOCATION_ZOOM_LEVEL),
                    1000,
                    new GoogleMap.CancelableCallback() {
                        @Override
                        public void onFinish() {
                            mInitialLocationBounds = mObservationsMap.getProjection().getVisibleRegion().latLngBounds;
                            mObservationsMapContainer.setVisibility(View.GONE);

                            if (shouldRedoSearch) {
                                mLastMapBounds = mInitialLocationBounds;
                                mRedoObservationsSearch.performClick();
                            }

                            mObservationsMapMyLocation.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
        }
    }

    private class AnnotationsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

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
                return;
            }

            mAllAnnotations = new SerializableJSONArray(results);
        }
    }

    private class ExploreResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            int index = 0;
            if (intent.getAction().equals(INaturalistService.EXPLORE_GET_OBSERVATIONS_RESULT)) {
                index = VIEW_TYPE_OBSERVATIONS;
            } else if (intent.getAction().equals(INaturalistService.EXPLORE_GET_SPECIES_RESULT)) {
                index = VIEW_TYPE_SPECIES;
            } else if (intent.getAction().equals(INaturalistService.EXPLORE_GET_IDENTIFIERS_RESULT)) {
                index = VIEW_TYPE_IDENTIFIERS;
            } else if (intent.getAction().equals(INaturalistService.EXPLORE_GET_OBSERVERS_RESULT)) {
                index = VIEW_TYPE_OBSERVERS;
            }

            mLoadingNextResults[index] = false;
            mLoadingMoreResults[index].setVisibility(View.GONE);

            if (index == VIEW_TYPE_OBSERVATIONS) mMapMoved = false;

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
            int totalResults = 0;

            if (resultsObject != null) {
                resultsJSON = resultsObject.getJSONArray("results");
                Integer count = resultsObject.getInt("total_results");
                mCurrentResultsPage[index] = resultsObject.getInt("page");
                if (count != null) {
                    totalResults = count;
                    results = resultsJSON.getJSONArray();
                }
            }

            if (results == null) {
                refreshViewState();
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

            if ((mCurrentResultsPage[index] <= 1) || (mResults[index] == null)) {
                // Fresh results - overwrite old ones
                mResults[index] = resultsArray;
                mTotalResults[index] = totalResults;

                if ((index == VIEW_TYPE_OBSERVATIONS) && ((mCurrentResultsPage[index] == 1)) && (mObservationsMap != null)) {
                    // New search - clear all observation markers on map
                    mObservationsMap.clear();
                }

            } else {
                // Paginated results - append to old ones
                mResults[index].addAll(resultsArray);
                mTotalResults[index] = totalResults;

                saveListViewOffset(mObservationsGrid, VIEW_TYPE_OBSERVATIONS);
                saveListViewOffset(mList[VIEW_TYPE_SPECIES], VIEW_TYPE_SPECIES);
                saveListViewOffset(mList[VIEW_TYPE_OBSERVERS], VIEW_TYPE_OBSERVERS);
                saveListViewOffset(mList[VIEW_TYPE_IDENTIFIERS], VIEW_TYPE_IDENTIFIERS);
            }

            refreshViewState();
        }
    }


    private void refreshFilterBar(TextView filterBar) {
        if ((mSearchFilters == null) || (mMapHide == null)) return;
        if (filterBar == null) return;

        if (!mSearchFilters.isDirty()) {
            // Filters are default - don't display the filter bar
            filterBar.setVisibility(View.GONE);
            return;
        }

        filterBar.setVisibility(View.VISIBLE);
        mMapHide.setVisibility(View.GONE);

        StringBuilder builder = new StringBuilder();

        if (!mSearchFilters.iconicTaxa.isEmpty()) {
            builder.append(StringUtils.join(mSearchFilters.iconicTaxa, ", "));
            builder.append(", ");
        }

        if (mSearchFilters.project != null) {
            builder.append(mSearchFilters.project.optString("title"));
            builder.append(", ");
        }

        if (mSearchFilters.user != null) {
            builder.append(mSearchFilters.user.optString("login"));
            builder.append(", ");
        }

        if (mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_RESEARCH)) {
            builder.append(getString(R.string.research_grade));
            builder.append(", ");
        }
        if (mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID)) {
            builder.append(getString(R.string.needs_id));
            builder.append(", ");
        }
        if (mSearchFilters.qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_CASUAL)) {
            builder.append(getString(R.string.casual_grade));
            builder.append(", ");
        }

        switch (mSearchFilters.dateFilterType) {
            case ExploreSearchFilters.DATE_TYPE_EXACT_DATE:
                if (mSearchFilters.observedOn != null) {
                    builder.append(mSearchFilters.formatDate(mSearchFilters.observedOn));
                    builder.append(", ");
                }
                break;
            case ExploreSearchFilters.DATE_TYPE_MIN_MAX_DATE:
                if ((mSearchFilters.observedOnMinDate != null) && (mSearchFilters.observedOnMaxDate != null)) {
                    builder.append(mSearchFilters.formatDate(mSearchFilters.observedOnMinDate));
                    builder.append(" - ");
                    builder.append(mSearchFilters.formatDate(mSearchFilters.observedOnMaxDate));
                    builder.append(", ");
                }
                break;
            case ExploreSearchFilters.DATE_TYPE_MONTHS:
                if (!mSearchFilters.observedOnMonths.isEmpty()) {
                    Calendar cal = Calendar.getInstance();
                    SortedSet<Integer> sortedMonths = new TreeSet<>(mSearchFilters.observedOnMonths);
                    for (int month : sortedMonths) {
                        cal.set(Calendar.MONTH, month - 1); // Calendar has zero-based indexing for months
                        builder.append(new SimpleDateFormat("MMMM").format(cal.getTime()));
                        builder.append(", ");
                    }
                }
                break;
        }

        if ((mSearchFilters.annotationNameId != null) && (mSearchFilters.annotationName != null)) {
            builder.append(mSearchFilters.annotationName);

            if ((mSearchFilters.annotationValueId != null) && (mSearchFilters.annotationValue != null)) {
                builder.append(" = ");
                builder.append(mSearchFilters.annotationValue);
            }

            builder.append(", ");
        }

        if (mSearchFilters.hasPhotos) {
            builder.append(getString(R.string.has_photos));
            builder.append(", ");
        }
        if (mSearchFilters.hasSounds) {
            builder.append(getString(R.string.has_sounds));
            builder.append(", ");
        }


        if (builder.length() == 0) {
            filterBar.setText("");
            filterBar.setVisibility(View.GONE);
        } else {
            filterBar.setText(builder.substring(0, builder.length() - 2));
            filterBar.setVisibility(View.VISIBLE);
        }

    }

    private void refreshActionBar() {
        ActionBar actionBar = getSupportActionBar();
        final TextView title = (TextView) actionBar.getCustomView().findViewById(R.id.title);
        final TextView subTitle = (TextView) actionBar.getCustomView().findViewById(R.id.sub_title);

        if (mSearchFilters == null) return;

        if (mSearchFilters.taxon != null) {
            // Searching for a specific taxa
            title.setText(TaxonUtils.getTaxonName(this, mSearchFilters.taxon));
        } else {
            title.setText(R.string.exploring_all);
        }


        if (mSearchFilters.isCurrentLocation) {
            // Nearby observations
            subTitle.setText(R.string.my_location);
        } else if (mSearchFilters.mapBounds != null) {
            // Specific map bounds
            subTitle.setText(R.string.map_area);
        } else if (mSearchFilters.place != null) {
            // Specific place
            subTitle.setText(mSearchFilters.place.optString("display_name"));
        } else {
            // No specific place search - global search
            subTitle.setText(R.string.global);
        }
    }

    private void refreshTabTitles() {
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        for (int i = 0; i < mTotalResults.length; i++) {
            final TextView count = ((TextView) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.count));
            ProgressBar loading = ((ProgressBar) mTabLayout.getTabAt(i).getCustomView().findViewById(R.id.loading));

            if ((mLastTotalResults[i] == mTotalResults[i]) && (mLastTotalResults[i] != NOT_LOADED)) {
                // Already refreshed this tab title (prevent multiple fade in animations)
                continue;
            }

            mLastTotalResults[i] = mTotalResults[i];

            loading.setVisibility(View.GONE);

            if (mTotalResults[i] == NOT_LOADED) {
                // Still loading
                count.setVisibility(View.INVISIBLE);
            } else {
                // Already loaded - set the count
                count.setText(formatter.format(mTotalResults[i]));

                Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                fadeIn.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        count.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                count.startAnimation(fadeIn);
            }
        }
    }

    private void refreshResultsView(final int resultsType, final Class<? extends ArrayAdapter> adapterClass) {
        if (mLoadingList[resultsType] == null) {
            // View hasn't loaded yet
            return;
        }

        if (mFilterBar[resultsType] != null) {
            refreshFilterBar(mFilterBar[resultsType]);
        }

        if ((mTotalResults[resultsType] == NOT_LOADED) || (mResults[resultsType] == null)) {
            mLoadingList[resultsType].setVisibility(View.VISIBLE);
            mList[resultsType].setVisibility(View.GONE);
            mListEmpty[resultsType].setVisibility(View.GONE);
            if (mListHeader[resultsType] != null) mListHeader[resultsType].setVisibility(View.GONE);
        } else {
            mLoadingList[resultsType].setVisibility(View.GONE);

            if (mResults[resultsType].size() == 0) {
                mListEmpty[resultsType].setVisibility(View.VISIBLE);
            } else {
                mListEmpty[resultsType].setVisibility(View.GONE);
            }

            if (mListHeader[resultsType] != null)
                mListHeader[resultsType].setVisibility(View.VISIBLE);

            Runnable setResults = new Runnable() {
                @Override
                public void run() {
                    if ((mListAdapter[resultsType] != null) && (mCurrentResultsPage[resultsType] > 1)) {
                        // New results appended - don't reload the entire adapter
                        mListAdapter[resultsType].notifyDataSetChanged();
                    } else {
                        try {
                            // Create a new adapter
                            mListAdapter[resultsType] = adapterClass.getDeclaredConstructor(Context.class, ArrayList.class).newInstance(ExploreActivity.this, (ArrayList<JSONObject>) mResults[resultsType]);
                            mList[resultsType].setAdapter(mListAdapter[resultsType]);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            mList[resultsType].post(setResults);

            mList[resultsType].setVisibility(View.VISIBLE);

            mList[resultsType].setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if ((firstVisibleItem + visibleItemCount >= totalItemCount - 9) && (totalItemCount > 0) &&
                            (mResults[resultsType] != null) && ((mResults[resultsType].size() - 1) > mList[resultsType].getLastVisiblePosition())) {
                        // The end has been reached - load more results
                        loadNextResultsPage(resultsType, false);
                    }
                }

                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                }
            });

            loadListViewOffset(mList[resultsType], resultsType);
        }
    }

    private void refreshObservations() {
        if (mLoadingObservationsGrid == null) {
            // View hasn't loaded yet
            return;
        }

        refreshFilterBar(mObservationsGridFilterBar);
        refreshFilterBar(mObservationsMapFilterBar);

        mObservationsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                JSONObject item = (JSONObject) view.getTag();

                Intent intent = new Intent(ExploreActivity.this, ObservationViewerActivity.class);
                intent.putExtra("observation", item.toString());
                intent.putExtra("read_only", true);
                intent.putExtra("reload", true);
                startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_EXPLORE_GRID);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        if ((mTotalResults[VIEW_TYPE_OBSERVATIONS] == NOT_LOADED) || (mResults[VIEW_TYPE_OBSERVATIONS] == null)) {
            if (mObservationsViewMode == OBSERVATIONS_VIEW_MODE_GRID) {
                mLoadingObservationsGrid.setVisibility(View.VISIBLE);
            } else {
                mLoadingObservationsGrid.setVisibility(View.GONE);
            }

            mObservationsGrid.setVisibility(View.GONE);
            mObservationsGridEmpty.setVisibility(View.GONE);
            mObservationsMapContainer.setVisibility(View.GONE);
        } else {
            mLoadingObservationsGrid.setVisibility(View.GONE);

            if (mResults[VIEW_TYPE_OBSERVATIONS].size() == 0) {
                mObservationsGridEmpty.setVisibility(View.VISIBLE);
            } else {
                mObservationsGridEmpty.setVisibility(View.GONE);
            }

            Runnable setObsInGrid = new Runnable() {
                @Override
                public void run() {
                    if ((mGridAdapter != null) && (mCurrentResultsPage[VIEW_TYPE_OBSERVATIONS] > 1)) {
                        // New results appended - don't reload the entire adapter
                        mGridAdapter.notifyDataSetChanged();
                    } else if (mObservationsGrid.getColumnWidth() > 0) {
                        mGridAdapter = new ObservationGridAdapter(ExploreActivity.this, mObservationsGrid.getColumnWidth(), mResults[VIEW_TYPE_OBSERVATIONS]);
                        mObservationsGrid.setAdapter(mGridAdapter);
                    } else if (mObservationsGrid.getColumnWidth() == 0) {
                        mObservationsGrid.postDelayed(this, 100);
                    }
                }
            };
            mObservationsGrid.post(setObsInGrid);

            mObservationsGrid.setVisibility(View.VISIBLE);
        }

        loadListViewOffset(mObservationsGrid, VIEW_TYPE_OBSERVATIONS);


        mObservationsGrid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if ((firstVisibleItem + visibleItemCount >= totalItemCount - 9) && (totalItemCount > 0) &&
                        (mResults[VIEW_TYPE_OBSERVATIONS] != null) && ((mResults[VIEW_TYPE_OBSERVATIONS].size() - 1) > mObservationsGrid.getLastVisiblePosition())) {
                    // The end has been reached - load more observations
                    loadNextResultsPage(VIEW_TYPE_OBSERVATIONS, false);
                }
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
        });


        if (mObservationsViewMode == OBSERVATIONS_VIEW_MODE_GRID) {
            mObservationsViewModeGrid.setSelected(true);
            mObservationsViewModeGrid.setColorFilter(Color.parseColor("#ffffff"));
            mObservationsViewModeMap.setSelected(false);
            mObservationsViewModeMap.setColorFilter(Color.parseColor("#676767"));

            mObservationsMapContainer.setVisibility(View.GONE);
        } else {
            mObservationsViewModeGrid.setSelected(false);
            mObservationsViewModeGrid.setColorFilter(Color.parseColor("#676767"));
            mObservationsViewModeMap.setSelected(true);
            mObservationsViewModeMap.setColorFilter(Color.parseColor("#ffffff"));
            mLoadingObservationsGrid.setVisibility(View.GONE);

            mObservationsMapContainer.setVisibility(View.VISIBLE);
            mMapHide.setVisibility(View.GONE);
            mObservationsGrid.setVisibility(View.GONE);
            mObservationsGridEmpty.setVisibility(View.GONE);
        }

        mObservationsViewModeGrid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mObservationsViewMode = OBSERVATIONS_VIEW_MODE_GRID;
                refreshObservations();
            }
        });
        mObservationsViewModeMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mObservationsViewMode = OBSERVATIONS_VIEW_MODE_MAP;
                refreshObservations();
            }
        });

        mObservationsChangeMapLayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
                    mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
                } else {
                    mObservationsMapType = GoogleMap.MAP_TYPE_SATELLITE;
                }

                refreshMapType();
            }
        });

        refreshMapType();

        mRedoObservationsSearch.setVisibility(mMapMoved ? View.VISIBLE : View.GONE);
        mPerformingSearch.setVisibility(mLoadingNextResults[VIEW_TYPE_OBSERVATIONS] ? View.VISIBLE : View.GONE);
        mRedoObservationSearchIcon.setVisibility(mLoadingNextResults[VIEW_TYPE_OBSERVATIONS] ? View.GONE : View.VISIBLE);
        mRedoObservationsSearchText.setTextColor(mLoadingNextResults[VIEW_TYPE_OBSERVATIONS] ? Color.parseColor("#8A000000") : Color.parseColor("#000000"));

        mRedoObservationsSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Re-search under the current map bounds
                VisibleRegion vr = mObservationsMap.getProjection().getVisibleRegion();
                LatLngBounds bounds = new LatLngBounds(new LatLng(vr.nearLeft.latitude, vr.farLeft.longitude), new LatLng(vr.farRight.latitude, vr.farRight.longitude));
                mSearchFilters.mapBounds = bounds;
                mSearchFilters.place = null; // Clear out the place (search by map bounds only)


                if ((mLastMapBounds != null) && (mInitialLocationBounds != null) && (mLastMapBounds.equals(mInitialLocationBounds) == true)) {
                    mSearchFilters.isCurrentLocation = true;
                } else {
                    mSearchFilters.isCurrentLocation = false;
                }

                resetResults(true);
                loadAllResults();

                mPerformingSearch.setVisibility(View.VISIBLE);
                mRedoObservationSearchIcon.setVisibility(View.GONE);
                mRedoObservationsSearchText.setTextColor(Color.parseColor("#8A000000"));
            }
        });

        if ((mSearchFilters != null) && (mSearchFilters.place != null)) {
            // Show the boundaries/border of the place on the map
            addPlaceLayerToMap(mSearchFilters.place);
        }


        // Set the tile overlay (for the taxon's observations map)
        TileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public URL getTileUrl(int x, int y, int zoom) {

                String s = String.format(INaturalistService.API_HOST + "/%s/%d/%d/%d.png?%s",
                        zoom <= 9 ? "colored_heatmap" : "points", zoom, x, y, mSearchFilters.toUrlQueryString());

                try {
                    return new URL(s);
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
            }
        };

        if (mObservationsMap != null) {
            TileOverlay tileOverlay = mObservationsMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));
        }
    }

    private void refreshMapType() {
        if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_terrain_black_48dp);
        } else {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_satellite_black_48dp);
        }

        if ((mObservationsMap != null) && (mObservationsMap.getMapType() != mObservationsMapType))
            mObservationsMap.setMapType(mObservationsMapType);
    }

    private void resetResults(boolean resetOffsets) {
        // Reset old results while we're loading the new ones
        for (int i = 0; i < mTotalResults.length; i++) {
            mTotalResults[i] = NOT_LOADED;
            mResults[i] = null;
        }

        if (mObservationsMap != null) mObservationsMap.clear();

        if (resetOffsets) {
            mListViewIndex = new ArrayList<>(Arrays.asList(0 ,0, 0, 0));
            mListViewOffset = new ArrayList<>(Arrays.asList(0 ,0, 0, 0));
        }
    }

    private void loadAllResults() {
        refreshViewState();

        loadNextResultsPage(VIEW_TYPE_OBSERVATIONS, true);
        loadNextResultsPage(VIEW_TYPE_SPECIES, true);
        loadNextResultsPage(VIEW_TYPE_IDENTIFIERS, true);
        loadNextResultsPage(VIEW_TYPE_OBSERVERS, true);
    }

    private void loadNextResultsPage(final int resultsType, boolean resetResults) {
        if (resetResults) {
            mCurrentResultsPage[resultsType] = 0;
        }

        if (!mLoadingNextResults[resultsType] && ((resetResults) || (mResults[resultsType] == null) || (mResults[resultsType].size() < mTotalResults[resultsType]))) {
            mLoadingNextResults[resultsType] = true;

            String action = null;
            switch (resultsType) {
                case VIEW_TYPE_OBSERVATIONS:
                    action = INaturalistService.ACTION_EXPLORE_GET_OBSERVATIONS;
                    break;
                case VIEW_TYPE_SPECIES:
                    action = INaturalistService.ACTION_EXPLORE_GET_SPECIES;
                    break;
                case VIEW_TYPE_IDENTIFIERS:
                    action = INaturalistService.ACTION_EXPLORE_GET_IDENTIFIERS;
                    break;
                case VIEW_TYPE_OBSERVERS:
                    action = INaturalistService.ACTION_EXPLORE_GET_OBSERVERS;
                    break;
            }

            Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.FILTERS, mSearchFilters);
            serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, mCurrentResultsPage[resultsType] + 1);
            ContextCompat.startForegroundService(this, serviceIntent);

            if (mCurrentResultsPage[resultsType] > 0) {
                mLoadingMoreResults[resultsType].setVisibility(View.VISIBLE);
                mMapHide.setVisibility(View.GONE);
            }
        }
    }

    private void refreshViewState() {
        refreshActionBar();
        refreshTabTitles();
        refreshObservations();
        refreshResultsView(VIEW_TYPE_SPECIES, UserSpeciesAdapter.class);
        refreshResultsView(VIEW_TYPE_OBSERVERS, ProjectUserAdapter.class);
        refreshResultsView(VIEW_TYPE_IDENTIFIERS, ProjectUserAdapter.class);
    }


    public class ExplorePagerAdapter extends PagerAdapter {
        final int PAGE_COUNT = 4;
        private Context mContext;

        public ExplorePagerAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            int layoutResource = 0;

            switch (position) {
                case VIEW_TYPE_OBSERVATIONS:
                    layoutResource = R.layout.explore_observations;
                    break;
                case VIEW_TYPE_SPECIES:
                    layoutResource = R.layout.project_species;
                    break;
                case VIEW_TYPE_IDENTIFIERS:
                    layoutResource = R.layout.project_identifiers;
                    break;
                case VIEW_TYPE_OBSERVERS:
                    layoutResource = R.layout.project_people;
                    break;
            }

            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(layoutResource, collection, false);

            if (position == VIEW_TYPE_OBSERVERS) {
                mApp.setStringResourceForView(layout, R.id.observations_title, "observations_regular", "project_observations");
            }


            if (position == VIEW_TYPE_OBSERVATIONS) {
                mLoadingObservationsGrid = (ProgressBar) layout.findViewById(R.id.loading_observations_grid);
                mObservationsGridEmpty = (TextView) layout.findViewById(R.id.observations_grid_empty);
                mObservationsGrid = (GridViewExtended) layout.findViewById(R.id.observations_grid);
                ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.observations_map)).getMapAsync(new OnMapReadyCallback() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onMapReady(GoogleMap googleMap) {
                        mObservationsMap = googleMap;
                        mObservationsMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                            @Override
                            public void onMapClick(LatLng latLng) {
                                onObservationsMapClick(latLng);
                            }
                        });

                        mObservationsMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                            @Override
                            public void onMapLoaded() {
                                mMapReady = true;

                                if (mShouldMoveMapAccordingToSearchFilters) {
                                    mShouldMoveMapAccordingToSearchFilters = false;
                                    moveMapAccordingToSearchFilters();
                                }
                            }
                        });

                        if (mApp.isLocationPermissionGranted()) {
                            mObservationsMap.setMyLocationEnabled(true);
                        } else {
                            mObservationsMap.setMyLocationEnabled(false);
                        }

                        mObservationsMap.getUiSettings().setMyLocationButtonEnabled(false);
                        mObservationsMap.getUiSettings().setMapToolbarEnabled(false);
                        mObservationsMap.getUiSettings().setCompassEnabled(false);
                        mObservationsMap.getUiSettings().setIndoorLevelPickerEnabled(false);
                        mObservationsMap.setIndoorEnabled(false);
                        mObservationsMap.setTrafficEnabled(false);
                        mObservationsMap.getUiSettings().setRotateGesturesEnabled(false);

                        mObservationsMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
                            @Override
                            public void onCameraMove() {
                                // User moved the map view - allow him to make a new search on those new map bounds
                                if ((mLastMapBounds == null) || (!mLastMapBounds.equals(mObservationsMap.getProjection().getVisibleRegion().latLngBounds))) {
                                    mMapMoved = true;
                                    mRedoObservationsSearch.setVisibility(View.VISIBLE);

                                    if ((mInitialLocationBounds == null) || !(mInitialLocationBounds.equals(mObservationsMap.getProjection().getVisibleRegion().latLngBounds))) {
                                        mObservationsMapMyLocation.setColorFilter(Color.parseColor("#676767"));
                                    }
                                }

                                mLastMapBounds = mObservationsMap.getProjection().getVisibleRegion().latLngBounds;
                            }
                        });

                        refreshViewState();

                        if (mShouldMoveMapAccordingToSearchFilters) {
                            mShouldMoveMapAccordingToSearchFilters = false;

                            if (mMapReady) {
                                moveMapAccordingToSearchFilters();
                            } else {
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mMapReady) moveMapAccordingToSearchFilters();
                                    }
                                }, 1000);

                            }
                        }
                    }
                });
                mObservationsMapContainer = (ViewGroup) layout.findViewById(R.id.observations_map_container);
                mMapHide = (View) layout.findViewById(R.id.map_hide);
                mObservationsGridFilterBar = (TextView) layout.findViewById(R.id.grid_filter_bar);
                mObservationsMapFilterBar = (TextView) layout.findViewById(R.id.map_filter_bar);
                ViewCompat.setNestedScrollingEnabled(mObservationsGrid, true);

                mObservationsViewModeGrid = (ImageView) layout.findViewById(R.id.observations_grid_view_button);
                mObservationsViewModeMap = (ImageView) layout.findViewById(R.id.observations_map_view_button);
                mObservationsChangeMapLayers = (ImageView) layout.findViewById(R.id.change_map_layers);
                mObservationsMapMyLocation = (ImageView) layout.findViewById(R.id.my_location);
                mRedoObservationsSearch = (ViewGroup) layout.findViewById(R.id.redo_search);
                mPerformingSearch = (ProgressBar) layout.findViewById(R.id.performing_search);
                mRedoObservationSearchIcon = (ImageView) layout.findViewById(R.id.redo_search_icon);
                mRedoObservationsSearchText = (TextView) layout.findViewById(R.id.redo_search_text);
                mLoadingMoreResults[position] = (ViewGroup) layout.findViewById(R.id.loading_more_results);
                mLoadingMoreResults[position].setVisibility(View.GONE);

                mObservationsMapMyLocation.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_CURRENT_LOCATION, null, ExploreActivity.this, INaturalistService.class);
                        ContextCompat.startForegroundService(ExploreActivity.this, serviceIntent);
                    }
                });

                mObservationsMapMyLocation.setVisibility(mApp.isLocationPermissionGranted() ? View.VISIBLE : View.INVISIBLE);

                if (mLastMapBounds == null) {
                    if (mApp.isLocationPermissionGranted() && (mSearchFilters != null) && (mSearchFilters.isCurrentLocation)) {
                        // Initially zoom to current location
                        mObservationsMapMyLocation.performClick();
                    } else if (mSearchFilters != null) {
                        // No location permissions given - show a world map
                        mSearchFilters.mapBounds = null;
                        mSearchFilters.isCurrentLocation = false;
                    }
                }


            } else {
                int loadingListResource = 0;
                int listEmptyResource = 0;
                int listResource = 0;
                int listHeaderResource = 0;
                AdapterView.OnItemClickListener itemClickHandler = null;

                switch (position) {
                    case VIEW_TYPE_SPECIES:
                        loadingListResource = R.id.loading_species_list;
                        listEmptyResource = R.id.species_list_empty;
                        listResource = R.id.species_list;

                        itemClickHandler = new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                JSONObject item = (JSONObject) view.getTag();
                                Intent intent = new Intent(ExploreActivity.this, TaxonActivity.class);
                                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item));
                                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                                startActivity(intent);
                            }
                        };

                        break;
                    case VIEW_TYPE_OBSERVERS:
                        loadingListResource = R.id.loading_people_list;
                        listEmptyResource = R.id.people_list_empty;
                        listResource = R.id.people_list;
                        listHeaderResource = R.id.people_list_header;

                        itemClickHandler = new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                JSONObject item = (JSONObject) view.getTag();
                                Intent intent = new Intent(ExploreActivity.this, UserProfile.class);
                                intent.putExtra("user", new BetterJSONObject(item));
                                startActivity(intent);
                            }
                        };
                        break;

                    case VIEW_TYPE_IDENTIFIERS:
                        loadingListResource = R.id.loading_identifiers_list;
                        listEmptyResource = R.id.identifiers_list_empty;
                        listResource = R.id.identifiers_list;
                        listHeaderResource = R.id.identifiers_list_header;

                        itemClickHandler = new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                JSONObject item = (JSONObject) view.getTag();
                                Intent intent = new Intent(ExploreActivity.this, UserProfile.class);
                                intent.putExtra("user", new BetterJSONObject(item));
                                startActivity(intent);
                            }
                        };
                        break;
                }


                mLoadingList[position] = (ProgressBar) layout.findViewById(loadingListResource);
                mListEmpty[position] = (TextView) layout.findViewById(listEmptyResource);
                mList[position] = (ListView) layout.findViewById(listResource);
                mFilterBar[position] = (TextView) layout.findViewById(R.id.filter_bar);
                mLoadingMoreResults[position] = (ViewGroup) layout.findViewById(R.id.loading_more_results);
                mLoadingMoreResults[position].setVisibility(View.GONE);
                if (listHeaderResource != 0) mListHeader[position] = (ViewGroup) layout.findViewById(listHeaderResource);
                ViewCompat.setNestedScrollingEnabled(mList[position], true);

                mList[position].setOnItemClickListener(itemClickHandler);
            }

            collection.addView(layout);

            refreshViewState();

            return layout;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    private void onObservationsMapClick(LatLng latLng) {
        final int zoom = (int)Math.floor(mObservationsMap.getCameraPosition().zoom);

        final UTFPosition position = new UTFPosition(zoom, latLng.latitude, latLng.longitude);

        final String gridUrl = String.format(INaturalistService.API_HOST + "/points/%d/%d/%d.grid.json?%s", zoom, position.getTilePositionX(), position.getTilePositionY(), mSearchFilters.toUrlQueryString());

        // Download the UTFGrid JSON for that tile
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(gridUrl)
                        .build();

                try {
                    Response response = client.newCall(request).execute();
                    String responseBody = response.body().string();
                    JSONObject utfGridJson = new JSONObject(responseBody);
                    UTFGrid utfGrid = new UTFGrid(utfGridJson);

                    JSONObject observation = utfGrid.getDataForPixel(position.getPixelPositionX(), position.getPixelPositionY());

                    if (observation != null) {
                        // Found a matching observation
                        Logger.tag(TAG).debug("UTFGrid Observation: " + observation.toString());

                        Intent intent = new Intent(ExploreActivity.this, ObservationViewerActivity.class);

                        if (observation.has("captive")) observation.remove("captive"); // Since "captive" in the UTFGrid is a string instead of a boolean
                        if (observation.has("private_location")) observation.remove("private_location"); // Since "private_location" in the UTFGrid is a weird format ("[object Object]") instead of an actual string

                        intent.putExtra("observation", observation.toString());
                        intent.putExtra("read_only", true);
                        intent.putExtra("reload", true);
                        startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);

                        JSONObject eventParams = new JSONObject();
                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_EXPLORE_MAP);

                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void moveMapAccordingToSearchFilters() {
        if (mSearchFilters == null) return;

        if (mSearchFilters.place != null) {
            // New place selected for search - zoom the map to that location
            zoomMapToPlace(mSearchFilters.place);

        } else if (mSearchFilters.isCurrentLocation) {
            // Current location - we'll be setting to current location
            resetResults(true);
            mLastMapBounds = null;
            mObservationsMapMyLocation.performClick();
            return;
        } else if (mSearchFilters.mapBounds == null) {
            // No place set - global search (zoom out to world map)
            //LatLngBounds bounds = new LatLngBounds(new LatLng(-85, -180), new LatLng(85, 180));
            LatLngBounds bounds = new LatLngBounds(new LatLng(-60, -106), new LatLng(74, 38));
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 0);
            mMapMoved = false;
            mObservationsMap.moveCamera(cameraUpdate);
        }
    }


    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == VIEW_OBSERVATION_REQUEST_CODE) {
			if (resultCode == ObservationViewerActivity.RESULT_FLAGGED_AS_CAPTIVE) {
				return;
			}
		} else if ((requestCode == SEARCH_REQUEST_CODE) || (requestCode == FILTERS_REQUEST_CODE)) {
            if (resultCode == RESULT_OK) {
                // Update search filters and refresh results
                mSearchFilters = (ExploreSearchFilters) data.getSerializableExtra(ExploreSearchActivity.SEARCH_FILTERS);

                if (mMapReady) {
                    moveMapAccordingToSearchFilters();
                } else {
                    // Map not loaded yet - wait for it load before moving
                    mShouldMoveMapAccordingToSearchFilters = true;
                }

                resetResults(true);

                if ((!mSearchFilters.isCurrentLocation) || (mSearchFilters.place != null)) {
                    loadAllResults();
                }
            }
        }
	}


	private void addPlaceLayerToMap(JSONObject place) {
        if (place == null) return;
        if (mObservationsMap == null) return;

        GeoJsonLayer layer = getGeoJsonLayer(place.optJSONObject("geometry_geojson"));
        if (layer == null) return;

        // Using our own forked version of GeoJsonLayer, we can set polygons as non-clickable,
        // so we'll be able to catch clicks on markers inside the polygons.
        layer.setPolygonsClickable(false);
        layer.addLayerToMap();
    }


    private GeoJsonLayer getGeoJsonLayer(JSONObject boundingBox) {
        if (boundingBox == null) return null;

        try {
            JSONObject geoJson = new JSONObject();
            geoJson.put("type", "FeatureCollection");
            JSONArray features = new JSONArray();
            JSONObject feature = new JSONObject();
            feature.put("type", "Feature");
            feature.put("geometry", boundingBox);
            feature.put("properties", new JSONObject());
            features.put(feature);
            geoJson.put("features", features);

            GeoJsonLayer layer = new GeoJsonLayer(mObservationsMap, geoJson);
            layer.getDefaultLineStringStyle().setColor(Color.parseColor("#ccf16f3a"));
            layer.getDefaultPolygonStyle().setStrokeColor(Color.parseColor("#ccf16f3a"));
            Resources r = getResources();
            float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, r.getDisplayMetrics());
            layer.getDefaultPolygonStyle().setStrokeWidth(px);
            return layer;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void zoomMapToPlace(JSONObject place) {
        if (place == null) return;

        GeoJsonLayer layer = getGeoJsonLayer(place.optJSONObject("bounding_box_geojson"));
        String location = place.optString("location");
        CameraUpdate cameraUpdate = null;

        if (layer != null) {
            LatLngBounds bounds = layer.getBoundingBox();
            if (bounds == null) {
                LatLngBounds.Builder builder = LatLngBounds.builder();
                for (GeoJsonFeature f : layer.getFeatures()) {
                    if (f.getGeometry() instanceof GeoJsonPolygon) {
                        GeoJsonPolygon polygon = (GeoJsonPolygon) f.getGeometry();
                        for (List<LatLng> coordsList : polygon.getCoordinates()) {
                            for (LatLng coords : coordsList) {
                                builder.include(coords);
                            }
                        }
                    }
                }

                bounds = builder.build();
            }

            mLastMapBounds = bounds;
            cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 0);

        } else if (location != null) {

            String[] parts = location.split(",");
            LatLng latlng = new LatLng(Double.valueOf(parts[0]), Double.valueOf(parts[1]));
            cameraUpdate = CameraUpdateFactory.newLatLngZoom(latlng, MY_LOCATION_ZOOM_LEVEL);
        }

        if (cameraUpdate != null) {
            mMapMoved = false;
            mObservationsMap.moveCamera(cameraUpdate);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        mApp.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
