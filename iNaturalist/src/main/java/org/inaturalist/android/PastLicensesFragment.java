package org.inaturalist.android;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.tinylog.Logger;

public class PastLicensesFragment extends PreferenceFragmentCompat {
    private static final String TAG = "PastLicensesFragment";

    private Preference mChangePastLicensesForObservations;
    private Preference mChangePastLicensesForPhotos;
    private Preference mChangePastLicensesForSounds;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private INaturalistApp mApp;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.past_licenses);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mChangePastLicensesForObservations = getPreferenceManager().findPreference("change_licenses_for_all_existing_observations");
        mChangePastLicensesForObservations.setSingleLineTitle(false);
        mChangePastLicensesForPhotos = getPreferenceManager().findPreference("change_licenses_for_all_existing_photos");
        mChangePastLicensesForPhotos.setSingleLineTitle(false);
        mChangePastLicensesForSounds = getPreferenceManager().findPreference("change_licenses_for_all_existing_sounds");
        mChangePastLicensesForSounds.setSingleLineTitle(false);

        mChangePastLicensesForObservations.setOnPreferenceClickListener(preference -> {
            LicenseUtils.showLicenseChooser(getActivity(), R.string.observation_license, null, license -> {
                Integer obsCount = mPreferences.getInt("observation_count", 0);
                String message = String.format(getString(R.string.change_licenses_for_observations_confirmation), obsCount, license.shortName);

                mHelper.confirm(getString(R.string.change_observation_licenses), message, (dialog, which) -> {
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, getActivity(), INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.ACTION_USER_LICENSE, license.value);
                    serviceIntent.putExtra(INaturalistService.ACTION_MAKE_LICENSE_SAME, true);
                    ContextCompat.startForegroundService(getActivity(), serviceIntent);
                    mApp.setDefaultObservationLicense(license.value);

                    String confirmation = String.format(getString(R.string.existing_observation_licenses_have_been_updated), license.shortName);
                    mHelper.alert(getString(R.string.observation_licenses_updated), confirmation);
                }, (dialog, which) -> { }, R.string.yes, R.string.cancel);
            });
            return false;
        });

        mChangePastLicensesForPhotos.setOnPreferenceClickListener(preference -> {
            LicenseUtils.showLicenseChooser(getActivity(), R.string.photo_license, null, license -> {
                String message = String.format(getString(R.string.change_licenses_for_photos_confirmation), license.shortName);

                mHelper.confirm(getString(R.string.change_photo_licenses), message, (dialog, which) -> {
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, getActivity(), INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.ACTION_USER_PHOTO_LICENSE, license.value);
                    serviceIntent.putExtra(INaturalistService.ACTION_MAKE_PHOTO_LICENSE_SAME, true);
                    ContextCompat.startForegroundService(getActivity(), serviceIntent);
                    mApp.setDefaultPhotoLicense(license.value);

                    String confirmation = String.format(getString(R.string.existing_photo_licenses_have_been_updated), license.shortName);
                    mHelper.alert(getString(R.string.photo_licenses_updated), confirmation);
                }, (dialog, which) -> { }, R.string.yes, R.string.cancel);
            });
            return false;
        });

        mChangePastLicensesForSounds.setOnPreferenceClickListener(preference -> {
            LicenseUtils.showLicenseChooser(getActivity(), R.string.sound_license, null, license -> {
                String message = String.format(getString(R.string.change_licenses_for_sounds_confirmation), license.shortName);

                mHelper.confirm(getString(R.string.change_sound_licenses), message, (dialog, which) -> {
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, getActivity(), INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.ACTION_USER_SOUND_LICENSE, license.value);
                    serviceIntent.putExtra(INaturalistService.ACTION_MAKE_SOUND_LICENSE_SAME, true);
                    ContextCompat.startForegroundService(getActivity(), serviceIntent);
                    mApp.setDefaultSoundLicense(license.value);

                    String confirmation = String.format(getString(R.string.existing_sound_licenses_have_been_updated), license.shortName);
                    mHelper.alert(getString(R.string.sound_licenses_updated), confirmation);
                }, (dialog, which) -> { }, R.string.yes, R.string.cancel);
            });
            return false;
        });

        mHelper = new ActivityHelper(getActivity());
        mPreferences = getActivity().getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add the dividers between the preference items
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        recyclerView.addItemDecoration(new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }

}
