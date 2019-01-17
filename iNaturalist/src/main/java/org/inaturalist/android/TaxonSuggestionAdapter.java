package org.inaturalist.android;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.support.v7.view.menu.ShowableListMenu;
import android.util.Log;
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
import java.util.List;

class TaxonSuggestionAdapter extends ArrayAdapter<String> {
    private final INaturalistApp mApp;
    private List<BetterJSONObject> mResultList;
    private Context mContext;
    private OnTaxonSuggestion mOnTaxonSuggestion;
    private boolean mShowCompare;

    public interface OnTaxonSuggestion {
        // When the user selected a specific taxon
        void onTaxonSelected(int position, JSONObject taxon);
        // When the user wants to view taxon details page
        void onTaxonDetails(int position, JSONObject taxon);
        // When the user wants to compare a taxon
        void onTaxonCompared(int position, JSONObject taxon);
    }

    public TaxonSuggestionAdapter(Context context, List<BetterJSONObject> results, OnTaxonSuggestion onTaxonSuggestion, boolean showCompare) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mResultList = results;
        mOnTaxonSuggestion = onTaxonSuggestion;
        mShowCompare = showCompare;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.taxon_suggestion_item, parent, false);

        BetterJSONObject item = mResultList.get(position);
        final JSONObject taxon = item.getJSONObject("taxon");

        ImageView taxonPhoto = (ImageView) view.findViewById(R.id.taxon_photo);
        TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
        TextView taxonScientificName = (TextView) view.findViewById(R.id.taxon_scientific_name);
        TextView visuallySimilar = (TextView) view.findViewById(R.id.visually_similar);
        View selectTaxon = view.findViewById(R.id.select_taxon);
        View compareTaxon = view.findViewById(R.id.compare_taxon);

        Float visionScore = item.getFloat("vision_score");
        Float frequencyScore = item.getFloat("frequency_score");

        if ((visionScore == null) || (frequencyScore == null)) {
            visuallySimilar.setVisibility(View.GONE);
        } else if ((visionScore > 0) && (frequencyScore > 0)) {
            visuallySimilar.setText(R.string.visually_similar_seen_nearby);
        } else if (visionScore > 0) {
            visuallySimilar.setText(R.string.visually_similar);
        } else if (frequencyScore > 0) {
            visuallySimilar.setText(R.string.seen_nearby);
        }

        compareTaxon.setVisibility(mShowCompare ? View.VISIBLE : View.INVISIBLE);

        selectTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnTaxonSuggestion.onTaxonSelected(position, taxon);
            }
        });

        compareTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnTaxonSuggestion.onTaxonCompared(position, taxon);
            }
        });

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnTaxonSuggestion.onTaxonDetails(position, taxon);
            }
        });

        // Get the taxon display name according to device locale

        if (mApp.getShowScientificNameFirst()) {
            // Show scientific name first, before common name
            TaxonUtils.setTaxonScientificName(taxonName, taxon);
            taxonScientificName.setText(TaxonUtils.getTaxonName(mContext, taxon));
        } else {
            TaxonUtils.setTaxonScientificName(taxonScientificName, taxon);
            taxonName.setText(TaxonUtils.getTaxonName(mContext, taxon));
        }


        if (taxon.has("default_photo") && !taxon.isNull("default_photo")) {
            JSONObject defaultPhoto = taxon.optJSONObject("default_photo");
            Picasso.with(mContext)
                    .load(defaultPhoto.optString("square_url"))
                    .fit()
                    .centerCrop()
                    .placeholder(TaxonUtils.observationIcon(taxon))
                    .into(taxonPhoto);
        } else {
            taxonPhoto.setImageResource(R.drawable.iconic_taxon_unknown);
        }

        view.setTag(taxon);

        return view;
    }

}

