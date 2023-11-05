package org.inaturalist.android;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Pair;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ObservationSound implements BaseColumns, Serializable {
    public Integer _id;
    public Integer id;
    public String file_url;
    public String filename;
    public String attribution;
    public String file_content_type;
    public String subtype;
    public Integer observation_id;
    public String observation_uuid;
    public Integer _observation_id;
    public Boolean is_deleted;
    public Boolean hidden;

    public static final int OBSERVATION_SOUNDS_URI_CODE = 1802;
    public static final int OBSERVATION_SOUND_ID_URI_CODE = 1687;

    public static final String AUTHORITY = "org.inaturalist.android.observation_sound";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/observation_sounds");
    public static final String DEFAULT_SORT_ORDER = "id ASC, _id ASC";
    public static final String REVERSE_DEFAULT_SORT_ORDER = "id DESC, _id DESC";

    public static final String TABLE_NAME = "observation_sounds";

    public static final String _OBSERVATION_ID = "_observation_id";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_UUID = "observation_uuid";
    public static final String SUBTYPE = "subtype";
    public static final String FILE_CONTENT_TYPE = "file_content_type";
    public static final String ATTRIBUTION = "attribution";
    public static final String FILE_URL = "file_url";
    public static final String FILENAME = "filename";
    public static final String IS_DELETED = "is_deleted";
    public static final String ID = "id";

    public static HashMap<String, String> PROJECTION_MAP;

    public static final String[] PROJECTION = new String[] {
        ObservationSound._ID,
        ObservationSound.ID,
        ObservationSound.OBSERVATION_ID,
        ObservationSound.OBSERVATION_UUID,
        ObservationSound._OBSERVATION_ID,
        ObservationSound.SUBTYPE,
        ObservationSound.FILE_CONTENT_TYPE,
        ObservationSound.ATTRIBUTION,
        ObservationSound.FILE_URL,
        ObservationSound.FILENAME,
        ObservationPhoto.IS_DELETED
    };


    static {
        PROJECTION_MAP = new HashMap<String, String>();
        PROJECTION_MAP.put(ObservationSound._ID, ObservationSound._ID);
        PROJECTION_MAP.put(ObservationSound.ID, ObservationSound.ID);
        PROJECTION_MAP.put(ObservationSound._OBSERVATION_ID, ObservationSound._OBSERVATION_ID);
        PROJECTION_MAP.put(ObservationSound.OBSERVATION_ID, ObservationSound.OBSERVATION_ID);
        PROJECTION_MAP.put(ObservationSound.OBSERVATION_UUID, ObservationSound.OBSERVATION_UUID);
        PROJECTION_MAP.put(ObservationSound.SUBTYPE, ObservationSound.SUBTYPE);
        PROJECTION_MAP.put(ObservationSound.FILE_CONTENT_TYPE, ObservationSound.FILE_CONTENT_TYPE);
        PROJECTION_MAP.put(ObservationSound.ATTRIBUTION, ObservationSound.ATTRIBUTION);
        PROJECTION_MAP.put(ObservationSound.FILE_URL, ObservationSound.FILE_URL);
        PROJECTION_MAP.put(ObservationSound.FILENAME, ObservationSound.FILENAME);
        PROJECTION_MAP.put(ObservationSound.IS_DELETED, ObservationSound.IS_DELETED);
    }

    public String toString() {
        return String.format(Locale.ENGLISH, "ObservationSound (%d / %d): %d/%d - %s - %s / %s", _id, id != null ? id : -1, observation_id != null ? observation_id : -1, _observation_id != null ? _observation_id : -1, observation_uuid, filename, file_url);
    }

    public ObservationSound() {}


    public ObservationSound(Cursor c) {
        if (c.getPosition() == -1) c.moveToFirst();
        BetterCursor bc = new BetterCursor(c);
        this._id = bc.getInt(_ID);
        this._observation_id = bc.getInteger(_OBSERVATION_ID);
        this.id = bc.getInteger(ID);
        this.observation_id = bc.getInteger(OBSERVATION_ID);
        this.observation_uuid = bc.getString(OBSERVATION_UUID);
        this.subtype = bc.getString(SUBTYPE);
        this.file_content_type = bc.getString(FILE_CONTENT_TYPE);
        this.attribution = bc.getString(ATTRIBUTION);
        this.file_url = bc.getString(FILE_URL);
        this.filename = bc.getString(FILENAME);
        this.is_deleted = bc.getBoolean(IS_DELETED);
    }

    public ObservationSound(BetterJSONObject json) {
        this.id = json.getInt("id");

        if (json.has("sound")) {
            BetterJSONObject sound = new BetterJSONObject(json.getJSONObject("sound"));
            this.file_url = sound.getString("file_url");
            this.attribution = sound.getString("attribution");
            this.file_content_type = sound.getString("file_content_type");
            this.subtype = sound.getString("subtype");
            this.hidden = sound.getBoolean("hidden");
        } else {
            this.file_url = json.getString("file_url");
            this.attribution = json.getString("attribution");
            this.file_content_type = json.getString("file_content_type");
            this.subtype = json.getString("subtype");
        }
    }

    public boolean isSoundCloud() {
        return (this.file_url == null) && (this.filename == null);
    }

    public static String sqlCreate() {
        return "CREATE TABLE " + TABLE_NAME + " ("
                + ObservationSound._ID + " INTEGER PRIMARY KEY,"
                + "id INTEGER,"
                + "_observation_id INTEGER,"
                + "observation_id INTEGER,"
                + "observation_uuid TEXT,"
                + "subtype TEXT,"
                + "file_content_type TEXT,"
                + "attribution TEXT,"
                + "is_deleted INTEGER,"
                + "file_url TEXT,"
                + "filename TEXT"
                + ");";
    }

    public ContentValues getContentValues() {
        ContentValues cv = new ContentValues();
        cv.put(ID, id);
        cv.put(OBSERVATION_ID, observation_id);
        cv.put(OBSERVATION_UUID, observation_uuid);
        cv.put(_OBSERVATION_ID, _observation_id);
        cv.put(SUBTYPE, subtype);
        cv.put(FILE_CONTENT_TYPE, file_content_type);
        cv.put(ATTRIBUTION, attribution);
        cv.put(FILE_URL, file_url);
        cv.put(FILENAME, filename);
        cv.put(IS_DELETED, is_deleted);

        return cv;
    }

    public ArrayList<Pair<String, String>> getParams() {
        final ArrayList<Pair<String, String>> params = new ArrayList<Pair<String, String>>();
        if (observation_id != null) { params.add(new Pair("observation_sound[observation_id]", observation_id.toString())); }

        return params;
    }


    public Uri getUri() {
        if (_id == null) {
            return null;
        } else {
            return ContentUris.withAppendedId(CONTENT_URI, _id);
        }
    }

    public void merge(ObservationSound observation_sound) {
        // overwrite
        this.id = observation_sound.id;
        this.subtype = observation_sound.subtype;
        this.file_content_type = observation_sound.file_content_type;
        this.attribution = observation_sound.attribution;
        this.file_url = observation_sound.file_url;
    }
}
