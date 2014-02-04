package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

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
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationListActivity extends SherlockListActivity {
	public static String TAG = "INAT";
	
	private PullToRefreshListView mPullRefreshListView;
	
	private SyncCompleteReceiver mSyncCompleteReceiver;
	
	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}	
	
    private class SyncCompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPullRefreshListView.onRefreshComplete();
        }
    } 	
  
    public static Intent createIntent(Context context) {
        Intent i = new Intent(context, ObservationListActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    } 
    
    
    @Override
    protected void onPause() {
        super.onPause();
        
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

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.observation_list);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(Observation.CONTENT_URI);
        }
        
        mSyncCompleteReceiver = new SyncCompleteReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_SYNC_COMPLETE);
        Log.i(TAG, "Registering ACTION_SYNC_COMPLETE");
        registerReceiver(mSyncCompleteReceiver, filter);
        
        mPullRefreshListView = (PullToRefreshListView) findViewById(R.id.observations_list);
        
        
        mPullRefreshListView.getLoadingLayoutProxy().setPullLabel(getResources().getString(R.string.pull_to_sync));
        mPullRefreshListView.getLoadingLayoutProxy().setReleaseLabel(getResources().getString(R.string.release_to_sync));
        mPullRefreshListView.getLoadingLayoutProxy().setRefreshingLabel(getResources().getString(R.string.syncing));
        mPullRefreshListView.setReleaseRatio(2.5f);
        
        // Set a listener to be invoked when the list should be refreshed.
        mPullRefreshListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                if (!isNetworkAvailable()) {
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
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show(); 
                    return;
                }
                
                // Start sync
                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationListActivity.this, INaturalistService.class);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.observations_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
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
            ArrayList<Long> photoIds = new ArrayList<Long>();
            while (!c.isAfterLast()) {
                obsIds.add(c.getLong(c.getColumnIndexOrThrow(Observation._ID)));
                c.moveToNext();
            }
            
            
            // Add any online-only photos
            Cursor onlinePc = managedQuery(ObservationPhoto.CONTENT_URI, 
                    new String[]{ObservationPhoto._ID, ObservationPhoto._OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL}, 
                    "_observation_id IN ("+StringUtils.join(obsIds, ',')+") AND photo_url IS NOT NULL", 
                    null, 
                    ObservationPhoto._ID);
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
                                    photoUrl
                            });
                }
                onlinePc.moveToNext();
            }
            
            onlinePc.close();

            
            Cursor opc = managedQuery(ObservationPhoto.CONTENT_URI, 
                    new String[]{ObservationPhoto._ID, ObservationPhoto._OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL}, 
                    "_observation_id IN ("+StringUtils.join(obsIds, ',')+") AND photo_url IS NULL", 
                    null, 
                    ObservationPhoto._ID);
            if (opc.getCount() == 0) return;
            opc.moveToFirst();
            while (!opc.isAfterLast()) {
                photoIds.add(opc.getLong(opc.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID)));
                opc.moveToNext();
            }
            
            Cursor pc = managedQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.Images.ImageColumns.ORIENTATION}, 
                    "_ID IN ("+StringUtils.join(photoIds, ',')+")", 
                    null, 
                    null);
            if (pc.getCount() == 0) { opc.close(); return; }
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
                String photoUrl = opc.getString(opc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                if (!mPhotoInfo.containsKey(obsId)) {
                    mPhotoInfo.put(
                            obsId,
                            new String[] {
                                photoId.toString(),
                                orientationsByPhotoId.get(photoId),
                                null
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
            
            String[] photoInfo = mPhotoInfo.get(obsId);
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
            Long totalCount = commentsCount + idCount;
            if (idCount > 0) totalCount--; // Don't count our own ID
            
            if (totalCount == 0) {
                // No comments/IDs - don't display the indicator
                commentIdCountText.setVisibility(View.INVISIBLE);
            } else {
                commentIdCountText.setVisibility(View.VISIBLE);
                if ((lastCommentsCount == null) || (lastCommentsCount != commentsCount) ||
                        (lastIdCount == null) || (lastIdCount != idCount)) {
                    // There are unread comments/IDs
                    commentIdCountText.setBackgroundResource(R.drawable.comments_ids_background_highlighted);
                } else {
                    commentIdCountText.setBackgroundResource(R.drawable.comments_ids_background);
                }
                
                refreshCommentsIdSize(commentIdCountText, totalCount);
                
            }
 
            Long syncedAt = c.getLong(c.getColumnIndexOrThrow(Observation._SYNCED_AT));
            Long updatedAt = c.getLong(c.getColumnIndexOrThrow(Observation._UPDATED_AT));
            
            ImageView needToSync = (ImageView) view.findViewById(R.id.syncRequired);
            
            if ((syncedAt == null) || (updatedAt > syncedAt)) {
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