package org.inaturalist.android;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import java.util.LinkedList;
import java.util.List;

public class TrackingCursor extends SQLiteCursor {
    private static List<Cursor> openCursors = new LinkedList<Cursor>();
    private String[] mSelectionArgs;

    public TrackingCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
                          String editTable, SQLiteQuery query) {
        super(db, driver, editTable, query);
        openCursors.add(this);
    }

    public void close() {
        openCursors.remove(this);
    }

    public static List<Cursor> getOpenCursors() {
        return openCursors;
    }
    
    public void setSelectionArgs(String[] selectionArgs) {
        mSelectionArgs = selectionArgs;
    }

}