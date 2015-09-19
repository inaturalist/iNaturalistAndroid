package org.inaturalist.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * Utility class for implementing the side-menu (navigation drawer) used throughout the app
 *
 */
public class BaseFragmentActivity extends SherlockFragmentActivity {
	
    static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
    static final int SELECT_IMAGE_REQUEST_CODE = 2;
	private static final String TAG = "BaseFragmentActivity";

	private DrawerLayout mDrawerLayout;
	private LinearLayout mSideMenu;
	private ListView mListView;

    private List<Map <String, ?>> MENU_ITEMS;
	private ActionBarDrawerToggle mDrawerToggle;
	private INaturalistApp app;
	private ActivityHelper mHelper;

	public void onDrawerCreate(Bundle savedInstanceState) {
        Fabric.with(this, new Crashlytics());
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mSideMenu = (LinearLayout) findViewById(R.id.left_drawer);
        mListView = (ListView) findViewById(R.id.menu_items);
        
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_drawer, 0, 0) {
            public void onDrawerClosed(View view) {
            }

            public void onDrawerOpened(View drawerView) {
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setIcon(android.R.color.transparent);

        buildSideMenu();
        
        if (app == null) { app = (INaturalistApp) getApplicationContext(); }
        if (mHelper == null) { mHelper = new ActivityHelper(this);}
        
 
        // See if we need to display the tutorial (only for the first time using the app)
        SharedPreferences preferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        boolean firstTime = preferences.getBoolean("first_time", true);
        
        if (firstTime) {
            Intent intent = new Intent(this, TutorialActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("first_time", true);
            startActivity(intent);
        } else {
        	app.detectUserCountryAndUpdateNetwork(this);
        }
	}
	
	private void buildSideMenu() {
        MENU_ITEMS = new ArrayList<Map <String, ?>>();
        Map<String,String> map;
        
        map = new HashMap<String,String>();
        map.put("title", getString(R.string.observations));
        map.put("description", getString(R.string.observations_description));
        MENU_ITEMS.add(map);

        map = new HashMap<String,String>();
        map.put("title", getString(R.string.explore));
        map.put("description", getString(R.string.explore_description));
        MENU_ITEMS.add(map);

        map = new HashMap<String,String>();
        map.put("title", getString(R.string.projects));
        map.put("description", getString(R.string.projects_description));
        MENU_ITEMS.add(map);

        // Only show guides only for Android 4+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            map = new HashMap<String,String>();
            map.put("title", getString(R.string.guides));
            map.put("description", getString(R.string.guides_description));
            MENU_ITEMS.add(map);
        }

        map = new HashMap<String,String>();
        map.put("title", getString(R.string.updates));
        map.put("description", getString(R.string.updates_description));
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title", getString(R.string.settings));
        map.put("description", getString(R.string.settings_description));
        MENU_ITEMS.add(map);
        

        SimpleAdapter adapter = new SimpleAdapter(this, 
                (List<? extends Map<String, ?>>) MENU_ITEMS,
                R.layout.menu_item,
                new String[] {"title"},
                new int[] {R.id.title});
        ListView lv = mListView;
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {
        	@Override
        	public void onItemClick(AdapterView parent, View view, int position, long id) {
        		// Side menu item was selected

        		Map<String,String> item = (Map<String,String>) mListView.getItemAtPosition(position);
        		String title = item.get("title");
        		if (title.equals(getString(R.string.observations))) {
        			startActivityIfNew(new Intent(BaseFragmentActivity.this, ObservationListActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        		} else if (title.equals(getString(R.string.explore))) {
        			startActivityIfNew(new Intent(BaseFragmentActivity.this, INaturalistMapActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        		} else if (title.equals(getString(R.string.updates))) {
        			if (!isNetworkAvailable()) {
        				Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show(); 
        				return;
        			}

        			startActivityIfNew(new Intent(BaseFragmentActivity.this, WebActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        		} else if (title.equals(getString(R.string.settings))) {
        			startActivityIfNew(new Intent(BaseFragmentActivity.this, INaturalistPrefsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        		} else if (title.equals(getString(R.string.projects))) {
        			startActivityIfNew(new Intent(BaseFragmentActivity.this, ProjectsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        		} else if (title.equals(getString(R.string.guides))) {
        			startActivityIfNew(new Intent(BaseFragmentActivity.this, GuidesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        		}
        	}

		}); 
	}
	
	private void startActivityIfNew(Intent intent) {
		if (intent.getComponent().getClassName().equals(this.getClass().getName())) {
			// Activity is already loaded
			mDrawerLayout.closeDrawer(mSideMenu);
			return;
		}
		
		startActivity(intent);
		finish();
	}


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {

        	if (mDrawerLayout.isDrawerOpen(mSideMenu)) {
        		mDrawerLayout.closeDrawer(mSideMenu);
        	} else {
        		mDrawerLayout.openDrawer(mSideMenu);
        	}
        	return true;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }
    
    private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}	
 	

    @Override
    protected void onResume() {
        super.onResume();
        if (app == null) { app = (INaturalistApp) getApplicationContext(); }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mHelper != null) {
            mHelper.stopLoading();
        }
    }

}
