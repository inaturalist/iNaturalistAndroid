package org.inaturalist.android;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import android.util.Log;

/**
 * Implement the cursor factory in order to log the queries before returning 
 * the cursor
 * 
 * @author Vincent @ MarvinLabs
 * http://stackoverflow.com/questions/5966584/logging-sql-queries-in-android
 */
public class SQLiteCursorFactory implements CursorFactory {

  private boolean debugQueries = false;

  public SQLiteCursorFactory() {
    this.debugQueries = false;
  }

  public SQLiteCursorFactory(boolean debugQueries) {
    this.debugQueries = debugQueries;
  }

  @Override
  public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, 
      String editTable, SQLiteQuery query) {
    if (debugQueries) {
      Log.d("SQL", query.toString());
    }
    return new SQLiteCursor(db, masterQuery, editTable, query);
  }
}