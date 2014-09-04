package org.inaturalist.shedd.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.inaturalist.shedd.android.R;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class MenuActivity extends ListActivity {
    public static String TAG = "MenuActivity";
    List<Map> MENU_ITEMS;
    static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
    static final int SELECT_IMAGE_REQUEST_CODE = 2;
    private Uri mPhotoUri;
    private INaturalistApp app;
    private ActivityHelper mHelper;
	private Button mSyncObservationsButton;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.menu);

        getListView().setDivider(null);
        getListView().setDividerHeight(0);        

        MENU_ITEMS = new ArrayList<Map>();
        Map<String,String> map;

        map = new HashMap<String,String>();
        map.put("title", getString(R.string.add_observation));
        map.put("description", getString(R.string.add_observation));
        MENU_ITEMS.add(map);
        
       
        map = new HashMap<String,String>();
        map.put("title", getString(R.string.observations));
        map.put("description", getString(R.string.observations_description));
        MENU_ITEMS.add(map);
        
        // Only show guides only for Android 4+
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			map = new HashMap<String,String>();
			map.put("title", getString(R.string.guides));
			map.put("description", getString(R.string.guides_description));
			MENU_ITEMS.add(map);
		}
        
        map = new HashMap<String,String>();
        map.put("title", getString(R.string.map));
        map.put("description", getString(R.string.map_description));
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title", getString(R.string.settings));
        map.put("description", getString(R.string.settings_description));
        MENU_ITEMS.add(map);

        map = new HashMap<String,String>();
        map.put("title", getString(R.string.about_menu));
        map.put("description", getString(R.string.about_menu_description));
        MENU_ITEMS.add(map);
            
       
        SimpleAdapter adapter = new SimpleAdapter(this, 
                (List<? extends Map<String, ?>>) MENU_ITEMS, 
                R.layout.menu_item,
                new String[] {"title"},
                new int[] {R.id.title});
        ListView lv = getListView();
        LinearLayout header = (LinearLayout) getLayoutInflater().inflate(R.layout.menu_header, lv, false);
        lv.addHeaderView(header, null, false);
        LinearLayout footer = (LinearLayout) getLayoutInflater().inflate(R.layout.menu_footer, lv, false);
        lv.addFooterView(footer, null, false);
 
        setListAdapter(adapter);
        
        if  (savedInstanceState != null) {
            String photoUri = savedInstanceState.getString("mFileUri");
            if (photoUri != null) {mPhotoUri = Uri.parse(photoUri);}
        }
        
        if (app == null) { app = (INaturalistApp) getApplicationContext(); }
        if (mHelper == null) { mHelper = new ActivityHelper(this);}
        
        
        mSyncObservationsButton = (Button) findViewById(R.id.sync_observations);

        mSyncObservationsButton.setOnClickListener(new View.OnClickListener() {
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

                Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, MenuActivity.this, INaturalistService.class);
                startService(serviceIntent);
 
            }
        });
        
        refreshSyncButton();        
        
        // See if we need to display the tutorial (only for the first time using the app)
        SharedPreferences preferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        boolean firstTime = preferences.getBoolean("first_time", true);
        
        if (firstTime) {
            Intent intent = new Intent(this, TutorialActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("first_time", true);
            startActivity(intent);
            preferences.edit().putBoolean("first_time", false).apply();
        }
    }
    
    public static void openImageIntent(Activity activity, Uri captureImageOutputFile, int requestCode) {

        // Camera
        final List<Intent> cameraIntents = new ArrayList<Intent>();
        final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        final PackageManager packageManager = activity.getPackageManager();
        final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for(ResolveInfo res : listCam) {
            final String packageName = res.activityInfo.packageName;
            final Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(packageName);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, captureImageOutputFile);
            cameraIntents.add(intent);
        }

        // File system
        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

        // Chooser of filesystem options.
        final Intent chooserIntent = Intent.createChooser(galleryIntent, "Select Source");

        // Add the camera options
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

        activity.startActivityForResult(chooserIntent, requestCode);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (app == null) { app = (INaturalistApp) getApplicationContext(); }

        refreshSyncButton();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mHelper.stopLoading();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mPhotoUri != null) { outState.putString("mFileUri", mPhotoUri.toString()); }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                final boolean isCamera;
                if(data == null) {
                    isCamera = true;
                } else {
                    final String action = data.getAction();
                    if(action == null) {
                        isCamera = false;
                    } else {
                        isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    }
                }

                Uri selectedImageUri;
                if(isCamera) {
                    selectedImageUri = mPhotoUri;
                } else {
                    selectedImageUri = data == null ? null : data.getData();
                }
                
                Log.v(TAG, String.format("%s: %s", isCamera, selectedImageUri));
                
                mHelper.loading(getString(R.string.processing));
                Intent intent = new Intent(Intent.ACTION_INSERT, ObservationPhoto.CONTENT_URI, this, ObservationEditor.class);
                intent.putExtra("photoUri", selectedImageUri);
                startActivity(intent);
                
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
                getContentResolver().delete(mPhotoUri, null, null);
                
            } else {
                // Image capture failed, advise user
                Toast.makeText(this, String.format(getString(R.string.something_went_wrong), mPhotoUri.toString()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + (data == null ? "null" : data.getData()));
                getContentResolver().delete(mPhotoUri, null, null);
            }
  
            mPhotoUri = null; // don't let this hang around
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Map<String,String> item = (Map<String,String>) l.getItemAtPosition(position);
        String title = item.get("title");
        if (title.equals(getString(R.string.observations))) {
            startActivity(new Intent(this, ObservationListActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(getString(R.string.map))) {
            startActivity(new Intent(this, INaturalistMapActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(getString(R.string.settings))) {
            startActivity(new Intent(this, INaturalistPrefsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(getString(R.string.about_menu))) {
            startActivity(new Intent(this, AboutSHEDDActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(getString(R.string.add_observation))) {
        	Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, this, ObservationEditor.class);
        	startActivity(intent);
        } else if (title.equals(getString(R.string.guides))) {
            Intent intent = new Intent(this, GuideDetails.class);
            BetterJSONObject guide = new BetterJSONObject();
            guide.put("id", INaturalistService.DEFAULT_GUIDE_ID);
            guide.put("title", INaturalistService.DEFAULT_GUIDE_TITLE);
            intent.putExtra("guide", guide);
            startActivity(intent);
        }
    }
    
    
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    }    

 	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}	
 	
 	
    /** Shows the sync observations button, if needed */
    private void refreshSyncButton() {
    	Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
    			Observation.PROJECTION, 
    			"((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", 
    			null, 
    			Observation.SYNC_ORDER);
    	int syncCount = c.getCount();
    	c.close();

    	if (syncCount > 0) {
    		mSyncObservationsButton.setVisibility(View.VISIBLE);
    	} else {
    		mSyncObservationsButton.setVisibility(View.GONE);
    	}
    } 	
}
