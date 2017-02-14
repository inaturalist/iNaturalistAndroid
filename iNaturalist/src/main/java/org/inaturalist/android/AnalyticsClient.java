package org.inaturalist.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.amplitude.api.Amplitude;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Encapsulates the analytics client (Amplitude) and adds some extra functionality (singleton class)
 */
public class AnalyticsClient {

    public static final String EVENT_PARAM_VIA = "Via";

    public static final String EVENT_NAME_MENU = "Menu";
    public static final String EVENT_NAME_ME = "Me";
    public static final String EVENT_NAME_NEW_OBS_START = "New Obs - Start";
    public static final String EVENT_NAME_NEW_OBS_SAVE = "New Obs - Save New Observation";
    public static final String EVENT_NAME_EXPLORE_MAP = "Explore - Map";

    public static final String EVENT_NAME_MISSIONS = "Missions";
    public static final String EVENT_NAME_MISSIONS_ONBOARDING = "Missions - Onboarding";
    public static final String EVENT_NAME_MISSIONS_CATEGORY = "Missions - Category %s";
    public static final String EVENT_NAME_MISSIONS_CATEGORY_ALL = "Missions - Category All";
    public static final String EVENT_NAME_MISSION_DETAILS = "Missions - View Detail";
    public static final String EVENT_NAME_NEARBY_MISSION = "Missions - Tap - Nearby Mission";
    public static final String EVENT_NAME_NEARBY_OBS_FROM_MISSION = "Missions - Tap - Nearby Observation";
    public static final String EVENT_NAME_NEARBY_WIKI_ARTICLE_FROM_MISSION = "Missions - Tap - Wikipedia Article";

    // Singleton instance
    private static AnalyticsClient mAnalyticsClient = null;

    private Application mApplication;
    private Activity mCurrentActivity;

    private AnalyticsClient(Application application) {
        mApplication = application;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // Modern way of getting current activity
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {

                }

                @Override
                public void onActivityStarted(Activity activity) {

                }

                @Override
                public void onActivityResumed(Activity activity) {
                    mCurrentActivity = activity;
                }

                @Override
                public void onActivityPaused(Activity activity) {

                }

                @Override
                public void onActivityStopped(Activity activity) {

                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

                }

                @Override
                public void onActivityDestroyed(Activity activity) {

                }
            });
        }
    }

    // Initializes the analytics client - should be called from the main activity or application class
    public static void initAnalyticsClient(Application application) {
        mAnalyticsClient = new AnalyticsClient(application);
    }

    public static AnalyticsClient getInstance() {
        return mAnalyticsClient;
    }

    // Logs an event - automatically ads the "Via" parameter (that indicates the current activity name)
    public void logEvent(String eventName) {
        logEvent(eventName, new JSONObject());
    }

    // Logs an event with parameters - automatically ads the "Via" parameter (that indicates the current activity name)
    public void logEvent(String eventName, JSONObject parameters) {

        // Add the via parameter (which indicates the current screen the event was initiated from)
        String currentActivityName = getCurrentActivityName();
        try {
            parameters.put(EVENT_PARAM_VIA, currentActivityName);
            Amplitude.getInstance().logEvent(eventName, parameters);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Returns current activity name
    private String getCurrentActivityName() {
        String className;

        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) || (mCurrentActivity == null)) {
            // Use deprecated way of getting current activity (when Android version is old)
            ActivityManager am = (ActivityManager)mApplication.getSystemService(Context.ACTIVITY_SERVICE);
            ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
            className = cn.getClassName();
        } else {
            // Use "modern" method of returning current activity
            className = mCurrentActivity.getClass().getName();
        }

        // Extract just the activity class name (not including the package namespace)
        String[] parts = className.split("\\.");
        return parts[parts.length - 1];
    }
}
