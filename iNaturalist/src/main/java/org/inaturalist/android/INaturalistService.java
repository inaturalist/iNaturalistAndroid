package org.inaturalist.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class INaturalistService extends IntentService {
    // How many observations should we initially download for the user
    private static final int INITIAL_SYNC_OBSERVATION_COUNT = 100;

    private static final int JWT_TOKEN_EXPIRATION_MINS = 25; // JWT Tokens expire after 30 mins - consider 25 mins as the max time (safe margin)
    private static final int OLD_PHOTOS_CACHE_EXPIRATION_HOURS = 24 * 7; // Number of hours after which old cached photos will be deleted locally (and viewed remotely)

    public static final String IS_SHARED_ON_APP = "is_shared_on_app";

    public static final String USER = "user";
    public static final String AUTHENTICATION_FAILED = "authentication_failed";
    public static final String IDENTIFICATION_ID = "identification_id";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String METRIC = "metric";
    public static final String ATTRIBUTE_ID = "attribute_id";
    public static final String VALUE_ID = "value_id";
    public static final String FOLLOWING = "following";
    public static final String FIELD_ID = "field_id";
    public static final String COMMENT_ID = "comment_id";
    public static final String OBSERVATION_RESULT = "observation_result";
    public static final String HISTOGRAM_RESULT = "histogram_result";
    public static final String POPULAR_FIELD_VALUES_RESULT = "popular_field_values_result";
    public static final String USER_OBSERVATIONS_RESULT = "user_observations_result";
    public static final String USER_SEARCH_OBSERVATIONS_RESULT = "user_search_observations_result";
    public static final String OBSERVATION_JSON_RESULT = "observation_json_result";
    public static final String SUCCESS = "success";
    public static final String OBSERVATION_COUNT = "observation_count";
    public static final String PROJECTS_RESULT = "projects_result";
    public static final String IDENTIFICATIONS_RESULT = "identifications_result";
    public static final String EXPLORE_GET_OBSERVATIONS_RESULT = "explore_get_observations_result";
    public static final String EXPLORE_GET_SPECIES_RESULT = "explore_get_species_result";
    public static final String EXPLORE_GET_IDENTIFIERS_RESULT = "explore_get_identifiers_result";
    public static final String EXPLORE_GET_OBSERVERS_RESULT = "explore_get_observers_result";
    public static final String GET_ATTRIBUTES_FOR_TAXON_RESULT = "get_attributes_for_taxon_result";
    public static final String GET_ALL_ATTRIBUTES_RESULT = "get_all_attributes_result";
    public static final String DELETE_ANNOTATION_RESULT = "delete_annotation_result";
    public static final String DELETE_ANNOTATION_VOTE_RESULT = "delete_annotation_vote_result";
    public static final String DELETE_DATA_QUALITY_VOTE_RESULT = "delete_data_quality_vote_result";
    public static final String SET_ANNOTATION_VALUE_RESULT = "set_annotation_value_result";
    public static final String DATA_QUALITY_METRICS_RESULT = "data_quality_metrics_result";
    public static final String AGREE_ANNOTATION_RESULT = "agree_annotation_result";
    public static final String DELETE_ID_CAN_BE_IMPROVED_VOTE_RESULT = "delete_id_can_be_improved_vote_result";
    public static final String ID_CAN_BE_IMPROVED_RESULT = "id_can_be_improved_result";
    public static final String ID_CANNOT_BE_IMPROVED_RESULT = "id_cannot_be_improved_result";
    public static final String AGREE_DATA_QUALITY_RESULT = "agree_data_quality_result";
    public static final String DISAGREE_DATA_QUALITY_RESULT = "disagree_data_quality_result";
    public static final String DISAGREE_ANNOTATION_RESULT = "disagree_annotation_result";
    public static final String UPDATES_RESULT = "updates_results";
    public static final String UPDATES_FOLLOWING_RESULT = "updates_following_results";
    public static final String LIFE_LIST_RESULT = "life_list_result";
    public static final String SPECIES_COUNT_RESULT = "species_count_result";
    public static final String RECOMMENDED_MISSIONS_RESULT = "recommended_missions_result";
    public static final String MISSIONS_BY_TAXON_RESULT = "missions_by_taxon_result";
    public static final String GET_CURRENT_LOCATION_RESULT = "get_current_location_result";
    public static final String TAXON_OBSERVATION_BOUNDS_RESULT = "taxon_observation_bounds_result";
    public static final String USER_DETAILS_RESULT = "user_details_result";
    public static final String REFRESH_CURRENT_USER_SETTINGS_RESULT = "refresh_current_user_settings_result";
    public static final String UPDATE_CURRENT_USER_DETAILS_RESULT = "update_current_user_details_result";
    public static final String OBSERVATION_SYNC_PROGRESS = "observation_sync_progress";
    public static final String ADD_OBSERVATION_TO_PROJECT_RESULT = "add_observation_to_project_result";
    public static final String TAXON_ID = "taxon_id";
    public static final String RESEARCH_GRADE = "research_grade";
    public static final String TAXON = "taxon";
    public static final String UUID = "uuid";
    public static final String NETWORK_SITE_ID = "network_site_id";
    public static final String COMMENT_BODY = "comment_body";
    public static final String IDENTIFICATION_BODY = "id_body";
    public static final String DISAGREEMENT = "disagreement";
    public static final String FROM_VISION = "from_vision";
    public static final String PROJECT_ID = "project_id";
    public static final String CHECK_LIST_ID = "check_list_id";
    public static final String ACTION_CHECK_LIST_RESULT = "action_check_list_result";
    public static final String CHECK_LIST_RESULT = "check_list_result";
    public static final String ACTION_GET_TAXON_RESULT = "action_get_taxon_result";
    public static final String SEARCH_TAXA_RESULT = "search_taxa_result";
    public static final String SEARCH_USERS_RESULT = "search_users_result";
    public static final String SEARCH_PLACES_RESULT = "search_places_result";
    public static final String ACTION_GET_TAXON_NEW_RESULT = "action_get_taxon_new_result";
    public static final String ACTION_GET_TAXON_SUGGESTIONS_RESULT = "action_get_taxon_suggestions_result";
    public static final String TAXON_RESULT = "taxon_result";
    public static final String TAXON_SUGGESTIONS = "taxon_suggestions";
    public static final String GUIDE_XML_RESULT = "guide_xml_result";
    public static final String EMAIL = "email";
    public static final String OBS_PHOTO_FILENAME = "obs_photo_filename";
    public static final String OBS_PHOTO_URL = "obs_photo_url";
    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String OBSERVED_ON = "observed_on";
    public static final String USERNAME = "username";
    public static final String LOCATION = "location";
    public static final String FILTERS = "filters";
    public static final String PROGRESS = "progress";
    public static final String EXPAND_LOCATION_BY_DEGREES = "expand_location_by_degrees";
    public static final String QUERY = "query";
    public static final String OBSERVATIONS = "observations";
    public static final String IDENTIFICATIONS = "identifications";
    public static final String LIFE_LIST_ID = "life_list_id";
    public static final String PASSWORD = "password";
    public static final String LICENSE = "license";
    public static final String RESULTS = "results";
    public static final String LIFE_LIST = "life_list";
    public static final String REGISTER_USER_ERROR = "error";
    public static final String REGISTER_USER_STATUS = "status";
    public static final String SYNC_CANCELED = "sync_canceled";
    public static final String SYNC_FAILED = "sync_failed";
    public static final String FIRST_SYNC = "first_sync";
    public static final String PAGE_NUMBER = "page_number";
    public static final String PAGE_SIZE = "page_size";

    public static final int NEAR_BY_OBSERVATIONS_PER_PAGE = 25;
    public static final int EXPLORE_DEFAULT_RESULTS_PER_PAGE = 30;

    public static String TAG = "INaturalistService";
    public static String HOST = "https://www.inaturalist.org";
    public static String API_HOST = "https://api.inaturalist.org/v1";
    public static String USER_AGENT = "iNaturalist/%VERSION% (" +
            "Build %BUILD%; " +
            "Android " + System.getProperty("os.version") + " " + android.os.Build.VERSION.INCREMENTAL + "; " +
            "SDK " + android.os.Build.VERSION.SDK_INT + "; " +
            android.os.Build.DEVICE + " " +
            android.os.Build.MODEL + " " +
            android.os.Build.PRODUCT + ")";
    public static String ACTION_GET_HISTOGRAM = "action_get_histogram";
    public static String ACTION_GET_POPULAR_FIELD_VALUES = "action_get_popular_field_values";
    public static String ACTION_REGISTER_USER = "register_user";
    public static String ACTION_PASSIVE_SYNC = "passive_sync";
    public static String ACTION_GET_ADDITIONAL_OBS = "get_additional_observations";
    public static String ACTION_ADD_IDENTIFICATION = "add_identification";
    public static String ACTION_ADD_PROJECT_FIELD = "add_project_field";
    public static String ACTION_ADD_FAVORITE = "add_favorite";
    public static String ACTION_REMOVE_FAVORITE = "remove_favorite";
    public static String ACTION_GET_TAXON = "get_taxon";
    public static String ACTION_SEARCH_TAXA = "search_taxa";
    public static String ACTION_SEARCH_USERS = "search_users";
    public static String ACTION_SEARCH_PLACES = "search_places";
    public static String ACTION_GET_TAXON_NEW = "get_taxon_new";
    public static String ACTION_GET_TAXON_SUGGESTIONS = "get_taxon_suggestions";
    public static String ACTION_FIRST_SYNC = "first_sync";
    public static String ACTION_PULL_OBSERVATIONS = "pull_observations";
    public static String ACTION_GET_OBSERVATION = "get_observation";
    public static String ACTION_GET_AND_SAVE_OBSERVATION = "get_and_save_observation";
    public static String ACTION_FLAG_OBSERVATION_AS_CAPTIVE = "flag_observation_as_captive";
    public static String ACTION_GET_CHECK_LIST = "get_check_list";
    public static String ACTION_JOIN_PROJECT = "join_project";
    public static String ACTION_LEAVE_PROJECT = "leave_project";
    public static String ACTION_GET_JOINED_PROJECTS = "get_joined_projects";
    public static String ACTION_GET_JOINED_PROJECTS_ONLINE = "get_joined_projects_online";
    public static String ACTION_GET_NEARBY_PROJECTS = "get_nearby_projects";
    public static String ACTION_GET_FEATURED_PROJECTS = "get_featured_projects";
    public static String ACTION_ADD_OBSERVATION_TO_PROJECT = "add_observation_to_project";
    public static String ACTION_REMOVE_OBSERVATION_FROM_PROJECT = "remove_observation_from_project";
    public static String ACTION_GET_ALL_GUIDES = "get_all_guides";
    public static String ACTION_GET_MY_GUIDES = "get_my_guides";
    public static String ACTION_GET_NEAR_BY_GUIDES = "get_near_by_guides";
    public static String ACTION_TAXA_FOR_GUIDE = "get_taxa_for_guide";
    public static String ACTION_GET_USER_DETAILS = "get_user_details";
    public static String ACTION_UPDATE_USER_DETAILS = "update_user_details";
    public static String ACTION_CLEAR_OLD_PHOTOS_CACHE = "clear_old_photos_cache";
    public static String ACTION_GET_ATTRIBUTES_FOR_TAXON = "get_attributes_for_taxon";
    public static String ACTION_GET_ALL_ATTRIBUTES = "get_all_attributes";
    public static String ACTION_DELETE_ANNOTATION = "delete_annotation";
    public static String ACTION_AGREE_ANNOTATION = "agree_annotation";
    public static String ACTION_AGREE_DATA_QUALITY = "agree_data_quality";
    public static String ACTION_DISAGREE_DATA_QUALITY = "disagree_data_quality";
    public static String ACTION_DELETE_ID_CAN_BE_IMPROVED_VOTE = "delete_id_can_be_improved_vote";
    public static String ACTION_ID_CAN_BE_IMPROVED_VOTE = "id_can_be_improved_vote";
    public static String ACTION_ID_CANNOT_BE_IMPROVED_VOTE = "id_cannot_be_improved_vote";
    public static String ACTION_DELETE_ANNOTATION_VOTE = "delete_annotation_vote";
    public static String ACTION_DELETE_DATA_QUALITY_VOTE = "delete_data_quality_vote";
    public static String ACTION_GET_DATA_QUALITY_METRICS = "get_data_quality_metrics";
    public static String ACTION_SET_ANNOTATION_VALUE = "set_annotation_value";
    public static String ACTION_DISAGREE_ANNOTATION = "disagree_annotation";
    public static String ACTION_GET_PROJECT_NEWS = "get_project_news";
    public static String ACTION_GET_NEWS = "get_news";
    public static String ACTION_GET_PROJECT_OBSERVATIONS = "get_project_observations";
    public static String ACTION_GET_PROJECT_SPECIES = "get_project_species";
    public static String ACTION_GET_PROJECT_OBSERVERS = "get_project_observers";
    public static String ACTION_GET_PROJECT_IDENTIFIERS = "get_project_identifiers";
    public static String ACTION_PROJECT_OBSERVATIONS_RESULT = "get_project_observations_result";
    public static String ACTION_PROJECT_NEWS_RESULT = "get_project_news_result";
    public static String ACTION_NEWS_RESULT = "get_news_result";
    public static String ACTION_PROJECT_SPECIES_RESULT = "get_project_species_result";
    public static String ACTION_PROJECT_OBSERVERS_RESULT = "get_project_observers_result";
    public static String ACTION_PROJECT_IDENTIFIERS_RESULT = "get_project_identifiers_result";
    public static String ACTION_SYNC = "sync";
    public static String ACTION_NEARBY = "nearby";
    public static String ACTION_AGREE_ID = "agree_id";
    public static String ACTION_REMOVE_ID = "remove_id";
    public static String ACTION_UPDATE_ID = "update_id";
    public static String ACTION_RESTORE_ID = "restore_id";
    public static String ACTION_GUIDE_ID = "guide_id";
    public static String ACTION_ADD_COMMENT = "add_comment";
    public static String ACTION_UPDATE_COMMENT = "update_comment";
    public static String ACTION_DELETE_COMMENT = "delete_comment";
    public static String ACTION_SYNC_COMPLETE = "sync_complete";
    public static String ACTION_GET_AND_SAVE_OBSERVATION_RESULT = "get_and_save_observation_result";
    public static String ACTION_GET_ADDITIONAL_OBS_RESULT = "get_additional_obs_result";
    public static String ACTION_OBSERVATION_RESULT = "action_observation_result";
    public static String ACTION_JOINED_PROJECTS_RESULT = "joined_projects_result";
    public static String ACTION_NEARBY_PROJECTS_RESULT = "nearby_projects_result";
    public static String ACTION_FEATURED_PROJECTS_RESULT = "featured_projects_result";
    public static String ACTION_ALL_GUIDES_RESULT = "all_guides_results";
    public static String ACTION_MY_GUIDES_RESULT = "my_guides_results";
    public static String ACTION_NEAR_BY_GUIDES_RESULT = "near_by_guides_results";
    public static String ACTION_TAXA_FOR_GUIDES_RESULT = "taxa_for_guides_results";
    public static String ACTION_GET_USER_DETAILS_RESULT = "get_user_details_result";
    public static String ACTION_UPDATE_USER_DETAILS_RESULT = "update_user_details_result";
    public static String ACTION_GUIDE_XML_RESULT = "guide_xml_result";
    public static String ACTION_GUIDE_XML = "guide_xml";
    public static String GUIDES_RESULT = "guides_result";
    public static String ACTION_USERNAME = "username";
    public static String ACTION_FULL_NAME = "full_name";
    public static String ACTION_USER_BIO = "user_bio";
    public static String ACTION_USER_PASSWORD = "user_password";
    public static String ACTION_USER_EMAIL = "user_email";
    public static String ACTION_USER_PIC = "user_pic";
    public static String ACTION_USER_DELETE_PIC = "user_delete_pic";
    public static String ACTION_REGISTER_USER_RESULT = "register_user_result";
    public static String TAXA_GUIDE_RESULT = "taxa_guide_result";
    public static String ACTION_GET_SPECIFIC_USER_DETAILS = "get_specific_user_details";
    public static String ACTION_REFRESH_CURRENT_USER_SETTINGS = "refresh_current_user_settings";
    public static String ACTION_UPDATE_CURRENT_USER_DETAILS = "update_current_user_details";
    public static String ACTION_GET_CURRENT_LOCATION = "get_current_location";
    public static String ACTION_GET_LIFE_LIST = "get_life_list";
    public static String ACTION_GET_USER_SPECIES_COUNT = "get_species_count";
    public static String ACTION_GET_USER_IDENTIFICATIONS = "get_user_identifications";
    public static String ACTION_GET_USER_UPDATES = "get_user_udpates";
    public static String ACTION_EXPLORE_GET_OBSERVATIONS = "explore_get_observations";
    public static String ACTION_EXPLORE_GET_SPECIES = "explore_get_species";
    public static String ACTION_EXPLORE_GET_IDENTIFIERS = "explore_get_identifiers";
    public static String ACTION_EXPLORE_GET_OBSERVERS = "explore_get_observers";
    public static String ACTION_VIEWED_UPDATE = "viewed_update";
    public static String ACTION_GET_USER_OBSERVATIONS = "get_user_observations";
    public static String ACTION_GET_RECOMMENDED_MISSIONS = "get_recommended_missions";
    public static String ACTION_GET_MISSIONS_BY_TAXON = "get_missions_by_taxon";
    public static String ACTION_SEARCH_USER_OBSERVATIONS = "search_user_observations";
    public static String ACTION_GET_TAXON_OBSERVATION_BOUNDS = "get_taxon_observation_bounds";
    public static String ACTION_UPDATE_USER_NETWORK = "update_user_network";
    public static Integer SYNC_OBSERVATIONS_NOTIFICATION = 1;
    public static Integer SYNC_PHOTOS_NOTIFICATION = 2;
    public static Integer AUTH_NOTIFICATION = 3;
    private String mLogin;
    private String mCredentials;
    private SharedPreferences mPreferences;
    private boolean mPassive;
    private INaturalistApp mApp;
    private LoginType mLoginType;

    private boolean mIsStopped = false;

    private boolean mIsSyncing;
    private boolean mIsClearingOldPhotosCache;

    private Handler mHandler;

    private GoogleApiClient mLocationClient;

    private ArrayList<SerializableJSONArray> mProjectObservations;

    private Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>> mProjectFieldValues;

    private Header[] mResponseHeaders = null;

    private JSONArray mResponseErrors;

    private String mNearByObservationsUrl;
    private int mLastStatusCode = 0;
    private Object mObservationLock = new Object();

    public enum LoginType {
        PASSWORD,
        GOOGLE,
        FACEBOOK,
        OAUTH_PASSWORD
    };


    public INaturalistService() {
        super("INaturalistService");

        mHandler = new Handler();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service onCreate");

        startIntentForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");

        startIntentForeground();

        return super.onStartCommand(intent, flags, startId);
    }


    private void startIntentForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            Log.i(TAG, "Service startIntentForeground");

            // See: https://stackoverflow.com/a/46449975/1233767
            String CHANNEL_ID = "inaturalist_service";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_LOW);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(1, notification);
        }
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                onHandleIntentWorker(intent);
            }
        }).start();
    }

    protected void onHandleIntentWorker(final Intent intent) {
        boolean cancelSyncRequested = false;
        boolean syncFailed = false;
        boolean dontStopSync = false;
        mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        mLogin = mPreferences.getString("username", null);
        mCredentials = mPreferences.getString("credentials", null);
        mLoginType = LoginType.valueOf(mPreferences.getString("login_type", LoginType.OAUTH_PASSWORD.toString()));
        mApp = (INaturalistApp) getApplicationContext();

        if (intent == null) return;

        String action = intent.getAction();

        if (action == null) return;

        mPassive = action.equals(ACTION_PASSIVE_SYNC);

        Log.d(TAG, "Service: " + action);

        try {
            if (action.equals(ACTION_NEARBY)) {

                Boolean getLocation = intent.getBooleanExtra("get_location", false);
                final float locationExpansion = intent.getFloatExtra("location_expansion", 0);
                if (!getLocation) {
                    getNearbyObservations(intent);
                } else {
                    // Retrieve current place before getting nearby observations
                    getLocation(new IOnLocation() {
                        @Override
                        public void onLocation(Location location) {
                            final Intent newIntent = new Intent(intent);

                            if (location != null) {
                                if (locationExpansion == 0) {
                                    newIntent.putExtra("lat", location.getLatitude());
                                    newIntent.putExtra("lng", location.getLongitude());
                                } else {
                                    // Expand place by requested degrees (to make sure results are returned from this API)
                                    newIntent.putExtra("minx", location.getLongitude() - locationExpansion);
                                    newIntent.putExtra("miny", location.getLatitude() - locationExpansion);
                                    newIntent.putExtra("maxx", location.getLongitude() + locationExpansion);
                                    newIntent.putExtra("maxy", location.getLatitude() + locationExpansion);
                                }
                            }
                            try {
                                getNearbyObservations(newIntent);
                            } catch (AuthenticationException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }

            } else if (action.equals(ACTION_FIRST_SYNC)) {
                mIsSyncing = true;
                mApp.setIsSyncing(mIsSyncing);

                saveJoinedProjects();
                boolean success = getUserObservations(INITIAL_SYNC_OBSERVATION_COUNT);

                Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "(is_deleted = 0 OR is_deleted is NULL) AND (user_login = '" + mLogin + "')",
                        null,
                        Observation.DEFAULT_SORT_ORDER);
                c.moveToLast();

                if (c.getCount() > 0) {
                    BetterCursor bc = new BetterCursor(c);
                    int lastId = bc.getInteger(Observation.ID);
                    mPreferences.edit().putInt("last_downloaded_id", lastId).commit();
                }
                c.close();
                if (success) {
                    long lastSync = System.currentTimeMillis();
                    mPreferences.edit().putLong("last_sync_time", lastSync).commit();
                }
                if (!success) throw new SyncFailedException();
                syncObservationFields();
                postProjectObservations();

            } else if (action.equals(ACTION_GET_HISTOGRAM)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                boolean researchGrade = intent.getBooleanExtra(RESEARCH_GRADE, false);
                BetterJSONObject results = getHistogram(taxonId, researchGrade);

                Intent reply = new Intent(HISTOGRAM_RESULT);
                reply.putExtra(HISTOGRAM_RESULT, results);
                reply.putExtra(RESEARCH_GRADE, researchGrade);

                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_POPULAR_FIELD_VALUES)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject results = getPopularFieldValues(taxonId);

                Intent reply = new Intent(POPULAR_FIELD_VALUES_RESULT);
                reply.putExtra(POPULAR_FIELD_VALUES_RESULT, results);

                sendBroadcast(reply);

            } else if (action.equals(ACTION_AGREE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                boolean disagreement = intent.getBooleanExtra(DISAGREEMENT, false);
                addIdentification(observationId, taxonId, null, disagreement, false);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                } else {
                    reply.putExtra(OBSERVATION_RESULT, (Serializable)null);
                    reply.putExtra(OBSERVATION_JSON_RESULT, (String)null);
                }

                sendBroadcast(reply);

            } else if (action.equals(ACTION_RESTORE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int identificationId = intent.getIntExtra(IDENTIFICATION_ID, 0);
                restoreIdentification(identificationId);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);
                Observation observation = new Observation(new BetterJSONObject(observationJson));

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                sendBroadcast(reply);

            } else if (action.equals(ACTION_UPDATE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                int identificationId = intent.getIntExtra(IDENTIFICATION_ID, 0);
                String body = intent.getStringExtra(IDENTIFICATION_BODY);
                updateIdentification(observationId, identificationId, taxonId, body);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);
                Observation observation = new Observation(new BetterJSONObject(observationJson));

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                sendBroadcast(reply);

            } else if (action.equals(ACTION_REMOVE_ID)) {
                int id = intent.getIntExtra(IDENTIFICATION_ID, 0);
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                JSONObject result = removeIdentification(id);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);
                Observation observation = new Observation(new BetterJSONObject(observationJson));

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                sendBroadcast(reply);

            } else if (action.equals(ACTION_ADD_FAVORITE)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                addFavorite(observationId);

            } else if (action.equals(ACTION_REMOVE_FAVORITE)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                removeFavorite(observationId);

            } else if (action.equals(ACTION_GET_ADDITIONAL_OBS)) {
                int obsCount = getAdditionalUserObservations(20);

                Intent reply = new Intent(ACTION_GET_ADDITIONAL_OBS_RESULT);
                reply.putExtra(SUCCESS, obsCount > -1);
                reply.putExtra(OBSERVATION_COUNT, obsCount);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_ADD_IDENTIFICATION)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                String body = intent.getStringExtra(IDENTIFICATION_BODY);
                boolean disagreement = intent.getBooleanExtra(DISAGREEMENT, false);
                boolean fromVision = intent.getBooleanExtra(FROM_VISION, false);
                addIdentification(observationId, taxonId, body, disagreement, fromVision);

                // Wait a little before refreshing the observation details - so we'll let the server update the ID
                // list (otherwise, it won't return the new ID)
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));

                    Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    sendBroadcast(reply);
                }

            } else if (action.equals(ACTION_ADD_PROJECT_FIELD)) {
                int fieldId = intent.getIntExtra(FIELD_ID, 0);
                addProjectField(fieldId);

            } else if (action.equals(ACTION_REGISTER_USER)) {
                String email = intent.getStringExtra(EMAIL);
                String password = intent.getStringExtra(PASSWORD);
                String username = intent.getStringExtra(USERNAME);
                String license = intent.getStringExtra(LICENSE);

                String error = registerUser(email, password, username, license);

                Intent reply = new Intent(ACTION_REGISTER_USER_RESULT);
                reply.putExtra(REGISTER_USER_STATUS, error == null);
                reply.putExtra(REGISTER_USER_ERROR, error);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_NEWS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                SerializableJSONArray results = getProjectNews(projectId);

                Intent reply = new Intent(ACTION_PROJECT_NEWS_RESULT);
                reply.putExtra(RESULTS, results);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_OBSERVATIONS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectObservations(projectId);

                mApp.setServiceResult(ACTION_PROJECT_OBSERVATIONS_RESULT, results);
                Intent reply = new Intent(ACTION_PROJECT_OBSERVATIONS_RESULT);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_IDENTIFIERS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectIdentifiers(projectId);

                Intent reply = new Intent(ACTION_PROJECT_IDENTIFIERS_RESULT);
                mApp.setServiceResult(ACTION_PROJECT_IDENTIFIERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_OBSERVERS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectObservers(projectId);

                Intent reply = new Intent(ACTION_PROJECT_OBSERVERS_RESULT);
                mApp.setServiceResult(ACTION_PROJECT_OBSERVERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_SPECIES)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectSpecies(projectId);

                Intent reply = new Intent(ACTION_PROJECT_SPECIES_RESULT);
                mApp.setServiceResult(ACTION_PROJECT_SPECIES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_ANNOTATION)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = deleteAnnotation(uuid);

                Intent reply = new Intent(DELETE_ANNOTATION_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_ANNOTATION_VOTE)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = deleteAnnotationVote(uuid);

                Intent reply = new Intent(DELETE_ANNOTATION_VOTE_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_ID_CAN_BE_IMPROVED_VOTE)) {
                int obsId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject result = deleteIdCanBeImprovedVote(obsId);

                Intent reply = new Intent(DELETE_ID_CAN_BE_IMPROVED_VOTE_RESULT);
                reply.putExtra(DELETE_ID_CAN_BE_IMPROVED_VOTE_RESULT, result);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_ID_CAN_BE_IMPROVED_VOTE)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject result = voteIdCanBeImproved(observationId, true);

                Intent reply = new Intent(ID_CAN_BE_IMPROVED_RESULT);
                reply.putExtra(ID_CAN_BE_IMPROVED_RESULT, result);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_ID_CANNOT_BE_IMPROVED_VOTE)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject result = voteIdCanBeImproved(observationId, false);

                Intent reply = new Intent(ID_CANNOT_BE_IMPROVED_RESULT);
                reply.putExtra(ID_CANNOT_BE_IMPROVED_RESULT, result);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_SET_ANNOTATION_VALUE)) {
                int obsId = intent.getIntExtra(OBSERVATION_ID, 0);
                int attributeId = intent.getIntExtra(ATTRIBUTE_ID, 0);
                int valueId = intent.getIntExtra(VALUE_ID, 0);
                BetterJSONObject results = setAnnotationValue(obsId, attributeId, valueId);

                Intent reply = new Intent(SET_ANNOTATION_VALUE_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_DATA_QUALITY_METRICS)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject results = getDataQualityMetrics(observationId);

                Intent reply = new Intent(DATA_QUALITY_METRICS_RESULT);
                reply.putExtra(DATA_QUALITY_METRICS_RESULT, results);
                sendBroadcast(reply);


             } else if (action.equals(ACTION_DELETE_DATA_QUALITY_VOTE)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String metric = intent.getStringExtra(METRIC);
                BetterJSONObject result = deleteDataQualityMetricVote(observationId, metric);

                Intent reply = new Intent(DELETE_DATA_QUALITY_VOTE_RESULT);
                reply.putExtra(SUCCESS, result != null);
                sendBroadcast(reply);


            } else if (action.equals(ACTION_AGREE_DATA_QUALITY)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String metric = intent.getStringExtra(METRIC);
                BetterJSONObject results = agreeDataQualityMetric(observationId, metric, true);

                Intent reply = new Intent(AGREE_DATA_QUALITY_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_DISAGREE_DATA_QUALITY)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String metric = intent.getStringExtra(METRIC);
                BetterJSONObject results = agreeDataQualityMetric(observationId, metric, false);

                Intent reply = new Intent(DISAGREE_DATA_QUALITY_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);


            } else if (action.equals(ACTION_AGREE_ANNOTATION)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = agreeAnnotation(uuid, true);

                Intent reply = new Intent(AGREE_ANNOTATION_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_DISAGREE_ANNOTATION)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = agreeAnnotation(uuid, false);

                Intent reply = new Intent(DISAGREE_ANNOTATION_RESULT);
                reply.putExtra(SUCCESS, results != null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ALL_ATTRIBUTES)) {
                BetterJSONObject results = getAllAttributes();

                Intent reply = new Intent(GET_ALL_ATTRIBUTES_RESULT);
                mApp.setServiceResult(GET_ALL_ATTRIBUTES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ATTRIBUTES_FOR_TAXON)) {
                BetterJSONObject taxon = (BetterJSONObject) intent.getSerializableExtra(TAXON);
                BetterJSONObject results = getAttributesForTaxon(taxon.getJSONObject());

                Intent reply = new Intent(GET_ATTRIBUTES_FOR_TAXON_RESULT);
                mApp.setServiceResult(GET_ATTRIBUTES_FOR_TAXON_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TAXON_SUGGESTIONS)) {
                String obsFilename = intent.getStringExtra(OBS_PHOTO_FILENAME);
                String obsUrl = intent.getStringExtra(OBS_PHOTO_URL);
                Double longitude = intent.getDoubleExtra(LONGITUDE, 0);
                Double latitude = intent.getDoubleExtra(LATITUDE, 0);
                Timestamp observedOn = (Timestamp) intent.getSerializableExtra(OBSERVED_ON);
                File tempFile = null;

                if (obsFilename == null) {
                    // It's an online observation - need to download the image first.
                    try {
                        tempFile = File.createTempFile("online_photo", ".jpeg", getCacheDir());

                        if (!downloadToFile(obsUrl, tempFile.getAbsolutePath())) {
                            Intent reply = new Intent(ACTION_GET_TAXON_SUGGESTIONS_RESULT);
                            reply.putExtra(TAXON_SUGGESTIONS, (Serializable) null);
                            sendBroadcast(reply);
                            return;
                        }

                        obsFilename = tempFile.getAbsolutePath();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                // Resize photo to 299x299 max
                String resizedPhotoFilename = ImageUtils.resizeImage(this, obsFilename, null, 299);

                if (tempFile != null) {
                    tempFile.delete();
                }

                BetterJSONObject taxonSuggestions = getTaxonSuggestions(resizedPhotoFilename, latitude, longitude, observedOn);

                File resizedFile = new File(resizedPhotoFilename);
                resizedFile.delete();

                Intent reply = new Intent(ACTION_GET_TAXON_SUGGESTIONS_RESULT);
                reply.putExtra(TAXON_SUGGESTIONS, taxonSuggestions);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TAXON_NEW)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject taxon = getTaxonNew(taxonId);

                Intent reply = new Intent(ACTION_GET_TAXON_NEW_RESULT);
                mApp.setServiceResult(ACTION_GET_TAXON_NEW_RESULT, taxon);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TAXON)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject taxon = getTaxon(taxonId);

                Intent reply = new Intent(ACTION_GET_TAXON_RESULT);
                reply.putExtra(TAXON_RESULT, taxon);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_SEARCH_PLACES)) {
                String query = intent.getStringExtra(QUERY);
                int page = intent.getIntExtra(PAGE_NUMBER, 1);
                BetterJSONObject results = searchAutoComplete("places", query, page);

                Intent reply = new Intent(SEARCH_PLACES_RESULT);
                mApp.setServiceResult(SEARCH_PLACES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_SEARCH_USERS)) {
                String query = intent.getStringExtra(QUERY);
                int page = intent.getIntExtra(PAGE_NUMBER, 1);
                BetterJSONObject results = searchAutoComplete("users", query, page);

                Intent reply = new Intent(SEARCH_USERS_RESULT);
                mApp.setServiceResult(SEARCH_USERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(QUERY, query);
                sendBroadcast(reply);


            } else if (action.equals(ACTION_SEARCH_TAXA)) {
                String query = intent.getStringExtra(QUERY);
                int page = intent.getIntExtra(PAGE_NUMBER, 1);
                BetterJSONObject results = searchAutoComplete("taxa", query, page);

                Intent reply = new Intent(SEARCH_TAXA_RESULT);
                mApp.setServiceResult(SEARCH_TAXA_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

             } else if (action.equals(ACTION_UPDATE_CURRENT_USER_DETAILS)) {
                BetterJSONObject params = (BetterJSONObject) intent.getSerializableExtra(USER);
                BetterJSONObject user = updateCurrentUserDetails(params.getJSONObject());

                Intent reply = new Intent(UPDATE_CURRENT_USER_DETAILS_RESULT);
                reply.putExtra(USER, user);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_REFRESH_CURRENT_USER_SETTINGS)) {
                BetterJSONObject user = getCurrentUserDetails();

                if (user != null) {
                    // Update settings
                    mApp.setShowScientificNameFirst(user.getJSONObject().optBoolean("prefers_scientific_name_first", false));

                    Intent reply = new Intent(REFRESH_CURRENT_USER_SETTINGS_RESULT);
                    reply.putExtra(USER, user);
                    sendBroadcast(reply);
                }

            } else if (action.equals(ACTION_GET_SPECIFIC_USER_DETAILS)) {
                String username = intent.getStringExtra(USERNAME);
                BetterJSONObject user = getUserDetails(username);

                Intent reply = new Intent(USER_DETAILS_RESULT);
                reply.putExtra(USER, user);
                reply.putExtra(USERNAME, username);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_CURRENT_LOCATION)) {
                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        Intent reply = new Intent(GET_CURRENT_LOCATION_RESULT);
                        reply.putExtra(LOCATION, location);
                        sendBroadcast(reply);
                    }
                });

            } else if (action.equals(ACTION_GET_MISSIONS_BY_TAXON)) {
                final String username = intent.getStringExtra(USERNAME);
                final Integer taxonId = intent.getIntExtra(TAXON_ID, 0);
                final float expandLocationByDegrees = intent.getFloatExtra(EXPAND_LOCATION_BY_DEGREES, 0);

                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        if (location == null) {
                            // No place
                            Intent reply = new Intent(MISSIONS_BY_TAXON_RESULT);
                            mApp.setServiceResult(MISSIONS_BY_TAXON_RESULT, null);
                            reply.putExtra(IS_SHARED_ON_APP, true);
                            reply.putExtra(TAXON_ID, taxonId);
                            sendBroadcast(reply);
                            return;
                        }

                        BetterJSONObject missions = getMissions(location, username, taxonId, expandLocationByDegrees);

                        Intent reply = new Intent(MISSIONS_BY_TAXON_RESULT);
                        mApp.setServiceResult(MISSIONS_BY_TAXON_RESULT, missions);
                        reply.putExtra(IS_SHARED_ON_APP, true);
                        reply.putExtra(TAXON_ID, taxonId);
                        sendBroadcast(reply);
                    }
                });

            } else if (action.equals(ACTION_GET_TAXON_OBSERVATION_BOUNDS)) {
                final Integer taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject bounds = getTaxonObservationsBounds(taxonId);

                Intent reply = new Intent(TAXON_OBSERVATION_BOUNDS_RESULT);
                reply.putExtra(TAXON_OBSERVATION_BOUNDS_RESULT, bounds);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_RECOMMENDED_MISSIONS)) {
                final String username = intent.getStringExtra(USERNAME);
                final float expandLocationByDegrees = intent.getFloatExtra(EXPAND_LOCATION_BY_DEGREES, 0);

                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        if (location == null) {
                            // No place
                            Intent reply = new Intent(RECOMMENDED_MISSIONS_RESULT);
                            mApp.setServiceResult(RECOMMENDED_MISSIONS_RESULT, null);
                            reply.putExtra(IS_SHARED_ON_APP, true);
                            sendBroadcast(reply);
                            return;
                        }

                        BetterJSONObject missions = getMissions(location, username, null, expandLocationByDegrees);

                        Intent reply = new Intent(RECOMMENDED_MISSIONS_RESULT);
                        mApp.setServiceResult(RECOMMENDED_MISSIONS_RESULT, missions);
                        reply.putExtra(IS_SHARED_ON_APP, true);
                        sendBroadcast(reply);
                    }
                });

            } else if (action.equals(ACTION_GET_USER_SPECIES_COUNT)) {
                String username = intent.getStringExtra(USERNAME);
                BetterJSONObject speciesCount = getUserSpeciesCount(username);

                Intent reply = new Intent(SPECIES_COUNT_RESULT);
                mApp.setServiceResult(SPECIES_COUNT_RESULT, speciesCount);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(USERNAME, username);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_LIFE_LIST)) {
                int lifeListId = intent.getIntExtra(LIFE_LIST_ID, 0);
                BetterJSONObject lifeList = getUserLifeList(lifeListId);

                Intent reply = new Intent(LIFE_LIST_RESULT);
                mApp.setServiceResult(LIFE_LIST_RESULT, lifeList);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_USER_OBSERVATIONS)) {
                String username = intent.getStringExtra(USERNAME);
                JSONObject observations = getUserObservations(username);

                Intent reply = new Intent(USER_OBSERVATIONS_RESULT);
                mApp.setServiceResult(USER_OBSERVATIONS_RESULT, observations != null ? new BetterJSONObject(observations) : null);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(USERNAME, username);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_SEARCH_USER_OBSERVATIONS)) {
                String query = intent.getStringExtra(QUERY);
                SerializableJSONArray observations = searchUserObservation(query);

                Intent reply = new Intent(USER_SEARCH_OBSERVATIONS_RESULT);
                mApp.setServiceResult(USER_SEARCH_OBSERVATIONS_RESULT, observations);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(QUERY, query);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_VIEWED_UPDATE)) {
                Integer obsId = intent.getIntExtra(OBSERVATION_ID, 0);
                setUserViewedUpdate(obsId);

            } else if (action.equals(ACTION_GET_USER_UPDATES)) {
                Boolean following = intent.getBooleanExtra(FOLLOWING, false);
                SerializableJSONArray updates = getUserUpdates(following);

                Intent reply;
                if (following) {
                    reply = new Intent(UPDATES_FOLLOWING_RESULT);
                    mApp.setServiceResult(UPDATES_FOLLOWING_RESULT, updates);
                } else {
                    reply = new Intent(UPDATES_RESULT);
                    mApp.setServiceResult(UPDATES_RESULT, updates);
                }
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_USER_IDENTIFICATIONS)) {
                String username = intent.getStringExtra(USERNAME);
                BetterJSONObject identifications = getUserIdentifications(username);

                Intent reply = new Intent(IDENTIFICATIONS_RESULT);
                mApp.setServiceResult(IDENTIFICATIONS_RESULT, identifications);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(USERNAME, username);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_OBSERVERS)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                BetterJSONObject results = getExploreResults("observers", filters, pageNumber, pageSize, null);

                Intent reply = new Intent(EXPLORE_GET_OBSERVERS_RESULT);
                mApp.setServiceResult(EXPLORE_GET_OBSERVERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_IDENTIFIERS)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                BetterJSONObject results = getExploreResults("identifiers", filters, pageNumber, pageSize, null);

                Intent reply = new Intent(EXPLORE_GET_IDENTIFIERS_RESULT);
                mApp.setServiceResult(EXPLORE_GET_IDENTIFIERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_SPECIES)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                BetterJSONObject results = getExploreResults("species_counts", filters, pageNumber, pageSize, null);

                Intent reply = new Intent(EXPLORE_GET_SPECIES_RESULT);
                mApp.setServiceResult(EXPLORE_GET_SPECIES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_OBSERVATIONS)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                BetterJSONObject observations = getExploreResults(null, filters, pageNumber, pageSize, "observation.id");

                Intent reply = new Intent(EXPLORE_GET_OBSERVATIONS_RESULT);
                mApp.setServiceResult(EXPLORE_GET_OBSERVATIONS_RESULT, observations);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_ADD_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String body = intent.getStringExtra(COMMENT_BODY);
                addComment(observationId, body);

                // Wait a little before refreshing the observation details - so we'll let the server update the comment
                // list (otherwise, it won't return the new comment)
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);
                Observation observation = new Observation(new BetterJSONObject(observationJson));

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                reply.putExtra(OBSERVATION_JSON_RESULT, observation != null ? observationJson.toString() : null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_UPDATE_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int commentId = intent.getIntExtra(COMMENT_ID, 0);
                String body = intent.getStringExtra(COMMENT_BODY);
                updateComment(commentId, observationId, body);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);
                Observation observation = new Observation(new BetterJSONObject(observationJson));

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int commentId = intent.getIntExtra(COMMENT_ID, 0);
                deleteComment(commentId);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false);
                Observation observation = new Observation(new BetterJSONObject(observationJson));

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GUIDE_XML)) {
                int guideId = intent.getIntExtra(ACTION_GUIDE_ID, 0);
                String guideXMLFilename = getGuideXML(guideId);

                if (guideXMLFilename == null) {
                    // Failed to get the guide XML - try and find the offline version, if available
                    GuideXML guideXml = new GuideXML(this, String.valueOf(guideId));

                    if (guideXml.isGuideDownloaded()) {
                        guideXMLFilename = guideXml.getOfflineGuideXmlFilePath();
                    }
                }

                Intent reply = new Intent(ACTION_GUIDE_XML_RESULT);
                reply.putExtra(GUIDE_XML_RESULT, guideXMLFilename);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_CLEAR_OLD_PHOTOS_CACHE)) {
                // Clear out the old cached photos
                if (!mIsClearingOldPhotosCache) {
                    mIsClearingOldPhotosCache = true;
                    clearOldCachedPhotos();
                    mIsClearingOldPhotosCache = false;
                }


            } else if (action.equals(ACTION_UPDATE_USER_NETWORK)) {
                int siteId = intent.getIntExtra(NETWORK_SITE_ID, 0);
                updateUserNetwork(siteId);

            } else if (action.equals(ACTION_UPDATE_USER_DETAILS)) {
                String username = intent.getStringExtra(ACTION_USERNAME);
                String fullName = intent.getStringExtra(ACTION_FULL_NAME);
                String bio = intent.getStringExtra(ACTION_USER_BIO);
                String password = intent.getStringExtra(ACTION_USER_PASSWORD);
                String email = intent.getStringExtra(ACTION_USER_EMAIL);
                String userPic = intent.getStringExtra(ACTION_USER_PIC);
                boolean deletePic = intent.getBooleanExtra(ACTION_USER_DELETE_PIC, false);

                JSONObject newUser = updateUser(username, email, password, fullName, bio, userPic, deletePic);

                if ((newUser != null) && (!newUser.has("errors"))) {
                    SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();

                    String prevLogin = mLogin;
                    mLogin = newUser.optString("login");
                    editor.putString("username", mLogin);
                    if (!newUser.has("user_icon_url") || newUser.isNull("user_icon_url")) {
                        editor.putString("user_icon_url", null);
                    } else {
                        editor.putString("user_icon_url", newUser.has("medium_user_icon_url") ? newUser.optString("medium_user_icon_url") : newUser.optString("user_icon_url"));
                    }
                    editor.putString("user_bio", newUser.optString("description"));
                    editor.putString("user_email", newUser.optString("email", email));
                    editor.putString("user_full_name", newUser.optString("name"));
                    editor.apply();


                    if ((prevLogin != null) && (!prevLogin.equals(mLogin))) {
                        // Update observations with the new username
                        ContentValues cv = new ContentValues();
                        cv.put("user_login", mLogin);
                        // Update its sync at time so we won't update the remote servers later on (since we won't
                        // accidently consider this an updated record)
                        cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                        int count = getContentResolver().update(Observation.CONTENT_URI, cv, "user_login = ?", new String[]{prevLogin});
                        Log.d(TAG, String.format("Updated %d observations with new user login %s from %s", count, mLogin, prevLogin));
                    }
                }

                Intent reply = new Intent(ACTION_UPDATE_USER_DETAILS_RESULT);
                reply.putExtra(USER, newUser != null ? new BetterJSONObject(newUser) : null);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_USER_DETAILS)) {
                BetterJSONObject user = null;
                boolean authenticationFailed = false;

                try {
                    user = getUserDetails();
                } catch (AuthenticationException exc) {
                    // This means the user has changed his password on the website
                    exc.printStackTrace();
                    authenticationFailed = true;
                }

                Intent reply = new Intent(ACTION_GET_USER_DETAILS_RESULT);
                reply.putExtra(USER, user);
                reply.putExtra(AUTHENTICATION_FAILED, authenticationFailed);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_TAXA_FOR_GUIDE)) {
                int guideId = intent.getIntExtra(ACTION_GUIDE_ID, 0);
                SerializableJSONArray taxa = getTaxaForGuide(guideId);

                mApp.setServiceResult(ACTION_TAXA_FOR_GUIDES_RESULT, taxa);
                Intent reply = new Intent(ACTION_TAXA_FOR_GUIDES_RESULT);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ALL_GUIDES)) {
                SerializableJSONArray guides = getAllGuides();

                mApp.setServiceResult(ACTION_ALL_GUIDES_RESULT, guides);
                Intent reply = new Intent(ACTION_ALL_GUIDES_RESULT);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_MY_GUIDES)) {
                SerializableJSONArray guides = null;
                guides = getMyGuides();

                Intent reply = new Intent(ACTION_MY_GUIDES_RESULT);
                reply.putExtra(GUIDES_RESULT, guides);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_NEAR_BY_GUIDES)) {
                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        if (location == null) {
                            // No place enabled
                            Intent reply = new Intent(ACTION_NEAR_BY_GUIDES_RESULT);
                            reply.putExtra(GUIDES_RESULT, new SerializableJSONArray());
                            sendBroadcast(reply);

                        } else {
                            SerializableJSONArray guides = null;
                            try {
                                guides = getNearByGuides(location);
                            } catch (AuthenticationException e) {
                                e.printStackTrace();
                            }

                            Intent reply = new Intent(ACTION_NEAR_BY_GUIDES_RESULT);
                            reply.putExtra(GUIDES_RESULT, guides);
                            sendBroadcast(reply);
                        }
                    }
                });

            } else if (action.equals(ACTION_GET_NEARBY_PROJECTS)) {
                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        if (location == null) {
                            // No place enabled
                            Intent reply = new Intent(ACTION_NEARBY_PROJECTS_RESULT);
                            mApp.setServiceResult(ACTION_NEARBY_PROJECTS_RESULT, new SerializableJSONArray());
                            reply.putExtra(IS_SHARED_ON_APP, true);
                            sendBroadcast(reply);

                        } else {
                            SerializableJSONArray projects = null;
                            try {
                                projects = getNearByProjects(location);
                            } catch (AuthenticationException e) {
                                e.printStackTrace();
                            }

                            Intent reply = new Intent(ACTION_NEARBY_PROJECTS_RESULT);
                            mApp.setServiceResult(ACTION_NEARBY_PROJECTS_RESULT, projects);
                            reply.putExtra(IS_SHARED_ON_APP, true);
                            sendBroadcast(reply);
                        }
                    }
                });

            } else if (action.equals(ACTION_GET_FEATURED_PROJECTS)) {
                SerializableJSONArray projects = getFeaturedProjects();

                Intent reply = new Intent(ACTION_FEATURED_PROJECTS_RESULT);
                reply.putExtra(PROJECTS_RESULT, projects);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_JOINED_PROJECTS_ONLINE)) {
                SerializableJSONArray projects = null;
                if (mCredentials != null) {
                    projects = getJoinedProjects();
                }

                Intent reply = new Intent(ACTION_JOINED_PROJECTS_RESULT);
                mApp.setServiceResult(ACTION_JOINED_PROJECTS_RESULT, projects);
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_JOINED_PROJECTS)) {
                SerializableJSONArray projects = null;
                if (mCredentials != null) {
                    projects = getJoinedProjectsOffline();
                }

                Intent reply = new Intent(ACTION_JOINED_PROJECTS_RESULT);
                reply.putExtra(PROJECTS_RESULT, projects);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_REMOVE_OBSERVATION_FROM_PROJECT)) {
                int observationId = intent.getExtras().getInt(OBSERVATION_ID);
                int projectId = intent.getExtras().getInt(PROJECT_ID);
                BetterJSONObject result = removeObservationFromProject(observationId, projectId);

            } else if (action.equals(ACTION_ADD_OBSERVATION_TO_PROJECT)) {
                int observationId = intent.getExtras().getInt(OBSERVATION_ID);
                int projectId = intent.getExtras().getInt(PROJECT_ID);
                BetterJSONObject result = addObservationToProject(observationId, projectId);

                Intent reply = new Intent(ADD_OBSERVATION_TO_PROJECT_RESULT);
                reply.putExtra(ADD_OBSERVATION_TO_PROJECT_RESULT, result);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_CHECK_LIST)) {
                int id = intent.getExtras().getInt(CHECK_LIST_ID);
                SerializableJSONArray checkList = getCheckList(id);

                Intent reply = new Intent(ACTION_CHECK_LIST_RESULT);
                reply.putExtra(CHECK_LIST_RESULT, checkList);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_FLAG_OBSERVATION_AS_CAPTIVE)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                flagObservationAsCaptive(id);

            } else if (action.equals(ACTION_GET_NEWS)) {
                SerializableJSONArray news = getNews();

                Intent reply = new Intent(ACTION_NEWS_RESULT);
                reply.putExtra(RESULTS, news);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_AND_SAVE_OBSERVATION)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                Observation observation = getAndDownloadObservation(id);

                Intent reply = new Intent(ACTION_GET_AND_SAVE_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_OBSERVATION)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                JSONObject observationJson = getObservationJson(id, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);

                synchronized (mObservationLock) {
                    String jsonString = observationJson != null ? observationJson.toString() : null;
                    Observation observation = observationJson == null ? null : new Observation(new BetterJSONObject(jsonString));

                    mApp.setServiceResult(ACTION_OBSERVATION_RESULT, observation);
                    mApp.setServiceResult(OBSERVATION_JSON_RESULT, observationJson != null ? jsonString : null);
                }
                reply.putExtra(IS_SHARED_ON_APP, true);
                sendBroadcast(reply);


            } else if (action.equals(ACTION_JOIN_PROJECT)) {
                int id = intent.getExtras().getInt(PROJECT_ID);
                joinProject(id);

            } else if (action.equals(ACTION_LEAVE_PROJECT)) {
                int id = intent.getExtras().getInt(PROJECT_ID);
                leaveProject(id);

            } else if (action.equals(ACTION_PULL_OBSERVATIONS)) {
                // Download observations without uploading any new ones
                if (!mIsSyncing && !mApp.getIsSyncing()) {
                    mIsSyncing = true;
                    mApp.setIsSyncing(mIsSyncing);

                    syncRemotelyDeletedObs();
                    boolean successful = getUserObservations(0);

                    if (successful) {
                        // Update last sync time
                        long lastSync = System.currentTimeMillis();
                        mPreferences.edit().putLong("last_sync_time", lastSync).commit();
                        mPreferences.edit().putLong("last_user_details_refresh_time", 0); // Force to refresh user details
                    } else {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), R.string.could_not_download_observations, Toast.LENGTH_LONG).show();
                            }
                        });

                    }
                }

            } else if (action.equals(ACTION_SYNC)) {
                if (!mIsSyncing && !mApp.getIsSyncing()) {
                    mIsSyncing = true;
                    mApp.setIsSyncing(mIsSyncing);
                    syncObservations();

                    // Update last sync time
                    long lastSync = System.currentTimeMillis();
                    mPreferences.edit().putLong("last_sync_time", lastSync).commit();
                } else {
                    // Already in middle of syncing
                    dontStopSync = true;
                }

            }
        } catch (IllegalArgumentException e) {
            // Handle weird exception raised when sendBroadcast causes serialization of BetterJSONObject
            // and that causes an IllegalArgumentException (see only once).
            e.printStackTrace();
            mIsSyncing = false;
        } catch (CancelSyncException e) {
            cancelSyncRequested = true;
            mApp.setCancelSync(false);
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

        } catch (SyncFailedException e) {
            syncFailed = true;
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

        } catch (AuthenticationException e) {
            if (!mPassive) {
                requestCredentials();
            }
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);
        } finally {
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

            if (mIsSyncing && !dontStopSync && (action.equals(ACTION_SYNC) || action.equals(ACTION_FIRST_SYNC) || action.equals(ACTION_PULL_OBSERVATIONS))) {
                mIsSyncing = false;
                mApp.setIsSyncing(mIsSyncing);

                Log.i(TAG, "Sending ACTION_SYNC_COMPLETE");

                // Notify the rest of the app of the completion of the sync
                Intent reply = new Intent(ACTION_SYNC_COMPLETE);
                reply.putExtra(SYNC_CANCELED, cancelSyncRequested);
                reply.putExtra(SYNC_FAILED, syncFailed);
                reply.putExtra(FIRST_SYNC, action.equals(ACTION_FIRST_SYNC));
                sendBroadcast(reply);
            }
        }
    }

    private void syncObservations() throws AuthenticationException, CancelSyncException, SyncFailedException {
        try {
            Log.d(TAG, "syncObservations: enter");

            JSONObject eventParams = new JSONObject();
            eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, mApp.getAutoSync() ? AnalyticsClient.EVENT_VALUE_AUTOMATIC_UPLOAD : AnalyticsClient.EVENT_VALUE_MANUAL_FULL_UPLOAD);
            Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "is_deleted = 1 AND user_login = '" + mLogin + "'",
                    null,
                    Observation.DEFAULT_SORT_ORDER);

            eventParams.put(AnalyticsClient.EVENT_PARAM_NUM_DELETES, c.getCount());
            Log.d(TAG, "syncObservations: to be deleted: " + c.getCount());
            c.close();

            c = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "(_updated_at > _synced_at AND _synced_at IS NOT NULL AND user_login = '" + mLogin + "') OR " +
                            "(id IS NULL AND _updated_at > _created_at)",
                    null,
                    Observation.SYNC_ORDER);
            eventParams.put(AnalyticsClient.EVENT_PARAM_NUM_UPLOADS, c.getCount());
            Log.d(TAG, "syncObservations: uploads: " + c.getCount());
            c.close();

            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_OBS, eventParams);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mApp.notify(getString(R.string.preparing), getString(R.string.preparing));

        Log.d(TAG, "syncObservations: Calling syncRemotelyDeletedObs");
        syncRemotelyDeletedObs();
        // First, download remote observations (new/updated)
        Log.d(TAG, "syncObservations: Calling getUserObservations");
        if (!getUserObservations(0)) throw new SyncFailedException();

        Log.d(TAG, "syncObservations: After calling getUserObservations");

        // Gather the list of observations that need syncing (because they're new, been updated
        // or had their photos/project fields updated
        Set<Integer> observationIdsToSync = new HashSet<>();

        // Any new/updated observations
        Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                Observation.PROJECTION,
                "(_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL)",
                null,
                Observation.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (!c.isAfterLast()) {
            observationIdsToSync.add(c.getInt(c.getColumnIndexOrThrow(Observation._ID)));
            c.moveToNext();
        }

        c.close();
        Log.d(TAG, "syncObservations: observationIdsToSync: " + observationIdsToSync);

        // Any observation that has new/updated/deleted photos
        c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL) OR " +
                        "(is_deleted = 1)",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (!c.isAfterLast()) {
            observationIdsToSync.add(c.getInt(c.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID)));
            c.moveToNext();
        }

        c.close();

        Log.d(TAG, "syncObservations: observationIdsToSync 2: " + observationIdsToSync);


        // Any observation that has new/updated project fields.
        c = getContentResolver().query(ProjectFieldValue.CONTENT_URI,
                ProjectFieldValue.PROJECTION,
                "(_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL)",
                null,
                ProjectFieldValue.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (!c.isAfterLast()) {
            observationIdsToSync.add(c.getInt(c.getColumnIndexOrThrow(ProjectFieldValue.OBSERVATION_ID)));
            c.moveToNext();
        }

        c.close();
        Log.d(TAG, "syncObservations: observationIdsToSync 3: " + observationIdsToSync);

        List<Integer> obsIdsToRemove = new ArrayList<>();

        for (Integer obsId : observationIdsToSync) {
            c = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "_id in (" + StringUtils.join(observationIdsToSync, ",") + ")",
                    null,
                    Observation.DEFAULT_SORT_ORDER);
            if (c.getCount() == 0) {
                obsIdsToRemove.add(obsId);
                c.close();
                continue;
            }

            c.moveToFirst();
            if (c.getInt(c.getColumnIndexOrThrow(Observation.IS_DELETED)) == 1) {
                obsIdsToRemove.add(obsId);
            }
            c.close();
        }

        Log.d(TAG, "syncObservations: obsIdsToRemove: " + obsIdsToRemove);

        for (Integer obsId : obsIdsToRemove) {
            observationIdsToSync.remove(obsId);
        }

        Log.d(TAG, "syncObservations: observationIdsToSync: " + observationIdsToSync);

        c = getContentResolver().query(Observation.CONTENT_URI,
                Observation.PROJECTION,
                "_id in (" + StringUtils.join(observationIdsToSync, ",") + ")",
                null,
                Observation.DEFAULT_SYNC_ORDER);

        c.moveToFirst();

        while (!c.isAfterLast()) {
            mApp.notify(getString(R.string.syncing_observations),
                    String.format(getString(R.string.syncing_x_out_of_y_observations), (c.getPosition() + 1), c.getCount()));

            Observation observation = new Observation(c);

            mCurrentObservationProgress = 0.0f;
            mTotalProgressForObservation = getTotalProgressForObservation(observation);
            increaseProgressForObservation(observation);

            mApp.setObservationIdBeingSynced(observation._id);
            Log.d(TAG, "syncObservations: Syncing " + observation._id + ": " + observation.toString());

            if ((observation._synced_at == null) || ((observation._updated_at != null) && (observation._updated_at.after(observation._synced_at)))) {
                postObservation(observation);
                increaseProgressForObservation(observation);
            }
            Log.d(TAG, "syncObservations: Finished Syncing " + observation._id + " - now uploading photos");

            postPhotos(observation);
            Log.d(TAG, "syncObservations: Finished uploading photos " + observation._id);
            deleteObservationPhotos(observation); // Delete locally-removed observation photos
            syncObservationFields(observation);
            postProjectObservations(observation);
            Log.d(TAG, "syncObservations: Finished delete photos, obs fields and project obs - " + observation._id);

            c.moveToNext();
        }

        c.close();

        Log.d(TAG, "syncObservations: Calling delete obs");
        deleteObservations(); // Delete locally-removed observations

        Log.d(TAG, "syncObservations: Calling saveJoinedProj");
        // General project data
        saveJoinedProjects();
        Log.d(TAG, "syncObservations: Calling storeProjObs");
        storeProjectObservations();

        redownloadOldObservations();

        Log.d(TAG, "syncObservations: Done");
        mPreferences.edit().putLong("last_user_details_refresh_time", 0); // Force to refresh user details
    }

    private int mTotalProgressForObservation = 0;
    private float mCurrentObservationProgress = 0;

    private void increaseProgressForObservation(Observation observation) {
        float currentProgress = mCurrentObservationProgress;
        float step = (100.0f / mTotalProgressForObservation);
        float newProgress = currentProgress + step;
        if (newProgress >= 99)
            newProgress = 100f; // Round to 100 in case of fractions (since otherwise, we'll get to 99.99999 and similar
        mCurrentObservationProgress = newProgress;

        // Notify the client of the new progress (so we'll update the progress bars)
        Intent reply = new Intent(OBSERVATION_SYNC_PROGRESS);
        reply.putExtra(OBSERVATION_ID, observation.id);
        reply.putExtra(PROGRESS, newProgress);
        sendBroadcast(reply);
    }

    private int getTotalProgressForObservation(Observation observation) {
        int obsCount = 0;
        if ((observation._synced_at == null) || ((observation._updated_at != null) && (observation._updated_at.after(observation._synced_at)))) {
            obsCount = 1;
        }

        int photoCount;
        int externalObsId = observation.id != null ? observation.id : observation._id;

        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "((_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL) OR (is_deleted = 1)) AND " +
                        "((observation_id = ?) OR (_observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ObservationPhoto.DEFAULT_SORT_ORDER);
        photoCount = c.getCount();
        c.close();

        int projectFieldCount;
        c = getContentResolver().query(ProjectFieldValue.CONTENT_URI,
                ProjectFieldValue.PROJECTION,
                "((_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL)) AND " +
                        "((observation_id = ?) OR (observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ProjectFieldValue.DEFAULT_SORT_ORDER);
        projectFieldCount = c.getCount();
        c.close();

        int projectObservationCount;
        c = getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "((is_deleted = 1) OR (is_new = 1)) AND " +
                        "((observation_id = ?) OR (observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ProjectObservation.DEFAULT_SORT_ORDER);
        projectObservationCount = c.getCount();
        c.close();


        return 1 + // We start off with some progress (one "part")
                obsCount + // For the observation upload itself (only if new/update)
                photoCount + // For photos
                projectFieldCount + // For updated/new obs project fields
                projectObservationCount; // For updated/new observation project fields
    }

    // Re-download any observations that have photos saved in the "old" way
    private void redownloadOldObservations() throws AuthenticationException {

        // Find all observations that have photos saved in the old way
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(photo_filename IS NULL) AND (photo_url IS NULL)",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        Log.d(TAG, "redownloadOldObservations: " + c.getCount());

        while (!c.isAfterLast()) {
            Integer obsId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto.OBSERVATION_ID));

            Log.d(TAG, "redownloadOldObservations: " + new ObservationPhoto(c));

            // Delete the observation photo
            Integer obsPhotoId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto.ID));
            getContentResolver().delete(ObservationPhoto.CONTENT_URI, "id = " + obsPhotoId, null);

            // Re-download this observation

            String url = HOST + "/observations/" + Uri.encode(mLogin) + ".json?extra=observation_photos,projects,fields";
            Locale deviceLocale = getResources().getConfiguration().locale;
            String deviceLanguage = deviceLocale.getLanguage();
            url += "&locale=" + deviceLanguage;
            JSONArray json = get(url, true);
            if (json != null && json.length() > 0) {
                syncJson(json, true);
            }

            c.moveToNext();
        }

        c.close();

    }

    private BetterJSONObject getHistogram(int taxonId, boolean researchGrade) throws AuthenticationException {
        String url = String.format("%s/observations/histogram?taxon_id=%d&", API_HOST, taxonId);

        if (researchGrade) {
            url += "quality_grade=research";
        } else {
            url += "verifiable=true";
        }

        JSONArray json = get(url);
        if (json == null || json.length() == 0) {
            return null;
        }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getPopularFieldValues(int taxonId) throws AuthenticationException {
        String url = String.format("%s/observations/popular_field_values?taxon_id=%d&verifiable=true", API_HOST, taxonId);

        JSONArray json = get(url);
        if (json == null || json.length() == 0) {
            return null;
        }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getTaxonSuggestions(String photoFilename, Double latitude, Double longitude, Timestamp observedOn) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String date = observedOn != null ? new SimpleDateFormat("yyyy-MM-dd").format(observedOn) : null;
        ArrayList<NameValuePair> params = new ArrayList<>();
        String url = String.format(API_HOST + "/computervision/score_image");

        params.add(new BasicNameValuePair("locale", deviceLanguage));
        params.add(new BasicNameValuePair("lat", latitude.toString()));
        params.add(new BasicNameValuePair("lng", longitude.toString()));
        if (date != null) params.add(new BasicNameValuePair("observed_on", date));
        params.add(new BasicNameValuePair("image", photoFilename));

        JSONArray json = request(url, "post", params, null, true, true, true);
        if (json == null || json.length() == 0) {
            return null;
        }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            if (!res.has("results")) return null;
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getTaxonNew(int id) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/taxa/" + id + "?locale=" + deviceLanguage;

        JSONArray json = get(url);
        if (json == null || json.length() == 0) {
            return null;
        }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            if (!res.has("results")) return null;
            JSONObject taxon = res.getJSONArray("results").getJSONObject(0);

            return new BetterJSONObject(taxon);
        } catch (JSONException e) {
            return null;
        }
    }


    private BetterJSONObject setAnnotationValue(int observationId, int attributeId, int valueId) throws AuthenticationException {
        String url = API_HOST + "/annotations";


        JSONObject params = new JSONObject();
        try {
            params.put("resource_type", "Observation");
            params.put("resource_id", observationId);
            params.put("controlled_attribute_id", attributeId);
            params.put("controlled_value_id", valueId);
        } catch (JSONException e) {
            e.printStackTrace();

            return null;
        }

        JSONArray json = post(url, params);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }


    private BetterJSONObject agreeAnnotation(String uuid, boolean agree) throws AuthenticationException {
        String url = API_HOST + "/votes/vote/annotation/" + uuid;

        JSONObject params = new JSONObject();

        try {
            if (!agree) params.put("vote", "bad");
            params.put("id", uuid);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        JSONArray json = post(url, params);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject deleteAnnotationVote(String uuid) throws AuthenticationException {
        String url = API_HOST + "/votes/unvote/annotation/" + uuid;

        JSONArray json = delete(url, null);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject voteIdCanBeImproved(int obsId, boolean yes) throws AuthenticationException {
        String url = API_HOST + "/votes/vote/observation/" + obsId;

        JSONObject params = new JSONObject();

        try {
            params.put("vote", yes ? "yes" : "no");
            params.put("id", obsId);
            params.put("scope", "needs_id");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        JSONArray json = post(url, params);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject deleteIdCanBeImprovedVote(int obsId) throws AuthenticationException {
        String url = String.format("%s/votes/unvote/observation/%d?id=%d&scope=needs_id", API_HOST, obsId, obsId);

        JSONArray json = delete(url, null);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getDataQualityMetrics(Integer observationId) throws AuthenticationException {
        String url = String.format("%s/observations/%d/quality_metrics?id=%d", API_HOST, observationId, observationId);

        JSONArray json = get(url, true);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            if (!res.has("results")) return null;
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject agreeDataQualityMetric(Integer observationId, String metric, boolean agree) throws AuthenticationException {
        String url = String.format("%s/observations/%d/quality/%s", API_HOST, observationId, metric);

        JSONObject params = new JSONObject();

        try {
            params.put("agree", agree ? "true" : "false");
            params.put("id", observationId);
            params.put("metric", metric);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        JSONArray json = post(url, params);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject deleteDataQualityMetricVote(Integer observationId, String metric) throws AuthenticationException {
        String url = String.format("%s/observations/%d/quality/%s?id=%d&metric=%s", API_HOST, observationId, metric, observationId, metric);

        JSONArray json = delete(url, null);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject deleteAnnotation(String uuid) throws AuthenticationException {
        String url = API_HOST + "/annotations/" + uuid;

        JSONArray json = delete(url, null);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getAllAttributes() throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();


        String url = API_HOST + "/controlled_terms?locale=" + deviceLanguage;

        JSONArray json = get(url);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            if (!res.has("results")) return null;
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getAttributesForTaxon(JSONObject taxon) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        JSONArray ancestors = taxon.optJSONArray("ancestor_ids");

        if (ancestors == null) return null;

        String ancestry = "";
        for (int i = 0; i < ancestors.length(); i++) {
            int currentTaxonId = ancestors.optInt(i);
            ancestry += String.format("%d,", currentTaxonId);
        }
        ancestry += String.format("%d", taxon.optInt("id"));

        String url = API_HOST + "/controlled_terms/for_taxon?taxon_id=" + ancestry + "&ttl=-1&locale=" + deviceLanguage;

        JSONArray json = get(url);
        if (json == null || json.length() == 0) { return null; }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
            if (!res.has("results")) return null;
            return new BetterJSONObject(res);
        } catch (JSONException e) {
            return null;
        }
    }

    private BetterJSONObject getTaxon(int id) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = String.format("%s/taxa/%d.json?locale=%s", HOST, id, deviceLanguage);

        JSONArray json = get(url);
        if (json == null || json.length() == 0) {
            return null;
        }

        JSONObject res;

        try {
            res = (JSONObject) json.get(0);
        } catch (JSONException e) {
            return null;
        }

        return new BetterJSONObject(res);
    }

    private boolean postProjectObservations(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        if (observation.id == null) {
            // Observation not synced yet - cannot sync its project associations yet
            return true;
        }

        // First, delete any project-observations that were deleted by the user
        Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "is_deleted = 1 AND observation_id = ?",
                new String[]{String.valueOf(observation.id)},
                ProjectObservation.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectObservation projectObservation = new ProjectObservation(c);

            // Clean the errors for the observation
            mApp.setErrorsForObservation(projectObservation.observation_id, projectObservation.project_id, new JSONArray());

            try {
                // Remove obs from project
                BetterJSONObject result = removeObservationFromProject(projectObservation.observation_id, projectObservation.project_id);
                if (result == null) {
                    c.close();
                    throw new SyncFailedException();
                }

                increaseProgressForObservation(observation);
            } catch (Exception exc) {
                // In case we're trying to delete a project-observation that wasn't synced yet
                c.close();
                throw new SyncFailedException();
            }

            getContentResolver().delete(ProjectObservation.CONTENT_URI, "_id = ?", new String[]{String.valueOf(projectObservation._id)});

            c.moveToNext();
        }

        c.close();

        // Next, add new project observations
        c = getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "is_new = 1 AND observation_id = ?",
                new String[]{String.valueOf(observation.id)},
                ProjectObservation.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectObservation projectObservation = new ProjectObservation(c);
            BetterJSONObject result = addObservationToProject(projectObservation.observation_id, projectObservation.project_id);

            if ((result == null) && (mResponseErrors == null)) {
                c.close();
                throw new SyncFailedException();
            }

            increaseProgressForObservation(observation);

            if (mResponseErrors != null) {
                handleProjectFieldErrors(projectObservation.observation_id, projectObservation.project_id);
            } else {
                // Unmark as new
                projectObservation.is_new = false;
                ContentValues cv = projectObservation.getContentValues();
                getContentResolver().update(projectObservation.getUri(), cv, null, null);

                // Clean the errors for the observation
                mApp.setErrorsForObservation(projectObservation.observation_id, projectObservation.project_id, new JSONArray());
            }

            c.moveToNext();
        }

        c.close();

        return true;
    }

    private boolean postProjectObservations() throws AuthenticationException, CancelSyncException, SyncFailedException {
        // First, delete any project-observations that were deleted by the user
        Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "is_deleted = 1",
                null,
                ProjectObservation.DEFAULT_SORT_ORDER);

        if (c.getCount() > 0) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    getString(R.string.projects),
                    getString(R.string.syncing_observation_fields),
                    getString(R.string.syncing));
        }

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectObservation projectObservation = new ProjectObservation(c);

            // Clean the errors for the observation
            mApp.setErrorsForObservation(projectObservation.observation_id, projectObservation.project_id, new JSONArray());

            try {
                // Remove obs from project
                BetterJSONObject result = removeObservationFromProject(projectObservation.observation_id, projectObservation.project_id);
                if (result == null) {
                    c.close();
                    throw new SyncFailedException();
                }
            } catch (Exception exc) {
                // In case we're trying to delete a project-observation that wasn't synced yet
                c.close();
                throw new SyncFailedException();
            }

            c.moveToNext();
        }

        c.close();

        // Now it's safe to delete all of the project-observations locally
        getContentResolver().delete(ProjectObservation.CONTENT_URI, "is_deleted = 1", null);


        // Next, add new project observations
        c = getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "is_new = 1",
                null,
                ProjectObservation.DEFAULT_SORT_ORDER);

        if (c.getCount() > 0) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    getString(R.string.projects),
                    getString(R.string.syncing_observation_fields),
                    getString(R.string.syncing));
        }


        c.moveToFirst();
        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectObservation projectObservation = new ProjectObservation(c);
            BetterJSONObject result = addObservationToProject(projectObservation.observation_id, projectObservation.project_id);

            if ((result == null) && (mResponseErrors == null)) {
                c.close();
                throw new SyncFailedException();
            }

            mApp.setObservationIdBeingSynced(projectObservation.observation_id);

            if (mResponseErrors != null) {
                handleProjectFieldErrors(projectObservation.observation_id, projectObservation.project_id);
            } else {
                // Unmark as new
                projectObservation.is_new = false;
                ContentValues cv = projectObservation.getContentValues();
                getContentResolver().update(projectObservation.getUri(), cv, null, null);

                // Clean the errors for the observation
                mApp.setErrorsForObservation(projectObservation.observation_id, projectObservation.project_id, new JSONArray());
            }

            c.moveToNext();
        }

        c.close();

        mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

        // Finally, retrieve all project observations
        storeProjectObservations();

        return true;
    }

    private boolean handleProjectFieldErrors(int observationId, int projectId) {
        SerializableJSONArray errors = new SerializableJSONArray(mResponseErrors);

        // Couldn't add the observation to the project (probably didn't pass validation)
        String error;
        try {
            error = errors.getJSONArray().getString(0);
        } catch (JSONException e) {
            return false;
        }

        Cursor c2 = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = '" + observationId + "'", null, Observation.DEFAULT_SORT_ORDER);
        c2.moveToFirst();
        if (c2.getCount() == 0) {
            c2.close();
            return false;
        }
        Observation observation = new Observation(c2);
        c2.close();

        c2 = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '" + projectId + "'", null, Project.DEFAULT_SORT_ORDER);
        c2.moveToFirst();
        if (c2.getCount() == 0) {
            c2.close();
            return false;
        }
        Project project = new Project(c2);
        c2.close();

        // Remember the errors for this observation (to be shown in the observation editor screen)
        JSONArray formattedErrors = new JSONArray();
        JSONArray unformattedErrors = errors.getJSONArray();

        for (int i = 0; i < unformattedErrors.length(); i++) {
            try {
                formattedErrors.put(String.format(getString(R.string.failed_to_add_to_project), project.title, unformattedErrors.getString(i)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mApp.setErrorsForObservation(observation.id, project.id, formattedErrors);

        final String errorMessage = String.format(getString(R.string.failed_to_add_obs_to_project),
                observation.species_guess == null ? getString(R.string.unknown) : observation.species_guess, project.title, error);

        // Display toast in this main thread handler (since otherwise it won't get displayed)
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        try {
            JSONObject eventParams = new JSONObject();
            eventParams.put(AnalyticsClient.EVENT_PARAM_ALERT, errorMessage);

            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_FAILED, eventParams);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        return true;
    }

    private void storeProjectObservations() {
        for (int j = 0; j < mProjectObservations.size(); j++) {
            SerializableJSONArray arr = mProjectObservations.get(j);
            if (arr == null) continue;

            JSONArray projectObservations = mProjectObservations.get(j).getJSONArray();

            for (int i = 0; i < projectObservations.length(); i++) {
                JSONObject jsonProjectObservation;
                try {
                    jsonProjectObservation = projectObservations.getJSONObject(i);
                    ProjectObservation projectObservation = new ProjectObservation(new BetterJSONObject(jsonProjectObservation));
                    ContentValues cv = projectObservation.getContentValues();
                    Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI,
                            ProjectObservation.PROJECTION,
                            "project_id = " + projectObservation.project_id + " AND observation_id = " + projectObservation.observation_id,
                            null, ProjectObservation.DEFAULT_SORT_ORDER);
                    if (c.getCount() == 0) {
                        getContentResolver().insert(ProjectObservation.CONTENT_URI, cv);
                    }
                    c.close();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean saveJoinedProjects() throws AuthenticationException, CancelSyncException, SyncFailedException {
        mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                getString(R.string.projects),
                getString(R.string.cleaning_up),
                getString(R.string.syncing));

        SerializableJSONArray projects = getJoinedProjects();

        checkForCancelSync();

        if (projects == null) {
            throw new SyncFailedException();
        }

        JSONArray arr = projects.getJSONArray();

        // Retrieve all currently-joined project IDs
        List<Integer> projectIds = new ArrayList<Integer>();
        HashMap<Integer, JSONObject> projectByIds = new HashMap<Integer, JSONObject>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject jsonProject = arr.getJSONObject(i);
                int id = jsonProject.getInt("id");
                projectIds.add(id);
                projectByIds.put(id, jsonProject);
            } catch (JSONException exc) {
                exc.printStackTrace();
            }
        }


        // Check which projects were un-joined and remove them locally
        try {
            int count = getContentResolver().delete(Project.CONTENT_URI, "id not in (" + StringUtils.join(projectIds, ',') + ")", null);
        } catch (Exception exc) {
            exc.printStackTrace();
            throw new SyncFailedException();
        }

        // Add any newly-joined projects

        for (Map.Entry<Integer, JSONObject> entry : projectByIds.entrySet()) {
            int id = entry.getKey();
            JSONObject jsonProject = entry.getValue();

            Cursor c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = ?", new String[]{String.valueOf(id)}, null);

            if (c.getCount() == 0) {
                Project project = new Project(new BetterJSONObject(jsonProject));
                ContentValues cv = project.getContentValues();
                getContentResolver().insert(Project.CONTENT_URI, cv);
            }
            c.close();
        }

        return true;
    }

    private boolean deleteObservationPhotos(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        // Remotely delete any locally-removed observation photos
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "is_deleted = 1 AND _observation_id = ?",
                new String[]{String.valueOf(observation._id)},
                ObservationPhoto.DEFAULT_SORT_ORDER);

        // for each observation DELETE to /observation_photos/:id
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ObservationPhoto op = new ObservationPhoto(c);

            Log.d(TAG, "deleteObservationPhotos: " + op + "::::" + op._synced_at);
            if (op._synced_at != null) {
                if (op.id != null) {
                    Log.d(TAG, "deleteObservationPhotos: Deleting " + op);
                    JSONArray result = delete(HOST + "/observation_photos/" + op.id + ".json", null);
                    if (result == null) {
                        Log.d(TAG, "deleteObservationPhotos: Deletion error: " + mLastStatusCode);
                        if (mLastStatusCode != HttpStatus.SC_NOT_FOUND) {
                            // Ignore the case where the photo was remotely deleted
                            Log.d(TAG, "deleteObservationPhotos: Not a 404 error");
                            c.close();
                            throw new SyncFailedException();
                        }
                    }
                }
            }
            increaseProgressForObservation(observation);

            int count = getContentResolver().delete(ObservationPhoto.CONTENT_URI,
                    "id = ? or _id = ?", new String[]{String.valueOf(op.id), String.valueOf(op._id)});
            Log.d(TAG, "deleteObservationPhotos: Deleted from DB: " + count);
            c.moveToNext();
        }

        c.close();


        checkForCancelSync();

        return true;
    }

    private boolean deleteObservations() throws AuthenticationException, CancelSyncException, SyncFailedException {
        // Remotely delete any locally-removed observations
        Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                Observation.PROJECTION,
                "is_deleted = 1 AND user_login = '" + mLogin + "'",
                null,
                Observation.DEFAULT_SORT_ORDER);

        Log.d(TAG, "deleteObservations: Deleting " + c.getCount());

        if (c.getCount() > 0) {
            mApp.notify(getString(R.string.deleting_observations), getString(R.string.deleting_observations));
        }

        // for each observation DELETE to /observations/:id
        ArrayList<Integer> obsIds = new ArrayList<Integer>();
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            Observation observation = new Observation(c);
            Log.d(TAG, "deleteObservations: Deleting " + observation);
            JSONArray results = delete(HOST + "/observations/" + observation.id + ".json", null);
            if (results == null) {
                c.close();
                throw new SyncFailedException();
            }

            obsIds.add(observation.id);
            c.moveToNext();
        }

        Log.d(TAG, "deleteObservations: Deleted IDs: " + obsIds);
        c.close();

        // Now it's safe to delete all of the observations locally
        getContentResolver().delete(Observation.CONTENT_URI, "is_deleted = 1", null);
        // Delete associated project-fields and photos
        int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count2 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count3 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);

        Log.d(TAG, "deleteObservations: " + count1 + ":" + count2 + ":" + count3);

        checkForCancelSync();

        return true;
    }

    private void checkForCancelSync() throws CancelSyncException {
        if (mApp.getCancelSync()) throw new CancelSyncException();
    }


    private JSONObject removeFavorite(int observationId) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        JSONArray result = delete(HOST + "/votes/unvote/observation/" + observationId + ".json", null);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private JSONObject addFavorite(int observationId) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        JSONArray result = post(HOST + "/votes/vote/observation/" + observationId + ".json", (JSONObject) null);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private JSONObject agreeIdentification(int observationId, int taxonId) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("identification[observation_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("identification[taxon_id]", new Integer(taxonId).toString()));

        JSONArray result = post(HOST + "/identifications.json", params);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private JSONObject removeIdentification(int identificationId) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        JSONArray result = delete(HOST + "/identifications/" + identificationId + ".json", null);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private void setUserViewedUpdate(int obsId) throws AuthenticationException {
        put(HOST + "/observations/" + obsId + "/viewed_updates", (JSONObject) null);
    }

    private void restoreIdentification(int identificationId) throws AuthenticationException {
        JSONObject paramsJson = new JSONObject();
        JSONObject paramsJsonIdentification = new JSONObject();
        try {
            paramsJsonIdentification.put("current", true);
            paramsJson.put("identification", paramsJsonIdentification);

            JSONArray arrayResult = put(API_HOST + "/identifications/" + identificationId, paramsJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateIdentification(int observationId, int identificationId, int taxonId, String body) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("identification[observation_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("identification[taxon_id]", new Integer(taxonId).toString()));
        params.add(new BasicNameValuePair("identification[body]", body));

        JSONArray arrayResult = put(HOST + "/identifications/" + identificationId + ".json", params);
    }

    private void addIdentification(int observationId, int taxonId, String body, boolean disagreement, boolean fromVision) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("identification[observation_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("identification[taxon_id]", new Integer(taxonId).toString()));
        if (body != null) params.add(new BasicNameValuePair("identification[body]", body));
        params.add(new BasicNameValuePair("identification[disagreement]", String.valueOf(disagreement)));
        params.add(new BasicNameValuePair("identification[vision]", String.valueOf(fromVision)));

        JSONArray arrayResult = post(HOST + "/identifications.json", params);

        if (arrayResult != null) {
            BetterJSONObject result;
            try {
                result = new BetterJSONObject(arrayResult.getJSONObject(0));
                JSONObject jsonObservation = result.getJSONObject("observation");
                Observation remoteObservation = new Observation(new BetterJSONObject(jsonObservation));

                Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "id = " + remoteObservation.id, null, Observation.DEFAULT_SORT_ORDER);

                // update local observation
                c.moveToFirst();
                if (c.isAfterLast() == false) {
                    Observation observation = new Observation(c);
                    boolean isModified = observation.merge(remoteObservation);
                    ContentValues cv = observation.getContentValues();
                    if (observation._updated_at.before(remoteObservation.updated_at)) {
                        // Remote observation is newer (and thus has overwritten the local one) - update its
                        // sync at time so we won't update the remote servers later on (since we won't
                        // accidently consider this an updated record)
                        cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                    }
                    if (isModified) {
                        // Only update the DB if needed
                        getContentResolver().update(observation.getUri(), cv, null, null);
                    }
                }
                c.close();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    // Updates a user's inat network settings
    private JSONObject updateUserNetwork(int siteId) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("user[site_id]", String.valueOf(siteId)));

        JSONArray array = put(HOST + "/users/" + mLogin + ".json", params);

        if ((mResponseErrors != null) || (array == null)) {
            // Couldn't update user
            return null;
        } else {
            return array.optJSONObject(0);
        }
    }

    // Updates a user's profile
    private JSONObject updateUser(String username, String email, String password, String fullName, String bio, String userPic, boolean deletePic) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("user[login]", username));
        params.add(new BasicNameValuePair("user[name]", fullName));
        params.add(new BasicNameValuePair("user[description]", bio));
        params.add(new BasicNameValuePair("user[email]", email));
        if ((password != null) && (password.length() > 0)) {
            params.add(new BasicNameValuePair("user[password]", password));
            params.add(new BasicNameValuePair("user[password_confirmation]", password));
        }

        if (deletePic) {
            // Delete profile pic
            params.add(new BasicNameValuePair("icon_delete", "true"));
        } else if (userPic != null) {
            // New profile pic
            params.add(new BasicNameValuePair("user[icon]", userPic));
        }

        JSONArray array = put(HOST + "/users/" + mLogin + ".json", params);

        if ((mResponseErrors != null) || (array == null)) {
            // Couldn't update user
            return null;
        } else {
            return array.optJSONObject(0);
        }
    }

    // Registers a user - returns an error message in case of an error (null if successful)
    private String registerUser(String email, String password, String username, String license) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("user[email]", email));
        params.add(new BasicNameValuePair("user[login]", username));
        params.add(new BasicNameValuePair("user[password]", password));
        params.add(new BasicNameValuePair("user[password_confirmation]", password));
        String inatNetwork = mApp.getInaturalistNetworkMember();
        params.add(new BasicNameValuePair("user[site_id]", mApp.getStringResourceByName("inat_site_id_" + inatNetwork)));
        params.add(new BasicNameValuePair("user[preferred_observation_license]", license));
        params.add(new BasicNameValuePair("user[preferred_photo_license]", license));
        params.add(new BasicNameValuePair("user[preferred_sound_license]", license));
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        params.add(new BasicNameValuePair("user[locale]", deviceLanguage));

        post(HOST + "/users.json", params, false);
        if (mResponseErrors != null) {
            // Couldn't create user
            try {
                return mResponseErrors.getString(0);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private void updateComment(int commentId, int observationId, String body) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("comment[parent_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("comment[parent_type]", "Observation"));
        params.add(new BasicNameValuePair("comment[body]", body));

        put(HOST + "/comments/" + commentId + ".json", params);
    }

    private void deleteComment(int commentId) throws AuthenticationException {
        delete(HOST + "/comments/" + commentId + ".json", null);
    }

    private void addComment(int observationId, String body) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("comment[parent_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("comment[parent_type]", "Observation"));
        params.add(new BasicNameValuePair("comment[body]", body));

        post(HOST + "/comments.json", params);
    }

    private boolean postObservation(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        if (observation.id != null) {
            // Update observation
            Log.d(TAG, "postObservation: Updating existing " + observation.id + ":" + observation._id);

            JSONArray response = request(API_HOST + "/observations/" + observation.id, "put", null, observationToJsonObject(observation, false), true, true, false);

            if (response == null) {
                Log.d(TAG, "postObservation: Error for " + observation.id + ":" + observation._id + ":" + mLastStatusCode);
                // Some sort of error
                if ((mLastStatusCode >= 400) && (mLastStatusCode < 500)) {
                    // Observation doesn't exist anymore (deleted remotely, and due to network
                    // issues we didn't get any notification of this) - so delete the observation
                    // locally.
                    Log.d(TAG, "postObservation: Deleting obs " + observation.id + ":" + observation._id);

                    getContentResolver().delete(Observation.CONTENT_URI, "id = " + observation.id, null);
                    // Delete associated project-fields and photos
                    int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id = " + observation.id, null);
                    int count2 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id = " + observation.id, null);
                    int count3 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id = " + observation.id, null);

                    Log.d(TAG, "postObservation: After delete: " + count1 + ":" + count2 + ":" + count3);

                    return true;
                }
            }

            boolean success = handleObservationResponse(observation, response);
            if (!success) {
                throw new SyncFailedException();
            }

            return true;
        }

        // New observation

        String inatNetwork = mApp.getInaturalistNetworkMember();
        JSONObject observationParams = observationToJsonObject(observation, true);
        Log.d(TAG, "postObservation: New obs");
        Log.d(TAG, observationParams.toString());

        boolean success = handleObservationResponse(
                observation,
                request(API_HOST + "/observations", "post", null, observationParams, true, true, false)
        );

        if (!success) {
            throw new SyncFailedException();
        }

        return true;
    }

    private JSONObject getObservationJson(int id, boolean authenticated) throws AuthenticationException {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();

        String url = String.format("%s/observations/%d?locale=%s&include_new_projects=true", API_HOST, id, deviceLanguage);

        JSONArray json = get(url, authenticated);
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            if (results.length() == 0) return null;

            return results.getJSONObject(0);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Observation getAndDownloadObservation(int id) throws AuthenticationException {
        // Download the observation
        JSONObject json = getObservationJson(id, true);
        if (json == null) return null;

        Observation obs = new Observation(new BetterJSONObject(json));

        // Save the downloaded observation
        if (mProjectObservations == null)
            mProjectObservations = new ArrayList<SerializableJSONArray>();
        if (mProjectFieldValues == null)
            mProjectFieldValues = new Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>>();

        JSONArray arr = new JSONArray();
        arr.put(json);
        syncJson(arr, true);

        return obs;
    }

    private boolean postPhotos(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        Integer observationId = observation.id;
        ObservationPhoto op;
        int createdCount = 0;
        ContentValues cv;

        Log.d(TAG, "postPhotos: " + observationId + ":" + observation);

        if (observationId != null) {
            // See if there any photos in an invalid state
            Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NULL AND observation_id = ?",
                    new String[]{String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            c.moveToFirst();
            while (c.isAfterLast() == false) {
                op = new ObservationPhoto(c);
                // Shouldn't happen - a photo with null external ID is marked as sync - unmark it
                op._synced_at = null;
                Log.d(TAG, "postPhotos: Updating with _synced_at = null: " + op);
                getContentResolver().update(op.getUri(), op.getContentValues(), null, null);
                c.moveToNext();
            }
            c.close();

            c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_synced_at IS NULL AND id IS NOT NULL AND observation_id = ?",
                    new String[]{String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            c.moveToFirst();
            while (c.isAfterLast() == false) {
                op = new ObservationPhoto(c);
                // Shouldn't happen - a photo with an external ID is marked as never been synced
                cv = op.getContentValues();
                cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                getContentResolver().update(op.getUri(), cv, null, null);
                c.moveToNext();
            }
            c.close();


            // update photos - for each observation PUT to /observation_photos/:id
            c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL AND observation_id = ? AND ((is_deleted == 0) OR (is_deleted IS NULL))",
                    new String[]{String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            int updatedCount = c.getCount();

            Log.d(TAG, "postPhotos: Updating photos: " + updatedCount);

            c.moveToFirst();
            while (c.isAfterLast() == false) {
                checkForCancelSync();

                op = new ObservationPhoto(c);
                ArrayList<NameValuePair> params = op.getParams();
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
                params.add(new BasicNameValuePair("site_id", mApp.getStringResourceByName("inat_site_id_" + inatNetwork)));

                Log.d(TAG, "postPhotos: Updating " + op + ":" + params);
                JSONArray response = put(inatHost + "/observation_photos/" + op.id + ".json", params);
                try {
                    if (response == null || response.length() != 1) {
                        Log.d(TAG, "postPhotos: Failed updating " + op.id);
                        c.close();
                        throw new SyncFailedException();
                    }
                    increaseProgressForObservation(observation);

                    JSONObject json = response.getJSONObject(0);
                    BetterJSONObject j = new BetterJSONObject(json);
                    ObservationPhoto jsonObservationPhoto = new ObservationPhoto(j);
                    Log.d(TAG, "postPhotos after put: " + j);
                    Log.d(TAG, "postPhotos after put 2: " + jsonObservationPhoto);
                    op.merge(jsonObservationPhoto);
                    Log.d(TAG, "postPhotos after put 3 - merge: " + op);
                    cv = op.getContentValues();
                    Log.d(TAG, "postPhotos: Setting _SYNCED_AT - " + op.id + ":" + op._id + ":" + op._observation_id + ":" + op.observation_id);
                    cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                    getContentResolver().update(op.getUri(), cv, null, null);
                    createdCount += 1;
                } catch (JSONException e) {
                    Log.e(TAG, "JSONException: " + e.toString());
                }

                c.moveToNext();
            }
            c.close();

        }

        // query observation photos where _synced_at is null (i.e. new photos)
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(_synced_at IS NULL) AND (id IS NULL) AND (observation_id = ?) AND ((is_deleted == 0) OR (is_deleted IS NULL))", new String[]{String.valueOf(observation.id)}, ObservationPhoto.DEFAULT_SORT_ORDER);
        Log.d(TAG, "postPhotos: New photos: " + c.getCount());
        if (c.getCount() == 0) {
            c.close();
            return true;
        }

        checkForCancelSync();

        // for each observation POST to /observation_photos
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            op = new ObservationPhoto(c);

            Log.d(TAG, "postPhotos: Posting photo - " + op);

            if (op.photo_url != null) {
                // Online photo
                Log.d(TAG, "postPhotos: Skipping because photo_url is not null");
                c.moveToNext();
                continue;
            }

            ArrayList<NameValuePair> params = op.getParams();

            String imgFilePath = op.photo_filename;
            if (imgFilePath == null) {
                // Observation photo is saved in the "old" way (prior to latest change in the way we store photos)
                Log.d(TAG, "postPhotos: Posting photo - photo_filename is null");
                if (op._photo_id != null) {
                    Uri photoUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, op._photo_id);
                    Cursor pc = getContentResolver().query(photoUri,
                            new String[]{MediaStore.MediaColumns._ID, MediaStore.Images.Media.DATA},
                            null,
                            null,
                            MediaStore.Images.Media.DEFAULT_SORT_ORDER);
                    if (pc != null) {
                        if (pc.getCount() > 0) {
                            pc.moveToFirst();
                            imgFilePath = pc.getString(pc.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                        }
                        pc.close();
                    }
                }
            }
            if ((imgFilePath == null) || !(new File(imgFilePath)).exists()) {
                // Local (cached) photo was deleted - probably because the user deleted the app's cache
                Log.d(TAG, "postPhotos: Posting photo - still problematic photo filename: " + imgFilePath);

                // First, delete this photo record
                getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_id = ?", new String[]{String.valueOf(op._id)});

                // Set errors for this obs - to notify the user that we couldn't upload the obs photos
                JSONArray errors = new JSONArray();
                errors.put(getString(R.string.deleted_photos_from_cache_error));
                mApp.setErrorsForObservation(op.observation_id, 0, errors);

                // Move to next observation photo
                c.moveToNext();
                checkForCancelSync();

                continue;
            }
            params.add(new BasicNameValuePair("file", imgFilePath));

            String inatNetwork = mApp.getInaturalistNetworkMember();
            String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
            params.add(new BasicNameValuePair("site_id", mApp.getStringResourceByName("inat_site_id_" + inatNetwork)));

            JSONArray response;
            Log.d(TAG, "postPhotos: POSTing new photo: " + params);
            response = post(inatHost + "/observation_photos.json", params, true);
            try {
                if (response == null || response.length() != 1) {
                    c.close();
                    throw new SyncFailedException();
                }

                increaseProgressForObservation(observation);

                JSONObject json = response.getJSONObject(0);
                BetterJSONObject j = new BetterJSONObject(json);
                ObservationPhoto jsonObservationPhoto = new ObservationPhoto(j, false);
                Log.d(TAG, "postPhotos: Response for POST: ");
                LoggingUtils.largeLog(TAG, json.toString());
                Log.d(TAG, "postPhotos: Response for POST 2: " + jsonObservationPhoto);
                op.merge(jsonObservationPhoto);
                Log.d(TAG, "postPhotos: Response for POST 3: " + op);

                cv = op.getContentValues();
                Log.d(TAG, "postPhotos - Setting _SYNCED_AT - " + op.id + ":" + op._id + ":" + op._observation_id + ":" + op.observation_id);
                cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                getContentResolver().update(op.getUri(), cv, null, null);
                createdCount += 1;
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.toString());
            }

            c.moveToNext();
            checkForCancelSync();
        }
        c.close();

        c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(_synced_at IS NULL) AND ((_observation_id = ? OR observation_id = ?)) AND ((is_deleted == 0) OR (is_deleted IS NULL))", new String[]{String.valueOf(observation._id), String.valueOf(observation.id)}, ObservationPhoto.DEFAULT_SORT_ORDER);
        int currentCount = c.getCount();
        Log.d(TAG, "postPhotos: currentCount = " + currentCount);
        c.close();

        if (currentCount == 0) {
            // Sync completed successfully
            return true;
        } else {
            // Sync failed
            throw new SyncFailedException();
        }
    }


    // Warms the images cache by pre-loading a remote image
    private void warmUpImageCache(final String url) {
        Handler handler = new Handler(Looper.getMainLooper());

        // Need to run on main thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                Picasso.with(INaturalistService.this).load(url).into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        // cache is now warmed up
                    }

                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) {
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                    }
                });
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // Goes over cached photos that were uploaded and that are old enough and deletes them
    // to clear out storage space (they're replaced with their online version, so it'll be
    // accessible by the user).
    private void clearOldCachedPhotos() {
        if (!isNetworkAvailable()) return;

        long cacheTime = System.currentTimeMillis() - (OLD_PHOTOS_CACHE_EXPIRATION_HOURS * 60 * 60 * 1000);
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION,
                "_updated_at = _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL AND " +
                        "_updated_at < ? AND " +
                        "photo_filename IS NOT NULL AND observation_id IS NOT NULL",
                new String[]{String.valueOf(cacheTime)}, ObservationPhoto.DEFAULT_SORT_ORDER);

        while (!c.isAfterLast()) {
            ObservationPhoto op = new ObservationPhoto(c);
            File obsPhotoFile = new File(op.photo_filename);

            if (op.photo_url == null) {
                // No photo URL defined - download the observation and get the external URL for that photo
                boolean foundPhoto = false;
                try {
                    JSONObject json = getObservationJson(op.observation_id, false);

                    if (json != null) {
                        Observation obs = new Observation(new BetterJSONObject(json));
                        for (int i = 0; i < obs.photos.size(); i++) {
                            if ((obs.photos.get(0).id != null) && (op.id != null)) {
                                if (obs.photos.get(0).id.equals(op.id)) {
                                    // Found the appropriate photo - update the URL
                                    op.photo_url = obs.photos.get(i).photo_url;
                                    foundPhoto = true;
                                    break;
                                }
                            }
                        }
                    }

                } catch (AuthenticationException e) {
                    e.printStackTrace();
                }


                if (!foundPhoto) {
                    // Couldn't download remote URL for the observation photo - don't delete it
                    c.moveToNext();
                    continue;
                }
            }

            if (obsPhotoFile.exists()) {
                // Delete the local cached photo file
                obsPhotoFile.delete();
            }

            // Update the obs photo record with the remote photo URL
            Log.d(TAG, "OP - clearOldCachedPhotos - Setting _SYNCED_AT - " + op.id + ":" + op._id + ":" + op._observation_id + ":" + op.observation_id);
            op.photo_filename = null;
            ContentValues cv = op.getContentValues();
            cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(op.getUri(), cv, null, null);

            // Warm up the cache for the image
            warmUpImageCache(op.photo_url);

            c.moveToNext();
        }

        c.close();
    }

    private String getGuideXML(Integer guideId) throws AuthenticationException {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();

        String url = HOST + "/guides/" + guideId.toString() + ".xml?locale=" + deviceLanguage;

        try {
            HttpClient httpClient = new DefaultHttpClient();
            httpClient.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
            HttpGet httpGet = new HttpGet(url);
            HttpResponse response = null;
            response = httpClient.execute(httpGet);

            InputStream buffer = new BufferedInputStream(response.getEntity().getContent());
            File outputFile = File.createTempFile(guideId.toString() + ".xml", null, getBaseContext().getCacheDir());
            OutputStream output = new FileOutputStream(outputFile);

            int count = 0;
            byte data[] = new byte[1024];
            while ((count = buffer.read(data)) != -1) {
                output.write(data, 0, count);
            }

            // flushing output
            output.flush();

            // closing streams
            output.close();
            buffer.close();

            // Return the downloaded full file name
            return outputFile.getAbsolutePath();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }


    private BetterJSONObject getCurrentUserDetails() throws AuthenticationException {
        String url = API_HOST + "/users/me";
        JSONArray json = get(url, true);
        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            JSONObject user = json.getJSONObject(0).getJSONArray("results").getJSONObject(0);
            return new BetterJSONObject(user);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BetterJSONObject updateCurrentUserDetails(JSONObject params) throws AuthenticationException {
        JSONObject input = new JSONObject();
        try {
            input.put("user", params);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JSONArray json = request(API_HOST + "/users/" + mApp.currentUserLogin(), "put", null, input, true, true, false);

        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    private BetterJSONObject getUserDetails(String username) throws AuthenticationException {
        String url = HOST + "/users/" + username + ".json";
        JSONArray json = get(url, false);
        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private SerializableJSONArray searchUserObservation(String query) throws AuthenticationException {
        String url = null;

        try {
            StringBuilder sb = new StringBuilder(INaturalistService.HOST + "/observations/" + mLogin + ".json");
            sb.append("?per_page=100");
            sb.append("&q=");
            sb.append(URLEncoder.encode(query, "utf8"));

            sb.append("&extra=observation_photos,projects,fields");

            Locale deviceLocale = getResources().getConfiguration().locale;
            String deviceLexicon = deviceLocale.getLanguage();
            sb.append("&locale=");
            sb.append(deviceLexicon);

            url = sb.toString();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        JSONArray json = get(url, true);
        if (json == null) return null;
        if (json.length() == 0) return null;

        return new SerializableJSONArray(json);
    }

    private BetterJSONObject searchAutoComplete(String type, String query, int page) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/" + type + "/autocomplete?geo=true&locale=" + deviceLanguage + "&per_page=50&page=" + page + "&q=" + Uri.encode(query);
        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    private JSONObject getUserObservations(String username) throws AuthenticationException {
        String url = API_HOST + "/observations?per_page=30&user_id=" + username;
        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return json.getJSONObject(0);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private SerializableJSONArray getUserUpdates(boolean following) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/observations/updates?locale=" + deviceLanguage + "&per_page=200&observations_by=" +
                (following ? "following" : "owner");
        JSONArray json = request(url, "get", null, null, true, true, false); // Use JWT Token authentication
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            JSONObject resObject = json.getJSONObject(0);
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            return new SerializableJSONArray(results);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    private BetterJSONObject getUserIdentifications(String username) throws AuthenticationException {
        String url = API_HOST + "/identifications?user_id=" + username + "&own_observation=false&per_page=30";
        JSONArray json = get(url, false);

        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BetterJSONObject getExploreResults(String command, ExploreSearchFilters filters, int pageNumber, int pageSize, String orderBy) throws AuthenticationException {
        if (filters == null) return null;

        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = String.format("%s/observations%s?locale=%s&page=%d&per_page=%d&ordered_by=%s&order=desc&return_bounds=true&%s",
                API_HOST,
                command == null ? "" : "/" + command,
                deviceLanguage,
                pageNumber,
                pageSize,
                orderBy == null ? "" : orderBy,
                filters.toUrlQueryString());

        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    private BetterJSONObject getUserSpeciesCount(String username) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/observations/species_counts?place_id=any&verifiable=any&per_page=30&user_id=" + username + "&locale=" + deviceLanguage;
        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BetterJSONObject getUserLifeList(int lifeListId) throws AuthenticationException {
        String url = HOST + "/life_lists/" + lifeListId + ".json";
        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BetterJSONObject getUserDetails() throws AuthenticationException {
        String url = HOST + "/users/edit.json";
        JSONArray json = get(url, true);
        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    private BetterJSONObject getProjectObservations(int projectId) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/observations?project_id=" + projectId + "&per_page=50&locale=" + deviceLanguage;
        JSONArray json = get(url);
        if (json == null) return new BetterJSONObject();
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BetterJSONObject();
        }
    }

    private BetterJSONObject getTaxonObservationsBounds(Integer taxonId) {
        String url = API_HOST + "/observations?per_page=1&return_bounds=true&taxon_id=" + taxonId;

        JSONArray json = null;
        try {
            json = get(url, false);
        } catch (AuthenticationException e) {
            e.printStackTrace();
            return null;
        }
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            JSONObject response = json.getJSONObject(0);
            return new BetterJSONObject(response.getJSONObject("total_bounds"));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BetterJSONObject getMissions(Location location, String username, Integer taxonId, float expandLocationByDegress) {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/observations/species_counts?locale=" + deviceLanguage +
                "&verifiable=true&hrank=species&oauth_application_id=2,3";

        if (expandLocationByDegress == 0) {
            url += "&lat=" + location.getLatitude() + "&lng=" + location.getLongitude();
        } else {
            // Search for taxa in a bounding box expanded by a certain number of degrees (used to expand
            // our search in case we can't find any close taxa)
            url += String.format("&nelat=%f&nelng=%f&swlat=%f&swlng=%f",
                    location.getLatitude() + expandLocationByDegress,
                    location.getLongitude() + expandLocationByDegress,
                    location.getLatitude() - expandLocationByDegress,
                    location.getLongitude() - expandLocationByDegress);
        }

        if (username != null) {
            // Taxa unobserved by a specific user
            url += "&unobserved_by_user_id=" + username;
        }
        if (taxonId != null) {
            // Taxa under a specific category (e.g. fungi)
            url += "&taxon_id=" + taxonId;
        }

        // Make sure to show only taxa observable for the current months (+/- 1 month from current one)
        Calendar c = Calendar.getInstance();
        int month = c.get(Calendar.MONTH);
        url += String.format("&month=%d,%d,%d", modulo(month - 1, 12) + 1, month + 1, modulo(month + 1, 12) + 1);

        JSONArray json = null;
        try {
            json = get(url, false);
        } catch (AuthenticationException e) {
            e.printStackTrace();
            return null;
        }
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private BetterJSONObject getProjectSpecies(int projectId) throws AuthenticationException {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        String url = API_HOST + "/observations/species_counts?project_id=" + projectId + "&locale=" + deviceLanguage;
        JSONArray json = get(url);
        try {
            if (json == null) return new BetterJSONObject();
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BetterJSONObject();
        }
    }

    private SerializableJSONArray getNews() throws AuthenticationException {
        String url = HOST + "/posts/for_user.json";
        JSONArray json = get(url, mCredentials != null); // If user is logged-in, returns his news (using an authenticated endpoint)
        return new SerializableJSONArray(json);
    }


    private SerializableJSONArray getProjectNews(int projectId) throws AuthenticationException {
        String url = HOST + "/projects/" + projectId + "/journal.json";
        JSONArray json = get(url);
        return new SerializableJSONArray(json);
    }


    private BetterJSONObject getProjectObservers(int projectId) throws AuthenticationException {
        String url = API_HOST + "/observations/observers?project_id=" + projectId;
        JSONArray json = get(url);
        try {
            if (json == null) return new BetterJSONObject();
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BetterJSONObject();
        }
    }

    private BetterJSONObject getProjectIdentifiers(int projectId) throws AuthenticationException {
        String url = API_HOST + "/observations/identifiers?project_id=" + projectId;
        JSONArray json = get(url);
        try {
            if (json == null) return new BetterJSONObject();
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BetterJSONObject();
        }
    }

    private SerializableJSONArray getTaxaForGuide(Integer guideId) throws AuthenticationException {
        String url = HOST + "/guide_taxa.json?guide_id=" + guideId.toString();
        JSONArray json = get(url);
        try {
            if (json == null) return new SerializableJSONArray();
            return new SerializableJSONArray(json.getJSONObject(0).getJSONArray("guide_taxa"));
        } catch (JSONException e) {
            e.printStackTrace();
            return new SerializableJSONArray();
        }
    }


    private SerializableJSONArray getAllGuides() throws AuthenticationException {
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

        // Just retrieve the first page results (if the user wants to find more guides,
        // he can search for guides)
        String url = inatHost + "/guides.json?per_page=200&page=1";
        JSONArray results = get(url);

        return new SerializableJSONArray(results);
    }

    private SerializableJSONArray getMyGuides() throws AuthenticationException {
        JSONArray json = null;
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
        String url = inatHost + "/guides.json?by=you&per_page=200";

        if (mCredentials != null) {
            try {
                json = get(url, true);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }

        if (json == null) {
            json = new JSONArray();
        }

        // Build a list of result guide IDs
        int i = 0;
        List<String> guideIds = new ArrayList<String>();
        while (i < json.length()) {
            try {
                JSONObject guide = json.getJSONObject(i);
                guideIds.add(String.valueOf(guide.getInt("id")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            i++;
        }

        // Add any offline guides
        List<GuideXML> offlineGuides = GuideXML.getAllOfflineGuides(this);
        List<JSONObject> guidesJson = new ArrayList<JSONObject>();

        for (GuideXML guide : offlineGuides) {
            JSONObject guideJson = new JSONObject();
            if (guideIds.contains(guide.getID())) {
                // Guide already found in current guide results - no need to add it again
                continue;
            }

            try {
                guideJson.put("id", Integer.valueOf(guide.getID()));
                guideJson.put("title", guide.getTitle());
                guideJson.put("description", guide.getDescription());
                // TODO - no support for "icon_url" (not found in XML file)
            } catch (JSONException e) {
                e.printStackTrace();
            }

            json.put(guideJson);
        }

        return new SerializableJSONArray(json);
    }

    private SerializableJSONArray getNearByGuides(Location location) throws AuthenticationException {
        if (location == null) {
            // No place found - return an empty result
            Log.e(TAG, "Current place is null");
            return new SerializableJSONArray();
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

        String url = inatHost + String.format("/guides.json?latitude=%s&longitude=%s&per_page=200", lat, lon);
        Log.d(TAG, url);

        JSONArray json = get(url);

        return new SerializableJSONArray(json);
    }


    private SerializableJSONArray getNearByProjects(Location location) throws AuthenticationException {
        if (location == null) {
            // No place found - return an empty result
            Log.e(TAG, "Current place is null");
            return new SerializableJSONArray();
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

        String url = inatHost + String.format("/projects.json?latitude=%s&longitude=%s", lat, lon);

        Log.e(TAG, url);

        JSONArray json = get(url);

        if (json == null) {
            return new SerializableJSONArray();
        }

        // Determine which projects are already joined
        for (int i = 0; i < json.length(); i++) {
            Cursor c;
            try {
                c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '" + json.getJSONObject(i).getInt("id") + "'", null, Project.DEFAULT_SORT_ORDER);
                c.moveToFirst();
                int count = c.getCount();
                c.close();
                if (count > 0) {
                    json.getJSONObject(i).put("joined", true);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }

        }

        return new SerializableJSONArray(json);
    }

    private SerializableJSONArray getFeaturedProjects() throws AuthenticationException {
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String siteId = mApp.getStringResourceByName("inat_site_id_" + inatNetwork);
        String url = API_HOST + "/projects?featured=true&site_id=" + siteId;

        JSONArray json = get(url);

        if (json == null) {
            return new SerializableJSONArray();
        }

        JSONArray results;

        try {
            results = json.getJSONObject(0).getJSONArray("results");
        } catch (JSONException e) {
            e.printStackTrace();
            return new SerializableJSONArray();
        }

        // Determine which projects are already joined
        for (int i = 0; i < results.length(); i++) {
            Cursor c;
            try {
                c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '" + results.getJSONObject(i).getInt("id") + "'", null, Project.DEFAULT_SORT_ORDER);
                c.moveToFirst();
                int count = c.getCount();
                c.close();
                if (count > 0) {
                    results.getJSONObject(i).put("joined", true);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }

        }

        return new SerializableJSONArray(results);
    }

    private void addProjectFields(JSONArray jsonFields) {
        int projectId = -1;
        ArrayList<ProjectField> projectFields = new ArrayList<ProjectField>();

        for (int i = 0; i < jsonFields.length(); i++) {
            try {
                BetterJSONObject jsonField = new BetterJSONObject(jsonFields.getJSONObject(i));
                ProjectField field = new ProjectField(jsonField);
                projectId = field.project_id;
                projectFields.add(field);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (projectId != -1) {
            // First, delete all previous project fields (for that project)
            getContentResolver().delete(ProjectField.CONTENT_URI, "(project_id IS NOT NULL) and (project_id = " + projectId + ")", null);

            // Next, re-add all project fields
            for (int i = 0; i < projectFields.size(); i++) {
                ProjectField field = projectFields.get(i);
                getContentResolver().insert(ProjectField.CONTENT_URI, field.getContentValues());
            }
        }
    }


    public void flagObservationAsCaptive(int obsId) throws AuthenticationException {
        post(String.format("%s/observations/%d/quality/wild.json?agree=false", HOST, obsId), (JSONObject) null);
    }

    public void joinProject(int projectId) throws AuthenticationException {
        post(String.format("%s/projects/%d/join.json", HOST, projectId), (JSONObject) null);

        try {
            JSONArray result = get(String.format("%s/projects/%d.json", HOST, projectId));
            if (result == null) return;
            BetterJSONObject jsonProject = new BetterJSONObject(result.getJSONObject(0));
            Project project = new Project(jsonProject);

            // Add joined project locally
            ContentValues cv = project.getContentValues();
            getContentResolver().insert(Project.CONTENT_URI, cv);

            // Save project fields
            addProjectFields(jsonProject.getJSONArray("project_observation_fields").getJSONArray());

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void leaveProject(int projectId) throws AuthenticationException {
        delete(String.format("%s/projects/%d/leave.json", HOST, projectId), null);

        // Remove locally saved project (because we left it)
        getContentResolver().delete(Project.CONTENT_URI, "(id IS NOT NULL) and (id = " + projectId + ")", null);
    }


    private BetterJSONObject removeObservationFromProject(int observationId, int projectId) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }

        String url = String.format("%s/projects/%d/remove.json?observation_id=%d", HOST, projectId, observationId);
        JSONArray json = request(url, "delete", null, null, true, true, false);

        if (json == null) return null;

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BetterJSONObject();
        }
    }


    private BetterJSONObject addObservationToProject(int observationId, int projectId) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }

        String url = HOST + "/project_observations.json";

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("project_observation[observation_id]", String.valueOf(observationId)));
        params.add(new BasicNameValuePair("project_observation[project_id]", String.valueOf(projectId)));
        JSONArray json = post(url, params);

        if (json == null) {
            return null;
        }

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }


    private SerializableJSONArray getCheckList(int id) throws AuthenticationException {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();

        String url = String.format("%s/lists/%d.json?per_page=50&locale=%s", HOST, id, deviceLanguage);

        JSONArray json = get(url);

        if (json == null) {
            return null;
        }

        try {
            return new SerializableJSONArray(json.getJSONObject(0).getJSONArray("listed_taxa"));
        } catch (JSONException e) {
            e.printStackTrace();
            return new SerializableJSONArray();
        }
    }

    public static boolean hasJoinedProject(Context context, int projectId) {
        Cursor c = context.getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = " + projectId, null, Project.DEFAULT_SORT_ORDER);
        int count = c.getCount();
        c.close();

        return count > 0;
    }


    private SerializableJSONArray getJoinedProjectsOffline() {
        JSONArray projects = new JSONArray();

        Cursor c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, null, null, Project.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        int index = 0;

        while (c.isAfterLast() == false) {
            Project project = new Project(c);
            JSONObject jsonProject = project.toJSONObject();
            try {
                jsonProject.put("joined", true);
                projects.put(index, jsonProject);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            c.moveToNext();
            index++;
        }
        c.close();

        return new SerializableJSONArray(projects);
    }

    private SerializableJSONArray getJoinedProjects() throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }
        String url = HOST + "/projects/user/" + Uri.encode(mLogin) + ".json";

        JSONArray json = get(url, true);
        JSONArray finalJson = new JSONArray();

        if (json == null) {
            return null;
        }

        for (int i = 0; i < json.length(); i++) {
            try {
                JSONObject obj = json.getJSONObject(i);
                JSONObject project = obj.getJSONObject("project");
                project.put("joined", true);
                finalJson.put(project);

                // Save project fields
                addProjectFields(project.getJSONArray("project_observation_fields"));

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return new SerializableJSONArray(finalJson);
    }


    @SuppressLint("NewApi")
    private int getAdditionalUserObservations(int maxCount) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return -1;
        }


        Integer lastId = mPreferences.getInt("last_downloaded_id", 0);

        if (lastId == 0) {
            Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "(is_deleted = 0 OR is_deleted is NULL) AND (user_login = '" + mLogin + "')",
                    null,
                    Observation.DEFAULT_SORT_ORDER);

            if (c.getCount() > 0) {
                c.moveToLast();
                BetterCursor bc = new BetterCursor(c);
                lastId = bc.getInteger(Observation.ID);
            } else {
                lastId = Integer.MAX_VALUE;
            }
            c.close();
        }


        String url = API_HOST + "/observations?user_id=" + Uri.encode(mLogin) + "&per_page=" + maxCount + "&id_below=" + lastId;

        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        url += "&locale=" + deviceLanguage;

        mProjectObservations = new ArrayList<SerializableJSONArray>();
        mProjectFieldValues = new Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>>();

        JSONArray json = get(url, true);
        if (json != null && json.length() > 0) {
            try {
                JSONArray results = json.getJSONObject(0).getJSONArray("results");
                JSONArray newResults = new JSONArray();
                int minId = Integer.MAX_VALUE;

                // Remove any observations that were previously downloaded
                for (int i = 0; i < results.length(); i++) {
                    int currentId = results.getJSONObject(i).getInt("id");
                    Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                            Observation.PROJECTION,
                            "_id = ?",
                            new String[]{String.valueOf(currentId)},
                            Observation.DEFAULT_SORT_ORDER);

                    if (currentId < minId) {
                        minId = currentId;
                    }

                    if (c.getCount() == 0) {
                        newResults.put(results.getJSONObject(i));
                    }

                    c.close();
                }
                syncJson(newResults, true);

                if (results.length() == 0) {
                    mPreferences.edit().putInt("last_downloaded_id", mPreferences.getInt("last_downloaded_id", 0)).commit();
                } else {
                    mPreferences.edit().putInt("last_downloaded_id", minId).commit();
                }

                return results.length();
            } catch (JSONException e) {
                e.printStackTrace();
                return -1;
            }
        } else {
            return -1;
        }
    }


    @SuppressLint("NewApi")
    private boolean syncRemotelyDeletedObs() throws AuthenticationException, CancelSyncException {
        if (ensureCredentials() == false) {
            return false;
        }
        String url = API_HOST + "/observations/deleted";

        long lastSync = mPreferences.getLong("last_sync_time", 0);

        if (lastSync == 0) {
            Log.d(TAG, "syncRemotelyDeletedObs: First time syncing, no need to delete observations");
            return true;
        }

        Timestamp lastSyncTS = new Timestamp(lastSync);
        //url += String.format("?since=%s", URLEncoder.encode(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(lastSyncTS)));
        url += String.format("?since=%s", URLEncoder.encode(new SimpleDateFormat("yyyy-MM-dd").format(lastSyncTS)));

        JSONArray json = get(url, true);
        if (json != null && json.length() > 0) {
            // Delete any local observations which were deleted remotely by the user
            JSONArray results = null;
            try {
                results = json.getJSONObject(0).getJSONArray("results");
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }

            if (results.length() == 0) return true;

            ArrayList<String> ids = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                ids.add(String.valueOf(results.optInt(i)));
            }
            String deletedIds = StringUtils.join(ids, ",");

            Log.d(TAG, "syncRemotelyDeletedObs: " + deletedIds);

            getContentResolver().delete(Observation.CONTENT_URI, "(id IN (" + deletedIds + "))", null);
            // Delete associated project-fields and photos
            int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
            int count2 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
            int count3 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
        }

        checkForCancelSync();

        return (json != null);
    }

    @SuppressLint("NewApi")
    private boolean getUserObservations(int maxCount) throws AuthenticationException, CancelSyncException {
        if (ensureCredentials() == false) {
            return false;
        }
        String url = API_HOST + "/observations?user_id=" + mLogin;

        long lastSync = mPreferences.getLong("last_sync_time", 0);
        Timestamp lastSyncTS = new Timestamp(lastSync);
        url += String.format("&updated_since=%s&order_by=created_at&order=desc&extra=observation_photos,projects,fields", URLEncoder.encode(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(lastSyncTS)));

        if (maxCount == 0) {
            maxCount = 200;
        }

        if (maxCount > 0) {
            // Retrieve only a certain number of observations
            url += String.format("&per_page=%d&page=1", maxCount);
        }

        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        url += "&locale=" + deviceLanguage;

        mProjectObservations = new ArrayList<SerializableJSONArray>();
        mProjectFieldValues = new Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>>();

        JSONArray json = get(url, true);
        if (json != null && json.length() > 0) {
            Log.d(TAG, "getUserObservations");
            JSONArray results = null;
            try {
                results = json.getJSONObject(0).getJSONArray("results");
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
            syncJson(results, true);
            return true;
        }

        checkForCancelSync();

        return (json != null);
    }

    private boolean syncObservationFields(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        if ((observation.id == null) || (mProjectFieldValues == null)) {
            // Observation hasn't been synced yet - no way to sync its project fields
            return true;
        }

        // First, remotely update the observation fields which were modified
        Cursor c = getContentResolver().query(ProjectFieldValue.CONTENT_URI,
                ProjectFieldValue.PROJECTION,
                "_updated_at > _synced_at AND _synced_at IS NOT NULL AND observation_id = ?",
                new String[]{String.valueOf(observation.id)},
                ProjectFieldValue.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        if ((c.getCount() == 0) && (mProjectFieldValues.size() == 0)) {
            c.close();
            return true;
        }

        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectFieldValue localField = new ProjectFieldValue(c);

            increaseProgressForObservation(observation);

            if (!mProjectFieldValues.containsKey(Integer.valueOf(localField.observation_id))) {
                // Need to retrieve remote observation fields to see how to sync the fields
                JSONArray jsonResult = get(HOST + "/observations/" + localField.observation_id + ".json");

                if (jsonResult != null) {
                    Hashtable<Integer, ProjectFieldValue> fields = new Hashtable<Integer, ProjectFieldValue>();

                    try {
                        JSONArray jsonFields = jsonResult.getJSONObject(0).getJSONArray("observation_field_values");

                        for (int j = 0; j < jsonFields.length(); j++) {
                            JSONObject jsonField = jsonFields.getJSONObject(j);
                            JSONObject observationField = jsonField.getJSONObject("observation_field");
                            int id = observationField.optInt("id", jsonField.getInt("observation_field_id"));
                            fields.put(id, new ProjectFieldValue(new BetterJSONObject(jsonField)));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    mProjectFieldValues.put(localField.observation_id, fields);

                    checkForCancelSync();
                } else {
                    c.close();
                    throw new SyncFailedException();
                }
            }

            Hashtable<Integer, ProjectFieldValue> fields = mProjectFieldValues.get(Integer.valueOf(localField.observation_id));

            boolean shouldOverwriteRemote = false;
            ProjectFieldValue remoteField = null;

            if (fields == null) {
                c.moveToNext();
                continue;
            }

            if (!fields.containsKey(Integer.valueOf(localField.field_id))) {
                // No remote field - add it
                shouldOverwriteRemote = true;
            } else {
                remoteField = fields.get(Integer.valueOf(localField.field_id));

                if ((remoteField.updated_at != null) && (remoteField.updated_at.before(localField._updated_at))) {
                    shouldOverwriteRemote = true;
                }
            }

            if (shouldOverwriteRemote) {
                // Overwrite remote value
                ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("observation_field_value[observation_id]", Integer.valueOf(localField.observation_id).toString()));
                params.add(new BasicNameValuePair("observation_field_value[observation_field_id]", Integer.valueOf(localField.field_id).toString()));
                params.add(new BasicNameValuePair("observation_field_value[value]", localField.value));
                JSONArray result = post(HOST + "/observation_field_values.json", params);

                if (result == null) {
                    if (mResponseErrors == null) {
                        c.close();
                        throw new SyncFailedException();
                    } else {
                        Cursor c2 = getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                                "field_id = " + localField.field_id, null, Project.DEFAULT_SORT_ORDER);
                        c2.moveToFirst();
                        if (c2.getCount() > 0) {
                            ProjectField projectField = new ProjectField(c2);
                            handleProjectFieldErrors(localField.observation_id, projectField.project_id);
                        }
                        c2.close();
                        c.moveToNext();
                        checkForCancelSync();
                        continue;
                    }
                }

            } else {
                // Overwrite local value
                localField.created_at = remoteField.created_at;
                localField.id = remoteField.id;
                localField.observation_id = remoteField.observation_id;
                localField.field_id = remoteField.field_id;
                localField.value = remoteField.value;
                localField.updated_at = remoteField.updated_at;
            }

            ContentValues cv = localField.getContentValues();
            cv.put(ProjectFieldValue._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(localField.getUri(), cv, null, null);

            fields.remove(Integer.valueOf(localField.field_id));

            c.moveToNext();
            checkForCancelSync();
        }
        c.close();

        // Next, add any new observation fields
        Hashtable<Integer, ProjectFieldValue> fields = mProjectFieldValues.get(Integer.valueOf(observation.id));

        if (fields == null) {
            return true;
        }

        for (ProjectFieldValue field : fields.values()) {
            ContentValues cv = field.getContentValues();
            cv.put(ProjectFieldValue._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().insert(ProjectFieldValue.CONTENT_URI, cv);

            c = getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                    "field_id = " + field.field_id, null, Project.DEFAULT_SORT_ORDER);
            if (c.getCount() == 0) {
                // This observation has a non-project custom field - add it as well
                boolean success = addProjectField(field.field_id);
                if (!success) {
                    c.close();
                    throw new SyncFailedException();
                }
            }
            c.close();

        }

        return true;
    }

    private boolean syncObservationFields() throws AuthenticationException, CancelSyncException, SyncFailedException {

        // First, remotely update the observation fields which were modified

        Cursor c = getContentResolver().query(ProjectFieldValue.CONTENT_URI,
                ProjectFieldValue.PROJECTION,
                "_updated_at > _synced_at AND _synced_at IS NOT NULL",
                null,
                ProjectFieldValue.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        if ((c.getCount() > 0) || (mProjectFieldValues.size() > 0)) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    getString(R.string.projects),
                    getString(R.string.syncing_observation_fields),
                    getString(R.string.syncing));
        } else {
            c.close();
            return true;
        }

        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectFieldValue localField = new ProjectFieldValue(c);

            // Make sure that the local field has an *external* observation id (i.e. the observation
            // it belongs to has been synced)
            Cursor obsc = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "id = ? AND _synced_at IS NOT NULL AND _id != ?",
                    new String[]{localField.observation_id.toString(), localField.observation_id.toString()},
                    ProjectFieldValue.DEFAULT_SORT_ORDER);
            int count = obsc.getCount();
            obsc.close();
            if (count == 0) {
                c.moveToNext();
                continue;
            }


            mApp.setObservationIdBeingSynced(localField.observation_id);

            if (!mProjectFieldValues.containsKey(Integer.valueOf(localField.observation_id))) {
                // Need to retrieve remote observation fields to see how to sync the fields
                JSONArray jsonResult = get(HOST + "/observations/" + localField.observation_id + ".json");

                if (jsonResult != null) {
                    Hashtable<Integer, ProjectFieldValue> fields = new Hashtable<Integer, ProjectFieldValue>();

                    try {
                        JSONArray jsonFields = jsonResult.getJSONObject(0).getJSONArray("observation_field_values");

                        for (int j = 0; j < jsonFields.length(); j++) {
                            JSONObject jsonField = jsonFields.getJSONObject(j);
                            JSONObject observationField = jsonField.getJSONObject("observation_field");
                            int id = observationField.optInt("id", jsonField.getInt("observation_field_id"));
                            fields.put(id, new ProjectFieldValue(new BetterJSONObject(jsonField)));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    mProjectFieldValues.put(localField.observation_id, fields);

                    checkForCancelSync();
                } else {
                    mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);
                    c.close();
                    throw new SyncFailedException();
                }
            }

            Hashtable<Integer, ProjectFieldValue> fields = mProjectFieldValues.get(Integer.valueOf(localField.observation_id));

            boolean shouldOverwriteRemote = false;
            ProjectFieldValue remoteField = null;

            if (fields == null) {
                c.moveToNext();
                continue;
            }

            if (!fields.containsKey(Integer.valueOf(localField.field_id))) {
                // No remote field - add it
                shouldOverwriteRemote = true;
            } else {
                remoteField = fields.get(Integer.valueOf(localField.field_id));

                if (remoteField.updated_at.before(localField._updated_at)) {
                    shouldOverwriteRemote = true;
                }
            }

            if (shouldOverwriteRemote) {
                // Overwrite remote value
                ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("observation_field_value[observation_id]", Integer.valueOf(localField.observation_id).toString()));
                params.add(new BasicNameValuePair("observation_field_value[observation_field_id]", Integer.valueOf(localField.field_id).toString()));
                params.add(new BasicNameValuePair("observation_field_value[value]", localField.value));
                JSONArray result = post(HOST + "/observation_field_values.json", params);

                if (result == null) {
                    if (mResponseErrors == null) {
                        c.close();
                        throw new SyncFailedException();
                    } else {
                        Cursor c2 = getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                                "field_id = " + localField.field_id, null, Project.DEFAULT_SORT_ORDER);
                        c2.moveToFirst();
                        if (c2.getCount() > 0) {
                            ProjectField projectField = new ProjectField(c2);
                            handleProjectFieldErrors(localField.observation_id, projectField.project_id);
                        }
                        c2.close();
                        c.moveToNext();
                        checkForCancelSync();
                        continue;
                    }
                }

            } else {
                // Overwrite local value
                localField.created_at = remoteField.created_at;
                localField.id = remoteField.id;
                localField.observation_id = remoteField.observation_id;
                localField.field_id = remoteField.field_id;
                localField.value = remoteField.value;
                localField.updated_at = remoteField.updated_at;
            }

            ContentValues cv = localField.getContentValues();
            cv.put(ProjectFieldValue._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(localField.getUri(), cv, null, null);

            fields.remove(Integer.valueOf(localField.field_id));

            c.moveToNext();
            checkForCancelSync();
        }
        c.close();

        mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

        // Next, add any new observation fields
        for (Hashtable<Integer, ProjectFieldValue> fields : mProjectFieldValues.values()) {
            for (ProjectFieldValue field : fields.values()) {
                ContentValues cv = field.getContentValues();
                cv.put(ProjectFieldValue._SYNCED_AT, System.currentTimeMillis());
                getContentResolver().insert(ProjectFieldValue.CONTENT_URI, cv);

                c = getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                        "field_id = " + field.field_id, null, Project.DEFAULT_SORT_ORDER);
                if (c.getCount() == 0) {
                    // This observation has a non-project custom field - add it as well
                    boolean success = addProjectField(field.field_id);
                    if (!success) {
                        c.close();
                        throw new SyncFailedException();
                    }
                }
                c.close();

            }
        }

        return true;
    }

    private boolean addProjectField(int fieldId) throws AuthenticationException {
        try {
            JSONArray result = get(String.format("%s/observation_fields/%d.json", HOST, fieldId));
            if (result == null) return false;

            BetterJSONObject jsonObj;
            jsonObj = new BetterJSONObject(result.getJSONObject(0));
            ProjectField field = new ProjectField(jsonObj);

            getContentResolver().insert(ProjectField.CONTENT_URI, field.getContentValues());

            return true;

        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void getNearbyObservations(Intent intent) throws AuthenticationException {
        Bundle extras = intent.getExtras();
        Double minx = extras.getDouble("minx");
        Double maxx = extras.getDouble("maxx");
        Double miny = extras.getDouble("miny");
        Double maxy = extras.getDouble("maxy");
        Double lat = extras.getDouble("lat");
        Double lng = extras.getDouble("lng");
        Boolean clearMapLimit = extras.getBoolean("clear_map_limit", false);
        Integer page = extras.getInt("page", 0);
        Integer perPage = extras.getInt("per_page", NEAR_BY_OBSERVATIONS_PER_PAGE);

        String url = HOST;
        if (extras.containsKey("username")) {
            url = HOST + "/observations/" + extras.getString("username") + ".json?extra=observation_photos";
        } else {
            url = HOST + "/observations.json?extra=observation_photos";
        }

        url += "&captive=false&page=" + page + "&per_page=" + perPage;

        if (extras.containsKey("taxon_id")) {
            url += "&taxon_id=" + extras.getInt("taxon_id");
        }
        if (extras.containsKey("location_id")) {
            url += "&place_id=" + extras.getInt("location_id");
        } else if (!clearMapLimit) {
            if ((lat != null) && (lng != null) && !((lat == 0) && (lng == 0))) {
                url += "&lat=" + lat;
                url += "&lng=" + lng;
            } else {
                url += "&swlat=" + miny;
                url += "&nelat=" + maxy;
                url += "&swlng=" + minx;
                url += "&nelng=" + maxx;
            }
        }

        if (extras.containsKey("project_id")) {
            url += "&projects[]=" + extras.getInt("project_id");
        }

        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        url += "&locale=" + deviceLanguage;


        Log.d(TAG, "Near by observations URL: " + url);

        mNearByObservationsUrl = url;

        JSONArray json = get(url, mApp.loggedIn());
        Intent reply = new Intent(ACTION_NEARBY);
        reply.putExtra("minx", minx);
        reply.putExtra("maxx", maxx);
        reply.putExtra("miny", miny);
        reply.putExtra("maxy", maxy);
        if (json == null) {
            reply.putExtra("error", String.format(getString(R.string.couldnt_load_nearby_observations), ""));
        } else {
            //syncJson(json, false);
        }

        if (url.equalsIgnoreCase(mNearByObservationsUrl)) {
            // Only send the reply if a new near by observations request hasn't been made yet
            mApp.setServiceResult(ACTION_NEARBY, new SerializableJSONArray(json));
            sendBroadcast(reply);
        }
    }

    private JSONArray put(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        params.add(new BasicNameValuePair("_method", "PUT"));
        return request(url, "put", params, null, true);
    }

    private JSONArray put(String url, JSONObject jsonContent) throws AuthenticationException {
        return request(url, "put", null, jsonContent, true);
    }

    private JSONArray delete(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        return request(url, "delete", params, null, true);
    }

    private JSONArray post(String url, ArrayList<NameValuePair> params, boolean authenticated) throws AuthenticationException {
        return request(url, "post", params, null, authenticated);
    }

    private JSONArray post(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        return request(url, "post", params, null, true);
    }

    private JSONArray post(String url, JSONObject jsonContent) throws AuthenticationException {
        return request(url, "post", null, jsonContent, true);
    }


    private JSONArray get(String url) throws AuthenticationException {
        return get(url, false);
    }

    private JSONArray get(String url, boolean authenticated) throws AuthenticationException {
        return request(url, "get", null, null, authenticated);
    }

    private JSONArray request(String url, String method, ArrayList<NameValuePair> params, JSONObject jsonContent, boolean authenticated) throws AuthenticationException {
        return request(url, method, params, jsonContent, authenticated, false, false);
    }

    private String getAnonymousJWTToken() {
        String anonymousApiSecret = getString(R.string.jwt_anonymous_api_secret);

        if (anonymousApiSecret == null) return null;

        Map<String, Object> claims = new HashMap<>();
        claims.put("application", "android");
        claims.put("exp", (System.currentTimeMillis() / 1000) + 300);

        String compactJwt = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, anonymousApiSecret.getBytes())
                .compact();

        return compactJwt;
    }

    private String getJWTToken() {
        if (mPreferences == null)
            mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String jwtToken = mPreferences.getString("jwt_token", null);
        Long jwtTokenExpiration = mPreferences.getLong("jwt_token_expiration", 0);

        if ((jwtToken == null) || ((System.currentTimeMillis() - jwtTokenExpiration) / 1000 > JWT_TOKEN_EXPIRATION_MINS * 60)) {
            // JWT Tokens expire after 30 mins - if the token is non-existent or older than 25 mins (safe margin) - ask for a new one
            try {
                JSONArray result = get(HOST + "/users/api_token.json", true);
                if ((result == null) || (result.length() == 0)) return null;

                // Get newest JWT Token
                jwtToken = result.getJSONObject(0).getString("api_token");
                jwtTokenExpiration = System.currentTimeMillis();

                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putString("jwt_token", jwtToken);
                editor.putLong("jwt_token_expiration", jwtTokenExpiration);
                editor.commit();

                return jwtToken;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            // Current JWT token is still fresh/valid - return it as-is
            return jwtToken;
        }
    }

    private JSONArray request(String url, String method, ArrayList<NameValuePair> params, JSONObject jsonContent, boolean authenticated, boolean useJWTToken, boolean allowAnonymousJWTToken) throws AuthenticationException {
        CloseableHttpClient client = HttpClientBuilder.create()
                // Faster reading of data
                .disableContentCompression()
                // Handle redirects (301/302) for all HTTP methods (including POST)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setUserAgent(getUserAgent(mApp))
                .build();

//        Log.d(TAG, String.format("%s (%b - %s): %s", method, authenticated,
//                authenticated ? mCredentials : "<null>",
//                url));

        HttpRequestBase request;

        Log.d(TAG, String.format("URL: %s - %s (params: %s / %s)", method, url, (params != null ? params.toString() : "null"), (jsonContent != null ? jsonContent.toString() : "null")));

        if (method.equalsIgnoreCase("post")) {
            request = new HttpPost(url);
        } else if (method.equalsIgnoreCase("delete")) {
            request = new HttpDelete(url);
        } else if (method.equalsIgnoreCase("put")) {
            request = new HttpPut(url);
        } else {
            request = new HttpGet(url);
        }

        // POST params
        if (jsonContent != null) {
            // JSON body content
            request.setHeader("Content-type", "application/json");
            StringEntity entity = null;
            try {
                entity = new StringEntity(jsonContent.toString(), HTTP.UTF_8);
            } catch (Exception exc) {
                exc.printStackTrace();
            }

            if (method.equalsIgnoreCase("put")) {
                ((HttpPut) request).setEntity(entity);
            } else {
                ((HttpPost) request).setEntity(entity);
            }

        } else if (params != null) {
            // "Standard" multipart encoding
            Charset utf8Charset = Charset.forName("UTF-8");
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).getName().equalsIgnoreCase("image") || params.get(i).getName().equalsIgnoreCase("file") || params.get(i).getName().equalsIgnoreCase("user[icon]")) {
                    // If the key equals to "image", we use FileBody to transfer the data
                    String value = params.get(i).getValue();
                    if (value != null)
                        entity.addPart(params.get(i).getName(), new FileBody(new File(value)));
                } else {
                    // Normal string data
                    try {
                        entity.addPart(params.get(i).getName(), new StringBody(params.get(i).getValue(), utf8Charset));
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "failed to add " + params.get(i).getName() + " to entity for a " + method + " request: " + e);
                    }
                }
            }
            if (method.equalsIgnoreCase("put")) {
                ((HttpPut) request).setEntity(entity);
            } else {
                ((HttpPost) request).setEntity(entity);
            }
        }

        if (url.startsWith(API_HOST) && (mCredentials != null)) {
            // For the node API, if we're logged in, *always* use JWT authentication
            authenticated = true;
            useJWTToken = true;
        }

        if (authenticated) {
            if (useJWTToken && allowAnonymousJWTToken && (mCredentials == null)) {
                // User not logged in, but allow using anonymous JWT
                request.setHeader("Authorization", getAnonymousJWTToken());
            } else {
                ensureCredentials();

                if (useJWTToken) {
                    // Use JSON Web Token for this request
                    request.setHeader("Authorization", getJWTToken());
                } else if (mLoginType == LoginType.PASSWORD) {
                    // Old-style password authentication
                    request.setHeader("Authorization", "Basic " + mCredentials);
                } else {
                    // OAuth2 token (Facebook/G+/etc)
                    request.setHeader("Authorization", "Bearer " + mCredentials);
                }
            }
        }

        try {
            mResponseErrors = null;
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = entity != null ? EntityUtils.toString(entity) : null;

            Log.d(TAG, "Response: " + response.getStatusLine().toString());
            Log.d(TAG, String.format("(for URL: %s - %s (params: %s / %s))", method, url, (params != null ? params.toString() : "null"), (jsonContent != null ? jsonContent.toString() : "null")));
            LoggingUtils.largeLog(TAG, content);

            JSONArray json = null;
            mLastStatusCode = response.getStatusLine().getStatusCode();

            switch (response.getStatusLine().getStatusCode()) {
                //switch (response.getStatusCode()) {
                case HttpStatus.SC_UNPROCESSABLE_ENTITY:
                    // Validation error - still need to return response
                    Log.e(TAG, response.getStatusLine().toString());
                case HttpStatus.SC_OK:
                    try {
                        json = new JSONArray(content);
                    } catch (JSONException e) {
                        try {
                            JSONObject jo = new JSONObject(content);
                            json = new JSONArray();
                            json.put(jo);
                        } catch (JSONException e2) {
                        }
                    }

                    mResponseHeaders = response.getAllHeaders();

                    try {
                        if ((json != null) && (json.length() > 0)) {
                            JSONObject result = json.getJSONObject(0);
                            if (result.has("errors")) {
                                // Error response
                                Log.e(TAG, "Got an error response: " + result.get("errors").toString());
                                mResponseErrors = result.getJSONArray("errors");
                                return null;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if ((content != null) && (content.length() == 0)) {
                        // In case it's just non content (but OK HTTP status code) - so there's no error
                        json = new JSONArray();
                    }

                    return json;

                case HttpStatus.SC_UNAUTHORIZED:
                    throw new AuthenticationException();
                case HttpStatus.SC_GONE:
                    Log.e(TAG, "GONE: " + response.getStatusLine().toString());
                    // TODO create notification that informs user some observations have been deleted on the server,
                    // click should take them to an activity that lets them decide whether to delete them locally
                    // or post them as new observations
                default:
                    Log.e(TAG, response.getStatusLine().toString());
            }
        } catch (IOException e) {
            //request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        }
        return null;
    }

    private boolean ensureCredentials() throws AuthenticationException {
        if (mCredentials != null) {
            return true;
        }

        // request login unless passive
        if (!mPassive) {
            throw new AuthenticationException();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            stopSelf();
        }

        return false;
    }

    private void requestCredentials() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            stopSelf();
        }

        mApp.sweepingNotify(AUTH_NOTIFICATION, getString(R.string.please_sign_in), getString(R.string.please_sign_in_description), null);
    }


    // Returns an array of two strings: access token + iNat username
    public static String[] verifyCredentials(Context context, String username, String oauth2Token, LoginType authType) {
        String grantType = null;
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, getUserAgent(context));
        String url = HOST + (authType == LoginType.OAUTH_PASSWORD ? "/oauth/token" : "/oauth/assertion_token");
        HttpRequestBase request = new HttpPost(url);
        ArrayList<NameValuePair> postParams = new ArrayList<NameValuePair>();

        postParams.add(new BasicNameValuePair("format", "json"));
        postParams.add(new BasicNameValuePair("client_id", INaturalistApp.getAppContext().getString(R.string.oauth_client_id)));
        if (authType == LoginType.FACEBOOK) {
            grantType = "facebook";
        } else if (authType == LoginType.GOOGLE) {
            grantType = "google";
        } else if (authType == LoginType.OAUTH_PASSWORD) {
            grantType = "password";
        }

        postParams.add(new BasicNameValuePair("grant_type", grantType));
        if (authType == LoginType.OAUTH_PASSWORD) {
            postParams.add(new BasicNameValuePair("password", oauth2Token));
            postParams.add(new BasicNameValuePair("username", username));
            postParams.add(new BasicNameValuePair("client_secret", INaturalistApp.getAppContext().getString(R.string.oauth_client_secret)));
        } else {
            postParams.add(new BasicNameValuePair("assertion", oauth2Token));
        }

        try {
            ((HttpPost) request).setEntity(new UrlEncodedFormEntity(postParams));
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
            return null;
        }

        try {
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                // Upgrade to an access token
//                Log.d(TAG, "Authorization Response: " + content);
                JSONObject json = new JSONObject(content);
                String accessToken = json.getString("access_token");

                // Next, find the iNat username (since we currently only have the FB/Google email)
                request = new HttpGet(HOST + "/users/edit.json");
                request.setHeader("Authorization", "Bearer " + accessToken);

                response = client.execute(request);
                entity = response.getEntity();
                content = EntityUtils.toString(entity);

                Log.d(TAG, String.format("RESP2: %s", content));

                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    return null;
                }

                json = new JSONObject(content);
                if (!json.has("login")) {
                    return null;
                }

                String returnedUsername = json.getString("login");

                return new String[]{accessToken, returnedUsername};

            } else {
                Log.e(TAG, "Authentication failed: " + content);
                return null;
            }
        } catch (IOException e) {
            request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;

    }


    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(Observation observation) {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String name = "observation_" + observation.created_at.getTime() + "_" + timeStamp;
        values.put(android.provider.MediaStore.Images.Media.TITLE, name);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }


    public void syncJson(JSONArray json, boolean isUser) {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ArrayList<Integer> existingIds = new ArrayList<Integer>();
        ArrayList<Integer> newIds = new ArrayList<Integer>();
        HashMap<Integer, Observation> jsonObservationsById = new HashMap<Integer, Observation>();
        Observation observation;
        Observation jsonObservation;

        BetterJSONObject o;

        Log.d(TAG, "syncJson: " + isUser);
        LoggingUtils.largeLog(TAG, json.toString());

        for (int i = 0; i < json.length(); i++) {
            try {
                o = new BetterJSONObject(json.getJSONObject(i));
                ids.add(o.getInt("id"));

                Observation obs = new Observation(o);
                jsonObservationsById.put(o.getInt("id"), obs);

                if (isUser) {
                    // Save the project observations aside (will be later used in the syncing of project observations)
                    mProjectObservations.add(o.getJSONArray("project_observations"));

                    // Save project field values
                    Hashtable<Integer, ProjectFieldValue> fields = new Hashtable<Integer, ProjectFieldValue>();
                    JSONArray jsonFields = o.getJSONArray(o.has("ofvs") ? "ofvs" : "observation_field_values").getJSONArray();

                    for (int j = 0; j < jsonFields.length(); j++) {
                        BetterJSONObject field = new BetterJSONObject(jsonFields.getJSONObject(j));
                        int fieldId;
                        if (field.has("observation_field")) {
                            fieldId = field.getJSONObject("observation_field").getInt("id");
                        } else {
                            fieldId = field.getInt("field_id");
                        }
                        fields.put(fieldId, new ProjectFieldValue(field));
                    }

                    mProjectFieldValues.put(o.getInt("id"), fields);
                }
            } catch (JSONException e) {
                Log.e(TAG, "syncJson: JSONException: " + e.toString());
            }
        }
        // find obs with existing ids
        String joinedIds = StringUtils.join(ids, ",");
        // TODO why doesn't selectionArgs work for id IN (?)
        Cursor c = getContentResolver().query(Observation.CONTENT_URI,
                Observation.PROJECTION,
                "id IN (" + joinedIds + ")", null, Observation.DEFAULT_SORT_ORDER);

        // update existing
        c.moveToFirst();
        ContentValues cv;
        while (c.isAfterLast() == false) {
            observation = new Observation(c);
            jsonObservation = jsonObservationsById.get(observation.id);
            boolean isModified = observation.merge(jsonObservation);

            Log.d(TAG, "syncJson - updating existing: " + observation.id + ":" + observation._id + ":" + observation.preferred_common_name + ":" + observation.taxon_id);
            Log.d(TAG, "syncJson - remote obs: " + jsonObservation.id + ":" + jsonObservation.preferred_common_name + ":" + jsonObservation.taxon_id);

            cv = observation.getContentValues();
            if (observation._updated_at.before(jsonObservation.updated_at)) {
                // Remote observation is newer (and thus has overwritten the local one) - update its
                // sync at time so we won't update the remote servers later on (since we won't
                // accidently consider this an updated record)
                cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            }

            // Add any new photos that were added remotely
            ArrayList<Integer> observationPhotoIds = new ArrayList<Integer>();
            ArrayList<Integer> existingObservationPhotoIds = new ArrayList<Integer>();
            Cursor pc = getContentResolver().query(
                    ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "(observation_id = " + observation.id + ")",
                    null, null);
            pc.moveToFirst();
            while (pc.isAfterLast() == false) {
                int photoId = pc.getInt(pc.getColumnIndexOrThrow(ObservationPhoto.ID));
                if (photoId != 0) {
                    existingObservationPhotoIds.add(photoId);
                }
                pc.moveToNext();
            }
            pc.close();
            Log.d(TAG, "syncJson: Adding photos for obs " + observation.id + ":" + existingObservationPhotoIds.toString());
            Log.d(TAG, "syncJson: JsonObservation: " + jsonObservation + ":" + jsonObservation.photos);
            for (int j = 0; j < jsonObservation.photos.size(); j++) {
                ObservationPhoto photo = jsonObservation.photos.get(j);
                photo._observation_id = jsonObservation._id;

                if (photo.id == null) {
                    Log.w(TAG, "syncJson: Null photo ID! " + photo);
                    continue;
                }

                observationPhotoIds.add(photo.id);
                if (existingObservationPhotoIds.contains(photo.id)) {
                    Log.d(TAG, "syncJson: photo " + photo.id + " has already been added, skipping...");
                    continue;
                }
                ContentValues opcv = photo.getContentValues();
                // So we won't re-add this photo as though it was a local photo
                Log.d(TAG, "syncJson: Setting _SYNCED_AT - " + photo.id + ":" + photo._id + ":" + photo._observation_id + ":" + photo.observation_id);
                opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                opcv.put(ObservationPhoto._OBSERVATION_ID, photo.observation_id);
                opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
                opcv.put(ObservationPhoto.ID, photo.id);
                try {
                    getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
                } catch (SQLException ex) {
                    // Happens when the photo already exists - ignore
                    ex.printStackTrace();
                }
            }

            // Delete photos that were synced but weren't present in the remote response,
            // indicating they were deleted elsewhere
            String joinedPhotoIds = StringUtils.join(observationPhotoIds, ",");
            String where = "observation_id = " + observation.id + " AND id IS NOT NULL";
            if (observationPhotoIds.size() > 0) {
                where += " AND id NOT in (" + joinedPhotoIds + ")";
            }
            Log.d(TAG, "syncJson: Deleting local photos: " + where);
            Log.d(TAG, "syncJson: Deleting local photos, IDs: " + observationPhotoIds);
            int deleteCount = getContentResolver().delete(
                    ObservationPhoto.CONTENT_URI,
                    where,
                    null);
            Log.d(TAG, "syncJson: Deleting local photos: " + deleteCount);

            if (deleteCount > 0) {
                Crashlytics.log(1, TAG, String.format("Warning: Deleted %d photos locally after sever did not contain those IDs - observation id: %s, photo ids: %s",
                        deleteCount, observation.id, joinedPhotoIds));
            }

            if (isModified) {
                // Only update the DB if needed
                Log.d(TAG, "syncJson: Updating observation: " + observation.id + ":" + observation._id);
                getContentResolver().update(observation.getUri(), cv, null, null);
            }
            existingIds.add(observation.id);
            c.moveToNext();
        }
        c.close();

        // insert new
        List<Observation> newObservations = new ArrayList<Observation>();
        newIds = (ArrayList<Integer>) CollectionUtils.subtract(ids, existingIds);
        Collections.sort(newIds);
        Log.d(TAG, "syncJson: Adding new observations: " + newIds);
        for (int i = 0; i < newIds.size(); i++) {
            jsonObservation = jsonObservationsById.get(newIds.get(i));

            Cursor c2 = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "id = ?", new String[]{String.valueOf(jsonObservation.id)}, Observation.DEFAULT_SORT_ORDER);
            int count = c2.getCount();
            c2.close();

            if (count > 0) {
                Log.d(TAG, "syncJson: Observation " + jsonObservation.id + " already exists locally - not adding");
                continue;
            }

            cv = jsonObservation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            cv.put(Observation.LAST_COMMENTS_COUNT, jsonObservation.comments_count);
            cv.put(Observation.LAST_IDENTIFICATIONS_COUNT, jsonObservation.identifications_count);
            Uri newObs = getContentResolver().insert(Observation.CONTENT_URI, cv);
            Long newObsId = ContentUris.parseId(newObs);
            jsonObservation._id = Integer.valueOf(newObsId.toString());
            Log.d(TAG, "syncJson: Adding new obs: " + jsonObservation);
            newObservations.add(jsonObservation);
        }

        if (isUser) {
            for (int i = 0; i < newObservations.size(); i++) {
                jsonObservation = newObservations.get(i);

                // Save the new observation's photos
                Log.d(TAG, "syncJson: Saving new obs' photos: " + jsonObservation + ":" + jsonObservation.photos);
                for (int j = 0; j < jsonObservation.photos.size(); j++) {
                    ObservationPhoto photo = jsonObservation.photos.get(j);

                    if (photo.uuid == null) {
                        c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                                ObservationPhoto.PROJECTION,
                                "_id = ?", new String[]{String.valueOf(photo.id)}, ObservationPhoto.DEFAULT_SORT_ORDER);
                    } else {
                        c = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                                ObservationPhoto.PROJECTION,
                                "uuid = ?", new String[]{String.valueOf(photo.uuid)}, ObservationPhoto.DEFAULT_SORT_ORDER);
                    }

                    if (c.getCount() > 0) {
                        // Photo already exists - don't save
                        Log.d(TAG, "syncJson: Photo already exists - skipping: " + photo.id);
                        c.close();
                        continue;
                    }

                    c.close();

                    photo._observation_id = jsonObservation._id;

                    ContentValues opcv = photo.getContentValues();
                    Log.d(TAG, "syncJson: Setting _SYNCED_AT - " + photo.id + ":" + photo._id + ":" + photo._observation_id + ":" + photo.observation_id);
                    Log.d(TAG, "syncJson: Setting _SYNCED_AT - " + photo);
                    opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis()); // So we won't re-add this photo as though it was a local photo
                    opcv.put(ObservationPhoto._OBSERVATION_ID, photo._observation_id);
                    opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
                    opcv.put(ObservationPhoto._ID, photo.id);
                    try {
                        getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
                    } catch (SQLException ex) {
                        // Happens when the photo already exists - ignore
                        ex.printStackTrace();
                    }
                }
            }
        }

        if (isUser) {
            storeProjectObservations();
        }
    }

    private JSONObject observationToJsonObject(Observation observation, boolean isPOST) {
        JSONObject obs = observation.toJSONObject();
        try {

            if (isPOST) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                obs.put("site_id", mApp.getStringResourceByName("inat_site_id_" + inatNetwork));
            }

            JSONObject obsContainer = new JSONObject();
            obsContainer.put("observation", obs);
            obsContainer.put("ignore_photos", true);

            return obsContainer;
        } catch (JSONException exc) {
            exc.printStackTrace();
            return null;
        }
    }

    private ArrayList<NameValuePair> paramsForObservation(Observation observation, boolean isPOST) {
        ArrayList<NameValuePair> params = observation.getParams();
        params.add(new BasicNameValuePair("ignore_photos", "true"));

        if (isPOST) {
            String inatNetwork = mApp.getInaturalistNetworkMember();
            params.add(new BasicNameValuePair("site_id", mApp.getStringResourceByName("inat_site_id_" + inatNetwork)));
        }

        return params;
    }

    private boolean handleObservationResponse(Observation observation, JSONArray response) {
        try {
            if (response == null || response.length() != 1) {
                return false;
            }
            JSONObject json = response.getJSONObject(0);
            BetterJSONObject o = new BetterJSONObject(json);
            Log.d(TAG, "handleObservationResponse: Observation: " + observation);
            Log.d(TAG, "handleObservationResponse: JSON: ");
            LoggingUtils.largeLog(TAG, json.toString());

            Observation jsonObservation = new Observation(o);
            Log.d(TAG, "handleObservationResponse: jsonObservation: " + jsonObservation);
            observation.merge(jsonObservation);
            Log.d(TAG, "handleObservationResponse: merged obs: " + observation);
            ContentValues cv = observation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(observation.getUri(), cv, null, null);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private class AuthenticationException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public interface IOnLocation {
        void onLocation(Location location);
    }

    private Location getLocationFromGPS() {
        LocationManager locationManager = (LocationManager) mApp.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, false);
        if (provider == null) return null;

        Location location = locationManager.getLastKnownLocation(provider);
        Log.e(TAG, "getLocationFromGPS: " + location);

        return location;
    }

    private Location getLastKnownLocationFromClient() {
        Location location = null;

        try {
            location = LocationServices.FusedLocationApi.getLastLocation(mLocationClient);
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
        }

        Log.e(TAG, "getLastKnownLocationFromClient: " + location);
        if (location == null) {
            // Failed - try and return last place using GPS
            return getLocationFromGPS();
        } else {
            return location;
        }
    }

    private void getLocation(final IOnLocation callback) {
        if (!mApp.isLocationEnabled(null)) {
            Log.e(TAG, "getLocation: Location not enabled");
            // Location not enabled
            new Thread(new Runnable() {
                @Override
                public void run() {
                    callback.onLocation(null);
                }
            }).start();
            return;
        }

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

        Log.e(TAG, "getLocation: resultCode = " + resultCode);

        if (ConnectionResult.SUCCESS == resultCode) {
            // User Google Play services if available
            if ((mLocationClient != null) && (mLocationClient.isConnected())) {
                // Location client already initialized and connected - use it
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onLocation(getLastKnownLocationFromClient());
                    }
                }).start();
            } else {
                // Connect to the place services
                mLocationClient = new GoogleApiClient.Builder(this)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(new ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                // Connected successfully
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onLocation(getLastKnownLocationFromClient());
                                        mLocationClient.disconnect();
                                    }
                                }).start();
                            }

                            @Override
                            public void onConnectionSuspended(int i) { }
                        })
                        .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(ConnectionResult connectionResult) {
                                // Couldn't connect
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onLocation(null);
                                        mLocationClient.disconnect();
                                    }
                                }).start();
                            }
                        })
                        .build();
                mLocationClient.connect();
            }

        } else {
            // Use GPS alone for place
            new Thread(new Runnable() {
                @Override
                public void run() {
                    callback.onLocation(getLocationFromGPS());
                }
            }).start();
        }
    }

    @Override
    public void onDestroy() {
        mIsStopped = true;
        super.onDestroy();
    }


    public static String getUserAgent(Context context) {
        PackageInfo info = null;
        try {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String userAgent = USER_AGENT.replace("%BUILD%", info != null ? String.valueOf(info.versionCode) : String.valueOf(INaturalistApp.VERSION));
        userAgent = userAgent.replace("%VERSION%", info != null ? info.versionName : String.valueOf(INaturalistApp.VERSION));

        return userAgent;
    }


    private int modulo(int x, int y) {
        int result = x % y;
        if (result < 0) {
            result += y;
        }
        return result;
    }

    private boolean downloadToFile(String uri, String outputFilename) {
        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            URL url = new URL(uri);
            conn = (HttpURLConnection) url.openConnection();
            InputStream in = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(outputFilename);

            int read;
            byte[] buff = new byte[1024];
            while ((read = in.read(buff)) != -1) {
                output.write(buff, 0, read);
            }

            output.close();

            conn.disconnect();
        } catch (MalformedURLException e) {
            return false;
        } catch (IOException e) {
            return false;
        }

        return true;
    }
}

