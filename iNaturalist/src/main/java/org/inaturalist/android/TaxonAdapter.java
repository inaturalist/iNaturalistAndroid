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

import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

class TaxonAdapter extends ArrayAdapter<String> {
    private final INaturalistApp mApp;
    private ArrayList<JSONObject> mResultList;
    private Context mContext;

    public TaxonAdapter(Context context, ArrayList<JSONObject> results) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mApp = (INaturalistApp) context.getApplicationContext();
        mResultList = results;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.taxon_result_item, parent, false);
        JSONObject item = null;
        try {
            item = mResultList.get(position).has("taxon") ? mResultList.get(position).getJSONObject("taxon") : mResultList.get(position);
        } catch (JSONException e) {
            e.printStackTrace();
            return view;
        }

        ((ViewGroup)view.findViewById(R.id.taxon_result)).setVisibility(View.VISIBLE);
        ((ViewGroup)view.findViewById(R.id.unknown_taxon_result)).setVisibility(View.GONE);

        // Get the taxon display name according to device locale
        try {
            ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
            TextView idName = (TextView) view.findViewById(R.id.id_name);
            TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);

            String commonName = TaxonUtils.getTaxonName(mContext, item);

            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name first, before common name
                TaxonUtils.setTaxonScientificName(idName, item);
                idTaxonName.setText(commonName);
            } else {
                // Show common name first
                TaxonUtils.setTaxonScientificName(idTaxonName, item);
                idName.setText(commonName);
            }

            if (item.has("default_photo") && !item.isNull("default_photo")) {
                JSONObject defaultPhoto = item.getJSONObject("default_photo");
                Picasso.with(mContext)
                        .load(defaultPhoto.getString("square_url"))
                        .transform(new RoundedCornersTransformation(3, 0))
                        .placeholder(TaxonUtils.observationIcon(item))
                        .fit()
                        .into(idPic);
            } else {
                idPic.setImageResource(TaxonUtils.observationIcon(item));
            }

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

}

