package org.inaturalist.android;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ObservationProvider extends ContentProvider {
	private static final String TAG = "ObservationProvider";
	private static final String DATABASE_NAME = "inaturalist.db";
	private static final int DATABASE_VERSION = 3;
	private static final String TABLE_NAME = "observations";
	private static final SQLiteCursorFactory sFactory;
	
	static {
		sFactory = new SQLiteCursorFactory(true);
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
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(TABLE_NAME);

		switch (Observation.URI_MATCHER.match(uri)) {
		case Observation.OBSERVATIONS_URI_CODE:
			qb.setProjectionMap(Observation.PROJECTION_MAP);
			break;

		case Observation.OBSERVATION_ID_URI_CODE:
			qb.setProjectionMap(Observation.PROJECTION_MAP);
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
		switch (Observation.URI_MATCHER.match(uri)) {
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
		// Validate the requested uri
		if (Observation.URI_MATCHER.match(uri) != Observation.OBSERVATIONS_URI_CODE) {
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
		switch (Observation.URI_MATCHER.match(uri)) {
		case Observation.OBSERVATIONS_URI_CODE:
			count = db.delete(TABLE_NAME, where, whereArgs);
			break;

		case Observation.OBSERVATION_ID_URI_CODE:
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
		switch (Observation.URI_MATCHER.match(uri)) {
		case Observation.OBSERVATIONS_URI_CODE:
			count = db.update(TABLE_NAME, values, where, whereArgs);
			break;

		case Observation.OBSERVATION_ID_URI_CODE:
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
