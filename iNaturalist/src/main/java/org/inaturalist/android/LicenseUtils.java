package org.inaturalist.android;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.StringRes;

import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utility methods related to observation/photo/sound license handling */
public class LicenseUtils {

    private static final String TAG = "LicenseUtils";

    /** Returns all possible license types (loaded from license_types.xml) */
    public static List<License> getAllLicenseTypes(INaturalistApp app) {
        List<License> licenseTypes = new ArrayList<>();

        Resources res = app.getResources();
        String[] licenseTypesIds = res.getStringArray(R.array.license_types);

        for (int i = 0; i < licenseTypesIds.length; i++) {
            License license = new License();

            license.id = licenseTypesIds[i];
            license.value = app.getStringResourceByName("license_value_" + license.id);
            license.shortName = app.getStringResourceByName("license_short_name_" + license.id);
            license.name = app.getStringResourceByName("license_name_" + license.id);
            license.gbifCompatible = app.getStringResourceByName("license_gbif_" + license.id).equals("1");
            license.wikimediaCompatible = app.getStringResourceByName("license_wikimedia_" + license.id).equals("1");
            int logoResource = app.getDrawableResourceByName("license_logo_" + license.id);
            license.logoResource = logoResource != 0 ? logoResource : null;
            String url = app.getStringResourceByName("license_url_" + license.id, "");
            license.url = url.equals("") ? null : url;
            license.description = app.getStringResourceByName("license_description_" + license.id);

            licenseTypes.add(license);
        }

        return licenseTypes;
    }

    /** Returns a license by its value (case insenstive) */
    public static License getLicenseByValue(INaturalistApp app, String value) {
        List<License> licenses = getAllLicenseTypes(app);

        for (License license : licenses) {
            if (license.value.equalsIgnoreCase(value)) {
                return license;
            }
        }

        return null;
    }

    public interface OnLicenseChosen {
        /** License type was chosen */
        void onLicenseChosen(License license);
    }

    /** Shows the license chooser dialog */
    public static void showLicenseChooser(Context context, @StringRes int title, String selectedValue, OnLicenseChosen cb) {
        ActivityHelper helper = new ActivityHelper(context);
        INaturalistApp app = (INaturalistApp) context.getApplicationContext();
        List<License> licenses = getAllLicenseTypes(app);
        LicenseListAdapter adapter = new LicenseListAdapter(context, licenses.toArray(new License[0]), selectedValue);

        Drawable icon = context.getDrawable(R.drawable.baseline_help_outline_black_24);
        icon.mutate().setColorFilter(Color.parseColor("#5d5e5f"), PorterDuff.Mode.SRC_IN);
        helper.selection(context.getString(title), adapter, (dialog, position) -> cb.onLicenseChosen(licenses.get(position)), icon, v -> {
            // Show about licenses screen
            Intent intent = new Intent(context, AboutLicensesActivity.class);
            context.startActivity(intent);
        });
    }
}
