package org.inaturalist.android;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.SeekBarPreference;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.DatePicker;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DebugSettingsFragment extends PreferenceFragmentCompat implements DatePickerDialog.OnDateSetListener {
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
                mHelper.confirm("Clear All Debug Logs", "Are you sure you want to delete all debug logs? Only press 'Yes' if you want clear up storage room for your phone",
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
                        }, "Yes", "No");

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
        RecyclerView recyclerView = (RecyclerView) getView().findViewById(R.id.list);
        recyclerView.addItemDecoration(
                new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        List<File> debugFiles = LoggingUtils.getAllDebugLogs(getContext(), cal.getTime(), null);

        if (debugFiles.size() == 0) {
            Toast.makeText(getContext(), "No log files found for that date range - try earlier date?", Toast.LENGTH_LONG).show();
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
            e.printStackTrace();
            info = null;
        }

        // Send the file using email
        Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("vnd.android.cursor.dir/email");

        // Add the attachments
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (File file : debugFiles) {
            Uri u = Uri.fromFile(file);
            uris.add(u);
        }
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        if (info == null) {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("iNaturalist Android Logs (user id - %s; Android API = %d)", username == null ? "N/A" : username, Build.VERSION.SDK_INT));
        } else {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("iNaturalist Android Logs (version %s - %s; user id - %s; Android API = %d)", info.versionName, info.versionCode, username == null ? "N/A" : username, Build.VERSION.SDK_INT));
        }
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});

        startActivity(Intent.createChooser(emailIntent , getString(R.string.send_email)));
    }

}
