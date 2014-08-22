package org.inaturalist.shedd.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.inaturalist.shedd.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

import android.app.IntentService;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

public class INaturalistService extends IntentService implements ConnectionCallbacks, OnConnectionFailedListener {
    // The project ID every observation will be added to by default
    public static int DEFAULT_PROJECT_ID = 2415; // SHEDD Project
    public static int DEFAULT_GUIDE_ID = 338; // Great Lakes Fishes Field Guide
    public static String DEFAULT_GUIDE_TITLE = "Great Lakes Fishes";

    // How many observations should we initially download for the user
    private static final int INITIAL_SYNC_OBSERVATION_COUNT = 100;
    
    private boolean mGetLocationForProjects = false; // if true -> we assume it's for near by guides
    
    
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_RESULT = "observation_result";
    public static final String PROJECTS_RESULT = "projects_result";
    public static final String ADD_OBSERVATION_TO_PROJECT_RESULT = "add_observation_to_project_result";
    public static final String TAXON_ID = "taxon_id";
    public static final String COMMENT_BODY = "comment_body";
    public static final String IDENTIFICATION_BODY = "id_body";
    public static final String PROJECT_ID = "project_id";
    public static final String CHECK_LIST_ID = "check_list_id";
    public static final String ACTION_CHECK_LIST_RESULT = "action_check_list_result";
    public static final String CHECK_LIST_RESULT = "check_list_result";
    public static final String ACTION_GET_TAXON_RESULT = "action_get_taxon_result";
    public static final String TAXON_RESULT = "taxon_result";

    public static String TAG = "INaturalistService";
    public static String HOST = "https://www.inaturalist.org";
//    public static String HOST = "http://10.0.2.2:3000";
    public static String MEDIA_HOST = HOST;
    public static String USER_AGENT = "iNaturalist/" + INaturalistApp.VERSION + " (" +
        "Android " + System.getProperty("os.version") + " " + android.os.Build.VERSION.INCREMENTAL + "; " +
        "SDK " + android.os.Build.VERSION.SDK_INT + "; " +
        android.os.Build.DEVICE + " " +
        android.os.Build.MODEL + " " + 
        android.os.Build.PRODUCT + ")";
    public static String ACTION_PASSIVE_SYNC = "passive_sync";
    public static String ACTION_ADD_IDENTIFICATION = "add_identification";
    public static String ACTION_GET_TAXON = "get_taxon";
    public static String ACTION_FIRST_SYNC = "first_sync";
    public static String ACTION_PULL_OBSERVATIONS = "pull_observations";
    public static String ACTION_GET_OBSERVATION = "get_observation";
    public static String ACTION_GET_CHECK_LIST = "get_check_list";
    public static String ACTION_JOIN_PROJECT = "join_project";
    public static String ACTION_LEAVE_PROJECT = "leave_project";
    public static String ACTION_GET_JOINED_PROJECTS = "get_joined_projects";
    public static String ACTION_GET_NEARBY_PROJECTS = "get_nearby_projects";
    public static String ACTION_GET_FEATURED_PROJECTS = "get_featured_projects";
    public static String ACTION_ADD_OBSERVATION_TO_PROJECT = "add_observation_to_project";
    public static String ACTION_REMOVE_OBSERVATION_FROM_PROJECT = "remove_observation_from_project";
    public static String ACTION_GET_ALL_GUIDES = "get_all_guides";
    public static String ACTION_GET_MY_GUIDES = "get_my_guides";
    public static String ACTION_GET_NEAR_BY_GUIDES = "get_near_by_guides";
    public static String ACTION_TAXA_FOR_GUIDE = "get_taxa_for_guide";
    public static String ACTION_SYNC = "sync";
    public static String ACTION_NEARBY = "nearby";
    public static String ACTION_AGREE_ID = "agree_id";
    public static String ACTION_GUIDE_ID = "guide_id";
    public static String ACTION_ADD_COMMENT = "add_comment";
    public static String ACTION_SYNC_COMPLETE = "sync_complete";
    public static String ACTION_OBSERVATION_RESULT = "observation_result";
    public static String ACTION_JOINED_PROJECTS_RESULT = "joined_projects_result";
    public static String ACTION_NEARBY_PROJECTS_RESULT = "nearby_projects_result";
    public static String ACTION_FEATURED_PROJECTS_RESULT = "featured_projects_result";
    public static String ACTION_ALL_GUIDES_RESULT = "all_guides_results";
    public static String ACTION_MY_GUIDES_RESULT = "my_guides_results";
    public static String ACTION_NEAR_BY_GUIDES_RESULT = "near_by_guides_results";
    public static String ACTION_TAXA_FOR_GUIDES_RESULT = "taxa_for_guides_results";
    public static String GUIDES_RESULT = "guides_result";
    public static String TAXA_GUIDE_RESULT = "taxa_guide_result";
    public static Integer SYNC_OBSERVATIONS_NOTIFICATION = 1;
    public static Integer SYNC_PHOTOS_NOTIFICATION = 2;
    public static Integer AUTH_NOTIFICATION = 3;
    private String mLogin;
    private String mCredentials;
    private SharedPreferences mPreferences;
    private boolean mPassive;
    private INaturalistApp mApp;
    private LoginType mLoginType;

    private boolean mIsSyncing;
    
    private Handler mHandler;

    private LocationClient mLocationClient;

    private ArrayList<SerializableJSONArray> mProjectObservations;
    
    private Hashtable<Integer, Hashtable<Integer, ProjectFieldValue>> mProjectFieldValues;

    private Header[] mResponseHeaders = null;

	private JSONArray mResponseErrors;
    
	public enum LoginType {
	    PASSWORD,
	    GOOGLE,
	    FACEBOOK
	};


