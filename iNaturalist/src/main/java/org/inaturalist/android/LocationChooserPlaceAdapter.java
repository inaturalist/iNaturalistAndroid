package org.inaturalist.android;


import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class LocationChooserPlaceAdapter extends ArrayAdapter<INatPlace> {
    private List<INatPlace> mResultList;
    private Context mContext;

    public LocationChooserPlaceAdapter(Context context, List<INatPlace> results) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = convertView == null ? inflater.inflate(R.layout.location_chooser_result_item, parent, false) : convertView;
        INatPlace item = mResultList.get(position);

        TextView placeName = (TextView) view.findViewById(R.id.place_name);
        TextView subtitle1 = (TextView) view.findViewById(R.id.place_subtitle1);
        TextView subtitle2 = (TextView) view.findViewById(R.id.place_subtitle2);

        placeName.setText(item.title);


        if (item.geoprivacy == null) {
            // Results from Google API place suggestions
            subtitle1.setText(item.subtitle);
            subtitle2.setVisibility(View.GONE);
        } else {
            // Results from iNat pinned locations

            String coords = String.format(mContext.getString(R.string.location_coords),
                    String.format("%.2f", item.latitude),
                    String.format("%.2f", item.longitude),
                    String.format("%d", item.accuracy.intValue())
            );

            subtitle1.setText(coords);
            subtitle2.setText(String.format(mContext.getString(R.string.geoprivacy_with_value),
                    item.geoprivacy));
            subtitle2.setVisibility(View.VISIBLE);
        }

        view.setTag(item);

        return view;
    }

}

