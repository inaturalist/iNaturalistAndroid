package org.inaturalist.android;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.StrictMode;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.DatePicker;
import android.widget.Toast;

import org.tinylog.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DebugSettingsFragment extends PreferenceFragmentCompat implements DatePickerDialog.OnDateSetListener {
    private static final String TAG = "DebugSettingsFragment";
    private Preference mClearLogs;
    private Preference mSendLogs;
    private SeekBarPreference mDayCount;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private INaturalistApp mApp;

    DatePickerDialog mDatePickerDialog;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.debug_settings_preferences);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mClearLogs = getPreferenceManager().findPreference("clear_logs");
        mSendLogs = getPreferenceManager().findPreference("send_logs");
        mDayCount = (SeekBarPreference) getPreferenceManager().findPreference("day_count");

        mHelper = new ActivityHelper(getActivity());
        mPreferences = getActivity().getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();

        refreshSettings();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshSettings();

        Calendar cal = Calendar.getInstance();

        mDatePickerDialog = new DatePickerDialog(this.getContext(), this,
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
    }


    private void refreshSettings() {
        mClearLogs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mHelper.confirm(getString(R.string.clear_all_debug_logs), getString(R.string.are_you_sure_you_want_to_delete_logs),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // Clear all debug logs
                                LoggingUtils.clearAllLogs(getContext());
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        }, getString(R.string.yes), getString(R.string.no));

                return true;
            }
        });

        mDayCount.setValue(mApp.getDebugLogDayCount());
        mDayCount.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mApp.setDebugLogDayCount((Integer)newValue);
                return true;
            }
        });


        mSendLogs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mDatePickerDialog.show();
                return false;
            }
        });
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add the dividers between the preference items
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        recyclerView.addItemDecoration(
                new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        List<File> debugFiles = LoggingUtils.getAllDebugLogs(getContext(), cal.getTime(), null, true);

        if (debugFiles.size() == 0) {
            Toast.makeText(getContext(), getString(R.string.no_log_files_found), Toast.LENGTH_LONG).show();
            return;
        }

        sendDebugLogs(debugFiles);
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

    public void sendDebugLogs(List<File> debugFiles) {
        String username = mPreferences.getString("username", null);
        PackageInfo info = null;

        try {
            PackageManager manager = getActivity().getPackageManager();
            info = manager.getPackageInfo(getActivity().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.tag(TAG).error(e);
            info = null;
        }

        // Send the file using email
        Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE, Uri.fromParts("mailto", getString(R.string.inat_support_email_address), null));
        emailIntent.setType("vnd.android.cursor.dir/email");

        List<ResolveInfo> resInfoList = mApp.getPackageManager().queryIntentActivities(emailIntent, PackageManager.MATCH_DEFAULT_ONLY);
        List<String> emailPackageNames = new ArrayList<>();
        for (ResolveInfo resolveInfo : resInfoList) {
            emailPackageNames.add(resolveInfo.activityInfo.packageName);
        }

        // Add the attachments
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (File file : debugFiles) {
            Uri u = FileProvider.getUriForFile(mApp, BuildConfig.APPLICATION_ID + ".fileProvider", file);
            uris.add(u);

            for (String packageName : emailPackageNames) {
                mApp.grantUriPermission(packageName, u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uris);

        if (info == null) {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format(Locale.ENGLISH, "iNaturalist Android Logs (user id - %s; Android API = %d)", username == null ? "N/A" : username, Build.VERSION.SDK_INT));
        } else {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format(Locale.ENGLISH, "iNaturalist Android Logs (version %s - %s; user id - %s; Android API = %d)", info.versionName, info.versionCode, username == null ? "N/A" : username, Build.VERSION.SDK_INT));
        }
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});

        Intent queryIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        Intent targetIntent = getSelectiveIntentChooser(mApp, queryIntent, emailIntent);
        startActivity(Intent.createChooser(targetIntent , getString(R.string.send_email)));
    }

    private Intent getSelectiveIntentChooser(Context context, Intent queryIntent, Intent dataIntent) {
        List<ResolveInfo> appList = context.getPackageManager().queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY);

        Intent finalIntent = null;

        if (!appList.isEmpty()) {
            List<android.content.Intent> targetedIntents = new ArrayList<android.content.Intent>();

            for (ResolveInfo resolveInfo : appList) {
                String packageName = resolveInfo.activityInfo != null ? resolveInfo.activityInfo.packageName : null;

                Intent allowedIntent = new Intent(dataIntent);
                allowedIntent.setComponent(new ComponentName(packageName, resolveInfo.activityInfo.name));
                allowedIntent.setPackage(packageName);

                targetedIntents.add(allowedIntent);
            }

            if (!targetedIntents.isEmpty()) {
                // Share Intent
                Intent startIntent = targetedIntents.remove(0);

                Intent chooserIntent = android.content.Intent.createChooser(startIntent, "");
                chooserIntent.putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, targetedIntents.toArray(new Parcelable[]{}));
                chooserIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

                finalIntent = chooserIntent;
            }

        }

        if (finalIntent == null) { // As a fallback, we are using the sent data intent
            finalIntent = dataIntent;
        }

        return finalIntent;
    }

}
