package org.inaturalist.shedd.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.inaturalist.shedd.android.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.annotation.TargetApi;
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
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationListActivity extends SherlockListActivity {
	public static String TAG = "INAT";
	
	private PullToRefreshListView mPullRefreshListView;
	
	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private int mLastIndex;
	private int mLastTop;
	private ActionBar mTopActionBar;

	private TextView mSyncObservations;
	
	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}	
	
    private class SyncCompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Log.i(TAG, "Got ACTION_SYNC_COMPLETE");
            mPullRefreshListView.onRefreshComplete();
            mPullRefreshListView.refreshDrawableState();
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
    	Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
    			Observation.PROJECTION, 
    			"((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", 
    			null, 
    			Observation.SYNC_ORDER);
    	int syncCount = c.getCount();
    	c.close();

    	if (syncCount > 0) {
    		mSyncObservations.setText(String.format(getResources().getString(R.string.sync_x_observations), syncCount));
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
        
        mSyncObservations = (TextView) findViewById(R.id.sync_observations);
        mSyncObservations.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show(); 
                    return;
                } else if (!isLoggedIn()) {
                    Toast.makeText(getApplicationContext(), R.string.please_sign_in, Toast.LENGTH_LONG).show(); 
                    return;
                }

                Toast.makeText(getApplicationContext(), R.string.syncing_observations, Toast.LENGTH_LONG).show(); 

                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
                startService(serviceIntent);
                
                mSyncObservations.setVisibility(View.GONE);
			}
		});        
        
              
        refreshSyncBar(); 
        
        mTopActionBar = getSupportActionBar();
        mTopActionBar.setHomeButtonEnabled(true);
        mTopActionBar.setDisplayHomeAsUpEnabled(true);
        mTopActionBar.setDisplayShowCustomEnabled(true);
        mTopActionBar.setCustomView(R.layout.observation_list_top_action_bar);
        mTopActionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#111111")));
        TextView addButton = (TextView) mTopActionBar.getCustomView().findViewById(R.id.add);
        addButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), ObservationListActivity.this, ObservationEditor.class));
            }
        });
        
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
        
        Cursor cursor = managedQuery(getIntent().getData(), Observation.PROJECTION, 
        		conditions, null, Observation.DEFAULT_SORT_ORDER);

        // Used to map notes entries from the database to views
        ObservationCursorAdapter adapter = new ObservationCursorAdapter(
                this, R.layout.list_item, cursor,
                new String[] { Observation.SPECIES_GUESS, Observation.DESCRIPTION }, 
                new int[] { R.id.speciesGuess, R.id.subContent });
        setListAdapter(adapter);
    }
    
    @Override
    public void onPause() {
        // save last position of list so we can resume there later
        // http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
        ListView lv = mPullRefreshListView.getRefreshableView();
        mLastIndex = lv.getFirstVisiblePosition();
        View v = lv.getChildAt(0);
        mLastTop = (v == null) ? 0 : v.getTop();
        super.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        ListView lv = mPullRefreshListView.getRefreshableView();
        lv.setSelectionFromTop(mLastIndex, mLastTop);
      
        refreshSyncBar();
    }
    
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    } 
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.observations_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;

        case R.id.observations_menu_add:
            // Launch activity to insert a new item
        	startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), this, ObservationEditor.class));
            return true;
        case R.id.observations_menu_sync:
            if (!isNetworkAvailable()) {
                Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show(); 
                return true;
            }

            Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, this, INaturalistService.class);
            startService(serviceIntent);
            return true;
        case R.id.observations_menu_menu:
            startActivity(new Intent(this, MenuActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user.  The have clicked on one, so return it now.
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, uri, this, ObservationEditor.class));
        }
    }
    
    private class ObservationCursorAdapter extends SimpleCursorAdapter {
        private HashMap<Long, String[]> mPhotoInfo = new HashMap<Long, String[]>();
        
        public ObservationCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
            getPhotoInfo();
        }
        
        /**
         * Retrieves photo ids and orientations for photos associated with the listed observations.
         */
        public void getPhotoInfo() {
            Cursor c = getCursor();
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
            
 
            // Add any online-only photos
            Cursor onlinePc = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
                    new String[]{ObservationPhoto._ID, ObservationPhoto._OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL}, 
                    "(_observation_id IN ("+StringUtils.join(obsIds, ',')+") OR observation_id IN ("+StringUtils.join(obsExternalIds, ',')+")  )  AND photo_url IS NOT NULL", 
                    null, 
                    ObservationPhoto.DEFAULT_SORT_ORDER);
            onlinePc.moveToFirst();
            while (!onlinePc.isAfterLast()) {
                Long obsId = onlinePc.getLong(onlinePc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
                Long photoId = onlinePc.getLong(onlinePc.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
                String photoUrl = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                
                if (!mPhotoInfo.containsKey(obsId)) {
                    mPhotoInfo.put(
                            obsId,
                            new String[] {
                                    photoId.toString(),
                                    null,
                                    photoUrl,
                                    null,
                                    null
                            });
                }
                onlinePc.moveToNext();
            }
            
            onlinePc.close();
            
            
            Cursor opc = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
                    new String[]{
                        ObservationPhoto._ID, 
                        ObservationPhoto._OBSERVATION_ID, 
                        ObservationPhoto._PHOTO_ID, 
                        ObservationPhoto.PHOTO_URL,
                        ObservationPhoto._UPDATED_AT,
                        ObservationPhoto._SYNCED_AT
                    }, 
                    "_observation_id IN ("+StringUtils.join(obsIds, ',')+") AND photo_url IS NULL", 
                    null, 
                    ObservationPhoto._ID);
            if (opc.getCount() == 0) return;
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                photoIds.add(opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID)));
                opc.moveToNext();
            }
            
            Cursor pc = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.Images.ImageColumns.ORIENTATION}, 
                    "_ID IN ("+StringUtils.join(photoIds, ',')+")", 
                    null, 
                    null);
            if (pc.getCount() == 0) { pc.close(); opc.close(); return; }
            HashMap<Long,String> orientationsByPhotoId = new HashMap<Long,String>();
            pc.moveToFirst();
            while (!pc.isAfterLast()) {
                orientationsByPhotoId.put(
                        pc.getLong(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID)), 
                        pc.getString(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION)));
                pc.moveToNext();
            }
            
            pc.close();
            
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                Long obsId = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
                Long photoId = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
                Long syncedAt = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._SYNCED_AT));
                Long updatedAt = opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._UPDATED_AT));
                String photoUrl = opc.getString(opc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                if (!mPhotoInfo.containsKey(obsId)) {
                    mPhotoInfo.put(
                            obsId,
                            new String[] {
                                photoId.toString(),
                                orientationsByPhotoId.get(photoId),
                                null,
                                updatedAt.toString(),
                                syncedAt.toString()
                            });
                }
                opc.moveToNext();
            }
            
            opc.close();
            
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            Cursor c = this.getCursor();
            if (c.getCount() == 0) {
                return view;
            }
            
            getPhotoInfo();
            
            ImageView image = (ImageView) view.findViewById(R.id.image);
            c.moveToPosition(position);
            Long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
            final Long externalObsId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
            
            String[] photoInfo = mPhotoInfo.get(obsId);
            
            if (photoInfo == null) {
            	// Try getting the external observation photo info
            	photoInfo = mPhotoInfo.get(externalObsId);
            }

            if (photoInfo != null) {
                if (photoInfo[0] == null || photoInfo[0].equals("null")) return view;
                Long photoId = Long.parseLong(photoInfo[0]);
                
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
                    Bitmap bitmapImage = MediaStore.Images.Thumbnails.getThumbnail(
                            getContentResolver(), 
                            photoId, 
                            MediaStore.Images.Thumbnails.MICRO_KIND, 
                            (BitmapFactory.Options) null);
                    if (orientation != 0) {
                        Matrix matrix = new Matrix();
                        matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
                        bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
                    }
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
            RelativeLayout clickCatcher = (RelativeLayout) view.findViewById(R.id.commentsIdClickCatcher);

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
                		// Show the comments/IDs for the observation
                		Intent intent = new Intent(ObservationListActivity.this, CommentsIdsActivity.class);
                		intent.putExtra(INaturalistService.OBSERVATION_ID, externalObsId.intValue());
                		intent.putExtra(INaturalistService.TAXON_ID, taxonId.intValue());
                		startActivity(intent);

                		// Get the observation's IDs/comments
                		Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationListActivity.this, INaturalistService.class);
                		serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, externalObsId.intValue());
                		startService(serviceIntent);

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
            
            ImageView needToSync = (ImageView) view.findViewById(R.id.syncRequired);
            
            if (syncNeeded) {
                // This observations needs to be synced
                needToSync.setVisibility(View.VISIBLE);
            } else {
                needToSync.setVisibility(View.INVISIBLE);
            }
            
            return view;
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
}