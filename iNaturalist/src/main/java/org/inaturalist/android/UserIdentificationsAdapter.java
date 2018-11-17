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
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

class UserIdentificationsAdapter extends ArrayAdapter<String> implements AbsListView.OnScrollListener {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private String mUsername;
    private String mLoggedInUsername;
    private boolean mIsGrid;
    private int mDimension;
    private PullToRefreshGridViewExtended mGrid;
    private HashMap<Integer, Boolean> mObservationLoaded;

    private AbsListView.OnScrollListener mOriginalScrollListener;

    public UserIdentificationsAdapter(Context context, ArrayList<JSONObject> results, String username, boolean isGrid, PullToRefreshGridViewExtended grid) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
        mUsername = username;
        mIsGrid = isGrid;
        mGrid = grid;

        mLoggedInUsername = ((INaturalistApp)mContext.getApplicationContext()).currentUserLogin();

        mObservationLoaded = new HashMap<>();
    }

    public UserIdentificationsAdapter(Context context, ArrayList<JSONObject> results, String username) {
        this(context, results, username, false, null);
    }

    public void setOnScrollListener(AbsListView.OnScrollListener listener) {
        mOriginalScrollListener = listener;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = (convertView != null) ? convertView : inflater.inflate(mIsGrid ? R.layout.observation_grid_item : R.layout.user_profile_identifications_item, parent, false);
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

            idPic.setVisibility(View.INVISIBLE);

            idIconicPic.setImageResource(TaxonUtils.observationIcon(item));
            idIconicPic.setVisibility(View.VISIBLE);

            JSONObject observation = item.getJSONObject("observation");
            JSONObject taxon = item.getJSONObject("taxon");
            idName.setText(getTaxonName(taxon));
            if (!mIsGrid) {
                if (!mUsername.equals(mLoggedInUsername)) {
                    idTaxonName.setText(String.format(mContext.getString(R.string.users_identification), mUsername, getTaxonName(taxon)));
                } else {
                    idTaxonName.setText(String.format(mContext.getString(R.string.your_identification), getTaxonName(taxon)));
                }
            }

            if (mIsGrid) {
                mDimension = mGrid.getColumnWidth();
                idPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));

                int newDimension = (int) (mDimension * 0.48); // So final image size will be 48% of original size
                int speciesGuessHeight = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, mContext.getResources().getDisplayMetrics());
                int leftRightMargin = (mDimension - newDimension) / 2;
                int topBottomMargin = (mDimension - speciesGuessHeight - newDimension) / 2;
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newDimension, newDimension);
                layoutParams.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, 0);
                idIconicPic.setLayoutParams(layoutParams);
            }

            JSONArray photos = observation.optJSONArray("photos");
            String photoUrl = null;
            if (mIsGrid && (convertView == null)) {
                idPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
            }

            if ((photos != null) && (photos.length() > 0)) {
                photoUrl = photos.getJSONObject(0).getString("url");
                loadObsImage(position, idPic, photoUrl);
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

    private void loadObsImage(final int position, final ImageView imageView, String url) {
        Picasso.with(mContext)
                .load(url)
                .fit()
                .centerCrop()
                .into(imageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        imageView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError() {

                    }
                });
    }

    @Override
    public void onScrollStateChanged(AbsListView listView, int scrollState) {
        final Picasso picasso = Picasso.with(mContext);

        if (scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_TOUCH_SCROLL) {
            picasso.resumeTag(mContext);
        } else {
            picasso.pauseTag(mContext);
        }

        if (mOriginalScrollListener != null) {
            mOriginalScrollListener.onScrollStateChanged(listView, scrollState);
        }
    }
    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {
        if (mOriginalScrollListener != null) {
            mOriginalScrollListener.onScroll(absListView, i, i1, i2);
        }
    }
}

