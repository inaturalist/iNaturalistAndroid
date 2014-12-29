package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.inaturalist.android.R;
import org.inaturalist.android.INaturalistService.LoginType;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class INaturalistApp extends Application {
    private final static String TAG = "INAT: Application";
    private SharedPreferences mPrefs;
    private NotificationManager mNotificationManager;
	private boolean mIsSyncing = false;
    public static Integer VERSION = 1;
    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    public static SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd h:mm:ss a z");
    public static SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("d MMM yyyy");
    public static SimpleDateFormat SHORT_TIME_FORMAT = new SimpleDateFormat("h:mm a z");
    private static Integer SYNC_NOTIFICATION = 3;
    private static Context context;
    private Locale locale = null;
    private Locale deviceLocale = null;
    
    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        INaturalistApp.context = getApplicationContext();
        deviceLocale = getResources().getConfiguration().locale;
        applyLocaleSettings();
    }

 	/**
	 * Get ISO 3166-1 alpha-2 country code for this device (or null if not available)
	 * @param context Context reference to get the TelephonyManager instance from
	 * @return country code or null
	 */
	public static String getUserCountry(Context context) {
		try {
			final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			final String simCountry = tm.getSimCountryIso();
			if (simCountry != null && simCountry.length() == 2) { // SIM country code is available
				return simCountry.toLowerCase(Locale.US);
			}
			else if (tm.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) { // device is not 3G (would be unreliable)
				String networkCountry = tm.getNetworkCountryIso();
				if (networkCountry != null && networkCountry.length() == 2) { // network country code is available
					return networkCountry.toLowerCase(Locale.US);
				}
			}
		}
		catch (Exception e) { }
		return null;
	}	
	
	/** The types of inat network memebers */
	public enum InaturalistNetworkMember {
		INATURLIST,
		NATURALISTA
	}
	
	/** Returns the set inat network member */
	public InaturalistNetworkMember getInaturalistNetworkMember() {
    	SharedPreferences settings = getPrefs();
        String networkMemberString = settings.getString("pref_network_member", InaturalistNetworkMember.INATURLIST.toString());
        
        return InaturalistNetworkMember.valueOf(networkMemberString);
	}
	   
	
	/** Set the inat network member */
	public void setInaturalistNetworkMember(InaturalistNetworkMember memberNetwork) {
    	SharedPreferences settings = getPrefs();
    	Editor settingsEditor = settings.edit();

    	settingsEditor.putString("pref_network_member", memberNetwork.toString());
    	settingsEditor.apply();
	}

 	private void updateUIAccordingToNetworkMember() {
 		InaturalistNetworkMember networkMember = getInaturalistNetworkMember();
		String newLocale;
		Resources res = getBaseContext().getResources();
 		
 		if (networkMember == InaturalistNetworkMember.NATURALISTA) {
 			newLocale = "es";
 		} else {
			// Default - USA
			newLocale = "en";
 		}

		// Change locale settings in the app
		DisplayMetrics dm = res.getDisplayMetrics();
		android.content.res.Configuration conf = res.getConfiguration();
		conf.locale = new Locale(newLocale);
		res.updateConfiguration(conf, dm);		
		
    	SharedPreferences settings = getPrefs();
    	Editor settingsEditor = settings.edit();
		settingsEditor.putString("pref_locale", newLocale);
		settingsEditor.apply();
		
		restart();
 	}
	
 	public void detectUserCountryAndUpdateNetwork(Context context) {
 		ActivityHelper helper;
        helper = new ActivityHelper(context);

		Resources res = getBaseContext().getResources();
		String country = getUserCountry(this);
		Log.d(TAG, "Detected country: " + country);
		
		country = "mx";
		if (country.equals("mx")) {
			// Don't ask the user again to switch to NATURALISTA if they've done so in the past
			if (getInaturalistNetworkMember() == InaturalistNetworkMember.NATURALISTA) return;
			
			// Mexico
			helper.confirm(
					res.getString(R.string.alert_title_use_naturalista),
					res.getString(R.string.alert_message_use_naturalista),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							setInaturalistNetworkMember(InaturalistNetworkMember.NATURALISTA);
							updateUIAccordingToNetworkMember();
						}
					});
		}

	}
   
    
    public String getFormattedDeviceLocale(){
    	if(deviceLocale!=null){
    		return deviceLocale.getDisplayLanguage();
    	}    	
    	return "";
    }
    
    public void applyLocaleSettings(){
    	SharedPreferences settings = getPrefs();

        Configuration config = getBaseContext().getResources().getConfiguration();
        
        String lang = settings.getString("pref_locale", "");
        if (! "".equals(lang) && ! config.locale.getLanguage().equals(lang))
        {
            locale = new Locale(lang);            
        }else{        	
        	locale = deviceLocale;
        }
        Locale.setDefault(locale);
        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }
    
    public void restart(){
    	Intent i = getBaseContext().getPackageManager()
	             .getLaunchIntentForPackage( getBaseContext().getPackageName() );
	    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	    startActivity(i);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    	Configuration config = new Configuration(newConfig); 
    	if (locale != null)
        {
    		config.locale = locale;
            Locale.setDefault(locale);
            getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
        }
    }
    
    public static Context getAppContext() {
        return INaturalistApp.context;
    }
    
    public void setIsSyncing(boolean isSyncing) {
    	mIsSyncing  = isSyncing;
    }
    
    public void checkSyncNeeded() {
        Cursor oCursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, 
                "_synced_at IS NULL OR (_updated_at > _synced_at)", null, Observation.DEFAULT_SORT_ORDER);
        Cursor opCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION, 
                "_synced_at IS NULL OR (_updated_at > _synced_at)", null, ObservationPhoto.DEFAULT_SORT_ORDER);
        if (!mIsSyncing) {
            mNotificationManager.cancel(SYNC_NOTIFICATION);
        } else {
            Resources res = getResources();
            serviceNotify(SYNC_NOTIFICATION, 
                    res.getString(R.string.sync_required),
                    String.format(res.getString(R.string.sync_required_message), oCursor.getCount(), opCursor.getCount()),
                    null,
                    new Intent(INaturalistService.ACTION_SYNC, null, this, INaturalistService.class));
        }
    }

    public boolean loggedIn() {
        return getPrefs().contains("credentials");
    }
    
    public LoginType getLoginType() {
        return LoginType.valueOf(getPrefs().getString("login_type", LoginType.PASSWORD.toString()));
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
    public String shortFormatDate(Timestamp date) {
        SimpleDateFormat f;
        if (Locale.getDefault().getCountry().equals("US")) {
            f = new SimpleDateFormat("MMM d, yyyy");
        } else {
            f = SHORT_DATE_FORMAT;
        }
        return f.format(date);
    }
    public String shortFormatTime(Timestamp date) { return SHORT_TIME_FORMAT.format(date); }
}
