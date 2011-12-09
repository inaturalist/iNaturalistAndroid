package org.inaturalist.android;

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
    private static final String TAG = "ObservationProvider";
    private static final String DATABASE_NAME = "inaturalist.db";
    private static final int DATABASE_VERSION = 7;
    private static final String[] TABLE_NAMES = new String[]{Observation.TABLE_NAME, ObservationPhoto.TABLE_NAME};
    private static final SQLiteCursorFactory sFactory;
    public static final UriMatcher URI_MATCHER;

    static {
        sFactory = new SQLiteCursorFactory(true);
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(Observation.AUTHORITY, "observations", Observation.OBSERVATIONS_URI_CODE);
        URI_MATCHER.addURI(Observation.AUTHORITY, "observations/#", Observation.OBSERVATION_ID_URI_CODE);
        URI_MATCHER.addURI(ObservationPhoto.AUTHORITY, "observation_photos", ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE);
        URI_MATCHER.addURI(ObservationPhoto.AUTHORITY, "observation_photos/#", ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE);
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, sFactory, DATABASE_VERSION);
            Log.d(TAG, "created DatabaseHelper");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Observation.sqlCreate());
            db.execSQL(ObservationPhoto.sqlCreate());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            for (int i = 0; i < TABLE_NAMES.length; i++) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAMES[i]);
            }
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            qb.setTables(Observation.TABLE_NAME);
            qb.setProjectionMap(Observation.PROJECTION_MAP);
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            qb.setTables(Observation.TABLE_NAME);
            qb.setProjectionMap(Observation.PROJECTION_MAP);
            qb.appendWhere(Observation._ID + "=" + uri.getPathSegments().get(1));
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            qb.setTables(ObservationPhoto.TABLE_NAME);
            qb.setProjectionMap(ObservationPhoto.PROJECTION_MAP);
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            qb.setTables(ObservationPhoto.TABLE_NAME);
            qb.setProjectionMap(ObservationPhoto.PROJECTION_MAP);
            qb.appendWhere(ObservationPhoto._ID + "=" + uri.getPathSegments().get(1));
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
        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            return Observation.CONTENT_TYPE;

        case Observation.OBSERVATION_ID_URI_CODE:
            return Observation.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        String tableName;
        Uri contentUri;
        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            tableName = Observation.TABLE_NAME;
            contentUri = Observation.CONTENT_URI;
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            tableName = Observation.TABLE_NAME;
            contentUri = Observation.CONTENT_URI;
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            tableName = ObservationPhoto.TABLE_NAME;
            contentUri = ObservationPhoto.CONTENT_URI;
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            tableName = ObservationPhoto.TABLE_NAME;
            contentUri = ObservationPhoto.CONTENT_URI;
            break;
        default:
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
        if (values.containsKey(Observation._CREATED_AT) == false) {
            values.put(Observation._CREATED_AT, now);
        }

        if (values.containsKey(Observation._UPDATED_AT) == false) {
            values.put(Observation._UPDATED_AT, now);
        }


        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(tableName, BaseColumns._ID, values);
        if (rowId > 0) {
            Uri newUri = ContentUris.withAppendedId(contentUri, rowId);
            getContext().getContentResolver().notifyChange(newUri, null);
            return newUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String id;
        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            count = db.delete(Observation.TABLE_NAME, where, whereArgs);
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            count = db.delete(Observation.TABLE_NAME, ObservationPhoto._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            count = db.delete(ObservationPhoto.TABLE_NAME, where, whereArgs);
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            count = db.delete(ObservationPhoto.TABLE_NAME, ObservationPhoto._ID + "=" + id
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
        String id;
        Long now = Long.valueOf(System.currentTimeMillis());
        if (values.containsKey(Observation._UPDATED_AT) == false) {
            values.put(Observation._UPDATED_AT, now);
        }
        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            count = db.update(Observation.TABLE_NAME, values, where, whereArgs);
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            count = db.update(Observation.TABLE_NAME, values, ObservationPhoto._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            count = db.update(ObservationPhoto.TABLE_NAME, values, where, whereArgs);
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            count = db.update(ObservationPhoto.TABLE_NAME, values, ObservationPhoto._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
