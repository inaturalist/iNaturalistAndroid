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

public class Project implements BaseColumns, Serializable {
    public static final String TAG = "Project";
    
    public Integer _id;
    public Integer id;
    public String title;
    public String description;
    public String icon_url;
    public Integer check_list_id;
    
    public static final String TABLE_NAME = "projects";
    
    public static final int PROJECTS_URI_CODE = 1979;
    public static final int PROJECT_ID_URI_CODE = 1964;
    
    public static HashMap<String, String> PROJECTION_MAP;
    public static final String AUTHORITY = "org.inaturalist.android.project";
    public static final Uri    CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/projects");
    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.inatproject";
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.inatproject";
    public static final String DEFAULT_SORT_ORDER = "_id DESC";
    
    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String ICON_URL = "icon_url";
    public static final String CHECK_LIST_ID = "check_list_id";


    public static final String[] PROJECTION = new String[] {
        Project._ID,
        Project.ID,
        Project.DESCRIPTION,
        Project.TITLE,
        Project.ICON_URL,
        Project.CHECK_LIST_ID
    };

    static {
        PROJECTION_MAP = new HashMap<String, String>();
        PROJECTION_MAP.put(Project._ID, Project._ID);
        PROJECTION_MAP.put(Project.ID, Project.ID);
        PROJECTION_MAP.put(Project.DESCRIPTION, Project.DESCRIPTION);
        PROJECTION_MAP.put(Project.TITLE, Project.TITLE);
        PROJECTION_MAP.put(Project.ICON_URL, Project.ICON_URL);
        PROJECTION_MAP.put(Project.CHECK_LIST_ID, Project.CHECK_LIST_ID);
    }

    public Project() {}

    public Project(Cursor c) {
        if (c.getPosition() == -1) c.moveToFirst();
        BetterCursor bc = new BetterCursor(c);
        
        this._id = bc.getInt(_ID);
        this.id = bc.getInt(ID);
        this.description = bc.getString(DESCRIPTION);
        this.title = bc.getString(TITLE);
        this.icon_url = bc.getString(ICON_URL);
        this.check_list_id = bc.getInt(CHECK_LIST_ID);
    }

    public Project(BetterJSONObject o) {
        this.id = o.getInt("id");
        this.title = o.getString("title");
        this.description = o.getString("description");
        this.icon_url = o.getString("icon_url");
        
        try {
            this.check_list_id = o.getJSONObject("project_list").getInt("id");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    public JSONObject toJSONObject() {
        BetterJSONObject obj = new BetterJSONObject();
        
        obj.put("id", id);
        obj.put("title", title);
        obj.put("description", description);
        obj.put("icon_url", icon_url);
        BetterJSONObject projectList = new BetterJSONObject();
        projectList.put("id", check_list_id);
        obj.put("project_list", projectList.getJSONObject());
        
        return obj.getJSONObject();
    }


    @Override
    public String toString() {
        return "Project(id: " + id + ", _id: " + _id + ")";
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
        
        cv.put(ID, id);
        cv.put(DESCRIPTION, description);
        cv.put(TITLE, title);
        cv.put(ICON_URL, icon_url);
        cv.put(CHECK_LIST_ID, check_list_id);

        return cv;
    }

    public static String sqlCreate() {
        return "CREATE TABLE " + TABLE_NAME + " ("
                + Project._ID + " INTEGER PRIMARY KEY,"
                + "title TEXT,"
                + "description TEXT,"
                + "icon_url TEXT,"
                + "id INTEGER,"
                + "check_list_id INTEGER"
                + ");";
    }

}
