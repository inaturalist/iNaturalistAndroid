package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.Html;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;

public class UserProfile extends AppCompatActivity implements TabHost.OnTabChangeListener, AppBarLayout.OnOffsetChangedListener {
 	private final static String VIEW_TYPE_OBSERVATIONS = "observations";
	private final static String VIEW_TYPE_SPECIES = "species";
    private final static String VIEW_TYPE_IDENTIFICATIONS = "identifications";
    private static final String TAG = "UserProfile";

    @State public String mViewType;

    private INaturalistApp mApp;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mUser;

    private TabHost mTabHost;
    private ActivityHelper mHelper;

    private ListView mObservationsList;
    private UserObservationAdapter mObservationsListAdapter;
    private ProgressBar mLoadingObservationsList;
    private ViewGroup mObservationsContainer;
    private TextView mObservationsListEmpty;

    private ListView mSpeciesList;
    private UserSpeciesAdapter mSpeciesListAdapter;
    private ProgressBar mLoadingSpeciesList;
    private ViewGroup mSpeciesContainer;
    private TextView mSpeciesListEmpty;

    private ListView mIdentificationsList;
    private UserIdentificationsAdapter mIdentificationsListAdapter;
    private ProgressBar mLoadingIdentificationsList;
    private ViewGroup mIdentificationsContainer;
    private TextView mIdentificationsListEmpty;

    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mObservations;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mSpecies;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mIdentifications;

    private UserDetailsReceiver mUserDetailsReceiver;

    @State public int mTotalObservations;
    @State public int mTotalSpecies;
    @State public int mTotalIdentifications;

    private AppBarLayout mAppBarLayout;
    private boolean mUserPicHidden;
    private ViewGroup mUserPicContainer;
    private TextView mUserName;
    private TextView mUserBio;
    @State public int mObservationListIndex;
    @State public int mObservationListOffset;
    @State public int mSpeciesListIndex;
    @State public int mSpeciesListOffset;
    @State public int mIdentificationsListIndex;
    @State public int mIdentificationsListOffset;

