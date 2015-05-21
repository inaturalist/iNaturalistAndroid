package org.inaturalist.android;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

public class ProjectFieldValue implements BaseColumns, Serializable {
    public Integer _id;
    public Integer id;
    
    public Timestamp _created_at;
    public Timestamp _synced_at;
    public Timestamp _updated_at;
    public Timestamp created_at;
    public Timestamp updated_at;
    
    public Integer observation_id;
    public Integer field_id;
    public String value;

    public static final String TAG = "ProjectFieldValue";
    public static final String TABLE_NAME = "project_field_values";
    public static final int PROJECT_FIELD_VALUES_URI_CODE = 4801;
    public static final int PROJECT_FIELD_VALUE_ID_URI_CODE = 4686;
    public static HashMap<String, String> PROJECTION_MAP;
    public static final String AUTHORITY = "org.inaturalist.android.project_field_value";
    public static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/project_field_values");
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.project_field_value";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.project_field_value";
    public static final String DEFAULT_SORT_ORDER = "_id DESC";
    
    public static final String _CREATED_AT = "_created_at";
    public static final String _SYNCED_AT = "_synced_at";
    public static final String _UPDATED_AT = "_updated_at";
    public static final String CREATED_AT = "created_at";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String FIELD_ID = "field_id";
    public static final String VALUE = "value";
    public static final String ID = "id";
    public static final String UPDATED_AT = "updated_at";


    public static final String[] PROJECTION = new String[] {
        ProjectFieldValue._ID,
        ProjectFieldValue._CREATED_AT,
        ProjectFieldValue.OBSERVATION_ID,
        ProjectFieldValue.FIELD_ID,
        ProjectFieldValue.VALUE,
        ProjectFieldValue._SYNCED_AT,
        ProjectFieldValue._UPDATED_AT,
        ProjectFieldValue.CREATED_AT,
        ProjectFieldValue.ID,
        ProjectFieldValue.UPDATED_AT,
    };

    static {
        PROJECTION_MAP = new HashMap<String, String>();
        PROJECTION_MAP.put(ProjectFieldValue._ID, ProjectFieldValue._ID);
        PROJECTION_MAP.put(ProjectFieldValue._CREATED_AT, ProjectFieldValue._CREATED_AT);
        PROJECTION_MAP.put(ProjectFieldValue._SYNCED_AT, ProjectFieldValue._SYNCED_AT);
        PROJECTION_MAP.put(ProjectFieldValue._UPDATED_AT, ProjectFieldValue._UPDATED_AT);
        PROJECTION_MAP.put(ProjectFieldValue.CREATED_AT, ProjectFieldValue.CREATED_AT);
        PROJECTION_MAP.put(ProjectFieldValue.ID, ProjectFieldValue.ID);
        PROJECTION_MAP.put(ProjectFieldValue.OBSERVATION_ID, ProjectFieldValue.OBSERVATION_ID);
        PROJECTION_MAP.put(ProjectFieldValue.FIELD_ID, ProjectFieldValue.FIELD_ID);
        PROJECTION_MAP.put(ProjectFieldValue.VALUE, ProjectFieldValue.VALUE);
        PROJECTION_MAP.put(ProjectFieldValue.UPDATED_AT, ProjectFieldValue.UPDATED_AT);

    }

    public ProjectFieldValue() {}

    public ProjectFieldValue(Cursor c) {
        if (c.getPosition() == -1) c.moveToFirst();
        BetterCursor bc = new BetterCursor(c);
        this._id = bc.getInt(_ID);
        this._created_at = bc.getTimestamp(_CREATED_AT);
        this._synced_at = bc.getTimestamp(_SYNCED_AT);
        this._updated_at = bc.getTimestamp(_UPDATED_AT);
        this.created_at = bc.getTimestamp(CREATED_AT);
        this.id = bc.getInteger(ID);
        this.observation_id = bc.getInteger(OBSERVATION_ID);
        this.updated_at = bc.getTimestamp(UPDATED_AT);
        this.field_id = bc.getInteger(FIELD_ID);
        this.value = bc.getString(VALUE);
    }

    public ProjectFieldValue(BetterJSONObject o) {
        this._created_at = o.getTimestamp("_created_at");
        this._synced_at = o.getTimestamp("_synced_at");
        this._updated_at = o.getTimestamp("_updated_at");
        this.created_at = o.getTimestamp("created_at");
        this.id = o.getInteger("id");
        this.observation_id = o.getInteger("observation_id");
        this.updated_at = o.getTimestamp("updated_at");
        this.field_id = new BetterJSONObject(o.getJSONObject("observation_field")).getInteger("id");
        if (field_id == null) 
            this.field_id = o.getInt("observation_field_id");
        this.value = o.getString("value");
    }

    @Override
    public String toString() {
        return "ProjectFieldValue(id: " + id + ", _id: " + _id + ")";
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
        if (created_at != null) { cv.put(CREATED_AT, created_at.getTime()); }
        if (id != null) { cv.put(ID, id); }
        cv.put(OBSERVATION_ID, observation_id);
        cv.put(FIELD_ID, field_id);
        cv.put(VALUE, value);
        if (updated_at != null) { cv.put(UPDATED_AT, updated_at.getTime()); }

        return cv;
    }

    public static String sqlCreate() {
        return "CREATE TABLE " + TABLE_NAME + " ("
                + ProjectFieldValue._ID + " INTEGER PRIMARY KEY,"
                + "_created_at INTEGER,"
                + "_synced_at INTEGER,"
                + "_updated_at INTEGER,"
                + "created_at INTEGER,"
                + "id INTEGER,"
                + "observation_id INTEGER,"
                + "updated_at INTEGER,"
                + "value TEXT,"
                + "field_id INTEGER,"
                + "UNIQUE(field_id, observation_id) ON CONFLICT REPLACE"
                + ");";
    }


}
