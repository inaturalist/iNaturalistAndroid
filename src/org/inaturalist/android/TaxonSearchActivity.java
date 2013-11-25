package org.inaturalist.android;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class TaxonSearchActivity extends ListActivity {
    private static final String LOG_TAG = "TaxonSearchActivity";
    
    public static final String TAXON_ID = "taxon_id";
	public static final String ID_NAME = "id_name";
	public static final String TAXON_NAME = "taxon_name";
    public static final String ID_PIC_URL = "id_url";
    public static final String FIELD_ID = "field_id";
    
    private TaxonAutoCompleteAdapter mAdapter;

    private int mFieldId;


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
        private ArrayList<JSONObject> resultList;
        private Context mContext;

        public TaxonAutoCompleteAdapter(Context context, int resourceId) {
            super(context, resourceId, new ArrayList<String>());
            
            mContext = context;
            
            resultList = new ArrayList<JSONObject>();
        }

        @Override
        public int getCount() {
            return resultList.size();
        }

        @Override
        public String getItem(int index) {
            try {
                return resultList.get(index).getString("iconic_taxon_name");
            } catch (JSONException e) {
                return "";
            }
        }

        @Override
        public Filter getFilter() {
            Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        resultList = autocomplete(constraint.toString());

                        // Assign the data to the FilterResults
                        filterResults.values = resultList;
                        filterResults.count = resultList.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged();
                    }
                    else {
                        notifyDataSetInvalidated();
                    }
                }};
                
                return filter;
        }
        
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.taxon_result_item, parent, false); 
            JSONObject item = resultList.get(position);
            
            try {
                ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
                UrlImageViewHelper.setUrlDrawable(idPic, item.getString("image_url"));
                TextView idName = (TextView) view.findViewById(R.id.id_name);
                idName.setText(item.getString("name"));
                TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);
                idTaxonName.setText(item.getString("iconic_taxon_name"));
                view.setTag(item);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return view;
        }

    } 
    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.taxon_search);
        
        Intent intent = getIntent();
        mFieldId = intent.getIntExtra(FIELD_ID, 0);
        
        ImageButton backButton = (ImageButton) findViewById(R.id.back);
        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        mAdapter = new TaxonAutoCompleteAdapter(getApplicationContext(), R.layout.taxon_result_item);
        AutoCompleteTextView autoCompView = (AutoCompleteTextView) findViewById(R.id.search_text);
        autoCompView.setAdapter(mAdapter);
        autoCompView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int arg2, long arg3) {
                // TODO Auto-generated method stub
                JSONObject item = (JSONObject) view.getTag();
                try {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putInt(TaxonSearchActivity.TAXON_ID, item.getInt("id"));
                    bundle.putString(TaxonSearchActivity.ID_NAME, item.getString("unique_name"));
                    bundle.putString(TaxonSearchActivity.TAXON_NAME, item.getString("name"));
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
        });

        //setListAdapter(mAdapter);
    }
}