    public INaturalistService() {
        super("INaturalistService");
        
        mHandler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        mLogin = mPreferences.getString("username", null);
        mCredentials = mPreferences.getString("credentials", null);
        mLoginType = LoginType.valueOf(mPreferences.getString("login_type", LoginType.PASSWORD.toString()));
        mApp = (INaturalistApp) getApplicationContext();
        String action = intent.getAction();
        mPassive = action.equals(ACTION_PASSIVE_SYNC);
        
        
        Log.d(TAG, "Service: " + action);

        try {
            if (action.equals(ACTION_NEARBY)) {
                getNearbyObservations(intent);
                
            } else if (action.equals(ACTION_FIRST_SYNC)) {
                joinProject(DEFAULT_PROJECT_ID); // Make sure user is always part of the default project
                saveJoinedProjects();
                getUserObservations(INITIAL_SYNC_OBSERVATION_COUNT);
                syncObservationFields();
                postProjectObservations();
                
            } else if (action.equals(ACTION_AGREE_ID)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                JSONObject result = agreeIdentification(observationId, taxonId);
                
                if (result != null) {
                	// Reload the observation at the end (need to refresh comment/ID list)
                	Observation observation = getObservation(observationId);

                	Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                	reply.putExtra(OBSERVATION_RESULT, observation);
                	sendBroadcast(reply);
                }
                
            } else if (action.equals(ACTION_ADD_IDENTIFICATION)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                String body = intent.getStringExtra(IDENTIFICATION_BODY);
                addIdentification(observationId, taxonId, body);
                
             } else if (action.equals(ACTION_GET_TAXON)) {
                int taxonId = intent.getIntExtra(TAXON_ID, 0);
                BetterJSONObject taxon = getTaxon(taxonId);
                
                Intent reply = new Intent(ACTION_GET_TAXON_RESULT);
                reply.putExtra(TAXON_RESULT, taxon);
                sendBroadcast(reply);

             } else if (action.equals(ACTION_ADD_COMMENT)) {
                int observationId = intent.getIntExtra(OBSERVATION_ID, 0);
                String body = intent.getStringExtra(COMMENT_BODY);
                addComment(observationId, body);

             } else if (action.equals(ACTION_TAXA_FOR_GUIDE)) {
                int guideId = intent.getIntExtra(ACTION_GUIDE_ID, 0);
                SerializableJSONArray taxa = getTaxaForGuide(guideId);

                Intent reply = new Intent(ACTION_TAXA_FOR_GUIDES_RESULT);
                reply.putExtra(TAXA_GUIDE_RESULT, taxa);
                sendBroadcast(reply);

             } else if (action.equals(ACTION_GET_ALL_GUIDES)) {
                SerializableJSONArray guides = getAllGuides();
                
                Intent reply = new Intent(ACTION_ALL_GUIDES_RESULT);
                reply.putExtra(GUIDES_RESULT, guides);
                sendBroadcast(reply);

             } else if (action.equals(ACTION_GET_MY_GUIDES)) {
                SerializableJSONArray guides = getMyGuides();
                
                Intent reply = new Intent(ACTION_MY_GUIDES_RESULT);
                reply.putExtra(GUIDES_RESULT, guides);
                sendBroadcast(reply);

             } else if (action.equals(ACTION_GET_NEAR_BY_GUIDES)) {
                 int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

                 // If Google Play services is available
                 if (ConnectionResult.SUCCESS == resultCode) {
                     // Use Google Location Services to determine location
                     mLocationClient = new LocationClient(getApplicationContext(), this, this);
                     mLocationClient.connect();
                     
                     // Only once we're connected - we'll call getNearByGuides()
                     mGetLocationForProjects = false;
                     
                 } else {
                     // Use GPS for the location
                     SerializableJSONArray guides = getNearByGuides(false);

                     Intent reply = new Intent(ACTION_NEAR_BY_GUIDES_RESULT);
                     reply.putExtra(GUIDES_RESULT, guides);
                     sendBroadcast(reply);
                 }
               
             } else if (action.equals(ACTION_GET_NEARBY_PROJECTS)) {
                 int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

                 // If Google Play services is available
                 if (ConnectionResult.SUCCESS == resultCode) {
                     // Use Google Location Services to determine location
                     mLocationClient = new LocationClient(getApplicationContext(), this, this);
                     mLocationClient.connect();
                     
                     // Only once we're connected - we'll call getNearByProjects()
                     mGetLocationForProjects = true;
                     
                 } else {
                     // Use GPS for the location
                     SerializableJSONArray projects = getNearByProjects(false);

                     Intent reply = new Intent(ACTION_NEARBY_PROJECTS_RESULT);
                     reply.putExtra(PROJECTS_RESULT, projects);
                     sendBroadcast(reply);
                 }
                 
              } else if (action.equals(ACTION_GET_FEATURED_PROJECTS)) {
                 SerializableJSONArray projects = getFeaturedProjects();

                 Intent reply = new Intent(ACTION_FEATURED_PROJECTS_RESULT);
                 reply.putExtra(PROJECTS_RESULT, projects);
                 sendBroadcast(reply);
                
             } else if (action.equals(ACTION_GET_JOINED_PROJECTS)) {
                 SerializableJSONArray projects = getJoinedProjectsOffline();

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
 
            } else if (action.equals(ACTION_GET_OBSERVATION)) {
                int id = intent.getExtras().getInt(OBSERVATION_ID);
                Observation observation = getObservation(id);
                
                Intent reply = new Intent(ACTION_OBSERVATION_RESULT);
                reply.putExtra(OBSERVATION_RESULT, observation);
                sendBroadcast(reply);
                
            } else if (action.equals(ACTION_JOIN_PROJECT)) {
                int id = intent.getExtras().getInt(PROJECT_ID);
                joinProject(id);
                
            } else if (action.equals(ACTION_LEAVE_PROJECT)) {
                int id = intent.getExtras().getInt(PROJECT_ID);
                leaveProject(id);
                
            } else if (action.equals(ACTION_PULL_OBSERVATIONS)) {
            	// Download observations without uploading any new ones
                mIsSyncing = true;
                mApp.setIsSyncing(mIsSyncing);
                getUserObservations(0);

                 // Update last sync time
                long lastSync = System.currentTimeMillis();
                mPreferences.edit().putLong("last_sync_time", lastSync).commit();
                 
            } else {
                mIsSyncing = true;
                mApp.setIsSyncing(mIsSyncing);
                syncObservations();
               
                // Update last sync time
                long lastSync = System.currentTimeMillis();
                mPreferences.edit().putLong("last_sync_time", lastSync).commit();
 
            }
        } catch (AuthenticationException e) {
            if (!mPassive) {
                requestCredentials();
            }
        } finally {
            if (mIsSyncing) {
                mIsSyncing = false;
                mApp.setIsSyncing(mIsSyncing);
                
                Log.i(TAG, "Sending ACTION_SYNC_COMPLETE");
                
                // Notify the rest of the app of the completion of the sync
                Intent reply = new Intent(ACTION_SYNC_COMPLETE);
                sendBroadcast(reply); 
            }
        }
    }
    
