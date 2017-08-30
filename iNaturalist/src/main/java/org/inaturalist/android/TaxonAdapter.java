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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

class TaxonAdapter extends ArrayAdapter<String> {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;

    public TaxonAdapter(Context context, ArrayList<JSONObject> results) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
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
            item = mResultList.get(position).getJSONObject("taxon");
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
            String commonName = item.optString("preferred_common_name", null);
            if ((commonName == null) || (commonName.length() == 0)) {
                commonName = item.optString("english_common_name");
            }

            if ((commonName != null) && (commonName.length() > 0)) {
                idName.setText(commonName);
                idTaxonName.setText(item.getString("name"));
            } else {
                idName.setText(item.getString("name"));
                idTaxonName.setVisibility(View.GONE);
            }

            idTaxonName.setTypeface(null, Typeface.ITALIC);
            if (item.has("default_photo") && !item.isNull("default_photo")) {
                JSONObject defaultPhoto = item.getJSONObject("default_photo");
                UrlImageViewHelper.setUrlDrawable(idPic, defaultPhoto.getString("square_url"), TaxonUtils.observationIcon(item), new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        if (loadedBitmap != null)
                            imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(loadedBitmap, 4));
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        return loadedBitmap;
                    }
                });
            } else {
                idPic.setImageResource(R.drawable.iconic_taxon_unknown);
            }

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

}

