package org.inaturalist.android;


import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

class UserIdentificationsAdapter extends ArrayAdapter<String> {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private String mUsername;
    private boolean mIsGrid;
    private int mDimension;
    private PullToRefreshGridViewExtended mGrid;


    public UserIdentificationsAdapter(Context context, ArrayList<JSONObject> results, String username, boolean isGrid, PullToRefreshGridViewExtended grid) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
        mUsername = username;
        mIsGrid = isGrid;
        mGrid = grid;
    }

    public UserIdentificationsAdapter(Context context, ArrayList<JSONObject> results, String username) {
        this(context, results, username, false, null);
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(mIsGrid ? R.layout.observation_grid_item : R.layout.user_profile_identifications_item, parent, false);
        JSONObject item = null;
        item = mResultList.get(position);

        if (!mIsGrid) {
            ((ViewGroup) view.findViewById(R.id.taxon_result)).setVisibility(View.VISIBLE);
        }


        // Get the taxon display name according to device locale
        try {
            ImageView idPic = (ImageView) view.findViewById(mIsGrid ? R.id.observation_pic : R.id.id_pic);
            ImageView idIconicPic = (ImageView) view.findViewById(R.id.observation_iconic_pic);
            TextView idName = (TextView) view.findViewById(mIsGrid ? R.id.species_guess : R.id.id_name);
            TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);

            idIconicPic.setImageResource(ObservationPhotosViewer.observationIcon(item));
            idIconicPic.setVisibility(View.VISIBLE);

            JSONObject observation = item.getJSONObject("observation");
            JSONObject taxon = item.getJSONObject("taxon");
            idName.setText(getTaxonName(taxon));
            if (!mIsGrid) idTaxonName.setText(String.format(mContext.getString(R.string.users_identification), mUsername, getTaxonName(taxon)));

            if (mIsGrid) {
                mDimension = mGrid.getColumnWidth();
                idPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                idIconicPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                int newPadding = (int) (mDimension * 0.48 * 0.5); // So final image size will be 48% of original size
                idIconicPic.setPadding(newPadding, newPadding, newPadding, newPadding);
            }

            JSONArray photos = observation.optJSONArray("photos");
            if ((photos != null) && (photos.length() > 0)) {
                idPic.setVisibility(View.VISIBLE);

                UrlImageViewCallback callback = new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        if (loadedBitmap != null)
                            imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(loadedBitmap, 4));

                        if (mIsGrid) {
                            imageView.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                        }

                        if (!loadedFromCache) {
                            Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.fade_in);
                            imageView.startAnimation(animation);
                        }
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        return loadedBitmap;
                    }
                };

                UrlImageViewHelper.setUrlDrawable(idPic, photos.getJSONObject(0).getString("square_url"), callback);
            } else {
                idPic.setVisibility(View.INVISIBLE);
            }

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

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
                    displayName = item.optString("name");
                }
            }
        }

        return displayName;

    }
}

