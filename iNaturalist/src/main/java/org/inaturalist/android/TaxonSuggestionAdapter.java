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
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class TaxonSuggestionAdapter extends ArrayAdapter<String> {
    private List<BetterJSONObject> mResultList;
    private Context mContext;
    private OnTaxonSuggestion mOnTaxonSuggestion;

    public interface OnTaxonSuggestion {
        // When the user selected a specific taxon
        void onTaxonSelected(JSONObject taxon);
        // When the user wants to view taxon details page
        void onTaxonDetails(JSONObject taxon);
    }

    public TaxonSuggestionAdapter(Context context, List<BetterJSONObject> results, OnTaxonSuggestion onTaxonSuggestion) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
        mOnTaxonSuggestion = onTaxonSuggestion;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.taxon_suggestion_item, parent, false);

        final JSONObject item = mResultList.get(position).getJSONObject("taxon");

        ImageView taxonPhoto = (ImageView) view.findViewById(R.id.taxon_photo);
        TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
        TextView taxonScientificName = (TextView) view.findViewById(R.id.taxon_scientific_name);
        View selectTaxon = view.findViewById(R.id.select_taxon);

        selectTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnTaxonSuggestion.onTaxonSelected(item);
            }
        });

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnTaxonSuggestion.onTaxonDetails(item);
            }
        });

        // Get the taxon display name according to device locale
        taxonName.setText(TaxonUtils.getTaxonName(mContext, item));
        taxonScientificName.setText(item.optString("name"));

        if (item.has("default_photo") && !item.isNull("default_photo")) {
            JSONObject defaultPhoto = item.optJSONObject("default_photo");
            Picasso.with(mContext)
                    .load(defaultPhoto.optString("square_url"))
                    .fit()
                    .centerCrop()
                    .placeholder(TaxonUtils.observationIcon(item))
                    .into(taxonPhoto);
        } else {
            taxonPhoto.setImageResource(R.drawable.iconic_taxon_unknown);
        }

        view.setTag(item);

        return view;
    }

}

