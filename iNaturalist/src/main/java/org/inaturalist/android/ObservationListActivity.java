package org.inaturalist.android;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.inaturalist.android.INaturalistApp.INotificationCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.cocosw.bottomsheet.BottomSheet;
import com.flurry.android.FlurryAgent;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationListActivity extends BaseFragmentActivity implements INotificationCallback, DialogInterface.OnClickListener {
	public static String TAG = "INAT:ObservationListActivity";

    private boolean[] mIsGrid = new boolean[] { false, false, false };

    private NewsReceiver mNewsReceiver;

	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private int mLastIndex;
	private int mLastTop;

    private ViewGroup mSyncingTopBar;

	private ObservationCursorAdapter mObservationListAdapter;
    private ObservationCursorAdapter mObservationGridAdapter;

	private ActivityHelper mHelper;

	private String mLastMessage;

    private INaturalistApp mApp;
    private TextView mSyncingStatus;
    private TextView mCancelSync;

    private boolean mUserCanceledSync = false; // When the user chose to pause/stop sync while in auto sync mode
    private boolean mSyncRequested = false;
    private Menu mMenu;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    private String mViewType;

    private final static String VIEW_TYPE_OBSERVATIONS = "observations";
	private final static String VIEW_TYPE_SPECIES = "species";
    private final static String VIEW_TYPE_IDENTIFICATIONS = "identifications";

    private ProgressBar mLoadingIdentifications;
    private TextView mIdentificationsEmpty;
    private PullToRefreshListView mIdentificationsList;
    private PullToRefreshGridViewExtended mIdentificationsGrid;

    private ProgressBar mLoadingSpecies;
    private TextView mSpeciesEmpty;
    private PullToRefreshListView mSpeciesList;
    private PullToRefreshGridViewExtended mSpeciesGrid;

    private ProgressBar mLoadingObservations;
    private TextView mObservationsEmpty;
    private PullToRefreshListView mObservationsList;
    private PullToRefreshGridViewExtended mObservationsGrid;

    private ArrayList<JSONObject> mSpecies;
    private ArrayList<JSONObject> mIdentifications;
    
    private int mTotalIdentifications = 0;
    private int mTotalSpecies = 0;

    private UserSpeciesAdapter mSpeciesListAdapter;
    private UserSpeciesAdapter mSpeciesGridAdapter;
    private UserIdentificationsAdapter mIdentificationsListAdapter;
    private UserIdentificationsAdapter mIdentificationsGridAdapter;

    private BetterJSONObject mUser;
    private UserDetailsReceiver mUserDetailsReceiver;
    private ObservationSyncProgressReceiver mObservationSyncProgressReceiver;

    private ViewGroup mOnboardingSyncing;
    private View mOnboardingSyncingClose;

    private boolean mSelectedBottomGrid = false;


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
	
	
	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        // User chose to cancel sync
        mApp.setCancelSync(true);
        refreshSyncBar();
    }

    private class SyncCompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.i(TAG, "Got ACTION_SYNC_COMPLETE");

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            mObservationsList.onRefreshComplete();
            mObservationsList.refreshDrawableState();
            mObservationsGrid.onRefreshComplete();
            mObservationsGrid.refreshDrawableState();

            mObservationListAdapter.refreshCursor();
            mObservationGridAdapter.refreshCursor();
            refreshSyncBar();

            if (mApp.loggedIn() && !mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "no observations" message
                TextView emptyView = (TextView)findViewById(android.R.id.empty);
                if (emptyView != null) emptyView.setText(R.string.no_observations_yet);
            }

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            SharedPreferences settings = mApp.getPrefs();
            ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(settings.getInt("observation_count", mObservationListAdapter.getCount())));

            mSyncRequested = false;

            if (!mApp.getIsSyncing()) {
                if ((intent != null) && (!intent.getBooleanExtra(INaturalistService.SYNC_CANCELED, false))) {
                    // Sync finished
                    mUserCanceledSync = false;
                    refreshSyncBar();
                    refreshViewState();


                    try {
                        JSONObject eventParams = new JSONObject();
                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_UPLOAD_COMPLETE);

                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_STOPPED, eventParams);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                // Decide if to show onboarding message
                SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                boolean hasOnboardedSyncing = prefs.getBoolean("onboarded_syncing", false);
                if (!intent.getBooleanExtra(INaturalistService.FIRST_SYNC, false)) {
                    mOnboardingSyncing.setVisibility(hasOnboardedSyncing || !mApp.loggedIn() ? View.GONE : View.VISIBLE);
                }
            }
        }
    } 	
  
    public static Intent createIntent(Context context) {
        Intent i = new Intent(context, ObservationListActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    } 
    
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (mSyncCompleteReceiver != null) {
            Log.i(TAG, "Unregistering ACTION_SYNC_COMPLETE");
            try {
                unregisterReceiver(mSyncCompleteReceiver);
                unregisterReceiver(mConnectivityListener);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
            mSyncCompleteReceiver = null;
            mConnectivityListener = null;
        }
    }

    /** Shows the sync required bottom bar, if needed */
    private void refreshSyncBar() {
        int syncCount = 0;
        int photoSyncCount = 0;

        if (mApp.getAutoSync()) {
            // Auto sync is on - no need for manual sync (only if the sync wasn't paused by the user)
            if (!mUserCanceledSync) {
                if (mSyncingTopBar != null) mSyncingTopBar.setVisibility(View.GONE);
                return;
            }
        }

        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
        		Observation.PROJECTION, 
        		"((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL)) AND (_updated_at > _created_at)",
        		null, 
        		Observation.SYNC_ORDER);
        syncCount = c.getCount();
        c.close();
        
        Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
        		new String[]{
        		ObservationPhoto._ID, 
        		ObservationPhoto._OBSERVATION_ID, 
        		ObservationPhoto._PHOTO_ID, 
        		ObservationPhoto.PHOTO_URL,
        		ObservationPhoto._UPDATED_AT,
        		ObservationPhoto._SYNCED_AT
            }, 
            "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NULL)) OR " +
            "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at)) OR " +
            "(is_deleted = 1)",
            null, 
            ObservationPhoto._ID);
        photoSyncCount = opc.getCount();
        opc.close();

        if (mSyncingTopBar != null) {
            if ((syncCount > 0) || (photoSyncCount > 0)) {
                int count = (syncCount > 0 ? syncCount : photoSyncCount);
                if (count == 1) {
                    mSyncingStatus.setText(R.string.sync_1_observation);
                } else {
                    mSyncingStatus.setText(String.format(getResources().getString(R.string.sync_x_observations), count));
                }
                mSyncingTopBar.setVisibility(View.VISIBLE);
                mUserCanceledSync = true; // To make it so that the button on the sync bar will trigger a sync
                mCancelSync.setText(R.string.upload);
            } else {
                mSyncingTopBar.setVisibility(View.GONE);
            }
        }
    	
    }
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.observation_list);

        setTitle(R.string.observations);

        getSupportActionBar().setElevation(0);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ME);

        mHelper = new ActivityHelper(this);

        mApp = (INaturalistApp)getApplication();

        if (savedInstanceState != null) {
            mLastMessage = savedInstanceState.getString("mLastMessage");
            mUserCanceledSync = savedInstanceState.getBoolean("mUserCanceledSync");
            mIsGrid = savedInstanceState.getBooleanArray("mIsGrid");
            mViewType = savedInstanceState.getString("mViewType");
            mUser = (BetterJSONObject) savedInstanceState.getSerializable("user");

            mSpecies = loadListFromBundle(savedInstanceState, "mSpecies");
            mIdentifications = loadListFromBundle(savedInstanceState, "mIdentifications");

            mTotalIdentifications = savedInstanceState.getInt("mTotalIdentifications");
            mTotalSpecies = savedInstanceState.getInt("mTotalSpecies");

        } else {
            SharedPreferences settings = mApp.getPrefs();
            String isGridArray = settings.getString("me_screen_list_grid", null);
            if (isGridArray != null) {
                int i = 0;
                for (String value : isGridArray.split(",")) {
                    mIsGrid[i] = Boolean.valueOf(value);
                    i++;
                }
            }

            mViewType = VIEW_TYPE_OBSERVATIONS;

            if (mApp.loggedIn()) {
                getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);
                getUserDetails(INaturalistService.ACTION_GET_USER_IDENTIFICATIONS);
                getUserDetails(INaturalistService.ACTION_GET_USER_SPECIES_COUNT);
            }
        }
        
        SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String username = pref.getString("username", null);
        if (username == null) {
            if (!mApp.shownOnboarding()) {
                // Show login/onboarding screen
                mApp.setShownOnboarding(true);
                Intent intent = new Intent(this, OnboardingActivity.class);
                intent.putExtra(OnboardingActivity.SHOW_SKIP, true);
                startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        }

        INaturalistApp app = (INaturalistApp)(getApplication());
        
        app.setNotificationCallback(this);
       
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(Observation.CONTENT_URI);
        }
        
        mSyncCompleteReceiver = new SyncCompleteReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_SYNC_COMPLETE);
        Log.i(TAG, "Registering ACTION_SYNC_COMPLETE");
        registerReceiver(mSyncCompleteReceiver, filter);

        mConnectivityListener = new ConnectivityBroadcastReceiver();
        IntentFilter filter2 = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        Log.i(TAG, "Registering CONNECTIVITY_ACTION");
        registerReceiver(mConnectivityListener, filter2);

        onDrawerCreate(savedInstanceState);
        
        initializeTabs();

        triggerSyncIfNeeded();
        refreshViewState();

        // Clear out any old cached photos
        Intent serviceIntent = new Intent(INaturalistService.ACTION_CLEAR_OLD_PHOTOS_CACHE, null, ObservationListActivity.this, INaturalistService.class);
        startService(serviceIntent);

        // Get the user's activities
        serviceIntent = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.FOLLOWING, false);
        startService(serviceIntent);
    }

    private void refreshViewState() {
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        if (mLoadingObservations != null) {
            if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "downloading ..." message instead of "no observations yet"
                mObservationsEmpty.setText(R.string.downloading_observations);
                mLoadingObservations.setVisibility(View.VISIBLE);
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);

                mObservationsGrid.setVisibility(View.GONE);
                mObservationsList.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                SharedPreferences settings = mApp.getPrefs();
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(settings.getInt("observation_count", mObservationListAdapter.getCount())));
                mLoadingObservations.setVisibility(View.GONE);

                if (mIsGrid[0]) {
                    mObservationsGrid.setVisibility(View.VISIBLE);
                    mObservationsList.setVisibility(View.GONE);
                } else {
                    mObservationsGrid.setVisibility(View.GONE);
                    mObservationsList.setVisibility(View.VISIBLE);
                }
            }

       }

        if (mLoadingSpecies != null) {
            if ((mSpecies == null) && (mApp.loggedIn())) {
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingSpecies.setVisibility(View.VISIBLE);
                mSpeciesEmpty.setVisibility(View.GONE);
                mSpeciesList.setVisibility(View.GONE);
                mSpeciesGrid.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalSpecies));
                mLoadingSpecies.setVisibility(View.GONE);

                if ((mSpecies == null) || (mSpecies.size() == 0)) {
                    mSpeciesEmpty.setVisibility(View.VISIBLE);
                } else {
                    mSpeciesEmpty.setVisibility(View.GONE);

                    mSpeciesListAdapter = new UserSpeciesAdapter(this, mSpecies);
                    mSpeciesList.setAdapter(mSpeciesListAdapter);
                    mSpeciesGridAdapter = new UserSpeciesAdapter(this, mSpecies, UserSpeciesAdapter.VIEW_TYPE_GRID, mSpeciesGrid);
                    mSpeciesGrid.setAdapter(mSpeciesGridAdapter);

                    mSpeciesList.setOnScrollListener(mSpeciesListAdapter);
                    mSpeciesGrid.setOnScrollListener(mSpeciesGridAdapter);

                    if (mIsGrid[1]) {
                        mSpeciesGrid.setVisibility(View.VISIBLE);
                        mSpeciesList.setVisibility(View.GONE);
                    } else {
                        mSpeciesGrid.setVisibility(View.GONE);
                        mSpeciesList.setVisibility(View.VISIBLE);
                    }
                }

            }
        }

        if (mLoadingIdentifications != null) {
            if ((mIdentifications == null) && (mApp.loggedIn())) {
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingIdentifications.setVisibility(View.VISIBLE);
                mIdentificationsEmpty.setVisibility(View.GONE);
                mIdentificationsList.setVisibility(View.GONE);
                mIdentificationsGrid.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalIdentifications));
                mLoadingIdentifications.setVisibility(View.GONE);

                if ((mIdentifications == null) || (mIdentifications.size() == 0)) {
                    mIdentificationsEmpty.setVisibility(View.VISIBLE);
                } else {
                    mIdentificationsEmpty.setVisibility(View.GONE);

                    mIdentificationsListAdapter = new UserIdentificationsAdapter(this, mIdentifications, mApp.currentUserLogin());
                    mIdentificationsList.setAdapter(mIdentificationsListAdapter);
                    mIdentificationsGridAdapter = new UserIdentificationsAdapter(this, mIdentifications, mApp.currentUserLogin(), true, mIdentificationsGrid);
                    mIdentificationsGrid.setAdapter(mIdentificationsGridAdapter);

                    // Make sure the images get loaded only when the user stops scrolling
                    mIdentificationsList.setOnScrollListener(mIdentificationsListAdapter);
                    mIdentificationsGrid.setOnScrollListener(mIdentificationsGridAdapter);

                    if (mIsGrid[2]) {
                        mIdentificationsGrid.setVisibility(View.VISIBLE);
                        mIdentificationsList.setVisibility(View.GONE);
                    } else {
                        mIdentificationsGrid.setVisibility(View.GONE);
                        mIdentificationsList.setVisibility(View.VISIBLE);
                    }
                }
            }
        }


        refreshGridListMenuIcon();
    }

    private void triggerSyncIfNeeded() {
        boolean hasOldObs = hasOldObservations();
        if ((mApp.getAutoSync() && !mApp.getIsSyncing() && (!mSyncRequested)) || (hasOldObs)) {
            int syncCount = 0;
            int photoSyncCount = 0;

            if (!hasOldObs) {
                Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL) OR (is_deleted = 1))",
                        null,
                        Observation.SYNC_ORDER);
                syncCount = c.getCount();
                c.close();

                c = getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION,
                        "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NULL)) OR " +
                                "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at)) OR " +
                                "(is_deleted = 1)"
                        , null, ObservationPhoto.DEFAULT_SORT_ORDER);

                photoSyncCount = c.getCount();
                c.close();
            }

            // Trigger a sync (in case of auto-sync and unsynced obs OR when having old-style observations)
            if (hasOldObs || (((syncCount > 0) || (photoSyncCount > 0)) && (!mUserCanceledSync) && (isNetworkAvailable()))) {
                mSyncRequested = true;
                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                startService(serviceIntent);


                if (mSyncingTopBar != null) {
                    mSyncingStatus.setText(R.string.syncing);
                    mSyncingTopBar.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    // Checks to see if there are any observations that have the "old" way of saving photos
    private boolean hasOldObservations() {
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(photo_filename IS NULL) AND (photo_url IS NULL)",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);
        int count = c.getCount();
        c.close();

        return count > 0;
    }
    

    @SuppressLint("NewApi")
	@Override
    public void onPause() {
        super.onPause();

        // save last position of list so we can resume there later
        // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
        if (mObservationsGrid != null) {
            AbsListView lv = mIsGrid[0] ? mObservationsGrid.getRefreshableView() : mObservationsList.getRefreshableView();
            mLastIndex = lv.getFirstVisiblePosition();
            View v = lv.getChildAt(0);
            mLastTop = (v == null) ? 0 : v.getTop();
        }


        // Save listview/gridview preferences
        SharedPreferences settings = mApp.getPrefs();
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.putString("me_screen_list_grid", String.format("%s,%s,%s", mIsGrid[0], mIsGrid[1], mIsGrid[2]));
        settingsEditor.apply();

        try {
            unregisterReceiver(mObservationSyncProgressReceiver);
            if (mNewsReceiver != null) unregisterReceiver(mNewsReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onPause();
    }
    
    @Override
    public void onResume() {
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
        registerReceiver(mUserDetailsReceiver, filter);

        mObservationSyncProgressReceiver = new ObservationSyncProgressReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(INaturalistService.OBSERVATION_SYNC_PROGRESS);
        registerReceiver(mObservationSyncProgressReceiver, filter2);

        mNewsReceiver = new NewsReceiver();
        IntentFilter filter3 = new IntentFilter();
        filter3.addAction(INaturalistService.UPDATES_RESULT);
        registerReceiver(mNewsReceiver, filter3);

        if (mLoadingObservations != null) {
            if (mIsGrid[0]) {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    GridView grid = mObservationsGrid.getRefreshableView();
                    grid.setSelectionFromTop(mLastIndex, mLastTop);
                }
            } else {
                ListView lv = mObservationsList.getRefreshableView();
                lv.setSelectionFromTop(mLastIndex, mLastTop);
            }

            mObservationListAdapter.refreshCursor();
            mObservationListAdapter.refreshPhotoInfo();
            if (mObservationGridAdapter != null) mObservationGridAdapter.refreshCursor();

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            SharedPreferences settings = mApp.getPrefs();
            ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(settings.getInt("observation_count", mObservationListAdapter.getCount())));
        }
      
        refreshSyncBar();

        if (mLoadingObservations != null) {
            INaturalistApp app = (INaturalistApp) (getApplication());
            if (app.getIsSyncing()) {
                // We're still syncing
                if ((mLastMessage != null) && (!mApp.getAutoSync()))
                    mSyncingStatus.setText(mLastMessage);
                app.setNotificationCallback(this);
                if (!app.getAutoSync()) mCancelSync.setText(R.string.stop);
            }

            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_observations), Toast.LENGTH_LONG).show();
                    }
                }
            }, 100);


            if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "downloading ..." message instead of "no observations yet"
                mObservationsEmpty.setText(R.string.downloading_observations);
                mLoadingObservations.setVisibility(View.VISIBLE);
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);

                refreshViewState();
                getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);
                getUserDetails(INaturalistService.ACTION_GET_USER_IDENTIFICATIONS);
                getUserDetails(INaturalistService.ACTION_GET_USER_SPECIES_COUNT);
            }
        }

        triggerSyncIfNeeded();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mLastMessage != null) outState.putString("mLastMessage", mLastMessage);
        outState.putBoolean("mUserCanceledSync", mUserCanceledSync);
        outState.putBooleanArray("mIsGrid", mIsGrid);
        outState.putString("mViewType", mViewType);
        outState.putSerializable("user", mUser);

        outState.putInt("mTotalIdentifications", mTotalIdentifications);
        outState.putInt("mTotalSpecies", mTotalSpecies);

        saveListToBundle(outState, mSpecies, "mSpecies");
        saveListToBundle(outState, mIdentifications, "mIdentifications");

        super.onSaveInstanceState(outState);
    }
 
    
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    } 
    
	@Override
	public void onNotification(String title, final String content) {
		mLastMessage = content;

		runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSyncingStatus != null) {
                    mSyncingStatus.setText(content);
                    int visibility;
                    if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                        visibility = View.GONE;
                    } else {
                        visibility = mApp.getIsSyncing() ? View.VISIBLE : View.GONE;
                    }
                    mSyncingTopBar.setVisibility(visibility);
                    mObservationListAdapter.refreshCursor();
                    mObservationGridAdapter.refreshCursor();
                }
            }
        });
	}



    private ConnectivityBroadcastReceiver mConnectivityListener = null;

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                return;
            }

            boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            if (!noConnectivity) {
                // We're connected to the Internet - try syncing again
                triggerSyncIfNeeded();
            }
        }
    }


    private void onRefreshView(final PullToRefreshBase pullToRefresh) {
        if (!isNetworkAvailable() || !isLoggedIn()) {
            Thread t = (new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pullToRefresh.onRefreshComplete();
                        }
                    });
                }
            }));
            t.start();
            if (!isNetworkAvailable()) {
                Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
            } else if (!isLoggedIn()) {
                Toast.makeText(getApplicationContext(), R.string.please_sign_in, Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Start sync
        Intent serviceIntent = new Intent(INaturalistService.ACTION_PULL_OBSERVATIONS, null, ObservationListActivity.this, INaturalistService.class);
        startService(serviceIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.my_observations_menu, menu);
        mMenu = menu;

        refreshGridListMenuIcon();
        return true;
    }

    private void refreshGridListMenuIcon() {
        if (mMenu != null) {
            int index = mViewPager.getCurrentItem();
            if (!mIsGrid[index]) {
                mMenu.getItem(0).setIcon(R.drawable.grid_view_gray);
            } else {
                mMenu.getItem(0).setIcon(R.drawable.list_view_gray);
            }

            switch (index) {
                case 0:
                    mMenu.getItem(0).setEnabled(true);
                    break;
                case 1:
                    mMenu.getItem(0).setEnabled(mSpecies != null);
                    break;
                case 2:
                    mMenu.getItem(0).setEnabled(mIdentifications != null);
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.observation_view_type:
                mIsGrid[mViewPager.getCurrentItem()] = !mIsGrid[mViewPager.getCurrentItem()];

                mLastIndex = 0;
                mLastTop = 0;
                refreshViewState();
                return true;

            case R.id.search_observations:
                startActivity(new Intent(ObservationListActivity.this, ObservationSearchActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }



    public class ObservationsPageAdapter extends PagerAdapter {
        final int PAGE_COUNT = 3;
        private Context mContext;

        public ObservationsPageAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        private void initPullToRefreshList(PullToRefreshBase pullToRefresh, ViewGroup layout) {
            pullToRefresh.getLoadingLayoutProxy().setPullLabel(getResources().getString(R.string.pull_to_refresh));
            pullToRefresh.getLoadingLayoutProxy().setReleaseLabel(getResources().getString(R.string.release_to_refresh));
            pullToRefresh.getLoadingLayoutProxy().setRefreshingLabel(getResources().getString(R.string.refreshing));
            pullToRefresh.setReleaseRatio(2.5f);

            if (pullToRefresh instanceof  PullToRefreshListView) {
                ((PullToRefreshListView) pullToRefresh).setEmptyView(layout.findViewById(R.id.empty));
            } else {
                ((PullToRefreshGridViewExtended) pullToRefresh).setEmptyView(layout.findViewById(R.id.empty));
            }
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.observations_list_grid, collection, false);

            switch (position) {
                case 2:
                    mLoadingIdentifications = (ProgressBar) layout.findViewById(R.id.loading);
                    mIdentificationsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mIdentificationsEmpty.setText(R.string.no_identifications_found);
                    mIdentificationsList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mIdentificationsList.setMode(PullToRefreshBase.Mode.DISABLED);
                    mIdentificationsGrid = (PullToRefreshGridViewExtended) layout.findViewById(R.id.grid);
                    mIdentificationsGrid.setMode(PullToRefreshBase.Mode.DISABLED);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    initPullToRefreshList(mIdentificationsList, layout);
                    initPullToRefreshList(mIdentificationsGrid, layout);

                    OnItemClickListener onIdentificationsClick = new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            Intent intent = new Intent(ObservationListActivity.this, ObservationViewerActivity.class);
                            intent.putExtra("observation", item.optJSONObject("observation").toString());
                            intent.putExtra("read_only", true);
                            intent.putExtra("reload", true);
                            startActivity(intent);

                            try {
                                JSONObject eventParams = new JSONObject();
                                eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_IDENTIFICATIONS_TAB);

                                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    };

                    mIdentificationsList.setOnItemClickListener(onIdentificationsClick);
                    mIdentificationsGrid.setOnItemClickListener(onIdentificationsClick);

                    break;

                case 1:
                    mLoadingSpecies = (ProgressBar) layout.findViewById(R.id.loading);
                    mSpeciesEmpty = (TextView) layout.findViewById(R.id.empty);
                    mSpeciesEmpty.setText(R.string.no_species_found);
                    mSpeciesList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mSpeciesList.setMode(PullToRefreshBase.Mode.DISABLED);
                    mSpeciesGrid = (PullToRefreshGridViewExtended) layout.findViewById(R.id.grid);
                    mSpeciesGrid.setMode(PullToRefreshBase.Mode.DISABLED);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    initPullToRefreshList(mSpeciesList, layout);
                    initPullToRefreshList(mSpeciesGrid, layout);

                    OnItemClickListener onSpeciesClick = new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            if (item == null) return;
                            Intent intent = new Intent(ObservationListActivity.this, GuideTaxonActivity.class);
                            intent.putExtra("taxon", new BetterJSONObject(item));
                            intent.putExtra("guide_taxon", false);
                            intent.putExtra("show_add", false);
                            intent.putExtra("download_taxon", true);
                            startActivity(intent);
                        }
                    };

                    mSpeciesList.setOnItemClickListener(onSpeciesClick);
                    mSpeciesGrid.setOnItemClickListener(onSpeciesClick);

                    break;

                case 0:
                    mLoadingObservations = (ProgressBar) layout.findViewById(R.id.loading);
                    mObservationsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mObservationsList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mObservationsGrid = (PullToRefreshGridViewExtended) layout.findViewById(R.id.grid);

                    mOnboardingSyncing = (ViewGroup) layout.findViewById(R.id.onboarding_syncing);
                    mOnboardingSyncingClose = layout.findViewById(R.id.onboarding_syncing_close);

                    mOnboardingSyncingClose.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mOnboardingSyncing.setVisibility(View.GONE);
                            SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                            prefs.edit().putBoolean("onboarded_syncing", true).commit();
                        }
                    });

                    initPullToRefreshList(mObservationsList, layout);
                    initPullToRefreshList(mObservationsGrid, layout);

                    OnItemClickListener onClick = new OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                            Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

                            String action = getIntent().getAction();
                            if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                                // The caller is waiting for us to return a note selected by
                                // the user.  The have clicked on one, so return it now.
                                setResult(RESULT_OK, new Intent().setData(uri));
                            } else {
                                if ((!mObservationListAdapter.isLocked(uri)) || (mObservationListAdapter.isLocked(uri) && !mApp.getIsSyncing())) {
                                    // Launch activity to view/edit the currently selected item
                                    startActivity(new Intent(Intent.ACTION_VIEW, uri, ObservationListActivity.this, ObservationViewerActivity.class));

                                    try {
                                        JSONObject eventParams = new JSONObject();
                                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_ME_TAB);

                                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                }
                            }
                        }
                    };

                    mObservationsList.setOnItemClickListener(onClick);
                    mObservationsGrid.setOnItemClickListener(onClick);

                    mSyncingTopBar = (ViewGroup) layout.findViewById(R.id.syncing_top_bar);
                    mSyncingTopBar.setVisibility(View.GONE);
                    mSyncingStatus = (TextView) layout.findViewById(R.id.syncing_status);
                    mCancelSync = (TextView) layout.findViewById(R.id.cancel_sync);

                    if (mApp.getAutoSync()) {
                        // Auto sync
                        mCancelSync.setText(R.string.stop);
                    } else {
                        // Manual
                        mCancelSync.setText(R.string.upload);
                    }

                    mCancelSync.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (!mUserCanceledSync) {
                                // User chose to cancel sync
                                mUserCanceledSync = true;
                                mApp.setCancelSync(true);
                                mCancelSync.setText(R.string.resume);
                                mSyncingStatus.setText(R.string.syncing_paused);

                                try {
                                    JSONObject eventParams = new JSONObject();
                                    eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_STOP_UPLOAD_BUTTON);

                                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_STOPPED, eventParams);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else {
                                // User chose to resume sync
                                if (!isNetworkAvailable()) {
                                    try {
                                        JSONObject eventParams = new JSONObject();
                                        eventParams.put(AnalyticsClient.EVENT_PARAM_ALERT, getString(R.string.not_connected));

                                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_FAILED, eventParams);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                                    return;
                                } else if (!isLoggedIn()) {
                                    // User not logged-in - redirect to onboarding screen
                                    startActivity(new Intent(ObservationListActivity.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));


                                    try {
                                        JSONObject eventParams = new JSONObject();
                                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_AUTH_REQUIRED);

                                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_STOPPED, eventParams);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    return;
                                }

                                mUserCanceledSync = false;
                                mApp.setCancelSync(false);
                                mCancelSync.setText(R.string.stop);
                                mSyncingStatus.setText(R.string.syncing);
                                // Re-sync
                                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                startService(serviceIntent);

                            }
                        }
                    });


                    String login = mApp.currentUserLogin();

                    // Perform a managed query. The Activity will handle closing and requerying the cursor
                    // when needed.
                    String conditions = "(_synced_at IS NULL";
                    if (login != null) {
                        conditions += " OR user_login = '" + login + "'";
                    }
                    conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

                    final Cursor cursor = getContentResolver().query(getIntent().getData(), Observation.PROJECTION,
                            conditions, null, Observation.DEFAULT_SORT_ORDER);

                    mObservationListAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor);
                    mObservationGridAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor, true, mObservationsGrid);
                    mObservationsGrid.setAdapter(mObservationGridAdapter);
                    mObservationsList.setAdapter(mObservationListAdapter);

                    mObservationsList.setOnScrollListener(mObservationListAdapter);
                    mObservationsGrid.setOnScrollListener(mObservationGridAdapter);

                    // Set a listener to be invoked when the list should be refreshed.
                    mObservationsList.setOnRefreshListener(new OnRefreshListener<ListView>() {
                        @Override
                        public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                            onRefreshView(mObservationsList);
                        }
                    });
                    mObservationsGrid.setOnRefreshListener(new OnRefreshListener<GridView>() {
                        @Override
                        public void onRefresh(PullToRefreshBase<GridView> refreshView) {
                            onRefreshView(mObservationsGrid);
                        }
                    });

                    refreshSyncBar();

                    View addButton = (View) layout.findViewById(R.id.add_observation);
                    addButton.setVisibility(View.VISIBLE);
                    addButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_START);

                            mSelectedBottomGrid = false;
                            new BottomSheet.Builder(ObservationListActivity.this).sheet(R.menu.observation_list_menu).listener(new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent;
                                    mSelectedBottomGrid = true;

                                    switch (which) {
                                        case R.id.camera:
                                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_SHUTTER);

                                            intent = new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class);
                                            intent.putExtra(ObservationEditor.TAKE_PHOTO, true);
                                            startActivity(intent);
                                            break;
                                        case R.id.upload_photo:
                                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_LIBRARY_START);

                                            intent = new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class);
                                            intent.putExtra(ObservationEditor.CHOOSE_PHOTO, true);
                                            startActivity(intent);
                                            break;
                                        case R.id.text:
                                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_NO_PHOTO);

                                            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class));
                                            break;
                                    }
                                }
                            }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    if (!mSelectedBottomGrid) {
                                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_CANCEL);
                                    }
                                    mSelectedBottomGrid = false;
                                }
                            }).show();
                        }
                    });


                    if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                        // Show a "downloading ..." message instead of "no observations yet"
                        mObservationsEmpty.setText(R.string.downloading_observations);
                        mLoadingObservations.setVisibility(View.VISIBLE);
                        ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                        ((ProgressBar) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                    }

                    break;
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

    // Method to add a TabHost
    private void addTab(int position, View tabContent) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);
        tab.setCustomView(tabContent);
    }

    private View createTabContent(String tabName, int count) {
        ViewGroup view = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.my_observations_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        TextView tabNameText = (TextView) view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        return view;
    }

    // Tabs Creation
    private void initializeTabs() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        mViewPager.setOffscreenPageLimit(3); // So we wouldn't have to recreate the views every time
        ObservationsPageAdapter adapter = new ObservationsPageAdapter(this);
        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);

        addTab(0, createTabContent(getString(R.string.project_observations), 1000));
        addTab(1, createTabContent(getString(R.string.project_species), 2000));
        addTab(2, createTabContent(getString(R.string.identifications), 3000));

        TabLayout.OnTabSelectedListener tabListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView tabNameText = (TextView) tab.getCustomView().findViewById(R.id.tab_name);

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
                switch (position) {
                    case 2:
                        mViewType = VIEW_TYPE_IDENTIFICATIONS;
                        break;
                    case 1:
                        mViewType = VIEW_TYPE_SPECIES;
                        break;
                    case 0:
                    default:
                        mViewType = VIEW_TYPE_OBSERVATIONS;
                        break;
                }

                refreshGridListMenuIcon();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mViewPager.addOnPageChangeListener(pageListener);

        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(0));
        } else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(1));
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFICATIONS)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(2));
        }
    }


    private class ObservationSyncProgressReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            Integer obsId = extras.getInt(INaturalistService.OBSERVATION_ID);
            Float progress = extras.getFloat(INaturalistService.PROGRESS);

            if ((obsId == null) || (progress == null) || (mObservationListAdapter == null) || (mObservationGridAdapter == null)) {
                return;
            }

            mObservationListAdapter.updateProgress(obsId, progress);
            mObservationGridAdapter.updateProgress(obsId, progress);
        }
    }

    private class UserDetailsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            String username = extras.getString(INaturalistService.USERNAME);

            if (!username.equals(mApp.currentUserLogin())) {
                // Results returned for another user
                return;
            }

            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_user_details), error));
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
                // Network error of some kind
                if (intent.getAction().equals(INaturalistService.IDENTIFICATIONS_RESULT)) {
                    mTotalIdentifications = 0;
                    mIdentifications = new ArrayList<>();
                } else if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
                    mTotalSpecies = 0;
                    mSpecies = new ArrayList<>();
                }
                refreshViewState();
                return;
            }

            if (intent.getAction().equals(INaturalistService.USER_DETAILS_RESULT)) {
                // Extended user details
                mUser = (BetterJSONObject) object;

                mTotalIdentifications = mUser.getInt("identifications_count");
                return;
            } else if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
                // Species count result
                resultsObject = (BetterJSONObject) object;
                totalResults = resultsObject.getInt("total_results") == null ? 0 : resultsObject.getInt("total_results") ;
                SerializableJSONArray resultsArray = resultsObject.getJSONArray("results");
                results = resultsArray != null ? resultsArray.getJSONArray() : new JSONArray();
            } else {
                // Identifications result
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

            if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
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

    private void getUserDetails(String action) {
        Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
        startService(serviceIntent);
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

    private class NewsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(INaturalistService.UPDATES_RESULT)) {
                return;
            }

            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            SerializableJSONArray resultsJSON;

            if (isSharedOnApp) {
                resultsJSON = (SerializableJSONArray) mApp.getServiceResult(intent.getAction());
            } else {
                resultsJSON = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.RESULTS);
            }

            if (resultsJSON == null) {
                return;
            }

            JSONArray results = resultsJSON.getJSONArray();
            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            if (results == null) {
                return;
            }

            // Count how many unread activities are there
            int unreadActivities = 0;
            for (int i = 0; i < results.length(); i++) {
                try {
                    JSONObject item = results.getJSONObject(i);
                    if (!item.getBoolean("viewed")) unreadActivities++;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            SharedPreferences settings = mApp.getPrefs();
            settings.edit().putInt("unread_activities", unreadActivities).commit();
        }
    }
}
