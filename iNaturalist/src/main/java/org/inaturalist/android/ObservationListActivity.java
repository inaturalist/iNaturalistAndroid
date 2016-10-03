package org.inaturalist.android;

import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.inaturalist.android.INaturalistApp.INotificationCallback;

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
import android.support.v7.app.ActionBar;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationListActivity extends BaseFragmentActivity implements OnItemClickListener, INotificationCallback, DialogInterface.OnClickListener {
	public static String TAG = "INAT:ObservationListActivity";

	private PullToRefreshListView mPullRefreshListView;
    private PullToRefreshGridViewExtended mPullRefreshGridView;

    private boolean mIsGrid = false;

	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private int mLastIndex;
	private int mLastTop;
	private ActionBar mTopActionBar;

    private ViewGroup mSyncingTopBar;

	private ObservationCursorAdapter mListAdapter;
    private ObservationCursorAdapter mGridAdapter;

	private ActivityHelper mHelper;

	private String mLastMessage;

	private static final int COMMENTS_IDS_REQUEST_CODE = 100;

	private static final int OBSERVATION_LIST_LOADER = 0x01;
    private INaturalistApp mApp;
    private TextView mSyncingStatus;
    private TextView mCancelSync;

    private boolean mUserCanceledSync = false; // When the user chose to pause/stop sync while in auto sync mode
    private boolean mSyncRequested = false;
    private Menu mMenu;

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
            mPullRefreshListView.onRefreshComplete();
            mPullRefreshListView.refreshDrawableState();
            mPullRefreshGridView.onRefreshComplete();
            mPullRefreshGridView.refreshDrawableState();

            mListAdapter.refreshCursor();
            mGridAdapter.refreshCursor();
            refreshSyncBar();

            if (mApp.loggedIn() && !mApp.getIsSyncing() && (mListAdapter.getCount() == 0)) {
                // Show a "no observations" message
                ((TextView)findViewById(android.R.id.empty)).setText(R.string.no_observations_yet);
            }


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
            mSyncingTopBar.setVisibility(View.GONE);
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

    	if ((syncCount > 0) || (photoSyncCount > 0)) {
    		mSyncingStatus.setText(String.format(getResources().getString(R.string.sync_x_observations), (syncCount > 0 ? syncCount : photoSyncCount)));
    		mSyncingTopBar.setVisibility(View.VISIBLE);
            mUserCanceledSync = true; // To make it so that the button on the sync bar will trigger a sync
            mCancelSync.setText(R.string.upload);
    	} else {
    		mSyncingTopBar.setVisibility(View.GONE);
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

        mSyncingTopBar = (ViewGroup) findViewById(R.id.syncing_top_bar);
        mSyncingTopBar.setVisibility(View.GONE);
        mSyncingStatus = (TextView) findViewById(R.id.syncing_status);
        mCancelSync = (TextView) findViewById(R.id.cancel_sync);

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

        if (savedInstanceState != null) {
            mLastMessage = savedInstanceState.getString("mLastMessage");
            mUserCanceledSync = savedInstanceState.getBoolean("mUserCanceledSync");
            mIsGrid = savedInstanceState.getBoolean("mIsGrid");
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

        refreshSyncBar();

        mTopActionBar = getSupportActionBar();

        View addButton = (View) findViewById(R.id.add_observation);
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

        mPullRefreshListView = (PullToRefreshListView) findViewById(R.id.observations_list);
        mPullRefreshListView.getLoadingLayoutProxy().setPullLabel(getResources().getString(R.string.pull_to_refresh));
        mPullRefreshListView.getLoadingLayoutProxy().setReleaseLabel(getResources().getString(R.string.release_to_refresh));
        mPullRefreshListView.getLoadingLayoutProxy().setRefreshingLabel(getResources().getString(R.string.refreshing));
        mPullRefreshListView.setReleaseRatio(2.5f);

        mPullRefreshGridView = (PullToRefreshGridViewExtended) findViewById(R.id.observations_grid);
        mPullRefreshGridView.getLoadingLayoutProxy().setPullLabel(getResources().getString(R.string.pull_to_refresh));
        mPullRefreshGridView.getLoadingLayoutProxy().setReleaseLabel(getResources().getString(R.string.release_to_refresh));
        mPullRefreshGridView.getLoadingLayoutProxy().setRefreshingLabel(getResources().getString(R.string.refreshing));
        mPullRefreshGridView.setReleaseRatio(2.5f);


        // Set a listener to be invoked when the list should be refreshed.
        mPullRefreshListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                onRefreshView(false);
            }
        });
        mPullRefreshGridView.setOnRefreshListener(new OnRefreshListener<GridView>() {
            @Override
            public void onRefresh(PullToRefreshBase<GridView> refreshView) {
                onRefreshView(true);
            }
        });

        onDrawerCreate(savedInstanceState);
        
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);
        
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations
        
        final Cursor cursor = getContentResolver().query(getIntent().getData(), Observation.PROJECTION,
        		conditions, null, Observation.DEFAULT_SORT_ORDER);

        mListAdapter = new ObservationCursorAdapter(this, cursor, false, 0);
        mGridAdapter = new ObservationCursorAdapter(ObservationListActivity.this, cursor, true, mPullRefreshGridView.getColumnWidth());
        mPullRefreshGridView.setAdapter(mGridAdapter);


        mPullRefreshListView.setEmptyView(findViewById(android.R.id.empty));
        mPullRefreshListView.setAdapter(mListAdapter);
        mPullRefreshListView.setOnItemClickListener(this);

        mPullRefreshGridView.setEmptyView(findViewById(android.R.id.empty));
        mPullRefreshGridView.setAdapter(mGridAdapter);
        mPullRefreshGridView.setOnItemClickListener(this);

        triggerSyncIfNeeded();
        refreshViewState();
    }

    private void refreshViewState() {
        if (mIsGrid) {
            mPullRefreshGridView.setVisibility(View.VISIBLE);
            mPullRefreshListView.setVisibility(View.GONE);
        } else {
            mPullRefreshGridView.setVisibility(View.GONE);
            mPullRefreshListView.setVisibility(View.VISIBLE);
        }

        if (mMenu != null) {
            if (!mIsGrid) {
                mMenu.getItem(0).setIcon(R.drawable.grid_view_gray);
            } else {
                mMenu.getItem(0).setIcon(R.drawable.list_view_gray);
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
        AbsListView lv = mIsGrid ? mPullRefreshGridView.getRefreshableView() : mPullRefreshListView.getRefreshableView();
        mLastIndex = lv.getFirstVisiblePosition();
        View v = lv.getChildAt(0);
        mLastTop = (v == null) ? 0 : v.getTop();

        super.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();

        if (mIsGrid) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                GridView grid = mPullRefreshGridView.getRefreshableView();
                grid.setSelectionFromTop(mLastIndex, mLastTop);
            }
        } else {
            ListView lv = mPullRefreshListView.getRefreshableView();
            lv.setSelectionFromTop(mLastIndex, mLastTop);
        }
      
        refreshSyncBar();

        mListAdapter.refreshCursor();
        if (mGridAdapter != null) mGridAdapter.refreshCursor();

        INaturalistApp app = (INaturalistApp)(getApplication());
        if (app.getIsSyncing()) {
        	// We're still syncing
        	if ((mLastMessage != null) && (!mApp.getAutoSync())) mSyncingStatus.setText(mLastMessage);
        	app.setNotificationCallback(this);
            if (!app.getAutoSync()) mCancelSync.setText(R.string.stop);
        }

        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mApp.loggedIn() && mApp.getIsSyncing() && (mListAdapter.getCount() == 0)) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_observations), Toast.LENGTH_LONG).show();
                }
            }
        }, 100);


        if (mApp.loggedIn() && mApp.getIsSyncing() && (mListAdapter.getCount() == 0)) {
            // Show a "downloading ..." message instead of "no observations yet"
            ((TextView)findViewById(android.R.id.empty)).setText(R.string.downloading_observations);
        }

        triggerSyncIfNeeded();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mLastMessage != null) outState.putString("mLastMessage", mLastMessage);
        outState.putBoolean("mUserCanceledSync", mUserCanceledSync);
        outState.putBoolean("mIsGrid", mIsGrid);
        super.onSaveInstanceState(outState);
    }
 
    
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    } 
    
   
    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user.  The have clicked on one, so return it now.
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            if ((!mListAdapter.isLocked(uri)) || (mListAdapter.isLocked(uri) && !mApp.getIsSyncing())) {
                // Launch activity to view/edit the currently selected item
                startActivity(new Intent(Intent.ACTION_VIEW, uri, this, ObservationViewerActivity.class));
            }
        }
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
                mDimension = mPullRefreshGridView.getColumnWidth();
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
                if (mApp.loggedIn() && mApp.getIsSyncing() && (mListAdapter.getCount() == 0)) {
                    visibility = View.GONE;
                } else {
                    visibility = mApp.getIsSyncing() ? View.VISIBLE : View.GONE;
                }
                mSyncingTopBar.setVisibility(visibility);
                mListAdapter.refreshCursor();
                mGridAdapter.refreshCursor();
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


    private void onRefreshView(final boolean isGrid) {
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
                            if (!isGrid) {
                                mPullRefreshListView.onRefreshComplete();
                            } else {
                                mPullRefreshGridView.onRefreshComplete();
                            }
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

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.observation_view_type:
                mIsGrid = !mIsGrid;

                mLastIndex = 0;
                mLastTop = 0;
                refreshViewState();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
