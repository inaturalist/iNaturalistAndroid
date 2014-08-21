package org.inaturalist.shedd.android;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.inaturalist.shedd.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TaxonSearchActivity extends SherlockListActivity {
    private static final String LOG_TAG = "TaxonSearchActivity";
    
    public static final String TAXON_ID = "taxon_id";
	public static final String ID_NAME = "id_name";
	public static final String TAXON_NAME = "taxon_name";
	public static final String ICONIC_TAXON_NAME = "iconic_taxon_name";
    public static final String ID_PIC_URL = "id_url";
    public static final String FIELD_ID = "field_id";

    public static final String SPECIES_GUESS = "species_guess";
    
    
    private TaxonAutoCompleteAdapter mAdapter;

    private int mFieldId;

    private ProgressBar mProgress;


    private ArrayList<JSONObject> autocomplete(String input) {
        ArrayList<JSONObject> resultList = null;

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(INaturalistService.HOST + "/taxa/search.json");
            sb.append("?q=");
            sb.append(URLEncoder.encode(input, "utf8"));

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }
        } catch (MalformedURLException e) {
            Log.e(LOG_TAG, "Error processing Places API URL", e);
            return resultList;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error connecting to Places API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            JSONArray predsJsonArray = new JSONArray(jsonResults.toString());

            // Extract the Place descriptions from the results
            resultList = new ArrayList<JSONObject>(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                resultList.add(predsJsonArray.getJSONObject(i));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Cannot process JSON results", e);
        }

        return resultList;
    } 
    
    
    private class TaxonAutoCompleteAdapter extends ArrayAdapter<String> implements Filterable {
        private ArrayList<JSONObject> mResultList;
        private Context mContext;
        private String mCurrentSearchString;

        public TaxonAutoCompleteAdapter(Context context, int resourceId) {
            super(context, resourceId, new ArrayList<String>());
            
            mContext = context;
            
            mResultList = new ArrayList<JSONObject>();
        }

        @Override
        public int getCount() {
            return mResultList.size();
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
                        getListView().setVisibility(View.GONE);
                        mProgress.setVisibility(View.VISIBLE);
                    } else {
                        mProgress.setVisibility(View.GONE);
                        getListView().setVisibility(View.VISIBLE);
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
                            filterResults.count = results.size();
                        }
                    }
                    
                    toggleLoading(false);
                    
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        mResultList = (ArrayList<JSONObject>) results.values;
                        notifyDataSetChanged();
                    }
                    else {
                        if (results != null) {
                            mResultList = (ArrayList<JSONObject>) results.values;
                        }
                        
                        notifyDataSetInvalidated();
                    }
                }};
                
                return filter;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.taxon_result_item, parent, false); 
            JSONObject item = mResultList.get(position);
            JSONObject defaultName;
            String displayName;
            try {
                displayName = item.getString("unique_name");
            } catch (JSONException e2) {
                displayName = "unknown";
            }
            try {
                defaultName = item.getJSONObject("default_name");
                displayName = defaultName.getString("name");
            } catch (JSONException e1) {
                // alas
            }
            
            try {
                ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
                UrlImageViewHelper.setUrlDrawable(idPic, item.getString("image_url"));
                TextView idName = (TextView) view.findViewById(R.id.id_name);
                idName.setText(displayName);
                TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);
                idTaxonName.setText(item.getString("name"));
                idTaxonName.setTypeface(null, Typeface.ITALIC);
                view.setTag(item);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return view;
        }

    } 
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        
        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.taxon_search_action_bar, null);
        actionBar.setCustomView(customView);
       
        setContentView(R.layout.taxon_search);
        
        Intent intent = getIntent();
        mFieldId = intent.getIntExtra(FIELD_ID, 0);
        
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);
        
        mAdapter = new TaxonAutoCompleteAdapter(getApplicationContext(), R.layout.taxon_result_item);
        EditText autoCompView = (EditText) customView.findViewById(R.id.search_text);
        
        autoCompView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) mAdapter.getFilter().filter(s);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        String initialSearch = intent.getStringExtra(SPECIES_GUESS);
        
        if ((initialSearch != null) && (initialSearch.trim().length() > 0)) {
        	autoCompView.setText(initialSearch);
        	autoCompView.setSelection(initialSearch.length());
        } else {
        	autoCompView.setText("");
        }

        setListAdapter(mAdapter);
    }
    
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        JSONObject item = (JSONObject) v.getTag();
        try {
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putInt(TaxonSearchActivity.TAXON_ID, item.getInt("id"));
            bundle.putString(TaxonSearchActivity.ID_NAME, item.getString("unique_name"));
            bundle.putString(TaxonSearchActivity.TAXON_NAME, item.getString("name"));
            bundle.putString(TaxonSearchActivity.ICONIC_TAXON_NAME, item.getString("iconic_taxon_name"));
            bundle.putString(TaxonSearchActivity.ID_PIC_URL, item.getString("image_url"));
            bundle.putInt(TaxonSearchActivity.FIELD_ID, mFieldId);
            intent.putExtras(bundle);

            setResult(RESULT_OK, intent);
            finish();

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
 
}
