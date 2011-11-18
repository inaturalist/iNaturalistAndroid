package org.inaturalist.android;

import java.sql.Timestamp;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public class ObservationProvider extends ContentProvider {
	public static final String AUTHORITY = "org.inaturalist.android.observation";
    private static final String TAG = "ObservationProvider";
    private static final String DATABASE_NAME = "inaturalist.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_NAME = "observations";
    private static HashMap<String, String> sProjectionMap;
    
    // uri match ids
    private static final int OBSERVATIONS_URI_CODE = 1;
    private static final int OBSERVATION_ID_URI_CODE = 2;
    private static final UriMatcher sUriMatcher;
    
    public static final class Observation implements BaseColumns {
    	public int id;
    	public String speciesGuess;
    	public int taxonId;
    	public String description;
    	public Timestamp createdAt;
    	public Timestamp updatedAt;
    	
    	public static final Uri 	CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/observations");
        public static final String 	CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.observation";
        public static final String 	CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.observation";
        public static final String 	DEFAULT_SORT_ORDER = "created_at DESC";
        public static final String 	SPECIES_GUESS = "species_guess";
        public static final String 	TAXON_ID = "taxon_id";
        public static final String 	DESCRIPTION = "description";
        public static final String 	CREATED_AT = "created_at";
        public static final String 	UPDATED_AT = "updated_at";
        
        public Observation(Cursor c) {
        	c.moveToFirst();
        	this.id = c.getInt(c.getColumnIndexOrThrow(_ID));
        	this.speciesGuess = c.getString(c.getColumnIndexOrThrow(SPECIES_GUESS));
        	this.taxonId = c.getInt(c.getColumnIndexOrThrow(TAXON_ID));
        	this.description = c.getString(c.getColumnIndexOrThrow(DESCRIPTION));
//        	this.createdAt = c.getLong(c.getColumnIndexOrThrow(CREATED_AT));
        }
    }

    public static final String[] PROJECTION = new String[] {
        Observation._ID, 
        Observation.SPECIES_GUESS,
        Observation.TAXON_ID,
        Observation.DESCRIPTION,
        Observation.CREATED_AT,
        Observation.UPDATED_AT
    };
    
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "observations", OBSERVATIONS_URI_CODE);
        sUriMatcher.addURI(AUTHORITY, "observations/#", OBSERVATION_ID_URI_CODE);

        sProjectionMap = new HashMap<String, String>();
        sProjectionMap.put(Observation._ID, Observation._ID);
        sProjectionMap.put(Observation.SPECIES_GUESS, Observation.SPECIES_GUESS);
        sProjectionMap.put(Observation.TAXON_ID, Observation.TAXON_ID);
        sProjectionMap.put(Observation.DESCRIPTION, Observation.DESCRIPTION);
        sProjectionMap.put(Observation.CREATED_AT, Observation.CREATED_AT);
        sProjectionMap.put(Observation.UPDATED_AT, Observation.UPDATED_AT);
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            Log.d(TAG, "created DatabaseHelper");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	Log.d(TAG, "creating database");
            db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                    + Observation._ID + " INTEGER PRIMARY KEY,"
                    + Observation.SPECIES_GUESS + " TEXT,"
                    + Observation.TAXON_ID      + " INTEGER,"
                    + Observation.DESCRIPTION   + " TEXT,"
                    + Observation.CREATED_AT    + " INTEGER,"
                    + Observation.UPDATED_AT    + " INTEGER"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
    	Log.d(TAG, "setting mOpenHelper");
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
        case OBSERVATIONS_URI_CODE:
            qb.setProjectionMap(sProjectionMap);
            break;

        case OBSERVATION_ID_URI_CODE:
            qb.setProjectionMap(sProjectionMap);
            qb.appendWhere(Observation._ID + "=" + uri.getPathSegments().get(1));
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Observation.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case OBSERVATIONS_URI_CODE:
            return Observation.CONTENT_TYPE;

        case OBSERVATION_ID_URI_CODE:
            return Observation.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != OBSERVATIONS_URI_CODE) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        // Make sure that the fields are all set
        if (values.containsKey(Observation.CREATED_AT) == false) {
            values.put(Observation.CREATED_AT, now);
        }

        if (values.containsKey(Observation.UPDATED_AT) == false) {
            values.put(Observation.UPDATED_AT, now);
        }
//
//        if (values.containsKey(Observation.NOTE) == false) {
//            values.put(Observation.NOTE, "");
//        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_NAME, Observation.SPECIES_GUESS, values);
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(Observation.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case OBSERVATIONS_URI_CODE:
            count = db.delete(TABLE_NAME, where, whereArgs);
            break;

        case OBSERVATION_ID_URI_CODE:
            String noteId = uri.getPathSegments().get(1);
            count = db.delete(TABLE_NAME, Observation._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        Long now = Long.valueOf(System.currentTimeMillis());
        if (values.containsKey(Observation.UPDATED_AT) == false) {
            values.put(Observation.UPDATED_AT, now);
        }
        switch (sUriMatcher.match(uri)) {
        case OBSERVATIONS_URI_CODE:
            count = db.update(TABLE_NAME, values, where, whereArgs);
            break;

        case OBSERVATION_ID_URI_CODE:
            String noteId = uri.getPathSegments().get(1);
            count = db.update(TABLE_NAME, values, Observation._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

}
