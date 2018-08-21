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

    public static final String EVENT_PARAM_VALUE_YES = "Yes";
    public static final String EVENT_PARAM_VALUE_NO = "No";

    public static final String EVENT_NAME_APP_LAUNCH = "AppLaunch";

    public static final String EVENT_NAME_MENU = "Menu";
    public static final String EVENT_NAME_ME = "Me";
    public static final String EVENT_NAME_EXPLORE_MAP = "Explore - Map";
    public static final String EVENT_NAME_USER_ACTIVITY = "User Activity";

    // Missions
    public static final String EVENT_NAME_MISSIONS = "Missions";
    public static final String EVENT_NAME_MISSIONS_ONBOARDING = "Missions - Onboarding";
    public static final String EVENT_NAME_MISSIONS_CATEGORY = "Missions - Category %s";
    public static final String EVENT_NAME_MISSIONS_CATEGORY_ALL = "Missions - Category All";
    public static final String EVENT_NAME_MISSION_DETAILS = "Missions - View Detail";
    public static final String EVENT_NAME_NEARBY_MISSION = "Missions - Tap - Nearby Mission";
    public static final String EVENT_NAME_NEARBY_OBS_FROM_MISSION = "Missions - Tap - Nearby Observation";
    public static final String EVENT_NAME_NEARBY_WIKI_ARTICLE_FROM_MISSION = "Missions - Tap - Wikipedia Article";
    public static final String EVENT_NAME_MISSIONS_OBSERVE = "Missions - Observe";
    public static final String EVENT_PARAM_TAXON_ID = "TaxonId";

    // Onboarding
    public static final String EVENT_NAME_ONBOARDING_LOGO = "Navigate - Onboarding - Logo";
    public static final String EVENT_NAME_ONBOARDING_OBSERVE = "Navigate - Onboarding - Observe";
    public static final String EVENT_NAME_ONBOARDING_SHARE = "Navigate - Onboarding - Share";
    public static final String EVENT_NAME_ONBOARDING_LEARN = "Navigate - Onboarding - Learn";
    public static final String EVENT_NAME_ONBOARDING_CONTRIBUTE = "Navigate - Onboarding - Contribute";
    public static final String EVENT_NAME_ONBOARDING_LOGIN = "Navigate - Onboarding - Login";
    public static final String EVENT_NAME_ONBOARDING_VIEW_NEARBY_OBS = "Onboarding View Nearby Obs";
    public static final String EVENT_NAME_ONBOARDING_SKIP = "Onboarding Skip";
    public static final String EVENT_NAME_ONBOARDING_LOGIN_SKIP = "Onboarding Login Skip";
    public static final String EVENT_NAME_ONBOARDING_LOGIN_CANCEL = "Onboarding Login Cancel";
    public static final String EVENT_NAME_ONBOARDING_LOGIN_ACTION = "Onboarding Login Action";

    // Guides
    public static final String EVENT_NAME_GUIDE_DOWNLOAD_START = "Guide Download - Start";
    public static final String EVENT_NAME_GUIDE_DOWNLOAD_COMPLETE = "Guide Download - Complete";
    public static final String EVENT_NAME_GUIDE_DOWNLOAD_DELETE = "Guide Download - Delete";

    // News
    public static final String EVENT_NAME_NEWS_OPEN_ARTICLE = "News - Open Article";
    public static final String EVENT_NAME_NEWS_TAP_LINK = "News - Tap Link";
    public static final String EVENT_PARAM_PARENT_TYPE = "ParentType";
    public static final String EVENT_PARAM_PARENT_NAME = "ParentName";
    public static final String EVENT_PARAM_ARTICLE_TITLE = "ArticleTitle";
    public static final String EVENT_PARAM_LINK = "Link";

    // Settings
    public static final String EVENT_NAME_SETTING_ENABLED = "Setting Enabled";
    public static final String EVENT_NAME_SETTING_DISABLED = "Setting Disabled";
    public static final String EVENT_PARAM_SETTING = "setting";
    public static final String EVENT_PARAM_VALUE_AUTO_UPLOAD = "InatAutouploadPrefKey";
    public static final String EVENT_PARAM_VALUE_SUGGEST_SPECIES = "InatSuggestSpeciesPrefKey";
    public static final String EVENT_NAME_SETTINGS_NETWORK_CHANGE_BEGAN = "Settings Network Change Began";
    public static final String EVENT_NAME_SETTINGS_NETWORK_CHANGE_COMPLETED = "Settings Network Change Completed";
    public static final String EVENT_NAME_PROFILE_PHOTO_CHANGED = "Profile Photo Changed";
    public static final String EVENT_PARAM_ALREADY_HAD_PHOTO = "AlreadyHadPhoto";
    public static final String EVENT_NAME_PROFILE_PHOTO_REMOVED = "Profile Photo Removed";
    public static final String EVENT_NAME_PROFILE_USERNAME_CHANGED = "Profile Username Changed";

    // View observation events
    public static final String EVENT_NAME_OBS_SHARE_STARTED = "Obs - Share Started";
    public static final String EVENT_NAME_OBS_SHARE_CANCELLED = "Obs - Share Cancelled";
    public static final String EVENT_NAME_OBS_SHARE_FINISHED = "Obs - Share Finished";
    public static final String EVENT_NAME_OBS_FAVE = "Obs - Fave";
    public static final String EVENT_NAME_OBS_UNFAVE = "Obs - Unfave";
    public static final String EVENT_NAME_OBS_PHOTO_FAILED_TO_LOAD = "Obs - Photo Failed to Load";
    public static final String EVENT_PARAM_SIZE = "Size";
    public static final String EVENT_PARAM_VALUE_MEDIUM = "Medium";

    // Observation edits
    public static final String EVENT_PARAM_NEW_VALUE = "New Value";
    public static final String EVENT_NAME_OBS_CAPTIVE_CHANGED = "Obs - Captive Changed";
    public static final String EVENT_NAME_OBS_TAXON_CHANGED = "Obs - Taxon Changed";
    public static final String EVENT_PARAM_IS_TAXON = "Is Taxon";
    public static final String EVENT_VALUE_NO_TAXON = "No Taxon";
    public static final String EVENT_VALUE_UNKNOWN_TAXON = "Unknown";
    public static final String EVENT_NAME_OBS_PROJECTS_CHANGED = "Obs - Projects Changed";
    public static final String EVENT_NAME_OBS_GEOPRIVACY_CHANGED = "Obs - Geoprivacy Changed";
    public static final String EVENT_VALUE_GEOPRIVACY_OPEN = "Open";
    public static final String EVENT_VALUE_GEOPRIVACY_OBSCURED = "Obscured";
    public static final String EVENT_VALUE_GEOPRIVACY_PRIVATE = "Private";
    public static final String EVENT_NAME_OBS_NOTES_CHANGED = "Obs - Notes Changed";
    public static final String EVENT_NAME_OBS_DATE_CHANGED = "Obs - Date Changed";
    public static final String EVENT_NAME_OBS_LOCATION_CHANGED = "Obs - Location Changed";
    public static final String EVENT_PARAM_SOURCE = "Source";
    public static final String EVENT_PARAM_COUNT = "Count";
    public static final String EVENT_VALUE_CAMERA = "Camera";
    public static final String EVENT_VALUE_GALLERY = "Gallery";
    public static final String EVENT_NAME_OBS_ADD_PHOTO = "Obs - Add Photo";
    public static final String EVENT_NAME_OBS_DELETE_PHOTO = "Obs - Delete Photo";
    public static final String EVENT_NAME_OBS_NEW_DEFAULT_PHOTO = "Obs - New Default Photo";
    public static final String EVENT_NAME_OBS_VIEW_HIRES_PHOTO = "Obs - View Hires Photo";
    public static final String EVENT_NAME_OBS_DELETE = "Obs - Delete";
    public static final String EVENT_NAME_OBS_ADD_ID = "Obs - Add Identification";
    public static final String EVENT_VALUE_VIEW_OBS_AGREE = "View Obs Agree";
    public static final String EVENT_VALUE_VIEW_OBS_ADD = "View Obs Add";


    // New observation flow
    public static final String EVENT_NAME_NEW_OBS_START = "New Obs - Start";
    public static final String EVENT_NAME_NEW_OBS_SHUTTER = "New Obs - Shutter";
    public static final String EVENT_NAME_NEW_OBS_LIBRARY_START = "New Obs - Library Start";
    public static final String EVENT_NAME_NEW_OBS_LIBRARY_PICKED = "New Obs - Library Picked";
    public static final String EVENT_PARAM_NUM_PICS = "numPics";
    public static final String EVENT_NAME_NEW_OBS_NO_PHOTO = "New Obs - No Photo";
    public static final String EVENT_NAME_NEW_OBS_CANCEL = "New Obs - Cancel";
    public static final String EVENT_NAME_NEW_OBS_SAVE = "New Obs - Save New Observation";
    public static final String EVENT_PARAM_ONLINE_REACHABILITY = "Online Reachability";
    public static final String EVENT_PARAM_FROM_VISION_SUGGESTION = "owners_identification_from_vision";

    // iNat networks/partners
    public static final String EVENT_NAME_PARTNER_ALERT_PRESENTED = "Partner Alert Presented";
    public static final String EVENT_PARAM_PARTNER = "Partner";

    // Login related
    public static final String EVENT_NAME_LOGIN = "Login";
    public static final String EVENT_VALUE_FACEBOOK = "Facebook";
    public static final String EVENT_VALUE_INATURALIST = "iNaturalist";
    public static final String EVENT_VALUE_GOOGLE_PLUS = "Google+";
    public static final String EVENT_NAME_LOGIN_FAILED = "Login Failed";
    public static final String EVENT_PARAM_CODE = "code";
    public static final String EVENT_PARAM_FROM = "from";
    public static final String EVENT_NAME_CREATE_ACCOUNT = "Create Account";
    public static final String EVENT_NAME_LOGOUT = "Logout";
    public static final String EVENT_NAME_FORGOT_PASSWORD = "Forgot Password";

    // Observation activities
    public static final String EVENT_NAME_SYNC_OBS = "Sync Observation";
    public static final String EVENT_VALUE_MANUAL_FULL_UPLOAD = "Manual Full Upload";
    public static final String EVENT_VALUE_AUTOMATIC_UPLOAD = "Automatic Upload";
    public static final String EVENT_PARAM_NUM_DELETES = "numDeletes";
    public static final String EVENT_PARAM_NUM_UPLOADS = "numUploads";
    public static final String EVENT_NAME_SYNC_STOPPED = "Sync Stopped";
    public static final String EVENT_VALUE_STOP_UPLOAD_BUTTON = "Stop Upload Button";
    public static final String EVENT_VALUE_AUTH_REQUIRED = "Auth Required";
    public static final String EVENT_VALUE_UPLOAD_COMPLETE = "Upload Complete";
    public static final String EVENT_NAME_SYNC_FAILED = "Sync Failed";
    public static final String EVENT_PARAM_ALERT = "Alert";

    // Search in explore
    public static final String EVENT_NAME_EXPLORE_SEARCH_PEOPLE = "Explore - Search - People";
    public static final String EVENT_NAME_EXPLORE_SEARCH_PROJECTS = "Explore - Search - Projects";
    public static final String EVENT_NAME_EXPLORE_SEARCH_PLACES = "Explore - Search - Places";
    public static final String EVENT_NAME_EXPLORE_SEARCH_CRITTERS = "Explore - Search - Critters";
    public static final String EVENT_NAME_EXPLORE_SEARCH_NEAR_ME = "Explore - Search - Near Me";
    public static final String EVENT_NAME_EXPLORE_SEARCH_MINE = "Explore - Search - Mine";

    // Viewing obs details
    public static final String EVENT_NAME_NAVIGATE_OBS_DETAILS = "Navigate - Observations - Details";
    public static final String EVENT_VALUE_EXPLORE_GRID = "Explore Grid";
    public static final String EVENT_VALUE_EXPLORE_LIST = "Explore List";
    public static final String EVENT_VALUE_UPDATES = "Updates";
    public static final String EVENT_VALUE_EXPLORE_MAP = "Explore Map";
    public static final String EVENT_VALUE_ME_TAB = "Me Tab";
    public static final String EVENT_VALUE_IDENTIFICATIONS_TAB = "Identifications Tab";
    public static final String EVENT_VALUE_PROJECT_DETAILS = "Project Details";

    // Singleton instance
    private static AnalyticsClient mAnalyticsClient = null;

    private Application mApplication;
    private Activity mCurrentActivity;

    private AnalyticsClient(Application application) {
        Amplitude.getInstance().initialize(application, application.getString(R.string.amplitude_api_key)).enableForegroundTracking(application);

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // Don't log events for old phones - since those require using the GET_TASKS permission in
            // order to get the current running task (and GET_TASKS is a scary permission).
            return;
        }

        try {
            if (mCurrentActivity != null) {
                // Add the via parameter (which indicates the current screen the event was initiated from)
                String currentActivityName = getCurrentActivityName();
                if (!parameters.has(EVENT_PARAM_VIA)) parameters.put(EVENT_PARAM_VIA, currentActivityName);
            }

            Amplitude.getInstance().logEvent(eventName, parameters);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Returns current activity name
    private String getCurrentActivityName() {
        String className = mCurrentActivity.getClass().getName();

        // Extract just the activity class name (not including the package namespace)
        String[] parts = className.split("\\.");
        return parts[parts.length - 1];
    }
}
