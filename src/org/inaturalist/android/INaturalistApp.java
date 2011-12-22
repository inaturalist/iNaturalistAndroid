package org.inaturalist.android;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.util.Log;

public class INaturalistApp extends Application {
    private SharedPreferences mPrefs;
    private NotificationManager mNotificationManager;
    private static Integer SYNC_NOTIFICATION = 3;
    
    public void checkSyncNeeded() {
        Log.d("INAT", "observation changed");
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
}
