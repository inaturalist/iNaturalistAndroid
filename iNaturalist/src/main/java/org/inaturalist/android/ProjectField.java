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

public class ProjectField implements BaseColumns, Serializable {
    public static final String TAG = "ProjectField";
    
    public Integer _id;
    public Integer field_id;
    public Integer project_id;
    public String name;
    public String description;
    public String data_type;
    public String allowed_values;
    public Boolean is_required = false;
    public Integer position;
    
    public static final String TABLE_NAME = "project_fields";
    
    public static final int PROJECT_FIELDS_URI_CODE = 3979;
    public static final int PROJECT_FIELD_ID_URI_CODE = 3964;
    
    public static HashMap<String, String> PROJECTION_MAP;
    public static final String AUTHORITY = "org.inaturalist.android.project_field";
    public static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/project_fields");
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.project_field";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.project_field";
    public static final String DEFAULT_SORT_ORDER = "_id DESC";
    
    public static final String FIELD_ID = "field_id";
    public static final String PROJECT_ID = "project_id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String DATA_TYPE = "data_type";
    public static final String ALLOWED_VALUES = "allowed_values";
    public static final String IS_REQUIRED = "is_required";
    public static final String POSITION = "position";


    public static final String[] PROJECTION = new String[] {
        ProjectField._ID,
        ProjectField.FIELD_ID,
        ProjectField.PROJECT_ID,
        ProjectField.NAME,
        ProjectField.DESCRIPTION,
        ProjectField.DATA_TYPE,
        ProjectField.ALLOWED_VALUES,
        ProjectField.IS_REQUIRED,
        ProjectField.POSITION
    };

    static {
        PROJECTION_MAP = new HashMap<String, String>();
        PROJECTION_MAP.put(ProjectField._ID, ProjectField._ID);
        PROJECTION_MAP.put(ProjectField.FIELD_ID, ProjectField.FIELD_ID);
        PROJECTION_MAP.put(ProjectField.PROJECT_ID, ProjectField.PROJECT_ID);
        PROJECTION_MAP.put(ProjectField.NAME, ProjectField.NAME);
        PROJECTION_MAP.put(ProjectField.DESCRIPTION, ProjectField.DESCRIPTION);
        PROJECTION_MAP.put(ProjectField.DATA_TYPE, ProjectField.DATA_TYPE);
        PROJECTION_MAP.put(ProjectField.ALLOWED_VALUES, ProjectField.ALLOWED_VALUES);
        PROJECTION_MAP.put(ProjectField.IS_REQUIRED, ProjectField.IS_REQUIRED);
        PROJECTION_MAP.put(ProjectField.POSITION, ProjectField.POSITION);
    }

    public ProjectField() {}

    public ProjectField(Cursor c) {
        if (c.getPosition() == -1) c.moveToFirst();
        BetterCursor bc = new BetterCursor(c);
        
        this._id = bc.getInt(_ID);
        this.field_id = bc.getInt(FIELD_ID);
        this.project_id = bc.getInt(PROJECT_ID);
        this.name = bc.getString(NAME);
        this.description = bc.getString(DESCRIPTION);
        this.data_type = bc.getString(DATA_TYPE);
        this.allowed_values = bc.getString(ALLOWED_VALUES);
        this.is_required = bc.getBoolean(IS_REQUIRED);
        this.position = bc.getInt(POSITION);
    }

    public ProjectField(BetterJSONObject o) {
        try {
            this.field_id = o.getInt("observation_field_id");
            if (this.field_id == null) this.field_id = o.getInt("id");
            this.project_id = o.getInt("project_id");
            
            if (o.has("observation_field")) {
                this.name = o.getJSONObject("observation_field").getString("name");
                this.description = o.getJSONObject("observation_field").getString("description");
                this.data_type = o.getJSONObject("observation_field").getString("datatype");
                this.allowed_values = o.getJSONObject("observation_field").getString("allowed_values");
            } else {
                this.name = o.getString("name");
                this.description = o.getString("description");
                this.data_type = o.getString("datatype");
                this.allowed_values = o.getString("allowed_values");
            }
            
            this.is_required = o.getBoolean("required");
            if (this.is_required == null) this.is_required = false;
            this.position = o.getInt("position");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public String toString() {
        return "ProjectField(project id: " + (project_id == null ? "null" : project_id) + ", field_id: " + field_id + ", _id: " + _id + ")";
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
        
        cv.put(FIELD_ID, field_id);
        if (project_id != null) cv.put(PROJECT_ID, project_id);
        cv.put(NAME, name);
        cv.put(DESCRIPTION, description);
        cv.put(DATA_TYPE, data_type);
        cv.put(ALLOWED_VALUES, allowed_values);
        cv.put(IS_REQUIRED, is_required);
        cv.put(POSITION, position);

        return cv;
    }

    public static String sqlCreate() {
        return "CREATE TABLE " + TABLE_NAME + " ("
                + ProjectField._ID + " INTEGER PRIMARY KEY,"
                + "field_id INTEGER,"
                + "project_id INTEGER,"
                + "name TEXT, "
                + "description TEXT, "
                + "data_type TEXT, "
                + "allowed_values TEXT, "
                + "is_required INTEGER, "
                + "position INTEGER, "
                + "UNIQUE(field_id) ON CONFLICT REPLACE"
                + ");";
    }

}
