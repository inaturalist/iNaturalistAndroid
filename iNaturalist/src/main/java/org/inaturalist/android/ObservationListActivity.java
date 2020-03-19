package org.inaturalist.android;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.inaturalist.android.INaturalistApp.INotificationCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import com.evernote.android.state.State;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.livefront.bridge.Bridge;

import android.annotation.SuppressLint;
import android.content.ContentValues;
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
import com.google.android.material.tabs.TabLayout;

import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

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
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationListActivity extends BaseFragmentActivity implements INotificationCallback, DialogInterface.OnClickListener, ObservationCursorAdapter.OnLoadingMoreResultsListener {
    public static String TAG = "INAT:ObservationListActivity";

    public final static String PARAM_FROM_OBS_EDITOR = "from_obs_editor";
    protected static final int REQUEST_CODE_OBSERVATION_VIEWER = 0x1001;

    @State public boolean[] mIsGrid = new boolean[] { false, false, false };

    private NewsReceiver mNewsReceiver;

	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private int mLastIndex;
	private int mLastTop;

    private ViewGroup mSyncingTopBar;

	private ObservationCursorAdapter mObservationListAdapter;
    private ObservationCursorAdapter mObservationGridAdapter;

	private ActivityHelper mHelper;

	@State public String mLastMessage;

    private INaturalistApp mApp;
    private TextView mSyncingStatus;
    private TextView mCancelSync;

    @State public boolean mUserCanceledSync = false; // When the user chose to pause/stop sync while in auto sync mode
    private boolean mSyncRequested = false;
    private Menu mMenu;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    @State public String mViewType;

    private final static String VIEW_TYPE_OBSERVATIONS = "observations";
	private final static String VIEW_TYPE_SPECIES = "species";
    private final static String VIEW_TYPE_IDENTIFICATIONS = "identifications";

    private ProgressBar mLoadingIdentifications;
    private TextView mIdentificationsEmpty;
    private ImageView mIdentificationsEmptyIcon;
    private ListView mIdentificationsList;
    private GridView mIdentificationsGrid;

    private ProgressBar mLoadingSpecies;
    private TextView mSpeciesEmpty;
    private ImageView mSpeciesEmptyIcon;
    private ListView mSpeciesList;
    private GridView mSpeciesGrid;

    private ProgressBar mLoadingObservations;
    private TextView mObservationsEmpty;
    private ImageView mObservationsEmptyIcon;
    private ListView mObservationsList;
    private GridView mObservationsGrid;

    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mSpecies;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mIdentifications;

    @State(AndroidStateBundlers.SetBundler.class) public Set<Long> mObsIdsToSync = new HashSet<>();

    @State public int mTotalIdentifications = 0;
    @State public int mTotalSpecies = 0;

    private UserSpeciesAdapter mSpeciesListAdapter;
    private UserSpeciesAdapter mSpeciesGridAdapter;
    private UserIdentificationsAdapter mIdentificationsListAdapter;
    private UserIdentificationsAdapter mIdentificationsGridAdapter;

    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mUser;
    private UserDetailsReceiver mUserDetailsReceiver;
    private ObservationSyncProgressReceiver mObservationSyncProgressReceiver;

    private ViewGroup mOnboardingSyncing;
    private View mOnboardingSyncingClose;

    private boolean mSelectedBottomGrid = false;
    private TextView mAddButtonText;

    @State public boolean mFromObsEdit = false;
    private ViewGroup mLoadingMoreResults;

    private Button mShowMoreSpecies;
    private Button mShowMoreIdentifications;

    public static boolean sActivityCreated = false;
    private SwipeRefreshLayout mListSwipeContainer;
    private SwipeRefreshLayout mGridSwipeContainer;
    private ViewGroup mDeleteContainer;
    private TextView mDeleteSync;


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

    @Override
    public void onLoadingMoreResultsStart() {
        mLoadingMoreResults.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoadingMoreResultsFinish() {
        mLoadingMoreResults.setVisibility(View.GONE);
    }


    @Override
    public void onLoadingMoreResultsFailed() {
        mLoadingMoreResults.setVisibility(View.GONE);
        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.not_connected), Toast.LENGTH_LONG).show();
            }
        }, 100);
    }

    private class SyncCompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Logger.tag(TAG).info("Got ACTION_SYNC_COMPLETE");

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            mListSwipeContainer.setRefreshing(false);
            mObservationsList.refreshDrawableState();
            mGridSwipeContainer.setRefreshing(false);
            mObservationsGrid.refreshDrawableState();

            mObservationListAdapter.refreshCursor();
            mObservationGridAdapter.refreshCursor();
            refreshSyncBar();

            if (mApp.loggedIn() && !mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "no observations" message
                if (mObservationsEmpty != null) mObservationsEmpty.setText(R.string.no_observations_found_new);
            }

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            SharedPreferences settings = mApp.getPrefs();
            ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(settings.getInt("observation_count", mObservationListAdapter.getCount())));

            mSyncRequested = false;

            if (!mApp.getIsSyncing()) {
                if ((intent != null) && (!intent.getBooleanExtra(INaturalistService.SYNC_CANCELED, false)) && (!intent.getBooleanExtra(INaturalistService.SYNC_FAILED, false))) {
                    // Sync finished
                    mUserCanceledSync = false;
                    refreshSyncBar();
                    refreshViewState();

                    // Get updated species count (in case user added a new observation with a new taxon)
                    getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);

                    try {
                        JSONObject eventParams = new JSONObject();
                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_UPLOAD_COMPLETE);

                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_STOPPED, eventParams);
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                    }

                    // Trigger another sync if needed - in case the user added more obs in the meantime while sync was running
                    triggerSyncIfNeeded();
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
    
    /** Shows the sync required bottom bar, if needed */
    private void refreshSyncBar() {
        if (mApp.getAutoSync()) {
            // Auto sync is on - no need for manual sync (only if the sync wasn't paused by the user)
            if (!mUserCanceledSync) {
                if (mSyncingTopBar != null) mSyncingTopBar.setVisibility(View.GONE);
                return;
            }
        }

        HashMap<Long, Boolean> obsToSync = new HashMap<>();

        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
        		Observation.PROJECTION, 
        		"((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL)) AND (_updated_at > _created_at)",
        		null, 
        		Observation.SYNC_ORDER);
        c.moveToFirst();
        while (!c.isAfterLast()) {
            obsToSync.put(c.getLong(c.getColumnIndex(Observation._ID)), true);
            c.moveToNext();
        }

        c.close();
        
        Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI,
        		/*
        		new String[]{
        		ObservationPhoto._ID, 
        		ObservationPhoto._OBSERVATION_ID,
        		ObservationPhoto._PHOTO_ID, 
        		ObservationPhoto.PHOTO_URL,
        		ObservationPhoto._UPDATED_AT,
        		ObservationPhoto._SYNCED_AT
            },
            */
        		ObservationPhoto.PROJECTION,
            "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NULL)) OR " +
            "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at)) OR " +
            "(is_deleted = 1)",
            null, 
            ObservationPhoto._ID);

        boolean photosChanged = false;
        opc.moveToFirst();
        while (!opc.isAfterLast()) {
            obsToSync.put(opc.getLong(opc.getColumnIndex(ObservationPhoto._OBSERVATION_ID)), true);
            opc.moveToNext();
            photosChanged = true;
        }

        opc.close();


        Cursor osc = getContentResolver().query(ObservationSound.CONTENT_URI,
        		new String[]{
        		ObservationSound._ID,
                ObservationSound.ID,
                ObservationSound._OBSERVATION_ID,
        		ObservationSound.IS_DELETED
            },
            "(id IS NULL) OR " +
            "(is_deleted = 1)",
            null,
            ObservationSound._ID);

        boolean soundsChanged = false;
        osc.moveToFirst();
        while (!osc.isAfterLast()) {
            obsToSync.put(osc.getLong(osc.getColumnIndex(ObservationSound._OBSERVATION_ID)), true);
            osc.moveToNext();
            soundsChanged = true;
        }

        osc.close();

        if (mSyncingTopBar != null) {
            mDeleteContainer.setVisibility(View.GONE);

            if (mObsIdsToSync.size() > 0) {
                // User chose specific observations to sync
                int numObsToSync = mObsIdsToSync.size();
                mSyncingStatus.setText(getResources().getQuantityString(R.plurals.sync_x_observations, numObsToSync, numObsToSync));

                mSyncingTopBar.setVisibility(View.VISIBLE);
                mUserCanceledSync = true; // To make it so that the button on the sync bar will trigger a sync
                mCancelSync.setText(R.string.upload);

                mDeleteContainer.setVisibility(View.VISIBLE);

            } else if (obsToSync.keySet().size() > 0) {
                int count = obsToSync.keySet().size();
                mSyncingStatus.setText(getResources().getQuantityString(R.plurals.sync_x_observations, count, count));
                mSyncingTopBar.setVisibility(View.VISIBLE);
                mUserCanceledSync = true; // To make it so that the button on the sync bar will trigger a sync
                mCancelSync.setText(R.string.upload);

                if (photosChanged || soundsChanged) {
                    mObservationListAdapter.refreshPhotoInfo();
                }
            } else {
                mSyncingTopBar.setVisibility(View.GONE);
            }
        }
    	
    }

    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sActivityCreated = true;

        Bridge.restoreInstanceState(this, savedInstanceState);

        setContentView(R.layout.observation_list);

        setTitle(R.string.observations);

        getSupportActionBar().setElevation(0);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ME);

        mHelper = new ActivityHelper(this);

        mApp = (INaturalistApp)getApplication();


        if (savedInstanceState == null) {
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

            mFromObsEdit = getIntent().getBooleanExtra(PARAM_FROM_OBS_EDITOR, false);
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
        
        onDrawerCreate(savedInstanceState);
        
        initializeTabs();

        triggerSyncIfNeeded();
        refreshViewState();

        // Clear out any old cached photos
        Intent serviceIntent = new Intent(INaturalistService.ACTION_CLEAR_OLD_PHOTOS_CACHE, null, ObservationListActivity.this, INaturalistService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        // Get the user's activities
        serviceIntent = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.FOLLOWING, false);
        ContextCompat.startForegroundService(this, serviceIntent);

        if (mApp.loggedIn()) {
            // Refresh user settings on app open
            Intent serviceIntent2 = new Intent(INaturalistService.ACTION_REFRESH_CURRENT_USER_SETTINGS, null, this, INaturalistService.class);
            ContextCompat.startForegroundService(this, serviceIntent2);

            // Get latest joined projects
            Intent serviceIntent3 = new Intent(INaturalistService.ACTION_SYNC_JOINED_PROJECTS, null, ObservationListActivity.this, INaturalistService.class);
            ContextCompat.startForegroundService(this, serviceIntent3);

        }

        redownloadObservationsIfLocaleChanged();
    }

    private void redownloadObservationsIfLocaleChanged() {
        if (!mApp.hasLocaleChanged()) return;

        // User changed the phone's language - re-download all local observations with the new taxon names (in that new language)
        Intent serviceIntent = new Intent(INaturalistService.ACTION_REDOWNLOAD_OBSERVATIONS_FOR_TAXON, null, this, INaturalistService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void refreshViewState() {
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        if (mLoadingObservations != null) {
            if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "downloading ..." message instead of "no observations yet"
                mObservationsEmpty.setText(R.string.downloading_observations);
                mObservationsEmptyIcon.setVisibility(View.GONE);
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

                if (mObservationListAdapter.getCount() == 0) {
                    mObservationsEmptyIcon.setVisibility(View.VISIBLE);
                    mObservationsEmpty.setVisibility(View.VISIBLE);
                    mAddButtonText.setVisibility(View.VISIBLE);
                } else {
                    mObservationsEmptyIcon.setVisibility(View.GONE);
                    mObservationsEmpty.setVisibility(View.GONE);
                    mAddButtonText.setVisibility(View.GONE);
                }

                mObservationsEmpty.setText(R.string.no_observations_found_new);
            }

       }

        if (mLoadingSpecies != null) {
            if ((mSpecies == null) && (mApp.loggedIn())) {
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingSpecies.setVisibility(View.VISIBLE);
                mSpeciesEmpty.setVisibility(View.GONE);
                mSpeciesEmptyIcon.setVisibility(View.GONE);
                mSpeciesList.setVisibility(View.GONE);
                mSpeciesGrid.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalSpecies));
                mLoadingSpecies.setVisibility(View.GONE);

                if ((mSpecies == null) || (mSpecies.size() == 0)) {
                    mSpeciesEmpty.setVisibility(View.VISIBLE);
                    mSpeciesEmptyIcon.setVisibility(View.VISIBLE);
                    mSpeciesList.setVisibility(View.GONE);
                    mSpeciesGrid.setVisibility(View.GONE);

                    mSpeciesListAdapter = new UserSpeciesAdapter(this, new ArrayList<JSONObject>());
                    mSpeciesGridAdapter = new UserSpeciesAdapter(this, new ArrayList<JSONObject>(), UserSpeciesAdapter.VIEW_TYPE_GRID, mSpeciesGrid);
                } else {
                    mSpeciesEmpty.setVisibility(View.GONE);
                    mSpeciesEmptyIcon.setVisibility(View.GONE);

                    if (mIsGrid[1]) {
                        mSpeciesGrid.setVisibility(View.VISIBLE);
                        mSpeciesList.setVisibility(View.GONE);
                    } else {
                        mSpeciesGrid.setVisibility(View.GONE);
                        mSpeciesList.setVisibility(View.VISIBLE);
                    }

                    mSpeciesListAdapter = new UserSpeciesAdapter(this, mSpecies);
                    mSpeciesGridAdapter = new UserSpeciesAdapter(this, mSpecies, UserSpeciesAdapter.VIEW_TYPE_GRID, mSpeciesGrid);
                }

                mSpeciesGrid.setAdapter(mSpeciesGridAdapter);
                mSpeciesList.setAdapter(mSpeciesListAdapter);

                mSpeciesList.setOnScrollListener(mSpeciesListAdapter);
                mSpeciesGrid.setOnScrollListener(mSpeciesGridAdapter);

                AbsListView.OnScrollListener onScroll = new AbsListView.OnScrollListener() {
                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        if ((firstVisibleItem + visibleItemCount >= totalItemCount - 3) && (totalItemCount > 0) &&
                                (mSpecies != null) && (mSpecies.size() > 0)) {
                            // The end has been reached - show the more species button
                            mShowMoreSpecies.setVisibility(View.VISIBLE);
                        } else {
                            mShowMoreSpecies.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
                    }
                };

                mSpeciesGridAdapter.setOnScrollListener(onScroll);
                mSpeciesListAdapter.setOnScrollListener(onScroll);

            }
        }

        if (mLoadingIdentifications != null) {
            if ((mIdentifications == null) && (mApp.loggedIn())) {
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingIdentifications.setVisibility(View.VISIBLE);
                mIdentificationsEmpty.setVisibility(View.GONE);
                mIdentificationsEmptyIcon.setVisibility(View.GONE);
                mIdentificationsList.setVisibility(View.GONE);
                mIdentificationsGrid.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalIdentifications));
                mLoadingIdentifications.setVisibility(View.GONE);

                if ((mIdentifications == null) || (mIdentifications.size() == 0)) {
                    mIdentificationsEmpty.setVisibility(View.VISIBLE);
                    mIdentificationsEmptyIcon.setVisibility(View.VISIBLE);
                    mIdentificationsList.setVisibility(View.GONE);
                    mIdentificationsGrid.setVisibility(View.GONE);

                    mIdentificationsListAdapter = new UserIdentificationsAdapter(this, new ArrayList<JSONObject>(), mApp.currentUserLogin());
                    mIdentificationsGridAdapter = new UserIdentificationsAdapter(this, new ArrayList<JSONObject>(), mApp.currentUserLogin(), true, mIdentificationsGrid);
                } else {
                    mIdentificationsEmpty.setVisibility(View.GONE);
                    mIdentificationsEmptyIcon.setVisibility(View.GONE);

                    mIdentificationsListAdapter = new UserIdentificationsAdapter(this, mIdentifications, mApp.currentUserLogin());
                    mIdentificationsGridAdapter = new UserIdentificationsAdapter(this, mIdentifications, mApp.currentUserLogin(), true, mIdentificationsGrid);

                    if (mIsGrid[2]) {
                        mIdentificationsGrid.setVisibility(View.VISIBLE);
                        mIdentificationsList.setVisibility(View.GONE);
                    } else {
                        mIdentificationsGrid.setVisibility(View.GONE);
                        mIdentificationsList.setVisibility(View.VISIBLE);
                    }
                }

                mIdentificationsList.setAdapter(mIdentificationsListAdapter);
                mIdentificationsGrid.setAdapter(mIdentificationsGridAdapter);

                // Make sure the images get loaded only when the user stops scrolling
                mIdentificationsList.setOnScrollListener(mIdentificationsListAdapter);
                mIdentificationsGrid.setOnScrollListener(mIdentificationsGridAdapter);

            }
        }


        refreshGridListMenuIcon();

    }

    private void triggerSyncIfNeeded() {
        if (!mApp.loggedIn()) {
            return;
        }

        boolean hasOldObs = hasOldObservations();
        if ((mApp.getAutoSync() && !mApp.getIsSyncing() && (!mSyncRequested)) || (hasOldObs)) {
            int syncCount = 0;
            int photoSyncCount = 0;
            int soundSyncCount = 0;

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
                                "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at) AND (id IS NOT NULL)) OR " +
                                "(is_deleted = 1)"
                        , null, ObservationPhoto.DEFAULT_SORT_ORDER);

                photoSyncCount = c.getCount();
                c.close();

                Cursor osc = getContentResolver().query(ObservationSound.CONTENT_URI,
                        new String[]{
                                ObservationSound._ID,
                                ObservationSound.ID,
                                ObservationSound._OBSERVATION_ID,
                                ObservationSound.IS_DELETED
                        },
                        "(id IS NULL) OR " +
                                "(is_deleted = 1)",
                        null,
                        ObservationSound._ID);

                osc.moveToFirst();
                soundSyncCount = osc.getCount();
                osc.close();

            }

            Logger.tag(TAG).debug(String.format("triggerSyncIfNeeded: hasOldOBs: %b; syncCount: %d; photoSyncCount: %d; mUserCanceledSync: %b",
                    hasOldObs, syncCount, photoSyncCount, mUserCanceledSync));


            // Trigger a sync (in case of auto-sync and unsynced obs OR when having old-style observations)
            if (hasOldObs || (((syncCount > 0) || (photoSyncCount > 0) || (soundSyncCount > 0)) && (!mUserCanceledSync) && (isNetworkAvailable()))) {
                mSyncRequested = true;
                Logger.tag(TAG).debug("Start sync by triggerSyncIfNeeded");
                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                ContextCompat.startForegroundService(this, serviceIntent);

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

        Logger.tag(TAG).debug("onPause");

        // save last position of list so we can resume there later
        // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
        if (mObservationsGrid != null) {
            AbsListView lv = mIsGrid[0] ? mObservationsGrid : mObservationsList;
            mLastIndex = lv.getFirstVisiblePosition();
            View v = lv.getChildAt(0);
            mLastTop = (v == null) ? 0 : v.getTop();
        }


        // Save listview/gridview preferences
        SharedPreferences settings = mApp.getPrefs();
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.putString("me_screen_list_grid", String.format("%s,%s,%s", mIsGrid[0], mIsGrid[1], mIsGrid[2]));
        settingsEditor.apply();

        safeUnregisterReceiver(mObservationSyncProgressReceiver);
        safeUnregisterReceiver(mNewsReceiver);
        safeUnregisterReceiver(mUserDetailsReceiver);
        safeUnregisterReceiver(mSyncCompleteReceiver);
        safeUnregisterReceiver(mConnectivityListener);

        mSyncRequested = false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        if (mFromObsEdit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        }


        if (!mApp.loggedIn()) {
            if ((mTotalIdentifications > 0) || (mTotalIdentifications > 0)) {
                mTotalSpecies = 0;
                mTotalIdentifications = 0;

                mSpecies = null;
                mIdentifications = null;

                refreshViewState();
            }
        }

        mUserDetailsReceiver = new UserDetailsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.USER_DETAILS_RESULT);
        filter.addAction(INaturalistService.SPECIES_COUNT_RESULT);
        filter.addAction(INaturalistService.USER_OBSERVATIONS_RESULT);
        filter.addAction(INaturalistService.IDENTIFICATIONS_RESULT);
        safeRegisterReceiver(mUserDetailsReceiver, filter);

        mObservationSyncProgressReceiver = new ObservationSyncProgressReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(INaturalistService.OBSERVATION_SYNC_PROGRESS);
        safeRegisterReceiver(mObservationSyncProgressReceiver, filter2);

        mSyncCompleteReceiver = new SyncCompleteReceiver();
        IntentFilter filter3 = new IntentFilter(INaturalistService.ACTION_SYNC_COMPLETE);
        Logger.tag(TAG).info("Registering ACTION_SYNC_COMPLETE");
        safeRegisterReceiver(mSyncCompleteReceiver, filter3);

        mConnectivityListener = new ConnectivityBroadcastReceiver();
        IntentFilter filter4 = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        Logger.tag(TAG).info("Registering CONNECTIVITY_ACTION");
        safeRegisterReceiver(mConnectivityListener, filter4);


        mNewsReceiver = new NewsReceiver();
        IntentFilter filter5 = new IntentFilter();
        filter5.addAction(INaturalistService.UPDATES_RESULT);
        safeRegisterReceiver(mNewsReceiver, filter5);

        if (mLoadingObservations != null) {
            if (mIsGrid[0]) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mObservationsGrid.setSelectionFromTop(mLastIndex, mLastTop);
                }
            } else {
                ListView lv = mObservationsList;
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

            } else {
                if (mObservationListAdapter.getCount() == 0) {
                    mObservationsEmptyIcon.setVisibility(View.VISIBLE);
                    mObservationsEmpty.setVisibility(View.VISIBLE);
                    mAddButtonText.setVisibility(View.VISIBLE);
                } else {
                    mObservationsEmptyIcon.setVisibility(View.GONE);
                    mObservationsEmpty.setVisibility(View.GONE);
                    mAddButtonText.setVisibility(View.GONE);
                }
            }
        }

        if (mApp.loggedIn() && (!mApp.getIsSyncing() && ((mObservationListAdapter == null) || (mObservationListAdapter.getCount() > 0)))) {
            if (mUser == null) getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);
            if (mIdentifications == null) getUserDetails(INaturalistService.ACTION_GET_USER_IDENTIFICATIONS);
            if (mSpecies == null) getUserDetails(INaturalistService.ACTION_GET_USER_SPECIES_COUNT);
        }

        triggerSyncIfNeeded();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
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


    private void onRefreshView(final SwipeRefreshLayout refreshView) {
        if (!isNetworkAvailable() || !isLoggedIn()) {
            Thread t = (new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Logger.tag(TAG).error(e);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshView.setRefreshing(false);
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
        ContextCompat.startForegroundService(this, serviceIntent);
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

                if (mViewPager.getCurrentItem() == 0) {
                    if (mIsGrid[0]) {
                        mListSwipeContainer.setEnabled(false);
                        mGridSwipeContainer.setEnabled(true);
                    } else {
                        mListSwipeContainer.setEnabled(true);
                        mGridSwipeContainer.setEnabled(false);
                    }
                }

                mLastIndex = 0;
                mLastTop = 0;
                refreshViewState();
                return true;

            case R.id.search_observations:
                startActivity(new Intent(ObservationListActivity.this, ObservationSearchActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                return true;

            case R.id.mark_all_as_viewed:
                // Update last_comment_count / last_id_count to be equal to current comment/id count (so no observation
                // will be shown with unread notifications / pink color)
                markAllObservationsAsRead();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void markAllObservationsAsRead() {

        // Find all observations with unread notifications
        Cursor cursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "((last_comments_count < comments_count) OR (last_identifications_count < identifications_count)) AND (id IS NOT NULL)", null, Observation.DEFAULT_SORT_ORDER);

        if (cursor.getCount() == 0) {
            return;
        }

        do {
            ContentValues cv = new ContentValues();
            BetterCursor bc = new BetterCursor(cursor);

            cv.put(Observation.LAST_COMMENTS_COUNT, bc.getInt(Observation.COMMENTS_COUNT));
            cv.put(Observation.LAST_IDENTIFICATIONS_COUNT, bc.getInt(Observation.IDENTIFICATIONS_COUNT));
            // Update its sync at time so we won't update the remote servers later on (since we won't
            // accidentally consider this an updated record)
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());

            int count = getContentResolver().update(ContentUris.withAppendedId(Observation.CONTENT_URI, bc.getInt(Observation._ID)), cv, null, null);
        } while (cursor.moveToNext());

        mObservationListAdapter.refreshCursor();
        mObservationGridAdapter.refreshCursor();
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

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.observations_list_grid, collection, false);

            ((ViewGroup) layout.findViewById(R.id.loading_more_results)).setVisibility(View.GONE);

            mApp.setStringResourceForView(layout, R.id.onboarding_syncing_close, "got_it_all_caps", "got_it");


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

                    Intent intent = new Intent(ObservationListActivity.this, ExploreActivity.class);
                    intent.putExtra(ExploreActivity.SEARCH_FILTERS, searchFilters);
                    int activeTab = ExploreActivity.VIEW_TYPE_OBSERVATIONS;

                    if (view == mShowMoreSpecies) {
                        activeTab = ExploreActivity.VIEW_TYPE_SPECIES;
                    } else if (view == mShowMoreIdentifications) {
                        activeTab = ExploreActivity.VIEW_TYPE_IDENTIFIERS;
                    }

                    intent.putExtra(ExploreActivity.ACTIVE_TAB, activeTab);
                    startActivity(intent);
                }
            };

            switch (position) {
                case 2:
                    mLoadingIdentifications = (ProgressBar) layout.findViewById(R.id.loading);
                    mIdentificationsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mIdentificationsEmpty.setText(R.string.no_identifications_found);
                    mIdentificationsEmptyIcon = (ImageView) layout.findViewById(R.id.empty_icon);
                    mIdentificationsEmptyIcon.setImageResource(R.drawable.ic_empty_id);
                    mIdentificationsList = layout.findViewById(R.id.list);
                    ((SwipeRefreshLayout)layout.findViewById(R.id.list_swipe_container)).setEnabled(false);
                    mIdentificationsGrid = layout.findViewById(R.id.grid);
                    ((SwipeRefreshLayout)layout.findViewById(R.id.grid_swipe_container)).setEnabled(false);
                    mShowMoreIdentifications = (Button) layout.findViewById(R.id.show_more);
                    mShowMoreIdentifications.setText(R.string.see_more_identifications);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

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
                                Logger.tag(TAG).error(e);
                            }
                        }
                    };

                    mIdentificationsList.setOnItemClickListener(onIdentificationsClick);
                    mIdentificationsGrid.setOnItemClickListener(onIdentificationsClick);

                    mShowMoreIdentifications.setOnClickListener(showMore);

                    break;

                case 1:
                    mLoadingSpecies = (ProgressBar) layout.findViewById(R.id.loading);
                    mSpeciesEmpty = (TextView) layout.findViewById(R.id.empty);
                    mSpeciesEmpty.setText(R.string.no_species_found);
                    mSpeciesEmptyIcon = (ImageView) layout.findViewById(R.id.empty_icon);
                    mSpeciesEmptyIcon.setImageResource(R.drawable.ic_empty_leaf);
                    mSpeciesList = layout.findViewById(R.id.list);
                    ((SwipeRefreshLayout)layout.findViewById(R.id.list_swipe_container)).setEnabled(false);
                    mSpeciesGrid = layout.findViewById(R.id.grid);
                    ((SwipeRefreshLayout)layout.findViewById(R.id.grid_swipe_container)).setEnabled(false);
                    mShowMoreSpecies = (Button) layout.findViewById(R.id.show_more);
                    mShowMoreSpecies.setText(R.string.see_more_species);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    OnItemClickListener onSpeciesClick = new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            if (item == null) return;
                            Intent intent = new Intent(ObservationListActivity.this, TaxonActivity.class);
                            intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item));
                            intent.putExtra(TaxonActivity.OBSERVATION, new BetterJSONObject(item));
                            intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                            startActivity(intent);
                        }
                    };

                    mSpeciesList.setOnItemClickListener(onSpeciesClick);
                    mSpeciesGrid.setOnItemClickListener(onSpeciesClick);

                    mShowMoreSpecies.setOnClickListener(showMore);

                    break;

                case 0:
                    mLoadingObservations = (ProgressBar) layout.findViewById(R.id.loading);
                    mObservationsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mObservationsEmpty.setText(R.string.no_observations_found_new);
                    mObservationsEmptyIcon = (ImageView) layout.findViewById(R.id.empty_icon);
                    mObservationsEmptyIcon.setImageResource(R.drawable.ic_empty_binoculars);
                    mListSwipeContainer = layout.findViewById(R.id.list_swipe_container);
                    mObservationsList = layout.findViewById(R.id.list);
                    ((SwipeRefreshLayout)layout.findViewById(R.id.list_swipe_container)).setEnabled(true);
                    mObservationsGrid = layout.findViewById(R.id.grid);
                    ((SwipeRefreshLayout)layout.findViewById(R.id.grid_swipe_container)).setEnabled(true);
                    mGridSwipeContainer = layout.findViewById(R.id.grid_swipe_container);
                    mLoadingMoreResults = (ViewGroup) layout.findViewById(R.id.loading_more_results);

                    if (mIsGrid[0]) {
                        mListSwipeContainer.setEnabled(false);
                        mGridSwipeContainer.setEnabled(true);
                    } else {
                        mListSwipeContainer.setEnabled(true);
                        mGridSwipeContainer.setEnabled(false);
                    }

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
                                    startActivityForResult(new Intent(Intent.ACTION_VIEW, uri, ObservationListActivity.this, ObservationViewerActivity.class), REQUEST_CODE_OBSERVATION_VIEWER);

                                    try {
                                        JSONObject eventParams = new JSONObject();
                                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_ME_TAB);

                                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                                    } catch (JSONException e) {
                                        Logger.tag(TAG).error(e);
                                    }

                                }
                            }
                        }
                    };

                    mObservationsList.setOnItemClickListener(onClick);
                    mObservationsGrid.setOnItemClickListener(onClick);

                    AdapterView.OnItemLongClickListener onLongClick = new AdapterView.OnItemLongClickListener() {
                        @Override
                        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                            // Multiple-selection mode
                            if (mObsIdsToSync.contains(id)) {
                                mObsIdsToSync.remove(id);
                            } else {
                                mObsIdsToSync.add(id);
                            }

                            mObservationListAdapter.setSelectedObservations(mObsIdsToSync);
                            mObservationGridAdapter.setSelectedObservations(mObsIdsToSync);

                            refreshSyncBar();

                            mObservationListAdapter.refreshCursor();
                            mObservationGridAdapter.refreshCursor();

                            return true;
                        }
                    };
                    //mObservationsList.setOnItemLongClickListener(onLongClick);
                    //mObservationsGrid.setOnItemLongClickListener(onLongClick);

                    mSyncingTopBar = (ViewGroup) layout.findViewById(R.id.syncing_top_bar);
                    mSyncingTopBar.setVisibility(View.GONE);
                    mSyncingStatus = (TextView) layout.findViewById(R.id.syncing_status);
                    mCancelSync = (TextView) layout.findViewById(R.id.cancel_sync);
                    mDeleteContainer = (ViewGroup) layout.findViewById(R.id.delete_container);
                    mDeleteSync = (TextView) layout.findViewById(R.id.delete);

                    if (mApp.getAutoSync()) {
                        // Auto sync
                        mCancelSync.setText(R.string.stop);
                    } else {
                        // Manual
                        mCancelSync.setText(R.string.upload);
                    }

                    mDeleteSync.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // User chose to delete specific observations
                            int numObsToDelete = mObsIdsToSync.size();
                            mHelper.confirm(
                                    getString(R.string.are_you_sure),
                                    getResources().getQuantityString(R.plurals.are_you_sure_you_want_to_delete_observations, numObsToDelete, numObsToDelete),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            deleteSelectedObservations();
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            
                                        }
                                    },
                                    getString(R.string.yes),
                                    getString(R.string.no)
                            );
                        }
                    });

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
                                    Logger.tag(TAG).error(e);
                                }

                            } else {
                                // User chose to resume sync
                                if (!isNetworkAvailable()) {
                                    try {
                                        JSONObject eventParams = new JSONObject();
                                        eventParams.put(AnalyticsClient.EVENT_PARAM_ALERT, getString(R.string.not_connected));

                                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_FAILED, eventParams);
                                    } catch (JSONException e) {
                                        Logger.tag(TAG).error(e);
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
                                        Logger.tag(TAG).error(e);
                                    }
                                    return;
                                }

                                Logger.tag(TAG).debug("Start sync by button");

                                mUserCanceledSync = false;
                                mApp.setCancelSync(false);
                                mCancelSync.setText(R.string.stop);
                                mSyncingStatus.setText(R.string.syncing);
                                // Re-sync
                                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                                if (mObsIdsToSync.size() > 0) {
                                    Long[] obsIds = new Long[mObsIdsToSync.size()];
                                    mObsIdsToSync.toArray(obsIds);
                                    serviceIntent.putExtra(INaturalistService.OBS_IDS_TO_SYNC, ArrayUtils.toPrimitive(obsIds));
                                }
                                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                ContextCompat.startForegroundService(ObservationListActivity.this, serviceIntent);

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
                    conditions += ") AND (is_deleted = 0 OR is_deleted is NULL) "; // Don't show deleted observations
                    conditions += " AND ((id >= " + mApp.getPrefs().getInt("last_downloaded_id", 0) + ")"; // Don't show obs that was downloaded through activity screen, etc. (not "naturally" by user)
                    conditions += " OR (_synced_at IS NULL))";

                    final Cursor cursor = getContentResolver().query(getIntent().getData(), Observation.PROJECTION,
                            conditions, null, Observation.DEFAULT_SORT_ORDER);

                    mObservationListAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor);
                    mObservationGridAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor, true, mObservationsGrid);
                    mObservationsGrid.setAdapter(mObservationGridAdapter);
                    mObservationsList.setAdapter(mObservationListAdapter);

                    mObservationGridAdapter.setOnLoadingMoreResultsListener(ObservationListActivity.this);
                    mObservationListAdapter.setOnLoadingMoreResultsListener(ObservationListActivity.this);

                    mObservationsList.setOnScrollListener(mObservationListAdapter);
                    mObservationsGrid.setOnScrollListener(mObservationGridAdapter);

                    // Set a listener to be invoked when the list should be refreshed.
                    mListSwipeContainer.setOnRefreshListener(() -> onRefreshView(mListSwipeContainer));
                    mGridSwipeContainer.setOnRefreshListener(() -> onRefreshView(mGridSwipeContainer));

                    refreshSyncBar();

                    View addButton = (View) layout.findViewById(R.id.add_observation);
                    mAddButtonText = (TextView) layout.findViewById(R.id.add_observation_text);
                    addButton.setVisibility(View.VISIBLE);
                    mAddButtonText.setVisibility(mObservationListAdapter.getCount() > 0 ? View.GONE : View.VISIBLE);

                    OnClickListener onAddObs = new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_START);
                            showNewObsMenu();
                        }
                    };

                    addButton.setOnClickListener(onAddObs);
                    mAddButtonText.setOnClickListener(onAddObs);


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

    private void deleteSelectedObservations() {
        for (Long obsId : mObsIdsToSync) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), obsId);
            Cursor cursor = managedQuery(uri, Observation.PROJECTION, null, null, null);
            Observation observation = new Observation(cursor);

            if (observation.id == null) {
                // Local observation (not yet synced) - delete locally only
                getContentResolver().delete(uri, null, null);

                // Delete any observation photos taken with it
                getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_observation_id=?", new String[]{observation._id.toString()});

                // Delete any observation sounds taken with it
                getContentResolver().delete(ObservationSound.CONTENT_URI, "_observation_id=?", new String[]{observation._id.toString()});

            } else {

                // Only mark as deleted (so we'll later on sync the deletion)
                ContentValues cv = observation.getContentValues();
                cv.put(Observation.IS_DELETED, 1);
                Logger.tag(TAG).debug("delete: Update: " + uri + ":" + cv);
                getContentResolver().update(uri, cv, null, null);
            }
        }


        mObsIdsToSync.clear();
        mObservationListAdapter.setSelectedObservations(mObsIdsToSync);
        mObservationGridAdapter.setSelectedObservations(mObsIdsToSync);

        refreshSyncBar();

        mObservationListAdapter.refreshCursor();
        mObservationGridAdapter.refreshCursor();
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

        addTab(0, createTabContent(mApp.getStringResourceByName("observations_all_caps", "project_observations"), 1000));
        addTab(1, createTabContent(mApp.getStringResourceByName("species_all_caps", "project_species"), 2000));
        addTab(2, createTabContent(mApp.getStringResourceByName("identifications_all_caps", "identifications"), 3000));

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

        if (mViewType == null) {
            mViewType = VIEW_TYPE_OBSERVATIONS;
        }

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
            Integer obsIdInternal = extras.getInt(INaturalistService.OBSERVATION_ID_INTERNAL);
            Float progress = extras.getFloat(INaturalistService.PROGRESS);

            if ((obsId == null) || (progress == null) || (mObservationListAdapter == null) || (mObservationGridAdapter == null)) {
                return;
            }

            Logger.tag(TAG).debug(String.format("Updating progress for %d / %d: %f", obsId, obsIdInternal, progress));

            if (progress == 100 && mObsIdsToSync.contains(Long.valueOf(obsIdInternal))) {
                mObsIdsToSync.remove(Long.valueOf(obsIdInternal));
                mObservationListAdapter.setSelectedObservations(mObsIdsToSync);
                mObservationGridAdapter.setSelectedObservations(mObsIdsToSync);
            }

            mObservationListAdapter.updateProgress(obsId, progress);
            mObservationListAdapter.notifyDataSetChanged();
            mObservationGridAdapter.updateProgress(obsId, progress);
            mObservationGridAdapter.notifyDataSetChanged();

            mObservationListAdapter.refreshCursor();
            mObservationGridAdapter.refreshCursor();
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

            if (object != null) {
                if (intent.getAction().equals(INaturalistService.USER_DETAILS_RESULT)) {
                    // Extended user details
                    mUser = (BetterJSONObject) object;

                    if (mUser.has("observations_count") && !mUser.isNull("observation_count")) {
                        SharedPreferences settings = mApp.getPrefs();
                        settings.edit().putInt("observation_count", mUser.getInt("observations_count")).commit();
                    }

                    refreshUserDetails();

                    refreshViewState();

                    return;
                } else if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT) || intent.getAction().equals(INaturalistService.IDENTIFICATIONS_RESULT)) {
                    // Species/identifications count result
                    resultsObject = (BetterJSONObject) object;
                    totalResults = resultsObject.getInt("total_results") == null ? 0 : resultsObject.getInt("total_results");
                    SerializableJSONArray resultsArray = resultsObject.getJSONArray("results");
                    results = resultsArray != null ? resultsArray.getJSONArray() : new JSONArray();
                }
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

            if (intent.getAction().equals(INaturalistService.SPECIES_COUNT_RESULT)) {
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

    private void getUserDetails(String action) {
        Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
        ContextCompat.startForegroundService(this, serviceIntent);
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
                    Logger.tag(TAG).error(e);
                }
            }

            SharedPreferences settings = mApp.getPrefs();
            settings.edit().putInt("unread_activities", unreadActivities).commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        boolean triggerSync = false;
        if (requestCode == REQUEST_CODE_OBSERVATION_VIEWER) {
            if (resultCode == ObservationViewerActivity.RESULT_OBSERVATION_CHANGED) {
                // Updated an existing obs
                triggerSync = true;
            }
        } else if (requestCode == REQUEST_CODE_OBSERVATION_EDIT) {
            if ((resultCode == RESULT_OK) || (resultCode == ObservationEditor.RESULT_REFRESH_OBS)) {
                // Added a new obs
                triggerSync = true;
            }
        }

        if (triggerSync) {
            // Trigger another sync if needed, since the user added/changed an observation
            triggerSyncIfNeeded();
        }
    }

}
