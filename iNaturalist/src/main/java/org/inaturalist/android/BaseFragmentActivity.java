package org.inaturalist.android;

import com.crashlytics.android.Crashlytics;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.content.DialogInterface;
import android.os.Build;

import io.fabric.sdk.android.Fabric;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Utility class for implementing the side-menu (navigation drawer) used throughout the app
 *
 */
public class BaseFragmentActivity extends AppCompatActivity {
	
    static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
    static final int SELECT_IMAGE_REQUEST_CODE = 2;
	private static final String TAG = "BaseFragmentActivity";

    // Time in mins to refresh the user details (such as user obs count)
    private static final int USER_REFRESH_TIME_MINS = 1;

    private DrawerLayout mDrawerLayout;
	private LinearLayout mSideMenu;

	private ActionBarDrawerToggle mDrawerToggle;
	private INaturalistApp app;
	private ActivityHelper mHelper;
    private UserDetailsReceiver mUserDetailsReceiver;

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void moveDrawerToTop() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        DrawerLayout drawer = (DrawerLayout) inflater.inflate(R.layout.side_menu_decor, null); // "null" is important.

        // HACK: "steal" the first child of decor view
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        View child = decor.getChildAt(0);
        decor.removeView(child);
        ViewGroup container = (ViewGroup) drawer.findViewById(R.id.drawer_content); // This is the container we defined just now.
        container.addView(child, 0);
        drawer.findViewById(R.id.left_drawer).setPadding(0, getStatusBarHeight(), 0, 0);

