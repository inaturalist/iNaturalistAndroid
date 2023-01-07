package org.inaturalist.android;

import static org.inaturalist.android.DataBuilderExtensionKt.putSerializable;

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
import java.net.SocketTimeoutException;
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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.maps.GeoApiContext;
import com.google.maps.TimeZoneApi;
import com.google.maps.model.LatLng;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import org.inaturalist.android.DataBuilderExtensionKt.*;

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
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

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

import static java.net.HttpURLConnection.HTTP_GONE;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

public class INaturalistService extends IntentService {
    // How many observations should we initially download for the user
    public static final int INITIAL_SYNC_OBSERVATION_COUNT = 100;

    public static final int JWT_TOKEN_EXPIRATION_MINS = 25; // JWT Tokens expire after 30 mins - consider 25 mins as the max time (safe margin)
    public static final int OLD_PHOTOS_MAX_COUNT = 100; // Number of cached photos to save before removing them and turning them into online photos
    public static final int MAX_PHOTO_REPLACEMENTS_PER_RUN = 50; // Max number of photo replacements we'll do per run

    public static final String IS_SHARED_ON_APP = "is_shared_on_app";

    public static final Map<String, String> TIMEZONE_ID_TO_INAT_TIMEZONE;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final long HTTP_CONNECTION_TIMEOUT_SECONDS = 10;
    public static final long HTTP_READ_WRITE_TIMEOUT_SECONDS = 40;

