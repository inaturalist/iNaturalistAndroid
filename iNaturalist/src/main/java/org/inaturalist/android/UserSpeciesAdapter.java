package org.inaturalist.android;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class UserSpeciesAdapter extends ArrayAdapter<String> implements AbsListView.OnScrollListener {
    private final INaturalistApp mApp;
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private int mViewType;
    private int mDimension;
    private PullToRefreshGridViewExtended mGrid;

    private HashMap<Integer, Boolean> mObservationLoaded;


    public static final int VIEW_TYPE_LIST = 0x1000;
    public static final int VIEW_TYPE_GRID = 0x1001;
    public static final int VIEW_TYPE_CARDS = 0x1002;
    private AbsListView.OnScrollListener mOriginalScrollListener;

    public UserSpeciesAdapter(Context context, ArrayList<JSONObject> results) {
        this(context, results, VIEW_TYPE_LIST, null);
    }

    public UserSpeciesAdapter(Context context, ArrayList<JSONObject> results, int viewType, PullToRefreshGridViewExtended grid) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mApp = (INaturalistApp) context.getApplicationContext();
        mResultList = results;
        mViewType = viewType;
        mGrid = grid;

        mObservationLoaded = new HashMap<>();
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

        if (convertView != null) {
            try {
                JSONObject item = (JSONObject) convertView.getTag();
                JSONObject otherItem = mResultList.get(position).getJSONObject("taxon");

                if ((item != null) && (item.getInt("id") == otherItem.getInt("id"))) return convertView;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        int layout;
        switch (mViewType) {
            case VIEW_TYPE_CARDS:
                layout = R.layout.mission_grid_item;
                break;
            case VIEW_TYPE_GRID:
                layout = R.layout.observation_grid_item;
                break;
            case VIEW_TYPE_LIST:
            default:
                layout = R.layout.user_profile_species_item;
        }

        View view = (convertView != null) ? convertView : inflater.inflate(layout, parent, false);
        JSONObject item = null;
        try {
            item = mResultList.get(position).getJSONObject("taxon");
        } catch (JSONException e) {
            e.printStackTrace();
            return view;
        }

        TextView speciesName = (TextView) view.findViewById(mViewType == VIEW_TYPE_LIST ? R.id.species_name : R.id.species_guess);

        // Get the taxon display name according to device locale
        try {
            ImageView speciesPic = (ImageView) view.findViewById(mViewType == VIEW_TYPE_LIST ? R.id.species_pic : R.id.observation_pic);
            ImageView speciesIconicPic = (ImageView) view.findViewById(R.id.observation_iconic_pic);

            if (mViewType == VIEW_TYPE_GRID) {
                mDimension = mGrid.getColumnWidth();
                speciesPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));

                int newDimension = (int) (mDimension * 0.48); // So final image size will be 48% of original size
                int speciesGuessHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, mContext.getResources().getDisplayMetrics());
                int leftRightMargin = (mDimension - newDimension) / 2;
                int topBottomMargin = (mDimension - speciesGuessHeight - newDimension) / 2;
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newDimension, newDimension);
                layoutParams.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, 0);
                speciesIconicPic.setLayoutParams(layoutParams);
            } else if ((mViewType == VIEW_TYPE_CARDS) && (mGrid != null)) {
                mDimension = mGrid.getColumnWidth();
                int lineHeight = speciesName.getLineHeight();
                if (convertView == null) view.setLayoutParams(new AbsListView.LayoutParams(mDimension, mDimension + (lineHeight * 2)));
            }

            TextView scienceName = (TextView) view.findViewById(R.id.species_science_name);

            String commonName = TaxonUtils.getTaxonName(mContext, item);

            speciesName.setTypeface(null, Typeface.NORMAL);
            if (mViewType == VIEW_TYPE_LIST) scienceName.setTypeface(null, Typeface.NORMAL);

            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name first, before common name
                TaxonUtils.setTaxonScientificName(speciesName, item);
                if (mViewType == VIEW_TYPE_LIST) scienceName.setText(commonName);
            } else {
                // Show common name first
                if (mViewType == VIEW_TYPE_LIST) TaxonUtils.setTaxonScientificName(scienceName, item);
                speciesName.setText(commonName);
            }

            String photoUrl = item.optString("photo_url");
            if (item.has("taxon_photos")) {
                JSONArray taxonPhotos = item.optJSONArray("taxon_photos");
                if ((taxonPhotos != null) && (taxonPhotos.length() > 0)) {
                    JSONObject photo = taxonPhotos.getJSONObject(0);
                    JSONObject photoInner = photo.optJSONObject("photo");
                    if ((photoInner != null) && (!photoInner.isNull("medium_url"))) photoUrl = photoInner.optString("medium_url");
                }
            } else if (item.has("default_photo")) {
                JSONObject defaultPhoto = item.optJSONObject("default_photo");
                if ((defaultPhoto != null) && (defaultPhoto.has("medium_url"))) photoUrl = defaultPhoto.getString("medium_url");
            }

            speciesPic.setVisibility(View.INVISIBLE);
            speciesIconicPic.setVisibility(View.VISIBLE);
            speciesIconicPic.setImageResource(TaxonUtils.observationIcon(item));

            if ((photoUrl != null) && (photoUrl.length() > 0)) {
                if ((mViewType == VIEW_TYPE_GRID) && (convertView == null)) {
                    speciesPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                }

                loadObsImage(position, speciesPic, photoUrl);
            } else {
                speciesPic.setVisibility(View.INVISIBLE);
            }

            if (mViewType == VIEW_TYPE_LIST) {
                TextView speciesCount = (TextView) view.findViewById(R.id.species_count);
                int obsCount = mResultList.get(position).optInt("count", -1);
                int count = obsCount > -1 ? obsCount : item.getInt("observations_count");
                DecimalFormat formatter = new DecimalFormat("#,###,###");
                speciesCount.setText(formatter.format(count));
            }

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
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
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        final Picasso picasso = Picasso.with(mContext);

        if (scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_TOUCH_SCROLL) {
            picasso.resumeTag(mContext);
        } else {
            picasso.pauseTag(mContext);
        }

        if (mOriginalScrollListener != null) {
            mOriginalScrollListener.onScrollStateChanged(view, scrollState);
        }
    }

    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {
        if (mOriginalScrollListener != null) {
            mOriginalScrollListener.onScroll(absListView, i, i1, i2);
        }
    }
}

