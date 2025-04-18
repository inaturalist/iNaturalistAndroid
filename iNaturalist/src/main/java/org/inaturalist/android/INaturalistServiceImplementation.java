package org.inaturalist.android;

import static org.inaturalist.android.INaturalistService.*;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_GONE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;

import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Pair;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.maps.GeoApiContext;
import com.google.maps.TimeZoneApi;
import com.google.maps.model.LatLng;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class INaturalistServiceImplementation {
    private static final String TAG = "INaturalistServiceImplementation";

    private static final int MAX_OBSVERATIONS_TO_REDOWNLOAD = 100; // Happens when user switches language and we need the new taxon name in that language

    private final INaturalistApp mApp;
    private final Handler mHandler;
    private final Context mContext;

    private String mLogin;
    private String mCredentials;
    private SharedPreferences mPreferences;
    private boolean mPassive;
    private LoginType mLoginType;

    private boolean mIsSyncing;
    private boolean mIsClearingOldPhotosCache;

    private long mLastConnectionTest = 0;

    private GoogleApiClient mLocationClient;

    private ArrayList<SerializableJSONArray> mProjectObservations;

    private Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>> mProjectFieldValues;

    private Headers mResponseHeaders = null;
    private Date mRetryAfterDate = null;
    private long mLastServiceUnavailableNotification = 0;
    private boolean mServiceUnavailable = false;

    private JSONArray mResponseErrors;
    private JSONObject mLastResponseJson;

    private String mNearByObservationsUrl;
    private int mLastStatusCode = 0;
    private Object mObservationLock = new Object();
    private Object mSyncJsonLock = new Object();
    private List<String> mSyncedJSONs = new ArrayList<>();

    private Location mLastLocation = null;

    public enum LoginType {
        PASSWORD,
        GOOGLE,
        FACEBOOK,
        OAUTH_PASSWORD,
    };

    public INaturalistServiceImplementation(Context context) {
        mApp = (INaturalistApp) context.getApplicationContext();
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void onHandleIntentWorker(final Intent intent) {
        boolean cancelSyncRequested = false;
        boolean syncFailed = false;
        boolean dontStopSync = false;
        mPreferences = mContext.getSharedPreferences("iNaturalistPreferences", Context.MODE_PRIVATE);
        mLogin = mPreferences.getString("username", null);
        mCredentials = mPreferences.getString("credentials", null);
        mLoginType = LoginType.valueOf(mPreferences.getString("login_type", LoginType.OAUTH_PASSWORD.toString()));

        if (intent == null) return;

        String action = intent.getAction();

        if (action == null) return;

        mPassive = action.equals(ACTION_PASSIVE_SYNC);

        Logger.tag(TAG).debug("Service: " + action);

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
                                Logger.tag(TAG).error(e);
                            }
                        }
                    });
                }

            } else if (action.equals(ACTION_FIRST_SYNC)) {
                mIsSyncing = true;
                mApp.setIsSyncing(mIsSyncing);

                saveJoinedProjects();
                boolean success = getUserObservations(INITIAL_SYNC_OBSERVATION_COUNT);

                // Get total obs count
                BetterJSONObject user = getUserDetails();

                if (user == null) {
                    throw new SyncFailedException();
                }

                int totalObsCount = user.getInt("observations_count");
                mPreferences.edit().putInt("observation_count", totalObsCount).commit();

                Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "(is_deleted = 0 OR is_deleted is NULL) AND (user_login = '" + mLogin + "')",
                        null,
                        Observation.DEFAULT_SORT_ORDER);
                c.moveToLast();

                if (c.getCount() > 0) {
                    BetterCursor bc = new BetterCursor(c);
                    int lastId = bc.getInteger(Observation.ID);
                    mPreferences.edit().putInt("last_downloaded_id", lastId).commit();
                } else {
                    // No observations - probably a new user

                    // Update the user's timezone (in case we registered via FB/G+)
                    getTimezoneByCurrentLocation(new IOnTimezone() {
                        @Override
                        public void onTimezone(String timezoneName) {
                            Logger.tag(TAG).debug("Detected Timezone: " + timezoneName);

                            if (timezoneName != null) {
                                try {
                                    updateUserTimezone(timezoneName);
                                } catch (AuthenticationException e) {
                                    Logger.tag(TAG).error(e);
                                }
                            }
                        }
                    });

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
                reply.putExtra(TAXON_ID, taxonId);

                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_ADD_MISSING_OBS_UUID)) {
                addObservationUUIDsToPhotosAndSounds();

            } else if (action.equals(ACTION_GET_POPULAR_FIELD_VALUES)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject results = getPopularFieldValues(taxonId);

                Intent reply = new Intent(POPULAR_FIELD_VALUES_RESULT);
                reply.putExtra(POPULAR_FIELD_VALUES_RESULT, results);
                reply.putExtra(TAXON_ID, taxonId);

                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_AGREE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                boolean disagreement = intent.getBooleanExtra(DISAGREEMENT, false);
                addIdentification(observationId, taxonId, null, disagreement, false);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);
                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }


            } else if (action.equals(ACTION_RESTORE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int identificationId = intent.getIntExtra(IDENTIFICATION_ID, 0);
                restoreIdentification(identificationId);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }


            } else if (action.equals(ACTION_UPDATE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                int identificationId = intent.getIntExtra(IDENTIFICATION_ID, 0);
                String body = intent.getStringExtra(IDENTIFICATION_BODY);
                updateIdentification(observationId, identificationId, taxonId, body);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }


            } else if (action.equals(ACTION_REMOVE_ID)) {
                int id = intent.getIntExtra(IDENTIFICATION_ID, 0);
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                removeIdentification(id);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }

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
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

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
                    Logger.tag(TAG).error(e);
                }

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));

                    Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }

            } else if (action.equals(ACTION_ADD_PROJECT_FIELD)) {
                int fieldId = intent.getIntExtra(FIELD_ID, 0);
                addProjectField(fieldId);

            } else if (action.equals(ACTION_REGISTER_USER)) {
                String email = intent.getStringExtra(EMAIL);
                String password = intent.getStringExtra(PASSWORD);
                String username = intent.getStringExtra(USERNAME);
                String license = intent.getStringExtra(LICENSE);

                getTimezoneByCurrentLocation(new IOnTimezone() {
                    @Override
                    public void onTimezone(String timezoneName) {
                        Logger.tag(TAG).debug("Detected Timezone: " + timezoneName);

                        String error = null;
                        try {
                            error = registerUser(email, password, username, license, timezoneName);
                        } catch (AuthenticationException e) {
                            Logger.tag(TAG).error(e);
                            error = e.toString();
                        }

                        Intent reply = new Intent(ACTION_REGISTER_USER_RESULT);
                        reply.putExtra(REGISTER_USER_STATUS, (error == null || mLastStatusCode == HTTP_CREATED));
                        reply.putExtra(REGISTER_EMAIL_VERIFICATION_REQUIRED, mLastStatusCode == HTTP_CREATED);
                        reply.putExtra(REGISTER_USER_ERROR, error);
                        boolean b = LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

                    }
                });


            } else if (action.equals(ACTION_GET_PROJECT_NEWS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                SerializableJSONArray results = getProjectNews(projectId);

                Intent reply = new Intent(ACTION_PROJECT_NEWS_RESULT);
                reply.putExtra(RESULTS, results);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_OBSERVATIONS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectObservations(projectId);
                results = ObservationUtils.getMinimalObservationResults(results);

                mApp.setServiceResult(ACTION_PROJECT_OBSERVATIONS_RESULT, results);
                Intent reply = new Intent(ACTION_PROJECT_OBSERVATIONS_RESULT);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_IDENTIFIERS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectIdentifiers(projectId);
                results = ObservationUtils.getMinimalObserverResults(results);

                Intent reply = new Intent(ACTION_PROJECT_IDENTIFIERS_RESULT);
                mApp.setServiceResult(ACTION_PROJECT_IDENTIFIERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_OBSERVERS)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectObservers(projectId);
                results = ObservationUtils.getMinimalObserverResults(results);

                Intent reply = new Intent(ACTION_PROJECT_OBSERVERS_RESULT);
                mApp.setServiceResult(ACTION_PROJECT_OBSERVERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_PROJECT_SPECIES)) {
                int projectId = intent.getIntExtra(PROJECT_ID, 0);
                BetterJSONObject results = getProjectSpecies(projectId);
                results = ObservationUtils.getMinimalSpeciesResults(results);

                Intent reply = new Intent(ACTION_PROJECT_SPECIES_RESULT);
                mApp.setServiceResult(ACTION_PROJECT_SPECIES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_ANNOTATION)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = deleteAnnotation(uuid);

                Intent reply = new Intent(DELETE_ANNOTATION_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_ANNOTATION_VOTE)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = deleteAnnotationVote(uuid);

                Intent reply = new Intent(DELETE_ANNOTATION_VOTE_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_DELETE_ID_CAN_BE_IMPROVED_VOTE)) {
                int obsId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject result = deleteIdCanBeImprovedVote(obsId);

                Intent reply = new Intent(DELETE_ID_CAN_BE_IMPROVED_VOTE_RESULT);
                reply.putExtra(DELETE_ID_CAN_BE_IMPROVED_VOTE_RESULT, result);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_ID_CAN_BE_IMPROVED_VOTE)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject result = voteIdCanBeImproved(observationId, true);

                Intent reply = new Intent(ID_CAN_BE_IMPROVED_RESULT);
                reply.putExtra(ID_CAN_BE_IMPROVED_RESULT, result);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_ID_CANNOT_BE_IMPROVED_VOTE)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject result = voteIdCanBeImproved(observationId, false);

                Intent reply = new Intent(ID_CANNOT_BE_IMPROVED_RESULT);
                reply.putExtra(ID_CANNOT_BE_IMPROVED_RESULT, result);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_SET_ANNOTATION_VALUE)) {
                int obsId = intent.getIntExtra(OBSERVATION_ID, 0);
                int attributeId = intent.getIntExtra(ATTRIBUTE_ID, 0);
                int valueId = intent.getIntExtra(VALUE_ID, 0);
                BetterJSONObject results = setAnnotationValue(obsId, attributeId, valueId);

                Intent reply = new Intent(SET_ANNOTATION_VALUE_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_DATA_QUALITY_METRICS)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                BetterJSONObject results = getDataQualityMetrics(observationId);

                Intent reply = new Intent(DATA_QUALITY_METRICS_RESULT);
                reply.putExtra(DATA_QUALITY_METRICS_RESULT, results);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);


            } else if (action.equals(ACTION_DELETE_DATA_QUALITY_VOTE)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String metric = intent.getStringExtra(METRIC);
                BetterJSONObject result = deleteDataQualityMetricVote(observationId, metric);

                Intent reply = new Intent(DELETE_DATA_QUALITY_VOTE_RESULT);
                reply.putExtra(SUCCESS, result != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);


            } else if (action.equals(ACTION_AGREE_DATA_QUALITY)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String metric = intent.getStringExtra(METRIC);
                BetterJSONObject results = agreeDataQualityMetric(observationId, metric, true);

                Intent reply = new Intent(AGREE_DATA_QUALITY_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_DISAGREE_DATA_QUALITY)) {
                Integer observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String metric = intent.getStringExtra(METRIC);
                BetterJSONObject results = agreeDataQualityMetric(observationId, metric, false);

                Intent reply = new Intent(DISAGREE_DATA_QUALITY_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);


            } else if (action.equals(ACTION_AGREE_ANNOTATION)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = agreeAnnotation(uuid, true);

                Intent reply = new Intent(AGREE_ANNOTATION_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_DISAGREE_ANNOTATION)) {
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = agreeAnnotation(uuid, false);

                Intent reply = new Intent(DISAGREE_ANNOTATION_RESULT);
                reply.putExtra(SUCCESS, results != null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ALL_ATTRIBUTES)) {
                BetterJSONObject results = getAllAttributes();

                Intent reply = new Intent(GET_ALL_ATTRIBUTES_RESULT);
                mApp.setServiceResult(GET_ALL_ATTRIBUTES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ATTRIBUTES_FOR_TAXON)) {
                Integer taxonId = intent.getIntExtra(TAXON_ID, -1);
                Integer obsId = intent.getIntExtra(OBSERVATION_ID, -1);
                SerializableJSONArray ancestors = (SerializableJSONArray) intent.getSerializableExtra(ANCESTORS);
                BetterJSONObject results = getAttributesForTaxon(ancestors != null ? ancestors.getJSONArray() : null, taxonId);

                Intent reply = new Intent(GET_ATTRIBUTES_FOR_TAXON_RESULT);
                mApp.setServiceResult(GET_ATTRIBUTES_FOR_TAXON_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(OBSERVATION_ID, obsId);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TAXON_SUGGESTIONS)) {
                String obsFilename = intent.getStringExtra(OBS_PHOTO_FILENAME);
                String obsUrl = intent.getStringExtra(OBS_PHOTO_URL);
                Double longitude = intent.getDoubleExtra(LONGITUDE, 0);
                Double latitude = intent.getDoubleExtra(LATITUDE, 0);
                String suggestionSource = intent.getStringExtra(SUGGESTION_SOURCE);
                Timestamp observedOn = (Timestamp) intent.getSerializableExtra(OBSERVED_ON);
                Integer placeId = intent.getIntExtra(PLACE_ID, -1);
                if (placeId == -1) placeId = null;
                Integer taxonId = intent.getIntExtra(TAXON_ID, -1);
                if (taxonId == -1) taxonId = null;
                Integer limit = 50;
                Integer page = intent.getIntExtra(PAGE_NUMBER, -1);
                if (page == -1) page = 0;
                Double placeLat = intent.getDoubleExtra(PLACE_LAT, -1);
                if (placeLat == -1) placeLat = null;
                Double placeLng = intent.getDoubleExtra(PLACE_LNG, -1);
                if (placeLng == -1) placeLng = null;

                BetterJSONObject taxonSuggestions = null;
                if (obsFilename == null) {
                    // It's an online observation
                    taxonSuggestions = getTaxonSuggestions(obsUrl, latitude, longitude, observedOn, suggestionSource, placeId, taxonId, placeLat, placeLng, limit, page);
                } else {
                    // Local photo  - Resize photo to 640x640 max, not using Lanczos
                    String resizedPhotoFilename = ImageUtils.resizeImage(mContext, obsFilename, null, 640, true, false);

                    if (resizedPhotoFilename != null) {
                        taxonSuggestions = getTaxonSuggestions(resizedPhotoFilename, latitude, longitude, observedOn, suggestionSource, placeId, taxonId, placeLat, placeLng, limit, page);

                        File resizedFile = new File(resizedPhotoFilename);
                        resizedFile.delete();
                    }
                }


                Intent reply = new Intent(ACTION_GET_TAXON_SUGGESTIONS_RESULT);
                reply.putExtra(TAXON_SUGGESTIONS, taxonSuggestions);
                reply.putExtra(OBS_PHOTO_URL, obsUrl);
                reply.putExtra(OBS_PHOTO_FILENAME, obsFilename);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TAXON_NEW)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject taxon = getTaxonNew(taxonId);

                Intent reply = new Intent(ACTION_GET_TAXON_NEW_RESULT);
                mApp.setServiceResult(ACTION_GET_TAXON_NEW_RESULT, taxon);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TAXON)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject taxon = getTaxon(taxonId);

                Intent reply = new Intent(ACTION_GET_TAXON_RESULT);
                reply.putExtra(TAXON_RESULT, taxon);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_SEARCH_PLACES)) {
                String query = intent.getStringExtra(QUERY);
                int page = intent.getIntExtra(PAGE_NUMBER, 1);
                BetterJSONObject results = searchAutoComplete("places", query, page);

                Intent reply = new Intent(SEARCH_PLACES_RESULT);
                mApp.setServiceResult(SEARCH_PLACES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_SEARCH_USERS)) {
                String query = intent.getStringExtra(QUERY);
                int page = intent.getIntExtra(PAGE_NUMBER, 1);
                BetterJSONObject results = searchAutoComplete("users", query, page);

                Intent reply = new Intent(SEARCH_USERS_RESULT);
                mApp.setServiceResult(SEARCH_USERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(QUERY, query);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);


            } else if (action.equals(ACTION_SEARCH_TAXA)) {
                String query = intent.getStringExtra(QUERY);
                int page = intent.getIntExtra(PAGE_NUMBER, 1);
                BetterJSONObject results = searchAutoComplete("taxa", query, page);

                Intent reply = new Intent(SEARCH_TAXA_RESULT);
                mApp.setServiceResult(SEARCH_TAXA_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_UPDATE_CURRENT_USER_DETAILS)) {
                BetterJSONObject params = (BetterJSONObject) intent.getSerializableExtra(USER);
                BetterJSONObject user = updateCurrentUserDetails(params.getJSONObject());

                Intent reply = new Intent(UPDATE_CURRENT_USER_DETAILS_RESULT);
                reply.putExtra(USER, user);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_RESEND_EMAIL_CONFIRMATION)) {
                Boolean success = resendEmailConfirmation();

                Intent reply = new Intent(ACTION_RESEND_EMAIL_CONFIRMATION);
                reply.putExtra(SUCCESS, success);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_REFRESH_CURRENT_USER_SETTINGS)) {
                BetterJSONObject user = getCurrentUserDetails();

                if (user != null) {
                    // Update settings
                    mApp.setShowScientificNameFirst(user.getJSONObject().optBoolean("prefers_scientific_name_first", false));
                    mApp.setPrefersCommonNames(user.getJSONObject().optBoolean("prefers_common_names", true));

                    // Refresh privileges
                    JSONArray privileges = user.getJSONArray("privileges").getJSONArray();
                    Set<String> privilegesSet = new HashSet<>();
                    for (int i = 0; i < privileges.length(); i++) {
                        privilegesSet.add(privileges.optString(i));
                    }
                    mApp.setUserPrivileges(privilegesSet);

                    // Refresh muted users
                    JSONArray mutedUsers = user.getJSONArray("muted_user_ids").getJSONArray();
                    Set<Integer> mutedSet = new HashSet<>();
                    for (int i = 0; i < mutedUsers.length(); i++) {
                        mutedSet.add(mutedUsers.optInt(i));
                    }
                    mApp.setMutedUsers(mutedSet);

                    JSONArray arr = user.getJSONObject().optJSONArray("roles");
                    HashSet roles = new HashSet();
                    for (int i = 0; i < arr.length(); i++) {
                        roles.add(arr.optString(i));
                    }
                    mApp.setUserRoles(roles);

                    Intent reply = new Intent(REFRESH_CURRENT_USER_SETTINGS_RESULT);
                    reply.putExtra(USER, user);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }

            } else if (action.equals(ACTION_DELETE_PINNED_LOCATION)) {
                String id = intent.getStringExtra(ID);

                boolean success = deletePinnedLocation(id);

            } else if (action.equals(ACTION_PIN_LOCATION)) {
                Double latitude = intent.getDoubleExtra(LATITUDE, 0);
                Double longitude = intent.getDoubleExtra(LONGITUDE, 0);
                Double accuracy = intent.getDoubleExtra(ACCURACY, 0);
                String geoprivacy = intent.getStringExtra(GEOPRIVACY);
                String title = intent.getStringExtra(TITLE);

                boolean success = pinLocation(latitude, longitude, accuracy, geoprivacy, title);


            } else if (action.equals(ACTION_GET_PLACE_DETAILS)) {
                long placeId = intent.getIntExtra(PLACE_ID, 0);
                BetterJSONObject place = getPlaceDetails(placeId);

                Intent reply = new Intent(PLACE_DETAILS_RESULT);
                reply.putExtra(PLACE, place);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);


            } else if (action.equals(ACTION_GET_SPECIFIC_USER_DETAILS)) {
                String username = intent.getStringExtra(USERNAME);
                BetterJSONObject user = getUserDetails(username);

                Intent reply = new Intent(USER_DETAILS_RESULT);
                reply.putExtra(USER, user);
                reply.putExtra(USERNAME, username);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_CURRENT_LOCATION)) {
                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        Intent reply = new Intent(GET_CURRENT_LOCATION_RESULT);
                        reply.putExtra(LOCATION, location);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
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
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                            return;
                        }

                        BetterJSONObject missions = getMissions(location, username, taxonId, expandLocationByDegrees);
                        missions = ObservationUtils.getMinimalSpeciesResults(missions);

                        Intent reply = new Intent(MISSIONS_BY_TAXON_RESULT);
                        mApp.setServiceResult(MISSIONS_BY_TAXON_RESULT, missions);
                        reply.putExtra(IS_SHARED_ON_APP, true);
                        reply.putExtra(TAXON_ID, taxonId);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                    }
                });

            } else if (action.equals(ACTION_GET_TAXON_OBSERVATION_BOUNDS)) {
                final Integer taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject bounds = getTaxonObservationsBounds(taxonId);

                Intent reply = new Intent(TAXON_OBSERVATION_BOUNDS_RESULT);
                reply.putExtra(TAXON_OBSERVATION_BOUNDS_RESULT, bounds);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

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
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                            return;
                        }

                        BetterJSONObject missions = getMissions(location, username, null, expandLocationByDegrees);
                        missions = ObservationUtils.getMinimalSpeciesResults(missions);

                        Intent reply = new Intent(RECOMMENDED_MISSIONS_RESULT);
                        mApp.setServiceResult(RECOMMENDED_MISSIONS_RESULT, missions);
                        reply.putExtra(IS_SHARED_ON_APP, true);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                    }
                });

            } else if (action.equals(ACTION_GET_USER_SPECIES_COUNT)) {
                String username = intent.getStringExtra(USERNAME);
                BetterJSONObject speciesCount = getUserSpeciesCount(username);
                speciesCount = ObservationUtils.getMinimalSpeciesResults(speciesCount);

                Intent reply = new Intent(SPECIES_COUNT_RESULT);
                mApp.setServiceResult(SPECIES_COUNT_RESULT, speciesCount);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(USERNAME, username);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_USER_OBSERVATIONS)) {
                String username = intent.getStringExtra(USERNAME);
                JSONObject observations = getUserObservations(username);
                if (observations != null) {
                    BetterJSONObject minimalObs = ObservationUtils.getMinimalObservationResults(new BetterJSONObject(observations));
                    observations = minimalObs != null ? minimalObs.getJSONObject() : null;
                }

                Intent reply = new Intent(USER_OBSERVATIONS_RESULT);
                mApp.setServiceResult(USER_OBSERVATIONS_RESULT, observations != null ? new BetterJSONObject(observations) : null);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(USERNAME, username);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_SEARCH_USER_OBSERVATIONS)) {
                String query = intent.getStringExtra(QUERY);
                SerializableJSONArray observations = searchUserObservation(query);

                Intent reply = new Intent(USER_SEARCH_OBSERVATIONS_RESULT);
                mApp.setServiceResult(USER_SEARCH_OBSERVATIONS_RESULT, observations);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(QUERY, query);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

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
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_USER_IDENTIFICATIONS)) {
                String username = intent.getStringExtra(USERNAME);
                BetterJSONObject identifications = getUserIdentifications(username);
                BetterJSONObject minimalIdentifications = ObservationUtils.getMinimalIdentificationResults(identifications, username);

                Intent reply = new Intent(IDENTIFICATIONS_RESULT);
                mApp.setServiceResult(IDENTIFICATIONS_RESULT, minimalIdentifications);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(USERNAME, username);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TOP_IDENTIFIERS)) {
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                String uuid = intent.getStringExtra(UUID);
                SerializableJSONArray taxonIds = (SerializableJSONArray) intent.getSerializableExtra(TAXON_IDS);
                BetterJSONObject results = getObservationResultsForMultipleTaxa("identifiers", pageSize, taxonIds.getJSONArray());
                results = ObservationUtils.getMinimalObserverResults(results);

                Intent reply = new Intent(GET_TOP_IDENTIFIERS_RESULT);
                mApp.setServiceResult(GET_TOP_IDENTIFIERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(UUID, uuid);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_TOP_OBSERVERS)) {
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                String uuid = intent.getStringExtra(UUID);
                SerializableJSONArray taxonIds = (SerializableJSONArray) intent.getSerializableExtra(TAXON_IDS);
                BetterJSONObject results = getObservationResultsForMultipleTaxa("observers", pageSize, taxonIds.getJSONArray());
                results = ObservationUtils.getMinimalObserverResults(results);

                Intent reply = new Intent(GET_TOP_OBSERVERS_RESULT);
                mApp.setServiceResult(GET_TOP_OBSERVERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(UUID, uuid);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_OBSERVERS)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = getExploreResults("observers", filters, pageNumber, pageSize, null);
                results = ObservationUtils.getMinimalObserverResults(results);

                Intent reply = new Intent(EXPLORE_GET_OBSERVERS_RESULT);
                mApp.setServiceResult(EXPLORE_GET_OBSERVERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(UUID, uuid);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ANNOUNCEMENTS)) {
                SerializableJSONArray results = getAnnouncements();

                Intent reply = new Intent(ANNOUNCEMENTS_RESULT);
                mApp.setServiceResult(ANNOUNCEMENTS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_DISMISS_ANNOUNCEMENT)) {
                int announcementId = intent.getIntExtra(ANNOUNCEMENT_ID, 0);
                dismissAnnouncement(announcementId);

            } else if (action.equals(ACTION_EXPLORE_GET_IDENTIFIERS)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = getExploreResults("identifiers", filters, pageNumber, pageSize, null);
                results = ObservationUtils.getMinimalObserverResults(results);

                Intent reply = new Intent(EXPLORE_GET_IDENTIFIERS_RESULT);
                mApp.setServiceResult(EXPLORE_GET_IDENTIFIERS_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(UUID, uuid);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_SPECIES)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject results = getExploreResults("species_counts", filters, pageNumber, pageSize, null);
                results = ObservationUtils.getMinimalSpeciesResults(results);

                Intent reply = new Intent(EXPLORE_GET_SPECIES_RESULT);
                mApp.setServiceResult(EXPLORE_GET_SPECIES_RESULT, results);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(UUID, uuid);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_EXPLORE_GET_OBSERVATIONS)) {
                ExploreSearchFilters filters = (ExploreSearchFilters) intent.getSerializableExtra(FILTERS);
                int pageNumber = intent.getIntExtra(PAGE_NUMBER, 1);
                int pageSize = intent.getIntExtra(PAGE_SIZE, EXPLORE_DEFAULT_RESULTS_PER_PAGE);
                String uuid = intent.getStringExtra(UUID);
                BetterJSONObject observations = getExploreResults(null, filters, pageNumber, pageSize, null);
                observations = ObservationUtils.getMinimalObservationResults(observations);

                Intent reply = new Intent(EXPLORE_GET_OBSERVATIONS_RESULT);
                mApp.setServiceResult(EXPLORE_GET_OBSERVATIONS_RESULT, observations);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(UUID, uuid);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_ADD_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String body = intent.getStringExtra(COMMENT_BODY);
                addComment(observationId, body);

                // Wait a little before refreshing the observation details - so we'll let the server update the comment
                // list (otherwise, it won't return the new comment)
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Logger.tag(TAG).error(e);
                }

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);

                if (observationJson == null) {
                    reply.putExtra(OBSERVATION_RESULT, (Serializable)null);
                    reply.putExtra(OBSERVATION_JSON_RESULT, (String)null);
                } else {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));

                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observation != null ? observationJson.toString() : null);
                }
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_UPDATE_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int commentId = intent.getIntExtra(COMMENT_ID, 0);
                String body = intent.getStringExtra(COMMENT_BODY);
                updateComment(commentId, observationId, body);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));

                    Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }

            } else if (action.equals(ACTION_DELETE_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int commentId = intent.getIntExtra(COMMENT_ID, 0);
                deleteComment(commentId);

                // Reload the observation at the end (need to refresh comment/ID list)
                JSONObject observationJson = getObservationJson(observationId, false, false);

                if (observationJson != null) {
                    Observation observation = new Observation(new BetterJSONObject(observationJson));

                    Intent reply = new Intent(ACTION_OBSERVATION_RESULT + observationId);
                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, observationJson.toString());
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                }

            } else if (action.equals(ACTION_GUIDE_XML)) {
                int guideId = intent.getIntExtra(ACTION_GUIDE_ID, 0);
                String guideXMLFilename = getGuideXML(guideId);

                if (guideXMLFilename == null) {
                    // Failed to get the guide XML - try and find the offline version, if available
                    GuideXML guideXml = new GuideXML(mContext, String.valueOf(guideId));

                    if (guideXml.isGuideDownloaded()) {
                        guideXMLFilename = guideXml.getOfflineGuideXmlFilePath();
                    }
                }

                Intent reply = new Intent(ACTION_GUIDE_XML_RESULT);
                reply.putExtra(GUIDE_XML_RESULT, guideXMLFilename);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

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
                String userLicense = intent.getStringExtra(ACTION_USER_LICENSE);
                String userPhotoLicense = intent.getStringExtra(ACTION_USER_PHOTO_LICENSE);
                String userSoundLicense = intent.getStringExtra(ACTION_USER_SOUND_LICENSE);
                boolean makeLicenseSame = intent.getBooleanExtra(ACTION_MAKE_LICENSE_SAME, false);
                boolean makePhotoLicenseSame = intent.getBooleanExtra(ACTION_MAKE_PHOTO_LICENSE_SAME, false);
                boolean makeSoundLicenseSame = intent.getBooleanExtra(ACTION_MAKE_SOUND_LICENSE_SAME, false);
                boolean deletePic = intent.getBooleanExtra(ACTION_USER_DELETE_PIC, false);

                JSONObject newUser = updateUser(username, email, password, fullName, bio, userPic, deletePic, userLicense, userPhotoLicense, userSoundLicense, makeLicenseSame, makePhotoLicenseSame, makeSoundLicenseSame);

                if ((newUser != null) && (!newUser.has("errors"))) {
                    SharedPreferences prefs = mContext.getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();

                    String prevLogin = mLogin;
                    mLogin = newUser.optString("login");
                    editor.putString("username", mLogin);
                    editor.putInt("user_id", newUser.optInt("id"));
                    if (!newUser.has("icon_url") || newUser.isNull("icon_url")) {
                        editor.putString("user_icon_url", null);
                    } else {
                        editor.putString("user_icon_url", newUser.optString("icon_url"));
                    }
                    editor.putString("user_full_name", newUser.optString("name"));
                    editor.putString("user_bio", bio);
                    editor.putString("user_email", email);

                    editor.apply();

                    if (userLicense != null) mApp.setDefaultObservationLicense(userLicense);
                    if (userPhotoLicense != null) mApp.setDefaultPhotoLicense(userPhotoLicense);
                    if (userSoundLicense != null) mApp.setDefaultSoundLicense(userSoundLicense);


                    if ((prevLogin != null) && (!prevLogin.equals(mLogin))) {
                        // Update observations with the new username
                        ContentValues cv = new ContentValues();
                        cv.put("user_login", mLogin);
                        // Update its sync at time so we won't update the remote servers later on (since we won't
                        // accidentally consider this an updated record)
                        cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                        int count = mContext.getContentResolver().update(Observation.CONTENT_URI, cv, "(user_login = ?) AND (id IS NOT NULL)", new String[]{prevLogin});
                        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "Updated %d synced observations with new user login %s from %s", count, mLogin, prevLogin));

                        cv = new ContentValues();
                        cv.put("user_login", mLogin);
                        count = mContext.getContentResolver().update(Observation.CONTENT_URI, cv, "(user_login = ?) AND (id IS NULL)", new String[]{ prevLogin });
                        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "Updated %d new observations with new user login %s from %s", count, mLogin, prevLogin));
                    }
                }

                Intent reply = new Intent(ACTION_UPDATE_USER_DETAILS_RESULT);
                reply.putExtra(USER, newUser != null ? new BetterJSONObject(newUser) : null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_USER_DETAILS)) {
                BetterJSONObject user = null;
                boolean authenticationFailed = false;
                boolean isDeleted = false;

                try {
                    user = getUserDetails();
                } catch (AuthenticationException exc) {
                    Logger.tag(TAG).error(exc);

                    // See if user was deleted via the website
                    isDeleted = isUserDeleted(mLogin);

                    if (!isDeleted) {
                        // This means the user has changed his password on the website
                        authenticationFailed = true;
                    }
                }

                Intent reply = new Intent(ACTION_GET_USER_DETAILS_RESULT);
                reply.putExtra(USER, user);
                reply.putExtra(AUTHENTICATION_FAILED, authenticationFailed);
                reply.putExtra(USER_DELETED, isDeleted);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_TAXA_FOR_GUIDE)) {
                int guideId = intent.getIntExtra(ACTION_GUIDE_ID, 0);
                SerializableJSONArray taxa = getTaxaForGuide(guideId);

                mApp.setServiceResult(ACTION_TAXA_FOR_GUIDES_RESULT, taxa);
                Intent reply = new Intent(ACTION_TAXA_FOR_GUIDES_RESULT);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_ALL_GUIDES)) {
                SerializableJSONArray guides = getAllGuides();

                mApp.setServiceResult(ACTION_ALL_GUIDES_RESULT, guides);
                Intent reply = new Intent(ACTION_ALL_GUIDES_RESULT);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_MY_GUIDES)) {
                SerializableJSONArray guides = null;
                guides = getMyGuides();

                Intent reply = new Intent(ACTION_MY_GUIDES_RESULT);
                reply.putExtra(GUIDES_RESULT, guides);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_NEAR_BY_GUIDES)) {
                getLocation(new IOnLocation() {
                    @Override
                    public void onLocation(Location location) {
                        if (location == null) {
                            // No place enabled
                            Intent reply = new Intent(ACTION_NEAR_BY_GUIDES_RESULT);
                            reply.putExtra(GUIDES_RESULT, new SerializableJSONArray());
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

                        } else {
                            SerializableJSONArray guides = null;
                            try {
                                guides = getNearByGuides(location);
                            } catch (AuthenticationException e) {
                                Logger.tag(TAG).error(e);
                            }

                            Intent reply = new Intent(ACTION_NEAR_BY_GUIDES_RESULT);
                            reply.putExtra(GUIDES_RESULT, guides);
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
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
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

                        } else {
                            SerializableJSONArray projects = null;
                            try {
                                projects = getNearByProjects(location);
                            } catch (AuthenticationException e) {
                                Logger.tag(TAG).error(e);
                            }

                            Intent reply = new Intent(ACTION_NEARBY_PROJECTS_RESULT);
                            mApp.setServiceResult(ACTION_NEARBY_PROJECTS_RESULT, projects);
                            reply.putExtra(IS_SHARED_ON_APP, true);
                            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
                        }
                    }
                });

            } else if (action.equals(ACTION_GET_FEATURED_PROJECTS)) {
                SerializableJSONArray projects = getFeaturedProjects();

                Intent reply = new Intent(ACTION_FEATURED_PROJECTS_RESULT);
                reply.putExtra(PROJECTS_RESULT, projects);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_JOINED_PROJECTS_ONLINE)) {
                SerializableJSONArray projects = null;
                if (mCredentials != null) {
                    projects = getJoinedProjects();

                    if (projects != null) {
                        JSONArray arr = projects.getJSONArray();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject jsonProject = arr.optJSONObject(i);
                            try {
                                jsonProject.put("joined", true);
                                jsonProject.put("icon_url", jsonProject.optString("icon"));
                                arr.put(i, jsonProject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        projects = new SerializableJSONArray(arr);
                    }
                }

                Intent reply = new Intent(ACTION_JOINED_PROJECTS_RESULT);
                Logger.tag(TAG).debug("Joined projects: " + projects);
                if (projects != null) {
                    Logger.tag(TAG).debug("Joined projects 2: " + projects.getJSONArray().toString());
                }
                mApp.setServiceResult(ACTION_JOINED_PROJECTS_RESULT, projects);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_JOINED_PROJECTS)) {
                SerializableJSONArray projects = null;
                if (mCredentials != null) {
                    projects = getJoinedProjectsOffline();
                }

                if (projects != null) {
                    Logger.tag(TAG).debug("Joined projects offline: " + projects.getJSONArray().toString());
                }
                Intent reply = new Intent(ACTION_JOINED_PROJECTS_RESULT);
                reply.putExtra(PROJECTS_RESULT, projects);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_REDOWNLOAD_OBSERVATIONS_FOR_TAXON)) {
                redownloadOldObservationsForTaxonNames();

            } else if (action.equals(ACTION_DELETE_ACCOUNT)) {
                boolean success = deleteAccount();
                Intent reply = new Intent(DELETE_ACCOUNT_RESULT);
                reply.putExtra(SUCCESS, success);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_SYNC_JOINED_PROJECTS)) {
                saveJoinedProjects();

            } else if (action.equals(ACTION_GET_NOTIFICATION_COUNTS)) {
                BetterJSONObject notificationCounts = getNotificationCounts();

                Intent reply = new Intent(ACTION_NOTIFICATION_COUNTS_RESULT);
                reply.putExtra(NOTIFICATIONS, notificationCounts);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_POST_FLAG)) {
                String flaggableType = intent.getExtras().getString(FLAGGABLE_TYPE);
                Integer flaggableId = intent.getExtras().getInt(FLAGGABLE_ID);
                String flag = intent.getExtras().getString(FLAG);
                String flagExplanation = intent.getExtras().getString(FLAG_EXPLANATION);
                boolean success = postFlag(flaggableType, flaggableId, flag, flagExplanation);

                Intent reply = new Intent(ACTION_POST_FLAG_RESULT);
                reply.putExtra(SUCCESS, success);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_UNMUTE_USER)) {
                Integer userId = intent.getExtras().getInt(USER);
                boolean success = unmuteUser(userId);

                Intent reply = new Intent(ACTION_UNMUTE_USER_RESULT);
                reply.putExtra(SUCCESS, success);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_MUTE_USER)) {
                Integer userId = intent.getExtras().getInt(USER);
                boolean success = muteUser(userId);

                Intent reply = new Intent(ACTION_MUTE_USER_RESULT);
                reply.putExtra(SUCCESS, success);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_POST_MESSAGE)) {
                Integer toUser = intent.getExtras().getInt(TO_USER);
                Integer threadId = intent.getExtras().containsKey(THREAD_ID) ? intent.getExtras().getInt(THREAD_ID) : null;
                String subject = intent.getExtras().getString(SUBJECT);
                String body = intent.getExtras().getString(BODY);
                BetterJSONObject response = postMessage(toUser, threadId, subject, body);

                Intent reply = new Intent(ACTION_POST_MESSAGE_RESULT);
                mApp.setServiceResult(ACTION_POST_MESSAGE_RESULT, response);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_MESSAGES)) {
                String query = intent.getExtras() != null ? intent.getExtras().getString(QUERY) : null;
                String box = intent.getExtras() != null ? intent.getExtras().getString(BOX) : null;
                boolean groupByThreads = intent.getExtras() != null ? intent.getExtras().getBoolean(GROUP_BY_THREADS) : false;
                Integer messageId = (intent.getExtras() != null && intent.getExtras().containsKey(MESSAGE_ID)) ? intent.getExtras().getInt(MESSAGE_ID) : null;
                BetterJSONObject messages = getMessages(query, box, groupByThreads, messageId);
                messages = ObservationUtils.getMinimalMessagesResults(messages);

                Intent reply = new Intent(ACTION_MESSAGES_RESULT);
                mApp.setServiceResult(ACTION_MESSAGES_RESULT, messages);
                reply.putExtra(IS_SHARED_ON_APP, true);
                reply.putExtra(QUERY, query);
                reply.putExtra(MESSAGE_ID, messageId);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_OBSERVATION_SUBSCRIPTIONS)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                JSONArray subscriptions = getObservationSubscriptions(id);

                Intent reply = new Intent(ACTION_GET_OBSERVATION_SUBSCRIPTIONS_RESULT);
                reply.putExtra(RESULTS, new SerializableJSONArray(subscriptions));
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_FOLLOW_OBSERVATION)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                boolean success = followObservation(id);

                Intent reply = new Intent(ACTION_FOLLOW_OBSERVATION_RESULT);
                reply.putExtra(SUCCESS, success);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_FLAG_OBSERVATION_AS_CAPTIVE)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                flagObservationAsCaptive(id);

            } else if (action.equals(ACTION_GET_NEWS)) {
                SerializableJSONArray news = getNews();

                Intent reply = new Intent(ACTION_NEWS_RESULT);
                reply.putExtra(RESULTS, news);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_AND_SAVE_OBSERVATION)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                Pair<Observation, JSONObject> result = getAndDownloadObservation(id);

                Logger.tag(TAG).info("ACTION_GET_AND_SAVE_OBSERVATION - " + result);
                Intent reply = new Intent(ACTION_GET_AND_SAVE_OBSERVATION_RESULT);

                if (result != null) {
                    Observation observation = result.first;
                    JSONObject json = result.second;

                    Logger.tag(TAG).info("ACTION_GET_AND_SAVE_OBSERVATION 2 - " + result.first);
                    Logger.tag(TAG).info("ACTION_GET_AND_SAVE_OBSERVATION 3 - " + result.second);

                    Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[]{String.valueOf(observation.id)}, Observation.DEFAULT_SORT_ORDER);
                    if (c.getCount() > 0) {
                        Observation innerObs = new Observation(c);
                        observation._id = innerObs._id;
                        observation._synced_at = innerObs._synced_at;
                    }
                    c.close();

                    reply.putExtra(OBSERVATION_RESULT, observation);
                    reply.putExtra(OBSERVATION_JSON_RESULT, json.toString());
                } else {
                    reply.putExtra(OBSERVATION_RESULT, (Serializable)null);
                    reply.putExtra(OBSERVATION_JSON_RESULT, (String)null);
                }

                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);

            } else if (action.equals(ACTION_GET_OBSERVATION)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                boolean getProjects = intent.getExtras().getBoolean(GET_PROJECTS);
                JSONObject observationJson = getObservationJson(id, false, getProjects);

                Intent reply = new Intent(ACTION_OBSERVATION_RESULT + id);
                String jsonString = observationJson != null ? observationJson.toString() : null;
                Observation observation = observationJson == null ? null : new Observation(new BetterJSONObject(jsonString));

                mApp.setServiceResult(ACTION_OBSERVATION_RESULT + id, observation);
                mApp.setServiceResult(OBSERVATION_JSON_RESULT + id, observationJson != null ? jsonString : null);
                reply.putExtra(IS_SHARED_ON_APP, true);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);


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
                        mPreferences.edit().putLong("last_user_notifications_refresh_time", 0); // Force to refresh user notification counts
                    } else {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mContext.getApplicationContext(), R.string.could_not_download_observations, Toast.LENGTH_LONG).show();
                            }
                        });

                    }
                }

            } else if (action.equals(ACTION_DELETE_OBSERVATIONS)) {
                if (!mIsSyncing && !mApp.getIsSyncing()) {
                    long[] idsToDelete = null;
                    idsToDelete = intent.getExtras().getLongArray(OBS_IDS_TO_DELETE);

                    Logger.tag(TAG).debug("DeleteObservations: Calling delete obs");
                    mIsSyncing = true;
                    mApp.setIsSyncing(mIsSyncing);
                    deleteObservations(idsToDelete);
                } else {
                    // Already in middle of syncing
                    dontStopSync = true;
                }
            } else if (action.equals(ACTION_SYNC)) {
                if (!mIsSyncing && !mApp.getIsSyncing()) {
                    long[] idsToSync = null;

                    if (intent.hasExtra(OBS_IDS_TO_SYNC)) {
                        idsToSync = intent.getExtras().getLongArray(OBS_IDS_TO_SYNC);
                    }

                    mIsSyncing = true;
                    mApp.setIsSyncing(mIsSyncing);
                    syncObservations(idsToSync);

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
            Logger.tag(TAG).error(e);
            mIsSyncing = false;
        } catch (CancelSyncException e) {
            cancelSyncRequested = true;
            mApp.setCancelSync(false);
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

        } catch (SyncFailedException e) {
            syncFailed = true;
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

        } catch (AuthenticationException e) {
            syncFailed = true;
            if (!mPassive) {
                requestCredentials();
            }
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);
        } finally {
            mApp.setObservationIdBeingSynced(INaturalistApp.NO_OBSERVATION);

            if (mIsSyncing && !dontStopSync && (action.equals(ACTION_SYNC) || action.equals(ACTION_FIRST_SYNC) || action.equals(ACTION_PULL_OBSERVATIONS) || action.equals(ACTION_DELETE_OBSERVATIONS))) {
                mIsSyncing = false;
                mApp.setIsSyncing(mIsSyncing);

                Logger.tag(TAG).info("Sending ACTION_SYNC_COMPLETE");

                // Notify the rest of the app of the completion of the sync
                Intent reply = new Intent(ACTION_SYNC_COMPLETE);
                reply.putExtra(SYNC_CANCELED, cancelSyncRequested);
                reply.putExtra(SYNC_FAILED, syncFailed);
                reply.putExtra(FIRST_SYNC, action.equals(ACTION_FIRST_SYNC));
                reply.putExtra(SYNC_ERRORS, mResponseErrors != null ? mResponseErrors.toString() : null);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
            }
        }
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

        @SuppressLint("MissingPermission") Location location = locationManager.getLastKnownLocation(provider);
        Logger.tag(TAG).error("getLocationFromGPS: " + location);

        mLastLocation = location;

        return location;
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocationFromClient() {
        Location location = null;

        try {
            location = LocationServices.FusedLocationApi.getLastLocation(mLocationClient);
        } catch (IllegalStateException ex) {
            Logger.tag(TAG).error(ex);
        }

        Logger.tag(TAG).error("getLastKnownLocationFromClient: " + location);
        if (location == null) {
            // Failed - try and return last place using GPS
            return getLocationFromGPS();
        } else {
            mLastLocation = location;
            return location;
        }
    }

    private void getLocation(final IOnLocation callback) {
        if (!mApp.isLocationEnabled(null)) {
            Logger.tag(TAG).error("getLocation: Location not enabled");
            // Location not enabled
            new Thread(new Runnable() {
                @Override
                public void run() {
                    callback.onLocation(null);
                }
            }).start();
            return;
        }

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext.getApplicationContext());

        Logger.tag(TAG).error("getLocation: resultCode = " + resultCode);

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
                mLocationClient = new GoogleApiClient.Builder(mContext)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
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
                        .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
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

    public static String getUserAgent(Context context) {
        PackageInfo info = null;
        try {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.tag(TAG).error(e);
        }

        String userAgent = USER_AGENT.replace("%BUILD%", info != null ? String.valueOf(info.versionCode) : String.valueOf(INaturalistApp.VERSION));
        userAgent = userAgent.replace("%VERSION%", info != null ? info.versionName : String.valueOf(INaturalistApp.VERSION));
        // Remove non-ASCII characters (invalid for HTTP headers)
        userAgent = userAgent.replaceAll("[^\\x00-\\x7F]", "");

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


    private boolean deletePinnedLocation(String id) throws AuthenticationException {
        JSONArray result = delete(String.format(Locale.ENGLISH, "%s/saved_locations/%s", API_V2_HOST, id), null);

        if (result != null) {
            return true;
        } else {
            return false;
        }
    }

    private boolean pinLocation(Double latitude, Double longitude, Double accuracy, String geoprivacy, String title) throws AuthenticationException {

        try {
            JSONObject jsonBody = new JSONObject();
            JSONObject savedLocation = new JSONObject();
            savedLocation.put("latitude", latitude);
            savedLocation.put("longitude", longitude);
            savedLocation.put("positional_accuracy", accuracy.intValue());
            savedLocation.put("geoprivacy", geoprivacy);
            savedLocation.put("title", title);
            jsonBody.put("saved_location", savedLocation);
            JSONArray result = post(API_V2_HOST + "/saved_locations", jsonBody);

            if (result != null) {
                return true;
            } else {
                return false;
            }

        } catch (JSONException e) {
            return false;
        }

    }

    private void syncObservations(long[] idsToSync) throws AuthenticationException, CancelSyncException, SyncFailedException {
        try {
            Logger.tag(TAG).debug("syncObservations: enter");

            JSONObject eventParams = new JSONObject();
            eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, mApp.getAutoSync() ? AnalyticsClient.EVENT_VALUE_AUTOMATIC_UPLOAD : AnalyticsClient.EVENT_VALUE_MANUAL_FULL_UPLOAD);
            Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "is_deleted = 1 AND user_login = '" + mLogin + "'",
                    null,
                    Observation.DEFAULT_SORT_ORDER);

            eventParams.put(AnalyticsClient.EVENT_PARAM_NUM_DELETES, c.getCount());
            Logger.tag(TAG).debug("syncObservations: to be deleted: " + c.getCount());
            c.close();

            c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "(_updated_at > _synced_at AND _synced_at IS NOT NULL AND user_login = '" + mLogin + "') OR " +
                            "(id IS NULL AND _updated_at > _created_at)",
                    null,
                    Observation.SYNC_ORDER);
            eventParams.put(AnalyticsClient.EVENT_PARAM_NUM_UPLOADS, c.getCount());
            Logger.tag(TAG).debug("syncObservations: uploads: " + c.getCount());
            c.close();

            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_OBS, eventParams);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

        if (idsToSync == null) {
            mApp.notify(mContext.getString(R.string.preparing), mContext.getString(R.string.preparing));

            Logger.tag(TAG).debug("syncObservations: Calling syncRemotelyDeletedObs");
            if (!syncRemotelyDeletedObs()) throw new SyncFailedException();

            // First, download remote observations (new/updated)
            Logger.tag(TAG).debug("syncObservations: Calling getUserObservations");
            if (!getUserObservations(0)) throw new SyncFailedException();

            Logger.tag(TAG).debug("syncObservations: After calling getUserObservations");
        } else {
            mProjectObservations = new ArrayList<SerializableJSONArray>();
            mProjectFieldValues = new Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>>();
        }

        Set<Integer> observationIdsToSync = new HashSet<>();
        Cursor c;

        if (idsToSync != null) {
            // User chose to sync specific observations only
            for (long id : idsToSync) {
                observationIdsToSync.add(Integer.valueOf((int)id));
            }

            Logger.tag(TAG).debug("syncObservations: observationIdsToSync multi-selection: " + observationIdsToSync);
        } else {
            // Gather the list of observations that need syncing (because they're new, been updated
            // or had their photos/project fields updated
            // Any new/updated observations
            c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "(_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL)",
                    null,
                    Observation.DEFAULT_SORT_ORDER);

            c.moveToFirst();
            while (!c.isAfterLast()) {
                Integer internalId = c.getInt(c.getColumnIndexOrThrow(Observation._ID));

                // Make sure observation is not currently being edited by user (split-observation bug)
                observationIdsToSync.add(internalId);
                c.moveToNext();
            }

            c.close();
            Logger.tag(TAG).debug("syncObservations: observationIdsToSync: " + observationIdsToSync);

            // Any observation that has new/updated/deleted photos
            c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "(_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL) OR " +
                            "(is_deleted = 1)",
                    null,
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            c.moveToFirst();
            while (!c.isAfterLast()) {
                int internalObsId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));

                Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "_id = ?",
                        new String[] { String.valueOf(internalObsId) },
                        Observation.DEFAULT_SORT_ORDER);
                int obsCount = obsc.getCount();
                obsc.close();

                if (obsCount == 0) {
                    // Observation photo belongs to an observation that no longer exists - delete it
                    int obsPhotoId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto._ID));
                    Logger.tag(TAG).error("Observation photo " + obsPhotoId + " belongs to an observation that no longer exists: " + internalObsId + " - deleting it");
                    mContext.getContentResolver().delete(
                            ContentUris.withAppendedId(ObservationPhoto.CONTENT_URI, obsPhotoId),
                            null, null);
                } else {
                    observationIdsToSync.add(internalObsId);
                }

                c.moveToNext();
            }

            c.close();

            Logger.tag(TAG).debug("syncObservations: observationIdsToSync 2: " + observationIdsToSync);

            // Any observation that has new/deleted sounds
            c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                    ObservationSound.PROJECTION,
                    "(id IS NULL) OR (is_deleted = 1)",
                    null,
                    ObservationSound.DEFAULT_SORT_ORDER);

            c.moveToFirst();
            while (!c.isAfterLast()) {
                observationIdsToSync.add(c.getInt(c.getColumnIndexOrThrow(ObservationSound._OBSERVATION_ID)));
                c.moveToNext();
            }

            c.close();


            Logger.tag(TAG).debug("syncObservations: observationIdsToSync 2b: " + observationIdsToSync);


            // Any observation that has new/updated project fields.
            c = mContext.getContentResolver().query(ProjectFieldValue.CONTENT_URI,
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
        }
        Logger.tag(TAG).debug("syncObservations: observationIdsToSync 3: " + observationIdsToSync);

        List<Integer> obsIdsToRemove = new ArrayList<>();

        for (Integer obsId : observationIdsToSync) {
            c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "_id = " + obsId,
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

        Logger.tag(TAG).debug("syncObservations: obsIdsToRemove: " + obsIdsToRemove);

        for (Integer obsId : observationIdsToSync) {
            // Make sure observation is not currently being edited by user (split-observation bug)
            if (mApp.isObservationCurrentlyBeingEdited(obsId)) {
                Logger.tag(TAG).error("syncObservations: Observation " + obsId + " is currently being edited - not syncing it");
                obsIdsToRemove.add(obsId);
            }
        }

        Logger.tag(TAG).debug("syncObservations: obsIdsToRemove 2: " + obsIdsToRemove);

        for (Integer obsId : obsIdsToRemove) {
            observationIdsToSync.remove(obsId);
        }

        Logger.tag(TAG).debug("syncObservations: observationIdsToSync: " + observationIdsToSync);

        c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                Observation.PROJECTION,
                "_id in (" + StringUtils.join(observationIdsToSync, ",") + ")",
                null,
                Observation.DEFAULT_SYNC_ORDER);

        c.moveToFirst();

        while (!c.isAfterLast()) {
            int totalObs = c.getCount();
            mApp.notify(mContext.getString(R.string.syncing_observations),
                    mContext.getResources().getQuantityString(
                            R.plurals.syncing_x_out_of_y_observations,
                            totalObs,
                            (c.getPosition() + 1),
                            totalObs
                    )
            );

            Observation observation = new Observation(c);

            // Make sure observation is not currently being edited by user (split-observation bug)
            if (mApp.isObservationCurrentlyBeingEdited(observation._id)) {
                Logger.tag(TAG).error("syncObservations: Observation " + observation._id + " is currently being edited - not syncing it");
                continue;
            }

            mCurrentObservationProgress = 0.0f;
            mTotalProgressForObservation = getTotalProgressForObservation(observation);
            increaseProgressForObservation(observation);

            mApp.setObservationIdBeingSynced(observation._id);
            try {
                Logger.tag(TAG).debug("syncObservations: Syncing " + observation._id + ": " + observation.toString());

                if ((observation._synced_at == null) || ((observation._updated_at != null) && (observation._updated_at.after(observation._synced_at))) || (observation.id == null)) {
                    postObservation(observation);
                    increaseProgressForObservation(observation);
                }
                Logger.tag(TAG).debug("syncObservations: Finished Syncing " + observation._id + " - now uploading photos");

                postPhotos(observation);
                Logger.tag(TAG).debug("syncObservations: Finished uploading photos " + observation._id);
                deleteObservationPhotos(observation); // Delete locally-removed observation photos

                postSounds(observation);
                Logger.tag(TAG).debug("syncObservations: Finished uploading sounds " + observation._id);
                deleteObservationSounds(observation); // Delete locally-removed observation sounds

                syncObservationFields(observation);
                postProjectObservations(observation);
                Logger.tag(TAG).debug("syncObservations: Finished delete photos, obs fields and project obs - " + observation._id);
            } catch (SyncFailedException exc) {
                Logger.tag(TAG).error("Sync Failed for observation");
            }

            c.moveToNext();
        }

        c.close();

        if (idsToSync == null) {
            Logger.tag(TAG).debug("syncObservations: Calling delete obs");
            deleteObservations(null); // Delete locally-removed observations

            Logger.tag(TAG).debug("syncObservations: Calling saveJoinedProj");
            // General project data
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    mContext.getString(R.string.projects),
                    mContext.getString(R.string.cleaning_up),
                    mContext.getString(R.string.syncing));

            saveJoinedProjects();
            Logger.tag(TAG).debug("syncObservations: Calling storeProjObs");
            storeProjectObservations();

            redownloadOldObservations();
        }

        Logger.tag(TAG).debug("syncObservations: Done");
        mPreferences.edit().putLong("last_user_details_refresh_time", 0); // Force to refresh user details
        mPreferences.edit().putLong("last_user_notifications_refresh_time", 0); // Force to refresh user notification counts
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
        reply.putExtra(OBSERVATION_ID_INTERNAL, observation._id);
        reply.putExtra(PROGRESS, newProgress);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
    }

    // Adds observation UUIDs to photos and sounds that are missing them
    private void addObservationUUIDsToPhotosAndSounds() {
        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                new String[] { ObservationPhoto._OBSERVATION_ID, ObservationPhoto._ID, ObservationPhoto.ID },
                "observation_uuid is NULL",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);
        int count = c.getCount();

        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "addObservationUUIDsToPhotosAndSounds: Adding UUIDs to %d photos", count));

        c.moveToFirst();
        while (!c.isAfterLast()) {
            int obsInternalId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
            int obsPhotoId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto.ID));
            int obsPhotoInternalId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto._ID));

            Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    new String[] { Observation.UUID },
                    "_id = " + obsInternalId, null, Observation.DEFAULT_SORT_ORDER);
            if (obsc.getCount() > 0) {
                obsc.moveToFirst();
                String uuid = obsc.getString(obsc.getColumnIndexOrThrow(Observation.UUID));
                ContentValues cv = new ContentValues();
                cv.put(ObservationPhoto.OBSERVATION_UUID, uuid);
                // Update its sync at time so we won't update the remote servers later on (since we won't
                // accidentally consider this an updated record)
                cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                Uri photoUri = ContentUris.withAppendedId(ObservationPhoto.CONTENT_URI, obsPhotoInternalId);
                mContext.getContentResolver().update(photoUri, cv, null, null);

                Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "addObservationUUIDsToPhotosAndSounds - Adding observation_uuid %s to photo: id = %d; _id: %d", uuid, obsPhotoId, obsPhotoInternalId));
            }
            obsc.close();
            c.moveToNext();
        }
        c.close();


        c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                ObservationSound.PROJECTION,
                "observation_uuid is NULL",
                null,
                ObservationSound.DEFAULT_SORT_ORDER);
        count = c.getCount();

        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "addObservationUUIDsToPhotosAndSounds: Adding UUIDs to %d sounds", count));

        c.moveToFirst();
        while (!c.isAfterLast()) {
            ObservationSound sound = new ObservationSound(c);

            Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    new String[] { Observation.UUID },
                    "id = " + sound.observation_id, null, Observation.DEFAULT_SORT_ORDER);
            if (obsc.getCount() > 0) {
                obsc.moveToFirst();
                String uuid = obsc.getString(obsc.getColumnIndexOrThrow(Observation.UUID));
                sound.observation_uuid = uuid;
                ContentValues cv = sound.getContentValues();
                mContext.getContentResolver().update(sound.getUri(), cv, null, null);

                Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "addObservationUUIDsToPhotosAndSounds - Adding observation_uuid %s to sound: %s", uuid, sound));
            }
            obsc.close();
            c.moveToNext();
        }
        c.close();
    }

    private int getTotalProgressForObservation(Observation observation) {
        int obsCount = 0;
        if ((observation._synced_at == null) || ((observation._updated_at != null) && (observation._updated_at.after(observation._synced_at)))) {
            obsCount = 1;
        }

        int photoCount;
        int externalObsId = observation.id != null ? observation.id : observation._id;

        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "((_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL) OR (is_deleted = 1)) AND " +
                        "((observation_id = ?) OR (_observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ObservationPhoto.DEFAULT_SORT_ORDER);
        photoCount = c.getCount();
        c.close();

        int projectFieldCount;
        c = mContext.getContentResolver().query(ProjectFieldValue.CONTENT_URI,
                ProjectFieldValue.PROJECTION,
                "((_synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL)) AND " +
                        "((observation_id = ?) OR (observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ProjectFieldValue.DEFAULT_SORT_ORDER);
        projectFieldCount = c.getCount();
        c.close();

        int projectObservationCount;
        c = mContext.getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "((is_deleted = 1) OR (is_new = 1)) AND " +
                        "((observation_id = ?) OR (observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ProjectObservation.DEFAULT_SORT_ORDER);
        projectObservationCount = c.getCount();
        c.close();

        int soundCount;
        c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                ObservationSound.PROJECTION,
                "((is_deleted = 1) OR (id is NULL)) AND " +
                        "((observation_id = ?) OR (_observation_id = ?))",
                new String[]{String.valueOf(externalObsId), String.valueOf(observation._id)},
                ObservationSound.DEFAULT_SORT_ORDER);
        soundCount = c.getCount();
        c.close();


        return 1 + // We start off with some progress (one "part")
                obsCount + // For the observation upload itself (only if new/update)
                photoCount + // For photos
                soundCount + // For sounds
                projectFieldCount + // For updated/new obs project fields
                projectObservationCount; // For updated/new observation project fields
    }

    // Re-download old local observations and update their taxon names (preferred common names) - used when user switches language
    private void redownloadOldObservationsForTaxonNames() throws AuthenticationException {

        // Get most recent observation
        Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                Observation.PROJECTION,
                "(id IS NOT NULL)",
                null,
                "id DESC");
        c.moveToFirst();

        if (c.getCount() == 0) {
            c.close();
            return;
        }

        Integer currentObsId = c.getInt(c.getColumnIndexOrThrow(Observation.ID)) + 1;
        c.moveToLast();
        Integer lastObsId = c.getInt(c.getColumnIndexOrThrow(Observation.ID));
        c.close();
        JSONArray results = null;
        int obsCount = 0;

        do {
            Logger.tag(TAG).debug("redownloadOldObservationsForTaxonNames: " + currentObsId);
            String url = API_HOST + "/observations?user_id=" + Uri.encode(mLogin) + "&per_page=100&id_below=" + currentObsId;
            url += "&locale=" + mApp.getPrefLocale();

            JSONArray json = get(url, true);
            Logger.tag(TAG).debug("redownloadOldObservationsForTaxonNames - downloaded");
            if (json == null || json.length() == 0) {
                break;
            }
            Logger.tag(TAG).debug("redownloadOldObservationsForTaxonNames - downloaded 2");
            results = json.optJSONObject(0).optJSONArray("results");

            Logger.tag(TAG).debug("redownloadOldObservationsForTaxonNames - downloaded 3");
            for (int i = 0; i < results.length(); i++) {
                JSONObject currentObs = results.optJSONObject(i);
                int currentId = currentObs.optInt("id");
                c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "id = ?",
                        new String[]{String.valueOf(currentId)},
                        Observation.DEFAULT_SORT_ORDER);
                Logger.tag(TAG).debug("redownloadOldObservationsForTaxonNames - Updating taxon details for obs: " + currentId);
                if (c.getCount() == 0) {
                    c.close();
                    continue;
                }

                // Update current observation's taxon preferred common name
                Observation obs = new Observation(c);
                c.close();
                obs.setPreferredCommonName(currentObs);
                Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "redownloadOldObservationsForTaxonNames - Common name for observation %d: %s", currentId, obs.preferred_common_name));
                ContentValues cv = obs.getContentValues();

                if (!obs._updated_at.after(obs._synced_at)) {
                    // Update its sync at time so we won't update the remote servers later on (since we won't
                    // accidentally consider this an updated record)
                    cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                }
                mContext.getContentResolver().update(obs.getUri(), cv, null, null);

                currentObsId = obs.id;
                obsCount++;

                if (obsCount > MAX_OBSVERATIONS_TO_REDOWNLOAD) break;
            }
        } while ((results.length() > 0) && (currentObsId > lastObsId) && (obsCount <= MAX_OBSVERATIONS_TO_REDOWNLOAD));

        Logger.tag(TAG).debug("redownloadOldObservationsForTaxonNames - finished");
        mApp.setLastLocale();
    }

    // Re-download any observations that have photos saved in the "old" way
    private void redownloadOldObservations() throws AuthenticationException {

        // Find all observations that have photos saved in the old way
        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(photo_filename IS NULL) AND (photo_url IS NULL)",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        Logger.tag(TAG).debug("redownloadOldObservations: " + c.getCount());

        while (!c.isAfterLast()) {
            Integer obsId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto.OBSERVATION_ID));

            Logger.tag(TAG).debug("redownloadOldObservations: " + new ObservationPhoto(c));

            // Delete the observation photo
            Integer obsPhotoId = c.getInt(c.getColumnIndexOrThrow(ObservationPhoto.ID));
            mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "id = " + obsPhotoId, null);

            // Re-download this observation

            String url = API_HOST + "/observations?user_id=" + Uri.encode(mLogin) + "extra=observation_photos,projects,fields";
            url += "&locale=" + mApp.getPrefLocale();
            JSONArray json = get(url, true);
            if (json != null && json.length() > 0) {
                syncJson(json, true);
            }

            c.moveToNext();
        }

        c.close();

    }

    private BetterJSONObject getHistogram(int taxonId, boolean researchGrade) throws AuthenticationException {
        String url = String.format(Locale.ENGLISH, "%s/observations/histogram?taxon_id=%d&", API_HOST, taxonId);

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
        String url = String.format(Locale.ENGLISH, "%s/observations/popular_field_values?taxon_id=%d&verifiable=true", API_HOST, taxonId);

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

    private BetterJSONObject getTaxonSuggestions(String photoFilename, Double latitude, Double longitude, Timestamp observedOn, String suggestionSource, Integer placeId, Integer taxonId, Double placeLat, Double placeLng, Integer limit, Integer page) throws AuthenticationException {
        String date = observedOn != null ? new SimpleDateFormat("yyyy-MM-dd").format(observedOn) : null;
        ArrayList<Pair<String, String>> params = new ArrayList<>();
        String url = String.format(Locale.ENGLISH, API_HOST + "/taxa/suggest");

        if (limit != null) params.add(new Pair("limit", limit.toString()));
        if (page != null) params.add(new Pair("page", page.toString()));
        params.add(new Pair("locale", mApp.getPrefLocale()));
        if (date != null) params.add(new Pair("observed_on", date));
        boolean isOnline = false;
        if (suggestionSource.equals(SUGGESTION_SOURCE_VISUAL)) {
            if (photoFilename.startsWith("http://") || photoFilename.startsWith("https://")) {
                // Online photo
                isOnline = true;
                params.add(new Pair("image_url", photoFilename));
            } else {
                // Local filename
                params.add(new Pair("image", photoFilename));
            }
        }
        params.add(new Pair("source", suggestionSource));
        if (placeId != null) {
            params.add(new Pair("place_id", placeId.toString()));
        }
        if (taxonId != null) {
            params.add(new Pair("taxon_id", taxonId.toString()));
        }
        if (suggestionSource.equals(SUGGESTION_SOURCE_VISUAL)) {
            params.add(new Pair("lat", latitude.toString()));
            params.add(new Pair("lng", longitude.toString()));
        } else {
            if (placeLat != null) {
                params.add(new Pair("place_lat", placeLat.toString()));
            }
            if (placeLng != null) {
                params.add(new Pair("place_lng", placeLng.toString()));
            }
        }


        JSONArray json;
        if (isOnline) {
            json = request(url, "get", params, null, true, true, true);
        } else {
            json = request(url, "post", params, null, true, true, true);
        }
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
        String url = API_HOST + "/taxa/" + id + "?locale=" + mApp.getPrefLocale();

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
            Logger.tag(TAG).error(e);

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
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
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
        String url = String.format(Locale.ENGLISH, "%s/votes/unvote/observation/%d?id=%d&scope=needs_id", API_HOST, obsId, obsId);

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
        String url = String.format(Locale.ENGLISH, "%s/observations/%d/quality_metrics?id=%d", API_HOST, observationId, observationId);

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
        String url = String.format(Locale.ENGLISH, "%s/observations/%d/quality/%s", API_HOST, observationId, metric);

        JSONObject params = new JSONObject();

        try {
            params.put("agree", agree ? "true" : "false");
            params.put("id", observationId);
            params.put("metric", metric);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
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
        String url = String.format(Locale.ENGLISH, "%s/observations/%d/quality/%s?id=%d&metric=%s", API_HOST, observationId, metric, observationId, metric);

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
        String url = API_HOST + "/controlled_terms?locale=" + mApp.getPrefLocale();

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

    private BetterJSONObject getAttributesForTaxon(JSONArray ancestors, Integer taxonId) throws AuthenticationException {
        String url;
        if (ancestors != null) {
            String ancestry = "";

            for (int i = 0; i < ancestors.length(); i++) {
                int currentTaxonId = ancestors.optInt(i);
                ancestry += String.format(Locale.ENGLISH, "%d,", currentTaxonId);
            }
            ancestry += String.format(Locale.ENGLISH, "%d", taxonId);
            url = API_HOST + "/controlled_terms/for_taxon?taxon_id=" + ancestry + "&ttl=-1&locale=" + mApp.getPrefLocale();

        } else {
            url = API_HOST + "/controlled_terms?ttl=-1&locale=" + mApp.getPrefLocale();
        }

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
        String url = String.format(Locale.ENGLISH, "%s/taxa/%d?locale=%s", API_HOST, id, mApp.getPrefLocale());

        JSONArray json = get(url);
        if (json == null || json.length() == 0) {
            return null;
        }

        JSONObject res;

        try {
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            if (results.length() == 0) return null;

            return new BetterJSONObject(results.getJSONObject(0));
        } catch (JSONException e) {
            return null;
        }
    }

    private boolean postProjectObservations(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        if (observation.id == null) {
            // Observation not synced yet - cannot sync its project associations yet
            return true;
        }

        // First, delete any project-observations that were deleted by the user
        Cursor c = mContext.getContentResolver().query(ProjectObservation.CONTENT_URI,
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
                BetterJSONObject result = removeObservationFromProject(projectObservation.id, projectObservation.observation_id, projectObservation.project_id);
                if ((result == null) && (mLastStatusCode != HTTP_OK)) {
                    c.close();
                    throw new SyncFailedException();
                }

                increaseProgressForObservation(observation);
            } catch (Exception exc) {
                // In case we're trying to delete a project-observation that wasn't synced yet
                c.close();
                throw new SyncFailedException();
            }

            mContext.getContentResolver().delete(ProjectObservation.CONTENT_URI, "_id = ?", new String[]{String.valueOf(projectObservation._id)});

            c.moveToNext();
        }

        if (!c.isClosed()) c.close();

        // Next, add new project observations
        c = mContext.getContentResolver().query(ProjectObservation.CONTENT_URI,
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
                // Save external ID
                projectObservation.id = result.getInt("id");
                ContentValues cv = projectObservation.getContentValues();
                mContext.getContentResolver().update(projectObservation.getUri(), cv, null, null);

                // Clean the errors for the observation
                mApp.setErrorsForObservation(projectObservation.observation_id, projectObservation.project_id, new JSONArray());
            }

            c.moveToNext();
        }

        if (!c.isClosed()) c.close();

        return true;
    }

    private boolean postProjectObservations() throws AuthenticationException, CancelSyncException, SyncFailedException {
        // First, delete any project-observations that were deleted by the user
        Cursor c = mContext.getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "is_deleted = 1",
                null,
                ProjectObservation.DEFAULT_SORT_ORDER);

        if (c.getCount() > 0) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    mContext.getString(R.string.projects),
                    mContext.getString(R.string.syncing_observation_fields),
                    mContext.getString(R.string.syncing));
        }

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectObservation projectObservation = new ProjectObservation(c);

            // Clean the errors for the observation
            mApp.setErrorsForObservation(projectObservation.observation_id, projectObservation.project_id, new JSONArray());

            try {
                // Remove obs from project
                BetterJSONObject result = removeObservationFromProject(projectObservation.id, projectObservation.observation_id, projectObservation.project_id);
                if ((result == null) && (mLastStatusCode != HTTP_OK)) {
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
        mContext.getContentResolver().delete(ProjectObservation.CONTENT_URI, "is_deleted = 1", null);


        // Next, add new project observations
        c = mContext.getContentResolver().query(ProjectObservation.CONTENT_URI,
                ProjectObservation.PROJECTION,
                "is_new = 1",
                null,
                ProjectObservation.DEFAULT_SORT_ORDER);

        if (c.getCount() > 0) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    mContext.getString(R.string.projects),
                    mContext.getString(R.string.syncing_observation_fields),
                    mContext.getString(R.string.syncing));
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
                // Save external ID
                projectObservation.id = result.getInt("id");
                ContentValues cv = projectObservation.getContentValues();
                mContext.getContentResolver().update(projectObservation.getUri(), cv, null, null);

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

        Cursor c2 = mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = '" + observationId + "'", null, Observation.DEFAULT_SORT_ORDER);
        c2.moveToFirst();
        if (c2.getCount() == 0) {
            c2.close();
            return false;
        }
        Observation observation = new Observation(c2);
        c2.close();

        c2 = mContext.getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '" + projectId + "'", null, Project.DEFAULT_SORT_ORDER);
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
                formattedErrors.put(String.format(Locale.ENGLISH, mContext.getString(R.string.failed_to_add_to_project), project.title, unformattedErrors.getString(i)));
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }
        }

        mApp.setErrorsForObservation(observation.id, project.id, formattedErrors);

        final String errorMessage = String.format(Locale.ENGLISH, mContext.getString(R.string.failed_to_add_obs_to_project),
                observation.species_guess == null ? mContext.getString(R.string.unknown) : observation.species_guess, project.title, error);

        // Display toast in this main thread handler (since otherwise it won't get displayed)
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext.getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });

        try {
            JSONObject eventParams = new JSONObject();
            eventParams.put(AnalyticsClient.EVENT_PARAM_ALERT, errorMessage);

            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_SYNC_FAILED, eventParams);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
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
                    Cursor c = mContext.getContentResolver().query(ProjectObservation.CONTENT_URI,
                            ProjectObservation.PROJECTION,
                            "project_id = " + projectObservation.project_id + " AND observation_id = " + projectObservation.observation_id,
                            null, ProjectObservation.DEFAULT_SORT_ORDER);
                    if (c.getCount() == 0) {
                        mContext.getContentResolver().insert(ProjectObservation.CONTENT_URI, cv);
                    }
                    c.close();
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }
        }
    }

    private boolean deleteAccount() throws AuthenticationException {
        String username = mApp.currentUserLogin();

        JSONArray result = delete(
                String.format(Locale.ENGLISH, "%s/users/%s.json?confirmation_code=%s&confirmation=%s", HOST, username, username, username), null);

        if (result == null) {
            Logger.tag(TAG).debug("deleteAccount error: " + mLastStatusCode);
            return false;
        }

        return true;
    }

    private boolean saveJoinedProjects() throws AuthenticationException, CancelSyncException, SyncFailedException {
        SerializableJSONArray projects = getJoinedProjects();

        checkForCancelSync();

        if (projects == null) {
            throw new SyncFailedException();
        }

        JSONArray arr = projects.getJSONArray();

        // Delete all projects first
        try {
            int count = mContext.getContentResolver().delete(Project.CONTENT_URI, null, null);
        } catch (Exception exc) {
            Logger.tag(TAG).error(exc);
            throw new SyncFailedException();
        }

        // Add any newly-joined projects

        for (int i = 0; i < arr.length(); i++) {
            JSONObject jsonProject = arr.optJSONObject(i);
            Project project = new Project(new BetterJSONObject(jsonProject));
            ContentValues cv = project.getContentValues();
            mContext.getContentResolver().insert(Project.CONTENT_URI, cv);
        }

        return true;
    }

    private boolean deleteObservationSounds(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        // Remotely delete any locally-removed observation sounds
        Cursor c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                ObservationSound.PROJECTION,
                "is_deleted = 1 AND _observation_id = ?",
                new String[]{String.valueOf(observation._id)},
                ObservationSound.DEFAULT_SORT_ORDER);

        String inatNetwork = mApp.getInaturalistNetworkMember();

        // for each observation DELETE to /sounds/:id
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ObservationSound os = new ObservationSound(c);

            Logger.tag(TAG).debug("deleteObservationSounds: " + os);
            if (os.id != null) {
                Logger.tag(TAG).debug("deleteObservationSounds: Deleting " + os);
                JSONArray result = delete(API_HOST + "/observation_sounds/" + os.id, null);
                if ((result == null) && (mLastStatusCode != HTTP_OK)) {
                    Logger.tag(TAG).debug("deleteObservationSounds: Deletion error: " + mLastStatusCode);
                    if (mLastStatusCode != HTTP_NOT_FOUND) {
                        // Ignore the case where the sound was remotely deleted
                        Logger.tag(TAG).debug("deleteObservationSounds: Not a 404 error");
                        c.close();
                        throw new SyncFailedException();
                    }
                }
            }
            increaseProgressForObservation(observation);

            int count = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI,
                    "id = ? or _id = ?", new String[]{String.valueOf(os.id), String.valueOf(os._id)});
            Logger.tag(TAG).debug("deleteObservationSounds: Deleted from DB: " + count);
            c.moveToNext();
        }

        c.close();


        checkForCancelSync();

        return true;
    }

    private boolean deleteObservationPhotos(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        // Remotely delete any locally-removed observation photos
        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "is_deleted = 1 AND _observation_id = ?",
                new String[]{String.valueOf(observation._id)},
                ObservationPhoto.DEFAULT_SORT_ORDER);

        // for each observation DELETE to /observation_photos/:id
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ObservationPhoto op = new ObservationPhoto(c);

            Logger.tag(TAG).debug("deleteObservationPhotos: " + op + "::::" + op._synced_at);
            if (op._synced_at != null) {
                if (op.id != null) {
                    Logger.tag(TAG).debug("deleteObservationPhotos: Deleting " + op);
                    JSONArray result = delete(API_HOST + "/observation_photos/" + op.id, null);
                    if ((result == null) && (mLastStatusCode != HTTP_OK)) {
                        Logger.tag(TAG).debug("deleteObservationPhotos: Deletion error: " + mLastStatusCode);
                        if (mLastStatusCode != HTTP_NOT_FOUND) {
                            // Ignore the case where the photo was remotely deleted
                            Logger.tag(TAG).debug("deleteObservationPhotos: Not a 404 error");
                            c.close();
                            throw new SyncFailedException();
                        }
                    }
                }
            }
            increaseProgressForObservation(observation);

            int count = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI,
                    "id = ? or _id = ?", new String[]{String.valueOf(op.id), String.valueOf(op._id)});
            Logger.tag(TAG).debug("deleteObservationPhotos: Deleted from DB: " + count);
            c.moveToNext();
        }

        c.close();


        checkForCancelSync();

        return true;
    }

    private boolean deleteObservations(long[] idsToDelete) throws AuthenticationException, CancelSyncException, SyncFailedException {
        Cursor c;

        if (idsToDelete != null) {
            // Remotely delete selected observations only
            c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "_id in (" + StringUtils.join(ArrayUtils.toObject(idsToDelete), ",") + ")",
                    null,
                    Observation.DEFAULT_SORT_ORDER);
        } else {
            // Remotely delete any locally-removed observations (marked for deletion)
            c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "is_deleted = 1",
                    null,
                    Observation.DEFAULT_SORT_ORDER);
        }

        Logger.tag(TAG).debug("deleteObservations: Deleting " + c.getCount());

        if (c.getCount() > 0) {
            mApp.notify(mContext.getString(R.string.deleting_observations), mContext.getString(R.string.deleting_observations));
        }

        // for each observation DELETE to /observations/:id
        ArrayList<Integer> obsIds = new ArrayList<Integer>();
        ArrayList<String> obsUUIDs = new ArrayList<String>();
        ArrayList<Integer> internalObsIds = new ArrayList<Integer>();
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            Observation observation = new Observation(c);
            Logger.tag(TAG).debug("deleteObservations: Deleting " + observation);
            JSONArray results = delete(API_HOST + "/observations/" + observation.id, null);
            if ((results == null) && (mLastStatusCode != HTTP_OK)) {
                c.close();
                throw new SyncFailedException();
            }

            obsIds.add(observation.id);
            obsUUIDs.add('"' + observation.uuid + '"');
            internalObsIds.add(observation._id);
            c.moveToNext();
        }

        Logger.tag(TAG).debug("deleteObservations: Deleted IDs: " + obsIds);
        c.close();

        // Now it's safe to delete all of the observations locally
        mContext.getContentResolver().delete(Observation.CONTENT_URI, "is_deleted = 1", null);
        // Delete associated project-fields and photos
        int count1 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count2 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_uuid in (" + StringUtils.join(obsUUIDs, ",") + ")", null);
        int count3 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count4 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_uuid in (" + StringUtils.join(obsUUIDs, ",") + ")", null);
        int count5 = mContext.getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count6 = mContext.getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count7 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_observation_id in (" + StringUtils.join(internalObsIds, ",") + ")", null);
        int count8 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "_observation_id in (" + StringUtils.join(internalObsIds, ",") + ")", null);

        Logger.tag(TAG).debug("deleteObservations: " + count1 + ":" + count2 + ":" + count3 + ":" + count4 + ":" + count5 + ":" + count6 + ":" + count7 + ":" + count8);


        checkForCancelSync();

        return true;
    }

    private void checkForCancelSync() throws CancelSyncException {
        if (mApp.getCancelSync()) throw new CancelSyncException();
    }


    private JSONObject removeFavorite(int observationId) throws AuthenticationException {
        ArrayList<Pair<String, String>> params = new ArrayList<Pair<String, String>>();
        JSONArray result = delete(API_HOST + "/observations/" + observationId + "/unfave", null);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
                return null;
            }
        } else {
            return null;
        }
    }

    private JSONObject addFavorite(int observationId) throws AuthenticationException {
        JSONArray result = post(API_HOST + "/observations/" + observationId + "/fave", (JSONObject) null);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
                return null;
            }
        } else {
            return null;
        }
    }

    private JSONObject removeIdentification(int identificationId) throws AuthenticationException {
        ArrayList<Pair<String, String>> params = new ArrayList<Pair<String, String>>();
        JSONArray result = delete(API_HOST + "/identifications/" + identificationId, null);

        if (result != null) {
            try {
                return result.getJSONObject(0);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
                return null;
            }
        } else {
            return null;
        }
    }

    private void setUserViewedUpdate(int obsId) throws AuthenticationException {
        put(API_HOST + "/observations/" + obsId + "/viewed_updates", (JSONObject) null);
    }

    private void restoreIdentification(int identificationId) throws AuthenticationException {
        JSONObject paramsJson = new JSONObject();
        JSONObject paramsJsonIdentification = new JSONObject();
        try {
            paramsJsonIdentification.put("current", true);
            paramsJson.put("identification", paramsJsonIdentification);

            JSONArray arrayResult = put(API_HOST + "/identifications/" + identificationId, paramsJson);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }
    }

    private void updateIdentification(int observationId, int identificationId, int taxonId, String body) throws AuthenticationException {
        JSONObject params = new JSONObject();
        JSONObject id = new JSONObject();
        try {
            id.put("observation_id", observationId);
            id.put("taxon_id", taxonId);
            id.put("body", body);

            params.put("identification", id);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        put(API_HOST + "/identifications/" + identificationId, params);
    }

    private void addIdentification(int observationId, int taxonId, String body, boolean disagreement, boolean fromVision) throws AuthenticationException {
        JSONObject params = new JSONObject();
        JSONObject id = new JSONObject();
        try {
            id.put("observation_id", observationId);
            id.put("taxon_id", taxonId);
            if (body != null) id.put("body", body);
            id.put("disagreement", disagreement);
            id.put("vision", fromVision);

            params.put("identification", id);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JSONArray arrayResult = post(API_HOST + "/identifications", params);
    }

    // Updates a user's inat network settings
    private JSONObject updateUserTimezone(String timezone) throws AuthenticationException {
        ArrayList<Pair<String, String>> params = new ArrayList<Pair<String, String>>();
        params.add(new Pair("user[time_zone]", timezone));

        try {
            JSONObject paramsJson = new JSONObject();
            JSONObject userJson = new JSONObject();
            userJson.put("time_zone", timezone);
            paramsJson.put("user", userJson);
            JSONArray array = put(API_HOST + "/users/" + mLogin, paramsJson);
            if ((mResponseErrors != null) || (array == null)) {
                // Couldn't update user
                return null;
            } else {
                return array.optJSONObject(0);
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    // Updates a user's inat network settings
    private JSONObject updateUserNetwork(int siteId) throws AuthenticationException {
        ArrayList<Pair<String, String>> params = new ArrayList<>();
        params.add(new Pair("user[site_id]", String.valueOf(siteId)));

        JSONArray array = put(API_HOST + "/users/" + mLogin, params);

        if ((mResponseErrors != null) || (array == null)) {
            // Couldn't update user
            return null;
        } else {
            return array.optJSONObject(0);
        }
    }

    // Updates a user's profile
    private JSONObject updateUser(String username, String email, String password, String fullName, String bio, String userPic, boolean deletePic, String license, String photoLicense, String soundLicense, boolean makeLicenseSame, boolean makePhotoLicenseSame, boolean makeSoundLicenseSame) throws AuthenticationException {
        ArrayList<Pair<String, String>> params = new ArrayList<>();
        if (username != null) params.add(new Pair("user[login]", username));
        if (fullName != null) params.add(new Pair("user[name]", fullName));
        if (bio != null) params.add(new Pair("user[description]", bio));
        if (email != null) params.add(new Pair("user[email]", email));
        if ((password != null) && (password.length() > 0)) {
            params.add(new Pair("user[password]", password));
            params.add(new Pair("user[password_confirmation]", password));
        }

        if (deletePic) {
            // Delete profile pic
            params.add(new Pair("icon_delete", "true"));
        } else if (userPic != null) {
            // New profile pic
            if (userPic != null) params.add(new Pair("user[icon]", userPic));
        }

        if (license != null) {
            params.add(new Pair("user[preferred_observation_license]", license));
            if (makeLicenseSame) params.add(new Pair("user[make_observation_licenses_same]", "1"));
        }
        if (photoLicense != null) {
            params.add(new Pair("user[preferred_photo_license]", photoLicense));
            if (makePhotoLicenseSame) params.add(new Pair("user[make_photo_licenses_same]", "1"));
        }
        if (soundLicense != null) {
            params.add(new Pair("user[preferred_sound_license]", soundLicense));
            if (makeSoundLicenseSame) params.add(new Pair("user[make_sound_licenses_same]", "1"));
        }

        JSONArray array = put(API_HOST + "/users/" + mLogin, params);

        if ((mResponseErrors != null) || (array == null)) {
            // Couldn't update user
            if ((mLastResponseJson != null) && (mLastResponseJson.has("error"))) {
                // API returned specific validation errors - return those
                try {
                    return mLastResponseJson.getJSONObject("error").getJSONObject("original");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return array.optJSONObject(0);
        }
    }

    interface IOnTimezone {
        void onTimezone(String timezoneName);
    }

    private void getTimezoneByCurrentLocation(IOnTimezone cb) {
        getLocation(new IOnLocation() {
            @Override
            public void onLocation(Location location) {
                if (location == null) {
                    // Couldn't retrieve current location
                    cb.onTimezone(null);
                    return;
                }

                // Convert coordinates to timezone
                GeoApiContext context = new GeoApiContext.Builder()
                        .apiKey(mContext.getString(R.string.gmaps2_api_key))
                        .build();

                LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                String zoneIdName = null;

                try {
                    TimeZone zone = TimeZoneApi.getTimeZone(context, currentLocation).await();
                    zoneIdName = zone.getID();
                } catch (NoClassDefFoundError exc) {
                    // Not working on older Androids
                    Logger.tag(TAG).error(exc);
                    cb.onTimezone(null);
                    return;
                } catch (Exception exc) {
                    // Couldn't convert coordinates to timezone
                    Logger.tag(TAG).error(exc);
                    cb.onTimezone(null);
                    return;
                }

                // Next, convert from standard zone name into iNaturalist-API-accepted name
                if (!TIMEZONE_ID_TO_INAT_TIMEZONE.containsKey(zoneIdName)) {
                    // Timezone is unsupported by iNaturalist
                    cb.onTimezone(null);
                    return;
                }

                String zoneName = TIMEZONE_ID_TO_INAT_TIMEZONE.get(zoneIdName);
                cb.onTimezone(zoneName);
            }
        });
    }


    // Registers a user - returns an error message in case of an error (null if successful)
    private String registerUser(String email, String password, String username, String license, String timezone) throws AuthenticationException {
        ArrayList<Pair<String, String>> params = new ArrayList<Pair<String, String>>();
        params.add(new Pair("user[email]", email));
        params.add(new Pair("user[login]", username));
        params.add(new Pair("user[password]", password));
        params.add(new Pair("user[password_confirmation]", password));
        String inatNetwork = mApp.getInaturalistNetworkMember();
        params.add(new Pair("user[site_id]", mApp.getStringResourceByName("inat_site_id_" + inatNetwork)));
        params.add(new Pair("user[preferred_observation_license]", license));
        params.add(new Pair("user[preferred_photo_license]", license));
        params.add(new Pair("user[preferred_sound_license]", license));
        Locale deviceLocale = mContext.getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();
        params.add(new Pair("user[locale]", deviceLanguage));
        if (timezone != null) {
            params.add(new Pair("user[time_zone]", timezone));
        }

        JSONArray response = request(HOST + "/users.json", "post", params, null, true, true, true);
        if (mResponseErrors != null) {
            // Couldn't create user
            try {
                return mResponseErrors.getString(0);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
                return null;
            }

        } else if (mLastStatusCode == HTTP_CREATED) {
            // This mean email verification is required - return the status message
            JSONObject json = response.optJSONObject(0);
            BetterJSONObject o = new BetterJSONObject(json);
            return o.getString("message");

        } else {
            return null;
        }
    }

    private void updateComment(int commentId, int observationId, String body) throws AuthenticationException {
        JSONObject params = new JSONObject();
        JSONObject comment = new JSONObject();
        try {
            comment.put("parent_id", observationId);
            comment.put("parent_type", "Observation");
            comment.put("body", body);
            params.put("comment", comment);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        put(API_HOST + "/comments/" + commentId, params);
    }

    private void deleteComment(int commentId) throws AuthenticationException {
        delete(API_HOST + "/comments/" + commentId, null);
    }

    private void addComment(int observationId, String body) throws AuthenticationException {
        JSONObject params = new JSONObject();
        JSONObject comment = new JSONObject();
        try {
            comment.put("parent_id", observationId);
            comment.put("parent_type", "Observation");
            comment.put("body", body);
            params.put("comment", comment);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        post(API_HOST + "/comments", params);
    }

    private boolean postObservation(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        if (observation.id != null) {
            // Update observation
            Logger.tag(TAG).debug("postObservation: Updating existing " + observation.id + ":" + observation._id);

            JSONArray response = request(API_HOST + "/observations/" + observation.id + "?locale=" + mApp.getPrefLocale(), "put", null, observationToJsonObject(observation, false), true, true, false);

            if (response == null) {
                Logger.tag(TAG).debug("postObservation: Error for " + observation.id + ":" + observation._id + ":" + mLastStatusCode);
                // Some sort of error
                if ((mLastStatusCode >= 400) && (mLastStatusCode < 500)) {
                    // Observation doesn't exist anymore (deleted remotely, and due to network
                    // issues we didn't get any notification of this) - so delete the observation
                    // locally.
                    Logger.tag(TAG).debug("postObservation: Deleting obs " + observation.id + ":" + observation._id);

                    mContext.getContentResolver().delete(Observation.CONTENT_URI, "id = " + observation.id, null);
                    // Delete associated project-fields and photos
                    int count1 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id = " + observation.id, null);
                    int count2 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_uuid = \"" + observation.uuid + "\"", null);
                    int count3 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_id = " + observation.id, null);
                    int count4 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_uuid = \"" + observation.uuid + "\"", null);
                    int count5 = mContext.getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id = " + observation.id, null);
                    int count6 = mContext.getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id = " + observation.id, null);

                    Logger.tag(TAG).debug("postObservation: After delete: " + count1 + ":" + count2 + ":" + count3 + ":" + count4 + ":" + count5 + ":" + count6);

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
        Logger.tag(TAG).debug("postObservation: New obs");
        Logger.tag(TAG).debug(observationParams.toString());

        boolean success = handleObservationResponse(
                observation,
                request(API_HOST + "/observations?locale=" + mApp.getPrefLocale(), "post", null, observationParams, true, true, false)
        );

        if (!success) {
            throw new SyncFailedException();
        }

        return true;
    }

    private JSONObject getObservationJson(int id, boolean authenticated, boolean includeNewProjects) throws AuthenticationException {
        String url = String.format(Locale.ENGLISH, "%s/observations/%d?locale=%s&%s", API_HOST, id, mApp.getPrefLocale(), includeNewProjects ? "include_new_projects=true" : "");

        JSONArray json = get(url, authenticated);
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            if (results.length() == 0) return null;

            return results.getJSONObject(0);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private Pair<Observation, JSONObject> getAndDownloadObservation(int id) throws AuthenticationException {
        // Download the observation
        JSONObject json = getObservationJson(id, true, true);
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

        return new Pair<>(obs, json);
    }

    private boolean postSounds(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        Integer observationId = observation.id;
        ObservationSound os;
        int createdCount = 0;
        ContentValues cv;

        Logger.tag(TAG).debug("postSounds: " + observationId + ":" + observation);

        // query observation sounds where id is null (i.e. new sounds)
        Cursor c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                ObservationSound.PROJECTION,
                "(id IS NULL) AND (observation_uuid = ?) AND ((is_deleted == 0) OR (is_deleted IS NULL))", new String[]{observation.uuid}, ObservationSound.DEFAULT_SORT_ORDER);
        Logger.tag(TAG).debug("postSounds: New sounds: " + c.getCount());
        if (c.getCount() == 0) {
            c.close();
            return true;
        }

        checkForCancelSync();

        // for each observation POST to /sounds
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            os = new ObservationSound(c);

            Logger.tag(TAG).debug("postSounds: Posting sound - " + os);

            if (os.file_url != null) {
                // Online sound
                Logger.tag(TAG).debug("postSounds: Skipping because file_url is not null");
                c.moveToNext();
                continue;
            }

            if ((os.filename == null) || !(new File(os.filename)).exists()) {
                // Local (cached) sound was deleted - probably because the user deleted the app's cache
                Logger.tag(TAG).debug("postSounds: Posting sound - filename doesn't exist: " + os.filename);

                // First, delete this photo record
                mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "_id = ?", new String[]{String.valueOf(os._id)});

                // Set errors for this obs - to notify the user that we couldn't upload the obs sounds
                JSONArray errors = new JSONArray();
                errors.put(mContext.getString(R.string.deleted_sounds_from_cache_error));
                mApp.setErrorsForObservation(os.observation_id, 0, errors);

                // Move to next observation sound
                c.moveToNext();
                checkForCancelSync();

                continue;
            }

            ArrayList<Pair<String, String>> params = os.getParams();
            params.add(new Pair("audio", os.filename));

            JSONArray response;
            String inatNetwork = mApp.getInaturalistNetworkMember();
            response = request( API_HOST + "/observation_sounds", "post", params, null, true, true, false);

            try {
                if (response == null || response.length() != 1 || mLastStatusCode != HTTP_OK) {
                    c.close();

                    if (mLastStatusCode == HTTP_ENTITY_TOO_LARGE) {
                        JSONArray errors = new JSONArray();
                        errors.put(mContext.getString(R.string.couldnt_upload_file_too_large));
                        mApp.setErrorsForObservation(os.observation_id, 0, errors);
                    }

                    throw new SyncFailedException();
                }

                JSONObject json = response.getJSONObject(0);
                BetterJSONObject j = new BetterJSONObject(json);
                ObservationSound jsonObservationSound = new ObservationSound(j);
                Logger.tag(TAG).debug("postSounds: Response for POST: ");
                Logger.tag(TAG).debug(json.toString());
                Logger.tag(TAG).debug("postSounds: Response for POST 2: " + jsonObservationSound);
                os.merge(jsonObservationSound);
                Logger.tag(TAG).debug("postSounds: Response for POST 3: " + os);

                cv = os.getContentValues();
                Logger.tag(TAG).debug("postSounds - Setting _SYNCED_AT - " + os.id + ":" + os._id + ":" + os._observation_id + ":" + os.observation_id);
                mContext.getContentResolver().update(os.getUri(), cv, null, null);
                createdCount += 1;

                increaseProgressForObservation(observation);

            } catch (JSONException e) {
                Logger.tag(TAG).error("JSONException: " + e.toString());
            }

            c.moveToNext();
            checkForCancelSync();
        }
        c.close();


        c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                ObservationSound.PROJECTION,
                "(id IS NULL) AND (observation_uuid = ?) AND ((is_deleted == 0) OR (is_deleted IS NULL))", new String[]{observation.uuid}, ObservationSound.DEFAULT_SORT_ORDER);
        int currentCount = c.getCount();
        Logger.tag(TAG).debug("postSounds: currentCount = " + currentCount);
        c.close();

        if (currentCount == 0) {
            // Sync completed successfully
            return true;
        } else {
            // Sync failed
            throw new SyncFailedException();
        }
    }

    private boolean postPhotos(Observation observation) throws AuthenticationException, CancelSyncException, SyncFailedException {
        Integer observationId = observation.id;
        ObservationPhoto op;
        int createdCount = 0;
        ContentValues cv;

        Logger.tag(TAG).debug("postPhotos: " + observationId + ":" + observation);

        if (observationId != null) {
            // See if there any photos in an invalid state
            Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NULL AND observation_id = ?",
                    new String[]{String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            c.moveToFirst();
            while (c.isAfterLast() == false) {
                op = new ObservationPhoto(c);
                // Shouldn't happen - a photo with null external ID is marked as sync - unmark it
                op._synced_at = null;
                Logger.tag(TAG).debug("postPhotos: Updating with _synced_at = null: " + op);
                mContext.getContentResolver().update(op.getUri(), op.getContentValues(), null, null);
                c.moveToNext();
            }
            c.close();

            c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
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
                mContext.getContentResolver().update(op.getUri(), cv, null, null);
                c.moveToNext();
            }
            c.close();


            // update photos - for each observation PUT to /observation_photos/:id
            c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL AND observation_uuid = ? AND ((is_deleted == 0) OR (is_deleted IS NULL))",
                    new String[]{observation.uuid},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            int updatedCount = c.getCount();

            Logger.tag(TAG).debug("postPhotos: Updating photos: " + updatedCount);

            c.moveToFirst();
            while (c.isAfterLast() == false) {
                checkForCancelSync();

                op = new ObservationPhoto(c);
                String inatNetwork = mApp.getInaturalistNetworkMember();

                // Updating the photo requires calling two APIs - one for updating the license (the Photo object itself),
                // the other for updating the ObservationPhoto (e.g. position)


                // Photo object update
                JSONObject params = new JSONObject();
                try {
                    params.put("license_code", op.license);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Logger.tag(TAG).debug("postPhotos: Photo: Updating " + op + ":" + params);
                JSONArray response = put(API_HOST + "/photos/" + op.photo_id, params);
                try {
                    if (response == null || response.length() != 1) {
                        Logger.tag(TAG).debug("postPhotos: Failed updating " + op.id);

                        if ((mLastStatusCode != HTTP_FORBIDDEN) && (mLastStatusCode != HTTP_NOT_FOUND)) {
                            c.close();
                            throw new SyncFailedException();
                        } else {
                            // Sepcial handling for bug #1055 - don't fail the entire syncing, just skip this one
                            Logger.tag(TAG).debug("postPhotos: Skipping to next photo");
                            c.moveToNext();

                            // Set errors for this obs - to notify the user that we couldn't upload the obs photos
                            JSONArray errors = new JSONArray();
                            errors.put(mContext.getString(R.string.issue_with_updating_photos));
                            mApp.setErrorsForObservation(op.observation_id, 0, errors);

                            continue;
                        }
                    }

                    JSONObject params2 = new JSONObject();
                    JSONObject innerPhoto = new JSONObject();
                    innerPhoto.put("position", op.position);
                    params2.put("observation_photo", innerPhoto);
                    Logger.tag(TAG).debug("postPhotos: ObservationPhoto: Updating " + op + ":" + params2);
                    response = put(API_HOST + "/observation_photos/" + op.id, params2);

                    if (response == null || response.length() != 1) {
                        Logger.tag(TAG).debug("postPhotos: Failed updating " + op.id);
                        c.close();
                        throw new SyncFailedException();
                    }

                    increaseProgressForObservation(observation);

                    JSONObject json = response.getJSONObject(0);
                    BetterJSONObject j = new BetterJSONObject(json);
                    ObservationPhoto jsonObservationPhoto = new ObservationPhoto(j, op);
                    Logger.tag(TAG).debug("postPhotos after put: " + j.getJSONObject());
                    Logger.tag(TAG).debug("postPhotos after put 2: " + jsonObservationPhoto);
                    op.merge(jsonObservationPhoto);
                    Logger.tag(TAG).debug("postPhotos after put 3 - merge: " + op);
                    cv = op.getContentValues();
                    Logger.tag(TAG).debug("postPhotos: Setting _SYNCED_AT - " + op.id + ":" + op._id + ":" + op._observation_id + ":" + op.observation_id);
                    cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                    mContext.getContentResolver().update(op.getUri(), cv, null, null);
                    createdCount += 1;
                } catch (JSONException e) {
                    Logger.tag(TAG).error("JSONException: " + e.toString());
                }

                c.moveToNext();
            }
            c.close();

        }

        // query observation photos where _synced_at is null (i.e. new photos)
        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(_synced_at IS NULL) AND (id IS NULL) AND (observation_uuid = ?) AND ((is_deleted == 0) OR (is_deleted IS NULL))", new String[]{observation.uuid}, ObservationPhoto.DEFAULT_SORT_ORDER);
        Logger.tag(TAG).debug("postPhotos: New photos: " + c.getCount());
        if (c.getCount() == 0) {
            c.close();
            return true;
        }

        checkForCancelSync();

        // for each observation POST to /observation_photos
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            op = new ObservationPhoto(c);

            Logger.tag(TAG).debug("postPhotos: Posting photo - " + op);

            if (op.photo_url != null) {
                // Online photo
                Logger.tag(TAG).debug("postPhotos: Skipping because photo_url is not null");
                c.moveToNext();
                continue;
            }

            ArrayList<Pair<String, String>> params = op.getParams();

            String imgFilePath = op.photo_filename;
            if (imgFilePath == null) {
                // Observation photo is saved in the "old" way (prior to latest change in the way we store photos)
                Logger.tag(TAG).debug("postPhotos: Posting photo - photo_filename is null");
                if (op._photo_id != null) {
                    Uri photoUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, op._photo_id);
                    Cursor pc = mContext.getContentResolver().query(photoUri,
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
                Logger.tag(TAG).debug("postPhotos: Posting photo - still problematic photo filename: " + imgFilePath);

                // First, delete this photo record
                mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_id = ?", new String[]{String.valueOf(op._id)});

                // Set errors for this obs - to notify the user that we couldn't upload the obs photos
                JSONArray errors = new JSONArray();
                errors.put(mContext.getString(R.string.deleted_photos_from_cache_error));
                mApp.setErrorsForObservation(op.observation_id, 0, errors);

                // Move to next observation photo
                c.moveToNext();
                checkForCancelSync();

                continue;
            }
            params.add(new Pair("file", imgFilePath));

            String inatNetwork = mApp.getInaturalistNetworkMember();
            params.add(new Pair("site_id", mApp.getStringResourceByName("inat_site_id_" + inatNetwork)));

            JSONArray response;
            Logger.tag(TAG).debug("postPhotos: POSTing new photo: " + params);
            response = post(API_HOST + "/observation_photos", params, true);
            try {
                if (response == null || response.length() != 1 || mLastStatusCode != HTTP_OK) {
                    c.close();

                    if (mLastStatusCode == HTTP_ENTITY_TOO_LARGE) {
                        JSONArray errors = new JSONArray();
                        errors.put(mContext.getString(R.string.couldnt_upload_file_too_large));
                        mApp.setErrorsForObservation(op.observation_id, 0, errors);
                    }

                    throw new SyncFailedException();
                }

                increaseProgressForObservation(observation);

                JSONObject json = response.getJSONObject(0);
                BetterJSONObject j = new BetterJSONObject(json);
                ObservationPhoto jsonObservationPhoto = new ObservationPhoto(j);
                Logger.tag(TAG).debug("postPhotos: Response for POST: ");
                Logger.tag(TAG).debug(json.toString());
                Logger.tag(TAG).debug("postPhotos: Response for POST 2: " + jsonObservationPhoto);
                op.merge(jsonObservationPhoto);
                Logger.tag(TAG).debug("postPhotos: Response for POST 3: " + op);

                cv = op.getContentValues();
                Logger.tag(TAG).debug("postPhotos - Setting _SYNCED_AT - " + op.id + ":" + op._id + ":" + op._observation_id + ":" + op.observation_id);
                cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                mContext.getContentResolver().update(op.getUri(), cv, null, null);
                createdCount += 1;
            } catch (JSONException e) {
                Logger.tag(TAG).error("JSONException: " + e.toString());
            }

            c.moveToNext();
            checkForCancelSync();
        }
        c.close();

        c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                "(_synced_at IS NULL) AND ((_observation_id = ? OR observation_id = ?)) AND ((is_deleted == 0) OR (is_deleted IS NULL))", new String[]{String.valueOf(observation._id), String.valueOf(observation.id)}, ObservationPhoto.DEFAULT_SORT_ORDER);
        int currentCount = c.getCount();
        Logger.tag(TAG).debug("postPhotos: currentCount = " + currentCount);
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
                Picasso.with(mContext).load(url).into(new Target() {
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
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // Goes over cached photos that were uploaded and that are old enough and deletes them
    // to clear out storage space (they're replaced with their online version, so it'll be
    // accessible by the user).
    private void clearOldCachedPhotos() {
        if (!isNetworkAvailable()) return;
        if (!mApp.loggedIn()) return;

        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION,
                "_updated_at = _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL AND " +
                        "photo_filename IS NOT NULL AND observation_id IS NOT NULL",
                new String[]{}, ObservationPhoto.DEFAULT_SORT_ORDER);

        int totalCount = c.getCount();
        int photosReplacedCount = 0;

        Logger.tag(TAG).info(String.format(Locale.ENGLISH, "clearOldCachedPhotos - %d available cached photos", totalCount));

        while ((totalCount > OLD_PHOTOS_MAX_COUNT) && (!c.isAfterLast()) && (photosReplacedCount < MAX_PHOTO_REPLACEMENTS_PER_RUN)) {
            ObservationPhoto op = new ObservationPhoto(c);

            Logger.tag(TAG).info(String.format(Locale.ENGLISH, "clearOldCachedPhotos - clearing photo %d: %s", photosReplacedCount, op.toString()));

            File obsPhotoFile = new File(op.photo_filename);

            if (op.photo_url == null) {
                // No photo URL defined - download the observation and get the external URL for that photo
                Logger.tag(TAG).info(String.format(Locale.ENGLISH, "clearOldCachedPhotos - No photo_url found for obs photo: %s", op.toString()));
                boolean foundPhoto = false;
                try {
                    JSONObject json = getObservationJson(op.observation_id, false, false);

                    if (json != null) {
                        Observation obs = new Observation(new BetterJSONObject(json));
                        for (int i = 0; i < obs.photos.size(); i++) {
                            if ((obs.photos.get(i).id != null) && (op.id != null)) {
                                if (obs.photos.get(i).id.equals(op.id)) {
                                    // Found the appropriate photo - update the URL
                                    op.photo_url = obs.photos.get(i).photo_url;
                                    Logger.tag(TAG).info(String.format(Locale.ENGLISH, "clearOldCachedPhotos - foundPhoto: %s", op.photo_url));
                                    foundPhoto = true;
                                    break;
                                }
                            }
                        }
                    }

                } catch (AuthenticationException e) {
                    Logger.tag(TAG).error(e);
                }


                Logger.tag(TAG).info(String.format(Locale.ENGLISH, "clearOldCachedPhotos - foundPhoto: %s", foundPhoto));

                if (!foundPhoto) {
                    // Couldn't download remote URL for the observation photo - don't delete it
                    c.moveToNext();
                    continue;
                }
            }

            if (obsPhotoFile.exists()) {
                // Delete the local cached photo file
                boolean success = obsPhotoFile.delete();
                Logger.tag(TAG).info(String.format(Locale.ENGLISH, "clearOldCachedPhotos - deleted photo: %s: %s", success, obsPhotoFile.toString()));
            }

            // Update the obs photo record with the remote photo URL
            Logger.tag(TAG).debug("OP - clearOldCachedPhotos - Setting _SYNCED_AT - " + op.id + ":" + op._id + ":" + op._observation_id + ":" + op.observation_id);
            op.photo_filename = null;
            ContentValues cv = op.getContentValues();
            cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
            mContext.getContentResolver().update(op.getUri(), cv, null, null);

            // Warm up the cache for the image
            warmUpImageCache(op.photo_url);

            photosReplacedCount += 1;
            c.moveToNext();
        }

        c.close();

        Logger.tag(TAG).info("clearOldCachedPhotos - Clearing by files in cache folder");

        // Find all files in the cache folder that look like cached images (<uuid>.jpeg).
        // For each one, see if there's an obs photo pointing to it - if non, it's safe to delete

        FilenameFilter fileFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                int extensionStart = name.indexOf(".");
                if (extensionStart == -1) return false;
                String nameNoExtension = name.substring(0, extensionStart);
                return (
                        (name.endsWith(".jpeg") || name.endsWith(".jpg") || name.endsWith(".heif") ||
                                name.endsWith(".png") || name.endsWith(".png") || name.endsWith(".webp")) &&
                                (!name.endsWith(".do_not_delete.jpeg")) &&
                                (nameNoExtension.length() == 36));
            }
        };

        List<File> allCacheFiles = new ArrayList<>();
        if (mContext.getFilesDir() != null) {
            allCacheFiles.addAll(Arrays.asList(mContext.getFilesDir().listFiles(fileFilter)));
        }
        if (mContext.getExternalCacheDir() != null) {
            allCacheFiles.addAll(Arrays.asList(mContext.getExternalCacheDir().listFiles(fileFilter)));
        }
        if (mContext.getCacheDir() != null) {
            allCacheFiles.addAll(Arrays.asList(mContext.getCacheDir().listFiles(fileFilter)));
        }

        Collection<File> list = CollectionUtils.select(
                allCacheFiles,
                new Predicate<File>() {
                    @Override
                    public boolean evaluate(File f) {
                        if (f.isDirectory()) return false;

                        String filePath = f.getAbsolutePath();
                        Cursor c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI, new String[] { ObservationPhoto._ID },
                                "photo_filename = ?",
                                new String[]{ filePath }, ObservationPhoto.DEFAULT_SORT_ORDER);
                        int count = c.getCount();
                        c.close();

                        return count == 0;
                    }
                });

        long total = 0;

        if (list == null) return;

        for (File f : list) {
            total += f.length();
            Logger.tag(TAG).debug("clearOldCachedPhotos - Removing File: " + f.getAbsoluteFile() + ": " + f.length());
            f.delete();
        }

        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "clearOldCachedPhotos - Removed: %d bytes", total));
    }

    private String getGuideXML(Integer guideId) throws AuthenticationException {
        String url = HOST + "/guides/" + guideId.toString() + ".xml?locale=" + mApp.getPrefLocale();

        try {
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(HTTP_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(HTTP_READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(HTTP_READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                return null;
            }

            InputStream buffer = new BufferedInputStream(response.body().byteStream());
            File outputFile = File.createTempFile(guideId.toString() + ".xml", null, mContext.getCacheDir());
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

            response.close();

            // Return the downloaded full file name
            return outputFile.getAbsolutePath();

        } catch (IOException e) {
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private boolean resendEmailConfirmation() throws AuthenticationException {

        try {
            JSONArray json = request(API_HOST + "/users/resend_confirmation", "post", null, null, true, true, false);

            if (json == null) return false;
            if (json.length() == 0) return false;
            JSONObject response = json.getJSONObject(0);
            if (response.has("errors")) {
                return false;
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return false;
        }

        return true;
    }

    private BetterJSONObject updateCurrentUserDetails(JSONObject params) throws AuthenticationException {
        JSONObject input = new JSONObject();
        try {
            input.put("user", params);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

        JSONArray json = request(API_HOST + "/users/" + mApp.currentUserLogin(), "put", null, input, true, true, false);

        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }


    private BetterJSONObject getUserDetails(String username) throws AuthenticationException {
        String url = API_V2_HOST + "/users/" + username + "?fields=all";
        JSONArray json = get(url, false);
        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            return new BetterJSONObject(json.getJSONObject(0).getJSONArray("results").getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private SerializableJSONArray searchUserObservation(String query) throws AuthenticationException {
        String url = null;

        try {
            StringBuilder sb = new StringBuilder(INaturalistService.API_HOST + "/observations?");
            sb.append("user_id=");
            sb.append(Uri.encode(mLogin));
            sb.append("&per_page=100");
            sb.append("&q=");
            sb.append(URLEncoder.encode(query, "utf8"));

            sb.append("&extra=observation_photos,projects,fields");

            sb.append("&locale=");
            sb.append(mApp.getPrefLocale());

            url = sb.toString();
        } catch (UnsupportedEncodingException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        JSONArray json = get(url, true);
        if (json == null) return null;
        if (json.length() == 0) return null;
        JSONArray results = json.optJSONObject(0).optJSONArray("results");

        return new SerializableJSONArray(results);
    }

    private BetterJSONObject searchAutoComplete(String type, String query, int page) throws AuthenticationException {
        String url = API_HOST + "/" + type + "/autocomplete?geo=true&locale=" + mApp.getPrefLocale() + "&per_page=50&page=" + page + "&q=" + Uri.encode(query);
        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private SerializableJSONArray getUserUpdates(boolean following) throws AuthenticationException {
        String url = API_HOST + "/observations/updates?locale=" + mApp.getPrefLocale() + "&per_page=200&observations_by=" +
                (following ? "following" : "owner");
        JSONArray json = request(url, "get", null, null, true, true, false); // Use JWT Token authentication
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            JSONObject resObject = json.getJSONObject(0);
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            return new SerializableJSONArray(results);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private BetterJSONObject getObservationResultsForMultipleTaxa(String type, int pageSize, JSONArray taxonIds) throws AuthenticationException {
        String ids = taxonIds.toString();
        String url = String.format(Locale.ENGLISH, "%s/observations/%s?per_page=%d&taxon_id=%s",
                API_HOST,
                type,
                pageSize,
                ids.substring(1, ids.length() - 1));

        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private void dismissAnnouncement(int announcementId) throws AuthenticationException {
        String url = String.format(Locale.ENGLISH, "%s/announcements/%d/dismiss",
                API_V2_HOST,
                announcementId);

        JSONArray json = put(url, (JSONObject) null);
    }


    private SerializableJSONArray getAnnouncements() throws AuthenticationException {
        String url = String.format(Locale.ENGLISH, "%s/announcements?locale=%s&placement=mobile&fields=all",
                    API_V2_HOST,
                    mApp.getPrefLocale());

        JSONArray json = get(url, mCredentials != null);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            return new SerializableJSONArray(results);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private BetterJSONObject getExploreResults(String command, ExploreSearchFilters filters, int pageNumber, int pageSize, String orderBy) throws AuthenticationException {
        if (filters == null) return null;

        String url;
        if (command == null) {
            url = String.format(Locale.ENGLISH, "%s/observations%s?locale=%s&page=%d&per_page=%d&ordered_by=%s&%s",
                    API_HOST,
                    command == null ? "" : "/" + command,
                    mApp.getPrefLocale(),
                    pageNumber,
                    pageSize,
                    orderBy == null ? "" : orderBy,
                    filters.toUrlQueryString());
        } else if (command.equals("species_counts")) {
            url = String.format(Locale.ENGLISH, "%s/observations/%s?locale=%s&page=%d&per_page=%d&%s",
                    API_HOST,
                    command,
                    mApp.getPrefLocale(),
                    pageNumber,
                    pageSize,
                    filters.toUrlQueryString());
        } else {
            url = String.format(Locale.ENGLISH, "%s/observations/%s?page=%d&per_page=%d&%s",
                    API_HOST,
                    command,
                    pageNumber,
                    pageSize,
                    filters.toUrlQueryString());
        }

        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }


    private BetterJSONObject getUserSpeciesCount(String username) throws AuthenticationException {
        String url = API_HOST + "/observations/species_counts?place_id=any&verifiable=any&user_id=" + username + "&locale=" + mApp.getPrefLocale();
        JSONArray json = get(url, false);
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private BetterJSONObject getPlaceDetails(long placeId) throws AuthenticationException {
        String url = API_HOST + "/places/" + placeId;
        JSONArray json = get(url, false);
        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            JSONArray results = json.getJSONObject(0).getJSONArray("results");
            return new BetterJSONObject(results.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }


    private boolean isUserDeleted(String username) {
        try {
            JSONArray result = get(API_HOST + "/users/" + username, false);
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }

        return mLastStatusCode == HTTP_NOT_FOUND;
    }

    private BetterJSONObject getUserDetails() throws AuthenticationException {
        String url = API_HOST + "/users/me";
        JSONArray json = get(url, true);
        try {
            if (json == null) return null;
            if (json.length() == 0) return null;
            return new BetterJSONObject(json.getJSONObject(0).getJSONArray("results").getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }


    private BetterJSONObject getProjectObservations(int projectId) throws AuthenticationException {
        String url = API_HOST + "/observations?project_id=" + projectId + "&per_page=50&locale=" + mApp.getPrefLocale();
        JSONArray json = get(url);
        if (json == null) return new BetterJSONObject();
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return new BetterJSONObject();
        }
    }

    private BetterJSONObject getTaxonObservationsBounds(Integer taxonId) {
        String url = API_HOST + "/observations?per_page=1&return_bounds=true&verifiable=true&taxon_id=" + taxonId;

        JSONArray json = null;
        try {
            json = get(url, false);
        } catch (AuthenticationException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            JSONObject response = json.getJSONObject(0);
            return new BetterJSONObject(response.getJSONObject("total_bounds"));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private BetterJSONObject getMissions(Location location, String username, Integer taxonId, float expandLocationByDegress) {
        String url = API_HOST + "/observations/species_counts?locale=" + mApp.getPrefLocale() +
                "&verifiable=true&hrank=species&oauth_application_id=2,3";

        if (expandLocationByDegress == 0) {
            url += "&lat=" + location.getLatitude() + "&lng=" + location.getLongitude();
        } else {
            // Search for taxa in a bounding box expanded by a certain number of degrees (used to expand
            // our search in case we can't find any close taxa)
            url += String.format(Locale.ENGLISH, "&nelat=%f&nelng=%f&swlat=%f&swlng=%f",
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
        url += String.format(Locale.ENGLISH, "&month=%d,%d,%d", modulo(month - 1, 12) + 1, month + 1, modulo(month + 1, 12) + 1);

        JSONArray json = null;
        try {
            json = get(url, false);
        } catch (AuthenticationException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
        if (json == null) return null;
        if (json.length() == 0) return null;
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private BetterJSONObject getProjectSpecies(int projectId) throws AuthenticationException {
        String url = API_HOST + "/observations/species_counts?project_id=" + projectId + "&locale=" + mApp.getPrefLocale();
        JSONArray json = get(url);
        try {
            if (json == null) return new BetterJSONObject();
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return new BetterJSONObject();
        }
    }

    private SerializableJSONArray getNews() throws AuthenticationException {
        String url = API_HOST + "/posts/for_user";
        JSONArray json = get(url, mCredentials != null); // If user is logged-in, returns his news (using an authenticated endpoint)
        return new SerializableJSONArray(json);
    }


    private SerializableJSONArray getProjectNews(int projectId) throws AuthenticationException {
        String url = API_HOST + "/posts?project_id=" + projectId;
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
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
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
                Logger.tag(TAG).error(exc);
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
                Logger.tag(TAG).error(e);
            }
            i++;
        }

        // Add any offline guides
        List<GuideXML> offlineGuides = GuideXML.getAllOfflineGuides(mContext);
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
                Logger.tag(TAG).error(e);
            }

            json.put(guideJson);
        }

        return new SerializableJSONArray(json);
    }

    private SerializableJSONArray getNearByGuides(Location location) throws AuthenticationException {
        if (location == null) {
            // No place found - return an empty result
            Logger.tag(TAG).error("Current place is null");
            return new SerializableJSONArray();
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

        String url = inatHost + String.format(Locale.ENGLISH, "/guides.json?latitude=%s&longitude=%s&per_page=200", lat, lon);
        Logger.tag(TAG).debug(url);

        JSONArray json = get(url);

        return new SerializableJSONArray(json);
    }


    private SerializableJSONArray getNearByProjects(Location location) throws AuthenticationException {
        if (location == null) {
            // No place found - return an empty result
            Logger.tag(TAG).error("Current place is null");
            return new SerializableJSONArray();
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        String url = API_HOST + String.format(Locale.ENGLISH, "/projects?per_page=100&spam=false&lat=%s&lng=%s&order_by=distance", lat, lon);

        Logger.tag(TAG).error(url);

        JSONArray json = get(url);

        if (json == null) {
            return new SerializableJSONArray();
        }
        json = json.optJSONObject(0).optJSONArray("results");

        // Determine which projects are already joined
        for (int i = 0; i < json.length(); i++) {
            Cursor c;
            try {
                c = mContext.getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '" + json.getJSONObject(i).getInt("id") + "'", null, Project.DEFAULT_SORT_ORDER);
                c.moveToFirst();
                int count = c.getCount();
                c.close();
                if (count > 0) {
                    json.getJSONObject(i).put("joined", true);
                }
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
            return new SerializableJSONArray();
        }

        // Determine which projects are already joined
        for (int i = 0; i < results.length(); i++) {
            Cursor c;
            try {
                c = mContext.getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '" + results.getJSONObject(i).getInt("id") + "'", null, Project.DEFAULT_SORT_ORDER);
                c.moveToFirst();
                int count = c.getCount();
                c.close();
                if (count > 0) {
                    results.getJSONObject(i).put("joined", true);
                }
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
                continue;
            }

        }

        return new SerializableJSONArray(results);
    }

    private void addProjectFields(JSONArray jsonFields, int projectId) {
        ArrayList<ProjectField> projectFields = new ArrayList<ProjectField>();

        for (int i = 0; i < jsonFields.length(); i++) {
            try {
                BetterJSONObject jsonField = new BetterJSONObject(jsonFields.getJSONObject(i));
                ProjectField field = new ProjectField(jsonField);
                field.project_id = projectId;
                projectFields.add(field);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }
        }

        // First, delete all previous project fields (for that project)
        mContext.getContentResolver().delete(ProjectField.CONTENT_URI, "(project_id IS NOT NULL) and (project_id = " + projectId + ")", null);

        // Next, re-add all project fields
        for (int i = 0; i < projectFields.size(); i++) {
            ProjectField field = projectFields.get(i);
            mContext.getContentResolver().insert(ProjectField.CONTENT_URI, field.getContentValues());
        }
    }


    public JSONArray getObservationSubscriptions(int obsId) throws AuthenticationException {
        JSONArray json = get(String.format(Locale.ENGLISH, "%s/observations/%d/subscriptions", API_HOST, obsId));

        if (json == null) {
            return null;
        }

        JSONArray results = json.optJSONObject(0).optJSONArray("results");

        return results;
    }



    public boolean followObservation(int obsId) throws AuthenticationException {
        JSONArray result = null;
        try {
            result = post(String.format(Locale.ENGLISH, "%s/subscriptions/observation/%d/subscribe", API_HOST, obsId), (JSONObject) null);
        } catch (Exception exc) {
            exc.printStackTrace();
            return false;
        }

        return ((mResponseErrors == null) && (mLastStatusCode == HTTP_OK));
    }


    public void flagObservationAsCaptive(int obsId) throws AuthenticationException {
        JSONObject params = new JSONObject();
        try {
            params.put("agree", false);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        post(String.format(Locale.ENGLISH, "%s/observations/%d/quality/wild", API_HOST, obsId), params);
    }

    public void joinProject(int projectId) throws AuthenticationException {
        post(String.format(Locale.ENGLISH, "%s/projects/%d/join", API_HOST, projectId), (JSONObject) null);

        try {
            JSONArray result = get(String.format(Locale.ENGLISH, "%s/projects/%d", API_HOST, projectId));
            if (result == null) return;
            JSONArray results = result.getJSONObject(0).getJSONArray("results");
            BetterJSONObject jsonProject = new BetterJSONObject(results.getJSONObject(0));
            Project project = new Project(jsonProject);

            Cursor c = mContext.getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = ?", new String[]{String.valueOf(project.id)}, null);

            if (c.getCount() == 0) {
                // Add joined project locally
                ContentValues cv = project.getContentValues();
                mContext.getContentResolver().insert(Project.CONTENT_URI, cv);
            }
            c.close();

            // Save project fields
            addProjectFields(jsonProject.getJSONArray("project_observation_fields").getJSONArray(), jsonProject.getInt("id"));

        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }
    }

    public void leaveProject(int projectId) throws AuthenticationException {
        delete(String.format(Locale.ENGLISH, "%s/projects/%d/leave", API_HOST, projectId), null);

        // Remove locally saved project (because we left it)
        mContext.getContentResolver().delete(Project.CONTENT_URI, "(id IS NOT NULL) and (id = " + projectId + ")", null);
    }


    private BetterJSONObject removeObservationFromProject(Integer projectObservationId, int observationId, int projectId) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }

        String url = projectObservationId != null ?
                String.format(Locale.ENGLISH, "%s/project_observations/%d", API_HOST, projectObservationId) :
                String.format(Locale.ENGLISH, "%s/projects/%d/remove.json?observation_id=%d", HOST, projectId, observationId);
        JSONArray json = request(url, "delete", null, null, true, true, false);

        if (json == null) return null;

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return new BetterJSONObject();
        }
    }


    private BetterJSONObject addObservationToProject(int observationId, int projectId) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }

        String url = API_HOST + "/project_observations";

        JSONObject params = new JSONObject();
        JSONObject projectObs = new JSONObject();
        try {
            projectObs.put("observation_id", observationId);
            projectObs.put("project_id", projectId);
            params.put("project_observation", projectObs);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        JSONArray json = post(url, params);

        if (json == null) {
            return null;
        }

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }
    }

    private boolean postFlag(String flaggableType, Integer flaggableId, String flag, String flagExplanation) throws AuthenticationException {
        String url = String.format("%s/flags", API_HOST);

        JSONObject content = new JSONObject();

        try {
            JSONObject flagObject = new JSONObject();
            flagObject.put("flaggable_type", flaggableType);
            flagObject.put("flaggable_id", flaggableId);
            flagObject.put("flag", flag);
            content.put("flag", flagObject);
            if (flagExplanation != null) content.put("flag_explanation", flagExplanation);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONArray json = post(url, content);

        return mLastStatusCode == HTTP_OK;
    }

    private boolean muteUser(Integer userId) throws AuthenticationException {
        String url = String.format("%s/users/%d/mute", API_HOST, userId);

        JSONObject content = new JSONObject();
        JSONArray json = post(url, content);

        return mLastStatusCode == HTTP_OK;
    }

    private boolean unmuteUser(Integer userId) throws AuthenticationException {
        String url = String.format("%s/users/%d/mute", API_HOST, userId);

        JSONObject content = new JSONObject();
        JSONArray json = delete(url, null);

        return mLastStatusCode == HTTP_OK;
    }


    private BetterJSONObject postMessage(Integer toUser, Integer threadId, String subject, String body) throws AuthenticationException {
        String url = String.format("%s/messages", API_HOST);

        JSONObject message = new JSONObject();
        JSONObject content = new JSONObject();
        try {
            message.put("to_user_id", toUser);
            if (threadId != null) message.put("thread_id", threadId);
            message.put("subject", subject);
            message.put("body", body);

            content.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONArray json = post(url, content);

        if (json == null) {
            return null;
        }

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return new BetterJSONObject();
        }
    }


    private BetterJSONObject getNotificationCounts() throws AuthenticationException {
        String url = String.format("%s/users/notification_counts", API_HOST);

        JSONArray json = get(url);

        if (json == null) {
            return null;
        }

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return new BetterJSONObject();
        }
    }

    private BetterJSONObject getMessages(String searchQuery, String box, boolean groupByThreads, Integer messageId) throws AuthenticationException {
        String url = messageId == null ?
                String.format("%s/messages?q=%s&box=%s&threads=%s&per_page=200",
                        API_HOST, searchQuery != null ? URLEncoder.encode(searchQuery) : "", box != null ? box : "inbox", groupByThreads) :
                String.format(Locale.ENGLISH, "%s/messages/%d", API_HOST, messageId);

        JSONArray json = get(url);

        if (json == null) {
            return null;
        }

        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return new BetterJSONObject();
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

        Cursor c = mContext.getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, null, null, Project.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        int index = 0;

        while (c.isAfterLast() == false) {
            Project project = new Project(c);
            JSONObject jsonProject = project.toJSONObject();
            try {
                jsonProject.put("joined", true);
                projects.put(index, jsonProject);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
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

        int totalResults;
        int projectsDownloaded = 0;
        int page = 1;
        JSONArray finalJson = new JSONArray();

        do {
            String url = API_HOST + "/users/" + Uri.encode(mLogin) + "/projects?per_page=100&page=" + page;
            JSONArray json = get(url, true);

            if (json == null) {
                return new SerializableJSONArray(finalJson);
            }

            totalResults = json.optJSONObject(0).optInt("total_results");
            JSONArray results = json.optJSONObject(0).optJSONArray("results");
            projectsDownloaded += results.length();
            page++;

            for (int i = 0; i < results.length(); i++) {
                try {
                    JSONObject project = results.getJSONObject(i);
                    project.put("joined", true);
                    finalJson.put(project);

                    // Save project fields
                    addProjectFields(project.getJSONArray("project_observation_fields"), project.optInt("id"));

                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }
        } while (projectsDownloaded < totalResults);

        return new SerializableJSONArray(finalJson);
    }


    @SuppressLint("NewApi")
    private int getAdditionalUserObservations(int maxCount) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return -1;
        }


        Integer lastId = mPreferences.getInt("last_downloaded_id", 0);

        if (lastId == 0) {
            Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI,
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
        url += "&locale=" + mApp.getPrefLocale();

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
                    Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI,
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
                Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).debug("syncRemotelyDeletedObs: First time syncing, no need to delete observations");
            return true;
        }

        Timestamp lastSyncTS = new Timestamp(lastSync);
        url += String.format(Locale.ENGLISH, "?since=%s", URLEncoder.encode(new SimpleDateFormat("yyyy-MM-dd").format(lastSyncTS)));

        JSONArray json = get(url, true);
        if (json != null && json.length() > 0) {
            // Delete any local observations which were deleted remotely by the user
            JSONArray results = null;
            try {
                results = json.getJSONObject(0).getJSONArray("results");
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
                return false;
            }

            if (results.length() == 0) return true;

            ArrayList<String> ids = new ArrayList<>();
            ArrayList<String> uuids = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                String id = String.valueOf(results.optInt(i));
                ids.add(id);

                Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "id = " + id, null, Observation.DEFAULT_SORT_ORDER);
                if (obsc.getCount() > 0) {
                    obsc.moveToFirst();
                    Observation obs = new Observation(obsc);
                    uuids.add('"' + obs.uuid + '"');
                }
                obsc.close();
            }
            String deletedIds = StringUtils.join(ids, ",");
            String deletedUUIDs = uuids.size() > 0 ? StringUtils.join(uuids, ",") : null;

            Logger.tag(TAG).debug("syncRemotelyDeletedObs: " + deletedIds);

            mContext.getContentResolver().delete(Observation.CONTENT_URI, "(id IN (" + deletedIds + "))", null);
            // Delete associated project-fields and photos
            int count1 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
            int count2 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
            int count3 = mContext.getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
            int count4 = mContext.getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);

            if (deletedUUIDs != null) {
                int count5 = mContext.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_uuid in (" + deletedUUIDs + ")", null);
                int count6 = mContext.getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_uuid in (" + deletedUUIDs + ")", null);
            }
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
        url += String.format(Locale.ENGLISH, "&updated_since=%s&order_by=created_at&order=desc&extra=observation_photos,projects,fields", URLEncoder.encode(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(lastSyncTS)));

        if (maxCount == 0) {
            maxCount = 200;
        }

        if (maxCount > 0) {
            // Retrieve only a certain number of observations
            url += String.format(Locale.ENGLISH, "&per_page=%d&page=1", maxCount);
        }

        url += "&locale=" + mApp.getPrefLocale();

        mProjectObservations = new ArrayList<SerializableJSONArray>();
        mProjectFieldValues = new Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>>();

        JSONArray json = get(url, true);
        if (json != null && json.length() > 0) {
            Logger.tag(TAG).debug("getUserObservations");
            JSONArray results = null;
            try {
                results = json.getJSONObject(0).getJSONArray("results");
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
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
        Cursor c = mContext.getContentResolver().query(ProjectFieldValue.CONTENT_URI,
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
                JSONArray jsonResult = get(API_HOST + "/observations/" + localField.observation_id);

                if (jsonResult != null) {
                    Hashtable<Integer, ProjectFieldValue> fields = new Hashtable<Integer, ProjectFieldValue>();

                    try {
                        JSONArray results = jsonResult.getJSONObject(0).getJSONArray("results");
                        JSONArray jsonFields = results.getJSONObject(0).getJSONArray("observation_field_values");

                        for (int j = 0; j < jsonFields.length(); j++) {
                            JSONObject jsonField = jsonFields.getJSONObject(j);
                            JSONObject observationField = jsonField.getJSONObject("observation_field");
                            int id = observationField.optInt("id", jsonField.getInt("observation_field_id"));
                            fields.put(id, new ProjectFieldValue(new BetterJSONObject(jsonField)));
                        }
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
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
                JSONObject params = new JSONObject();
                JSONObject obsFieldValue = new JSONObject();
                try {
                    obsFieldValue.put("observation_id", localField.observation_id);
                    obsFieldValue.put("observation_field_id", localField.field_id);
                    obsFieldValue.put("value", localField.value);

                    params.put("observation_field_value", obsFieldValue);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                JSONArray result = post(API_HOST + "/observation_field_values", params);

                if (result == null) {
                    if (mResponseErrors == null) {
                        c.close();
                        throw new SyncFailedException();
                    } else {
                        Cursor c2 = mContext.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
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
            mContext.getContentResolver().update(localField.getUri(), cv, null, null);

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
            mContext.getContentResolver().insert(ProjectFieldValue.CONTENT_URI, cv);

            c = mContext.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
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

        Cursor c = mContext.getContentResolver().query(ProjectFieldValue.CONTENT_URI,
                ProjectFieldValue.PROJECTION,
                "_updated_at > _synced_at AND _synced_at IS NOT NULL",
                null,
                ProjectFieldValue.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        if ((c.getCount() > 0) || (mProjectFieldValues.size() > 0)) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION,
                    mContext.getString(R.string.projects),
                    mContext.getString(R.string.syncing_observation_fields),
                    mContext.getString(R.string.syncing));
        } else {
            c.close();
            return true;
        }

        while (c.isAfterLast() == false) {
            checkForCancelSync();
            ProjectFieldValue localField = new ProjectFieldValue(c);

            // Make sure that the local field has an *external* observation id (i.e. the observation
            // it belongs to has been synced)
            Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
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
                JSONArray jsonResult = get(API_HOST + "/observations/" + localField.observation_id);

                if (jsonResult != null) {
                    Hashtable<Integer, ProjectFieldValue> fields = new Hashtable<Integer, ProjectFieldValue>();

                    try {
                        JSONArray results = jsonResult.getJSONObject(0).getJSONArray("results");
                        JSONArray jsonFields = results.getJSONObject(0).getJSONArray("observation_field_values");

                        for (int j = 0; j < jsonFields.length(); j++) {
                            JSONObject jsonField = jsonFields.getJSONObject(j);
                            JSONObject observationField = jsonField.getJSONObject("observation_field");
                            int id = observationField.optInt("id", jsonField.getInt("observation_field_id"));
                            fields.put(id, new ProjectFieldValue(new BetterJSONObject(jsonField)));
                        }
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
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
                JSONObject params = new JSONObject();
                JSONObject obsFieldValue = new JSONObject();
                try {
                    obsFieldValue.put("observation_id", localField.observation_id);
                    obsFieldValue.put("observation_field_id", localField.field_id);
                    obsFieldValue.put("value", localField.value);

                    params.put("observation_field_value", obsFieldValue);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                JSONArray result = post(API_HOST + "/observation_field_values", params);

                if (result == null) {
                    if (mResponseErrors == null) {
                        c.close();
                        throw new SyncFailedException();
                    } else {
                        Cursor c2 = mContext.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
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
            mContext.getContentResolver().update(localField.getUri(), cv, null, null);

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
                mContext.getContentResolver().insert(ProjectFieldValue.CONTENT_URI, cv);

                c = mContext.getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
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
            JSONArray result = get(String.format(Locale.ENGLISH, "%s/observation_fields/%d.json", HOST, fieldId));
            if (result == null) return false;

            BetterJSONObject jsonObj;
            jsonObj = new BetterJSONObject(result.getJSONObject(0));
            ProjectField field = new ProjectField(jsonObj);

            mContext.getContentResolver().insert(ProjectField.CONTENT_URI, field.getContentValues());

            return true;

        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
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
        Integer page = extras.getInt("page", 1);
        Integer perPage = extras.getInt("per_page", NEAR_BY_OBSERVATIONS_PER_PAGE);

        String url;
        if (extras.containsKey("username")) {
            url = API_HOST + "/observations?user_id=" + Uri.encode(extras.getString("username")) + "&extra=observation_photos";
        } else {
            url = API_HOST + "/observations?extra=observation_photos";
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
            url += "&project_id=" + extras.getInt("project_id");
        }

        url += "&locale=" + mApp.getPrefLocale();

        Logger.tag(TAG).debug("Near by observations URL: " + url);

        mNearByObservationsUrl = url;

        JSONArray json = get(url, mApp.loggedIn());
        Intent reply = new Intent(ACTION_NEARBY);
        reply.putExtra("minx", minx);
        reply.putExtra("maxx", maxx);
        reply.putExtra("miny", miny);
        reply.putExtra("maxy", maxy);
        if (json == null) {
            reply.putExtra("error", String.format(Locale.ENGLISH, mContext.getString(R.string.couldnt_load_nearby_observations), ""));
        }

        if (url.equalsIgnoreCase(mNearByObservationsUrl)) {
            // Only send the reply if a new near by observations request hasn't been made yet
            if (json != null) {
                JSONArray results = json.optJSONObject(0).optJSONArray("results");
                mApp.setServiceResult(ACTION_NEARBY, new SerializableJSONArray(results));
            }
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(reply);
        }
    }

    private JSONArray put(String url, ArrayList<Pair<String, String>> params) throws AuthenticationException {
        params.add(new Pair("_method", "PUT"));
        return request(url, "put", params, null, true);
    }

    private JSONArray put(String url, JSONObject jsonContent) throws AuthenticationException {
        return request(url, "put", null, jsonContent, true);
    }

    private JSONArray delete(String url, ArrayList<Pair<String, String>> params) throws AuthenticationException {
        return request(url, "delete", params, null, true);
    }

    private JSONArray post(String url, ArrayList<Pair<String, String>> params, boolean authenticated) throws AuthenticationException {
        return request(url, "post", params, null, authenticated);
    }

    private JSONArray post(String url, ArrayList<Pair<String, String>> params) throws AuthenticationException {
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

    private JSONArray request(String url, String method, ArrayList<Pair<String, String>> params, JSONObject jsonContent, boolean authenticated) throws AuthenticationException {
        return request(url, method, params, jsonContent, authenticated, false, false);
    }

    private String getAnonymousJWTToken() {
        String anonymousApiSecret = mContext.getString(R.string.jwt_anonymous_api_secret);

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

    private String getJWTToken() throws AuthenticationException {
        if (mPreferences == null)
            mPreferences = mContext.getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String jwtToken = mPreferences.getString("jwt_token", null);
        Long jwtTokenExpiration = mPreferences.getLong("jwt_token_expiration", 0);

        if ((jwtToken == null) || ((System.currentTimeMillis() - jwtTokenExpiration) / 1000 > JWT_TOKEN_EXPIRATION_MINS * 60)) {
            // JWT Tokens expire after 30 mins - if the token is non-existent or older than 25 mins (safe margin) - ask for a new one
            try {
                JSONArray result = get(HOST + "/users/api_token.json", true);
                if ((result == null) || (result.length() == 0)) return null;
                if (result.optJSONObject(0) == null) return null;

                // Get newest JWT Token
                jwtToken = result.optJSONObject(0).optString("api_token");
                jwtTokenExpiration = System.currentTimeMillis();

                SharedPreferences.Editor editor = mPreferences.edit();
                editor.putString("jwt_token", jwtToken);
                editor.putLong("jwt_token_expiration", jwtTokenExpiration);
                editor.commit();

                return jwtToken;
            } catch (AuthenticationException e) {
                Logger.tag(TAG).error(e);
                throw e;
            }
        } else {
            // Current JWT token is still fresh/valid - return it as-is
            return jwtToken;
        }
    }

    private JSONArray request(String url, String method, ArrayList<Pair<String, String>> params, JSONObject jsonContent, boolean authenticated, boolean useJWTToken, boolean allowAnonymousJWTToken) throws AuthenticationException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(HTTP_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HTTP_READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        Request.Builder requestBuilder = new Request.Builder()
                .addHeader("User-Agent", getUserAgent(mApp))
                .addHeader("X-Installation-ID", mApp.getInstallationID())
                .url(url);

        mRetryAfterDate = null;
        mServiceUnavailable = false;

        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "URL: %s - %s (params: %s / %s)", method, url, (params != null ? params.toString() : "null"), (jsonContent != null ? jsonContent.toString() : "null")));

        method = method.toUpperCase();
        RequestBody requestBody = null;

        if (method.equals("GET") && (params != null)) {
            HttpUrl.Builder httpUriBuilder = HttpUrl.parse(url).newBuilder();

            for (int i = 0; i < params.size(); i++) {
                httpUriBuilder.addQueryParameter(params.get(i).first, params.get(i).second);
            }
            HttpUrl httpUrl = httpUriBuilder.build();
            requestBuilder = new Request.Builder()
                    .addHeader("User-Agent", getUserAgent(mApp))
                    .url(httpUrl);

        } else if ((jsonContent == null) && (params == null) && (method.equals("PUT") || method.equals("POST"))) {
            // PUT/POST with empty body
            requestBody = RequestBody.create(null, new byte[]{});

        } else if (jsonContent != null) {
            // PUT/POST with JSON body content
            requestBuilder.addHeader("Content-type", "application/json");
            requestBody = RequestBody.create(JSON, jsonContent.toString());

        } else if (params != null) {
            // PUT/POST with "Standard" multipart encoding
            MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).first.equalsIgnoreCase("image") || params.get(i).first.equalsIgnoreCase("file") || params.get(i).first.equalsIgnoreCase("user[icon]") || params.get(i).first.equalsIgnoreCase("audio")) {
                    // If the key equals to "image", we use FileBody to transfer the data
                    String value = params.get(i).second;
                    MediaType mediaType;
                    if (value != null) {
                        String name;
                        if (params.get(i).first.equalsIgnoreCase("audio")) {
                            name = "file";
                            requestBuilder.addHeader("Accept", "application/json");
                            mediaType = MediaType.parse("audio/" + value.substring(value.lastIndexOf(".") + 1));
                        } else {
                            name = params.get(i).first;
                            mediaType = MediaType.parse("image/" + value.substring(value.lastIndexOf(".") + 1));
                        }
                        File file = new File(value);
                        requestBodyBuilder.addFormDataPart(name, file.getName(),
                                RequestBody.create(mediaType, file));
                    }
                } else {
                    // Normal string data
                    requestBodyBuilder.addFormDataPart(params.get(i).first, params.get(i).second);
                }
            }

            requestBody = requestBodyBuilder.build();
        }

        if ((url.startsWith(API_HOST) || url.startsWith(API_V2_HOST)) && (mCredentials != null)) {
            // For the node API, if we're logged in, *always* use JWT authentication
            authenticated = true;
            useJWTToken = true;
        }

        if (authenticated) {
            if (useJWTToken && allowAnonymousJWTToken && (mCredentials == null)) {
                // User not logged in, but allow using anonymous JWT
                requestBuilder.addHeader("Authorization", getAnonymousJWTToken());
            } else {
                ensureCredentials();

                if (useJWTToken) {
                    // Use JSON Web Token for this request
                    String jwtToken = getJWTToken();
                    if (jwtToken == null) {
                        // Could not renew JWT token due to connectivity issues
                        Logger.tag(TAG).error("JWT Token is null");
                        return null;
                    }
                    requestBuilder.addHeader("Authorization", jwtToken);
                } else if (mLoginType == LoginType.PASSWORD) {
                    // Old-style password authentication
                    requestBuilder.addHeader("Authorization", "Basic " + mCredentials);
                } else {
                    // OAuth2 token (G+/etc)
                    requestBuilder.addHeader("Authorization", "Bearer " + mCredentials);
                }
            }
        }

        requestBuilder.addHeader("Accept-Language", mApp.getPrefLocale());

        try {
            mResponseErrors = null;
            mLastResponseJson = null;

            Request request = requestBuilder.method(method, requestBody).build();
            Response response = client.newCall(request).execute();

            Logger.tag(TAG).debug("Response: " + response.code() + ": " + response.message());

            mLastStatusCode = response.code();

            Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "(for URL: %s - %s (params: %s / %s))", method, url, (params != null ? params.toString() : "null"), (jsonContent != null ? jsonContent.toString() : "null")));

            String content = null;
            try {
                content = response.body().string();
            } catch (Exception exc) {
                Logger.tag(TAG).error(exc);
            }

            JSONArray json = null;

            if (content != null) {
                Logger.tag(TAG).debug(content);

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
            }

            mResponseHeaders = response.headers();
            response.close();

            try {
                if ((json != null) && (json.length() > 0)) {
                    JSONObject result = json.getJSONObject(0);
                    mLastResponseJson = result;
                    if (result.has("errors")) {
                        // Error response
                        Logger.tag(TAG).error("Got an error response: " + result.get("errors").toString());
                        mResponseErrors = result.getJSONArray("errors");
                        return null;
                    }
                }
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }

            if ((content != null) && (content.length() == 0)) {
                // In case it's just non content (but OK HTTP status code) - so there's no error
                json = new JSONArray();
            }

            if (response.isSuccessful()) {
                return json;
            } else {
                // HTTP error of some kind - Check for response code
                switch (mLastStatusCode) {
                    case HTTP_UNAUTHORIZED:
                        // Authentication error

                        // Extract error description, if exists
                        if (json != null) {
                            JSONObject innerJson = json.optJSONObject(0);
                            if (innerJson != null) {
                                Object errorObject = innerJson.opt("error");
                                if (errorObject instanceof String) {
                                    mResponseErrors = new JSONArray();
                                    mResponseErrors.put((String) errorObject);
                                } else if (errorObject != null) {
                                    if (((JSONObject) errorObject).optJSONObject("original") != null) {
                                        String error = ((JSONObject) errorObject).optJSONObject("original").optString("error");
                                        if (error != null) {
                                            mResponseErrors = new JSONArray();
                                            mResponseErrors.put(error);
                                        }
                                    }
                                }
                            }
                        }
                        throw new AuthenticationException();

                    case HTTP_UNAVAILABLE:
                        Logger.tag(TAG).error("503 server unavailable");
                        mServiceUnavailable = true;

                        // Find out if there's a "Retry-After" header
                        List<String> headers = response.headers("Retry-After");
                        if (headers.size() > 0) {
                            for (String timestampString : headers) {
                                Logger.tag(TAG).error("Retry after raw string: " + timestampString);
                                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                                try {
                                    mRetryAfterDate = format.parse(timestampString);
                                    Logger.tag(TAG).error("Retry after: " + mRetryAfterDate);
                                    break;
                                } catch (ParseException e) {
                                    Logger.tag(TAG).error(e);
                                    try {
                                        // Try parsing it as a seconds-delay value
                                        int secondsDelay = Integer.valueOf(timestampString);
                                        Logger.tag(TAG).error("Retry after: " + secondsDelay);
                                        Calendar calendar = Calendar.getInstance();
                                        calendar.add(Calendar.SECOND, secondsDelay);
                                        mRetryAfterDate = calendar.getTime();

                                        break;
                                    } catch (NumberFormatException exc) {
                                        Logger.tag(TAG).error(exc);
                                    }
                                }
                            }
                        }

                        // Show service not available message to user
                        mHandler.post(() -> {
                            String errorMessage;
                            Date retryAfterDate = mRetryAfterDate;
                            Date currentTime = Calendar.getInstance().getTime();

                            if (retryAfterDate == null || currentTime == null) {
                                // No specific retry time
                                errorMessage = mContext.getString(R.string.please_try_again_in_a_few_hours);
                            } else if (retryAfterDate.before(currentTime)) {
                                // Service is down and we don't know when it'll be back
                                errorMessage = mContext.getString(R.string.please_try_again_soon);
                            } else {
                                // Specific retry time
                                long differenceSeconds = (retryAfterDate.getTime() - currentTime.getTime()) / 1000;

                                long delay;
                                String delayType;

                                if (differenceSeconds < 60) {
                                    delayType = mContext.getString(R.string.seconds_value);
                                    delay = differenceSeconds;
                                } else if (differenceSeconds < 60 * 60) {
                                    delayType = mContext.getString(R.string.minutes);
                                    delay = (differenceSeconds / 60);
                                } else {
                                    delayType = mContext.getString(R.string.hours);
                                    delay = (differenceSeconds / (60 * 60));
                                }
                                errorMessage = String.format(mContext.getString(R.string.please_try_again_in_x), delay, delayType);
                            }

                            if (System.currentTimeMillis() - mLastServiceUnavailableNotification > 30000) {
                                // Make sure we won't notify the user about this too often
                                mLastServiceUnavailableNotification = System.currentTimeMillis();
                                Toast.makeText(mContext.getApplicationContext(), mContext.getString(R.string.service_temporarily_unavailable) + " " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });

                        break;

                    case HTTP_GONE:
                        // TODO create notification that informs user some observations have been deleted on the server,
                        // click should take them to an activity that lets them decide whether to delete them locally
                        // or post them as new observations
                        break;
                    default:
                }

                return null;
            }
        } catch (IOException e) {
            Logger.tag(TAG).error("Error for URL " + url + ":" + e);
            Logger.tag(TAG).error(e);

            // Test out the Internet connection in multiple ways (helps pin-pointing issue)
            performConnectivityTest();
        }

        return null;
    }

    private void performConnectivityTest() {
        long currentTime = System.currentTimeMillis();

        // Perform connectivity test once every 5 minutes at most
        if (currentTime - mLastConnectionTest < 5 * 60 * 1000) {
            return;
        }

        mLastConnectionTest = currentTime;

        Logger.tag(TAG).info("Performing connectivity test");

        contactUrl("https://api.inaturalist.org");
        contactUrl("http://api.inaturalist.org");
        contactUrl("https://www.inaturalist.org/ping");
        contactUrl("http://www.inaturalist.org/ping");
        contactUrl("https://www.naturalista.mx/ping");
        contactUrl("http://www.naturalista.mx/ping");
        contactUrl("https://www.google.com");
        contactUrl("http://www.example.com");
    }

    private void contactUrl(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .addHeader("User-Agent", getUserAgent(mApp))
                .head()
                .url(url)
                .build();

        try {
            Logger.tag(TAG).info("Contacting " + url);
            Response response = client.newCall(request).execute();
            Logger.tag(TAG).info("isSuccessful: " + response.isSuccessful() + "; response code = " + response.code());
            response.close();
        } catch (Exception e) {
            Logger.tag(TAG).error("Failed contacting " + url);
            Logger.tag(TAG).error(e);
        }
    }

    private boolean ensureCredentials() throws AuthenticationException {
        if (mCredentials != null) {
            return true;
        }

        // request login unless passive
        if (!mPassive) {
            throw new AuthenticationException();
        }

        return false;
    }

    private void requestCredentials() {
        mApp.sweepingNotify(AUTH_NOTIFICATION, mContext.getString(R.string.please_sign_in), mContext.getString(R.string.please_sign_in_description), null);
    }


    // Returns an array of two strings: access token + iNat username. In case of error, returns
    // the array with first value (token) as null, second value as error description.
    public static String[] verifyCredentials(Context context, String username, String oauth2Token, LoginType authType, boolean askForScopeDeletion) {
        String grantType = null;

        INaturalistApp app = (INaturalistApp) context.getApplicationContext();
        String url = HOST + (authType == LoginType.OAUTH_PASSWORD ? "/oauth/token" : "/oauth/assertion_token");

        OkHttpClient client = new OkHttpClient().newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(HTTP_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HTTP_READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HTTP_READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();


        FormBody.Builder requestBodyBuilder = new FormBody.Builder()
                .add("format", "json")
                .add("client_id", INaturalistApp.getAppContext().getString(R.string.oauth_client_id))
                .add("client_secret", INaturalistApp.getAppContext().getString(R.string.oauth_client_secret));

        if (authType == LoginType.GOOGLE) {
            grantType = "google";
        } else if (authType == LoginType.OAUTH_PASSWORD) {
            grantType = "password";
        }

        requestBodyBuilder.add("grant_type", grantType);

        if (authType == LoginType.OAUTH_PASSWORD) {
            requestBodyBuilder.add("password", oauth2Token);
            requestBodyBuilder.add("username", username);
        } else {
            requestBodyBuilder.add("assertion", oauth2Token);
        }
        if (askForScopeDeletion) {
            requestBodyBuilder.add("scope", "login write account_delete");
        }

        requestBodyBuilder.add("locale", app.getPrefLocale());

        RequestBody requestBody = requestBodyBuilder.build();

        Request request = new Request.Builder()
                .addHeader("User-Agent", getUserAgent(context))
                .url(url)
                .post(requestBody)
                .build();

        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                Logger.tag(TAG).error("Authentication failed: " + response.code() + ": " + response.message());
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String content = responseBody.string();
                    JSONObject json = new JSONObject(content);
                    return new String[]{null, json.optString("error_description")};
                } else {
                    return new String[]{null, context.getString(R.string.authentication_failed)};
                }
            }

            String content = response.body().string();
            response.close();

            // Upgrade to an access token
            JSONObject json = new JSONObject(content);
            String accessToken = json.getString("access_token");

            // Next, find the iNat username (since we currently only have the FB/Google email)
            request = new Request.Builder()
                    .addHeader("User-Agent", getUserAgent(context))
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url(HOST + "/users/edit.json")
                    .build();

            response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                Logger.tag(TAG).error("Authentication failed (edit.json): " + response.code() + ": " + response.message());

                return new String[]{null, context.getString(R.string.authentication_failed)};
            }

            content = response.body().string();
            response.close();

            Logger.tag(TAG).debug(String.format("RESP2: %s", content));

            json = new JSONObject(content);
            if (!json.has("login")) {
                return new String[]{null, context.getString(R.string.authentication_failed)};
            }

            String returnedUsername = json.getString("login");

            return new String[]{accessToken, returnedUsername};

        } catch (IOException e) {
            Logger.tag(TAG).warn("Error for URL " + url, e);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

        return new String[]{null, context.getString(R.string.authentication_failed)};
    }


    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(Observation observation) {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String name = "observation_" + observation.created_at.getTime() + "_" + timeStamp;
        values.put(android.provider.MediaStore.Images.Media.TITLE, name);
        return mContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }


    public void syncJson(JSONArray json, boolean isUser) {
        synchronized (mSyncJsonLock) {
            ArrayList<Integer> ids = new ArrayList<Integer>();
            ArrayList<Integer> existingIds = new ArrayList<Integer>();
            ArrayList<Integer> newIds = new ArrayList<Integer>();
            HashMap<Integer, Observation> jsonObservationsById = new HashMap<Integer, Observation>();
            Observation observation;
            Observation jsonObservation;

            if (mSyncedJSONs.contains(json.toString())) {
                // Already synced this exact JSON recently
                Logger.tag(TAG).info("Skipping syncJSON - already synced same JSON");
                return;
            }

            mSyncedJSONs.add(json.toString());

            BetterJSONObject o;

            Logger.tag(TAG).debug("syncJson: " + isUser);
            Logger.tag(TAG).debug(json.toString());

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
                        SerializableJSONArray arr = o.getJSONArray(o.has("ofvs") ? "ofvs" : "observation_field_values");
                        if (arr == null) continue;

                        JSONArray jsonFields = arr.getJSONArray();

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
                    Logger.tag(TAG).error("syncJson: JSONException: " + e.toString());
                }
            }
            // find obs with existing ids
            String joinedIds = StringUtils.join(ids, ",");
            // TODO why doesn't selectionArgs work for id IN (?)
            Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "id IN (" + joinedIds + ")", null, Observation.DEFAULT_SORT_ORDER);

            // update existing
            c.moveToFirst();
            ContentValues cv;
            while (c.isAfterLast() == false) {
                observation = new Observation(c);
                jsonObservation = jsonObservationsById.get(observation.id);
                boolean isModified = observation.merge(jsonObservation);

                Logger.tag(TAG).debug("syncJson - updating existing: " + observation.id + ":" + observation._id + ":" + observation.preferred_common_name + ":" + observation.taxon_id + "; updated at = " + observation._updated_at + " / " + observation._updated_at);
                Logger.tag(TAG).debug("syncJson - remote obs: " + jsonObservation.id + ":" + jsonObservation.preferred_common_name + ":" + jsonObservation.taxon_id + "; updated_at = " + jsonObservation.updated_at );

                cv = observation.getContentValues();
                if (observation._updated_at.before(jsonObservation.updated_at)) {
                    // Remote observation is newer (and thus has overwritten the local one) - update its
                    // sync at time so we won't update the remote servers later on (since we won't
                    // accidentally consider this an updated record)
                    cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                }

                // Add any new photos that were added remotely
                ArrayList<Integer> observationPhotoIds = new ArrayList<Integer>();
                HashMap<Integer, ObservationPhoto> localPhotos = new HashMap<>();
                Cursor pc = mContext.getContentResolver().query(
                        ObservationPhoto.CONTENT_URI,
                        ObservationPhoto.PROJECTION,
                        "(observation_id = " + observation.id + ")",
                        null, null);
                pc.moveToFirst();
                while (pc.isAfterLast() == false) {
                    int photoId = pc.getInt(pc.getColumnIndexOrThrow(ObservationPhoto.ID));
                    if (photoId != 0) {
                        localPhotos.put(photoId, new ObservationPhoto(pc));
                    }
                    pc.moveToNext();
                }
                pc.close();
                Logger.tag(TAG).debug("syncJson: Adding photos for obs " + observation.id + ":" + localPhotos.toString());
                Logger.tag(TAG).debug("syncJson: JsonObservation: " + jsonObservation + ":" + jsonObservation.photos);
                for (int j = 0; j < jsonObservation.photos.size(); j++) {
                    ObservationPhoto photo = jsonObservation.photos.get(j);
                    photo._observation_id = jsonObservation._id;

                    if (photo.id == null) {
                        Logger.tag(TAG).warn("syncJson: Null photo ID! " + photo);
                        continue;
                    }

                    observationPhotoIds.add(photo.id);
                    if (localPhotos.containsKey(photo.id)) {
                        ObservationPhoto localPhoto = localPhotos.get(photo.id);

                        Logger.tag(TAG).debug("syncJson: photo " + photo.id + " has already been added");
                        localPhoto.merge(photo, true);
                        Logger.tag(TAG).debug("syncJson: merged: " + localPhoto.isDirty() + ":" + localPhoto);
                        if (localPhoto.isDirty()) {
                            ContentValues opcv = localPhoto.getContentValues();
                            Logger.tag(TAG).debug("syncJson: Setting _SYNCED_AT - " + localPhoto.id + ":" + localPhoto._id + ":" + localPhoto._observation_id + ":" + localPhoto.observation_id + ":" + opcv);
                            opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                            mContext.getContentResolver().update(localPhoto.getUri(), opcv, null, null);
                        }
                        continue;
                    }
                    ContentValues opcv = photo.getContentValues();
                    // So we won't re-add this photo as though it was a local photo
                    Logger.tag(TAG).debug("syncJson: Setting _SYNCED_AT - " + photo.id + ":" + photo._id + ":" + photo._observation_id + ":" + photo.observation_id);
                    opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                    opcv.put(ObservationPhoto._OBSERVATION_ID, observation._id);
                    opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
                    opcv.put(ObservationPhoto.ID, photo.id);

                    Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                            Observation.PROJECTION,
                            "id = " + photo.observation_id, null, Observation.DEFAULT_SORT_ORDER);
                    if (obsc.getCount() > 0) {
                        obsc.moveToFirst();
                        Observation obs = new Observation(obsc);
                        opcv.put(ObservationPhoto.OBSERVATION_UUID, obs.uuid);
                    }
                    obsc.close();
                    try {
                        mContext.getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
                    } catch (SQLException ex) {
                        // Happens when the photo already exists - ignore
                        Logger.tag(TAG).error(ex);
                    }
                }

                // Delete photos that were synced but weren't present in the remote response,
                // indicating they were deleted elsewhere
                String joinedPhotoIds = StringUtils.join(observationPhotoIds, ",");
                String where = "observation_id = " + observation.id + " AND id IS NOT NULL";
                if (observationPhotoIds.size() > 0) {
                    where += " AND id NOT in (" + joinedPhotoIds + ")";
                }
                Logger.tag(TAG).debug("syncJson: Deleting local photos: " + where);
                Logger.tag(TAG).debug("syncJson: Deleting local photos, IDs: " + observationPhotoIds);
                int deleteCount = mContext.getContentResolver().delete(
                        ObservationPhoto.CONTENT_URI,
                        where,
                        null);
                Logger.tag(TAG).debug("syncJson: Deleting local photos: " + deleteCount);

                if (deleteCount > 0) {
                    Logger.tag(TAG).error(String.format(Locale.ENGLISH, "Warning: Deleted %d photos locally after sever did not contain those IDs - observation id: %s, photo ids: %s",
                            deleteCount, observation.id, joinedPhotoIds));
                }

                // Add any new sounds that were added remotely
                ArrayList<Integer> observationSoundIds = new ArrayList<Integer>();
                ArrayList<Integer> existingObservationSoundIds = new ArrayList<Integer>();
                Cursor sc = mContext.getContentResolver().query(
                        ObservationSound.CONTENT_URI,
                        ObservationSound.PROJECTION,
                        "(observation_id = " + observation.id + ")",
                        null, null);
                sc.moveToFirst();
                while (sc.isAfterLast() == false) {
                    int soundId = sc.getInt(sc.getColumnIndexOrThrow(ObservationSound.ID));
                    if (soundId != 0) {
                        existingObservationSoundIds.add(soundId);
                    }
                    sc.moveToNext();
                }
                sc.close();
                Logger.tag(TAG).debug("syncJson: Adding sounds for obs " + observation.id + ":" + existingObservationSoundIds.toString());
                Logger.tag(TAG).debug("syncJson: JsonObservation: " + jsonObservation + ":" + jsonObservation.sounds);
                for (int j = 0; j < jsonObservation.sounds.size(); j++) {
                    ObservationSound sound = jsonObservation.sounds.get(j);
                    sound._observation_id = jsonObservation._id;

                    if (sound.id == null) {
                        Logger.tag(TAG).warn("syncJson: Null sound ID! " + sound);
                        continue;
                    }

                    observationSoundIds.add(sound.id);
                    if (existingObservationSoundIds.contains(sound.id)) {
                        Logger.tag(TAG).debug("syncJson: sound " + sound.id + " has already been added, skipping...");
                        continue;
                    }
                    ContentValues oscv = sound.getContentValues();
                    oscv.put(ObservationSound._OBSERVATION_ID, sound.observation_id);
                    oscv.put(ObservationSound.ID, sound.id);

                    Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                            Observation.PROJECTION,
                            "id = " + sound.observation_id, null, Observation.DEFAULT_SORT_ORDER);
                    if (obsc.getCount() > 0) {
                        obsc.moveToFirst();
                        Observation obs = new Observation(obsc);
                        oscv.put(ObservationSound.OBSERVATION_UUID, obs.uuid);
                    }
                    obsc.close();
                    try {
                        mContext.getContentResolver().insert(ObservationSound.CONTENT_URI, oscv);
                    } catch (SQLException ex) {
                        // Happens when the sound already exists - ignore
                        Logger.tag(TAG).error(ex);
                    }
                }

                // Delete sounds that were synced but weren't present in the remote response,
                // indicating they were deleted elsewhere
                String joinedSoundIds = StringUtils.join(observationSoundIds, ",");
                where = "observation_id = " + observation.id + " AND id IS NOT NULL";
                if (observationSoundIds.size() > 0) {
                    where += " AND id NOT in (" + joinedSoundIds + ")";
                }
                Logger.tag(TAG).debug("syncJson: Deleting local sounds: " + where);
                Logger.tag(TAG).debug("syncJson: Deleting local sounds, IDs: " + observationSoundIds);
                deleteCount = mContext.getContentResolver().delete(
                        ObservationSound.CONTENT_URI,
                        where,
                        null);
                Logger.tag(TAG).debug("syncJson: Deleting local sounds: " + deleteCount);

                if (deleteCount > 0) {
                    Logger.tag(TAG).error(String.format(Locale.ENGLISH, "Warning: Deleted %d sounds locally after server did not contain those IDs - observation id: %s, sound ids: %s",
                            deleteCount, observation.id, joinedSoundIds));
                }


                if (isModified) {
                    // Only update the DB if needed
                    Logger.tag(TAG).debug("syncJson: Updating observation: " + observation.id + ":" + observation._id);
                    mContext.getContentResolver().update(observation.getUri(), cv, null, null);
                }
                existingIds.add(observation.id);
                c.moveToNext();
            }
            c.close();

            // insert new
            List<Observation> newObservations = new ArrayList<Observation>();
            newIds = (ArrayList<Integer>) CollectionUtils.subtract(ids, existingIds);
            Collections.sort(newIds);
            Logger.tag(TAG).debug("syncJson: Adding new observations: " + newIds);
            for (int i = 0; i < newIds.size(); i++) {
                jsonObservation = jsonObservationsById.get(newIds.get(i));

                Cursor c2 = mContext.getContentResolver().query(Observation.CONTENT_URI,
                        Observation.PROJECTION,
                        "id = ?", new String[]{String.valueOf(jsonObservation.id)}, Observation.DEFAULT_SORT_ORDER);
                int count = c2.getCount();
                c2.close();

                if (count > 0) {
                    Logger.tag(TAG).debug("syncJson: Observation " + jsonObservation.id + " already exists locally - not adding");
                    continue;
                }

                cv = jsonObservation.getContentValues();
                cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
                cv.put(Observation.LAST_COMMENTS_COUNT, jsonObservation.comments_count);
                cv.put(Observation.LAST_IDENTIFICATIONS_COUNT, jsonObservation.identifications_count);
                Uri newObs = mContext.getContentResolver().insert(Observation.CONTENT_URI, cv);
                Long newObsId = ContentUris.parseId(newObs);
                jsonObservation._id = Integer.valueOf(newObsId.toString());
                Logger.tag(TAG).debug("syncJson: Adding new obs: " + jsonObservation);
                newObservations.add(jsonObservation);
            }

            if (isUser) {
                for (int i = 0; i < newObservations.size(); i++) {
                    jsonObservation = newObservations.get(i);

                    // Save new observation's sounds
                    Logger.tag(TAG).debug("syncJson: Saving new obs' sounds: " + jsonObservation + ":" + jsonObservation.sounds);
                    for (int j = 0; j < jsonObservation.sounds.size(); j++) {
                        ObservationSound sound = jsonObservation.sounds.get(j);

                        c = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                                ObservationSound.PROJECTION,
                                "id = ?", new String[]{String.valueOf(sound.id)}, ObservationSound.DEFAULT_SORT_ORDER);

                        if (c.getCount() > 0) {
                            // Sound already exists - don't save
                            Logger.tag(TAG).debug("syncJson: Sound already exists - skipping: " + sound.id);
                            c.close();
                            continue;
                        }

                        c.close();

                        sound._observation_id = jsonObservation._id;

                        ContentValues opcv = sound.getContentValues();
                        Logger.tag(TAG).debug("syncJson: Setting _SYNCED_AT - " + sound.id + ":" + sound._id + ":" + sound._observation_id + ":" + sound.observation_id);
                        Logger.tag(TAG).debug("syncJson: Setting _SYNCED_AT - " + sound);
                        opcv.put(ObservationSound._OBSERVATION_ID, sound._observation_id);
                        opcv.put(ObservationSound._ID, sound.id);
                        Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                                Observation.PROJECTION,
                                "id = " + sound.observation_id, null, Observation.DEFAULT_SORT_ORDER);
                        if (obsc.getCount() > 0) {
                            obsc.moveToFirst();
                            Observation obs = new Observation(obsc);
                            opcv.put(ObservationSound.OBSERVATION_UUID, obs.uuid);
                        }
                        obsc.close();
                        try {
                            mContext.getContentResolver().insert(ObservationSound.CONTENT_URI, opcv);
                        } catch (SQLException ex) {
                            // Happens when the sound already exists - ignore
                            Logger.tag(TAG).error(ex);
                        }
                    }

                    // Save the new observation's photos
                    Logger.tag(TAG).debug("syncJson: Saving new obs' photos: " + jsonObservation + ":" + jsonObservation.photos);
                    for (int j = 0; j < jsonObservation.photos.size(); j++) {
                        ObservationPhoto photo = jsonObservation.photos.get(j);

                        if (photo.uuid == null) {
                            c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                                    ObservationPhoto.PROJECTION,
                                    "_id = ?", new String[]{String.valueOf(photo.id)}, ObservationPhoto.DEFAULT_SORT_ORDER);
                        } else {
                            c = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                                    ObservationPhoto.PROJECTION,
                                    "uuid = ?", new String[]{String.valueOf(photo.uuid)}, ObservationPhoto.DEFAULT_SORT_ORDER);
                        }

                        if (c.getCount() > 0) {
                            // Photo already exists - don't save
                            Logger.tag(TAG).debug("syncJson: Photo already exists - skipping: " + photo.id);
                            c.close();
                            continue;
                        }

                        c.close();

                        photo._observation_id = jsonObservation._id;

                        ContentValues opcv = photo.getContentValues();
                        Logger.tag(TAG).debug("syncJson: Setting _SYNCED_AT - " + photo.id + ":" + photo._id + ":" + photo._observation_id + ":" + photo.observation_id);
                        Logger.tag(TAG).debug("syncJson: Setting _SYNCED_AT - " + photo);
                        opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis()); // So we won't re-add this photo as though it was a local photo
                        opcv.put(ObservationPhoto._OBSERVATION_ID, photo._observation_id);
                        opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
                        opcv.put(ObservationPhoto._ID, photo.id);
                        Cursor obsc = mContext.getContentResolver().query(Observation.CONTENT_URI,
                                Observation.PROJECTION,
                                "id = " + photo.observation_id, null, Observation.DEFAULT_SORT_ORDER);
                        if (obsc.getCount() > 0) {
                            obsc.moveToFirst();
                            Observation obs = new Observation(obsc);
                            opcv.put(ObservationPhoto.OBSERVATION_UUID, obs.uuid);
                        }
                        obsc.close();
                        try {
                            mContext.getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
                        } catch (SQLException ex) {
                            // Happens when the photo already exists - ignore
                            Logger.tag(TAG).error(ex);
                        }
                    }
                }
            }

            if (isUser) {
                storeProjectObservations();
            }

            if (mSyncedJSONs.size() > 5) {
                mSyncedJSONs.remove(0);
            }
        }
    }

    private JSONObject observationToJsonObject(Observation observation, boolean isPOST) {
        JSONObject obs = observation.toJSONObject(true);
        try {

            if (isPOST) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                obs.put("site_id", mApp.getStringResourceByName("inat_site_id_" + inatNetwork));
            }

            if (obs.has("longitude") && !obs.isNull("longitude")) {
                if (obs.getString("longitude").equals("0.0")) {
                    // Handle edge cases where long/lat was saved as 0.0 - just don't send a location
                    obs.remove("longitude");
                }
            }
            if (obs.has("latitude") && !obs.isNull("latitude")) {
                if (obs.getString("latitude").equals("0.0")) {
                    // Handle edge cases where long/lat was saved as 0.0 - just don't send a location
                    obs.remove("latitude");
                }
            }
            if (!isPOST) {
                // It's an observation update (PUT request) - instead of null values for location/place_guess - write empty strings
                if (obs.isNull("latitude")) obs.put("latitude", "");
                if (obs.isNull("longitude")) obs.put("longitude", "");
                if (obs.isNull("private_latitude")) obs.put("private_latitude", "");
                if (obs.isNull("private_longitude")) obs.put("private_longitude", "");
                if (obs.isNull("location")) obs.put("location", "");
                if (obs.isNull("private_location")) obs.put("private_location", "");
                if (obs.isNull("place_guess")) obs.put("place_guess", "");
                if (obs.isNull("private_place_guess")) obs.put("private_place_guess", "");
                if (obs.isNull("location")) obs.put("location", "");
                if (obs.isNull("private_location")) obs.put("private_location", "");
            }

            JSONObject obsContainer = new JSONObject();
            obsContainer.put("observation", obs);
            obsContainer.put("ignore_photos", true);

            return obsContainer;
        } catch (JSONException exc) {
            Logger.tag(TAG).error(exc);
            return null;
        }
    }

    private boolean handleObservationResponse(Observation observation, JSONArray response) {
        try {
            if (response == null || response.length() != 1) {
                return false;
            }
            JSONObject json = response.getJSONObject(0);
            BetterJSONObject o = new BetterJSONObject(json);
            Logger.tag(TAG).debug("handleObservationResponse: Observation: " + observation);
            Logger.tag(TAG).debug("handleObservationResponse: JSON: ");
            Logger.tag(TAG).debug(json.toString());

            if ((json.has("error") && !json.isNull("error")) || ((mLastStatusCode >= 400) && (mLastStatusCode < 500))) {
                // Error
                Logger.tag(TAG).debug("handleObservationResponse - error response (probably validation error)");
                JSONObject original = json.optJSONObject("error").optJSONObject("original");
                if ((original != null) && (original.has("error")) && (!original.isNull("error"))) {
                    JSONArray errors = new JSONArray();
                    errors.put(original.optString("error").trim());
                    mApp.setErrorsForObservation(observation.id != null ? observation.id : observation._id, 0, errors);
                }

                return false;
            } else if (mLastStatusCode == HTTP_UNAVAILABLE) {
                // Server not available
                Logger.tag(TAG).error("503 - server not available");
                return false;
            }

            Observation jsonObservation = new Observation(o);
            Logger.tag(TAG).debug("handleObservationResponse: jsonObservation: " + jsonObservation);
            observation.merge(jsonObservation);
            Logger.tag(TAG).debug("handleObservationResponse: merged obs: " + observation);
            ContentValues cv = observation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            mContext.getContentResolver().update(observation.getUri(), cv, null, null);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return false;
        }

        return true;
    }

}
