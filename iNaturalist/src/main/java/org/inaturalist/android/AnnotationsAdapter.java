package org.inaturalist.android;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

class AnnotationsAdapter extends ArrayAdapter<String> {
    private final INaturalistApp mApp;
    private final OnAnnotationActions mOnAnnonationsActions;
    private final ActivityHelper mHelper;
    private Context mContext;

    private ArrayList<Pair<JSONObject, JSONObject>> mAttributes;
    private HashMap<Integer, Set<String>> mAttributeValuesAdded;
    private JSONObject mTaxon;

    public interface OnAnnotationActions {
        // When the user chooses to delete his annotation value
        void onDeleteAnnotationValue(String uuid);
        // When the user agrees with an annotation value
        void onAnnotationAgree(String uuid);
        // When the user disagrees with an annotation value
        void onAnnotationDisagree(String uuid);
        // When the user deletes his vote
        void onAnnotationVoteDelete(String uuid);
        // When the user sets the value of an annotation
        void onSetAnnotationValue(int annotationId, int valueId);
    }

    public AnnotationsAdapter(Context context, OnAnnotationActions onAnnonationActions, JSONObject taxon, JSONArray attributes, JSONArray observationAnnotations) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mOnAnnonationsActions = onAnnonationActions;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mHelper = new ActivityHelper(mContext);
        mTaxon = taxon;

        mAttributeValuesAdded = new HashMap<>();

