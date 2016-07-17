package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.inaturalist.android.INaturalistApp.INotificationCallback;

import com.cocosw.bottomsheet.BottomSheet;
import com.flurry.android.FlurryAgent;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.NavUtils;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
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
	
	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private int mLastIndex;
	private int mLastTop;
	private ActionBar mTopActionBar;

	private TextView mSyncObservations;

	private ObservationCursorAdapter mAdapter;

	private TextView mTitleBar;

	private ActivityHelper mHelper;

	private String mLastMessage;

	private static final int COMMENTS_IDS_REQUEST_CODE = 100;

	private static final int OBSERVATION_LIST_LOADER = 0x01;
    private INaturalistApp mApp;

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

            mHelper.stopLoading();

            ObservationCursorAdapter adapter = mAdapter;
            adapter.refreshCursor();
            refreshSyncBar();

            if (mApp.loggedIn() && !mApp.getIsSyncing() && (mAdapter.getCount() == 0)) {
                // Show a "no observations" message
                ((TextView)findViewById(android.R.id.empty)).setText(R.string.no_observations_yet);
            }


            if ((mLastMessage != null) && (mLastMessage.length() > 0)) {
                Toast.makeText(getApplicationContext(), mLastMessage, Toast.LENGTH_LONG).show();
                mLastMessage = null;
            }

            if (mApp.getAutoSync()) {
                Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "(((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL) OR (is_deleted = 1))) AND (_updated_at > _created_at)",
                        null,
                        Observation.SYNC_ORDER);
                int syncCount = c.getCount();
                c.close();

                // Trigger a sync (more new observations were added in the mean time)
                if ((syncCount > 0) && (mApp.loggedIn())) {
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                    startService(serviceIntent);
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
            } catch (Exception exc) {
                exc.printStackTrace();
            }
            mSyncCompleteReceiver = null;
        }
    }

    /** Shows the sync required bottom bar, if needed */
    private void refreshSyncBar() {
        int syncCount = 0;
        int photoSyncCount = 0;

        if (mApp.getAutoSync()) {
            // Auto sync is on - no need for manual sync
            mSyncObservations.setVisibility(View.GONE);
            return;
        }

        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
        		Observation.PROJECTION, 
        		"((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", 
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
    		mSyncObservations.setText(String.format(getResources().getString(R.string.sync_x_observations), (syncCount > 0 ? syncCount : photoSyncCount)));
    		mSyncObservations.setVisibility(View.VISIBLE);
    	} else {
    		mSyncObservations.setVisibility(View.GONE);
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

        mSyncObservations = (TextView) findViewById(R.id.sync_observations);
        mSyncObservations.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return;
                } else if (!isLoggedIn()) {
                    // User not logged-in - redirect to onboarding screen
                    startActivity(new Intent(ObservationListActivity.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    return;
                }

                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                startService(serviceIntent);

                mSyncObservations.setVisibility(View.GONE);

                mHelper.loading(getResources().getString(R.string.syncing_observations), ObservationListActivity.this);
            }
        });
        
        if (savedInstanceState != null) {
            mLastMessage = savedInstanceState.getString("mLastMessage");
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
 
        mPullRefreshListView = (PullToRefreshListView) findViewById(R.id.observations_list);
        mPullRefreshListView.getLoadingLayoutProxy().setPullLabel(getResources().getString(R.string.pull_to_refresh));
        mPullRefreshListView.getLoadingLayoutProxy().setReleaseLabel(getResources().getString(R.string.release_to_refresh));
        mPullRefreshListView.getLoadingLayoutProxy().setRefreshingLabel(getResources().getString(R.string.refreshing));
        mPullRefreshListView.setReleaseRatio(2.5f);
        
        // Set a listener to be invoked when the list should be refreshed.
        mPullRefreshListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
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
                                    mPullRefreshListView.onRefreshComplete();
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
        });

        onDrawerCreate(savedInstanceState);
        
        
		ListView actualListView = mPullRefreshListView.getRefreshableView();

		// Need to use the Actual ListView when registering for Context Menu
		registerForContextMenu(actualListView);
        
        
        // Inform the list we provide context menus for items
        //getListView().setOnCreateContextMenuListener(this);
		
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);
        
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations
        
        Cursor cursor = getContentResolver().query(getIntent().getData(), Observation.PROJECTION, 
        		conditions, null, Observation.DEFAULT_SORT_ORDER);

        // Used to map notes entries from the database to views
        ObservationCursorAdapter adapter = new ObservationCursorAdapter(
                this, R.layout.list_item, cursor,
                new String[] { Observation.DESCRIPTION },
                new int[] { R.id.subContent });

        mAdapter = adapter;
        
        mPullRefreshListView.setEmptyView(findViewById(android.R.id.empty));
        mPullRefreshListView.setAdapter(mAdapter);
        mPullRefreshListView.setOnItemClickListener(this);


        boolean hasOldObs = hasOldObservations();
        if ((app.getAutoSync() && !app.getIsSyncing()) || (hasOldObs)) {
            int syncCount = 0;
            if (!hasOldObs) {
                Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL) OR (is_deleted = 1))",
                        null,
                        Observation.SYNC_ORDER);
                syncCount = c.getCount();
                c.close();
            }

            // Trigger a sync (in case of auto-sync and unsynced obs OR when having old-style observations)
            if (hasOldObs || (syncCount > 0)) {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                startService(serviceIntent);
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
        ListView lv = mPullRefreshListView.getRefreshableView();
        mLastIndex = lv.getFirstVisiblePosition();
        View v = lv.getChildAt(0);
        mLastTop = (v == null) ? 0 : v.getTop();
        

        /*
        ObservationCursorAdapter adapter = mAdapter;
        adapter.notifyDataSetInvalidated();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
        	Cursor oldCursor = adapter.swapCursor(null);
        	if ((oldCursor != null) && (!oldCursor.isClosed())) oldCursor.close();
        } else {
        	adapter.changeCursor(null);
        }
        */

        super.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();

        ListView lv = mPullRefreshListView.getRefreshableView();
        lv.setSelectionFromTop(mLastIndex, mLastTop);
      
        refreshSyncBar();

        ObservationCursorAdapter adapter = mAdapter;
        adapter.refreshCursor();
        
        INaturalistApp app = (INaturalistApp)(getApplication());
        if (app.getIsSyncing()) {
        	// We're still syncing
        	mHelper.stopLoading();
        	if ((mLastMessage != null) && (!mApp.getAutoSync())) mHelper.loading(mLastMessage, ObservationListActivity.this);
        	app.setNotificationCallback(this);
        }

        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mApp.loggedIn() && mApp.getIsSyncing() && (mAdapter.getCount() == 0)) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.downloading_observations), Toast.LENGTH_LONG).show();
                }
            }
        }, 100);


        if (mApp.loggedIn() && mApp.getIsSyncing() && (mAdapter.getCount() == 0)) {
            // Show a "downloading ..." message instead of "no observations yet"
            ((TextView)findViewById(android.R.id.empty)).setText(R.string.downloading_observations);
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mLastMessage != null) outState.putString("mLastMessage", mLastMessage);
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
            if ((!mAdapter.isLocked(uri)) || (mAdapter.isLocked(uri) && !mApp.getIsSyncing())) {
                // Launch activity to view/edit the currently selected item
                startActivity(new Intent(Intent.ACTION_VIEW, uri, this, ObservationViewerActivity.class));
            }
        }
    }
    
    private class ObservationCursorAdapter extends SimpleCursorAdapter {
		private HashMap<Long, String[]> mPhotoInfo = new HashMap<Long, String[]>();
        
        public ObservationCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
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
            

            /*
            Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
                    new String[]{
                        ObservationPhoto._ID, 
                        ObservationPhoto._OBSERVATION_ID, 
                        ObservationPhoto._PHOTO_ID, 
                        ObservationPhoto.PHOTO_URL,
                        ObservationPhoto.PHOTO_FILENAME,
                        ObservationPhoto._UPDATED_AT,
                        ObservationPhoto._SYNCED_AT
                    }, 
                    "_observation_id IN ("+StringUtils.join(obsIds, ',')+") AND photo_url IS NULL", 
                    null, 
                    ObservationPhoto._ID);
            if (opc.getCount() == 0) return;
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                Long obsId = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
                String photoFilename = opc.getString(opc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
                Long syncedAt = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._SYNCED_AT));
                Long updatedAt = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._UPDATED_AT));

                if (!mPhotoInfo.containsKey(obsId)) {
                    mPhotoInfo.put(
                            obsId,
                            new String[] {
                                photoFilename,
                                null,
                                null,
                                updatedAt.toString(),
                                syncedAt.toString()
                            });
                }
                opc.moveToNext();
            }

            opc.close();
            */
            
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


            ImageView image = (ImageView) view.findViewById(R.id.image);
            c.moveToPosition(position);
            final Long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
            final Long externalObsId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));

            refreshPhotoInfo(obsId);
            getPhotoInfo();

            String[] photoInfo = mPhotoInfo.get(obsId);
            
            if (photoInfo == null) {
            	// Try getting the external observation photo info
            	photoInfo = mPhotoInfo.get(externalObsId);
            }

            if (photoInfo != null) {
                String photoFilename = photoInfo[0];
                
                if (photoInfo[2] != null) {
                    // Online-only photo
                    UrlImageViewHelper.setUrlDrawable(image, photoInfo[2]); 
                    
                } else {
                    // Offline photo
                    Integer orientation;
                    if (photoInfo[1] == null || photoInfo[1].equals("null")) {
                        orientation = 0;
                    } else {
                        orientation = Integer.parseInt(photoInfo[1]);
                    }

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = ImageUtils.calculateInSampleSize(options, 100, 100);

                    // Decode bitmap with inSampleSize set
                    options.inJustDecodeBounds = false;
                    // This decreases in-memory byte-storage per pixel
                    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                    Bitmap bitmapImage = BitmapFactory.decodeFile(photoFilename, options);
                    image.setImageBitmap(bitmapImage);
                }
                
            } else {
                String iconicTaxonName = c.getString(c.getColumnIndexOrThrow(Observation.ICONIC_TAXON_NAME));
                if (iconicTaxonName == null) {
                    image.setImageResource(R.drawable.iconic_taxon_unknown);
                } else if (iconicTaxonName.equals("Animalia")) {
                    image.setImageResource(R.drawable.iconic_taxon_animalia);
                } else if (iconicTaxonName.equals("Plantae")) {
                    image.setImageResource(R.drawable.iconic_taxon_plantae);
                } else if (iconicTaxonName.equals("Chromista")) {
                    image.setImageResource(R.drawable.iconic_taxon_chromista);
                } else if (iconicTaxonName.equals("Fungi")) {
                    image.setImageResource(R.drawable.iconic_taxon_fungi);
                } else if (iconicTaxonName.equals("Protozoa")) {
                    image.setImageResource(R.drawable.iconic_taxon_protozoa);
                } else if (iconicTaxonName.equals("Actinopterygii")) {
                    image.setImageResource(R.drawable.iconic_taxon_actinopterygii);
                } else if (iconicTaxonName.equals("Amphibia")) {
                    image.setImageResource(R.drawable.iconic_taxon_amphibia);
                } else if (iconicTaxonName.equals("Reptilia")) {
                    image.setImageResource(R.drawable.iconic_taxon_reptilia);
                } else if (iconicTaxonName.equals("Aves")) {
                    image.setImageResource(R.drawable.iconic_taxon_aves);
                } else if (iconicTaxonName.equals("Mammalia")) {
                    image.setImageResource(R.drawable.iconic_taxon_mammalia);
                } else if (iconicTaxonName.equals("Mollusca")) {
                    image.setImageResource(R.drawable.iconic_taxon_mollusca);
                } else if (iconicTaxonName.equals("Insecta")) {
                    image.setImageResource(R.drawable.iconic_taxon_insecta);
                } else if (iconicTaxonName.equals("Arachnida")) {
                    image.setImageResource(R.drawable.iconic_taxon_arachnida);
                } else {
                    image.setImageResource(R.drawable.iconic_taxon_unknown);
                }
            }
                
            
            TextView observedOn = (TextView) view.findViewById(R.id.dateObserved);
            Long observationTimestamp = c.getLong(c.getColumnIndexOrThrow(Observation.OBSERVED_ON));
            
            if (observationTimestamp == 0) {
                // No observation date set - don't show it
                observedOn.setVisibility(View.INVISIBLE);
            } else {
                observedOn.setVisibility(View.VISIBLE);
                Timestamp observationDate = new Timestamp(observationTimestamp);
                observedOn.setText(new SimpleDateFormat("M/d/yyyy").format(observationDate));
            }
            
            
            TextView commentIdCountText = (TextView) view.findViewById(R.id.commentIdCount);
            Long commentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.COMMENTS_COUNT));
            Long idCount = c.getLong(c.getColumnIndexOrThrow(Observation.IDENTIFICATIONS_COUNT));
            Long lastCommentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_COMMENTS_COUNT));
            Long lastIdCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_IDENTIFICATIONS_COUNT));
            final Long taxonId = c.getLong(c.getColumnIndexOrThrow(Observation.TAXON_ID));
            if (taxonId != 0 && idCount > 0) {
                idCount--;
            }
            Long totalCount = commentsCount + idCount;
            ViewGroup clickCatcher = (ViewGroup) view.findViewById(R.id.rightObsPart);

            if (totalCount == 0) {
                // No comments/IDs - don't display the indicator
                commentIdCountText.setVisibility(View.INVISIBLE);
                clickCatcher.setClickable(false);
            } else {
                clickCatcher.setClickable(true);
                commentIdCountText.setVisibility(View.VISIBLE);
                if ((lastCommentsCount == null) || (lastCommentsCount < commentsCount) ||
                        (lastIdCount == null) || (lastIdCount < idCount)) {
                    // There are unread comments/IDs
                    commentIdCountText.setBackgroundResource(R.drawable.comments_ids_background_highlighted);
                } else {
                    commentIdCountText.setBackgroundResource(R.drawable.comments_ids_background);
                }
                
                refreshCommentsIdSize(commentIdCountText, totalCount);

                clickCatcher.setOnClickListener(new OnClickListener() {
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

            ImageView needToSync = (ImageView) view.findViewById(R.id.syncRequired);
            TextView subTitle = (TextView) view.findViewById(R.id.subContent);
            TextView title = (TextView) view.findViewById(R.id.speciesGuess);
            ProgressBar progress = (ProgressBar) view.findViewById(R.id.progress);
            ViewGroup commentCatcher = (ViewGroup) view.findViewById(R.id.commentsIdClickCatcher);

            String speciesGuess = c.getString(c.getColumnIndexOrThrow(Observation.SPECIES_GUESS));
            String preferredCommonName = c.getString(c.getColumnIndexOrThrow(Observation.PREFERRED_COMMON_NAME));
            title.setTextColor(Color.parseColor("#000000"));
            subTitle.setTextColor(Color.parseColor("#666666"));
            progress.setVisibility(View.GONE);
            observedOn.setVisibility(View.VISIBLE);
            commentCatcher.setVisibility(View.VISIBLE);

            if (preferredCommonName != null) {
                title.setText(preferredCommonName);
            } else if (speciesGuess != null) {
                title.setText("\"" + speciesGuess + "\"");
            } else {
                title.setText(R.string.unknown_species);
            }


            ImageView errorIcon = (ImageView) view.findViewById(R.id.error);
            boolean hasErrors = (mApp.getErrorsForObservation(externalObsId.intValue()).length() > 0);
            if (hasErrors)  {
                errorIcon.setVisibility(View.VISIBLE);
                needToSync.setVisibility(View.GONE);
                commentIdCountText.setVisibility(View.GONE);
                view.setBackgroundColor(Color.parseColor("#F3D3DA"));
                subTitle.setText(R.string.needs_your_attention);
            } else {
                errorIcon.setVisibility(View.GONE);
                view.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }

            if (syncNeeded) {
                // This observations needs to be synced
                needToSync.setVisibility(View.VISIBLE);

                if (mApp.getObservationIdBeingSynced() == obsId) {
                    // Observation is currently being uploaded
                    subTitle.setText(R.string.uploading);
                    view.setBackgroundColor(Color.parseColor("#76AA1B"));

                    title.setTextColor(Color.parseColor("#ffffff"));
                    subTitle.setTextColor(Color.parseColor("#ffffff"));

                    progress.setVisibility(View.VISIBLE);
                    observedOn.setVisibility(View.GONE);
                    commentCatcher.setVisibility(View.GONE);
                } else {
                    // Observation is waiting to be uploaded
                    if (!hasErrors) {
                        subTitle.setText(R.string.waiting_to_upload);
                        view.setBackgroundColor(Color.parseColor("#E3EDCD"));
                    }
                }
            } else {
                needToSync.setVisibility(View.INVISIBLE);
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
                if (!mApp.getAutoSync()) {
                    mHelper.loading(content, ObservationListActivity.this);
                }
                mAdapter.refreshCursor();
            }
        });
	}

}
