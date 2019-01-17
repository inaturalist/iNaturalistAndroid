package org.inaturalist.android;


import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class TaxonomyAdapter extends ArrayAdapter<String> {
    private final TaxonomyListener mTaxonomyListener;
    private final boolean mShowChildren;
    private final INaturalistApp mApp;
    private BetterJSONObject mTaxon;
    private List<JSONObject> mAncestors;
    private Context mContext;
    private boolean mExpanded;

    public interface TaxonomyListener {
        void onViewChildren(BetterJSONObject taxon);
        void onViewTaxon(BetterJSONObject taxon);
    }

    public TaxonomyAdapter(Context context, BetterJSONObject taxon, TaxonomyListener onViewChildren) {
        this(context, taxon, false, onViewChildren);
    }

    public TaxonomyAdapter(Context context, BetterJSONObject taxon, boolean showChildren, TaxonomyListener onViewChildren) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mTaxon = taxon;
        mTaxonomyListener = onViewChildren;
        mShowChildren = showChildren;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        refreshAncestors();
    }

    private void refreshAncestors() {
        mAncestors = new ArrayList<>();

        String key = mShowChildren ? "children" : "ancestors";

        if (mTaxon.has(key)) {
            JSONArray ancestors = mTaxon.getJSONArray(key).getJSONArray();

            if (mExpanded || mShowChildren) {
                // Show all ancestors / show all children
                for (int i = 0; i < ancestors.length(); i++) {
                    mAncestors.add(ancestors.optJSONObject(i));
                }
            } else {
                // Show all ancestors up to family (including), and then the kingdom. Also show
                // the direct ancestor of the current taxon.
                for (int i = 0; i < ancestors.length(); i++) {
                    JSONObject ancestor = ancestors.optJSONObject(i);
                    int rankLevel = ancestor.optInt("rank_level");
                    if ((rankLevel <= 30) || (rankLevel == 70) ||
                            (i == ancestors.length() - 1)) {
                        mAncestors.add(ancestor);
                    }
                }

                if (mAncestors.size() < ancestors.length()) {
                    // Add a "..." placeholder (to expand the list)
                    JSONObject expandList = new JSONObject();
                    try {
                        expandList.put("placeholder", "expand_list");
                        mAncestors.add(1, expandList);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (!mShowChildren && mTaxon.has("children")) {
            // Add a "View children" placeholder
            JSONObject viewChildren = new JSONObject();
            try {
                viewChildren.put("placeholder", "view_children");
                mAncestors.add(viewChildren);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
        refreshAncestors();
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    @Override
    public int getCount() {
        return mAncestors.size();
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final JSONObject taxon = mAncestors.get(position);
        String placeholder = taxon.optString("placeholder", "");
        View view;

        if (placeholder.equals("view_children")) {
            // Show view children link
            view = inflater.inflate(R.layout.view_children, parent, false);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mTaxonomyListener.onViewChildren(mTaxon);
                }
            });
            return view;

        } else if (placeholder.equals("expand_list")) {
            // Show expand list link
            view = inflater.inflate(R.layout.expand_list, parent, false);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setExpanded(true);
                    notifyDataSetChanged();
                }
            });
            return view;

        } else {
            // Show ancestor
            view = inflater.inflate(R.layout.taxonomy_item, parent, false);
        }


        ImageView taxonPhoto = (ImageView) view.findViewById(R.id.taxon_photo);
        TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
        TextView taxonScientificName = (TextView) view.findViewById(R.id.taxon_scientific_name);


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

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTaxonomyListener.onViewTaxon(new BetterJSONObject(taxon));
            }
        });

        return view;
    }

}

