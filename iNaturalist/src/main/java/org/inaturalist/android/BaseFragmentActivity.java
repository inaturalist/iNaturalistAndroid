package org.inaturalist.android;

import com.cocosw.bottomsheet.BottomSheet;
import com.crashlytics.android.Crashlytics;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.graphics.Point;
import android.net.Uri;
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
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;

/**
 * Utility class for implementing the side-menu (navigation drawer) used throughout the app
 *
 */
public class BaseFragmentActivity extends AppCompatActivity {
	
    static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
    static final int SELECT_IMAGE_REQUEST_CODE = 2;
    protected static final int REQUEST_CODE_OBSERVATION_EDIT = 0x1000;

	private static final String TAG = "BaseFragmentActivity";

    // Time in mins to refresh the user details (such as user obs count)
    private static final int USER_REFRESH_TIME_MINS = 1;

    private DrawerLayout mDrawerLayout;
	private ViewGroup mSideMenu;

	private ActionBarDrawerToggle mDrawerToggle;
	private INaturalistApp app;
	private ActivityHelper mHelper;
    private UserDetailsReceiver mUserDetailsReceiver;
    private boolean mSelectedBottomGrid;
    private INaturalistApp mApp;

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

        resizeMenu();
    }

    private void resizeMenu() {
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        ViewGroup sideMenu = ((ViewGroup)decor.findViewById(R.id.left_drawer));

        if (sideMenu == null) return;

        int navigationBarHeight = getNavigationBarHeight(this);
        sideMenu.setPadding(0, getStatusBarHeight(), 0, navigationBarHeight);
    }

    public void onDrawerCreate(Bundle savedInstanceState) {
        Fabric.with(this, new Crashlytics());

        moveDrawerToTop();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mSideMenu = (ViewGroup) findViewById(R.id.left_drawer);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, 0, 0) {

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                resizeMenu();
            }

            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            public void onDrawerOpened(View drawerView) {
                // Log an event every time the side menu is opened
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_MENU);

                refreshUnreadActivities();

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
            ((ImageView)findViewById(R.id.menu_missions_icon)).setAlpha(0.54f);
            ((ImageView)findViewById(R.id.menu_help_icon)).setAlpha(0.54f);
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


        refreshUnreadActivities();
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
            ((TextView)findViewById(R.id.side_menu_username)).setText(username);
            findViewById(R.id.menu_login).setVisibility(View.INVISIBLE);
            findViewById(R.id.side_menu_username).setVisibility(View.VISIBLE);

            if (System.currentTimeMillis() - lastRefreshTime > 1000 * 60 * USER_REFRESH_TIME_MINS) {
                // Get fresh user details from the server
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_USER_DETAILS, null, this, INaturalistService.class);
                ContextCompat.startForegroundService(this, serviceIntent);
            }
        } else {
            findViewById(R.id.menu_login).setVisibility(View.VISIBLE);
            findViewById(R.id.side_menu_username).setVisibility(View.INVISIBLE);
        }

        mApp = (INaturalistApp) getApplication();

        if (obsCount > -1) {
            if (obsCount == 1) {
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(mApp.getStringResourceByName("observation_count_single_all_caps", "observation_count_single"), obsCount));
            } else {
                DecimalFormat formatter = new DecimalFormat("#,###,###");
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(mApp.getStringResourceByName("observation_count_all_caps", "observation_count"), formatter.format(obsCount)));
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
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(mApp.getStringResourceByName("observation_count_single_all_caps", "observation_count_single"), count));
            } else {
                ((TextView) findViewById(R.id.observation_count)).setText(String.format(mApp.getStringResourceByName("observation_count_all_caps", "observation_count"), count));
            }

            cursor.close();
        }

        if (userIconUrl != null) {
            UrlImageViewHelper.setUrlDrawable((ImageView)findViewById(R.id.side_menu_user_pic), userIconUrl, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    ((ImageView)findViewById(R.id.side_menu_no_user_pic)).setVisibility(View.GONE);
                    ((ImageView)findViewById(R.id.side_menu_user_pic)).setVisibility(View.VISIBLE);
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    // Return a circular version of the profile picture
                    Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                    return ImageUtils.getCircleBitmap(centerCrop);
                }
            });

        } else {
            ((ImageView)findViewById(R.id.side_menu_no_user_pic)).setVisibility(View.VISIBLE);
            ((ImageView)findViewById(R.id.side_menu_user_pic)).setVisibility(View.GONE);
        }
    }

    public void showNewObsMenu() {
        mSelectedBottomGrid = false;
        new BottomSheet.Builder(this).sheet(R.menu.observation_list_menu).listener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent;
                mSelectedBottomGrid = true;

                switch (which) {
                    case R.id.camera:
                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_SHUTTER);

                        intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, BaseFragmentActivity.this, ObservationEditor.class);
                        intent.putExtra(ObservationEditor.TAKE_PHOTO, true);
                        startActivityForResult(intent, REQUEST_CODE_OBSERVATION_EDIT);
                        break;
                    case R.id.upload_photo:
                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_LIBRARY_START);

                        intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, BaseFragmentActivity.this, ObservationEditor.class);
                        intent.putExtra(ObservationEditor.CHOOSE_PHOTO, true);
                        startActivityForResult(intent, REQUEST_CODE_OBSERVATION_EDIT);
                        break;
                    case R.id.text:
                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_NO_PHOTO);

                        startActivityForResult(new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, BaseFragmentActivity.this, ObservationEditor.class), REQUEST_CODE_OBSERVATION_EDIT);
                        break;
                }
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (!mSelectedBottomGrid) {
                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEW_OBS_CANCEL);
                }
                mSelectedBottomGrid = false;
            }
        }).show();
    }

	private void buildSideMenu() {

        // Only show guides only for Android 4+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            findViewById(R.id.menu_guides).setVisibility(View.GONE);
        }


        findViewById(R.id.menu_add_obs).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNewObsMenu();
            }
        });
        findViewById(R.id.menu_explore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, ExploreActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
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

                Intent intent = new Intent(BaseFragmentActivity.this, UserActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivityIfNew(intent);
            }

        });
        findViewById(R.id.menu_missions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, MissionsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });
        findViewById(R.id.menu_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(getString(R.string.inat_help_url)));
                startActivity(i);
            }
        });

        findViewById(R.id.menu_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, SettingsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });

        findViewById(R.id.menu_header).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, ObservationListActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        });

        findViewById(R.id.side_menu_user_pic_container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityIfNew(new Intent(BaseFragmentActivity.this, ProfileEditor.class), false);
            }
        });


        if (ExploreActivity.class.getName().equals(this.getClass().getName())) {
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
        if (UserActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_activity).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_activity_icon)).setAlpha(1.0f);
            }
        }
        if (SettingsActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_settings).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_settings_icon)).setAlpha(1.0f);
            }
        }
        if (MissionsActivity.class.getName().equals(this.getClass().getName())) {
            findViewById(R.id.menu_missions).setBackgroundColor(getResources().getColor(R.color.side_menu_item_bg_current));
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                ((ImageView) findViewById(R.id.menu_missions_icon)).setAlpha(1.0f);
            }
        }
    }


    protected void startActivityIfNew(Intent intent) {
        startActivityIfNew(intent, true);
    }

    protected void startActivityIfNew(Intent intent, boolean closeCurrentActivity) {
        if (intent.getComponent().getClassName().equals(this.getClass().getName())) {
            // Activity is already loaded
            mDrawerLayout.closeDrawer(mSideMenu);
            return;
        }

        startActivity(intent);
        overridePendingTransition(R.anim.show, R.anim.hide);
        if (closeCurrentActivity) {
            if (this.getClass().getName().equals(ObservationListActivity.class.getName())) {
                // Always leave the top/main activity (obs list) as-is and don't close it -
                // so when the user clicks the phone's back button, it will return to this screen.
                // Do close the side drawer, though.
                mDrawerLayout.closeDrawer(mSideMenu);
            } else {
                finish();
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            if (mDrawerLayout != null) {
                if (mDrawerLayout.isDrawerOpen(mSideMenu)) {
                    mDrawerLayout.closeDrawer(mSideMenu);
                } else {
                    mDrawerLayout.openDrawer(mSideMenu);
                }

                return true;
            }
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
            safeRegisterReceiver(mUserDetailsReceiver, filter);
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

        safeUnregisterReceiver(mUserDetailsReceiver);
    }

    // Need to wrap the unregisterReceiver call in a try/catch, since sometimes the OS iteself
    // will unregister it for us in very low memory issues.
    protected void safeUnregisterReceiver(BroadcastReceiver receiver) {
        safeUnregisterReceiver(receiver, this);
    }

    protected static void safeUnregisterReceiver(BroadcastReceiver receiver, Context context) {
        try {
            if (receiver != null) context.unregisterReceiver(receiver);
        } catch (Exception e) {
        }
    }

    public void safeRegisterReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        safeRegisterReceiver(receiver, filter, this);
    }

    // Tries to unregister receiver before registering it
    public static void safeRegisterReceiver(BroadcastReceiver receiver, IntentFilter filter, Context context) {
        safeUnregisterReceiver(receiver, context);
        try {
            context.registerReceiver(receiver, filter);
        } catch (Exception e) {

        }
    }

    private class UserDetailsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Got GET_USER_DETAILS_RESULT");
            BetterJSONObject user = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.USER);
            boolean authenticationFailed = intent.getBooleanExtra(INaturalistService.AUTHENTICATION_FAILED, false);

            if (authenticationFailed) {
                // This means the user has changed his password on the website
                Intent intent2 = new Intent(BaseFragmentActivity.this, LoginSignupActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent2.putExtra(LoginSignupActivity.SIGNUP, false);
                intent2.putExtra(LoginSignupActivity.PASSWORD_CHANGED, true);
                startActivity(intent2);
            }

            if (user == null) {
                return;
            }

            SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putInt("observation_count", user.getInt("observations_count"));
            String iconUrl = user.has("medium_user_icon_url") ? user.getString("medium_user_icon_url") : user.getString("user_icon_url");
            editor.putString("user_icon_url", iconUrl);
            editor.putString("user_bio", user.getString("description"));
            editor.putString("user_email", user.getString("email"));
            editor.putString("user_full_name", user.getString("name"));
            editor.putLong("last_user_details_refresh_time", System.currentTimeMillis());
            String currentUsername = prefs.getString("username", null);
            String newUsername = user.getString("login");

            if ((currentUsername != null) && (newUsername != null) && (!currentUsername.equals(newUsername))) {
                // Username changed remotely - Update all existing observations' username
                ContentValues cv = new ContentValues();
                cv.put("user_login", newUsername);
                // Update its sync at time so we won't update the remote servers later on (since we won't
                // accidently consider this an updated record)
                cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                int count = getContentResolver().update(Observation.CONTENT_URI, cv, "user_login = ?", new String[]{ currentUsername });
                Log.d(TAG, String.format("Updated %d observations with new user login %s from %s", count, newUsername, currentUsername));
            }

            editor.putString("username", newUsername);
            editor.apply();

            // Update network settings as well
            int networkSiteId = user.getInt("site_id");
            // Find matching network name (according to site id)
            INaturalistApp app = (INaturalistApp) getApplication();
            final String[] inatNetworks = app.getINatNetworks();
            for (String inatNetwork : inatNetworks) {
                try {
                    int currentSiteId = Integer.valueOf(app.getStringResourceByName("inat_site_id_" + inatNetwork));
                    if (currentSiteId == networkSiteId) {
                        // Found a match
                        app.setInaturalistNetworkMember(inatNetwork, false);
                        break;
                    }
                } catch (NumberFormatException exc) {
                    exc.printStackTrace();
                    continue;
                }
            }

            refreshUserDetails();
        }
    }

    private void refreshUnreadActivities() {
        // Show the unread activities badge
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        int unreadActivities = prefs.getInt("unread_activities", 0);
        TextView activityBadge = (TextView)findViewById(R.id.activity_badge);
        activityBadge.setVisibility(unreadActivities > 0 ? View.VISIBLE : View.GONE);
        activityBadge.setText(String.format(getString(R.string.new_activities), unreadActivities));
    }


    // Taken from: https://stackoverflow.com/a/29609679/1233767
    private static int getNavigationBarHeight(Context context) {
        Point appUsableSize = getAppUsableScreenSize(context);
        Point realScreenSize = getRealScreenSize(context);

        // navigation bar on the right
        if (appUsableSize.x < realScreenSize.x) {
            // Treat this as zero height
            return 0;
        }

        // navigation bar at the bottom
        if (appUsableSize.y < realScreenSize.y) {
            return realScreenSize.y - appUsableSize.y;
        }

        // navigation bar is not present
        return 0;
    }

    // Taken from: https://stackoverflow.com/a/29609679/1233767
    private static Point getAppUsableScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    // Taken from: https://stackoverflow.com/a/29609679/1233767
    private static Point getRealScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();

        if (Build.VERSION.SDK_INT >= 17) {
            display.getRealSize(size);
        } else if (Build.VERSION.SDK_INT >= 14) {
            try {
                size.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                size.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            } catch (IllegalAccessException e) {} catch (InvocationTargetException e) {} catch (NoSuchMethodException e) {}
        }

        return size;
    }
}
