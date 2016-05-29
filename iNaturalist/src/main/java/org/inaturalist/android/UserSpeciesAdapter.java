package org.inaturalist.android;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;

class UserSpeciesAdapter extends ArrayAdapter<String> {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;

    public UserSpeciesAdapter(Context context, ArrayList<JSONObject> results) {
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
        View view = inflater.inflate(R.layout.user_profile_species_item, parent, false);
        JSONObject item = null;
        try {
            item = mResultList.get(position).getJSONObject("taxon");
        } catch (JSONException e) {
            e.printStackTrace();
            return view;
        }

        // Get the taxon display name according to device locale
        try {
            ImageView speciesPic = (ImageView) view.findViewById(R.id.species_pic);
            TextView speciesName = (TextView) view.findViewById(R.id.species_name);
            TextView scienceName = (TextView) view.findViewById(R.id.species_science_name);
            JSONObject defaultName = item.optJSONObject("default_name");

            if (defaultName != null) {
                speciesName.setText(defaultName.getString("name"));
                scienceName.setText(item.getString("name"));
            } else {
                speciesName.setText(item.getString("name"));
                scienceName.setVisibility(View.GONE);
            }

            if (item.has("photo_url") && !item.isNull("photo_url")) {
                String photoUrl = item.getString("photo_url");
                UrlImageViewHelper.setUrlDrawable(speciesPic, photoUrl, ObservationPhotosViewer.observationIcon(item), new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        if (loadedBitmap != null)
                            imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(loadedBitmap, 4));
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        return loadedBitmap;
                    }
                });
            } else {
                speciesPic.setImageResource(R.drawable.iconic_taxon_unknown);
            }

            TextView speciesCount = (TextView) view.findViewById(R.id.species_count);
            int count = item.getInt("observations_count");
            DecimalFormat formatter = new DecimalFormat("#,###,###");
            speciesCount.setText(formatter.format(count));

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

}