        try {
            JSONArray ancestors = taxon.optJSONArray("ancestor_ids");
            ancestors.put(taxon.getInt("id")); // Add our own taxon ID well

            // First, remove any possible attribute values that are not valid for this observation taxon ID
            for (int i = 0; i < attributes.length(); i++) {
                JSONObject attribute = attributes.getJSONObject(i);
                JSONArray values = attribute.getJSONArray("values");
                JSONArray filteredValues = new JSONArray();
                for (int c = 0; c < values.length(); c++) {
                    JSONObject value = values.getJSONObject(c);
                    boolean excludeValue = false;
                    if (value.has("taxon_ids")) {
                        // Make sure that our taxon ancestry list has a taxon ID from this list, and
                        // if not - that means this value should be excluded.
                        JSONArray taxonIds = value.getJSONArray("taxon_ids");
                        boolean foundTaxonId = false;
                        for (int x = 0 ; x < taxonIds.length(); x++) {
                            for (int y = 0 ; y < ancestors.length(); y++) {
                                if (taxonIds.getInt(x) == ancestors.getInt(y)) {
                                    foundTaxonId = true;
                                    break;
                                }
                            }
                            if (foundTaxonId) break;
                        }

                        if (!foundTaxonId) {
                            // Haven't found our or our ancestors' taxon ID in the allowed list - exclude this value
                            excludeValue = true;
                        }
                    }
                    if (!excludeValue && value.has("excepted_taxon_ids")) {
                         // Make sure that our taxon ancestry list IS NOT in this list, and
                        // if it is - that means this value should be excluded.
                        JSONArray taxonIds = value.getJSONArray("excepted_taxon_ids");
                        boolean foundTaxonId = false;
                        for (int x = 0 ; x < taxonIds.length(); x++) {
                            for (int y = 0 ; y < ancestors.length(); y++) {
                                if (taxonIds.getInt(x) == ancestors.getInt(y)) {
                                    foundTaxonId = true;
                                    break;
                                }
                            }
                            if (foundTaxonId) break;
                        }

                        if (foundTaxonId) {
                            // Found our or ancestors' taxon ID in the excluded list - exclude this value
                            excludeValue = true;
                        }
                    }

                    if (!excludeValue) {
                        // Value should be included
                        filteredValues.put(value);
                    }
                }

                // Overwrite the values array with our new, filtered values array
                attribute.put("values", filteredValues);
            }


            // Build the attribute / annotations list - take note of existing values and cases of
            // annotations with multiple values
            mAttributes = new ArrayList<>();
            for (int i = 0; i < observationAnnotations.length(); i++) {
                // Find matching attribute for the current value
                int currentAttributeValueId = observationAnnotations.getJSONObject(i).getInt("controlled_attribute_id");
                for (int c = 0; c < attributes.length(); c++) {
                    if (attributes.getJSONObject(c).getInt("id") == currentAttributeValueId) {
                        mAttributes.add(new Pair<>(attributes.getJSONObject(c), observationAnnotations.getJSONObject(i)));

                        if (!mAttributeValuesAdded.containsKey(currentAttributeValueId)) {
                            mAttributeValuesAdded.put(currentAttributeValueId, new HashSet<String>());
                        }
                        Set<String> values = mAttributeValuesAdded.get(currentAttributeValueId);

                        values.add(observationAnnotations.getJSONObject(i).getJSONObject("controlled_value").getString("label"));

                        break;
                    }
                }
            }

            // Next, see if any multivalued attributes has values not added by a user already - if so,
            // add a value-less attribute for it. Same goes for any attributes that don't have
            // any values set by users.
            if (mApp.loggedIn()) {
                for (int i = 0; i < attributes.length(); i++) {
                    if (!mAttributeValuesAdded.containsKey(attributes.getJSONObject(i).getInt("id"))) {
                        mAttributes.add(new Pair<>(attributes.getJSONObject(i), (JSONObject) null));
                    } else if (attributes.getJSONObject(i).getBoolean("multivalued")) {
                        int possibleValues = attributes.getJSONObject(i).getJSONArray("values").length();
                        if (mAttributeValuesAdded.get(attributes.getJSONObject(i).getInt("id")).size() < possibleValues) {
                            // Still at least one value left to chosen by users for this multivalued attribute
                            mAttributes.add(new Pair<>(attributes.getJSONObject(i), (JSONObject) null));
                        }
                    }
                }
            }


            // Finally, sort by annotation ID
            Collections.sort(mAttributes, new Comparator<Pair<JSONObject, JSONObject>>() {
                @Override
                public int compare(Pair<JSONObject, JSONObject> p1, Pair<JSONObject, JSONObject> p2) {
                    try {
                        return p1.first.getInt("id") - p2.first.getInt("id");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            });
        } catch (JSONException exc) {
            exc.printStackTrace();
        }
    }

    @Override
    public int getCount() {
        return (mAttributes != null ? mAttributes.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.annotation, parent, false);
        } else {
            view = convertView;
        }

        Pair<JSONObject, JSONObject> pair = mAttributes.get(position);
        final JSONObject attribute = pair.first;
        JSONObject annotationValue = pair.second;


        try {
            TextView attributeName = (TextView) view.findViewById(R.id.attribute_name);
            ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            TextView attributeValue = (TextView) view.findViewById(R.id.attribute_value);
            TextView selectAttributeValue = (TextView) view.findViewById(R.id.select_attribute_value);
            final ImageView deleteValue = (ImageView) view.findViewById(R.id.delete_attribute_value);
            final ImageView agree = (ImageView) view.findViewById(R.id.agree);
            final ImageView disagree = (ImageView) view.findViewById(R.id.disagree);
            final View loading = view.findViewById(R.id.loading);

            attributeName.setText(attribute.getString("label"));

            loading.setVisibility(View.GONE);

            if (annotationValue == null) {
                // No value for current attribute
                userPic.setVisibility(View.GONE);
                attributeValue.setVisibility(View.GONE);
                selectAttributeValue.setVisibility(View.VISIBLE);
                agree.setVisibility(View.GONE);
                disagree.setVisibility(View.GONE);
                deleteValue.setVisibility(View.GONE);


                // See what values can we display for the user choose (any values that were *not*
                // previously chosen by another user for this annotation)
                Set<String> valuesAddedAlready = mAttributeValuesAdded.get(attribute.getInt("id"));
                if (valuesAddedAlready == null) valuesAddedAlready = new HashSet<>();
                final ArrayList<String> valuesToDisplay = new ArrayList<>();
                final ArrayList<Integer> valuesIdsToDisplay = new ArrayList<>();

                for (int i = 0; i < attribute.getJSONArray("values").length(); i++) {
                    String value = attribute.getJSONArray("values").getJSONObject(i).getString("label");
                    if (!valuesAddedAlready.contains(value)) {
                        valuesToDisplay.add(value);
                        valuesIdsToDisplay.add(attribute.getJSONArray("values").getJSONObject(i).getInt("id"));
                    }
                }

                final String fieldName = attribute.getString("label");
                final int attributeId = attribute.getInt("id");

                // Show a value selection dialog
                selectAttributeValue.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String[] values = valuesToDisplay.toArray(new String[valuesToDisplay.size()]);

                        mHelper.selection(fieldName, values, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int position) {
                                int valueId = valuesIdsToDisplay.get(position);

                                loading.setVisibility(View.VISIBLE);

                                mOnAnnonationsActions.onSetAnnotationValue(attributeId, valueId);
                            }
                        });
                    }
                });

            } else {
                // There is a value for the current attribute
                userPic.setVisibility(View.VISIBLE);
                attributeValue.setVisibility(View.VISIBLE);
                selectAttributeValue.setVisibility(View.GONE);
                agree.setVisibility(View.VISIBLE);
                disagree.setVisibility(View.VISIBLE);

                final JSONObject user = annotationValue.getJSONObject("user");
                final String annotationValueUUID = annotationValue.getString("uuid");

                if (user.has("icon_url") && !user.isNull("icon_url")) {
                    UrlImageViewHelper.setUrlDrawable(userPic, user.getString("icon_url"), R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
                        @Override
                        public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) { }

                        @Override
                        public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            // Return a circular version of the profile picture
                            Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                            return ImageUtils.getCircleBitmap(centerCrop);
                        }
                    });
                } else {
                    userPic.setImageResource(R.drawable.ic_account_circle_black_24dp);
                }

                userPic.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Open the user details screen
                        Intent intent = new Intent(mContext, UserProfile.class);
                        intent.putExtra("user", new BetterJSONObject(user));
                        mContext.startActivity(intent);
                    }
                });

                String userLogin = user.getString("login").toLowerCase();

                if (mApp.loggedIn() && mApp.currentUserLogin().toLowerCase().equals(userLogin)) {
                    // Allow the user to delete his annotation value
                    deleteValue.setVisibility(View.VISIBLE);
                } else {
                    deleteValue.setVisibility(View.GONE);
                }

                attributeValue.setText(annotationValue.getJSONObject("controlled_value").getString("label"));
                deleteValue.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        loading.setVisibility(View.VISIBLE);
                        deleteValue.setVisibility(View.GONE);

                        mOnAnnonationsActions.onDeleteAnnotationValue(annotationValueUUID);
                    }
                });

                // Look at the votes of the annotation value - see if the user already voted for/against it

                agree.setColorFilter(null);
                disagree.setColorFilter(null);
                agree.setTag(new Boolean(false));
                disagree.setTag(new Boolean(false));

                JSONArray votes = annotationValue.getJSONArray("votes");
                for (int i = 0; i < votes.length(); i++) {
                    JSONObject vote = votes.getJSONObject(i);
                    JSONObject voteUser = vote.getJSONObject("user");

                    if (mApp.loggedIn() && mApp.currentUserLogin().toLowerCase().equals(voteUser.getString("login").toLowerCase())) {
                        if (vote.getBoolean("vote_flag")) {
                            // Agrees with this
                            agree.setColorFilter(mContext.getResources().getColor(R.color.inatapptheme_color));
                            agree.setTag(new Boolean(true));
                        } else {
                            // Disagrees with this
                            disagree.setColorFilter(Color.parseColor("#8B0000"));
                            disagree.setTag(new Boolean(true));
                        }

                        break;
                    }
                }

                agree.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        loading.setVisibility(View.VISIBLE);

                        if (((Boolean)agree.getTag()) == true) {
                            // User cancels his previous agreement
                            mOnAnnonationsActions.onAnnotationVoteDelete(annotationValueUUID);
                        } else {
                            // User agrees with this
                            mOnAnnonationsActions.onAnnotationAgree(annotationValueUUID);
                        }
                    }
                });

                disagree.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        loading.setVisibility(View.VISIBLE);

                        if (((Boolean)disagree.getTag()) == true) {
                            // User cancels his previous disagreement
                            mOnAnnonationsActions.onAnnotationVoteDelete(annotationValueUUID);
                        } else {
                            // User disagrees with this
                            mOnAnnonationsActions.onAnnotationDisagree(annotationValueUUID);
                        }
                    }
                });


                if (!mApp.loggedIn()) {
                    agree.setVisibility(View.GONE);
                    disagree.setVisibility(View.GONE);
                }
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

}

