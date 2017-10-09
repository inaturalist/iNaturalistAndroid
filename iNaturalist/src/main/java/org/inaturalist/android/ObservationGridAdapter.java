package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ObservationGridAdapter extends ArrayAdapter<JSONObject> {

    private List<JSONObject> mItems;
    private Context mContext;
    private ArrayList<JSONObject> mOriginalItems;
    private int mDimension;

    public ObservationGridAdapter(Context context, int dimension, List<JSONObject> objects) {
        super(context, R.layout.guide_taxon_item, objects);

        mItems = objects;
        mOriginalItems = new ArrayList<JSONObject>(mItems);
        mContext = context;
        mDimension = dimension;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public JSONObject getItem(int index) {
        return mItems.get(index);
    }

    @SuppressLint("NewApi")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.guide_taxon_item, parent, false);
        JSONObject item = mItems.get(position);

        TextView idName = (TextView) view.findViewById(R.id.id_name);
        final JSONObject taxon = item.optJSONObject("taxon");

        if (taxon != null) {
            String idNameString = getTaxonName(taxon);
            if (idNameString != null) {
                idName.setText(idNameString);
            } else {
                idName.setText(mContext.getResources().getString(R.string.unknown));
            }
        } else {
            String idNameStr = item.isNull("species_guess") ?
                    mContext.getResources().getString(R.string.unknown) :
                    item.optString("species_guess", mContext.getResources().getString(R.string.unknown));
            idName.setText(idNameStr);
        }


        final ImageView taxonPic = (ImageView) view.findViewById(R.id.taxon_photo);

        taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
                mDimension, mDimension));

        JSONArray observationPhotos;
        boolean isNewApi = !item.has("observation_photos");
        try {
            observationPhotos = item.getJSONArray(isNewApi ? "photos" : "observation_photos");
        } catch (JSONException e1) {
            e1.printStackTrace();
            observationPhotos = new JSONArray();
        }

        if (observationPhotos.length() > 0) {
            JSONObject observationPhoto;
            try {
                String url;
                observationPhoto = observationPhotos.getJSONObject(0);

                if (isNewApi) {
                    url = observationPhoto.optString("url");
                } else {
                    JSONObject innerPhoto = observationPhoto.optJSONObject("photo");
                    url = (innerPhoto.isNull("small_url") ? innerPhoto.optString("original_url") : innerPhoto.optString("small_url"));
                    if ((url == null) || (url.length() == 0)) url = innerPhoto.optString("url");
                }

                if ((url != null) && (url.length() > 0)) {
                    String extension = url.substring(url.lastIndexOf(".") + 1);
                    url = url.substring(0, url.lastIndexOf("/") + 1) + "medium." + extension;
                }

                Picasso.with(mContext)
                        .load(url)
                        .placeholder(TaxonUtils.observationIcon(item))
                        .fit()
                        .centerCrop()
                        .into(taxonPic, new Callback() {
                            @Override
                            public void onSuccess() {
                                taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
                                        mDimension, mDimension));
                            }

                            @Override
                            public void onError() {

                            }
                        });

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                // Could happen if user scrolls really fast and there a LOT of thumbnails being downloaded at once (too many threads at once)
                e.printStackTrace();
            }
        }

        view.setTag(item);

        return view;
    }

    private String getTaxonName(JSONObject item) {
        JSONObject defaultName;
        String displayName = null;

        // Get the taxon display name according to device locale
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Locale deviceLocale = mContext.getResources().getConfiguration().locale;
        String deviceLexicon =   deviceLocale.getLanguage();

        try {
            JSONArray taxonNames = item.getJSONArray("taxon_names");
            for (int i = 0; i < taxonNames.length(); i++) {
                JSONObject taxonName = taxonNames.getJSONObject(i);
                String lexicon = taxonName.getString("lexicon");
                if (lexicon.equals(deviceLexicon)) {
                    // Found the appropriate lexicon for the taxon
                    displayName = taxonName.getString("name");
                    break;
                }
            }
        } catch (JSONException e3) {
            //e3.printStackTrace();
        }

        if (displayName == null) {
            // Couldn't extract the display name from the taxon names list - use the default one
            try {
                displayName = item.getString("unique_name");
            } catch (JSONException e2) {
                displayName = null;
            }
            try {
                defaultName = item.getJSONObject("default_name");
                displayName = defaultName.getString("name");
            } catch (JSONException e1) {
                // alas
                JSONObject commonName = item.optJSONObject("common_name");
                if (commonName != null) {
                    displayName = commonName.optString("name");
                } else {
                    displayName = item.optString("preferred_common_name");
                    if ((displayName == null) || (displayName.length() == 0)) {
                        displayName = item.optString("english_common_name");
                        if ((displayName == null) || (displayName.length() == 0)) {
                            displayName = item.optString("name");
                        }
                    }
                }
            }
        }

        return displayName;

    }
}


