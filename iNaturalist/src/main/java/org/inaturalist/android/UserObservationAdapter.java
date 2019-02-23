package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
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
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.apache.commons.collections4.map.CompositeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserObservationAdapter extends ArrayAdapter<JSONObject> {

    private final INaturalistApp mApp;
    private List<JSONObject> mItems;
    private Context mContext;
    private int mViewType;

    public static final int VIEW_TYPE_CARDS = 0x1000;
    public static final int VIEW_TYPE_GRID = 0x1001;


    public UserObservationAdapter(Context context, List<JSONObject> objects, int viewType) {
        super(context, R.layout.guide_taxon_item, objects);

        mItems = objects;
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mViewType = viewType;
    }

    public UserObservationAdapter(Context context, List<JSONObject> objects) {
        this(context, objects, VIEW_TYPE_GRID);
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
        final View view = inflater.inflate(mViewType == VIEW_TYPE_GRID ? R.layout.user_profile_observation_item : R.layout.mission_grid_item, parent, false);
        JSONObject item = mItems.get(position);

        TextView idName = (TextView) view.findViewById(R.id.species_guess);

        if (mApp.getShowScientificNameFirst()) {
            // Show scientific name first, before common name
            if (!item.isNull("taxon")) {
                TaxonUtils.setTaxonScientificName(idName, item.optJSONObject("taxon"));
            } else {
                idName.setText(item.isNull("species_guess") ? mContext.getResources().getString(R.string.unknown) : item.optString("species_guess", mContext.getResources().getString(R.string.unknown)));
            }
        } else {
            if (!item.isNull("taxon")) {
                try {
                    idName.setText(TaxonUtils.getTaxonName(mContext, item.getJSONObject("taxon")));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                idName.setText(item.isNull("species_guess") ? mContext.getResources().getString(R.string.unknown) : item.optString("species_guess", mContext.getResources().getString(R.string.unknown)));
            }
        }



        if (mViewType == VIEW_TYPE_GRID) {
            TextView placeGuess = (TextView) view.findViewById(R.id.place_guess);
            if (item.isNull("place_guess") || (item.optString("place_guess").length() == 0)) {
                if (!item.isNull("latitude") && !item.isNull("longitude")) {
                    // Show coordinates instead
                    placeGuess.setText(String.format(mContext.getString(R.string.location_coords_no_acc),
                            String.format("%.4f...", Double.valueOf(item.optString("latitude"))), String.format("%.4f...", Double.valueOf(item.optString("longitude")))));
                } else {
                    // No place at all
                    placeGuess.setText(R.string.no_location);
                }
            } else {
                placeGuess.setText(item.optString("place_guess"));
            }
        }

        ImageView observationPic = (ImageView) view.findViewById(R.id.observation_pic);
        ImageView obsIconicImage = (ImageView) view.findViewById(R.id.observation_iconic_pic);
        obsIconicImage.setVisibility(View.VISIBLE);
        obsIconicImage.setImageResource(TaxonUtils.observationIcon(item));

        JSONArray observationPhotos;
        try {
            observationPhotos = item.getJSONArray("photos");
        } catch (JSONException e1) {
            e1.printStackTrace();
            observationPhotos = new JSONArray();
        }

        if (observationPhotos.length() > 0) {
            observationPic.setVisibility(View.VISIBLE);
            JSONObject observationPhoto;
            try {
                String url;
                observationPhoto = observationPhotos.getJSONObject(0);

                url = (observationPhoto.isNull("small_url") ? observationPhoto.optString("original_url") : observationPhoto.optString("small_url"));
                if ((url == null) || (url.length() == 0)) {
                    url = observationPhoto.optString("url");
                }

                Picasso.with(mContext)
                        .load(url)
                        .fit()
                        .centerCrop()
                        .into(observationPic, new Callback() {
                            @Override
                            public void onSuccess() {

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
        } else {
            observationPic.setVisibility(View.INVISIBLE);
        }

        if (mViewType == VIEW_TYPE_GRID) {
            TextView date = (TextView) view.findViewById(R.id.date);
            BetterJSONObject json = new BetterJSONObject(item);
            Timestamp dateTimestamp = json.getTimestamp("observed_on");
            if (dateTimestamp == null) {
                date.setVisibility(View.INVISIBLE);
            } else {
                date.setText(CommentsIdsAdapter.formatIdDate(mContext, dateTimestamp));
                date.setVisibility(View.VISIBLE);
            }

            TextView commentCountText = (TextView) view.findViewById(R.id.comment_count);
            ImageView commentCountIcon = (ImageView) view.findViewById(R.id.comment_pic);
            int commentCount = item.optInt("comments_count");

            if (commentCount > 0) {
                commentCountIcon.setVisibility(View.VISIBLE);
                commentCountText.setVisibility(View.VISIBLE);
                commentCountText.setText(String.valueOf(commentCount));
            } else {
                commentCountIcon.setVisibility(View.GONE);
                commentCountText.setVisibility(View.GONE);
            }

            TextView idCountText = (TextView) view.findViewById(R.id.id_count);
            ImageView idCountIcon = (ImageView) view.findViewById(R.id.id_pic);
            int idCount = item.optInt("identifications_count");

            if (idCount > 0) {
                idCountIcon.setVisibility(View.VISIBLE);
                idCountText.setVisibility(View.VISIBLE);
                idCountText.setText(String.valueOf(idCount));
            } else {
                idCountIcon.setVisibility(View.GONE);
                idCountText.setVisibility(View.GONE);
            }
        }

        view.setTag(item);

        return view;
    }
}


