package org.inaturalist.android;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final int REQUEST_CODE_LOGIN = 0x1000;
    private static final int REQUEST_CODE_DELETE_ACCOUNT = 0x1001;
    private static final int REQUEST_CODE_THIRD_PARTY_DATA_SHARING = 0x1002;
    private static final int REQUEST_CODE_VERIFY_PASSWORD = 0x1003;
    private static final int REQUEST_CODE_CHANGE_NAME_PLACE = 0x1004;
    private static final int REQUEST_CODE_CHANGE_DEFAULT_LICENSES = 0x1005;

    private static final String TAG = "SettingsFragment";

    private Preference mSignInPreference;
    private Preference mSignOutPreference;
    private CheckBoxPreference mAutoSyncPreference;
    private CheckBoxPreference mSuggestSpeciesPreference;
    private CheckBoxPreference mShowScientificNameFirstPreference;
    private CheckBoxPreference mPrefersCommonNames;
    private ListPreference mLanguagePreference;
    private Preference mNetworkPreference;
    private Preference mDeleteAccount;
    private Preference mThirdPartyDataSharing;
    private Preference mNamePlacePreference;
    private Preference mDefaultLicenses;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private INaturalistApp mApp;
    private ListPreference mDefaultObsAction;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.settings_preferences);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mDefaultObsAction = (ListPreference) getPreferenceManager().findPreference("default_obs_action");
        mSignInPreference = getPreferenceManager().findPreference("sign_in");
        mSignOutPreference = getPreferenceManager().findPreference("sign_out");
        mAutoSyncPreference = (CheckBoxPreference) getPreferenceManager().findPreference("auto_sync");
        mSuggestSpeciesPreference = (CheckBoxPreference) getPreferenceManager().findPreference("suggest_species");
        mShowScientificNameFirstPreference = (CheckBoxPreference) getPreferenceManager().findPreference("prefers_scientific_name_first");
        mPrefersCommonNames = (CheckBoxPreference) getPreferenceManager().findPreference("prefers_common_names");
        mLanguagePreference = (ListPreference) getPreferenceManager().findPreference("language");
        mNetworkPreference = (Preference) getPreferenceManager().findPreference("inat_network");
        mDeleteAccount = (Preference) getPreferenceManager().findPreference("delete_account");
        mThirdPartyDataSharing = (Preference) getPreferenceManager().findPreference("third_party_data_sharing");
        mNamePlacePreference = (Preference) getPreferenceManager().findPreference("name_place");
        mDefaultLicenses = (Preference) getPreferenceManager().findPreference("default_licenses");

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
        String[] defaultObsActions = new String[] {
                getString(R.string.show_options),
                getString(R.string.camera),
                getString(R.string.upload_photo),
                getString(R.string.record_sound),
                getString(R.string.upload_sound),
                getString(R.string.no_photo),
        };
        List<String> obsActionsValues = Arrays.asList(getResources().getStringArray(R.array.default_obs_action_values));

        mDefaultObsAction.setEntries(defaultObsActions);
        String defaultObsAction = mApp.getDefaultObsAction();
        mDefaultObsAction.setValue(defaultObsAction);
        mDefaultObsAction.setSummary(defaultObsActions[obsActionsValues.indexOf(defaultObsAction)]);

        mDefaultObsAction.setOnPreferenceChangeListener((preference, newValue) -> {
            mApp.setDefaultObsAction((String)newValue);
            refreshSettings();
            return false;
        });

        String username = mPreferences.getString("username", null);

        if (username == null) {
            // Signed out
            mSignInPreference.setVisible(true);
            mSignOutPreference.setVisible(false);
            mSignInPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
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
            mSignInPreference.setVisible(false);
            mSignOutPreference.setVisible(true);

            mSignOutPreference.setSummary(Html.fromHtml(String.format(getString(R.string.currently_logged_in_as_html), username)));

            mSignOutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
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
                    Logger.tag(TAG).error(e);
                }

                return false;
            }
        });


        mAutoSyncPreference.setChecked(mApp.getAutoSync());
        //mAutoSyncPreference.setTitle(R.string.automatic_upload);
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
                    Logger.tag(TAG).error(e);
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
                        Logger.tag(TAG).error(e);
                    }
                    serviceIntent.putExtra(INaturalistService.USER, new BetterJSONObject(userDetails));
                    INaturalistService.callService(getActivity(), serviceIntent);
                }

                return false;
            }
        });

        mPrefersCommonNames.setChecked(mApp.getPrefersCommonNames());
        mPrefersCommonNames.setOnPreferenceClickListener(preference -> {
            boolean newValue = mPrefersCommonNames.isChecked();
            mPrefersCommonNames.setChecked(newValue);
            mApp.setPrefersCommonNames(newValue);

            if (mApp.loggedIn()) {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_CURRENT_USER_DETAILS, null, getActivity(), INaturalistService.class);
                JSONObject userDetails = new JSONObject();
                try {
                    userDetails.put("prefers_common_names", newValue);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
                serviceIntent.putExtra(INaturalistService.USER, new BetterJSONObject(userDetails));
                INaturalistService.callService(getActivity(), serviceIntent);
            }

            return false;
        });

        refreshLanguageSettings();
        mLanguagePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                String locale = (String)o;
                Logger.tag(TAG).info(String.format("Setting onPreferenceChange - %s", locale));
                mPrefEditor.putString("pref_locale", locale);
                mPrefEditor.commit();

                if (mApp.loggedIn() && !locale.equals("")) {
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_CURRENT_USER_DETAILS, null, getActivity(), INaturalistService.class);
                    JSONObject userDetails = new JSONObject();
                    try {
                        userDetails.put("locale", locale.replace("-r", "-"));
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                    }
                    serviceIntent.putExtra(INaturalistService.USER, new BetterJSONObject(userDetails));
                    INaturalistService.callService(getActivity(), serviceIntent);
                }

                mApp.applyLocaleSettings();
                mApp.restart();
                getActivity().finish();

                return false;
            }
        });

        mDeleteAccount.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Force user to login again (verify password)
                Intent intent = new Intent(getActivity(), LoginSignupActivity.class);
                intent.putExtra(LoginSignupActivity.VERIFY_PASSWORD, true);
                startActivityForResult(intent, REQUEST_CODE_VERIFY_PASSWORD);

                return false;
            }
        });


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

        mDeleteAccount.setVisible(mApp.currentUserLogin() != null);
        mThirdPartyDataSharing.setSummary(mApp.getPrefersNoTracking() ? R.string.disabled : R.string.enabled);

        mThirdPartyDataSharing.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), ThirdPartyDataSharingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivityForResult(intent, REQUEST_CODE_THIRD_PARTY_DATA_SHARING);
                return false;
            }
        });

        mDefaultLicenses.setVisible(mApp.currentUserLogin() != null);
        mDefaultLicenses.setSummary(mApp.getDefaultObservationLicense().shortName);
        mDefaultLicenses.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), DefaultLicensesActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityForResult(intent, REQUEST_CODE_CHANGE_DEFAULT_LICENSES);
            return false;
        });

        mNamePlacePreference.setVisible(mApp.currentUserLogin() != null);
        String placeName = mApp.getPrefs().getString("user_place_display_name", getString(R.string.global));
        mNamePlacePreference.setTitle(Html.fromHtml(String.format(getString(R.string.common_names_place), placeName)));

        mNamePlacePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), PlaceSearchActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivityForResult(intent, REQUEST_CODE_CHANGE_NAME_PLACE);
                return false;
            }
        });
    }

    private void refreshLanguageSettings() {
        String prefLocale = mPreferences.getString("pref_locale", "");
        Logger.tag(TAG).info(String.format("refreshLanguageSettings - %s", prefLocale));
        String[] supportedLocales = getResources().getStringArray(R.array.language_values);

        if (prefLocale.equals("")) {
            // Use device locale
            mLanguagePreference.setSummary(R.string.use_device_language_settings);
            mLanguagePreference.setValueIndex(0);
        } else {
            // TODO - offline case
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
        RecyclerView recyclerView = getListView();
        recyclerView.addItemDecoration(new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }





    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // Refresh login state
            refreshSettings();
        } else if ((requestCode == REQUEST_CODE_VERIFY_PASSWORD) && (resultCode == Activity.RESULT_OK)) {
            // User verified password - Open deletion screen
            Intent intent = new Intent(getActivity(), DeleteAccount.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityForResult(intent, REQUEST_CODE_DELETE_ACCOUNT);
        } else if ((requestCode == REQUEST_CODE_DELETE_ACCOUNT) && (resultCode == Activity.RESULT_OK)) {
            // User deleted account - sign out immediately
            signOut();
        } else if (requestCode == REQUEST_CODE_THIRD_PARTY_DATA_SHARING) {
            // Refresh third party data sharing setting
            refreshSettings();
        } else if (requestCode == REQUEST_CODE_CHANGE_DEFAULT_LICENSES) {
            // Refresh default licenses setting
            refreshSettings();
        } else if (requestCode == REQUEST_CODE_CHANGE_NAME_PLACE) {
            if (resultCode == Activity.RESULT_OK) {
                // Update common name place
                int placeId = data.getIntExtra(PlaceSearchActivity.PLACE_ID, -1);
                String placeDisplayName = data.getStringExtra(PlaceSearchActivity.PLACE_DISPLAY_NAME);

                SharedPreferences.Editor editor = mApp.getPrefs().edit();
                editor.putString("user_place_display_name", placeDisplayName);
                editor.putInt("user_place_id", placeId);
                editor.apply();

                // Update remotely
                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_CURRENT_USER_DETAILS, null, getActivity(), INaturalistService.class);
                JSONObject userDetails = new JSONObject();
                try {
                    userDetails.put("place_id", placeId == -1 ? "" : placeId);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
                serviceIntent.putExtra(INaturalistService.USER, new BetterJSONObject(userDetails));
                INaturalistService.callService(getActivity(), serviceIntent);

                refreshSettings();
            }
        }
    }


    private void signOut() {
        BaseFragmentActivity.signOut(getActivity());

        refreshSettings();
        ((SettingsActivity)getActivity()).refreshUserDetails();

        mDeleteAccount.setVisible(false);
    }
}
