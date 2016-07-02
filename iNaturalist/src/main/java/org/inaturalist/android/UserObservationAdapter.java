package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.apache.commons.collections4.map.CompositeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserObservationAdapter extends ArrayAdapter<JSONObject> {

    private List<JSONObject> mItems;
    private Context mContext;

    public UserObservationAdapter(Context context, List<JSONObject> objects) {
        super(context, R.layout.guide_taxon_item, objects);

        mItems = objects;
        mContext = context;
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
        final View view = inflater.inflate(R.layout.user_profile_observation_item, parent, false);
        JSONObject item = mItems.get(position);

        TextView idName = (TextView) view.findViewById(R.id.species_guess);
        String idNameStr = item.isNull("species_guess") ? mContext.getResources().getString(R.string.unknown) : item.optString("species_guess", mContext.getResources().getString(R.string.unknown));
        idName.setText(idNameStr);


        TextView placeGuess = (TextView) view.findViewById(R.id.place_guess);
        if (item.isNull("place_guess") || (item.optString("place_guess").length() == 0)) {
            if (!item.isNull("latitude") && !item.isNull("longitude")) {
                // Show coordinates instead
                placeGuess.setText(String.format(mContext.getString(R.string.location_coords_no_acc),
                        String.format("%.4f...", Double.valueOf(item.optString("latitude"))), String.format("%.4f...", Double.valueOf(item.optString("longitude")))));
            } else {
                // No location at all
                placeGuess.setText(R.string.no_location);
            }
        } else {
            placeGuess.setText(item.optString("place_guess"));
        }

        ImageView observationPic = (ImageView) view.findViewById(R.id.observation_pic);

        JSONArray observationPhotos;
        try {
            observationPhotos = item.getJSONArray("photos");
        } catch (JSONException e1) {
            e1.printStackTrace();
            observationPhotos = new JSONArray();
        }

        if (observationPhotos.length() > 0) {
            JSONObject observationPhoto;
            try {
                String url;
                observationPhoto = observationPhotos.getJSONObject(0);

                url = (observationPhoto.isNull("small_url") ? observationPhoto.optString("original_url") : observationPhoto.optString("small_url"));
                UrlImageViewHelper.setUrlDrawable(observationPic, url, ObservationPhotosViewer.observationIcon(item), new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        Bitmap centerCrop = ImageUtils.getRoundedCornerBitmap(ImageUtils.centerCropBitmap(loadedBitmap), 4);
                        return centerCrop;
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                // Could happen if user scrolls really fast and there a LOT of thumbnails being downloaded at once (too many threads at once)
                e.printStackTrace();
            }
        }


        TextView date = (TextView) view.findViewById(R.id.date);
        BetterJSONObject json = new BetterJSONObject(item);
        Timestamp dateTimestamp = json.getTimestamp("observed_on");
        if (dateTimestamp == null) {
            date.setVisibility(View.INVISIBLE);
        } else {
            date.setText(CommentsIdsAdapter.formatIdDate(dateTimestamp));
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
                    displayName = item.optString("name");
                }
            }
        }

        return displayName;

    }
}