        // Make the drawer replace the first child
        decor.addView(drawer);
    }

    public void onDrawerCreate(Bundle savedInstanceState) {
        Fabric.with(this, new Crashlytics());

        moveDrawerToTop();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mSideMenu = (LinearLayout) findViewById(R.id.left_drawer);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, 0, 0) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setIcon(android.R.color.transparent);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            ((ImageView)findViewById(R.id.menu_explore_icon)).setAlpha(0.54f);
            ((ImageView)findViewById(R.id.menu_projects_icon)).setAlpha(0.54f);
            ((ImageView)findViewById(R.id.menu_guides_icon)).setAlpha(0.54f);
            ((ImageView)findViewById(R.id.menu_activity_icon)).setAlpha(0.54f);
            ((ImageView)findViewById(R.id.menu_settings_icon)).setAlpha(0.54f);
        }

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

        refreshUserDetails();

        ((Button)findViewById(R.id.menu_login)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // User not logged-in - redirect to onboarding screen
                startActivity(new Intent(BaseFragmentActivity.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });

        if (!app.hasAutoSync() && app.loggedIn()) {
            // Tell the user about the new auto sync feature
            mHelper.confirm(getString(R.string.introducing_auto_sync), getString(R.string.turn_on_auto_sync), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // Turn off auto sync
                    app.setAutoSync(false);
                }
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // Turn on auto sync
                    app.setAutoSync(true);
                }
            }, R.string.no_thanks, R.string.turn_on);
        } else if (!app.hasAutoSync()) {
            // Default - set auto sync on
            app.setAutoSync(true);
        }
	}

    public void refreshUserDetails() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String username = prefs.getString("username", null);
        Integer obsCount = prefs.getInt("observation_count", -1);
        String userIconUrl = prefs.getString("user_icon_url", null);
        Long lastRefreshTime = prefs.getLong("last_user_details_refresh_time", 0);

        if (username != null) {
            ((TextView)findViewById(R.id.username)).setText(username);
            findViewById(R.id.menu_login).setVisibility(View.INVISIBLE);
            findViewById(R.id.username).setVisibility(View.VISIBLE);

            if (System.currentTimeMillis() - lastRefreshTime > 1000 * 60 * USER_REFRESH_TIME_MINS) {
                // Get fresh user details from the server
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_USER_DETAILS, null, this, INaturalistService.class);
                startService(serviceIntent);
            }
        } else {
            findViewById(R.id.menu_login).setVisibility(View.VISIBLE);
            findViewById(R.id.username).setVisibility(View.INVISIBLE);
        }

        if (obsCount > -1) {
            if (obsCount == 1) {
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(getString(R.string.observation_count_single), obsCount));
            } else {
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(getString(R.string.observation_count), obsCount));
            }
        } else {
            String conditions = "(_synced_at IS NULL";
            if (username != null) {
                conditions += " OR user_login = '" + username + "'";
            }
            conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

            Cursor cursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, conditions, null, Observation.DEFAULT_SORT_ORDER);

            int count = cursor.getCount();
            if (count == 1) {
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(getString(R.string.observation_count_single), count));
            } else {
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(getString(R.string.observation_count), count));
            }

            cursor.close();
        }

        if (userIconUrl != null) {
            UrlImageViewHelper.setUrlDrawable((ImageView)findViewById(R.id.user_pic), userIconUrl, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    ((ImageView)findViewById(R.id.no_user_pic)).setVisibility(View.GONE);
                    ((ImageView)findViewById(R.id.user_pic)).setVisibility(View.VISIBLE);
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    // Return a circular version of the profile picture
                    Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                    return ImageUtils.getCircleBitmap(centerCrop);
                }
            });

        } else {
            ((ImageView)findViewById(R.id.no_user_pic)).setVisibility(View.VISIBLE);
            ((ImageView)findViewById(R.id.user_pic)).setVisibility(View.GONE);
        }
    }

	private void buildSideMenu() {

        // Only show guides only for Android 4+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            findViewById(R.id.menu_guides).setVisibility(View.GONE);
        }

        findViewById(R.id.menu_explore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, INaturalistMapActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });
        findViewById(R.id.menu_projects).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, ProjectsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });
        findViewById(R.id.menu_guides).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, GuidesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });
        findViewById(R.id.menu_activity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent(BaseFragmentActivity.this, ProjectNews.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("is_user_feed", true);
                startActivityIfNew(intent);
            }
        });
        findViewById(R.id.menu_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, INaturalistPrefsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });

        findViewById(R.id.menu_header).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, ObservationListActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });

        findViewById(R.id.user_pic_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, ProfileEditor.class), false);
            }
        });


        if (INaturalistMapActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_explore).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_explore_icon)).setAlpha(1.0f);
            }
        }
        if (ProjectsActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_projects).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_projects_icon)).setAlpha(1.0f);
            }
        }
        if (GuidesActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_guides).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_guides_icon)).setAlpha(1.0f);
            }
        }
        if (WebActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_activity).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_activity_icon)).setAlpha(1.0f);
            }
        }
        if (INaturalistPrefsActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_settings).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_settings_icon)).setAlpha(1.0f);
            }
        }
    }


    private void startActivityIfNew(Intent intent) {
        startActivityIfNew(intent, true);
    }

    private void startActivityIfNew(Intent intent, boolean closeCurrentActivity) {
        if (intent.getComponent().getClassName().equals(this.getClass().getName())) {
            // Activity is already loaded
            mDrawerLayout.closeDrawer(mSideMenu);
            return;
        }

        startActivity(intent);
        overridePendingTransition(R.anim.show, R.anim.hide);
        if (closeCurrentActivity) {
            finish();
        }
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
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();

            mUserDetailsReceiver = new UserDetailsReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_USER_DETAILS_RESULT);
            Log.i(TAG, "Registering ACTION_GET_USER_DETAILS_RESULT");
            registerReceiver(mUserDetailsReceiver, filter);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
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
        if (mDrawerToggle != null) {
            refreshUserDetails();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mHelper != null) {
            mHelper.stopLoading();
        }
    }

    private class UserDetailsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Got GET_USER_DETAILS_RESULT");
            BetterJSONObject user = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.USER);

            if (user == null) {
                return;
            }

            SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putInt("observation_count", user.getInt("observations_count"));
            String iconUrl = user.has("medium_user_icon_url") ? user.getString("medium_user_icon_url") : user.getString("user_icon_url");
            editor.putString("user_icon_url", iconUrl);
            editor.putString("user_bio", user.getString("description"));
            editor.putString("user_full_name", user.getString("name"));
            editor.putLong("last_user_details_refresh_time", System.currentTimeMillis());
            editor.apply();

            refreshUserDetails();
        }
    }
}
