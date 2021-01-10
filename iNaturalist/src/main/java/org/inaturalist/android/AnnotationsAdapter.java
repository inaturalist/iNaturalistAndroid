package org.inaturalist.android;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.inaturalist.android.TaxonUtils.toSnakeCase;

class AnnotationsAdapter extends ArrayAdapter<String> {
    private static final String TAG = "AnnotationsAdapter";
    private final INaturalistApp mApp;
    private final OnAnnotationActions mOnAnnonationsActions;
    private final ActivityHelper mHelper;
    private final JSONObject mObservation;
    private Context mContext;

    private ArrayList<Pair<JSONObject, JSONObject>> mAttributes;
    private ArrayList<Boolean> mIsAttributeExpanded;
    private HashMap<Integer, Set<JSONObject>> mAttributeValuesAdded;

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
        // When an annotation has been collapsed / expanded (so we can resize the list)
        void onAnnotationCollapsedExpanded();
    }

    private boolean shouldExclude(JSONObject object, JSONArray ancestors) {

        boolean excludeValue = false;

        if (object.has("taxon_ids")) {
            // Make sure that our taxon ancestry list has a taxon ID from this list, and
            // if not - that means this value should be excluded.
            JSONArray taxonIds = object.optJSONArray("taxon_ids");
            boolean foundTaxonId = false;
            for (int x = 0; x < taxonIds.length(); x++) {
                for (int y = 0; y < ancestors.length(); y++) {
                    if (taxonIds.optInt(x) == ancestors.optInt(y)) {
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
        if (!excludeValue && object.has("excepted_taxon_ids")) {
            // Make sure that our taxon ancestry list IS NOT in this list, and
            // if it is - that means this value should be excluded.
            JSONArray taxonIds = object.optJSONArray("excepted_taxon_ids");
            boolean foundTaxonId = false;
            for (int x = 0; x < taxonIds.length(); x++) {
                for (int y = 0; y < ancestors.length(); y++) {
                    if (taxonIds.optInt(x) == ancestors.optInt(y)) {
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

        return excludeValue;
    }

    public AnnotationsAdapter(Context context, OnAnnotationActions onAnnonationActions, JSONObject observation, JSONObject taxon, JSONArray attributes, JSONArray observationAnnotations) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mObservation = observation;
        mOnAnnonationsActions = onAnnonationActions;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mHelper = new ActivityHelper(mContext);

        mAttributeValuesAdded = new HashMap<>();

        try {
            JSONArray ancestors = taxon != null ? taxon.optJSONArray("ancestor_ids") : new JSONArray();
            if (taxon != null) ancestors.put(taxon.getInt("id")); // Add our own taxon ID well

            // First, remove any possible attribute values that are not valid for this observation taxon ID
            for (int i = 0; i < attributes.length(); i++) {
                JSONObject attribute = attributes.getJSONObject(i);
                JSONArray values = attribute.getJSONArray("values");
                JSONArray filteredValues = new JSONArray();

                boolean excludeAttribute = shouldExclude(attribute, ancestors);

                // Exclude/Include this entire attribute
                attribute.put("exclude", excludeAttribute);

                if (excludeAttribute) continue;

                for (int c = 0; c < values.length(); c++) {
                    JSONObject value = values.getJSONObject(c);

                    boolean excludeValue = shouldExclude(value, ancestors);

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
            for (int i = 0; i < (observationAnnotations != null ? observationAnnotations.length() : 0); i++) {
                // Find matching attribute for the current value
                int currentAttributeValueId = observationAnnotations.getJSONObject(i).getInt("controlled_attribute_id");
                for (int c = 0; c < attributes.length(); c++) {
                    if (attributes.getJSONObject(c).getBoolean("exclude")) continue;

                    if (attributes.getJSONObject(c).getInt("id") == currentAttributeValueId) {
                        mAttributes.add(new Pair<>(attributes.getJSONObject(c), observationAnnotations.getJSONObject(i)));

                        if (!mAttributeValuesAdded.containsKey(currentAttributeValueId)) {
                            mAttributeValuesAdded.put(currentAttributeValueId, new HashSet<JSONObject>());
                        }
                        Set<JSONObject> values = mAttributeValuesAdded.get(currentAttributeValueId);

                        values.add(observationAnnotations.getJSONObject(i).getJSONObject("controlled_value"));

                        break;
                    }
                }
            }

            // Next, see if any multivalued attributes has values not added by a user already - if so,
            // add a value-less attribute for it. Same goes for any attributes that don't have
            // any values set by users.
            if (mApp.loggedIn()) {
                for (int i = 0; i < attributes.length(); i++) {
                    if (attributes.getJSONObject(i).getBoolean("exclude")) continue;

                    if (!mAttributeValuesAdded.containsKey(attributes.getJSONObject(i).getInt("id"))) {
                        mAttributes.add(new Pair<>(attributes.getJSONObject(i), (JSONObject) null));
                    } else if (attributes.getJSONObject(i).getBoolean("multivalued")) {
                        // If blocking value selected - don't allow adding other values
                        boolean hasBlocking = CollectionUtils.exists(mAttributeValuesAdded.get(attributes.getJSONObject(i).getInt("id")), v -> v.optBoolean("blocking", false));

                        if (!hasBlocking) {
                            // Don't count blocking values in this possibleValues count
                            JSONArray possibleValues = attributes.getJSONObject(i).getJSONArray("values");
                            int valueCount = 0;
                            for (int c = 0; c < possibleValues.length(); c++) {
                                JSONObject value = possibleValues.optJSONObject(c);
                                if (!value.optBoolean("blocking", false)) {
                                    valueCount++;
                                }
                            }

                            if (mAttributeValuesAdded.get(attributes.getJSONObject(i).getInt("id")).size() < valueCount) {
                                // Still at least one value left to chosen by users for this multivalued attribute
                                mAttributes.add(new Pair<>(attributes.getJSONObject(i), (JSONObject) null));
                            }
                        }


                    }
                }
            }


            // Sort by annotation label
            Collections.sort(mAttributes, new Comparator<Pair<JSONObject, JSONObject>>() {
                @Override
                public int compare(Pair<JSONObject, JSONObject> p1, Pair<JSONObject, JSONObject> p2) {
                    try {
                        String translatedName1 = getAnnotationTranslatedValue(mApp, p1.first.getString("label"), false);
                        String translatedName2 = getAnnotationTranslatedValue(mApp, p2.first.getString("label"), false);
                        return translatedName1.compareTo(translatedName2);
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                        return 0;
                    }
                }
            });

            mIsAttributeExpanded = new ArrayList<Boolean>(Collections.nCopies(mAttributes.size(), false));

        } catch (JSONException exc) {
            Logger.tag(TAG).error(exc);
        }
    }

    @Override
    public int getCount() {
        return (mAttributes != null ? mAttributes.size() : 0);
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
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
            final ImageView agreeIcon = (ImageView) view.findViewById(R.id.agree_icon);
            final ImageView disagreeIcon = (ImageView) view.findViewById(R.id.disagree_icon);
            final ImageView agreePrefix = (ImageView) view.findViewById(R.id.agree_prefix);
            final ImageView disagreePrefix = (ImageView) view.findViewById(R.id.disagree_prefix);
            final ViewGroup agreeContainer = (ViewGroup) view.findViewById(R.id.agree_container);
            final ViewGroup disagreeContainer = (ViewGroup) view.findViewById(R.id.disagree_container);
            final TextView agreeText = (TextView) view.findViewById(R.id.agree_text);
            final TextView disagreeText = (TextView) view.findViewById(R.id.disagree_text);
            final View loading = view.findViewById(R.id.loading);
            final ImageView expand = (ImageView) view.findViewById(R.id.expand);
            final ViewGroup expandedSection = view.findViewById(R.id.expanded_section);

            if (mIsAttributeExpanded.get(position)) {
                expandedSection.setVisibility(View.VISIBLE);
                expand.setImageResource(R.drawable.baseline_arrow_drop_up_black_48);
            } else {
                expandedSection.setVisibility(View.GONE);
                expand.setImageResource(R.drawable.baseline_arrow_drop_down_black_48);
            }

            expand.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean isExpanded = !mIsAttributeExpanded.get(position);
                    mIsAttributeExpanded.set(position, isExpanded);

                    expandedSection.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                    expand.setImageResource(isExpanded ? R.drawable.baseline_arrow_drop_up_black_48 : R.drawable.baseline_arrow_drop_down_black_48);

                    mOnAnnonationsActions.onAnnotationCollapsedExpanded();
                }
            });

            String translatedName = getAnnotationTranslatedValue(mApp, attribute.getString("label"), false);
            attributeName.setText(translatedName);

            loading.setVisibility(View.GONE);

            if (annotationValue == null) {
                // No value for current attribute
                userPic.setVisibility(View.GONE);
                attributeValue.setVisibility(View.GONE);
                selectAttributeValue.setVisibility(View.VISIBLE);
                deleteValue.setVisibility(View.GONE);

                expand.setVisibility(View.GONE);

                // See what values can we display for the user choose (any values that were *not*
                // previously chosen by another user for this annotation)
                Set<JSONObject> valuesAddedAlready = mAttributeValuesAdded.get(attribute.getInt("id"));
                if (valuesAddedAlready == null) valuesAddedAlready = new HashSet<>();
                final ArrayList<String> valuesToDisplay = new ArrayList<>();
                final ArrayList<Integer> valuesIdsToDisplay = new ArrayList<>();

                for (int i = 0; i < attribute.getJSONArray("values").length(); i++) {
                    JSONObject value = attribute.getJSONArray("values").getJSONObject(i);

                    if ((value.optBoolean("blocking", false)) && (valuesAddedAlready.size() > 0)) {
                        // Don't show blocking items, if at least one value was selected already
                    } else if (!CollectionUtils.exists(valuesAddedAlready, v -> v.optInt("id") == value.optInt("id"))) {
                        String translatedValue = getAnnotationTranslatedValue(mApp, attribute.getJSONArray("values").getJSONObject(i).getString("label"), true);
                        valuesToDisplay.add(translatedValue);
                        valuesIdsToDisplay.add(attribute.getJSONArray("values").getJSONObject(i).getInt("id"));
                    }
                }

                final String fieldName = translatedName;
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
                expand.setVisibility(View.VISIBLE);

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


                deleteValue.setVisibility(View.GONE);

                // Allow the user to delete the annotation value only if -
                // A) This is the current user's annotation
                // B) This is the current user's observation
                if (mApp.loggedIn()) {
                    String loggedInUser = mApp.currentUserLogin().toLowerCase();
                    if (loggedInUser.equals(userLogin) || loggedInUser.equals(mObservation.optString("user_login"))) {
                        deleteValue.setVisibility(View.VISIBLE);
                    }
                }

                String translatedValue = getAnnotationTranslatedValue(mApp, annotationValue.getJSONObject("controlled_value").getString("label"), true) ;
                attributeValue.setText(translatedValue);
                deleteValue.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        loading.setVisibility(View.VISIBLE);
                        deleteValue.setVisibility(View.GONE);

                        mOnAnnonationsActions.onDeleteAnnotationValue(annotationValueUUID);
                    }
                });

                // Look at the votes of the annotation value - see if the user already voted for/against it

                agreeIcon.setColorFilter(Color.parseColor("#C3C3C3"));
                disagreeIcon.setColorFilter(Color.parseColor("#C3C3C3"));
                agreeText.setTextColor(Color.parseColor("#C3C3C3"));
                disagreeText.setTextColor(Color.parseColor("#C3C3C3"));
                agreeText.setTypeface(null, Typeface.NORMAL);
                disagreeText.setTypeface(null, Typeface.NORMAL);

                agreeContainer.setTag(new Boolean(false));
                disagreeContainer.setTag(new Boolean(false));

                JSONArray votes = annotationValue.getJSONArray("votes");
                int disagreeCount = 0, agreeCount = 0;
                for (int i = 0; i < votes.length(); i++) {
                    JSONObject vote = votes.getJSONObject(i);
                    JSONObject voteUser = vote.getJSONObject("user");

                    if (vote.getBoolean("vote_flag")) {
                        agreeCount++;
                    } else {
                        disagreeCount++;
                    }

                    if (mApp.loggedIn() && mApp.currentUserLogin().toLowerCase().equals(voteUser.getString("login").toLowerCase())) {
                        if (vote.getBoolean("vote_flag")) {
                            // Agrees with this
                            agreeIcon.setColorFilter(Color.parseColor("#808080"));
                            agreeText.setTextColor(Color.parseColor("#808080"));
                            agreeText.setTypeface(null, Typeface.BOLD);
                            agreeContainer.setTag(new Boolean(true));
                        } else {
                            // Disagrees with this
                            disagreeIcon.setColorFilter(Color.parseColor("#808080"));
                            disagreeText.setTextColor(Color.parseColor("#808080"));
                            disagreeText.setTypeface(null, Typeface.BOLD);
                            disagreeContainer.setTag(new Boolean(true));
                        }

                        break;
                    }
                }

                LinearLayout.LayoutParams agreeParams = (LinearLayout.LayoutParams) agreeContainer.getLayoutParams();
                LinearLayout.LayoutParams disagreeParams = (LinearLayout.LayoutParams) disagreeContainer.getLayoutParams();

                if (agreeCount == disagreeCount) {
                    agreePrefix.setVisibility(View.GONE);
                    disagreePrefix.setVisibility(View.GONE);
                    agreeParams.weight = 1;
                    disagreeParams.weight = 1;
                } else if (agreeCount > disagreeCount) {
                    agreePrefix.setVisibility(View.VISIBLE);
                    disagreePrefix.setVisibility(View.GONE);
                    agreeParams.weight = 3;
                    disagreeParams.weight = 2;
                } else {
                    agreePrefix.setVisibility(View.GONE);
                    disagreePrefix.setVisibility(View.VISIBLE);
                    agreeParams.weight = 2;
                    disagreeParams.weight = 3;
                }

                agreeContainer.setLayoutParams(agreeParams);
                disagreeContainer.setLayoutParams(disagreeParams);

                if (agreeCount > 0) {
                    agreeText.setText(String.format("%s (%d)", mContext.getText(R.string.agree2), agreeCount));
                }
                if (disagreeCount > 0) {
                    disagreeText.setText(String.format("%s (%d)", mContext.getText(R.string.disagree), disagreeCount));
                }

                agreeContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        loading.setVisibility(View.VISIBLE);

                        if (((Boolean)agreeContainer.getTag()) == true) {
                            // User cancels his previous agreement
                            mOnAnnonationsActions.onAnnotationVoteDelete(annotationValueUUID);
                        } else {
                            // User agrees with this
                            mOnAnnonationsActions.onAnnotationAgree(annotationValueUUID);
                        }
                    }
                });

                disagreeContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        loading.setVisibility(View.VISIBLE);

                        if (((Boolean)disagreeContainer.getTag()) == true) {
                            // User cancels his previous disagreement
                            mOnAnnonationsActions.onAnnotationVoteDelete(annotationValueUUID);
                        } else {
                            // User disagrees with this
                            mOnAnnonationsActions.onAnnotationDisagree(annotationValueUUID);
                        }
                    }
                });

                if (!mApp.loggedIn()) {
                    expand.setVisibility(View.GONE);
                }
            }


        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

        return view;

    }


    // Gets the translated value of an annotation label (according to current locale).
    // isValue param determines whether this is the annotation name or value.
    public static String getAnnotationTranslatedValue(INaturalistApp app, String label, boolean isValue)  {
        String translated = app.getStringResourceByNameOrNull(
                String.format("%s_%s", isValue ? "annotation_value" : "annotation_name", toSnakeCase(label)));

        if (translated == null) {
            // Couldn't find a translation - return original label
            return label;
        } else {
            return translated;
        }

    }
}

