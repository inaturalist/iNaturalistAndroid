package org.inaturalist.android;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.util.Pair;

import org.tinylog.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class TrackingCursor extends SQLiteCursor {
    private static List<TrackingCursor> openCursors = Collections.synchronizedList(new LinkedList<>());
    private String[] mSelectionArgs;
    public String query;
    public Date dateAdded;

    public TrackingCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
                          String editTable, SQLiteQuery query) {
        super(db, driver, editTable, query);
        this.query = query.toString();
        this.dateAdded = new Date();
        synchronized (openCursors) {
            openCursors.add(this);

            if (openCursors.size() > 100) {
                Logger.tag("TrackingCursor").debug("Open cursors: " + openCursors.size());

                Collections.sort(openCursors, new Comparator<TrackingCursor>() {
                    @Override
                    public int compare(TrackingCursor t1, TrackingCursor t2) {
                        return t1.dateAdded.compareTo(t2.dateAdded);
                    }
                });

                List<TrackingCursor> toRemove = new LinkedList();
                for (int i = 0; i < Math.max(20, openCursors.size() - 100); i++) {
                    Logger.tag("TrackingCursor").debug("Closing Open Cursor: " + openCursors.get(i).dateAdded + ": " + openCursors.get(i).query);
                    openCursors.get(i).close();
                    toRemove.add(openCursors.get(i));
                }
                openCursors.removeAll(toRemove);
            }
        }
    }

    public void close() {
        synchronized (openCursors) {
            openCursors.remove(this);
        }
    }

    public void setSelectionArgs(String[] selectionArgs) {
        mSelectionArgs = selectionArgs;
    }

}