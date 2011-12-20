package org.inaturalist.android;

import android.app.Application;
import android.content.SharedPreferences;

public class INaturalistApp extends Application {
    private SharedPreferences mPrefs;
    
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
}
