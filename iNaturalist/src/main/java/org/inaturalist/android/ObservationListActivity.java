package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.evernote.android.state.State;
import com.google.android.material.tabs.TabLayout;
import com.livefront.bridge.Bridge;

import org.apache.commons.lang3.ArrayUtils;
import org.inaturalist.android.INaturalistApp.INotificationCallback;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ObservationListActivity extends BaseFragmentActivity implements INotificationCallback,
        DialogInterface.OnClickListener, ObservationCursorAdapter.OnLoadingMoreResultsListener {
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

    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mUser;
    private UserDetailsReceiver mUserDetailsReceiver;
    private ObservationSyncProgressReceiver mObservationSyncProgressReceiver;

    private ViewGroup mOnboardingSyncing;

    private TextView mAddButtonText;

    @State public boolean mFromObsEdit = false;
    private ViewGroup mLoadingMoreResults;

    private Button mShowMoreSpecies;
    private Button mShowMoreIdentifications;

    public static boolean sActivityCreated = false;
    private SwipeRefreshLayout mListSwipeContainer;
    private SwipeRefreshLayout mGridSwipeContainer;
    private View mAddButton;
    private boolean mMultiSelectionMode = false;
    private TextView mMultiSelectionObsCount;


    private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    if (connectivityManager == null) return false;
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
        (new Handler()).postDelayed(() -> Toast.makeText(getApplicationContext(),
                getResources().getString(R.string.not_connected),
                Toast.LENGTH_LONG).show(), 100);
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
            TabLayout.Tab obsTab = mTabLayout.getTabAt(0);
            if (obsTab != null) {
                View tabView = obsTab.getCustomView();
                if (tabView != null) {
                    TextView obsCount = tabView.findViewById(R.id.count);
                    obsCount.setText(formatter.format(settings.getInt("observation_count",
                            mObservationListAdapter.getCount())));
                } else Logger.tag(TAG).warn("Null observation tab view in SyncCompleteReceiver");
            } else Logger.tag(TAG).warn("Null observation tab in SyncCompleteReceiver");

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
                if (intent != null && !intent.getBooleanExtra(INaturalistService.FIRST_SYNC, false)) {
                    mOnboardingSyncing.setVisibility(hasOnboardedSyncing || !mApp.loggedIn() ? View.GONE : View.VISIBLE);
                }
            }
        }
    } 	

    /** Shows the sync required bottom bar, if needed */
    private void refreshSyncBar() {
        if (mMultiSelectionMode && mSyncingTopBar != null) {
            mSyncingTopBar.setVisibility(View.GONE);

            mMultiSelectionObsCount.setText(String.valueOf(mObsIdsToSync.size()));
            return;
        }

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
        if (c != null) {
            c.moveToFirst();
            while (!c.isAfterLast()) {
                Long obsId = c.getLong(c.getColumnIndex(Observation._ID));
                obsToSync.put(obsId, true);
                Logger.tag(TAG).debug("refreshSyncBar - adding updated obs - " + obsId);
                c.moveToNext();
            }

            c.close();
        }

        Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI,
        		ObservationPhoto.PROJECTION,
            "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NULL)) OR " +
            "((_updated_at IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at)) OR " +
            "(is_deleted = 1)",
            null, 
            ObservationPhoto._ID);

        boolean photosChanged = false;
        if (opc != null) {
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                Long obsId = opc.getLong(opc.getColumnIndex(ObservationPhoto._OBSERVATION_ID));
                obsToSync.put(obsId, true);
                Logger.tag(TAG).debug("refreshSyncBar - adding photo for obs - " + obsId + ": photo = " + (new ObservationPhoto(opc)).toString());
                opc.moveToNext();
                photosChanged = true;
            }

            opc.close();
        }

        Cursor osc = getContentResolver().query(ObservationSound.CONTENT_URI,
            ObservationSound.PROJECTION,
            "(id IS NULL) OR " +
            "(is_deleted = 1)",
            null,
            ObservationSound._ID);

        boolean soundsChanged = false;
        if (osc != null) {
            osc.moveToFirst();
            while (!osc.isAfterLast()) {
                Long obsId = osc.getLong(osc.getColumnIndex(ObservationSound._OBSERVATION_ID));
                obsToSync.put(obsId, true);
                Logger.tag(TAG).debug("refreshSyncBar - adding sound for obs - " + obsId + ": sound = " + (new ObservationSound(osc)).toString());
                osc.moveToNext();
                soundsChanged = true;
            }

            osc.close();
        }

        Logger.tag(TAG).debug("refreshSyncBar - total - " + obsToSync.keySet().size());

        if (mSyncingTopBar != null) {
            if (obsToSync.keySet().size() > 0) {
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

        Logger.tag(TAG).debug("onCreate");

        Bridge.restoreInstanceState(this, savedInstanceState);

        setContentView(R.layout.observation_list);

        setTitle(R.string.observations);

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setElevation(0);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ME);

        mHelper = new ActivityHelper(this);

        mApp = (INaturalistApp)getApplication();


        if (savedInstanceState == null) {
            SharedPreferences settings = mApp.getPrefs();
            String isGridArray = settings.getString("me_screen_list_grid", null);
            Logger.tag(TAG).debug("Restore grid state: " + isGridArray);
            if (isGridArray != null) {
                int i = 0;
                for (String value : isGridArray.split(",")) {
                    mIsGrid[i] = Boolean.parseBoolean(value);
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

        // Get the user's activities
        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.FOLLOWING, false);
        ContextCompat.startForegroundService(this, serviceIntent);

        if (mApp.loggedIn()) {
            // Refresh user settings on app open
            Intent serviceIntent2 = new Intent(INaturalistService.ACTION_REFRESH_CURRENT_USER_SETTINGS, null, this, INaturalistService.class);
            ContextCompat.startForegroundService(this, serviceIntent2);
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
                TabLayout.Tab tab = mTabLayout.getTabAt(0);
                if (tab == null) {
                    Logger.tag(TAG).warn("Loading observations with no 0-tab");
                } else {
                    View tabView = tab.getCustomView();
                    if (tabView == null) {
                        Logger.tag(TAG).warn("Loading observations with no 0-tab view");
                    } else {
                        tabView.findViewById(R.id.count).setVisibility(View.GONE);
                        tabView.findViewById(R.id.loading).setVisibility(View.VISIBLE);
                    }
                }

                mObservationsGrid.setVisibility(View.GONE);
                mObservationsList.setVisibility(View.GONE);
            } else {
                mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count).setVisibility(View.VISIBLE);
                mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading).setVisibility(View.GONE);
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
                mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count).setVisibility(View.GONE);
                mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading).setVisibility(View.VISIBLE);
                mLoadingSpecies.setVisibility(View.VISIBLE);
                mSpeciesEmpty.setVisibility(View.GONE);
                mSpeciesEmptyIcon.setVisibility(View.GONE);
                mSpeciesList.setVisibility(View.GONE);
                mSpeciesGrid.setVisibility(View.GONE);
            } else {
                mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count).setVisibility(View.VISIBLE);
                mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalSpecies));
                mLoadingSpecies.setVisibility(View.GONE);

                UserSpeciesAdapter mSpeciesGridAdapter;
                UserSpeciesAdapter mSpeciesListAdapter;
                if ((mSpecies == null) || (mSpecies.size() == 0)) {
                    mSpeciesEmpty.setVisibility(View.VISIBLE);
                    mSpeciesEmptyIcon.setVisibility(View.VISIBLE);
                    mSpeciesList.setVisibility(View.GONE);
                    mSpeciesGrid.setVisibility(View.GONE);

                    mSpeciesListAdapter = new UserSpeciesAdapter(this, new ArrayList<>());
                    mSpeciesGridAdapter = new UserSpeciesAdapter(this, new ArrayList<>(), UserSpeciesAdapter.VIEW_TYPE_GRID, mSpeciesGrid);
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
                        } else mShowMoreSpecies.setVisibility(View.GONE);
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
                mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count).setVisibility(View.GONE);
                mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading).setVisibility(View.VISIBLE);
                mLoadingIdentifications.setVisibility(View.VISIBLE);
                mIdentificationsEmpty.setVisibility(View.GONE);
                mIdentificationsEmptyIcon.setVisibility(View.GONE);
                mIdentificationsList.setVisibility(View.GONE);
                mIdentificationsGrid.setVisibility(View.GONE);
            } else {
                mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count).setVisibility(View.VISIBLE);
                mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalIdentifications));
                mLoadingIdentifications.setVisibility(View.GONE);

                UserIdentificationsAdapter mIdentificationsGridAdapter;
                UserIdentificationsAdapter mIdentificationsListAdapter;
                if ((mIdentifications == null) || (mIdentifications.size() == 0)) {
                    mIdentificationsEmpty.setVisibility(View.VISIBLE);
                    mIdentificationsEmptyIcon.setVisibility(View.VISIBLE);
                    mIdentificationsList.setVisibility(View.GONE);
                    mIdentificationsGrid.setVisibility(View.GONE);

                    mIdentificationsListAdapter = new UserIdentificationsAdapter(this, new ArrayList<>(), mApp.currentUserLogin());
                    mIdentificationsGridAdapter = new UserIdentificationsAdapter(this, new ArrayList<>(), mApp.currentUserLogin(), true, mIdentificationsGrid);
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
                if (c != null) {
                    syncCount = c.getCount();
                    c.close();
                }

                c = getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION,
                        "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NULL)) OR " +
                                "((id IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at)) OR " +
                                "(is_deleted = 1)",
                        null, ObservationPhoto.DEFAULT_SORT_ORDER);
                if (c != null) {
                    photoSyncCount = c.getCount();
                    c.close();
                }

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

                if (osc != null) {
                    osc.moveToFirst();
                    soundSyncCount = osc.getCount();
                    osc.close();
                }
            }

            Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "triggerSyncIfNeeded: hasOldOBs: %b; syncCount: %d; photoSyncCount: %d; mUserCanceledSync: %b",
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
        int count = 0;
        if (c != null) {
            count = c.getCount();
            c.close();
        }

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
        saveGridState();

        safeUnregisterReceiver(mObservationSyncProgressReceiver);
        safeUnregisterReceiver(mNewsReceiver);
        safeUnregisterReceiver(mUserDetailsReceiver);
        safeUnregisterReceiver(mSyncCompleteReceiver);
        safeUnregisterReceiver(mConnectivityListener);

        mSyncRequested = false;
    }

    private void saveGridState() {
        // Save listview/gridview preferences
        SharedPreferences settings = mApp.getPrefs();
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.putString("me_screen_list_grid", String.format("%s,%s,%s", mIsGrid[0], mIsGrid[1], mIsGrid[2]));
        Logger.tag(TAG).debug("saveGridState: " + String.format("%s,%s,%s", mIsGrid[0], mIsGrid[1], mIsGrid[2]));
        settingsEditor.apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        if (mFromObsEdit) {
            finishAndRemoveTask();
        }


        if (!mApp.loggedIn()) {
            if ((mTotalIdentifications > 0) || (mTotalSpecies > 0)) {
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
                mObservationsGrid.setSelectionFromTop(mLastIndex, mLastTop);
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

            (new Handler()).postDelayed(() -> {
                if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_observations), Toast.LENGTH_LONG).show();
                }
            }, 100);


            if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "downloading ..." message instead of "no observations yet"
                mObservationsEmpty.setText(R.string.downloading_observations);
                mLoadingObservations.setVisibility(View.VISIBLE);
                mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count).setVisibility(View.GONE);
                mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading).setVisibility(View.VISIBLE);

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
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
 
    
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    } 
    
	@Override
	public void onNotification(String title, final String content) {
		mLastMessage = content;

		runOnUiThread(() -> {
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
        });
	}



    private ConnectivityBroadcastReceiver mConnectivityListener = null;

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null || !action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
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
            Thread t = (new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Logger.tag(TAG).error(e);
                }
                runOnUiThread(() -> refreshView.setRefreshing(false));
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
        if ((mMenu != null) && (!mMultiSelectionMode)) {
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
                saveGridState();

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

        if (cursor == null) {
            Logger.tag(TAG).warn("Null cursor from markAllObservationsAsRead");
            return;
        }

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
            if (count != 1) {
                Logger.tag(TAG).warn("Failed to update observation when marking all observations read");
            }

        } while (cursor.moveToNext());

        mObservationListAdapter.refreshCursor();
        mObservationGridAdapter.refreshCursor();
    }

    public class ObservationsPageAdapter extends PagerAdapter {
        final int PAGE_COUNT = 3;
        private Context mContext;

        ObservationsPageAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @NotNull
        @Override
        public Object instantiateItem(@NotNull ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.observations_list_grid, collection, false);

            Logger.tag(TAG).debug("instantiateItem - " + position);

            layout.findViewById(R.id.loading_more_results).setVisibility(View.GONE);

            mApp.setStringResourceForView(layout, R.id.onboarding_syncing_close, "got_it_all_caps", "got_it");

            View.OnClickListener showMore = view -> {
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
            };

            switch (position) {
                case 2:
                    mLoadingIdentifications = layout.findViewById(R.id.loading);
                    mIdentificationsEmpty = layout.findViewById(R.id.empty);
                    mIdentificationsEmpty.setText(R.string.no_identifications_found);
                    mIdentificationsEmptyIcon = layout.findViewById(R.id.empty_icon);
                    mIdentificationsEmptyIcon.setImageResource(R.drawable.ic_empty_id);
                    mIdentificationsList = layout.findViewById(R.id.list);
                    layout.findViewById(R.id.list_swipe_container).setEnabled(false);
                    mIdentificationsGrid = layout.findViewById(R.id.grid);
                    layout.findViewById(R.id.grid_swipe_container).setEnabled(false);
                    mShowMoreIdentifications = layout.findViewById(R.id.show_more);
                    mShowMoreIdentifications.setText(R.string.see_more_identifications);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    OnItemClickListener onIdentificationsClick = (adapterView, view, i, l) -> {
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
                    };

                    mIdentificationsList.setOnItemClickListener(onIdentificationsClick);
                    mIdentificationsGrid.setOnItemClickListener(onIdentificationsClick);

                    mShowMoreIdentifications.setOnClickListener(showMore);

                    break;

                case 1:
                    mLoadingSpecies = layout.findViewById(R.id.loading);
                    mSpeciesEmpty = layout.findViewById(R.id.empty);
                    mSpeciesEmpty.setText(R.string.no_species_found);
                    mSpeciesEmptyIcon = layout.findViewById(R.id.empty_icon);
                    mSpeciesEmptyIcon.setImageResource(R.drawable.ic_empty_leaf);
                    mSpeciesList = layout.findViewById(R.id.list);
                    layout.findViewById(R.id.list_swipe_container).setEnabled(false);
                    mSpeciesGrid = layout.findViewById(R.id.grid);
                    layout.findViewById(R.id.grid_swipe_container).setEnabled(false);
                    mShowMoreSpecies = layout.findViewById(R.id.show_more);
                    mShowMoreSpecies.setText(R.string.see_more_species);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    OnItemClickListener onSpeciesClick = (adapterView, view, i, l) -> {
                        JSONObject item = (JSONObject) view.getTag();
                        if (item == null) return;
                        Intent intent = new Intent(ObservationListActivity.this, TaxonActivity.class);
                        intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item));
                        intent.putExtra(TaxonActivity.OBSERVATION, new BetterJSONObject(item));
                        intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                        startActivity(intent);
                    };

                    mSpeciesList.setOnItemClickListener(onSpeciesClick);
                    mSpeciesGrid.setOnItemClickListener(onSpeciesClick);

                    mShowMoreSpecies.setOnClickListener(showMore);

                    break;

                case 0:
                    mLoadingObservations = layout.findViewById(R.id.loading);
                    mObservationsEmpty = layout.findViewById(R.id.empty);
                    mObservationsEmpty.setText(R.string.no_observations_found_new);
                    mObservationsEmptyIcon = layout.findViewById(R.id.empty_icon);
                    mObservationsEmptyIcon.setImageResource(R.drawable.ic_empty_binoculars);
                    mListSwipeContainer = layout.findViewById(R.id.list_swipe_container);
                    mObservationsList = layout.findViewById(R.id.list);
                    layout.findViewById(R.id.list_swipe_container).setEnabled(true);
                    mObservationsGrid = layout.findViewById(R.id.grid);
                    layout.findViewById(R.id.grid_swipe_container).setEnabled(true);
                    mGridSwipeContainer = layout.findViewById(R.id.grid_swipe_container);
                    mLoadingMoreResults = layout.findViewById(R.id.loading_more_results);

                    if (mIsGrid[0]) {
                        mListSwipeContainer.setEnabled(false);
                        mGridSwipeContainer.setEnabled(true);
                    } else {
                        mListSwipeContainer.setEnabled(true);
                        mGridSwipeContainer.setEnabled(false);
                    }

                    mOnboardingSyncing = layout.findViewById(R.id.onboarding_syncing);
                    View mOnboardingSyncingClose = layout.findViewById(R.id.onboarding_syncing_close);

                    mOnboardingSyncingClose.setOnClickListener(view -> {
                        mOnboardingSyncing.setVisibility(View.GONE);
                        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                        prefs.edit().putBoolean("onboarded_syncing", true).commit();
                    });

                    OnItemClickListener onClick = (adapterView, view, position1, id) -> {

                        if (mMultiSelectionMode) {
                            // Multi-selection mode - add/remove the observation
                            boolean selected = false;

                            if (mObsIdsToSync.contains(id)) {
                                mObsIdsToSync.remove(id);
                            } else {
                                mObsIdsToSync.add(id);
                                selected = true;
                            }

                            if (mObsIdsToSync.size() == 0) {
                                // Exit multi-selection mode in this case
                                setMultiSelectionMode(false);
                            } else {
                                mObservationListAdapter.setSelectedObservations(mObsIdsToSync);
                                mObservationGridAdapter.setSelectedObservations(mObsIdsToSync);

                                // Refresh the view of only the selected item
                                if (adapterView instanceof  ListView) {
                                    mObservationListAdapter.setItemSelected(view, selected);
                                } else {
                                    mObservationGridAdapter.setItemSelected(view, selected);
                                }

                                refreshSyncBar();
                            }

                            return;
                        }

                        // Regular mode - open up the observation details screen

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
                    };

                    mObservationsList.setOnItemClickListener(onClick);
                    mObservationsGrid.setOnItemClickListener(onClick);

                    AdapterView.OnItemLongClickListener onLongClick = (adapterView, view, position12, id) -> {
                        // Like in Google Photos - long-clicking only adds, doesn't remove
                        mObsIdsToSync.add(id);

                        // Multiple-selection mode
                        setMultiSelectionMode(true);

                        return true;
                    };
                    mObservationsList.setOnItemLongClickListener(onLongClick);
                    mObservationsGrid.setOnItemLongClickListener(onLongClick);

                    mSyncingTopBar = layout.findViewById(R.id.syncing_top_bar);
                    mSyncingTopBar.setVisibility(View.GONE);
                    mSyncingStatus = layout.findViewById(R.id.syncing_status);
                    mCancelSync = layout.findViewById(R.id.cancel_sync);

                    if (mApp.getAutoSync()) {
                        // Auto sync
                        mCancelSync.setText(R.string.stop);
                    } else {
                        // Manual
                        mCancelSync.setText(R.string.upload);
                    }

                    mCancelSync.setOnClickListener(view -> {
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
                            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            ContextCompat.startForegroundService(ObservationListActivity.this, serviceIntent);

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

                    if ((mObservationListAdapter != null) && (mObservationListAdapter.getCursor() != null)) {
                        Cursor oldCursor = mObservationListAdapter.getCursor();
                        if (!oldCursor.isClosed()) oldCursor.close();
                    }
                    if ((mObservationGridAdapter != null) && (mObservationGridAdapter.getCursor() != null)) {
                        Cursor oldCursor = mObservationGridAdapter.getCursor();
                        if (!oldCursor.isClosed()) oldCursor.close();
                    }

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

                    mAddButton = layout.findViewById(R.id.add_observation);
                    mAddButtonText = layout.findViewById(R.id.add_observation_text);
                    mAddButton.setVisibility(View.VISIBLE);
                    mAddButtonText.setVisibility(mObservationListAdapter.getCount() > 0 ? View.GONE : View.VISIBLE);

                    OnClickListener onAddObs = v -> {
                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_START);
                        showNewObsMenu();
                    };

                    mAddButton.setOnClickListener(onAddObs);
                    mAddButtonText.setOnClickListener(onAddObs);


                    if (mApp.loggedIn() && mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                        // Show a "downloading ..." message instead of "no observations yet"
                        mObservationsEmpty.setText(R.string.downloading_observations);
                        mLoadingObservations.setVisibility(View.VISIBLE);
                        mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count).setVisibility(View.GONE);
                        mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading).setVisibility(View.VISIBLE);
                    }

                    break;
            }

            collection.addView(layout);

            refreshViewState();

            return layout;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, @NotNull Object view) {
            collection.removeView((View) view);
        }
        @Override
        public boolean isViewFromObject(@NotNull View view, @NotNull Object object) {
            return view == object;
        }
    }

    private void deleteSelectedObservations() {
        List<Long> remoteIdsToDelete = new ArrayList<>();

        for (Long obsId : mObsIdsToSync) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), obsId);
            Cursor cursor = getContentResolver().query(uri, Observation.PROJECTION, null, null, null);
            Observation observation = new Observation(cursor);
            cursor.close();

            if (observation.id == null) {
                // Local observation (not yet synced) - delete locally only

                getContentResolver().delete(uri, null, null);

                // Delete any observation photos taken with it
                int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_observation_id=?", new String[]{observation._id.toString()});
                int count2 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_uuid=?", new String[]{observation.uuid});

                // Delete any observation sounds taken with it
                int count3 = getContentResolver().delete(ObservationSound.CONTENT_URI, "_observation_id=?", new String[]{observation._id.toString()});
                int count4 = getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_uuid=?", new String[]{observation.uuid});

                Logger.tag(TAG).debug("deleteSelectedObservations: " + count1 + ":" + count2 + ":" + count3 + ":" + count4);

            } else {
                // Need to remotely delete
                remoteIdsToDelete.add(obsId);
                // Mark for deletion (so we'll later on sync the deletion)
                ContentValues cv = observation.getContentValues();
                cv.put(Observation.IS_DELETED, 1);
                Logger.tag(TAG).debug("delete: Update: " + uri + ":" + cv);
                getContentResolver().update(uri, cv, null, null);
            }
        }

        if (remoteIdsToDelete.size() > 0) {
            Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_OBSERVATIONS, null, ObservationListActivity.this, INaturalistService.class);
            Long[] obsIds = new Long[remoteIdsToDelete.size()];
            remoteIdsToDelete.toArray(obsIds);
            serviceIntent.putExtra(INaturalistService.OBS_IDS_TO_DELETE, ArrayUtils.toPrimitive(obsIds));
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ContextCompat.startForegroundService(ObservationListActivity.this, serviceIntent);

        }

        setMultiSelectionMode(false);
    }


    // Method to add a TabHost
    private void addTab(int position, View tabContent) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);
        if (tab != null) {
            tab.setCustomView(tabContent);
        } else {
            Logger.tag(TAG).warn("Cannot set view on null tab at pos " + position);
        }
    }

    private View createTabContent(String tabName, int count) {
        ViewGroup view = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.my_observations_tab, null);
        TextView countText = view.findViewById(R.id.count);
        TextView tabNameText = view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        return view;
    }

    // Tabs Creation
    private void initializeTabs() {
        mTabLayout = findViewById(R.id.tabs);
        mViewPager = findViewById(R.id.view_pager);

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
                View tabView = tab.getCustomView();
                if (tabView == null) return;
                TextView tabNameText = tabView.findViewById(R.id.tab_name);
                if (tabNameText == null) return;

                tabNameText.setTextColor(Color.parseColor("#000000"));

                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View tabView = tab.getCustomView();
                if (tabView == null) return;
                TextView tabNameText = tabView.findViewById(R.id.tab_name);
                if (tabNameText == null) return;

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

        switch (mViewType) {
            case VIEW_TYPE_OBSERVATIONS:
                tabListener.onTabSelected(mTabLayout.getTabAt(0));
                break;
            case VIEW_TYPE_SPECIES:
                tabListener.onTabSelected(mTabLayout.getTabAt(1));
                break;
            case VIEW_TYPE_IDENTIFICATIONS:
                tabListener.onTabSelected(mTabLayout.getTabAt(2));
                break;
        }
    }


    private class ObservationSyncProgressReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            if (extras == null) {
                Logger.tag(TAG).warn("Null extras in syncProgressReceiver");
                return;
            }

            int obsId = extras.getInt(INaturalistService.OBSERVATION_ID);
            int obsIdInternal = extras.getInt(INaturalistService.OBSERVATION_ID_INTERNAL);
            float progress = extras.getFloat(INaturalistService.PROGRESS);

            if ((mObservationListAdapter == null) || (mObservationGridAdapter == null)) {
                return;
            }

            Logger.tag(TAG).debug(String.format(Locale.ENGLISH,
                    "Updating progress for %d / %d: %f", obsId, obsIdInternal, progress));

            mObservationListAdapter.updateProgress(progress);
            mObservationListAdapter.notifyDataSetChanged();
            mObservationGridAdapter.updateProgress(progress);
            mObservationGridAdapter.notifyDataSetChanged();

            mObservationListAdapter.refreshCursor();
            mObservationGridAdapter.refreshCursor();
        }
    }

    private class UserDetailsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            String username = null;
            if (extras != null) {
                username = extras.getString(INaturalistService.USERNAME);
            }

            if ((username == null) || (mApp.currentUserLogin() == null) || !username.equals(mApp.currentUserLogin())) {
                // Results returned for another user
                return;
            }

            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_user_details), error));
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            Object object;
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
            } else {
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

            ArrayList<JSONObject> resultsArray = new ArrayList<>();

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

        @Nullable
        private String actionToResultsParam(String action) {
            switch (action) {
                case INaturalistService.USER_DETAILS_RESULT:
                    return INaturalistService.USER;
                case INaturalistService.SPECIES_COUNT_RESULT:
                    return INaturalistService.SPECIES_COUNT_RESULT;
                case INaturalistService.USER_OBSERVATIONS_RESULT:
                    return INaturalistService.OBSERVATIONS;
                case INaturalistService.IDENTIFICATIONS_RESULT:
                    return INaturalistService.IDENTIFICATIONS;
                default:
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
            if (intent.getAction() == null ||
                    !intent.getAction().equals(INaturalistService.UPDATES_RESULT)) {
                return;
            }

            Bundle extras = intent.getExtras();
            if (extras == null || extras.getString("error") != null) {
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

    private void setMultiSelectionMode(boolean mode) {
        if (!mode) {
            mObsIdsToSync.clear();
        }

        mMultiSelectionMode = mode;

        mObservationListAdapter.setSelectedObservations(mObsIdsToSync);
        mObservationGridAdapter.setSelectedObservations(mObsIdsToSync);

        mObservationListAdapter.setMultiSelectionMode(mode);
        mObservationGridAdapter.setMultiSelectionMode(mode);

        mObservationListAdapter.refreshCursor();
        mObservationGridAdapter.refreshCursor();

        mAddButton.setVisibility(mode ? View.GONE : View.VISIBLE);
        mSyncingTopBar.setVisibility(mode ? View.GONE : View.VISIBLE);

        mTabLayout.setVisibility(mode ? View.GONE : View.VISIBLE);

        if (mode) {
            ActionBar ab = getSupportActionBar();
            if (ab == null) Logger.tag(TAG).warn("Null ActionBar when entering multi-select");
            else {
                ab.setCustomView(R.layout.multi_selection_action_bar);
                ab.setDisplayShowCustomEnabled(true);
                ab.setDisplayHomeAsUpEnabled(false);
            }
            
            mMultiSelectionObsCount = getSupportActionBar().getCustomView().findViewById(R.id.observation_count);
            View exitMultiObs = getSupportActionBar().getCustomView().findViewById(R.id.exit);

            exitMultiObs.setOnClickListener(view -> setMultiSelectionMode(false));


            View uploadMultiObs = getSupportActionBar().getCustomView().findViewById(R.id.sync);
            uploadMultiObs.setOnClickListener(view -> {
                // Sync those selected observations only
                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                Long[] obsIds = new Long[mObsIdsToSync.size()];
                mObsIdsToSync.toArray(obsIds);
                serviceIntent.putExtra(INaturalistService.OBS_IDS_TO_SYNC, ArrayUtils.toPrimitive(obsIds));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                ContextCompat.startForegroundService(ObservationListActivity.this, serviceIntent);

                // Exit multi-batch mode immediately
                setMultiSelectionMode(false);
            });

            View deleteMultiObs = getSupportActionBar().getCustomView().findViewById(R.id.delete);
            deleteMultiObs.setOnClickListener(view -> {
                // User chose to delete specific observations
                int numObsToDelete = mObsIdsToSync.size();
                mHelper.confirm(
                        getString(R.string.are_you_sure),
                        getResources().getQuantityString(R.plurals.are_you_sure_you_want_to_delete_observations, numObsToDelete, numObsToDelete),
                        (dialogInterface, i) -> deleteSelectedObservations(),
                        (dialogInterface, i) -> {},
                        getString(R.string.yes),
                        getString(R.string.no)
                );
            });

            mMenu.clear();
        } else {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setDisplayShowCustomEnabled(false);
                ab.setDisplayHomeAsUpEnabled(true);
            }

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.my_observations_menu, mMenu);
            refreshGridListMenuIcon();
        }


        refreshSyncBar();
    }

}
