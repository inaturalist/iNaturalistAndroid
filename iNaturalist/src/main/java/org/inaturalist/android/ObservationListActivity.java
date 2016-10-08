package org.inaturalist.android;

import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.inaturalist.android.INaturalistApp.INotificationCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.cocosw.bottomsheet.BottomSheet;
import com.flurry.android.FlurryAgent;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshGridView;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Handler;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
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
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationListActivity extends BaseFragmentActivity implements INotificationCallback, DialogInterface.OnClickListener {
	public static String TAG = "INAT:ObservationListActivity";

    private boolean[] mIsGrid = new boolean[] { false, false, false };

	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private int mLastIndex;
	private int mLastTop;

    private ViewGroup mSyncingTopBar;

	private ObservationCursorAdapter mObservationListAdapter;
    private ObservationCursorAdapter mObservationGridAdapter;

	private ActivityHelper mHelper;

	private String mLastMessage;

	private static final int COMMENTS_IDS_REQUEST_CODE = 100;

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
    private UserIdentificationsAdapter mIdentificationsAdapter;

    private BetterJSONObject mUser;
    private UserDetailsReceiver mUserDetailsReceiver;


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

            mObservationsList.onRefreshComplete();
            mObservationsList.refreshDrawableState();
            mObservationsGrid.onRefreshComplete();
            mObservationsGrid.refreshDrawableState();

            mObservationListAdapter.refreshCursor();
            mObservationGridAdapter.refreshCursor();
            refreshSyncBar();

            if (mApp.loggedIn() && !mApp.getIsSyncing() && (mObservationListAdapter.getCount() == 0)) {
                // Show a "no observations" message
                ((TextView)findViewById(android.R.id.empty)).setText(R.string.no_observations_yet);
            }

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(mObservationListAdapter.getCount()));

            mSyncRequested = false;

            if (!mApp.getIsSyncing()) {
                if ((intent != null) && (!intent.getBooleanExtra(INaturalistService.SYNC_CANCELED, false))) {
                    // Sync finished
                    mUserCanceledSync = false;
                    mSyncingTopBar.setVisibility(View.GONE);
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
            // Auto sync is on - no need for manual sync
            if (mSyncingTopBar != null) mSyncingTopBar.setVisibility(View.GONE);
            return;
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
            "((photo_url IS NULL) AND (_updated_at IS NOT NULL) AND (_synced_at IS NOT NULL) AND (_updated_at > _synced_at))", 
            null, 
            ObservationPhoto._ID);
        photoSyncCount = opc.getCount();
        opc.close();

        if (mSyncingTopBar != null) {
            if ((syncCount > 0) || (photoSyncCount > 0)) {
                mSyncingStatus.setText(String.format(getResources().getString(R.string.sync_x_observations), (syncCount > 0 ? syncCount : photoSyncCount)));
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
            mViewType = VIEW_TYPE_OBSERVATIONS;

            if (mApp.loggedIn()) {
                getUserDetails(INaturalistService.ACTION_GET_SPECIFIC_USER_DETAILS);
                getUserDetails(INaturalistService.ACTION_GET_USER_IDENTIFICATIONS);
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
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(mObservationListAdapter.getCount()));
                mLoadingObservations.setVisibility(View.GONE);

                if (mIsGrid[0]) {
                    mObservationsGrid.setVisibility(View.VISIBLE);
                    mObservationsList.setVisibility(View.GONE);
                } else {
                    mObservationsGrid.setVisibility(View.GONE);
                    mObservationsList.setVisibility(View.VISIBLE);
                }
            }

            mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.separator).setVisibility(View.GONE);

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
                    mSpeciesList.setVisibility(View.VISIBLE);
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

                    mIdentificationsAdapter = new UserIdentificationsAdapter(this, mIdentifications, mApp.currentUserLogin());
                    mIdentificationsList.setAdapter(mIdentificationsAdapter);
                    mIdentificationsList.setVisibility(View.VISIBLE);
                }
            }
        }


        if (mMenu != null) {
            if (!mIsGrid[0]) {
                mMenu.getItem(0).setIcon(R.drawable.grid_view_gray);
            } else {
                mMenu.getItem(0).setIcon(R.drawable.list_view_gray);
            }

            if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
                mMenu.getItem(0).setVisible(true);
            } else {
                mMenu.getItem(0).setVisible(false);
            }
        }
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

                c = getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION, "_synced_at IS NULL", null, ObservationPhoto.DEFAULT_SORT_ORDER);
                photoSyncCount = c.getCount();
                c.close();
            }

            // Trigger a sync (in case of auto-sync and unsynced obs OR when having old-style observations)
            if (hasOldObs || (((syncCount > 0) || (photoSyncCount > 0)) && (!mUserCanceledSync))) {
                mSyncRequested = true;
                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                startService(serviceIntent);

                mSyncingStatus.setText(R.string.syncing);
                mSyncingTopBar.setVisibility(View.VISIBLE);
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
        filter.addAction(INaturalistService.LIFE_LIST_RESULT);
        filter.addAction(INaturalistService.USER_OBSERVATIONS_RESULT);
        filter.addAction(INaturalistService.IDENTIFICATIONS_RESULT);
        registerReceiver(mUserDetailsReceiver, filter);


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
            if (mObservationGridAdapter != null) mObservationGridAdapter.refreshCursor();

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(mObservationListAdapter.getCount()));
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
    
    private class ObservationCursorAdapter extends SimpleCursorAdapter {
        private int mDimension;
        private HashMap<Long, String[]> mPhotoInfo = new HashMap<Long, String[]>();
        private boolean mIsGrid;
        
        public ObservationCursorAdapter(Context context, Cursor c, boolean isGrid, int dimension) {
            super(context, isGrid ? R.layout.observation_grid_item : R.layout.list_item, c, new String[] {}, new int[] {});
            mIsGrid = isGrid;
            mDimension = dimension;
            getPhotoInfo();
        }
        
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public void refreshCursor() {
        	SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        	String login = prefs.getString("username", null);
        	String conditions = "(_synced_at IS NULL";
        	if (login != null) {
        		conditions += " OR user_login = '" + login + "'";
        	}
        	conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations
        	
        	Cursor newCursor = getContentResolver().query(getIntent().getData(), Observation.PROJECTION, 
        			conditions, null, Observation.DEFAULT_SORT_ORDER);

        	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
        		Cursor oldCursor = swapCursor(newCursor);
        		if ((oldCursor != null) && (!oldCursor.isClosed())) oldCursor.close();
        	} else {
        		changeCursor(newCursor);
        	}
        }
        
        /**
         * Retrieves photo ids and orientations for photos associated with the listed observations.
         */
        public void getPhotoInfo() {
            Cursor c = getCursor();
            int originalPosition = c.getPosition();
            if (c.getCount() == 0) return;
            
            c.moveToFirst();
            ArrayList<Long> obsIds = new ArrayList<Long>();
            ArrayList<Long> obsExternalIds = new ArrayList<Long>();
            ArrayList<Long> photoIds = new ArrayList<Long>();
            while (!c.isAfterLast()) {
                obsIds.add(c.getLong(c.getColumnIndexOrThrow(Observation._ID)));
                try {
                	obsExternalIds.add(c.getLong(c.getColumnIndexOrThrow(Observation.ID)));
                } catch (Exception exc) { }
                c.moveToNext();
            }

            c.moveToPosition(originalPosition);

            // Add any online-only photos
            Cursor onlinePc = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    new String[]{ObservationPhoto._ID, ObservationPhoto._OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL, ObservationPhoto.PHOTO_FILENAME},
                    "(_observation_id IN (" + StringUtils.join(obsIds, ',') + ") OR observation_id IN (" + StringUtils.join(obsExternalIds, ',') + ")  )",
                    null,
                    ObservationPhoto.DEFAULT_SORT_ORDER);
            onlinePc.moveToFirst();
            while (!onlinePc.isAfterLast()) {
                Long obsId = onlinePc.getLong(onlinePc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
                String photoUrl = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                String photoFilename = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));

                if (!mPhotoInfo.containsKey(obsId)) {
                    mPhotoInfo.put(
                            obsId,
                            new String[] {
                                    photoFilename,
                                    null,
                                    photoUrl,
                                    null,
                                    null
                            });
                }
                onlinePc.moveToNext();
            }
            
            onlinePc.close();
        }

        public void refreshPhotoInfo() {
            mPhotoInfo = new HashMap<Long, String[]>();
            getPhotoInfo();
        }

        public void refreshPhotoInfo(long obsId) {
            if (mPhotoInfo.containsKey(obsId)) mPhotoInfo.remove(obsId);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            Cursor c = this.getCursor();
            if (c.getCount() == 0) {
                return view;
            }
            c.moveToPosition(position);

            ImageView obsImage = (ImageView) view.findViewById(R.id.observation_pic);
            TextView speciesGuess = (TextView) view.findViewById(R.id.species_guess);
            TextView dateObserved = (TextView) view.findViewById(R.id.date);
            ViewGroup commentIdContainer = (ViewGroup) view.findViewById(R.id.comment_id_container);

            ImageView commentIcon = (ImageView) view.findViewById(R.id.comment_pic);
            ImageView idIcon = (ImageView) view.findViewById(R.id.id_pic);
            TextView commentCount = (TextView) view.findViewById(R.id.comment_count);
            TextView idCount = (TextView) view.findViewById(R.id.id_count);

            TextView placeGuess = (TextView) view.findViewById(R.id.place_guess);
            ImageView locationIcon = (ImageView) view.findViewById(R.id.location_icon);

            View progress = view.findViewById(R.id.progress);


            final Long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
            final Long externalObsId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
            String placeGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.PLACE_GUESS));
            Double latitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LATITUDE));
            Double longitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LONGITUDE));

            if (mIsGrid) {
                mDimension = mObservationsGrid.getColumnWidth();
                obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                progress.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
            }

            refreshPhotoInfo(obsId);
            getPhotoInfo();

            String[] photoInfo = mPhotoInfo.get(obsId);
            
            if (photoInfo == null) {
            	// Try getting the external observation photo info
            	photoInfo = mPhotoInfo.get(externalObsId);
            }

            String iconicTaxonName = c.getString(c.getColumnIndexOrThrow(Observation.ICONIC_TAXON_NAME));
            int iconResource = 0;
            if (iconicTaxonName == null) {
                iconResource = R.drawable.iconic_taxon_unknown;
            } else if (iconicTaxonName.equals("Animalia")) {
                iconResource = R.drawable.iconic_taxon_animalia;
            } else if (iconicTaxonName.equals("Plantae")) {
                iconResource = R.drawable.iconic_taxon_plantae;
            } else if (iconicTaxonName.equals("Chromista")) {
                iconResource = R.drawable.iconic_taxon_chromista;
            } else if (iconicTaxonName.equals("Fungi")) {
                iconResource = R.drawable.iconic_taxon_fungi;
            } else if (iconicTaxonName.equals("Protozoa")) {
                iconResource = R.drawable.iconic_taxon_protozoa;
            } else if (iconicTaxonName.equals("Actinopterygii")) {
                iconResource = R.drawable.iconic_taxon_actinopterygii;
            } else if (iconicTaxonName.equals("Amphibia")) {
                iconResource = R.drawable.iconic_taxon_amphibia;
            } else if (iconicTaxonName.equals("Reptilia")) {
                iconResource = R.drawable.iconic_taxon_reptilia;
            } else if (iconicTaxonName.equals("Aves")) {
                iconResource = R.drawable.iconic_taxon_aves;
            } else if (iconicTaxonName.equals("Mammalia")) {
                iconResource = R.drawable.iconic_taxon_mammalia;
            } else if (iconicTaxonName.equals("Mollusca")) {
                iconResource = R.drawable.iconic_taxon_mollusca;
            } else if (iconicTaxonName.equals("Insecta")) {
                iconResource = R.drawable.iconic_taxon_insecta;
            } else if (iconicTaxonName.equals("Arachnida")) {
                iconResource = R.drawable.iconic_taxon_arachnida;
            } else {
                iconResource = R.drawable.iconic_taxon_unknown;
            }

            if (photoInfo != null) {
                obsImage.setPadding(0, 0, 0, 0);
                obsImage.clearColorFilter();
                String photoFilename = photoInfo[0];

                if (photoInfo[2] != null) {
                    // Online-only photo
                    UrlImageViewHelper.setUrlDrawable(obsImage, photoInfo[2], iconResource, new UrlImageViewCallback() {
                        @Override
                        public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            if (mIsGrid) {
                                imageView.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                            }
                        }

                        @Override
                        public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            return ImageUtils.getRoundedCornerBitmap(ImageUtils.centerCropBitmap(loadedBitmap));
                        }
                    });
                    
                } else {
                    // Offline photo
                    BitmapWorkerTask task = new BitmapWorkerTask(obsImage);
                    task.execute(photoFilename, String.valueOf(iconResource));
                }
            } else {
                obsImage.setImageResource(iconResource);

                // 5dp -> pixels
                int px = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
                obsImage.setPadding(px, px, px, px);
                obsImage.setColorFilter(Color.parseColor("#8C8C8C"));
            }
                
            
            Long observationTimestamp = c.getLong(c.getColumnIndexOrThrow(Observation.OBSERVED_ON));

            if (!mIsGrid) {
                if (observationTimestamp == 0) {
                    // No observation date set - don't show it
                    dateObserved.setVisibility(View.INVISIBLE);
                } else {
                    dateObserved.setVisibility(View.VISIBLE);
                    Timestamp observationDate = new Timestamp(observationTimestamp);
                    dateObserved.setText(CommentsIdsAdapter.formatIdDate(observationDate));
                }
            }

            Long commentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.COMMENTS_COUNT));
            Long idsCount = c.getLong(c.getColumnIndexOrThrow(Observation.IDENTIFICATIONS_COUNT));
            Long lastCommentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_COMMENTS_COUNT));
            Long lastIdCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_IDENTIFICATIONS_COUNT));

            if (commentsCount + idsCount == 0) {
                // No comments/IDs - don't display the indicator
                commentIdContainer.setVisibility(View.INVISIBLE);
                commentIdContainer.setClickable(false);
            } else {
                commentIdContainer.setClickable(true);
                commentIdContainer.setVisibility(View.VISIBLE);

                if ((lastCommentsCount == null) || (lastCommentsCount < commentsCount) ||
                        (lastIdCount == null) || (lastIdCount < idsCount)) {
                    // There are unread comments/IDs
                    if (mIsGrid) {
                        commentIdContainer.setBackgroundColor(Color.parseColor("#EA118D"));
                    } else {
                        commentCount.setTextColor(Color.parseColor("#EA118D"));
                        idCount.setTextColor(Color.parseColor("#EA118D"));

                        commentIcon.setColorFilter(Color.parseColor("#EA118D"));
                        idIcon.setColorFilter(Color.parseColor("#EA118D"));
                    }
                } else {
                    if (mIsGrid) {
                        commentIdContainer.setBackgroundColor(Color.parseColor("#00ffffff"));
                    } else {
                        commentCount.setTextColor(Color.parseColor("#959595"));
                        idCount.setTextColor(Color.parseColor("#959595"));

                        commentIcon.setColorFilter(Color.parseColor("#707070"));
                        idIcon.setColorFilter(Color.parseColor("#707070"));
                    }
                }

                if (commentsCount > 0) {
                    commentCount.setText(String.valueOf(commentsCount));
                    commentCount.setVisibility(View.VISIBLE);
                    commentIcon.setVisibility(View.VISIBLE);
                } else {
                    commentCount.setVisibility(View.GONE);
                    commentIcon.setVisibility(View.GONE);
                }

                if (idsCount > 0) {
                    idCount.setText(String.valueOf(idsCount));
                    idCount.setVisibility(View.VISIBLE);
                    idIcon.setVisibility(View.VISIBLE);
                } else {
                    idCount.setVisibility(View.GONE);
                    idIcon.setVisibility(View.GONE);
                }

                commentIdContainer.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!isNetworkAvailable()) {
                            Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Show the comments/IDs for the observation
                        Uri uri = ContentUris.withAppendedId(getIntent().getData(), obsId);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri, ObservationListActivity.this, ObservationViewerActivity.class);
                        intent.putExtra(ObservationViewerActivity.SHOW_COMMENTS, true);
                        startActivityForResult(intent, COMMENTS_IDS_REQUEST_CODE);
                    }
                });
            }

            Long syncedAt = c.getLong(c.getColumnIndexOrThrow(Observation._SYNCED_AT));
            Long updatedAt = c.getLong(c.getColumnIndexOrThrow(Observation._UPDATED_AT));
            Boolean syncNeeded = (syncedAt == null) || (updatedAt > syncedAt); 
            
            // if there's a photo and it is local
            if (syncNeeded == false && 
                    photoInfo != null && 
                    photoInfo[2] == null &&  
                    photoInfo[3] != null) {
                if (photoInfo[4] == null) {
                    syncNeeded = true;
                } else {
                    Long photoSyncedAt = Long.parseLong(photoInfo[4]);
                    Long photoUpdatedAt = Long.parseLong(photoInfo[3]);
                    if (photoUpdatedAt > photoSyncedAt) {
                        syncNeeded = true;
                    }
                }
            }

            if (!syncNeeded) {
                // See if it's an existing observation with a new photo:w

                Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                        new String[]{
                                ObservationPhoto._ID,
                                ObservationPhoto._OBSERVATION_ID,
                                ObservationPhoto._PHOTO_ID,
                                ObservationPhoto.PHOTO_URL,
                                ObservationPhoto._UPDATED_AT,
                                ObservationPhoto._SYNCED_AT
                        },
                        "_observation_id = ? AND photo_url IS NULL AND _synced_at IS NULL",
                        new String[] { String.valueOf(obsId) },
                        ObservationPhoto._ID);
                if (opc.getCount() > 0) {
                    syncNeeded = true;
                }
                opc.close();
            }


            if (!mIsGrid) {
                if ((placeGuessValue == null) || (placeGuessValue.length() == 0)) {
                    if ((longitude == null) || (latitude == null)) {
                        // Show coordinates instead
                        placeGuess.setText(String.format(getString(R.string.location_coords_no_acc),
                                String.format("%.4f...", latitude), String.format("%.4f...", longitude)));
                    } else {
                        // No location at all
                        placeGuess.setText(R.string.no_location);
                    }
                } else {
                    placeGuess.setText(placeGuessValue);
                }
            }


            String speciesGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.SPECIES_GUESS));
            String preferredCommonName = c.getString(c.getColumnIndexOrThrow(Observation.PREFERRED_COMMON_NAME));
            progress.setVisibility(View.GONE);
            if (!mIsGrid) {
                placeGuess.setTextColor(Color.parseColor("#666666"));
                dateObserved.setVisibility(View.VISIBLE);
                speciesGuess.setTextColor(Color.parseColor("#000000"));
            }

            if (preferredCommonName != null) {
                speciesGuess.setText(preferredCommonName);
            } else if ((speciesGuessValue != null) && (speciesGuessValue.trim().length() > 0)) {
                speciesGuess.setText("\"" + speciesGuess + "\"");
            } else {
                speciesGuess.setText(R.string.unknown_species);
            }


            boolean hasErrors = (mApp.getErrorsForObservation(externalObsId.intValue()).length() > 0);
            if (hasErrors)  {
                view.setBackgroundColor(Color.parseColor("#F3D3DA"));
                if (!mIsGrid) {
                    placeGuess.setText(R.string.needs_your_attention);
                    locationIcon.setVisibility(View.GONE);
                }
            } else {
                view.setBackgroundColor(Color.parseColor("#FFFFFF"));
                if (!mIsGrid) {
                    locationIcon.setVisibility(View.VISIBLE);
                }
            }

            if (syncNeeded) {
                // This observations needs to be synced

                if (mApp.getObservationIdBeingSynced() == obsId) {
                    // Observation is currently being uploaded
                    view.setBackgroundColor(Color.parseColor("#E3EDCD"));

                    if (!mIsGrid) {
                        placeGuess.setText(R.string.uploading);
                        placeGuess.setTextColor(Color.parseColor("#74Ac00"));
                        locationIcon.setVisibility(View.GONE);
                        dateObserved.setVisibility(View.GONE);
                    }

                    progress.setVisibility(View.VISIBLE);
                    commentIdContainer.setVisibility(View.GONE);
                } else {
                    // Observation is waiting to be uploaded
                    if (!hasErrors) {
                        view.setBackgroundColor(Color.parseColor("#E3EDCD"));
                        if (!mIsGrid) {
                            placeGuess.setText(R.string.waiting_to_upload);
                            locationIcon.setVisibility(View.GONE);
                        }
                    }
                }
            } else {
                if (!hasErrors)
                    view.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }

            return view;
        }

        // Should the specified observation be locked for editing (e.g. it's currently being uploaded)
        public boolean isLocked(Uri uri) {
            Cursor c = managedQuery(uri, Observation.PROJECTION, null, null, null);
            Observation obs = new Observation(c);

            Integer obsId = obs._id;
            String[] photoInfo = mPhotoInfo.get(obsId);
            Timestamp syncedAt = obs._synced_at;
            Timestamp updatedAt = obs._updated_at;
            Boolean syncNeeded = (syncedAt == null) || (updatedAt.after(syncedAt));

            // if there's a photo and it is local
            if (syncNeeded == false &&
                    photoInfo != null &&
                    photoInfo[2] == null &&
                    photoInfo[3] != null) {
                if (photoInfo[4] == null) {
                    syncNeeded = true;
                } else {
                    Long photoSyncedAt = Long.parseLong(photoInfo[4]);
                    Long photoUpdatedAt = Long.parseLong(photoInfo[3]);
                    if (photoUpdatedAt > photoSyncedAt) {
                        syncNeeded = true;
                    }
                }
            }

            if (!syncNeeded) {
                // See if it's an existing observation with a new photo:w
                Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                        new String[]{
                                ObservationPhoto._ID,
                                ObservationPhoto._OBSERVATION_ID,
                                ObservationPhoto._PHOTO_ID,
                                ObservationPhoto.PHOTO_URL,
                                ObservationPhoto._UPDATED_AT,
                                ObservationPhoto._SYNCED_AT
                        },
                        "_observation_id = ? AND photo_url IS NULL AND _synced_at IS NULL",
                        new String[] { String.valueOf(obsId) },
                        ObservationPhoto._ID);
                if (opc.getCount() > 0) {
                    syncNeeded = true;
                }
                opc.close();
            }

            if (mApp.getObservationIdBeingSynced() == obsId) {
                // Observation is currently being uploaded - is locked!
                return true;
            } else {
                if (!syncNeeded) {
                    // Item hasn't changed (shouldn't be locked)
                    return false;
                }

                if (!mApp.getAutoSync() || !isNetworkAvailable()) {
                    // Allow editing if not in auto sync mode or when network is not available
                    return false;
                } else {
                    return true;
                }
            }
        }
        
        private void refreshCommentsIdSize(final TextView view, Long value) {
            ViewTreeObserver observer = view.getViewTreeObserver();
            // Make sure the height and width of the rectangle are the same (i.e. a square)
            observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
				@Override
                public void onGlobalLayout() {
                    int dimension = view.getHeight();
                    ViewGroup.LayoutParams params = view.getLayoutParams();

                    if (dimension > view.getWidth()) {
                        // Only resize if there's enough room
                        params.width = dimension;
                        view.setLayoutParams(params);
                    }

                    ViewTreeObserver observer = view.getViewTreeObserver();
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        observer.removeGlobalOnLayoutListener(this);
                    } else {
                        observer.removeOnGlobalLayoutListener(this);
                    }  
                }
            });

            view.setText(value.toString());
        }
 
        
    }
    
	@Override
	public void onNotification(String title, final String content) {
		mLastMessage = content;

		runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSyncingStatus.setText(content);
                int visibility = View.GONE;
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


    // For caching observation thumbnails
    private HashMap<String, Bitmap> mObservationThumbnails = new HashMap<>();

    // Used for loading and processing the observation photo in the background (as to not block the UI)
    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private String mFilename = null;
        private int mIconResource;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mImageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            mFilename = params[0];
            mIconResource = Integer.valueOf(params[1]);

            Bitmap bitmapImage;
            if (mObservationThumbnails.containsKey(mFilename)) {
                // Load from cache
                bitmapImage = mObservationThumbnails.get(mFilename);
            } else {
                if (mImageViewReference != null) {
                    runOnUiThread(new Runnable() {
                                      @Override
                                      public void run() {
                                          mImageViewReference.get().setImageResource(mIconResource);
                                      }
                                  }
                    );
                }

                // Decode into a thumbnail
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = ImageUtils.calculateInSampleSize(options, 100, 100);

                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false;
                // This decreases in-memory byte-storage per pixel
                options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                bitmapImage = BitmapFactory.decodeFile(mFilename, options);
                bitmapImage = ImageUtils.rotateAccordingToOrientation(bitmapImage, mFilename);
                bitmapImage = ImageUtils.centerCropBitmap(bitmapImage);
                bitmapImage = ImageUtils.getRoundedCornerBitmap(bitmapImage);

                mObservationThumbnails.put(mFilename, bitmapImage);
            }

            return bitmapImage;
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (mImageViewReference != null && bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
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

        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            mMenu.getItem(0).setVisible(true);
        } else {
            mMenu.getItem(0).setVisible(false);
        }

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.observation_view_type:
                // TODO
                mIsGrid[0] = !mIsGrid[0];

                mLastIndex = 0;
                mLastTop = 0;
                refreshViewState();
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
                    mIdentificationsGrid = (PullToRefreshGridViewExtended) layout.findViewById(R.id.grid);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    initPullToRefreshList(mIdentificationsList, layout);
                    initPullToRefreshList(mIdentificationsGrid, layout);

                    mIdentificationsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            Intent intent = new Intent(ObservationListActivity.this, ObservationViewerActivity.class);
                            intent.putExtra("observation", item.optJSONObject("observation").toString());
                            intent.putExtra("read_only", true);
                            intent.putExtra("reload", true);
                            startActivity(intent);
                        }
                    });

                    break;

                case 1:
                    mLoadingSpecies = (ProgressBar) layout.findViewById(R.id.loading);
                    mSpeciesEmpty = (TextView) layout.findViewById(R.id.empty);
                    mSpeciesEmpty.setText(R.string.no_species_found);
                    mSpeciesList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mSpeciesGrid = (PullToRefreshGridViewExtended) layout.findViewById(R.id.grid);

                    layout.findViewById(R.id.syncing_top_bar).setVisibility(View.GONE);
                    layout.findViewById(R.id.add_observation).setVisibility(View.GONE);

                    initPullToRefreshList(mSpeciesList, layout);
                    initPullToRefreshList(mSpeciesGrid, layout);

                    mSpeciesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            Intent intent = new Intent(ObservationListActivity.this, GuideTaxonActivity.class);
                            intent.putExtra("taxon", new BetterJSONObject(item));
                            intent.putExtra("guide_taxon", false);
                            intent.putExtra("show_add", false);
                            intent.putExtra("download_taxon", true);
                            startActivity(intent);
                        }
                    });

                    break;

                case 0:
                    mLoadingObservations = (ProgressBar) layout.findViewById(R.id.loading);
                    mObservationsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mObservationsList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mObservationsGrid = (PullToRefreshGridViewExtended) layout.findViewById(R.id.grid);

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
                            } else {
                                // User chose to resume sync
                                if (!isNetworkAvailable()) {
                                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                                    return;
                                } else if (!isLoggedIn()) {
                                    // User not logged-in - redirect to onboarding screen
                                    startActivity(new Intent(ObservationListActivity.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                                    return;
                                }

                                mUserCanceledSync = false;
                                mApp.setCancelSync(false);
                                mCancelSync.setText(R.string.stop);
                                mSyncingStatus.setText(R.string.syncing);
                                // Re-sync
                                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
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

                    mObservationListAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor, false, 0);
                    mObservationGridAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor, true, mObservationsGrid.getColumnWidth());
                    mObservationsGrid.setAdapter(mObservationGridAdapter);
                    mObservationsList.setAdapter(mObservationListAdapter);

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
                    addButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new BottomSheet.Builder(ObservationListActivity.this).sheet(R.menu.observation_list_menu).listener(new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent;
                                    switch (which) {
                                        case R.id.camera:
                                            intent = new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class);
                                            intent.putExtra(ObservationEditor.TAKE_PHOTO, true);
                                            startActivity(intent);
                                            break;
                                        case R.id.upload_photo:
                                            intent = new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class);
                                            intent.putExtra(ObservationEditor.CHOOSE_PHOTO, true);
                                            startActivity(intent);
                                            break;
                                        case R.id.text:
                                            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class));
                                            break;
                                    }
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
                switch (position) {
                    case 2:
                        mViewType = VIEW_TYPE_IDENTIFICATIONS;
                        if (mMenu != null) mMenu.getItem(0).setVisible(false);
                        break;
                    case 1:
                        mViewType = VIEW_TYPE_SPECIES;
                        if (mMenu != null) mMenu.getItem(0).setVisible(false);
                        break;
                    case 0:
                    default:
                        mViewType = VIEW_TYPE_OBSERVATIONS;
                        if (mMenu != null) mMenu.getItem(0).setVisible(true);
                        break;
                }
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


    private class UserDetailsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
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
                if ((intent.getAction().equals(INaturalistService.USER_DETAILS_RESULT)) || (intent.getAction().equals(INaturalistService.LIFE_LIST_RESULT))) {
                    mTotalSpecies = 0;
                    mSpecies = new ArrayList<>();
                } else if (intent.getAction().equals(INaturalistService.IDENTIFICATIONS_RESULT)) {
                    mTotalIdentifications = 0;
                    mIdentifications = new ArrayList<>();
                }
                refreshViewState();
                return;
            }

            if (intent.getAction().equals(INaturalistService.USER_DETAILS_RESULT)) {
                // Extended user details
                mUser = (BetterJSONObject) object;

                mTotalIdentifications = mUser.getInt("identifications_count");

                // Retrieve the user's life list
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_LIFE_LIST, null, ObservationListActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.LIFE_LIST_ID, mUser.getInt("life_list_id"));
                startService(serviceIntent);
                return;
            } else if (intent.getAction().equals(INaturalistService.LIFE_LIST_RESULT)) {
                // Life list result (species)
                resultsObject = (BetterJSONObject) object;
                totalResults = resultsObject.getInt("total_entries");
                results = resultsObject.getJSONArray("listed_taxa").getJSONArray();
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

            if (intent.getAction().equals(INaturalistService.LIFE_LIST_RESULT)) {
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
            } else if (action.equals(INaturalistService.LIFE_LIST_RESULT)) {
                return INaturalistService.LIFE_LIST;
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

}
