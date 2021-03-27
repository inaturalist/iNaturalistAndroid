package org.inaturalist.android;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatRadioButton;

public class LicenseListAdapter extends ArrayAdapter<License> {
    private License[] mLicenses;
    private Context mContext;
    private String mSelectedValue;

    public LicenseListAdapter(Context context, License[] licenses, String selectedValue) {
        super(context, R.layout.license_chooser_list_item, licenses);

        mLicenses = licenses;
        mContext = context;
        mSelectedValue = selectedValue;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = convertView == null ? inflater.inflate(R.layout.license_chooser_list_item, parent, false) : convertView;
        License license = mLicenses[position];

        final AppCompatRadioButton radioButton = view.findViewById(R.id.radio_button);
        TextView licenseName = (TextView) view.findViewById(R.id.title);
        TextView licenseDescription = (TextView) view.findViewById(R.id.subtitle);
        TextView gbif = (TextView) view.findViewById(R.id.gbif);
        TextView wikimedia = (TextView) view.findViewById(R.id.wikimedia);

        radioButton.setChecked((mSelectedValue != null) &&  mSelectedValue.equalsIgnoreCase(license.value));

        licenseName.setText(license.shortName);
        licenseDescription.setText(license.name);

        gbif.setVisibility(license.gbifCompatible ? View.VISIBLE : View.GONE);
        wikimedia.setVisibility(license.wikimediaCompatible ? View.VISIBLE : View.GONE);

        return view;
    }

}

