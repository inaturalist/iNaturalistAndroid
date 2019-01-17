package org.inaturalist.android;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

public class ObservationProvider extends ContentProvider {
    private static final String TAG = "ObservationProvider";
    private static final String DATABASE_NAME = "inaturalist.db";
    private static final int DATABASE_VERSION = 16;
    private static final String[] TABLE_NAMES = new String[]{Observation.TABLE_NAME, ObservationPhoto.TABLE_NAME, Project.TABLE_NAME, ProjectObservation.TABLE_NAME, ProjectField.TABLE_NAME, ProjectFieldValue.TABLE_NAME};
    private static final SQLiteCursorFactory sFactory;
    public static final UriMatcher URI_MATCHER;

    static {
        sFactory = new SQLiteCursorFactory(true);
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(Observation.AUTHORITY, "observations", Observation.OBSERVATIONS_URI_CODE);
        URI_MATCHER.addURI(Observation.AUTHORITY, "observations/#", Observation.OBSERVATION_ID_URI_CODE);
        URI_MATCHER.addURI(ObservationPhoto.AUTHORITY, "observation_photos", ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE);
        URI_MATCHER.addURI(ObservationPhoto.AUTHORITY, "observation_photos/#", ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE);
        URI_MATCHER.addURI(Project.AUTHORITY, "projects", Project.PROJECTS_URI_CODE);
        URI_MATCHER.addURI(Project.AUTHORITY, "projects/#", Project.PROJECT_ID_URI_CODE);
        URI_MATCHER.addURI(ProjectObservation.AUTHORITY, "project_observations", ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE);
        URI_MATCHER.addURI(ProjectObservation.AUTHORITY, "project_observations/#", ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE);
        URI_MATCHER.addURI(ProjectField.AUTHORITY, "project_fields", ProjectField.PROJECT_FIELDS_URI_CODE);
        URI_MATCHER.addURI(ProjectField.AUTHORITY, "project_fields/#", ProjectField.PROJECT_FIELD_ID_URI_CODE);
        URI_MATCHER.addURI(ProjectFieldValue.AUTHORITY, "project_field_values", ProjectFieldValue.PROJECT_FIELD_VALUES_URI_CODE);
        URI_MATCHER.addURI(ProjectFieldValue.AUTHORITY, "project_field_values/#", ProjectFieldValue.PROJECT_FIELD_VALUE_ID_URI_CODE);
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        Context mContext;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, sFactory, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Observation.sqlCreate());
            db.execSQL(ObservationPhoto.sqlCreate());
            db.execSQL(Project.sqlCreate());
            db.execSQL(ProjectObservation.sqlCreate());
            db.execSQL(ProjectField.sqlCreate());
            db.execSQL(ProjectFieldValue.sqlCreate());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

            // Do any changes to the existing tables (e.g. add columns), according to the new DB version

