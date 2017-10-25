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
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class UserProfile extends AppCompatActivity implements TabHost.OnTabChangeListener, AppBarLayout.OnOffsetChangedListener {
 	private final static String VIEW_TYPE_OBSERVATIONS = "observations";
	private final static String VIEW_TYPE_SPECIES = "species";
    private final static String VIEW_TYPE_IDENTIFICATIONS = "identifications";

    private String mViewType;

    private INaturalistApp mApp;
    private BetterJSONObject mUser;

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

    private ArrayList<JSONObject> mObservations;
    private ArrayList<JSONObject> mSpecies;
    private ArrayList<JSONObject> mIdentifications;

    private UserDetailsReceiver mUserDetailsReceiver;

    private int mTotalObservations;
    private int mTotalSpecies;
    private int mTotalIdentifications;

    private AppBarLayout mAppBarLayout;
    private boolean mUserPicHidden;
    private ViewGroup mUserPicContainer;
    private TextView mUserName;
    private TextView mUserBio;
    private int mObservationListIndex;
    private int mObservationListOffset;
    private int mSpeciesListIndex;
    private int mSpeciesListOffset;
    private int mIdentificationsListIndex;
    private int mIdentificationsListOffset;

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

        mLoadingSpeciesList = (ProgressBar) findViewById(R.id.loading_species_list);
        mSpeciesListEmpty = (TextView) findViewById(R.id.species_list_empty);
        mSpeciesList = (ListView) findViewById(R.id.species_list);
        mSpeciesContainer = (ViewGroup) findViewById(R.id.species_container);

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

        ViewCompat.setNestedScrollingEnabled(mObservationsList, true);
        ViewCompat.setNestedScrollingEnabled(mIdentificationsList, true);
        ViewCompat.setNestedScrollingEnabled(mSpeciesList, true);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mUser = (BetterJSONObject) intent.getSerializableExtra("user");
            mTotalObservations = mUser.getInt("observations_count");
            mTotalIdentifications = mUser.getInt("identifications_count");
            mViewType = VIEW_TYPE_OBSERVATIONS;

            mObservationsContainer.setVisibility(View.VISIBLE);
            mSpeciesContainer.setVisibility(View.GONE);
            mIdentificationsContainer.setVisibility(View.GONE);

        } else {
            mUser = (BetterJSONObject) savedInstanceState.getSerializable("user");
            mViewType = savedInstanceState.getString("mViewType");
            mObservationListIndex = savedInstanceState.getInt("mObservationListIndex");
            mObservationListOffset = savedInstanceState.getInt("mObservationListOffset");
            mSpeciesListIndex = savedInstanceState.getInt("mSpeciesListIndex");
            mSpeciesListOffset = savedInstanceState.getInt("mSpeciesListOffset");
            mIdentificationsListIndex = savedInstanceState.getInt("mIdentificationsListIndex");
            mIdentificationsListOffset = savedInstanceState.getInt("IdentificationsmListOffset");

            mObservations = loadListFromBundle(savedInstanceState, "mObservations");
            mSpecies = loadListFromBundle(savedInstanceState, "mSpecies");
            mIdentifications = loadListFromBundle(savedInstanceState, "mIdentifications");

            mTotalIdentifications = savedInstanceState.getInt("mTotalIdentifications");
            mTotalObservations = savedInstanceState.getInt("mTotalObservations");
            mTotalSpecies = savedInstanceState.getInt("mTotalSpecies");
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

    private void saveListToBundle(Bundle outState, ArrayList<JSONObject> list, String key) {
        if (list != null) {
        	JSONArray arr = new JSONArray(list);
        	outState.putString(key, arr.toString());
        }
    }

    private ArrayList<JSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        ArrayList<JSONObject> results = new ArrayList<JSONObject>();

        String obsString = savedInstanceState.getString(key);
        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(arr.getJSONObject(i));
                }

                return results;
            } catch (JSONException exc) {
                exc.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("user", mUser);
        outState.putString("mViewType", mViewType);
        saveListToBundle(outState, mObservations, "mObservations");
        saveListToBundle(outState, mSpecies, "mSpecies");
        saveListToBundle(outState, mIdentifications, "mIdentifications");
        outState.putInt("mTotalIdentifications", mTotalIdentifications);
        outState.putInt("mTotalObservations", mTotalObservations);
        outState.putInt("mTotalSpecies", mTotalSpecies);

        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            View firstVisibleRow = mObservationsList.getChildAt(0);

            if (firstVisibleRow != null && mObservationsList != null) {
                mObservationListOffset = firstVisibleRow.getTop() - mObservationsList.getPaddingTop();
                mObservationListIndex = mObservationsList.getFirstVisiblePosition();

                outState.putInt("mObservationListIndex", mObservationListIndex);
                outState.putInt("mObservationListOffset", mObservationListOffset);
            }
        } else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            View firstVisibleRow = mSpeciesList.getChildAt(0);

            if (firstVisibleRow != null && mSpeciesList != null) {
                mSpeciesListOffset = firstVisibleRow.getTop() - mSpeciesList.getPaddingTop();
                mSpeciesListIndex = mSpeciesList.getFirstVisiblePosition();

                outState.putInt("mSpeciesListIndex", mSpeciesListIndex);
                outState.putInt("mSpeciesListOffset", mSpeciesListOffset);
            }
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFICATIONS)) {
            View firstVisibleRow = mIdentificationsList.getChildAt(0);

            if (firstVisibleRow != null && mIdentificationsList != null) {
                mIdentificationsListOffset = firstVisibleRow.getTop() - mIdentificationsList.getPaddingTop();
                mIdentificationsListIndex = mIdentificationsList.getFirstVisiblePosition();

                outState.putInt("mIdentificationsListIndex", mIdentificationsListIndex);
                outState.putInt("mIdentificationsListOffset", mIdentificationsListOffset);
            }
        }


        super.onSaveInstanceState(outState);
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

        if ((mUser == null) || (mUser.getInt("observations_count") == null)) getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);
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
        mObservationsContainer.setVisibility(View.GONE);
        mSpeciesContainer.setVisibility(View.GONE);
        mIdentificationsContainer.setVisibility(View.GONE);

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

    	if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            selectedTab = 0;
    		mObservationsContainer.setVisibility(View.VISIBLE);
    	} else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            selectedTab = 1;
            mSpeciesContainer.setVisibility(View.VISIBLE);
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFICATIONS)) {
            selectedTab = 2;
            mIdentificationsContainer.setVisibility(View.VISIBLE);
        }

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
                createTabContent(getString(R.string.project_observations), 1000)));
        UserProfile.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_SPECIES).setIndicator(
                createTabContent(getString(R.string.project_species), 2000)));
        UserProfile.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_IDENTIFICATIONS).setIndicator(
                createTabContent(getString(R.string.identifications), 3000)));

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
        startService(serviceIntent);
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
                mTotalIdentifications = mUser.getInt("identifications_count");
                return;
            } else if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
                // Life list result (species)
                resultsObject = (BetterJSONObject) object;
                totalResults = resultsObject.getInt("total_results");
                results = resultsObject.getJSONArray("results").getJSONArray();
            } else {
                // Observations / Identifications result
                results = ((SerializableJSONArray) object).getJSONArray();
                totalResults = results.length();
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
					e.printStackTrace();
				}
            }

            if (intent.getAction().equals(INaturalistService.USER_OBSERVATIONS_RESULT)) {
            	mObservations = resultsArray;
            } else if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
            	mSpecies = resultsArray;
                mTotalSpecies = totalResults;
            } else if (intent.getAction().equals(INaturalistService.IDENTIFICATIONS_RESULT)) {
                mIdentifications = resultsArray;
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
                mHelper.alert(title, mUser.getString("description"));
            }
        };

        if ((bio == null) || (bio.length() == 0)) {
            mUserBio.setVisibility(View.GONE);
        } else {
            mUserBio.setVisibility(View.VISIBLE);
            mUserBio.setText(Html.fromHtml(bio));

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
                            if (l.getEllipsisCount(lines - 1) > 0) {
                                // Bio is ellipsized - Trim the bio text to show the more link
                                String newBio = bio.substring(0, l.getLineStart(lines - 1) + l.getEllipsisStart(lines - 1) - 8) + "... " + getString(R.string.more_bio);
                                mUserBio.setText(Html.fromHtml(newBio));

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
                    imageView.setImageBitmap(ImageUtils.blur(UserProfile.this, ImageUtils.centerCropBitmap(loadedBitmap.copy(loadedBitmap.getConfig(), true))));
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
