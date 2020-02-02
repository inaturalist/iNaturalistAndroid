package org.inaturalist.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

public class ThirdPartyDataSharingFragment extends PreferenceFragmentCompat {
    private static final String TAG = "ThirdPartyDataSharingFragment";
    private CheckBoxPreference mDisableThirdPartyDataSharing;

    private INaturalistApp mApp;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.third_party_data_sharing_preferences);

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mDisableThirdPartyDataSharing = (CheckBoxPreference) getPreferenceManager().findPreference("disable_data_sharing");

        refreshSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshSettings();
    }


    private void refreshSettings() {
        mDisableThirdPartyDataSharing.setChecked(mApp.getPrefersNoTracking());
        mDisableThirdPartyDataSharing.setOnPreferenceClickListener(preference -> {
            boolean newValue = mDisableThirdPartyDataSharing.isChecked();
            mApp.setPrefersNoTracking(newValue);

            if (mApp.currentUserLogin() != null) {
                // Update setting remotely
                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_CURRENT_USER_DETAILS, null, getActivity(), INaturalistService.class);
                JSONObject userDetails = new JSONObject();
                try {
                    userDetails.put("prefers_no_tracking", newValue);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
                serviceIntent.putExtra(INaturalistService.USER, new BetterJSONObject(userDetails));
                ContextCompat.startForegroundService(getActivity(), serviceIntent);
            }

            return false;
        });
    }
}