    private void syncObservations() throws AuthenticationException {
        deleteObservations(); // Delete locally-removed observations
        saveJoinedProjects();
        getUserObservations(0); // First, download remote observations (new/updated)
        postObservations(); // Next, update local-to-remote observations
        syncObservationFields();
        postPhotos();
        postProjectObservations();
        
        
//        Toast.makeText(getApplicationContext(), "Observations synced", Toast.LENGTH_SHORT);
    }
    
    private BetterJSONObject getTaxon(int id) throws AuthenticationException {
        String url = String.format("%s/taxa/%d.json", HOST, id);

        JSONArray json = get(url);
        if (json == null || json.length() == 0) { return null; }
        
        JSONObject res;
        
        try {
            res = (JSONObject) json.get(0);
        } catch (JSONException e) {
            return null;
        }
        
        return new BetterJSONObject(res);
    }
 
    private void postProjectObservations() throws AuthenticationException {
        // First, delete any project-observations that were deleted by the user
        Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI, 
                ProjectObservation.PROJECTION, 
                "is_deleted = 1",
                null, 
                ProjectObservation.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ProjectObservation projectObservation = new ProjectObservation(c);
            removeObservationFromProject(projectObservation.observation_id, projectObservation.project_id);
            c.moveToNext();
        }

        // Now it's safe to delete all of the project-observations locally
        getContentResolver().delete(ProjectObservation.CONTENT_URI, "is_deleted = 1", null);
        
        
        // Next, add new project observations
        c = getContentResolver().query(ProjectObservation.CONTENT_URI, 
                ProjectObservation.PROJECTION, 
                "is_new = 1",
                null, 
                ProjectObservation.DEFAULT_SORT_ORDER);

        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ProjectObservation projectObservation = new ProjectObservation(c);
            BetterJSONObject result = addObservationToProject(projectObservation.observation_id, projectObservation.project_id);
            
