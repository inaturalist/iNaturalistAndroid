package org.inaturalist.android;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
import android.view.View;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import static org.inaturalist.android.DefaultLicensesActivity.REQUEST_CODE_PAST_LICENSES;

public class DefaultLicensesFragment extends PreferenceFragmentCompat {
    private static final String TAG = "DefaultLicensesFragment";

    private Preference mDefaultObservationLicense;
    private Preference mDefaultPhotoLicense;
    private Preference mDefaultSoundLicense;

    private INaturalistApp mApp;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.default_licenses);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mDefaultObservationLicense = getPreferenceManager().findPreference("default_observation_license");
        mDefaultPhotoLicense = getPreferenceManager().findPreference("default_photo_license");
        mDefaultSoundLicense = getPreferenceManager().findPreference("default_sound_license");

        refreshSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshSettings();
    }



    private void refreshSettings() {
        mDefaultObservationLicense.setSummary(mApp.getDefaultObservationLicense().shortName);
        mDefaultPhotoLicense.setSummary(mApp.getDefaultPhotoLicense().shortName);
        mDefaultSoundLicense.setSummary(mApp.getDefaultSoundLicense().shortName);

        mDefaultObservationLicense.setOnPreferenceClickListener(preference -> {
            LicenseUtils.showLicenseChooser(getActivity(), R.string.default_observation_license, mApp.getDefaultObservationLicense().value, license -> {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, getActivity(), INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.ACTION_USER_LICENSE, license.value);
                ContextCompat.startForegroundService(getActivity(), serviceIntent);
                mApp.setDefaultObservationLicense(license.value);

                refreshSettings();
            });
            return false;
        });

        mDefaultPhotoLicense.setOnPreferenceClickListener(preference -> {
            LicenseUtils.showLicenseChooser(getActivity(), R.string.default_photo_license, mApp.getDefaultPhotoLicense().value, license -> {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, getActivity(), INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.ACTION_USER_PHOTO_LICENSE, license.value);
                ContextCompat.startForegroundService(getActivity(), serviceIntent);
                mApp.setDefaultPhotoLicense(license.value);

                refreshSettings();
            });
            return false;
        });

        mDefaultSoundLicense.setOnPreferenceClickListener(preference -> {
            LicenseUtils.showLicenseChooser(getActivity(), R.string.default_sound_license, mApp.getDefaultSoundLicense().value, license -> {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, getActivity(), INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.ACTION_USER_SOUND_LICENSE, license.value);
                ContextCompat.startForegroundService(getActivity(), serviceIntent);
                mApp.setDefaultSoundLicense(license.value);

                refreshSettings();
            });
            return false;
        });
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add the dividers between the preference items
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        recyclerView.addItemDecoration(new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PAST_LICENSES) {
            // Refresh default license state
            refreshSettings();
        }
    }
}
