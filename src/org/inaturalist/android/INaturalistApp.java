package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

public class INaturalistApp extends Application {
    private SharedPreferences mPrefs;
    private NotificationManager mNotificationManager;
    public static Integer VERSION = 1;
    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    public static SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("MMMM d, yyyy h:mm:ss a z");
    public static SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy");
    public static SimpleDateFormat SHORT_TIME_FORMAT = new SimpleDateFormat("h:mm a z");
    private static Integer SYNC_NOTIFICATION = 3; 
    
    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        String s="Debug-infos:";
        s += "\n OS Version: " + System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")";
        s += "\n OS API Level: " + android.os.Build.VERSION.SDK;
        s += "\n Device: " + android.os.Build.DEVICE;
        s += "\n Model (and Product): " + android.os.Build.MODEL + " ("+ android.os.Build.PRODUCT + ")";
        Log.d("INAT", s);
    }
    
    public void checkSyncNeeded() {
        Cursor oCursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, 
                "_synced_at IS NULL OR (_updated_at > _synced_at)", null, Observation.DEFAULT_SORT_ORDER);
        Cursor opCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION, 
                "_synced_at IS NULL OR (_updated_at > _synced_at)", null, ObservationPhoto.DEFAULT_SORT_ORDER);
        if (oCursor.getCount() == 0 && opCursor.getCount() == 0) {
            mNotificationManager.cancel(SYNC_NOTIFICATION);
        } else {
            serviceNotify(SYNC_NOTIFICATION, 
                    "Sync required", 
                    oCursor.getCount() + " observations, " + opCursor.getCount() + " photos to sync",
                    null,
                    new Intent(INaturalistService.ACTION_SYNC, null, this, INaturalistService.class));
        }
    }

    public boolean loggedIn() {
        return getPrefs().contains("credentials");
    }

    public String currentUserLogin() {
        return getPrefs().getString("username", null);
    }

    public SharedPreferences getPrefs() {
        if (mPrefs == null) {
            mPrefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);   
        }
        return mPrefs;
    }

    public void notify(Integer id, String title, String content) {
        notify(id, title, content, null);
    }
    
    public void notify(Integer id, String title, String content, String ticker) {
        Intent intent = new Intent(this, ObservationListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notify(id, title, content, ticker, intent);
    }

    public void sweepingNotify(Integer id, String title, String content, String ticker, Intent intent) {
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }
        notify(id, title, content, ticker, intent);
    }
    
    public void sweepingNotify(Integer id, String title, String content, String ticker) {
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }
        notify(id, title, content, ticker);
    }
    
    public void notify(Integer id, String title, String content, String ticker, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        notify(id, title, content, ticker, pendingIntent);
    }
    
    public void serviceNotify(Integer id, String title, String content, String ticker, Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        notify(id, title, content, ticker, pendingIntent);
    }
    
    public void notify(Integer id, String title, String content, String ticker, PendingIntent pendingIntent) {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        Notification notification = new Notification(R.drawable.ic_stat_inaturalist, ticker, System.currentTimeMillis());
        notification.setLatestEventInfo(getApplicationContext(), title, content, pendingIntent);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(id, notification);
    }
    
    public String formatDate(Timestamp date) { return DATE_FORMAT.format(date); }
    public String formatDatetime(Timestamp date) { return DATETIME_FORMAT.format(date); }
    public String shortFormatDate(Timestamp date) { return SHORT_DATE_FORMAT.format(date); }
    public String shortFormatTime(Timestamp date) { return SHORT_TIME_FORMAT.format(date); }
}