            if (oldVersion < 7) {
                addColumnIfNotExists(db, ObservationPhoto.TABLE_NAME, "uuid", "TEXT");
                addColumnIfNotExists(db, Observation.TABLE_NAME, "uuid", "TEXT");
            }
            if (oldVersion < 8) {
                addColumnIfNotExists(db, Observation.TABLE_NAME, "preferred_common_name", "TEXT");
            }
            if (oldVersion < 9) {
                addColumnIfNotExists(db, ObservationPhoto.TABLE_NAME, "photo_filename", "TEXT");
            }
            if (oldVersion < 10) {
                // Need to change the constraint of the project field table - which is only possible
                // by recreating the table
                db.execSQL("DROP TABLE IF EXISTS " + ProjectField.TABLE_NAME);
                db.execSQL(ProjectField.sqlCreate());
                // Re-populate the table
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_JOINED_PROJECTS_ONLINE, null, mContext, INaturalistService.class);
                ContextCompat.startForegroundService(mContext, serviceIntent);
            }
            if (oldVersion < 11) {
                // Add a "is_deleted" column to ObservationPhoto
                addColumnIfNotExists(db, ObservationPhoto.TABLE_NAME, "is_deleted", "INTEGER");
            }
            if (oldVersion < 12) {
                // Add a "original_photo_filename" column to ObservationPhoto
                addColumnIfNotExists(db, ObservationPhoto.TABLE_NAME, "original_photo_filename", "TEXT");
            }
            if (oldVersion < 13) {
                // Add a "private_place_guess" column to Observation
                addColumnIfNotExists(db, Observation.TABLE_NAME, "private_place_guess", "TEXT");
            }
            if (oldVersion < 14) {
                // Add a "owners_identification_from_vision" column to Observation
                addColumnIfNotExists(db, Observation.TABLE_NAME, "owners_identification_from_vision", "INTEGER");
            }
            if (oldVersion < 15) {
                // Add a "project_type" column to Project
                addColumnIfNotExists(db, Project.TABLE_NAME, "project_type", "TEXT");
            }
            if (oldVersion < 16) {
                // Add "scientific_name", "rank" and "rank_level" columns to Observation
                addColumnIfNotExists(db, Observation.TABLE_NAME, "scientific_name", "TEXT");
                addColumnIfNotExists(db, Observation.TABLE_NAME, "rank", "TEXT");
                addColumnIfNotExists(db, Observation.TABLE_NAME, "rank_level", "INTEGER");
            }
        }

        // Adds a new column to a table if doesn't exist already
        // @param db
        // @param tableName
        // @param columnName
        // @param columnDefinition - type + default value + contraints (e.g. "CHAR(25) DEFAULT 4 NOT NULL")
        private void addColumnIfNotExists(SQLiteDatabase db,  String tableName, String columnName, String columnDefinition) {
            Cursor cursor = db.rawQuery("SELECT * FROM " + tableName + " LIMIT 1", null);

            // See if column exists
            int columnIndex = cursor.getColumnIndex(columnName);
            cursor.close();
            if (columnIndex < 0) {
                // Add in the new column
                db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition + ";");
            }

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
        String orderBy;

        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            qb.setTables(Observation.TABLE_NAME);
            qb.setProjectionMap(Observation.PROJECTION_MAP);
            orderBy = TextUtils.isEmpty(sortOrder) ? Observation.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            qb.setTables(Observation.TABLE_NAME);
            qb.setProjectionMap(Observation.PROJECTION_MAP);
            qb.appendWhere(Observation._ID + "=" + uri.getPathSegments().get(1));
            orderBy = TextUtils.isEmpty(sortOrder) ? Observation.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            qb.setTables(ObservationPhoto.TABLE_NAME);
            qb.setProjectionMap(ObservationPhoto.PROJECTION_MAP);
            orderBy = TextUtils.isEmpty(sortOrder) ? ObservationPhoto.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            qb.setTables(ObservationPhoto.TABLE_NAME);
            qb.setProjectionMap(ObservationPhoto.PROJECTION_MAP);
            qb.appendWhere(ObservationPhoto._ID + "=" + uri.getPathSegments().get(1));
            orderBy = TextUtils.isEmpty(sortOrder) ? ObservationPhoto.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case Project.PROJECTS_URI_CODE:
            qb.setTables(Project.TABLE_NAME);
            qb.setProjectionMap(Project.PROJECTION_MAP);
            orderBy = TextUtils.isEmpty(sortOrder) ? Project.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case Project.PROJECT_ID_URI_CODE:
            qb.setTables(Project.TABLE_NAME);
            qb.setProjectionMap(Project.PROJECTION_MAP);
            qb.appendWhere(Project._ID + "=" + uri.getPathSegments().get(1));
            orderBy = TextUtils.isEmpty(sortOrder) ? Project.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE:
            qb.setTables(ProjectObservation.TABLE_NAME);
            qb.setProjectionMap(ProjectObservation.PROJECTION_MAP);
            orderBy = TextUtils.isEmpty(sortOrder) ? ProjectObservation.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE:
            qb.setTables(ProjectObservation.TABLE_NAME);
            qb.setProjectionMap(ProjectObservation.PROJECTION_MAP);
            qb.appendWhere(ProjectObservation._ID + "=" + uri.getPathSegments().get(1));
            orderBy = TextUtils.isEmpty(sortOrder) ? ProjectObservation.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ProjectField.PROJECT_FIELDS_URI_CODE:
            qb.setTables(ProjectField.TABLE_NAME);
            qb.setProjectionMap(ProjectField.PROJECTION_MAP);
            orderBy = TextUtils.isEmpty(sortOrder) ? ProjectField.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ProjectField.PROJECT_FIELD_ID_URI_CODE:
            qb.setTables(ProjectField.TABLE_NAME);
            qb.setProjectionMap(ProjectField.PROJECTION_MAP);
            qb.appendWhere(ProjectField._ID + "=" + uri.getPathSegments().get(1));
            orderBy = TextUtils.isEmpty(sortOrder) ? ProjectField.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUES_URI_CODE:
            qb.setTables(ProjectFieldValue.TABLE_NAME);
            qb.setProjectionMap(ProjectFieldValue.PROJECTION_MAP);
            orderBy = TextUtils.isEmpty(sortOrder) ? ProjectFieldValue.DEFAULT_SORT_ORDER : sortOrder;
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUE_ID_URI_CODE:
            qb.setTables(ProjectFieldValue.TABLE_NAME);
            qb.setProjectionMap(ProjectField.PROJECTION_MAP);
            qb.appendWhere(ProjectFieldValue._ID + "=" + uri.getPathSegments().get(1));
            orderBy = TextUtils.isEmpty(sortOrder) ? ProjectFieldValue.DEFAULT_SORT_ORDER : sortOrder;
            break;
             
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
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
            
        case Project.PROJECT_ID_URI_CODE:
            return Project.CONTENT_ITEM_TYPE;

        case ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE:
            return ProjectObservation.CONTENT_ITEM_TYPE;
            
        case ProjectField.PROJECT_FIELD_ID_URI_CODE:
            return ProjectField.CONTENT_ITEM_TYPE;
            
        case ProjectFieldValue.PROJECT_FIELD_VALUE_ID_URI_CODE:
            return ProjectFieldValue.CONTENT_ITEM_TYPE;
            
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
        case Project.PROJECTS_URI_CODE:
            tableName = Project.TABLE_NAME;
            contentUri = Project.CONTENT_URI;
            break;
        case Project.PROJECT_ID_URI_CODE:
            tableName = Project.TABLE_NAME;
            contentUri = Project.CONTENT_URI;
            break;
        case ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE:
            tableName = ProjectObservation.TABLE_NAME;
            contentUri = ProjectObservation.CONTENT_URI;
            break;
        case ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE:
            tableName = ProjectObservation.TABLE_NAME;
            contentUri = ProjectObservation.CONTENT_URI;
            break;
        case ProjectField.PROJECT_FIELDS_URI_CODE:
            tableName = ProjectField.TABLE_NAME;
            contentUri = ProjectField.CONTENT_URI;
            break;
        case ProjectField.PROJECT_FIELD_ID_URI_CODE:
            tableName = ProjectField.TABLE_NAME;
            contentUri = ProjectField.CONTENT_URI;
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUES_URI_CODE:
            tableName = ProjectFieldValue.TABLE_NAME;
            contentUri = ProjectFieldValue.CONTENT_URI;
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUE_ID_URI_CODE:
            tableName = ProjectFieldValue.TABLE_NAME;
            contentUri = ProjectFieldValue.CONTENT_URI;
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
        
        int uriCode = URI_MATCHER.match(uri);

        // Make sure that the fields are all set
        if  (values.containsKey(Observation._SYNCED_AT)) {
            // if synced at is being set, updated at should *always* match exactly
            values.put(Observation._UPDATED_AT, values.getAsLong(Observation._SYNCED_AT));
            values.put(Observation._CREATED_AT, values.getAsLong(Observation._SYNCED_AT));
        } else if ((uriCode != Project.PROJECTS_URI_CODE) && (uriCode != Project.PROJECT_ID_URI_CODE) &&
                (uriCode != ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE) && (uriCode != ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE) &&
                (uriCode != ProjectField.PROJECT_FIELDS_URI_CODE) && (uriCode != ProjectField.PROJECT_FIELD_ID_URI_CODE)) {
            values.put(Observation._CREATED_AT, now);
            values.put(Observation.CREATED_AT, now);
            values.put(Observation._UPDATED_AT, now);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Log.d(TAG, "Insert: " + tableName + "; values: " + values.toString());
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
        Uri contentUri;
        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            // TODO delete associated observation photos
            count = db.delete(Observation.TABLE_NAME, where, whereArgs);
            contentUri = Observation.CONTENT_URI;
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = Observation.CONTENT_URI;
            count = db.delete(Observation.TABLE_NAME, Observation._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            delete(ObservationPhoto.CONTENT_URI, ObservationPhoto._OBSERVATION_ID + "=" + id, null);
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            count = db.delete(ObservationPhoto.TABLE_NAME, where, whereArgs);
            contentUri = ObservationPhoto.CONTENT_URI;
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ObservationPhoto.CONTENT_URI;
            count = db.delete(ObservationPhoto.TABLE_NAME, ObservationPhoto._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case Project.PROJECTS_URI_CODE:
            count = db.delete(Project.TABLE_NAME, where, whereArgs);
            contentUri = Project.CONTENT_URI;
            break;
        case Project.PROJECT_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = Project.CONTENT_URI;
            count = db.delete(Project.TABLE_NAME, Project._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE:
            count = db.delete(ProjectObservation.TABLE_NAME, where, whereArgs);
            contentUri = ProjectObservation.CONTENT_URI;
            break;
        case ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ProjectObservation.CONTENT_URI;
            count = db.delete(ProjectObservation.TABLE_NAME, Project._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ProjectField.PROJECT_FIELDS_URI_CODE:
            count = db.delete(ProjectField.TABLE_NAME, where, whereArgs);
            contentUri = ProjectField.CONTENT_URI;
            break;
        case ProjectField.PROJECT_FIELD_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ProjectField.CONTENT_URI;
            count = db.delete(ProjectField.TABLE_NAME, Project._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUES_URI_CODE:
            count = db.delete(ProjectFieldValue.TABLE_NAME, where, whereArgs);
            contentUri = ProjectFieldValue.CONTENT_URI;
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUE_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ProjectFieldValue.CONTENT_URI;
            count = db.delete(ProjectFieldValue.TABLE_NAME, Project._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
            
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        getContext().getContentResolver().notifyChange(contentUri, null);
        return count;
    }
    
    // Deletes photo files from local storage. Probably not a good idea, but leaving 
    // it here for now in case we want to bring it back as an option
    private void deleteAssociatedImages(String where) {
        Cursor c = query(ObservationPhoto.CONTENT_URI, 
                new String[] {ObservationPhoto._ID, ObservationPhoto._PHOTO_ID}, 
                where, 
                null, 
                null);
        if (c.getCount() == 0) return;
        BetterCursor bc;
        ArrayList<Integer> photoIds = new ArrayList<Integer>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            bc = new BetterCursor(c);
            photoIds.add(bc.getInt(ObservationPhoto._PHOTO_ID));
            c.moveToNext();
        }
        String photoWhere = MediaStore.Images.ImageColumns._ID+" IN ("+StringUtils.join(photoIds, ",")+")";
        getContext().getContentResolver().delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                photoWhere, 
                null);
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String id;
        Uri contentUri;
        
        int uriCode = URI_MATCHER.match(uri);
        
        if  (values.containsKey(Observation._SYNCED_AT)) {
            // if synced at is being set, updated at should *always* match exactly
            values.put(Observation._UPDATED_AT, values.getAsLong(Observation._SYNCED_AT));
        } else if ((uriCode != Project.PROJECTS_URI_CODE) && (uriCode != Project.PROJECT_ID_URI_CODE) &&
                (uriCode != ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE) && (uriCode != ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE) &&
                (uriCode != ProjectField.PROJECT_FIELDS_URI_CODE) && (uriCode != ProjectField.PROJECT_FIELD_ID_URI_CODE)) {
            values.put(Observation._UPDATED_AT, System.currentTimeMillis());
        }
        
        switch (URI_MATCHER.match(uri)) {
        case Observation.OBSERVATIONS_URI_CODE:
            count = db.update(Observation.TABLE_NAME, values, where, whereArgs);
            contentUri = Observation.CONTENT_URI;
            break;
        case Observation.OBSERVATION_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = Observation.CONTENT_URI;
            Log.d(TAG, "Update " + Observation.TABLE_NAME + "; " + values.toString());
            count = db.update(Observation.TABLE_NAME, values, Observation._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            
            // update foreign key in observation_photos
            if (count > 0 && values.containsKey(Observation.ID)) {
                ContentValues cv = new ContentValues();
                cv.put(ObservationPhoto.OBSERVATION_ID, values.getAsInteger(Observation.ID));
                Log.d(TAG, "Update " + ObservationPhoto.TABLE_NAME + "; " + cv.toString());
                db.update(ObservationPhoto.TABLE_NAME, cv, ObservationPhoto._OBSERVATION_ID + "=" + id, null);
            }
            
            // update foreign key in project_observations / project_field_values
            if ((count > 0) && (values.containsKey(Observation.ID)) && (values.get(Observation.ID) != null)) {
                ContentValues cv = new ContentValues();
                cv.put(ProjectObservation.OBSERVATION_ID, values.getAsInteger(Observation.ID));
                Log.d(TAG, "Update observation from " + id + "to " + values.getAsInteger(Observation.ID));
                db.update(ProjectObservation.TABLE_NAME, cv, ProjectObservation.OBSERVATION_ID + "=" + id, null);
                
                cv = new ContentValues();
                cv.put(ProjectFieldValue.OBSERVATION_ID, values.getAsInteger(Observation.ID));
                db.update(ProjectFieldValue.TABLE_NAME, cv, ProjectFieldValue.OBSERVATION_ID + "=" + id, null);
            }
            
            break;
        case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
            count = db.update(ObservationPhoto.TABLE_NAME, values, where, whereArgs);
            contentUri = ObservationPhoto.CONTENT_URI;
            break;
        case ObservationPhoto.OBSERVATION_PHOTO_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ObservationPhoto.CONTENT_URI;
            Log.d(TAG, "Update " + ObservationPhoto.TABLE_NAME + "; " + values.toString());
            count = db.update(ObservationPhoto.TABLE_NAME, values, ObservationPhoto._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case Project.PROJECTS_URI_CODE:
            count = db.update(Project.TABLE_NAME, values, where, whereArgs);
            contentUri = Project.CONTENT_URI;
            break;
        case Project.PROJECT_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = Project.CONTENT_URI;
            count = db.update(Project.TABLE_NAME, values, Project._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ProjectObservation.PROJECT_OBSERVATIONS_URI_CODE:
            count = db.update(ProjectObservation.TABLE_NAME, values, where, whereArgs);
            contentUri = ProjectObservation.CONTENT_URI;
            break;
        case ProjectObservation.PROJECT_OBSERVATION_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ProjectObservation.CONTENT_URI;
            count = db.update(ProjectObservation.TABLE_NAME, values, ProjectObservation._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ProjectField.PROJECT_FIELDS_URI_CODE:
            count = db.update(ProjectField.TABLE_NAME, values, where, whereArgs);
            contentUri = ProjectField.CONTENT_URI;
            break;
        case ProjectField.PROJECT_FIELD_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ProjectField.CONTENT_URI;
            count = db.update(ProjectField.TABLE_NAME, values, ProjectField._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUES_URI_CODE:
            count = db.update(ProjectFieldValue.TABLE_NAME, values, where, whereArgs);
            contentUri = ProjectFieldValue.CONTENT_URI;
            break;
        case ProjectFieldValue.PROJECT_FIELD_VALUE_ID_URI_CODE:
            id = uri.getPathSegments().get(1);
            contentUri = ProjectFieldValue.CONTENT_URI;
            count = db.update(ProjectFieldValue.TABLE_NAME, values, ProjectFieldValue._ID + "=" + id
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;
 
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null);
        getContext().getContentResolver().notifyChange(contentUri, null);
        return count;
    }
}
