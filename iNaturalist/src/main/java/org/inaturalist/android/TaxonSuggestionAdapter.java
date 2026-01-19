package org.inaturalist.android;


import android.content.Context;

import androidx.appcompat.view.menu.ShowableListMenu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONObject;

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

        JSONObject sourceDetails = item.getJSONObject("source_details");
        Double visionScore = null;
        Double frequencyScore = null;

        if (sourceDetails != null) {
            visionScore = sourceDetails.optDouble("vision_score", 0);
            frequencyScore = sourceDetails.optDouble("frequency_score", 0);
        }

        if (((visionScore == null) || (frequencyScore == null)) || ((frequencyScore == 0) && (visionScore == 0))) {
            visuallySimilar.setVisibility(View.GONE);
        } else if ((visionScore > 0) && (frequencyScore > 0)) {
            visuallySimilar.setText(R.string.visually_similar_expected_nearby);
        } else if (visionScore > 0) {
            visuallySimilar.setText(R.string.visually_similar);
        } else if (frequencyScore > 0) {
            visuallySimilar.setText(R.string.expected_nearby);
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
            TaxonUtils.setTaxonScientificName(mApp, taxonName, taxon);
            taxonScientificName.setText(TaxonUtils.getTaxonName(mContext, taxon));
        } else {
            TaxonUtils.setTaxonScientificName(mApp, taxonScientificName, taxon);
            taxonName.setText(TaxonUtils.getTaxonName(mContext, taxon));
        }


        if (taxon.has("representative_photo") && !taxon.isNull("representative_photo")) {
            JSONObject representativePhoto = taxon.optJSONObject("representative_photo");
            Picasso.with(mContext)
                    .load(representativePhoto.optString("square_url"))
                    .fit()
                    .centerCrop()
                    .placeholder(TaxonUtils.observationIcon(taxon))
                    .into(taxonPhoto);
        } else if (taxon.has("default_photo") && !taxon.isNull("default_photo")) {
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