    private Button mShowMoreObservations;
    private Button mShowMoreIdentifications;
    private Button mShowMoreSpecies;



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
        	this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        setContentView(R.layout.user_profile);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        CollapsingToolbarLayout collapsingToolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);

        mAppBarLayout = (AppBarLayout) findViewById(R.id.user_top_bar);
        mAppBarLayout.addOnOffsetChangedListener(this);

        mLoadingObservationsList = (ProgressBar) findViewById(R.id.loading_observations_list);
        mObservationsListEmpty = (TextView) findViewById(R.id.observations_list_empty);
        mObservationsList = (ListView) findViewById(R.id.observations_list);
        mObservationsContainer = (ViewGroup) findViewById(R.id.observations_container);
        mShowMoreObservations = (Button) findViewById(R.id.show_more_observations);

        mLoadingSpeciesList = (ProgressBar) findViewById(R.id.loading_species_list);
        mSpeciesListEmpty = (TextView) findViewById(R.id.species_list_empty);
        mSpeciesList = (ListView) findViewById(R.id.species_list);
        mSpeciesContainer = (ViewGroup) findViewById(R.id.species_container);
        mShowMoreSpecies = (Button) findViewById(R.id.show_more_species);

        mSpeciesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                JSONObject item = (JSONObject) view.getTag();
                Intent intent = new Intent(UserProfile.this, TaxonActivity.class);
                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item));
                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                startActivity(intent);
            }
        });

        mLoadingIdentificationsList = (ProgressBar) findViewById(R.id.loading_identifications_list);
        mIdentificationsListEmpty = (TextView) findViewById(R.id.identifications_list_empty);
        mIdentificationsList = (ListView) findViewById(R.id.identifications_list);
        mIdentificationsContainer = (ViewGroup) findViewById(R.id.identifications_container);
        mShowMoreIdentifications = (Button) findViewById(R.id.show_more_identifications);

        mObservationsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                JSONObject item = (JSONObject) view.getTag();
                Intent intent = new Intent(UserProfile.this, ObservationViewerActivity.class);
                intent.putExtra("observation", item.toString());
                intent.putExtra("read_only", true);
                intent.putExtra("reload", true);
                startActivity(intent);
            }
        });

        mObservationsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if ((firstVisibleItem + visibleItemCount >= totalItemCount - 3) && (totalItemCount > 0) &&
                        (mObservations != null) && (mObservations.size() > 0)) {
                    // The end has been reached - show the more obs button
                    mShowMoreObservations.setVisibility(View.VISIBLE);
                } else {
                    mShowMoreObservations.setVisibility(View.GONE);
                }
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
        });

        mSpeciesList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if ((firstVisibleItem + visibleItemCount >= totalItemCount - 3) && (totalItemCount > 0) &&
                        (mSpecies != null) && (mSpecies.size() > 0)) {
                    // The end has been reached - show the more obs button
                    mShowMoreSpecies.setVisibility(View.VISIBLE);
                } else {
                    mShowMoreSpecies.setVisibility(View.GONE);
                }
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
        });

        View.OnClickListener showMore = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Show explore screen with filtering by on this project, globally (not current user location)
                ExploreSearchFilters searchFilters = new ExploreSearchFilters();
                searchFilters.isCurrentLocation = false;
                searchFilters.mapBounds = null;
                searchFilters.place = null;
                searchFilters.qualityGrade = new HashSet<>();
                searchFilters.user = mUser.getJSONObject();

                Intent intent = new Intent(UserProfile.this, ExploreActivity.class);
                intent.putExtra(ExploreActivity.SEARCH_FILTERS, searchFilters);
                int activeTab = ExploreActivity.VIEW_TYPE_OBSERVATIONS;

                if (view == mShowMoreObservations) {
                    activeTab = ExploreActivity.VIEW_TYPE_OBSERVATIONS;
                } else if (view == mShowMoreSpecies) {
                    activeTab = ExploreActivity.VIEW_TYPE_SPECIES;
                } else if (view == mShowMoreIdentifications) {
                    activeTab = ExploreActivity.VIEW_TYPE_IDENTIFIERS;
                }

                intent.putExtra(ExploreActivity.ACTIVE_TAB, activeTab);
                startActivity(intent);
            }
        };

        mShowMoreObservations.setOnClickListener(showMore);
        mShowMoreSpecies.setOnClickListener(showMore);
        mShowMoreIdentifications.setOnClickListener(showMore);


        ViewCompat.setNestedScrollingEnabled(mObservationsList, true);
        ViewCompat.setNestedScrollingEnabled(mIdentificationsList, true);
        ViewCompat.setNestedScrollingEnabled(mSpeciesList, true);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mUser = (BetterJSONObject) intent.getSerializableExtra("user");
            mTotalObservations = mUser.getJSONObject().optInt("observations_count", 0);
            mTotalIdentifications = mUser.getJSONObject().optInt("identifications_count", 0);
            mViewType = VIEW_TYPE_OBSERVATIONS;

            mObservationsContainer.setVisibility(View.VISIBLE);
            mSpeciesContainer.setVisibility(View.INVISIBLE);
            mIdentificationsContainer.setVisibility(View.INVISIBLE);
        }

        // Tab Initialization
        initialiseTabHost();

        refreshViewState();
        refreshViewType();

        if (mUser == null) {
            finish();
            return;
        }


        mUserName = (TextView) findViewById(R.id.user_name);
        mUserBio = (TextView) findViewById(R.id.user_bio);

        mUserPicContainer = (ViewGroup) findViewById(R.id.user_pic_container);

        refreshUserDetails();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            View firstVisibleRow = mObservationsList.getChildAt(0);

            if (firstVisibleRow != null && mObservationsList != null) {
                mObservationListOffset = firstVisibleRow.getTop() - mObservationsList.getPaddingTop();
                mObservationListIndex = mObservationsList.getFirstVisiblePosition();
            }
        } else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            View firstVisibleRow = mSpeciesList.getChildAt(0);

            if (firstVisibleRow != null && mSpeciesList != null) {
                mSpeciesListOffset = firstVisibleRow.getTop() - mSpeciesList.getPaddingTop();
                mSpeciesListIndex = mSpeciesList.getFirstVisiblePosition();
            }
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFICATIONS)) {
            View firstVisibleRow = mIdentificationsList.getChildAt(0);

            if (firstVisibleRow != null && mIdentificationsList != null) {
                mIdentificationsListOffset = firstVisibleRow.getTop() - mIdentificationsList.getPaddingTop();
                mIdentificationsListIndex = mIdentificationsList.getFirstVisiblePosition();
            }
        }


        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mUserDetailsReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mUserDetailsReceiver = new UserDetailsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.USER_DETAILS_RESULT);
        filter.addAction(INaturalistService.SPECIES_COUNT_RESULT);
        filter.addAction(INaturalistService.USER_OBSERVATIONS_RESULT);
        filter.addAction(INaturalistService.IDENTIFICATIONS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mUserDetailsReceiver, filter, this);

        if ((mUser == null) || (mUser.getInt("observations_count") == null) || (mUser.getString("description") == null)) getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);
        if (mSpecies == null) getUserDetails(INaturalistService.ACTION_GET_USER_SPECIES_COUNT);
        if (mObservations == null) getUserDetails(INaturalistService.ACTION_GET_USER_OBSERVATIONS);
        if (mIdentifications == null) getUserDetails(INaturalistService.ACTION_GET_USER_IDENTIFICATIONS);

        refreshViewState();

        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            mObservationsList.setSelectionFromTop(mObservationListIndex, mObservationListOffset);
        } else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            mObservationsList.setSelectionFromTop(mSpeciesListIndex, mSpeciesListOffset);
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFICATIONS)) {
            mObservationsList.setSelectionFromTop(mIdentificationsListIndex, mIdentificationsListOffset);
        }


        if ((mObservations != null) && (mObservations.size() > 0) && (mObservationsList.getLastVisiblePosition() >= mObservations.size() - 3)) {
            mShowMoreObservations.setVisibility(View.VISIBLE);
        }
        if ((mSpecies != null) && (mSpecies.size() > 0) && (mSpeciesList.getLastVisiblePosition() >= mSpecies.size() - 3)) {
            mShowMoreSpecies.setVisibility(View.VISIBLE);
        }
    }

     // Method to add a TabHost
    private static void AddTab(UserProfile activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

    // Manages the Tab changes, synchronizing it with Pages
    public void onTabChanged(String tag) {
        mViewType = tag;
        refreshViewType();
    }

    private void refreshViewType() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mObservationsContainer.setVisibility(View.INVISIBLE);
                mSpeciesContainer.setVisibility(View.INVISIBLE);
                mIdentificationsContainer.setVisibility(View.INVISIBLE);
            }
        });

        TabWidget tabWidget = mTabHost.getTabWidget();
        for (int i = 0; i < tabWidget.getChildCount(); i++) {
            View tab = tabWidget.getChildAt(i);
            TextView tabNameText = (TextView) tab.findViewById(R.id.tab_name);
            View bottomLine = tab.findViewById(R.id.bottom_line);

            tabNameText.setTypeface(null, Typeface.NORMAL);
            tabNameText.setTextColor(Color.parseColor("#ACACAC"));
            bottomLine.setVisibility(View.GONE);
        }

        int selectedTab = 0;
        ViewGroup container = null;

        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            selectedTab = 0;
            container = mObservationsContainer;
        } else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            selectedTab = 1;
            container = mSpeciesContainer;
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFICATIONS)) {
            selectedTab = 2;
            container = mIdentificationsContainer;
        }

        container.setVisibility(View.VISIBLE);

        mTabHost.setCurrentTab(selectedTab);
        View tab = tabWidget.getChildAt(selectedTab);
        TextView tabNameText = (TextView) tab.findViewById(R.id.tab_name);
        View bottomLine = tab.findViewById(R.id.bottom_line);

        tabNameText.setTypeface(null, Typeface.BOLD);
        tabNameText.setTextColor(Color.parseColor("#000000"));
        bottomLine.setVisibility(View.VISIBLE);
    }


    // Tabs Creation
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        UserProfile.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_OBSERVATIONS).setIndicator(
                createTabContent(mApp.getStringResourceByName("observations_all_caps", "project_observations"), 1000)));
        UserProfile.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_SPECIES).setIndicator(
                createTabContent(mApp.getStringResourceByName("species_all_caps", "project_species"), 2000)));
        UserProfile.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_IDENTIFICATIONS).setIndicator(
                createTabContent(mApp.getStringResourceByName("identifications_all_caps", "identifications"), 3000)));

        mTabHost.getTabWidget().setDividerDrawable(null);

        mTabHost.setOnTabChangedListener(this);
    }

    private View createTabContent(String tabName, int count) {
        View view = LayoutInflater.from(this).inflate(R.layout.user_profile_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        TextView tabNameText = (TextView) view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        return view;
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    }


    private void getUserDetails(String action) {
        Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.USERNAME, mUser.getString("login"));
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private class UserDetailsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_user_details), error));
                return;
            }

            String username = intent.getStringExtra(INaturalistService.USERNAME);

            if ((username == null) || (!username.toLowerCase().equals(mUser.getString("login").toLowerCase()))) {
                // Results not for the current user name
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            Object object = null;
            BetterJSONObject resultsObject;
            JSONArray results = null;

            if (isSharedOnApp) {
                object = mApp.getServiceResult(intent.getAction());
            } else {
                object = intent.getSerializableExtra(actionToResultsParam(intent.getAction()));
            }

            int totalResults = 0;

            if (object == null) {
                refreshViewState();
                return;
            }

            if (intent.getAction().equals(INaturalistService.USER_DETAILS_RESULT)) {
                // Extended user details
                mUser = (BetterJSONObject) object;
                refreshUserDetails();

                mTotalObservations = mUser.getInt("observations_count");
                return;
            } else if ((intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) || (intent.getAction().equals(INaturalistService.IDENTIFICATIONS_RESULT) ||
                    (intent.getAction().equals(INaturalistService.USER_OBSERVATIONS_RESULT)))) {
                // Life list result (species) / identifications result
                resultsObject = (BetterJSONObject) object;
                totalResults = resultsObject.getInt("total_results");
                results = resultsObject.getJSONArray("results").getJSONArray();
            }

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            if (results == null) {
                refreshViewState();
                return;
            }

            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					Logger.tag(TAG).error(e);
				}
            }

            if (intent.getAction().equals(INaturalistService.USER_OBSERVATIONS_RESULT)) {
            	mObservations = resultsArray;
            } else if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
            	mSpecies = resultsArray;
                mTotalSpecies = totalResults;
            } else if (intent.getAction().equals(INaturalistService.IDENTIFICATIONS_RESULT)) {
                mIdentifications = resultsArray;
                mTotalIdentifications = totalResults;
            }

            refreshViewState();
        }

        private String actionToResultsParam(String action) {
            if (action.equals(INaturalistService.USER_DETAILS_RESULT)) {
                return INaturalistService.USER;
            } else if (action.equals(INaturalistService.SPECIES_COUNT_RESULT)) {
                return INaturalistService.SPECIES_COUNT_RESULT;
            } else if (action.equals(INaturalistService.USER_OBSERVATIONS_RESULT)) {
                return INaturalistService.OBSERVATIONS;
            } else if (action.equals(INaturalistService.IDENTIFICATIONS_RESULT)) {
                return INaturalistService.IDENTIFICATIONS;
            } else {
                return null;
            }
        }
    }

    private void refreshViewState() {
        TabWidget tabWidget = mTabHost.getTabWidget();
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        mShowMoreObservations.setVisibility(View.GONE);
        mShowMoreIdentifications.setVisibility(View.GONE);
        mShowMoreSpecies.setVisibility(View.GONE);

        if (mObservations == null) {
            ((TextView)tabWidget.getChildAt(0).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(0).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingObservationsList.setVisibility(View.VISIBLE);
            mObservationsList.setVisibility(View.GONE);
            mObservationsListEmpty.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(0).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(0).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(0).findViewById(R.id.count)).setText(formatter.format(mTotalObservations));
            mLoadingObservationsList.setVisibility(View.GONE);

            if (mObservations.size() == 0) {
                mObservationsListEmpty.setVisibility(View.VISIBLE);
            } else {
                mObservationsListEmpty.setVisibility(View.GONE);
            }

            if (mObservationsList.getAdapter() == null) {
                mObservationsListAdapter = new UserObservationAdapter(UserProfile.this, mObservations);
                mObservationsList.setAdapter(mObservationsListAdapter);
            }

            mObservationsList.setVisibility(View.VISIBLE);
        }

        if (mSpecies == null) {
            ((TextView)tabWidget.getChildAt(1).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(1).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingSpeciesList.setVisibility(View.VISIBLE);
            mSpeciesListEmpty.setVisibility(View.GONE);
            mSpeciesList.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(1).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(1).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(1).findViewById(R.id.count)).setText(formatter.format(mTotalSpecies));
            mLoadingSpeciesList.setVisibility(View.GONE);

            if (mSpecies.size() == 0) {
                mSpeciesListEmpty.setVisibility(View.VISIBLE);
            } else {
                mSpeciesListEmpty.setVisibility(View.GONE);
            }


            if (mSpeciesList.getAdapter() == null) {
                mSpeciesListAdapter = new UserSpeciesAdapter(UserProfile.this, mSpecies);
                mSpeciesList.setAdapter(mSpeciesListAdapter);
                mSpeciesList.setVisibility(View.VISIBLE);
            }
        }

        if (mIdentifications == null) {
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(2).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingIdentificationsList.setVisibility(View.VISIBLE);
            mIdentificationsListEmpty.setVisibility(View.GONE);
            mIdentificationsList.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(2).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setText(formatter.format(mTotalIdentifications));
            mLoadingIdentificationsList.setVisibility(View.GONE);

            if (mIdentifications.size() == 0) {
                mIdentificationsListEmpty.setVisibility(View.VISIBLE);
            } else {
                mIdentificationsListEmpty.setVisibility(View.GONE);
            }

            if (mIdentificationsList.getAdapter() == null) {
                mIdentificationsListAdapter = new UserIdentificationsAdapter(UserProfile.this, mIdentifications, mUser.getString("login"));
                mIdentificationsList.setAdapter(mIdentificationsListAdapter);
                mIdentificationsList.setVisibility(View.VISIBLE);

                // Make sure the images get loaded only when the user stops scrolling
                mIdentificationsList.setOnScrollListener(mIdentificationsListAdapter);

                mIdentificationsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        JSONObject item = (JSONObject) view.getTag();
                        Intent intent = new Intent(UserProfile.this, ObservationViewerActivity.class);
                        intent.putExtra("observation", item.optJSONObject("observation").toString());
                        intent.putExtra("read_only", true);
                        intent.putExtra("reload", true);
                        startActivity(intent);
                    }
                });

            }
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ViewGroup containers[] = { mSpeciesContainer, mObservationsContainer, mIdentificationsContainer };

                for (ViewGroup container: containers) {
                    ViewGroup.LayoutParams params = container.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    container.setLayoutParams(params);
                    container.requestLayout();
                }
            }
        }, 1);

    }


    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(offset) / (float) maxScroll;

        if (percentage >= 0.9f) {
            if (!mUserPicHidden) {
                startAlphaAnimation(mUserPicContainer, 100, View.INVISIBLE);
                mUserPicHidden = true;
            }
        } else {
            if (mUserPicHidden) {
                startAlphaAnimation(mUserPicContainer, 100, View.VISIBLE);
                mUserPicHidden = false;
            }
        }

    }

   public static void startAlphaAnimation (View v, long duration, int visibility) {
       AlphaAnimation alphaAnimation = (visibility == View.VISIBLE)
               ? new AlphaAnimation(0f, 1f)
               : new AlphaAnimation(1f, 0f);

       alphaAnimation.setDuration(duration);
       alphaAnimation.setFillAfter(true);
       v.startAnimation(alphaAnimation);
   }

    private void refreshUserDetails() {
        mUserName.setText(mUser.getString("login"));

        CollapsingToolbarLayout collapsingToolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        String fullName = mUser.getString("name");
        if ((fullName == null) || (fullName.length() == 0)) {
            // No full name - use username instead
            collapsingToolbar.setTitle(mUser.getString("login"));
            mUserName.setVisibility(View.INVISIBLE);
        } else {
            collapsingToolbar.setTitle(fullName);
            mUserName.setVisibility(View.VISIBLE);
        }

        final String bio = mUser.getString("description");
        mUserBio.setOnClickListener(null);

        final View.OnClickListener onBio = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String bio = mUser.getString("description");
                if ((bio == null) || (bio.length() == 0)) {
                    // No bio
                    return;
                }

                String title;
                String fullName = mUser.getString("name");
                if ((fullName == null) || (fullName.length() == 0)) {
                    title = mUser.getString("login");
                } else {
                    title = fullName;
                }
                String formattedBio = mUser.getString("description");
                formattedBio = formattedBio.replace("\n", "<br/>");
                mHelper.alert(title, formattedBio);
            }
        };

        if ((bio == null) || (bio.length() == 0)) {
            mUserBio.setVisibility(View.GONE);
        } else {
            mUserBio.setVisibility(View.VISIBLE);
            HtmlUtils.fromHtml(mUserBio, bio);

            ViewTreeObserver vto = mUserBio.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < 16) {
                        mUserBio.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        mUserBio.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }

                    Layout l = mUserBio.getLayout();
                    if (l != null) {
                        int lines = l.getLineCount();
                        if (lines > 0) {
                            //if (l.getEllipsisCount(lines - 1) > 0) {
                            if (l.getLineCount() > 2) {
                                // Bio is ellipsized - Trim the bio text to show the more link
                                String newBio = bio.substring(0, l.getLineEnd(1) - 8) + "... " + getString(R.string.more_bio);
                                HtmlUtils.fromHtml(mUserBio, newBio);

                                // Show the full bio when the shortened bio is clicked
                                mUserBio.setOnClickListener(onBio);
                            }
                        }
                    }
                }
            });

        }

        final ImageView userPic = (ImageView) findViewById(R.id.user_pic);
        String iconUrl = mUser.getString("medium_user_icon_url");
        if (iconUrl == null) iconUrl = mUser.getString("user_icon_url");
        if (iconUrl == null) iconUrl = mUser.getString("icon_url");

        if ((iconUrl != null) && (iconUrl.length() > 0)) {
            UrlImageViewHelper.setUrlDrawable(userPic, iconUrl, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    findViewById(R.id.no_user_pic).setVisibility(View.GONE);
                    userPic.setVisibility(View.VISIBLE);
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                    return ImageUtils.getCircleBitmap(centerCrop);
                }
            });

            UrlImageViewHelper.setUrlDrawable((ImageView) findViewById(R.id.user_bg), iconUrl + "?bg=1", new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    if (loadedBitmap != null) {
                        Bitmap.Config config = loadedBitmap.getConfig();
                        imageView.setImageBitmap(ImageUtils.blur(UserProfile.this, ImageUtils.centerCropBitmap(loadedBitmap.copy(config != null ? config : Bitmap.Config.ARGB_8888, true))));
                    }
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    return loadedBitmap;
                }
            });

            userPic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Only show the photo viewer if a large enough image exists
                    if ((mUser.getString("original_user_icon_url") != null) || (mUser.getString("medium_user_icon_url") != null) || (mUser.getString("icon_url") != null)) {
                        Intent intent = new Intent(UserProfile.this, ProfilePhotoViewer.class);
                        intent.putExtra(ProfilePhotoViewer.USER, mUser.getJSONObject().toString());
                        startActivity(intent);
                    }
                }
            });
        } else {
            userPic.setVisibility(View.GONE);
            findViewById(R.id.no_user_pic).setVisibility(View.VISIBLE);
        }

    }
}
