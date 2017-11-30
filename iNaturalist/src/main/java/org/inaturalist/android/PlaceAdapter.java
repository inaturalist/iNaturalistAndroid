package org.inaturalist.android;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

class PlaceAdapter extends ArrayAdapter<String> {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;

    public PlaceAdapter(Context context, ArrayList<JSONObject> results) {
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
        View view = convertView == null ? inflater.inflate(R.layout.place_result_item, parent, false) : convertView;
        JSONObject item = mResultList.get(position);

        ImageView placePic = (ImageView) view.findViewById(R.id.place_pic);
        TextView placeName = (TextView) view.findViewById(R.id.place_name);
        TextView placeType = (TextView) view.findViewById(R.id.place_type);

        if (item.optBoolean("is_my_location")) {
            // A my location placeholder
            Picasso.with(mContext).load(R.drawable.ic_location_on_black_24dp).into(placePic);
            placePic.setColorFilter(mContext.getResources().getColor(R.color.inatapptheme_color));

            placeName.setText(R.string.my_location);
            placeName.setTextColor(mContext.getResources().getColor(R.color.inatapptheme_color));
            placeType.setVisibility(View.GONE);
        } else {
            placeName.setText(item.optString("display_name"));
            placeName.setTextColor(Color.parseColor("#000000"));
            placeType.setText(PlaceUtils.placeTypeToStringResource(item.optInt("place_type", 0)));
            placeType.setVisibility(View.VISIBLE);

            if (item.optBoolean("is_recent_result")) {
                // A recent place (from the search history)
                Picasso.with(mContext).load(R.drawable.ic_history_black_48dp).into(placePic);
                placePic.setColorFilter(Color.parseColor("#646464"));
            } else {
                // An actual search result
                Picasso.with(mContext).load(R.drawable.ic_location_on_black_24dp).into(placePic);
                placePic.setColorFilter(mContext.getResources().getColor(R.color.inatapptheme_color));
            }
        }

        view.setTag(item);

        return view;
    }

}

