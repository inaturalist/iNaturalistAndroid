package org.inaturalist.android;
import java.io.Serializable;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

public class ProjectObservation implements BaseColumns, Serializable {
    public static final String TAG = "ProjectObservation";
    
    public Integer _id;
    public Integer project_id;
    public Integer observation_id;
    public Boolean is_new;
    public Boolean is_deleted;
    
    public static final String TABLE_NAME = "project_observations";
    
    public static final int PROJECT_OBSERVATIONS_URI_CODE = 2979;
    public static final int PROJECT_OBSERVATION_ID_URI_CODE = 2964;
    
    public static HashMap<String, String> PROJECTION_MAP;
    public static final String AUTHORITY = "org.inaturalist.android.project_observation";
    public static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/project_observations");
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.project_observation";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.project_observation";
    public static final String DEFAULT_SORT_ORDER = "_id DESC";
    
    public static final String PROJECT_ID = "project_id";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String IS_NEW = "is_new";
    public static final String IS_DELETED = "is_deleted";


    public static final String[] PROJECTION = new String[] {
        ProjectObservation._ID,
        ProjectObservation.PROJECT_ID,
        ProjectObservation.OBSERVATION_ID,
        ProjectObservation.IS_DELETED,
        ProjectObservation.IS_NEW
    };

    static {
        PROJECTION_MAP = new HashMap<String, String>();
        PROJECTION_MAP.put(ProjectObservation._ID, ProjectObservation._ID);
        PROJECTION_MAP.put(ProjectObservation.PROJECT_ID, ProjectObservation.PROJECT_ID);
        PROJECTION_MAP.put(ProjectObservation.OBSERVATION_ID, ProjectObservation.OBSERVATION_ID);
        PROJECTION_MAP.put(ProjectObservation.IS_DELETED, ProjectObservation.IS_DELETED);
        PROJECTION_MAP.put(ProjectObservation.IS_NEW, ProjectObservation.IS_NEW);
    }

    public ProjectObservation() {}

    public ProjectObservation(Cursor c) {
        if (c.getPosition() == -1) c.moveToFirst();
        BetterCursor bc = new BetterCursor(c);
        
        this._id = bc.getInt(_ID);
        this.project_id = bc.getInt(PROJECT_ID);
        this.observation_id = bc.getInt(OBSERVATION_ID);
        this.is_deleted = bc.getBoolean(IS_DELETED);
        this.is_new = bc.getBoolean(IS_NEW);
    }

    public ProjectObservation(BetterJSONObject o) {
        this.project_id = o.getInt("project_id");
        this.observation_id = o.getInt("observation_id");
        this.is_deleted = false;
        this.is_new = false;
    }
    
    @Override
    public String toString() {
        return "ProjectObservation(project id: " + project_id + ", observation_id: " + observation_id + ", _id: " + _id + ")";
    }

    public Uri getUri() {
        if (_id == null) {
            return null;
        } else {
            return ContentUris.withAppendedId(CONTENT_URI, _id);
        }
    }

    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues();
        
        cv.put(PROJECT_ID, project_id);
        cv.put(OBSERVATION_ID, observation_id);
        cv.put(IS_DELETED, is_deleted);
        cv.put(IS_NEW, is_new);

        return cv;
    }

    public static String sqlCreate() {
        return "CREATE TABLE " + TABLE_NAME + " ("
                + ProjectObservation._ID + " INTEGER PRIMARY KEY,"
                + "project_id INTEGER,"
                + "observation_id INTEGER,"
                + "is_deleted INTEGER,"
                + "is_new INTEGER, "
                + "UNIQUE(project_id, observation_id) ON CONFLICT REPLACE"
                + ");";
    }

}
