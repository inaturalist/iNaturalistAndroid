package org.inaturalist.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.tinylog.Logger;

public class AboutFragment extends PreferenceFragmentCompat {
    private static final String DONATION_URL = "http://www.inaturalist.org/donate?utm_source=Android&utm_medium=mobile";
    private static final String SHOP_URL = "https://store.inaturalist.org/?utm_source=android&utm_medium=mobile&utm_campaign=store";
    private static final String TOS_URL = "https://www.inaturalist.org/terms";
    private static final String PRIVACY_POLICY_URL = "https://www.inaturalist.org/privacy";
    private static final String TAG = "AboutFragment";

    private Preference mContactSupport;
    private Preference mVersion;
    private Preference mAbout;
    private Preference mDonate;
    private Preference mShop;
    private Preference mTOS;
    private Preference mPrivacyPolicy;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private INaturalistApp mApp;

    private int mDebugLogsClickCount = 0;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.about_preferences);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mContactSupport = (Preference) getPreferenceManager().findPreference("contact_support");
        mAbout = (Preference) getPreferenceManager().findPreference("about");
        mDonate = (Preference) getPreferenceManager().findPreference("donate");
        mShop = (Preference) getPreferenceManager().findPreference("shop");
        mTOS = (Preference) getPreferenceManager().findPreference("terms_of_service");
        mPrivacyPolicy = (Preference) getPreferenceManager().findPreference("privacy_policy");
        mVersion = (Preference) getPreferenceManager().findPreference("version");

        mHelper = new ActivityHelper(getActivity());
        mPreferences = getActivity().getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();

        refreshSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ((INaturalistApp) getActivity().getApplicationContext()).applyLocaleSettings(getActivity());
        super.onCreate(savedInstanceState);
        refreshSettings();
    }



    private void refreshSettings() {
        mVersion.setOnPreferenceClickListener(preference -> {
            mDebugLogsClickCount++;

            if (mDebugLogsClickCount >= 3) {
                // Open secret debug menu
                mDebugLogsClickCount = 0;
                Intent intent = new Intent(getActivity(), DebugSettingsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            } else if (mDebugLogsClickCount == 1) {
                FileUtils.copyToClipBoard(getActivity(), getAppVersion());
                Toast.makeText(getActivity(), getString(R.string.app_version_copied_to_clipboard), Toast.LENGTH_LONG).show();
            }

            return false;
        });

        mContactSupport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Get app version
                try {
                    PackageManager manager = getActivity().getPackageManager();
                    PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);

                    // Open the email client
                    Intent mailer = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.inat_support_email_address), null));
                    mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});
                    String username = mPreferences.getString("username", null);
                    String subject = String.format(getString(R.string.inat_support_email_subject), info.versionName, info.versionCode, username == null ? "N/A" : username);
                    mailer.putExtra(Intent.EXTRA_SUBJECT, subject);
                    mailer.setData(Uri.parse("mailto:" + getString(R.string.inat_support_email_address) + "?subject=" + subject));
                    startActivity(Intent.createChooser(mailer, getString(R.string.send_email)));
                } catch (PackageManager.NameNotFoundException e) {
                    Logger.tag(TAG).error(e);
                }
                return false;
            }
        });

        mAbout.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), CreditsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return false;
        });

        mDonate.setOnPreferenceClickListener(preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL));
            startActivity(browserIntent);
            return false;
        });

        mShop.setOnPreferenceClickListener(preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SHOP_URL));
            startActivity(browserIntent);
            return false;
        });

        mPrivacyPolicy.setOnPreferenceClickListener(preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL));
            startActivity(browserIntent);
            return false;
        });

        mTOS.setOnPreferenceClickListener(preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(TOS_URL));
            startActivity(browserIntent);
            return false;
        });

        // Show app version
        mVersion.setSummary(getAppVersion());
    }

    private String getAppVersion() {
        try {
            PackageManager manager = getActivity().getPackageManager();
            PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);

            return String.format("%s (%d)", info.versionName, info.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.tag(TAG).error(e);
            return "";
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add the dividers between the preference items
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        recyclerView.addItemDecoration(new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }
}
