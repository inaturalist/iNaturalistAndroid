package org.inaturalist.android;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.livefront.bridge.Bridge;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class PinnedLocationSearchActivity extends AppCompatActivity {
    private static final String TAG = "PinnedLocationSearchActivity";

    public static final String ID = "id";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String TITLE = "title";
    public static final String POSITIONAL_ACCURACY = "positional_accuracy";
    public static final String GEOPRIVACY = "geoprivacy";

    private PinnedLocationAutoCompleteAdapter mAdapter;

    private ProgressBar mProgress;
    
    private INaturalistApp mApp;

    private long mLastTime = 0;
    private TextView mNoResults;

    private JSONObject mLastTaxon;

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }
    }
 

    private ArrayList<JSONObject> autocomplete(String input) {
        ArrayList<JSONObject> resultList = null;

        if (!isNetworkAvailable()) {
            return new ArrayList<JSONObject>();
        }

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(INaturalistService.HOST + "/saved_locations.json");
            sb.append("?q=");
            sb.append(URLEncoder.encode(input, "utf8"));

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Locale deviceLocale = getResources().getConfiguration().locale;
            String deviceLexicon = deviceLocale.getLanguage();
            sb.append("&locale=");
            sb.append(deviceLexicon);

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            String jwtToken = mApp.getJWTToken();
            if (mApp.loggedIn() && (jwtToken != null)) conn.setRequestProperty ("Authorization", jwtToken);

            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }

        } catch (MalformedURLException e) {
            Logger.tag(TAG).error("Error processing Places API URL", e);
            return resultList;
        } catch (IOException e) {
            Logger.tag(TAG).error("Error connecting to Places API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            JSONObject resultsObject = new JSONObject(jsonResults.toString());
            JSONArray predsJsonArray = resultsObject.getJSONArray("results");

            resultList = new ArrayList<JSONObject>(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                resultList.add(predsJsonArray.getJSONObject(i));
            }
        } catch (JSONException e) {
            Logger.tag(TAG).error("Cannot process JSON results", e);
        }

        return resultList;
    }
    
    
    private class PinnedLocationAutoCompleteAdapter extends ArrayAdapter<String> implements Filterable {
        private ArrayList<JSONObject> mResultList;
        private Context mContext;
        private String mCurrentSearchString;

        public PinnedLocationAutoCompleteAdapter(Context context, int resourceId) {
            super(context, resourceId, new ArrayList<String>());
            
            mContext = context;
            
            mResultList = new ArrayList<JSONObject>();
        }

        @Override
        public int getCount() {
            return (mResultList != null ? mResultList.size() : 0);
        }

        @Override
        public String getItem(int index) {
            try {
                return mResultList.get(index).getString("name");
            } catch (JSONException e) {
                return "";
            }
        }
        
        private void toggleLoading(final boolean isLoading) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isLoading) {
                        mProgress.setVisibility(View.VISIBLE);

                        if (isNetworkAvailable()) {
                            // While we're waiting for results to load, show the string the user is
                            // typing as the first result (just with an unknown taxon type)
                            if (mResultList == null) {
                                mResultList = new ArrayList<JSONObject>();
                            } else {
                                mResultList.clear();
                            }
                            notifyDataSetChanged();
                        }

                    } else {
                        mProgress.setVisibility(View.GONE);
                    }
                }
            });
        }

        @Override
        public Filter getFilter() {
            Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();

                    if (constraint != null) {
                        if (constraint.length() == 0) {
                            filterResults.values = new ArrayList<JSONObject>();
                            filterResults.count = 0;

                        } else {
                            toggleLoading(true);

                            // Retrieve the autocomplete results.
                            ArrayList<JSONObject> results;
                            mCurrentSearchString = (String) constraint;
                            results = autocomplete(constraint.toString());

                            if (!constraint.equals(mCurrentSearchString)) {
                                // In the meanwhile, new searches were initiated by the user - ignore this result
                                return null;
                            }

                            // Assign the data to the FilterResults
                            filterResults.values = results;
                            filterResults.count = results != null ? results.size() : 0;
                        }
                    }

                    toggleLoading(false);

                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, final FilterResults results) {
                    ArrayList<JSONObject> values = results != null ? (ArrayList<JSONObject>) results.values : null;

                    if (results != null && results.count > 0 && results.values != null) {
                        mResultList = values;
                        notifyDataSetChanged();
                    } else {
                        if ((results != null) && (results.values != null)) {
                            mResultList = values;
                        }

                        notifyDataSetInvalidated();
                    }
                }

            };

            return filter;
        }

        
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.pinned_location_item, parent, false);
            final JSONObject place = mResultList.get(position);

            ImageView taxonPhoto = (ImageView) view.findViewById(R.id.taxon_photo);
            TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
            TextView taxonScientificName = (TextView) view.findViewById(R.id.taxon_scientific_name);
            TextView visuallySimilar = (TextView) view.findViewById(R.id.visually_similar);
            final View selectTaxon = view.findViewById(R.id.select_taxon);
            View compareTaxon = view.findViewById(R.id.compare_taxon);

            visuallySimilar.setVisibility(View.GONE);

            selectTaxon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTaxon((JSONObject) view.getTag());
                }
            });

            if (place == null) {
                // It's the unknown taxon row (the first row)
                Picasso.with(mContext)
                        .load(R.drawable.unknown_large)
                        .fit()
                        .centerCrop()
                        .into(taxonPhoto);


                taxonName.setText(R.string.unknown);
                taxonScientificName.setVisibility(View.GONE);

                compareTaxon.setVisibility(View.GONE);

                view.setOnClickListener(null);
                view.setTag(null);
                return view;

            } else if (place.optBoolean("is_custom", false)) {
                // Custom-named taxon
                Picasso.with(mContext)
                        .load(R.drawable.iconic_taxon_unknown)
                        .fit()
                        .centerCrop()
                        .into(taxonPhoto);

                taxonName.setText(place.optString("name"));

                taxonScientificName.setVisibility(View.GONE);
                compareTaxon.setVisibility(View.GONE);

                view.setOnClickListener(null);
                view.setTag(place);
                return view;
            }


            // TODO

            /*
            boolean hasPhotos = false;

            if ((mObsId > -1) || (mObsIdInternal > -1)) {
                Cursor cursor = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                        new String[]{ ObservationPhoto._OBSERVATION_ID, ObservationPhoto.OBSERVATION_ID },
                        "(observation_id = " + mObsId + " OR _observation_id = " + mObsIdInternal + ")",
                        null,
                        ObservationPhoto.DEFAULT_SORT_ORDER);
                hasPhotos = (cursor.getCount() > 0);
                cursor.close();
            }
            if (mObservationJson != null) {
                try {
                    JSONObject obs = new JSONObject(mObservationJson);
                    hasPhotos = hasPhotos || (obs.has("observation_photos") && (obs.optJSONArray("observation_photos").length() > 0));
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }

            if (!hasPhotos) {
                // Observation has no photos - don't show the compare button
                compareTaxon.setVisibility(View.GONE);
            }

            final boolean hasPhotosFinal = hasPhotos;

            compareTaxon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    compareTaxon(place);
                }
            });

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, TaxonActivity.class);
                    intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(place));
                    intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                    intent.putExtra(TaxonActivity.TAXON_SUGGESTION, hasPhotosFinal ? TaxonActivity.TAXON_SUGGESTION_COMPARE_AND_SELECT : TaxonActivity.TAXON_SUGGESTION_SELECT);
                    if (mObservationJson != null) {
                        intent.putExtra(TaxonActivity.OBSERVATION, new BetterJSONObject(mObservationJson));
                    }
                    mLastTaxon = place;
                    startActivityForResult(intent, VIEW_TAXON_REQUEST_CODE);
                }
            });


            taxonScientificName.setVisibility(View.VISIBLE);

            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name first, before common name
                TaxonUtils.setTaxonScientificName(taxonName, place);
                taxonScientificName.setText(TaxonUtils.getTaxonName(mContext, place));
            } else {
                TaxonUtils.setTaxonScientificName(taxonScientificName, place);
                taxonName.setText(TaxonUtils.getTaxonName(mContext, place));
            }


            if (place.has("default_photo") && !place.isNull("default_photo")) {
                JSONObject defaultPhoto = place.optJSONObject("default_photo");
                Picasso.with(mContext)
                        .load(defaultPhoto.optString("square_url"))
                        .placeholder(TaxonUtils.observationIcon(place))
                        .fit()
                        .centerCrop()
                        .into(taxonPhoto);
            } else {
                Picasso.with(mContext)
                        .load(R.drawable.iconic_taxon_unknown)
                        .fit()
                        .centerCrop()
                        .into(taxonPhoto);

            }

            view.setTag(place);
            */

            return view;
        }

    } 
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            setResult(RESULT_CANCELED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAfterTransition();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

   @Override
   public void onBackPressed() {
       setResult(RESULT_CANCELED);
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
           finishAfterTransition();
       } else {
           finish();
       }
   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.taxon_search_action_bar, null);
        actionBar.setCustomView(customView);
        actionBar.setLogo(R.drawable.ic_arrow_back);
       
        setContentView(R.layout.taxon_search);

        // TODO
        /*
        
        Intent intent = getIntent();
        mFieldId = intent.getIntExtra(FIELD_ID, 0);
        
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);

        mNoResults = (TextView) findViewById(android.R.id.empty);
        mNoResults.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            mSuggestId = intent.getBooleanExtra(SUGGEST_ID, false);

            mObsIdInternal = intent.getIntExtra(OBSERVATION_ID_INTERNAL, -1);
            mObsId = intent.getIntExtra(OBSERVATION_ID, -1);
            mObservationJson = intent.getStringExtra(OBSERVATION_JSON);
        }

        mAdapter = new PinnedLocationAutoCompleteAdapter(getApplicationContext(), R.layout.taxon_result_item);
        final EditText autoCompView = (EditText) customView.findViewById(R.id.search_text);
        
        autoCompView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence s, int start, int before, int count) {
                if (mAdapter != null) mAdapter.getFilter().filter(s);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        String initialSearch = intent.getStringExtra(SPECIES_GUESS);
        mShowUnknown = intent.getBooleanExtra(SHOW_UNKNOWN, false);

        if ((initialSearch != null) && (initialSearch.trim().length() > 0)) {
        	autoCompView.setText(initialSearch);
        	autoCompView.setSelection(initialSearch.length());
            autoCompView.requestFocus();

            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(autoCompView, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        }

        setListAdapter(mAdapter);
        */
    }

    public void selectTaxon(JSONObject item) {
        try {
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            // TODO
            /*

            if (item != null) {
                if (item.optBoolean("is_custom", false)) {
                    // Custom named taxon
                    bundle.putString(PinnedLocationSearchActivity.ID_NAME, item.getString("name"));
                    bundle.putBoolean(PinnedLocationSearchActivity.IS_CUSTOM, true);
                } else {
                    bundle.putString(PinnedLocationSearchActivity.ID_NAME, getTaxonName(item));
                    bundle.putString(PinnedLocationSearchActivity.TAXON_NAME, item.getString("name"));
                    bundle.putString(PinnedLocationSearchActivity.ICONIC_TAXON_NAME, item.optString("iconic_taxon_name"));
                    if (item.has("default_photo") && !item.isNull("default_photo")) bundle.putString(PinnedLocationSearchActivity.ID_PIC_URL, item.getJSONObject("default_photo").getString("square_url"));
                    bundle.putBoolean(PinnedLocationSearchActivity.IS_CUSTOM, false);
                    bundle.putInt(PinnedLocationSearchActivity.TAXON_ID, item.getInt("id"));
                    bundle.putInt(PinnedLocationSearchActivity.RANK_LEVEL, item.getInt("rank_level"));
                    bundle.putString(PinnedLocationSearchActivity.RANK, item.getString("rank"));

                }
                bundle.putInt(PinnedLocationSearchActivity.FIELD_ID, mFieldId);

            } else {
                // Unknown taxon
                bundle.putInt(PinnedLocationSearchActivity.TAXON_ID, UNKNOWN_TAXON_ID);
            }

            intent.putExtras(bundle);

            setResult(RESULT_OK, intent);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAfterTransition();
            } else {
                finish();
            }
            */

        } catch (Exception e) {
            // TODO Auto-generated catch block
            Logger.tag(TAG).error(e);
        }
    }

    private boolean isNetworkAvailable() {
         ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
         NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
         return activeNetworkInfo != null && activeNetworkInfo.isConnected();
     }


    private String getTaxonName(JSONObject item) {
        return item.optString("preferred_common_name", item.optString("matched_term"));
    }


    private ListView mListView;

    protected ListView getListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(android.R.id.list);
        }
        return mListView;
    }

    protected void setListAdapter(ListAdapter adapter) {
        getListView().setAdapter(adapter);
    }

    protected ListAdapter getListAdapter() {
        ListAdapter adapter = getListView().getAdapter();
        if (adapter instanceof HeaderViewListAdapter) {
            return ((HeaderViewListAdapter)adapter).getWrappedAdapter();
        } else {
            return adapter;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // TODO

        /*
        if (requestCode == VIEW_TAXON_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon search directly back to the caller (e.g. suggestions screen)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            } else if (resultCode == TaxonActivity.RESULT_COMPARE_TAXON) {
                // User chose to compare this specific taxon
                compareTaxon(mLastTaxon);
            }
        }
        */
    }


    private void compareTaxon(JSONObject taxon) {
        // TODO
        /*
        Intent intent = new Intent(this, CompareSuggestionActivity.class);
        intent.putExtra(CompareSuggestionActivity.SUGGESTION_INDEX, 0);
        if (mObservationJson != null) intent.putExtra(CompareSuggestionActivity.OBSERVATION_JSON, mObservationJson);
        if (mObsIdInternal > -1) intent.putExtra(CompareSuggestionActivity.OBSERVATION_ID_INTERNAL, mObsIdInternal);
        if (mObsId > -1) intent.putExtra(CompareSuggestionActivity.OBSERVATION_ID, mObsId);
        JSONObject suggestion = new JSONObject();
        try {
            suggestion.put("taxon", taxon);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }
        ArrayList<BetterJSONObject> suggestions = new ArrayList<>();
        suggestions.add(new BetterJSONObject(suggestion));
        CompareSuggestionActivity.setTaxonSuggestions(suggestions);
        startActivityForResult(intent, VIEW_TAXON_REQUEST_CODE);
        */
    }

}
