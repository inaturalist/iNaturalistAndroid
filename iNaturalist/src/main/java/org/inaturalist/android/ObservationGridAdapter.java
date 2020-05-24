package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ObservationGridAdapter extends ArrayAdapter<JSONObject> {

    private static final String TAG = "ObservationGridAdapter";
    private final INaturalistApp mApp;
    private List<JSONObject> mItems;
    private Context mContext;
    private int mDimension;

    @SuppressWarnings("WeakerAccess")
    public ObservationGridAdapter(Context context, int dimension, List<JSONObject> objects) {
        super(context, R.layout.guide_taxon_item, objects);

        mItems = objects != null ? objects : new ArrayList<>();
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
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

    @NotNull
    @Override
    public View getView(int position, View convertView, @NotNull ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.guide_taxon_item, parent, false);
        JSONObject item = mItems.get(position);

        TextView researchGrade = view.findViewById(R.id.is_research_grade);
        researchGrade.setVisibility(item.optString("quality_grade", "none").equals("research") ? View.VISIBLE : View.GONE);

        @SuppressWarnings("ConstantConditions")
        boolean hasSounds = (item.has("sounds")) && (!item.isNull("sounds")) && (item.optJSONArray("sounds").length() > 0);

        ImageView hasSoundsImage = view.findViewById(R.id.has_sounds);
        hasSoundsImage.setVisibility(hasSounds ? View.VISIBLE : View.INVISIBLE);

        TextView idName = view.findViewById(R.id.id_name);
        final JSONObject taxon = item.optJSONObject("taxon");

        if (taxon != null) {
            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name instead of common name
                TaxonUtils.setTaxonScientificName(idName, taxon);
            } else {
                String idNameStr = TaxonUtils.getTaxonName(mContext, taxon);
                idName.setText(idNameStr);
            }
        } else {
            idName.setText(R.string.unknown_species);
        }

        final ImageView taxonPic = view.findViewById(R.id.taxon_photo);
        final ImageView taxonIcon = view.findViewById(R.id.taxon_icon);

        taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
                mDimension, mDimension));
        taxonIcon.setLayoutParams(new RelativeLayout.LayoutParams(
                mDimension, mDimension));

        int labelHeight = idName.getLayoutParams().height;
        Resources r = mContext.getResources();
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, labelHeight, r.getDisplayMetrics());
        taxonIcon.setPadding(mDimension / 4, (mDimension - px) / 4, mDimension / 4, mDimension / 4);
        taxonPic.setVisibility(View.INVISIBLE);
        taxonIcon.setVisibility(View.VISIBLE);
        taxonIcon.setImageResource(TaxonUtils.observationIcon(item));

        JSONArray observationPhotos;
        boolean isNewApi = !item.has("observation_photos");
        try {
            observationPhotos = item.getJSONArray(isNewApi ? "photos" : "observation_photos");
        } catch (JSONException e1) {
            Logger.tag(TAG).error(e1);
            observationPhotos = new JSONArray();
        }

        if (observationPhotos.length() == 0) {
            if (hasSounds) {
                hasSoundsImage.setVisibility(View.INVISIBLE);
                taxonIcon.setImageResource(R.drawable.sound);
            }
        } else {
            JSONObject observationPhoto;
            //noinspection TryWithIdenticalCatches
            try {
                String url = null;
                observationPhoto = observationPhotos.getJSONObject(0);

                if (isNewApi) {
                    url = observationPhoto.optString("url");
                } else {
                    JSONObject innerPhoto = observationPhoto.optJSONObject("photo");
                    if (innerPhoto != null) {
                        url = (innerPhoto.isNull("small_url") ? innerPhoto.optString("original_url") : innerPhoto.optString("small_url"));
                        if (url.length() == 0) url = innerPhoto.optString("url");
                    } else Logger.tag(TAG).warn("photo field not present in json");
                }

                if ((url != null) && (url.length() > 0)) {
                    String extension = url.substring(url.lastIndexOf('.'));

                    // Deduce the original-sized URL
                    if (url.substring(0, url.lastIndexOf('/')).endsWith("assets")) {
                        // It's an assets default URL - e.g. https://www.inaturalist.org/assets/copyright-infringement-square.png
                        url = url.substring(0, url.lastIndexOf("-") + 1) + "medium" + extension;
                    } else {
                        // "Regular" observation photo
                        url = url.substring(0, url.lastIndexOf("/") + 1) + "medium" + extension;
                    }
                }

                Picasso.with(mContext)
                        .load(url)
                        .fit()
                        .centerCrop()
                        .into(taxonPic, new Callback() {
                            @Override
                            public void onSuccess() {
                                taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
                                        mDimension, mDimension));
                                taxonIcon.setVisibility(View.GONE);
                                taxonPic.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onError() {
                                Logger.tag(TAG).warn("Picasso error downloading obs url");
                            }
                        });

            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            } catch (Exception e) {
                // Could happen if user scrolls really fast and there a LOT of thumbnails being downloaded at once (too many threads at once)
                Logger.tag(TAG).error(e);
            }
        }

        view.setTag(item);

        return view;
    }
}


