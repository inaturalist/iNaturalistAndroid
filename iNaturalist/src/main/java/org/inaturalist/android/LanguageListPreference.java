package org.inaturalist.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckedTextView;
import androidx.preference.ListPreference;
import java.util.Arrays;

/** Custom list preference for language selection - will disable all entries but the first one
 * in case of no Internet */
public class LanguageListPreference extends ListPreference {
    private final INaturalistApp mApp;
    private Context mContext;

    public LanguageListPreference (Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
    }

    @Override
    public void onClick() {
        final CharSequence[] LANGUAGE_NAMES = mContext.getResources().getStringArray(R.array.language_names);
        final CharSequence[] LANGUAGE_VALUES = mContext.getResources().getStringArray(R.array.language_values);
        final CharSequence[] noInternetLanguageNames = new String[] {
                mContext.getString(R.string.use_device_language_settings),
                mContext.getString(R.string.choosing_specific_language_warning)
        };


        boolean noInternet = !mApp.isNetworkAvailable();

        final CharSequence[] languages = noInternet ? noInternetLanguageNames : LANGUAGE_NAMES;

        int checkItem = Arrays.asList(LANGUAGE_VALUES).indexOf(getValue());

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.language);

        builder.setSingleChoiceItems(
                languages,
                checkItem,
                (dialog, item) -> {
                    if (noInternet && item == 1) {
                        // Disabled item
                        ((AlertDialog) dialog).getListView().setItemChecked(item, false);
                        return;
                    }

                    dialog.dismiss();
                    LanguageListPreference.this.callChangeListener(LANGUAGE_VALUES[item]);
                    setSummary(languages[item]);
                }
        );

        AlertDialog dialog = builder.create();

        // Disable second choice if no Internet ("Choosing a specific language for your account requires an Internet connection")
        dialog.getListView().setOnHierarchyChangeListener(
                new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        CharSequence text = ((AppCompatCheckedTextView)child).getText();
                        int itemIndex = Arrays.asList(languages).indexOf(text);
                        if (noInternet && itemIndex == 1) {
                            child.setEnabled(false);
                        }
                    }

                    @Override
                    public void onChildViewRemoved(View view, View view1) {
                    }
                });
        dialog.show();
    }
}