    static {
        TIMEZONE_ID_TO_INAT_TIMEZONE = new HashMap();

        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Algiers", "West Central Africa");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Cairo", "Cairo");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Casablanca", "Casablanca");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Harare", "Harare");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Johannesburg", "Pretoria");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Monrovia", "Monrovia");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Africa/Nairobi", "Nairobi");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Argentina/Buenos_Aires", "Buenos Aires");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Bogota", "Bogota");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Caracas", "Caracas");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Chicago", "Central Time (US & Canada)");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Chihuahua", "Chihuahua");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Denver", "Mountain Time (US & Canada)");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Godthab", "Greenland");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Guatemala", "Central America");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Guyana", "Georgetown");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Halifax", "Atlantic Time (Canada)");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Indiana/Indianapolis", "Indiana (East)");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Juneau", "Alaska");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/La_Paz", "La Paz");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Lima", "Lima");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Los_Angeles", "Pacific Time (US & Canada)");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Mazatlan", "Mazatlan");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Mexico_City", "Mexico City");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Monterrey", "Monterrey");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Montevideo", "Montevideo");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/New_York", "Eastern Time (US & Canada)");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Phoenix", "Arizona");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Regina", "Saskatchewan");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Santiago", "Santiago");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Sao_Paulo", "Brasilia");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/St_Johns", "Newfoundland");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("America/Tijuana", "Tijuana");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Almaty", "Almaty");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Baghdad", "Baghdad");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Baku", "Baku");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Bangkok", "Bangkok");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Chongqing", "Chongqing");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Colombo", "Sri Jayawardenepura");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Dhaka", "Dhaka");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Hong_Kong", "Hong Kong");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Irkutsk", "Irkutsk");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Jakarta", "Jakarta");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Jerusalem", "Jerusalem");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Kabul", "Kabul");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Kamchatka", "Kamchatka");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Karachi", "Karachi");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Kathmandu", "Kathmandu");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Kolkata", "Kolkata");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Krasnoyarsk", "Krasnoyarsk");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Kuala_Lumpur", "Kuala Lumpur");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Kuwait", "Kuwait");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Magadan", "Magadan");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Muscat", "Muscat");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Novosibirsk", "Novosibirsk");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Rangoon", "Rangoon");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Riyadh", "Riyadh");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Seoul", "Seoul");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Shanghai", "Beijing");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Singapore", "Singapore");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Srednekolymsk", "Srednekolymsk");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Taipei", "Taipei");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Tashkent", "Tashkent");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Tbilisi", "Tbilisi");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Tehran", "Tehran");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Tokyo", "Tokyo");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Ulaanbaatar", "Ulaanbaatar");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Urumqi", "Urumqi");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Vladivostok", "Vladivostok");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Yakutsk", "Yakutsk");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Yekaterinburg", "Ekaterinburg");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Asia/Yerevan", "Yerevan");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Atlantic/Azores", "Azores");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Atlantic/Cape_Verde", "Cape Verde Is.");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Atlantic/South_Georgia", "Mid-Atlantic");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Adelaide", "Adelaide");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Brisbane", "Brisbane");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Darwin", "Darwin");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Hobart", "Hobart");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Melbourne", "Melbourne");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Perth", "Perth");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Australia/Sydney", "Sydney");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Etc/UTC", "UTC");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Amsterdam", "Amsterdam");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Athens", "Athens");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Belgrade", "Belgrade");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Berlin", "Berlin");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Bratislava", "Bratislava");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Brussels", "Brussels");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Bucharest", "Bucharest");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Budapest", "Budapest");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Copenhagen", "Copenhagen");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Dublin", "Dublin");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Helsinki", "Helsinki");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Istanbul", "Istanbul");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Kaliningrad", "Kaliningrad");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Kiev", "Kyiv");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Lisbon", "Lisbon");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Ljubljana", "Ljubljana");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/London", "London");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Madrid", "Madrid");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Minsk", "Minsk");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Moscow", "Moscow");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Paris", "Paris");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Prague", "Prague");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Riga", "Riga");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Rome", "Rome");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Samara", "Samara");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Sarajevo", "Sarajevo");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Skopje", "Skopje");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Sofia", "Sofia");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Stockholm", "Stockholm");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Tallinn", "Tallinn");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Vienna", "Vienna");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Vilnius", "Vilnius");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Volgograd", "Volgograd");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Warsaw", "Warsaw");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Europe/Zagreb", "Zagreb");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Apia", "Samoa");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Auckland", "Auckland");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Chatham", "Chatham Is.");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Fakaofo", "Tokelau Is.");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Fiji", "Fiji");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Guadalcanal", "Solomon Is.");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Guam", "Guam");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Honolulu", "Hawaii");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Majuro", "Marshall Is.");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Midway", "Midway Island");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Noumea", "New Caledonia");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Pago_Pago", "American Samoa");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Port_Moresby", "Port Moresby");
        TIMEZONE_ID_TO_INAT_TIMEZONE.put("Pacific/Tongatapu", "Nuku'alofa");
    }

    public static final String SUGGESTION_SOURCE = "suggestion_source";
    public static final String SUGGESTION_SOURCE_VISUAL = "visual";
    public static final String SUGGESTION_SOURCE_RESEARCH_GRADE_OBS = "rg_observations";
    public static final String USER = "user";
    public static final String AUTHENTICATION_FAILED = "authentication_failed";
    public static final String USER_DELETED = "user_deleted";
    public static final String IDENTIFICATION_ID = "identification_id";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String GET_PROJECTS = "get_projects";
    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
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
    public static final String FLAGGABLE_TYPE = "flaggable_type";
    public static final String FLAGGABLE_ID = "flaggable_id";
    public static final String FLAG = "flag";
    public static final String FLAG_EXPLANATION = "flag_explanation";
    public static final String OBSERVATION_COUNT = "observation_count";
    public static final String PROJECTS_RESULT = "projects_result";
    public static final String IDENTIFICATIONS_RESULT = "identifications_result";
    public static final String EXPLORE_GET_OBSERVATIONS_RESULT = "explore_get_observations_result";
    public static final String EXPLORE_GET_SPECIES_RESULT = "explore_get_species_result";
    public static final String EXPLORE_GET_IDENTIFIERS_RESULT = "explore_get_identifiers_result";
    public static final String EXPLORE_GET_OBSERVERS_RESULT = "explore_get_observers_result";
    public static final String GET_TOP_OBSERVERS_RESULT = "get_top_observers_result";
    public static final String GET_TOP_IDENTIFIERS_RESULT = "get_top_identifiers_result";
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
    public static final String PLACE_DETAILS_RESULT = "place_details_result";
    public static final String REFRESH_CURRENT_USER_SETTINGS_RESULT = "refresh_current_user_settings_result";
    public static final String UPDATE_CURRENT_USER_DETAILS_RESULT = "update_current_user_details_result";
    public static final String OBSERVATION_SYNC_PROGRESS = "observation_sync_progress";
    public static final String ADD_OBSERVATION_TO_PROJECT_RESULT = "add_observation_to_project_result";
    public static final String DELETE_ACCOUNT_RESULT = "delete_account_result";
    public static final String TAXON_ID = "taxon_id";
    public static final String ANCESTORS = "ancestors";
    public static final String PLACE_LAT = "place_lat";
    public static final String PLACE_LNG = "place_lng";
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
    public static final String ACTION = "action";
    public static final String REQUEST_UUID = "request_uuid";
    public static final String ACTION_CHECK_LIST_RESULT = "action_check_list_result";
    public static final String ACTION_MESSAGES_RESULT = "action_messages_result";
    public static final String ACTION_NOTIFICATION_COUNTS_RESULT = "action_notification_counts_result";
    public static final String ACTION_POST_MESSAGE_RESULT = "action_post_message_result";
    public static final String ACTION_MUTE_USER_RESULT = "action_mute_user_result";
    public static final String ACTION_POST_FLAG_RESULT = "action_post_flag_result";
    public static final String ACTION_UNMUTE_USER_RESULT = "action_unmute_user_result";
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
    public static final String ACCURACY = "accuracy";
    public static final String TITLE = "title";
    public static final String GEOPRIVACY = "geoprivacy";
    public static final String LATITUDE = "latitude";
    public static final String OBSERVED_ON = "observed_on";
    public static final String USERNAME = "username";
    public static final String PLACE = "place";
    public static final String PLACE_ID = "place_id";
    public static final String LOCATION = "location";
    public static final String FILTERS = "filters";
    public static final String PROGRESS = "progress";
    public static final String EXPAND_LOCATION_BY_DEGREES = "expand_location_by_degrees";
    public static final String QUERY = "query";
    public static final String BOX = "box";
    public static final String GROUP_BY_THREADS = "group_by_threads";
    public static final String MESSAGE_ID = "message_id";
    public static final String NOTIFICATIONS = "notifications";
    public static final String TO_USER = "to_user";
    public static final String THREAD_ID = "thread_id";
    public static final String SUBJECT = "subject";
    public static final String BODY = "body";
    public static final String OBSERVATIONS = "observations";
    public static final String IDENTIFICATIONS = "identifications";
    public static final String LIFE_LIST_ID = "life_list_id";
    public static final String PASSWORD = "password";
    public static final String LICENSE = "license";
    public static final String RESULTS = "results";
    public static final String LIFE_LIST = "life_list";
    public static final String REGISTER_USER_ERROR = "error";
    public static final String REGISTER_USER_STATUS = "status";
    public static final String REGISTER_EMAIL_VERIFICATION_REQUIRED = "email_verification_required";
    public static final String SYNC_CANCELED = "sync_canceled";
    public static final String SYNC_FAILED = "sync_failed";
    public static final String FIRST_SYNC = "first_sync";
    public static final String PAGE_NUMBER = "page_number";
    public static final String PAGE_SIZE = "page_size";
    public static final String TAXON_IDS = "taxon_ids";
    public static final String ID = "id";
    public static final String OBS_IDS_TO_SYNC = "obs_ids_to_sync";
    public static final String OBS_IDS_TO_DELETE = "obs_ids_to_delete";

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
            android.os.Build.PRODUCT + "; OS Version " +
            android.os.Build.VERSION.RELEASE +
            ")";
    public static String ACTION_GET_HISTOGRAM = "action_get_histogram";
    public static String ACTION_GET_POPULAR_FIELD_VALUES = "action_get_popular_field_values";
    public static String ACTION_ADD_MISSING_OBS_UUID = "action_add_missing_obs_uuid";
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
    public static String ACTION_DELETE_OBSERVATIONS = "delete_observations";
    public static String ACTION_GET_OBSERVATION = "get_observation";
    public static String ACTION_GET_AND_SAVE_OBSERVATION = "get_and_save_observation";
    public static String ACTION_FLAG_OBSERVATION_AS_CAPTIVE = "flag_observation_as_captive";
    public static String ACTION_FOLLOW_OBSERVATION = "follow_observation";
    public static String ACTION_GET_OBSERVATION_SUBSCRIPTIONS = "action_get_observation_subscriptions";
    public static String ACTION_GET_MESSAGES = "get_messages";
    public static String ACTION_GET_NOTIFICATION_COUNTS = "get_notification_counts";
    public static String ACTION_POST_MESSAGE = "post_message";
    public static String ACTION_MUTE_USER = "mute_user";
    public static String ACTION_POST_FLAG = "post_flag";
    public static String ACTION_UNMUTE_USER = "unmute_user";
    public static String ACTION_JOIN_PROJECT = "join_project";
    public static String ACTION_LEAVE_PROJECT = "leave_project";
    public static String ACTION_GET_JOINED_PROJECTS = "get_joined_projects";
    public static String ACTION_GET_JOINED_PROJECTS_ONLINE = "get_joined_projects_online";
    public static String ACTION_GET_NEARBY_PROJECTS = "get_nearby_projects";
    public static String ACTION_GET_FEATURED_PROJECTS = "get_featured_projects";
    public static String ACTION_REDOWNLOAD_OBSERVATIONS_FOR_TAXON = "redownload_observations_for_taxon";
    public static String ACTION_SYNC_JOINED_PROJECTS = "sync_joined_projects";
    public static String ACTION_DELETE_ACCOUNT = "delete_account";
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
    public static String ACTION_FOLLOW_OBSERVATION_RESULT = "action_follow_observation_result";
    public static String ACTION_GET_OBSERVATION_SUBSCRIPTIONS_RESULT = "action_get_observation_subscriptions_result";
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
    public static String ACTION_USER_LICENSE = "user_license";
    public static String ACTION_USER_PHOTO_LICENSE = "user_photo_license";
    public static String ACTION_USER_SOUND_LICENSE = "user_sound_license";
    public static String ACTION_MAKE_LICENSE_SAME = "make_license_same";
    public static String ACTION_MAKE_PHOTO_LICENSE_SAME = "make_photo_license_same";
    public static String ACTION_MAKE_SOUND_LICENSE_SAME = "make_sound_license_same";
    public static String ACTION_USER_DELETE_PIC = "user_delete_pic";
    public static String ACTION_REGISTER_USER_RESULT = "register_user_result";
    public static String TAXA_GUIDE_RESULT = "taxa_guide_result";
    public static String ACTION_GET_SPECIFIC_USER_DETAILS = "get_specific_user_details";
    public static String ACTION_GET_PLACE_DETAILS = "get_place_details";
    public static String ACTION_PIN_LOCATION = "pin_location";
    public static String ACTION_DELETE_PINNED_LOCATION = "delete_pinned_location";
    public static String ACTION_REFRESH_CURRENT_USER_SETTINGS = "refresh_current_user_settings";
    public static String ACTION_UPDATE_CURRENT_USER_DETAILS = "update_current_user_details";
    public static String ACTION_GET_CURRENT_LOCATION = "get_current_location";
    public static String ACTION_GET_USER_SPECIES_COUNT = "get_species_count";
    public static String ACTION_GET_USER_IDENTIFICATIONS = "get_user_identifications";
    public static String ACTION_GET_USER_UPDATES = "get_user_udpates";
    public static String ACTION_EXPLORE_GET_OBSERVATIONS = "explore_get_observations";
    public static String ACTION_EXPLORE_GET_SPECIES = "explore_get_species";
    public static String ACTION_EXPLORE_GET_IDENTIFIERS = "explore_get_identifiers";
    public static String ACTION_EXPLORE_GET_OBSERVERS = "explore_get_observers";
    public static String ACTION_GET_TOP_OBSERVERS = "action_get_top_observers";
    public static String ACTION_GET_TOP_IDENTIFIERS = "action_get_top_identifiers";
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
    private INaturalistApp mApp;

    public INaturalistService() {
        super("INaturalistService");
    }

    @Override
    public void onCreate() {
        Logger.tag(TAG).info("Service onCreate");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.tag(TAG).info("Service onStartCommand");

        mApp = (INaturalistApp) getApplicationContext();

        if (intent == null) {
            return super.onStartCommand(intent, flags, startId);
        }

        // Only use the notification for actions for which their response is crucial (e.g. syncing)
        // (but make sure we call it at least once)
        String action = intent.getAction();
        Logger.tag(TAG).info("Should call startIntentForeground? " + mApp.hasCalledStartForeground() + ":" + action);

        if (!mApp.hasCalledStartForeground() ||
                Arrays.asList(new String[]{
                    ACTION_DELETE_OBSERVATIONS, ACTION_DELETE_ACCOUNT, ACTION_FIRST_SYNC,
                    ACTION_GET_AND_SAVE_OBSERVATION, ACTION_JOIN_PROJECT, ACTION_LEAVE_PROJECT,
                    ACTION_PASSIVE_SYNC, ACTION_POST_MESSAGE, ACTION_PULL_OBSERVATIONS,
                    ACTION_REDOWNLOAD_OBSERVATIONS_FOR_TAXON, ACTION_REFRESH_CURRENT_USER_SETTINGS,
                    ACTION_REGISTER_USER, ACTION_SYNC,
                    ACTION_SYNC_JOINED_PROJECTS, ACTION_UPDATE_USER_DETAILS, ACTION_UPDATE_USER_NETWORK
            }).contains(action)) {
            mApp.setCalledStartForeground(true);
            startIntentForeground();
        }

        return super.onStartCommand(intent, flags, startId);
    }


    private void startIntentForeground() {
        String channelId = "inaturalist_service";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Logger.tag(TAG).info("Service startIntentForeground");

            // See: https://stackoverflow.com/a/46449975/1233767
            NotificationChannel channel = new NotificationChannel(channelId,
                    " ",
                    NotificationManager.IMPORTANCE_LOW);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                    .setOngoing(false)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setContentTitle(getString(R.string.app_title))
                    .setContentText(getString(R.string.running_in_background_notfication))
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.drawable.ic_notification);
            Notification notification = builder.build();

            startForeground(101, notification);
        }
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                INaturalistServiceImplementation implementation = new INaturalistServiceImplementation(getBaseContext());
                implementation.onHandleIntentWorker(intent);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        mApp.setCalledStartForeground(false);
        Logger.tag(TAG).info("onDestroy");
        super.onDestroy();
    }

    // Call the iNaturalistService according to OS version - e.g. in Build API 31 and above,
    // calling using a foreground service, while application in the background, will generate
    // an exception.
    // See: https://stackoverflow.com/questions/70044393/fatal-android-12-exception-startforegroundservice-not-allowed-due-to-mallows/72131422#72131422
    public static void callService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            Data.Builder data = new Data.Builder();
            data.putString(ACTION, intent.getAction());

            String uuid = java.util.UUID.randomUUID().toString();
            data.putString(REQUEST_UUID, uuid);

            INaturalistApp app = (INaturalistApp) context.getApplicationContext();
            Bundle extras = intent.getExtras();
            app.setServiceParams(uuid, extras);

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(INatutralistServiceWorker.class).setInputData(data.build()).build();
            WorkManager.getInstance(context).enqueue(request);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

}