            if (mResponseErrors != null) {
                SerializableJSONArray errors = new SerializableJSONArray(mResponseErrors);
            
                // Couldn't add the observation to the project (probably didn't pass validation)
                String error;
                try {
                    error = errors.getJSONArray().getString(0);
                } catch (JSONException e) {
                    e.printStackTrace();
                    c.moveToNext();
                    continue;
                }
                
                Cursor c2 = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = '"+projectObservation.observation_id+"'", null, Observation.DEFAULT_SORT_ORDER);
                c2.moveToFirst();
                if (c2.getCount() == 0) {
                    break;
                }
                Observation observation = new Observation(c2);
                c2.close();
                
                c2 = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '"+projectObservation.project_id+"'", null, Project.DEFAULT_SORT_ORDER);
                c2.moveToFirst();
                if (c2.getCount() == 0) {
                    break;
                }
                Project project = new Project(c2);
                c2.close();
                
                final String errorMessage = String.format(getString(R.string.failed_to_add_obs_to_project), observation.species_guess, project.title, error);

                // Notify user
                mApp.sweepingNotify(SYNC_OBSERVATIONS_NOTIFICATION, 
                        getString(R.string.syncing_observations), 
                        errorMessage,
                        getString(R.string.syncing));
                
                // Display toast in this main thread handler (since otherwise it won't get displayed)
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Toast doesn't support longer periods of display - this is a workaround
                        for (int i = 0; i < 3; i++) {
                            Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });

            } else {
                // Unmark as new
                projectObservation.is_new = false;
                ContentValues cv = projectObservation.getContentValues();
                getContentResolver().update(projectObservation.getUri(), cv, null, null);
            }
            
            c.moveToNext();
        }
        
        // Finally, retrieve all project observations
        for (int j = 0; j < mProjectObservations.size(); j++) {
            JSONArray projectObservations = mProjectObservations.get(j).getJSONArray();
            
            for (int i = 0; i < projectObservations.length(); i++) {
                JSONObject jsonProjectObservation;
                try {
                    jsonProjectObservation = projectObservations.getJSONObject(i);
                    ProjectObservation projectObservation = new ProjectObservation(new BetterJSONObject(jsonProjectObservation));
                    ContentValues cv = projectObservation.getContentValues();
                    getContentResolver().insert(ProjectObservation.CONTENT_URI, cv);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

    }
    
    private void saveJoinedProjects() throws AuthenticationException {
        SerializableJSONArray projects = getJoinedProjects();
        
        if (projects != null) {
            JSONArray arr = projects.getJSONArray();
            
            try {
            // First, delete all joined projects
            getContentResolver().delete(Project.CONTENT_URI, null, null);
            } catch (Exception exc) {
                exc.printStackTrace();
                return;
            }
            
            // Next, add the new joined projects
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject jsonProject = arr.getJSONObject(i);
                    Project project = new Project(new BetterJSONObject(jsonProject));
                    ContentValues cv = project.getContentValues();
                    getContentResolver().insert(Project.CONTENT_URI, cv);
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void deleteObservations() throws AuthenticationException {
        // Remotely delete any locally-removed observations
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "is_deleted = 1 AND user_login = '"+mLogin+"'", 
                null, 
                Observation.DEFAULT_SORT_ORDER);
        
       // for each observation DELETE to /observations/:id
        ArrayList<Integer> obsIds = new ArrayList<Integer>();
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            Observation observation = new Observation(c);
            delete(HOST + "/observations/" + observation.id + ".json", null);
            obsIds.add(observation.id);
            c.moveToNext();
        }
        
        // Now it's safe to delete all of the observations locally
        getContentResolver().delete(Observation.CONTENT_URI, "is_deleted = 1", null);
        // Delete associated project-fields and photos
        int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count2 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count3 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
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
    
    
     private void addIdentification(int observationId, int taxonId, String body) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("identification[observation_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("identification[taxon_id]", new Integer(taxonId).toString()));
        params.add(new BasicNameValuePair("identification[body]", body));
        
        JSONArray arrayResult = post(HOST + "/identifications.json", params);
        
        if (arrayResult != null) {
            BetterJSONObject result;
            try {
                result = new BetterJSONObject(arrayResult.getJSONObject(0));
                JSONObject jsonObservation = result.getJSONObject("observation");
                Observation remoteObservation = new Observation(new BetterJSONObject(jsonObservation));
                
                Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
                        Observation.PROJECTION, 
                        "id = "+ remoteObservation.id, null, Observation.DEFAULT_SORT_ORDER);

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
   
    
    private void addComment(int observationId, String body) throws AuthenticationException {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("comment[parent_id]", new Integer(observationId).toString()));
        params.add(new BasicNameValuePair("comment[parent_type]", "Observation"));
        params.add(new BasicNameValuePair("comment[body]", body));
        
        post(HOST + "/comments.json", params);
    }

    private void postObservations() throws AuthenticationException {
        Observation observation;
        // query observations where _updated_at > updated_at
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "_updated_at > _synced_at AND _synced_at IS NOT NULL AND user_login = '"+mLogin+"'", 
                null, 
                Observation.SYNC_ORDER);
        int updatedCount = c.getCount();
        mApp.sweepingNotify(SYNC_OBSERVATIONS_NOTIFICATION, 
                getString(R.string.syncing_observations), 
                String.format(getString(R.string.syncing_x_observations), c.getCount()),
                getString(R.string.syncing));
        // for each observation PUT to /observations/:id
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            mApp.notify(SYNC_OBSERVATIONS_NOTIFICATION, 
                    getString(R.string.updating_observations), 
                    String.format(getString(R.string.updating_x_observations), (c.getPosition() + 1), c.getCount()),
                    getString(R.string.syncing));
            observation = new Observation(c);
            handleObservationResponse(
                    observation,
                    put(HOST + "/observations/" + observation.id + ".json?extra=observation_photos", paramsForObservation(observation))
            );
            c.moveToNext();
        }
        c.close();

        // query observations where _synced_at IS NULL
        c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "id IS NULL", null, Observation.SYNC_ORDER);
        int createdCount = c.getCount();
        // for each observation POST to /observations/
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            mApp.notify(SYNC_OBSERVATIONS_NOTIFICATION, 
                    getString(R.string.posting_observations), 
                    String.format(getString(R.string.posting_x_observations), (c.getPosition() + 1), c.getCount()),
                    getString(R.string.syncing));
            observation = new Observation(c);
            handleObservationResponse(
                    observation,
                    post(HOST + "/observations.json?extra=observation_photos&project_id=" + DEFAULT_PROJECT_ID, paramsForObservation(observation))
            );
            c.moveToNext();
        }
        c.close();
        
        mApp.notify(SYNC_OBSERVATIONS_NOTIFICATION, 
                getString(R.string.observation_sync_complete), 
                String.format(getString(R.string.observation_sync_status), createdCount, updatedCount),
                getString(R.string.sync_complete));
    }
    
    
    private Observation getObservation(int id) throws AuthenticationException {
        String url = String.format("%s/observations/%d.json", HOST, id);

        JSONArray json = get(url);
        if (json == null || json.length() == 0) { return null; }
        
        JSONObject observation;
        
        try {
            observation = (JSONObject) json.get(0);
        } catch (JSONException e) {
            return null;
        }
        
        return new Observation(new BetterJSONObject(observation));
    }
    
    private void postPhotos() throws AuthenticationException {
        ObservationPhoto op;
        int createdCount = 0;
        // query observations where _updated_at > updated_at
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
                ObservationPhoto.PROJECTION, 
                "_synced_at IS NULL", null, ObservationPhoto.DEFAULT_SORT_ORDER);
        if (c.getCount() == 0) {
            return;
        }
            
        // for each observation PUT to /observations/:id
        ContentValues cv;
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            mApp.notify(SYNC_PHOTOS_NOTIFICATION, 
                    getString(R.string.posting_photos), 
                    String.format(getString(R.string.posting_x_photos), (c.getPosition() + 1), c.getCount()),
                    getString(R.string.syncing));
            op = new ObservationPhoto(c);
            ArrayList <NameValuePair> params = op.getParams();
            // http://stackoverflow.com/questions/2935946/sending-images-using-http-post
            Uri photoUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, op._photo_id); 
            Cursor pc = getContentResolver().query(photoUri, 
                    new String[] {MediaStore.MediaColumns._ID, MediaStore.Images.Media.DATA}, 
                    null, 
                    null, 
                    MediaStore.Images.Media.DEFAULT_SORT_ORDER);
            
            if (pc.getCount() == 0) {
                // photo has been deleted, destroy the ObservationPhoto
                getContentResolver().delete(op.getUri(), null, null);
                continue;
            } else {
                pc.moveToFirst();
            }
            
            String imgFilePath = pc.getString(pc.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
            params.add(new BasicNameValuePair("file", imgFilePath));
            
            // TODO LATER resize the image for upload, maybe a 1024px jpg
            JSONArray response = post(MEDIA_HOST + "/observation_photos.json", params);
            try {
                if (response == null || response.length() != 1) {
                    break;
                }
                JSONObject json = response.getJSONObject(0);
                BetterJSONObject j = new BetterJSONObject(json);
                ObservationPhoto jsonObservationPhoto = new ObservationPhoto(j);
                op.merge(jsonObservationPhoto);
                cv = op.getContentValues();
                cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                getContentResolver().update(op.getUri(), cv, null, null);
                createdCount += 1;
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.toString());
            }
            c.moveToNext();
        }
        c.close();
        mApp.notify(SYNC_PHOTOS_NOTIFICATION, 
                getString(R.string.photo_sync_complete), 
                String.format(getString(R.string.posted_new_x_photos), createdCount),
                getString(R.string.sync_complete));
    }

    private SerializableJSONArray getTaxaForGuide(Integer guideId) throws AuthenticationException {
        String url = HOST + "/guide_taxa.json?guide_id=" + guideId.toString();
        JSONArray json = get(url);
        try {
			return new SerializableJSONArray(json.getJSONObject(0).getJSONArray("guide_taxa"));
		} catch (JSONException e) {
			e.printStackTrace();
			return new SerializableJSONArray();
		}
    }
    
    
    private SerializableJSONArray getAllGuides() throws AuthenticationException {
        String url = HOST + "/guides.json";
        
        JSONArray json = get(url);
        
        return new SerializableJSONArray(json);
    }
    
    private SerializableJSONArray getMyGuides() throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }
        String url = HOST + "/guides.json?by=you";
        
        JSONArray json = get(url, true);
        
        return new SerializableJSONArray(json);
    }

    private SerializableJSONArray getNearByGuides(boolean useLocationServices) throws AuthenticationException {
        if (useLocationServices) {
            Location location = mLocationClient.getLastLocation();
            return getNearByGuides(location);
        } else {
            // Use GPS alone to determine location
            LocationManager locationManager = (LocationManager)mApp.getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            String provider = locationManager.getBestProvider(criteria, false);
            Location location = locationManager.getLastKnownLocation(provider);
            return getNearByGuides(location);
        }
    }

    private SerializableJSONArray getNearByGuides(Location location) throws AuthenticationException {
        if (location == null) {
            // No location found - return an empty result
            Log.e(TAG, "Current location is null");
            return new SerializableJSONArray();
        }

        double lat  = location.getLatitude();
        double lon  = location.getLongitude();

        String url = HOST + String.format("/guides.json?latitude=%s&longitude=%s", lat, lon);

        Log.e(TAG, url);

        JSONArray json = get(url);
        
        return new SerializableJSONArray(json);
    }
    
    
    private SerializableJSONArray getNearByProjects(Location location) throws AuthenticationException {
        if (location == null) {
            // No location found - return an empty result
            Log.e(TAG, "Current location is null");
            return new SerializableJSONArray();
        }

        double lat  = location.getLatitude();
        double lon  = location.getLongitude();

        String url = HOST + String.format("/projects.json?latitude=%s&longitude=%s", lat, lon);

        Log.e(TAG, url);

        JSONArray json = get(url);
        
        if (json == null) {
        	return new SerializableJSONArray();
        }
        
        // Determine which projects are already joined
        for (int i = 0; i < json.length(); i++) {
            Cursor c;
            try {
                c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '"+json.getJSONObject(i).getInt("id")+"'", null, Project.DEFAULT_SORT_ORDER);
                c.moveToFirst();
                if (c.getCount() > 0) {
                    json.getJSONObject(i).put("joined", true);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }

        }

        return new SerializableJSONArray(json);
    }
    
    private SerializableJSONArray getNearByProjects(boolean useLocationServices) throws AuthenticationException {
           
        if (useLocationServices) {
            Location location = mLocationClient.getLastLocation();
            
            return getNearByProjects(location);
        } else {
            // Use GPS alone to determine location
            LocationManager locationManager = (LocationManager)mApp.getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            String provider = locationManager.getBestProvider(criteria, false);
            Location location = locationManager.getLastKnownLocation(provider);
            
            return getNearByProjects(location);
        }
    }

    private SerializableJSONArray getFeaturedProjects() throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }
        String url = HOST + "/projects.json?featured=true";
        
        JSONArray json = get(url);
        
        if (json == null) {
        	return new SerializableJSONArray();
        }
 
        
        // Determine which projects are already joined
        for (int i = 0; i < json.length(); i++) {
            Cursor c;
            try {
                c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, "id = '"+json.getJSONObject(i).getInt("id")+"'", null, Project.DEFAULT_SORT_ORDER);
                c.moveToFirst();
                if (c.getCount() > 0) {
                    json.getJSONObject(i).put("joined", true);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }

        }

        return new SerializableJSONArray(json);
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
            getContentResolver().delete(ProjectField.CONTENT_URI, "(project_id IS NOT NULL) and (project_id = "+projectId+")", null);

            // Next, re-add all project fields
            for (int i = 0; i < projectFields.size(); i++) {
                ProjectField field = projectFields.get(i);
                getContentResolver().insert(ProjectField.CONTENT_URI, field.getContentValues());
            }
        }
    }
    
    public void joinProject(int projectId) throws AuthenticationException {
        post(String.format("%s/projects/%d/join.json", HOST, projectId), null);
        
        try {
            JSONArray result = get(String.format("%s/projects/%d.json", HOST, projectId));
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
        delete(String.format("%s/projects/%d/leave", HOST, projectId), null);
        
        // Remove locally saved project (because we left it)
        getContentResolver().delete(Project.CONTENT_URI, "(id IS NOT NULL) and (id = "+projectId+")", null);
    } 
    
    
    private BetterJSONObject removeObservationFromProject(int observationId, int projectId) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return null;
        }

        String url = String.format("%s/projects/%d/remove.json?observation_id=%d", HOST, projectId, observationId);
        JSONArray json = delete(url, null);
       
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
            return new BetterJSONObject();
        }
       
        try {
            return new BetterJSONObject(json.getJSONObject(0));
        } catch (JSONException e) {
            e.printStackTrace();
            return new BetterJSONObject();
        }
    }
 
    
    private SerializableJSONArray getCheckList(int id) throws AuthenticationException {
        String url = String.format("%s/lists/%d.json?per_page=100", HOST, id);
        
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
        String url = HOST + "/projects/user/" + mLogin + ".json";
        
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
    
    
    private void getUserObservations(int maxCount) throws AuthenticationException {
        if (ensureCredentials() == false) {
            return;
        }
        String url = HOST + "/observations/" + mLogin + ".json";
        
        long lastSync = mPreferences.getLong("last_sync_time", 0);
        Timestamp lastSyncTS = new Timestamp(lastSync);
        url += String.format("?updated_since=%s&order_by=date_added&order=desc&extra=observation_photos,projects,fields", URLEncoder.encode(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(lastSyncTS)));
        
        if (maxCount > 0) {
            // Retrieve only a certain number of observations
            url += String.format("&per_page=%d&page=1", maxCount);
        }
        
        mProjectObservations = new ArrayList<SerializableJSONArray>();
        mProjectFieldValues = new Hashtable<Integer, Hashtable<Integer,ProjectFieldValue>>();
        
        JSONArray json = get(url);
        if (json != null && json.length() > 0) {
            syncJson(json, true);
        } else {
        	if (mResponseHeaders != null) {
        		// Delete any local observations which were deleted remotely by the user
        		for (Header header : mResponseHeaders) {
        			if (!header.getName().equalsIgnoreCase("X-Deleted-Observations")) continue;

        			String deletedIds = header.getValue().trim();
        			getContentResolver().delete(Observation.CONTENT_URI, "(id IN ("+deletedIds+"))", null);
        			// Delete associated project-fields and photos
        			int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
        			int count2 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
        			int count3 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
        			break;
        		}

        		mResponseHeaders = null;
        	}
        }
    }
    
    private void syncObservationFields() throws AuthenticationException {
        
        // First, remotely update the observation fields which were modified
        
        Cursor c = getContentResolver().query(ProjectFieldValue.CONTENT_URI, 
                ProjectFieldValue.PROJECTION, 
                "_updated_at > _synced_at AND _synced_at IS NOT NULL", 
                null, 
                ProjectFieldValue.DEFAULT_SORT_ORDER);
        
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            ProjectFieldValue localField = new ProjectFieldValue(c);
            
            if (!mProjectFieldValues.containsKey(Integer.valueOf(localField.observation_id))) {
                // Need to retrieve remote observation fields to see how to sync the fields
                JSONArray jsonResult = get(HOST + "/observations/" + localField.observation_id + ".json");

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
            }
            
            Hashtable<Integer, ProjectFieldValue> fields = mProjectFieldValues.get(Integer.valueOf(localField.observation_id));
            
            boolean shouldOverwriteRemote = false;
            ProjectFieldValue remoteField = null;
            
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
                post(HOST + "/observation_field_values.json", params);
                
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
        }
        c.close();
        
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
                    addProjectField(field.field_id);
                }
                c.close();
 
            }
        }

    }
    
    private void addProjectField(int fieldId) throws AuthenticationException {
        try {
            JSONArray result = get(String.format("%s/observation_fields/%d.json", HOST, fieldId));
            BetterJSONObject jsonObj;
            jsonObj = new BetterJSONObject(result.getJSONObject(0));
            ProjectField field = new ProjectField(jsonObj);
            
            getContentResolver().insert(ProjectField.CONTENT_URI, field.getContentValues());
            
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    private void getNearbyObservations(Intent intent) throws AuthenticationException {
        Bundle extras = intent.getExtras();
        Double minx = extras.getDouble("minx");
        Double maxx = extras.getDouble("maxx");
        Double miny = extras.getDouble("miny");
        Double maxy = extras.getDouble("maxy");
        String url = HOST + "/observations/project/" + DEFAULT_PROJECT_ID + ".json?extra=observation_photos";
        url += "&swlat="+miny;
        url += "&nelat="+maxy;
        url += "&swlng="+minx;
        url += "&nelng="+maxx;
        JSONArray json = get(url, mApp.loggedIn());
        Intent reply = new Intent(ACTION_NEARBY);
        reply.putExtra("minx", minx);
        reply.putExtra("maxx", maxx);
        reply.putExtra("miny", miny);
        reply.putExtra("maxy", maxy);
        if (json == null) {
            reply.putExtra("error", getString(R.string.couldnt_load_nearby_observations));
        } else {
            syncJson(json, false);
        }
        sendBroadcast(reply);
    }

    private JSONArray put(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        params.add(new BasicNameValuePair("_method", "PUT"));
        return request(url, "put", params, true);
    }
    
    private JSONArray delete(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        return request(url, "delete", params, true);
    }

    private JSONArray post(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        return request(url, "post", params, true);
    }

    private JSONArray get(String url) throws AuthenticationException {
        return get(url, false);
    }

    private JSONArray get(String url, boolean authenticated) throws AuthenticationException {
        return request(url, "get", null, authenticated);
    }

    private JSONArray request(String url, String method, ArrayList<NameValuePair> params, boolean authenticated) throws AuthenticationException {
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);
        
//        Log.d(TAG, String.format("%s (%b - %s): %s", method, authenticated,
//                authenticated ? mCredentials : "<null>",
//                url));
        
        HttpRequestBase request;
        
        Log.d(TAG, String.format("URL: %s - %s (%s)", method, url, (params != null ? params.toString() : "null")));
        
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
        if (params != null) {
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).getName().equalsIgnoreCase("image") || params.get(i).getName().equalsIgnoreCase("file")) {
                    // If the key equals to "image", we use FileBody to transfer the data
                    entity.addPart(params.get(i).getName(), new FileBody(new File (params.get(i).getValue())));
                } else {
                    // Normal string data
                    try {
                        entity.addPart(params.get(i).getName(), new StringBody(params.get(i).getValue()));
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

        // auth
        if (authenticated) {
            ensureCredentials();
            
            if (mLoginType == LoginType.PASSWORD) {
                request.setHeader("Authorization", "Basic " + mCredentials);
            } else {
                request.setHeader("Authorization", "Bearer " + mCredentials);
            }
        }

        try {
            /*
            com.google.api.client.http.HttpResponse response = request.execute();
            String content = response.parseAsString();
            */
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            
            Log.d(TAG, String.format("RESP: %s", content));
            
            JSONArray json = null;
            switch (response.getStatusLine().getStatusCode()) {
            //switch (response.getStatusCode()) {
            case HttpStatus.SC_UNPROCESSABLE_ENTITY:
                // Validation error - still need to return response
                Log.e(TAG, response.getStatusLine().toString());
            case HttpStatus.SC_OK:
                try {
                    json = new JSONArray(content);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create JSONArray, JSONException: " + e.toString());
                    try {
                        JSONObject jo = new JSONObject(content);
                        json = new JSONArray();
                        json.put(jo);
                    } catch (JSONException e2) {
                        Log.e(TAG, "Failed to create JSONObject, JSONException: " + e2.toString());
                    }
                }
                
                mResponseHeaders = response.getAllHeaders();
                
                try {
                	if (json != null) {
                		JSONObject result = json.getJSONObject(0);
                		if ((result != null) && (result.has("errors"))) {
                			// Error response
                			Log.e(TAG, "Got an error response: " + result.get("errors").toString());
                			mResponseErrors = result.getJSONArray("errors");
                			return null;
                		}
                	}
				} catch (JSONException e) {
					e.printStackTrace();
				}
                
                mResponseErrors = null;
                
                
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
                //Log.e(TAG, response.getStatusMessage());
            }
        }
        catch (IOException e) {
            //request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        }
        return null;
    }

    private boolean ensureCredentials() throws AuthenticationException {
        if (mCredentials != null) { return true; }

        // request login unless passive
        if (!mPassive) {
            throw new AuthenticationException();
        }
        stopSelf();
        return false;
    }

    private void requestCredentials() {
        stopSelf();
        Intent intent = new Intent(
                mLogin == null ? "signin" : INaturalistPrefsActivity.REAUTHENTICATE_ACTION, 
                null, getBaseContext(), INaturalistPrefsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mApp.sweepingNotify(AUTH_NOTIFICATION, getString(R.string.please_sign_in), getString(R.string.please_sign_in_description), null, intent);
    }
    
    public static String verifyCredentials(String credentials) {
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);
        String url = HOST + "/observations/new.json";
        HttpRequestBase request = new HttpGet(url);
        request.setHeader("Authorization", "Basic "+credentials);
        request.setHeader("Content-Type", "application/json");

        try {
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            	// Next, find the iNat username (since we currently only have email address)
            	request = new HttpGet(HOST + "/users/edit.json");
                request.setHeader("Authorization", "Basic "+credentials);

            	response = client.execute(request);
            	entity = response.getEntity();
            	content = EntityUtils.toString(entity);

            	if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            		return null;
            	}

            	JSONObject json = new JSONObject(content);
            	if (!json.has("login")) {
            		return null;
            	}

            	String username = json.getString("login");

                return username;
            } else {
                Log.e(TAG, "Authentication failed: " + content);
                return null;
            }
        }
        catch (IOException e) {
            request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        } catch (JSONException e) {
			e.printStackTrace();
            Log.w(TAG, "Error for URL " + url, e);
		}
        return null;
    }

    public static String verifyCredentials(String username, String password) {
        String credentials = Base64.encodeToString(
                (username + ":" + password).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
                );
        return verifyCredentials(credentials);
    }
    
    // Returns an array of two strings: access token + iNat username
    public static String[] verifyCredentials(String oauth2Token, LoginType authType) {
        String grantType;
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);
        String url = HOST + "/oauth/assertion_token";
        HttpRequestBase request = new HttpPost(url);
        ArrayList<NameValuePair> postParams = new ArrayList<NameValuePair>();
        
        postParams.add(new BasicNameValuePair("format", "json"));
        postParams.add(new BasicNameValuePair("client_id", INaturalistApp.getAppContext().getString(R.string.oauth_client_id)));
        
        Log.d(TAG, "Client ID: " + INaturalistApp.getAppContext().getString(R.string.oauth_client_id));
        Log.d(TAG, "FB App Id: " + INaturalistApp.getAppContext().getString(R.string.facebook_app_id));

        if (authType == LoginType.FACEBOOK) {
            grantType = "facebook";
        } else {
            grantType = "google";
        }
        
        postParams.add(new BasicNameValuePair("grant_type", grantType));
        postParams.add(new BasicNameValuePair("assertion", oauth2Token));
        
        try {
            ((HttpPost)request).setEntity(new UrlEncodedFormEntity(postParams));
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
                
                String username = json.getString("login");
               
                return new String[] { accessToken, username };
                
            } else {
                Log.e(TAG, "Authentication failed: " + content);
                return null;
            }
        }
        catch (IOException e) {
            request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return null;

    }

    
    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(Observation observation){
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String name = "observation_" + observation.created_at.getTime() + "_" + timeStamp;
        values.put(android.provider.MediaStore.Images.Media.TITLE, name);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }


    private Uri createObservationPhotoForPhoto(Uri photoUri, Observation observation) {
        ObservationPhoto op = new ObservationPhoto();
        Long photoId = ContentUris.parseId(photoUri);
        ContentValues cv = op.getContentValues();
        cv.put(ObservationPhoto._OBSERVATION_ID, observation._id);
        cv.put(ObservationPhoto.OBSERVATION_ID, observation.id);
        cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis()); // So we won't re-add this photo as though it was a local photo
        if (photoId > -1) {
            cv.put(ObservationPhoto._PHOTO_ID, photoId.intValue());
        }
        return getContentResolver().insert(ObservationPhoto.CONTENT_URI, cv);
    }

    
    public void syncJson(JSONArray json, boolean isUser) {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ArrayList<Integer> existingIds = new ArrayList<Integer>();
        ArrayList<Integer> newIds = new ArrayList<Integer>();
        HashMap<Integer,Observation> jsonObservationsById = new HashMap<Integer,Observation>();
        Observation observation;
        Observation jsonObservation;
        
        BetterJSONObject o;
        for (int i = 0; i < json.length(); i++) {
            try {
                o = new BetterJSONObject(json.getJSONObject(i));
                ids.add(o.getInt("id"));
                jsonObservationsById.put(o.getInt("id"), new Observation(o));
                
                if (isUser) {
                    // Save the project observations aside (will be later used in the syncing of project observations)
                    mProjectObservations.add(o.getJSONArray("project_observations"));
                    
                    // Save project field values
                    Hashtable<Integer, ProjectFieldValue> fields = new Hashtable<Integer, ProjectFieldValue>();
                    JSONArray jsonFields = o.getJSONArray("observation_field_values").getJSONArray();
                    
                    for (int j = 0; j < jsonFields.length(); j++) {
                        BetterJSONObject field = new BetterJSONObject(jsonFields.getJSONObject(j));
                        fields.put(field.getJSONObject("observation_field").getInt("id"), new ProjectFieldValue(field));
                    }
                    
                    mProjectFieldValues.put(o.getInt("id"), fields);
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.toString());
            }
        }
        // find obs with existing ids
        String joinedIds = StringUtils.join(ids, ",");
        // TODO why doesn't selectionArgs work for id IN (?)
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "id IN ("+joinedIds+")", null, Observation.DEFAULT_SORT_ORDER);

        // update existing
        c.moveToFirst();
        ContentValues cv;
        while (c.isAfterLast() == false) {
            observation = new Observation(c);
            jsonObservation = jsonObservationsById.get(observation.id);
            boolean isModified = observation.merge(jsonObservation); 
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
                    "observation_id = "+observation.id, 
                    null, null);
            pc.moveToFirst();
            while(pc.isAfterLast() == false) {
                int photoId = pc.getInt(pc.getColumnIndexOrThrow(ObservationPhoto.ID));
                if (photoId != 0) {
                    existingObservationPhotoIds.add(photoId);
                }
                pc.moveToNext();
            }
            pc.close();
            for (int j = 0; j < jsonObservation.photos.size(); j++) {
                ObservationPhoto photo = jsonObservation.photos.get(j);
                photo._observation_id = jsonObservation._id;
                observationPhotoIds.add(photo.id);
                if (existingObservationPhotoIds.contains(photo.id)) {
                    Log.d(TAG, "photo " + photo.id + " has already been added, skipping...");
                    continue;
                }
                ContentValues opcv = photo.getContentValues();
                // So we won't re-add this photo as though it was a local photo
                opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                opcv.put(ObservationPhoto._OBSERVATION_ID, photo.observation_id);
                opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
                opcv.put(ObservationPhoto.ID, photo.id);
                try {
                    getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
                } catch(SQLException ex) {
                    // Happens when the photo already exists - ignore
                }
            }
            
            // Delete photos that were synced but weren't present in the remote response, 
            // indicating they were deleted elsewhere
            String joinedPhotoIds = StringUtils.join(observationPhotoIds, ",");
            String where = "observation_id = " + observation.id + " AND id IS NOT NULL";
            if (joinedPhotoIds.length() > 0) {
                where += " AND id NOT in (" + joinedPhotoIds + ")";
            }
            getContentResolver().delete(
                    ObservationPhoto.CONTENT_URI, 
                    where, 
                    null);

            if (isModified) {
                // Only update the DB if needed
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
        for (int i = 0; i < newIds.size(); i++) {			
            jsonObservation = jsonObservationsById.get(newIds.get(i));
            cv = jsonObservation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            cv.put(Observation.LAST_COMMENTS_COUNT, jsonObservation.comments_count);
            cv.put(Observation.LAST_IDENTIFICATIONS_COUNT, jsonObservation.identifications_count);
            Uri newObs = getContentResolver().insert(Observation.CONTENT_URI, cv);
            Long newObsId = ContentUris.parseId(newObs);
            jsonObservation._id = Integer.valueOf(newObsId.toString());
            newObservations.add(jsonObservation);
        }
        
        if (isUser) {
            for (int i = 0; i < newObservations.size(); i++) {
                jsonObservation = newObservations.get(i);
                
                // Save the new observation's photos
                for (int j = 0; j < jsonObservation.photos.size(); j++) {
                    ObservationPhoto photo = jsonObservation.photos.get(j);
                    photo._observation_id = jsonObservation._id;

                    ContentValues opcv = photo.getContentValues();
                    opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis()); // So we won't re-add this photo as though it was a local photo
                    opcv.put(ObservationPhoto._OBSERVATION_ID, photo._observation_id);
                    opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
                    opcv.put(ObservationPhoto._ID, photo.id);
                    getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
                }
            }
        }


        if (isUser) {
            if (mResponseHeaders != null) {
                // Delete any local observations which were deleted remotely by the user
                for (Header header : mResponseHeaders) {
                    if (!header.getName().equalsIgnoreCase("X-Deleted-Observations")) continue;
                    
                    String deletedIds = header.getValue().trim();
                    getContentResolver().delete(Observation.CONTENT_URI, "(id IN ("+deletedIds+"))", null);
        			// Delete associated project-fields and photos
        			int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
        			int count2 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
        			int count3 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + deletedIds + ")", null);
 
                    break;
                }
                
                mResponseHeaders = null;
            }
        }
    }
    
    private ArrayList<NameValuePair> paramsForObservation(Observation observation) {
        ArrayList<NameValuePair> params = observation.getParams();
        params.add(new BasicNameValuePair("ignore_photos", "true"));
        return params;
    }
    
    private void handleObservationResponse(Observation observation, JSONArray response) {
        try {
            if (response == null || response.length() != 1) {
                return;
            }
            JSONObject json = response.getJSONObject(0);
            BetterJSONObject o = new BetterJSONObject(json);
            Observation jsonObservation = new Observation(o);
            observation.merge(jsonObservation);
            ContentValues cv = observation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(observation.getUri(), cv, null, null);
        } catch (JSONException e) {
            // Log.d(TAG, "JSONException: " + e.toString());
        }
    }

    private class AuthenticationException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        Log.e(TAG, "onConnectionFailed: " + (arg0 != null ? arg0.toString() : "null"));
        
        // Try using the GPS for the location
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                SerializableJSONArray projects;
                try {
                	if (mGetLocationForProjects) {
                		projects = getNearByProjects(false);
                	} else {
                		projects = getNearByGuides(false);
                	}
                } catch (AuthenticationException e) {
                    projects = new SerializableJSONArray();
                    e.printStackTrace();
                }

                Intent reply = new Intent(mGetLocationForProjects ? ACTION_NEARBY_PROJECTS_RESULT: ACTION_NEAR_BY_GUIDES_RESULT);
                reply.putExtra(mGetLocationForProjects ? PROJECTS_RESULT : GUIDES_RESULT, projects);
                sendBroadcast(reply);
            }
        });
        thread.start();
    }

    @Override
    public void onConnected(Bundle arg0) {
        Log.i(TAG, "onConnected: " + (arg0 != null ? arg0.toString() : "null"));
        
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                SerializableJSONArray projects;
                try {
                	if (mGetLocationForProjects) {
                		projects = getNearByProjects(true);
                	} else {
                		projects = getNearByGuides(true);
                	}
                } catch (AuthenticationException e) {
                    projects = new SerializableJSONArray();
                    e.printStackTrace();
                }

                Intent reply = new Intent(mGetLocationForProjects ? ACTION_NEARBY_PROJECTS_RESULT : ACTION_NEAR_BY_GUIDES_RESULT);
                reply.putExtra(mGetLocationForProjects ? PROJECTS_RESULT : GUIDES_RESULT, projects);
                sendBroadcast(reply);
            }
        });
        thread.start();
    }

    @Override
    public void onDisconnected() {
        Log.e(TAG, "onDisconnected");
    }
}
