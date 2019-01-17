package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.login.LoginManager;

import org.apache.http.util.LangUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final int REQUEST_CODE_LOGIN = 0x1000;
    private static final String DONATION_URL = "http://www.inaturalist.org/donate?utm_source=Android&utm_medium=mobile";

    private Preference mUsernamePreference;
    private CheckBoxPreference mAutoSyncPreference;
    private CheckBoxPreference mSuggestSpeciesPreference;
    private CheckBoxPreference mShowScientificNameFirstPreference;
    private ListPreference mLanguagePreference;
    private Preference mNetworkPreference;
    private Preference mContactSupport;
    private Preference mVersion;
    private Preference mAbout;
    private Preference mDonate;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private INaturalistApp mApp;

    private int mDebugLogsClickCount = 0;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.settings_preferences);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mUsernamePreference = getPreferenceManager().findPreference("username");
        mAutoSyncPreference = (CheckBoxPreference) getPreferenceManager().findPreference("auto_sync");
        mSuggestSpeciesPreference = (CheckBoxPreference) getPreferenceManager().findPreference("suggest_species");
        mShowScientificNameFirstPreference = (CheckBoxPreference) getPreferenceManager().findPreference("prefers_scientific_name_first");
        mLanguagePreference = (ListPreference) getPreferenceManager().findPreference("language");
        mNetworkPreference = (Preference) getPreferenceManager().findPreference("inat_network");
        mContactSupport = (Preference) getPreferenceManager().findPreference("contact_support");
        mAbout = (Preference) getPreferenceManager().findPreference("about");
        mDonate = (Preference) getPreferenceManager().findPreference("donate");
        mVersion = (Preference) getPreferenceManager().findPreference("version");

        mHelper = new ActivityHelper(getActivity());
        mPreferences = getActivity().getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();

        refreshSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshSettings();
    }



    private void refreshSettings() {
        String username = mPreferences.getString("username", null);

        if (username == null) {
            // Signed out
            mUsernamePreference.setTitle(R.string.not_logged_in);
            mUsernamePreference.setSummary(R.string.log_in);
            mUsernamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity(), OnboardingActivity.class);
                    intent.putExtra(OnboardingActivity.LOGIN, true);

                    startActivityForResult(intent, REQUEST_CODE_LOGIN);
                    return false;
                }
            });

        } else {
            // Signed in
            mUsernamePreference.setTitle(Html.fromHtml(String.format(getString(R.string.logged_in_as_html), username)));
            mUsernamePreference.setSummary(R.string.log_out);

            mUsernamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mHelper.confirm(getString(R.string.signed_out),
                            getString(R.string.alert_sign_out),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    signOut();
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                }
                            }
                    );
                    return false;
                }
            });

        }

        mSuggestSpeciesPreference.setChecked(mApp.getSuggestSpecies());
        mSuggestSpeciesPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                boolean newValue = mSuggestSpeciesPreference.isChecked();
                mSuggestSpeciesPreference.setChecked(newValue);
                mApp.setSuggestSpecies(newValue);

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_SETTING, AnalyticsClient.EVENT_PARAM_VALUE_SUGGEST_SPECIES);

                    AnalyticsClient.getInstance().logEvent(
                            newValue ?
                                    AnalyticsClient.EVENT_NAME_SETTING_ENABLED :
                                    AnalyticsClient.EVENT_NAME_SETTING_DISABLED
                            , eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                return false;
            }
        });


        mAutoSyncPreference.setChecked(mApp.getAutoSync());
        mAutoSyncPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                boolean newValue = mAutoSyncPreference.isChecked();
                mAutoSyncPreference.setChecked(newValue);
                mApp.setAutoSync(newValue);

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_SETTING, AnalyticsClient.EVENT_PARAM_VALUE_AUTO_UPLOAD);

                    AnalyticsClient.getInstance().logEvent(
                            newValue ?
                                    AnalyticsClient.EVENT_NAME_SETTING_ENABLED :
                                    AnalyticsClient.EVENT_NAME_SETTING_DISABLED
                            , eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }


                return false;
            }
        });

        mShowScientificNameFirstPreference.setChecked(mApp.getShowScientificNameFirst());
        mShowScientificNameFirstPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                boolean newValue = mShowScientificNameFirstPreference.isChecked();
                mShowScientificNameFirstPreference.setChecked(newValue);
                mApp.setShowScientificNameFirst(newValue);

                if (mApp.loggedIn()) {
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_CURRENT_USER_DETAILS, null, getActivity(), INaturalistService.class);
                    JSONObject userDetails = new JSONObject();
                    try {
                        userDetails.put("prefers_scientific_name_first", newValue);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    serviceIntent.putExtra(INaturalistService.USER, new BetterJSONObject(userDetails));
                    ContextCompat.startForegroundService(getActivity(), serviceIntent);
                }

                return false;
            }
        });

        refreshLanguageSettings();
        mLanguagePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                int index = mLanguagePreference.findIndexOfValue((String)o);
                String locale = LocaleHelper.SupportedLocales[index];
                mPrefEditor.putString("pref_locale", locale);
                mPrefEditor.commit();
                mApp.applyLocaleSettings();
                mApp.restart();
                getActivity().finish();
                return false;
            }
        });


        mVersion.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mDebugLogsClickCount++;

                if (mDebugLogsClickCount >= 3) {
                    // Secret menu - Open up the email client with the app debug log as attachment
                    sendDebugLog();
                    mDebugLogsClickCount = 0;
                    return false;
                }

                return false;
            }
        });

        mContactSupport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Get app version
                try {
                    PackageManager manager = getActivity().getPackageManager();
                    PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);

                    // Open the email client
                    Intent mailer = new Intent(Intent.ACTION_SEND);
                    mailer.setType("message/rfc822");
                    mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});
                    String username = mPreferences.getString("username", null);
                    mailer.putExtra(Intent.EXTRA_SUBJECT, String.format(getString(R.string.inat_support_email_subject), info.versionName, info.versionCode, username == null ? "N/A" : username));
                    startActivity(Intent.createChooser(mailer, getString(R.string.send_email)));
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                return false;
            }
        });

        mAbout.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), About.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return false;
            }
        });

        mDonate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Open donation page
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL));
                startActivity(browserIntent);
                return false;
            }
        });


        // Show app version
        try {
            PackageManager manager = getActivity().getPackageManager();
            PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);

            mVersion.setSummary(String.format("%s (%d)", info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String network = mApp.getInaturalistNetworkMember();
        mNetworkPreference.setSummary(mApp.getStringResourceByName("network_" + network));
        mNetworkPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), NetworkSettings.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return false;
            }
        });
    }

    private void refreshLanguageSettings() {
        String prefLocale = mPreferences.getString("pref_locale", "");
        String[] supportedLocales = LocaleHelper.SupportedLocales;

        if (prefLocale.equals("")) {
            // Use device locale
            mLanguagePreference.setSummary(R.string.use_device_language_settings);
            mLanguagePreference.setValueIndex(0);
        } else {
            for (int i = 0; i < supportedLocales.length; i++) {
                if (prefLocale.equalsIgnoreCase(supportedLocales[i])) {
                    mLanguagePreference.setSummary(getResources().getStringArray(R.array.language_names)[i]);
                    mLanguagePreference.setValueIndex(i);
                    return;
                }
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add the dividers between the preference items
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = (RecyclerView) getView().findViewById(R.id.list);
        recyclerView.addItemDecoration(
                new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }


    private class DividerItemDecorationPreferences extends RecyclerView.ItemDecoration {

        private Drawable mDivider;
        private int paddingLeft = 0;
        private int paddingRight = 0;

        public DividerItemDecorationPreferences(Context context, int paddingLeft, int paddingRight) {
            mDivider = ContextCompat.getDrawable(context, R.drawable.divider_recycler_view);
            this.paddingLeft = paddingLeft;
            this.paddingRight = paddingRight;
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            int left = paddingLeft;
            int right = parent.getWidth() - paddingRight;
            int childCount = parent.getChildCount();
            boolean lastIteration = false;
            for (int i = 0; i < childCount; i++) {
                if (i == childCount - 1)
                    lastIteration = true;
                View child = parent.getChildAt(i);
                if (!lastIteration) {
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                    int top = child.getBottom() + params.bottomMargin;
                    int bottom = top + mDivider.getIntrinsicHeight();
                    mDivider.setBounds(left, top, right, bottom);
                    mDivider.draw(c);
                }
            }
        }

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // Refresh login state
            refreshSettings();
        }
    }


    private void signOut() {

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_LOGOUT);

        INaturalistService.LoginType loginType = INaturalistService.LoginType.valueOf(mPreferences.getString("login_type", INaturalistService.LoginType.OAUTH_PASSWORD.toString()));

        if (loginType == INaturalistService.LoginType.FACEBOOK) {
            LoginManager.getInstance().logOut();
        }

		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.remove("login_type");
        mPrefEditor.remove("last_sync_time");
		mPrefEditor.remove("observation_count");
		mPrefEditor.remove("user_icon_url");
		mPrefEditor.remove("user_bio");
        mPrefEditor.remove("user_email");
		mPrefEditor.remove("user_full_name");
		mPrefEditor.remove("last_user_details_refresh_time");
        mPrefEditor.remove("jwt_token");
        mPrefEditor.remove("jwt_token_expiration");
        mPrefEditor.remove("pref_observation_errors");
        mPrefEditor.remove("unread_activities");
        mPrefEditor.remove("prefers_scientific_name_first");
		mPrefEditor.commit();


        // Delete all locally-cached photo files
        Cursor c = mApp.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                ObservationPhoto.PROJECTION,
                null, null, ObservationPhoto.DEFAULT_SORT_ORDER);

        while (!c.isAfterLast()) {
            ObservationPhoto op = new ObservationPhoto(c);
            String photoFilename = op.photo_filename;

            if (photoFilename != null) {
                File photoFile = new File(photoFilename);
                if (photoFile.exists()) {
                    photoFile.delete();
                }
            }
            c.moveToNext();
        }
        c.close();

		int count1 = getActivity().getContentResolver().delete(Observation.CONTENT_URI, null, null);
		int count2 = getActivity().getContentResolver().delete(ObservationPhoto.CONTENT_URI, null, null);
        int count3 = getActivity().getContentResolver().delete(ProjectObservation.CONTENT_URI, null, null);
        int count4 = getActivity().getContentResolver().delete(ProjectFieldValue.CONTENT_URI, null, null);

        File obsPhotoCache = new File(getActivity().getFilesDir(), "observations_photo_info.dat");
        obsPhotoCache.delete();

        refreshSettings();
        ((SettingsActivity)getActivity()).refreshUserDetails();
	}


    public void sendDebugLog() {
        // Save Logcat output to a file
        File outputFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "logcat.txt");
        try {
            Runtime.getRuntime().exec("logcat -f " + outputFile.getAbsolutePath() + " -r 8136");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String username = mPreferences.getString("username", null);
        PackageInfo info = null;

        try {
            PackageManager manager = getActivity().getPackageManager();
            info = manager.getPackageInfo(getActivity().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        // Send the file using email
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("vnd.android.cursor.dir/email");
        // Add the attachment
        Uri path = Uri.fromFile(outputFile);
        emailIntent .putExtra(Intent.EXTRA_STREAM, path);
        if (info == null) {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("iNaturalist Android Logs (user id - %s; Android API = %d)", username == null ? "N/A" : username, Build.VERSION.SDK_INT));
        } else {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("iNaturalist Android Logs (version %s - %s; user id - %s; Android API = %d)", info.versionName, info.versionCode, username == null ? "N/A" : username, Build.VERSION.SDK_INT));
        }
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});

        startActivity(Intent.createChooser(emailIntent , getString(R.string.send_email)));
    }

}
