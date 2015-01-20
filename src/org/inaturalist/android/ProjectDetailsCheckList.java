package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.SherlockFragment;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProjectDetailsCheckList extends SherlockFragment {
    public static String TAG = "ProjectDetailsCheckList";
    public static final String KEY_PROJECT = "project";
    private BetterJSONObject mProject;
    private ProgressBar mProgress;
    private TextView mProjectTaxaEmpty;
    private ListView mProjectTaxa;
    private CheckListReceiver mCheckListReceiver;
    private EditText mSearchText;
    private CheckListAdapter mAdapter;
    
    private class CheckListAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;
        private List<JSONObject> mOriginalItems;
        private Filter mFilter;

        public CheckListAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.taxon_item, objects);

            mItems = objects;
            mContext = context;
            mOriginalItems = new ArrayList<JSONObject>(mItems);
            
            mFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        String search = constraint.toString().toLowerCase();
                        ArrayList<JSONObject> results = new ArrayList<JSONObject>(mOriginalItems.size());
                        for (JSONObject item : mOriginalItems) {
                            try {
                                BetterJSONObject taxon = new BetterJSONObject(item.getJSONObject("taxon"));
                                if (taxon.getString("name").toLowerCase().indexOf(search) > -1) {
                                    results.add(item);
                                } else {
                                    BetterJSONObject defaultName = new BetterJSONObject(taxon.getJSONObject("default_name"));
                                    if (defaultName.getString("name").toLowerCase().indexOf(search) > -1) {
                                        results.add(item);
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } 

                        // Assign the data to the FilterResults
                        filterResults.values = results;
                        filterResults.count = results.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    mItems = (List<JSONObject>) results.values;
                    notifyDataSetChanged();
                }
            };
        }

        public void addItemAtBeginning(JSONObject newItem) {
            mItems.add(0, newItem);
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public JSONObject getItem(int index) {
            return mItems.get(index);
        }
        
        @Override
        public Filter getFilter() {
            return mFilter;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) { 
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.taxon_item, parent, false); 
            BetterJSONObject item = null;
            BetterJSONObject defaultName = null;
            
            if (!mItems.get(position).has("taxon")) {
            	// It's the generic/general add observation item
            	
                TextView idName = (TextView) view.findViewById(R.id.id_name);
                idName.setText(R.string.add_observation);

                TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
                taxonName.setVisibility(View.GONE);
                
                Button addObservation = (Button) view.findViewById(R.id.add_observation);
                addObservation.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, getActivity(), ObservationEditor.class);
                        intent.putExtra(ObservationEditor.OBSERVATION_PROJECT, mProject.getInt("id"));
                        startActivity(intent);
                    }
                });

            } else {
            	// Regular taxon item            
            
            	try {
            		item = new BetterJSONObject(mItems.get(position).getJSONObject("taxon"));
            		defaultName = new BetterJSONObject(item.getJSONObject("default_name"));
            	} catch (JSONException e) {
            		e.printStackTrace();
            		return view;
            	}

            	TextView idName = (TextView) view.findViewById(R.id.id_name);
            	idName.setText(defaultName.getString("name"));
            	TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
            	taxonName.setText(item.getString("name"));
            	taxonName.setTypeface(null, Typeface.ITALIC);
            	ImageView taxonPic = (ImageView) view.findViewById(R.id.taxon_pic);
            	UrlImageViewHelper.setUrlDrawable(taxonPic, item.getString("photo_url"));

            	Button addObservation = (Button) view.findViewById(R.id.add_observation);
            	final BetterJSONObject defaultName2 = defaultName;
            	addObservation.setOnClickListener(new OnClickListener() {
            		@Override
            		public void onClick(View v) {
            			BetterJSONObject item = (BetterJSONObject) view.getTag();
            			Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, getActivity(), ObservationEditor.class);
            			intent.putExtra(ObservationEditor.SPECIES_GUESS, String.format("%s (%s)", defaultName2.getString("name"), item.getString("name")));
                        intent.putExtra(ObservationEditor.OBSERVATION_PROJECT, mProject.getInt("id"));
            			startActivity(intent);
            		}
            	});

            	view.setTag(item);
            }

            return view;
        }
    }
    
    private class CheckListReceiver extends BroadcastReceiver {
        private ArrayList<JSONObject> mCheckList;

        @Override
        public void onReceive(Context context, Intent intent) {
            getActivity().unregisterReceiver(mCheckListReceiver);
            
            SerializableJSONArray checkListSerializable = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.CHECK_LIST_RESULT);
            JSONArray checkList = (checkListSerializable == null ? new SerializableJSONArray() : checkListSerializable).getJSONArray();
            mCheckList = new ArrayList<JSONObject>();
            
            mCheckList.add(new JSONObject()); // The generic/general add taxon item
            
            for (int i = 0; i < checkList.length(); i++) {
                try {
                    mCheckList.add(checkList.getJSONObject(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            mProgress.setVisibility(View.GONE);
            
            mProjectTaxaEmpty.setVisibility(View.GONE);
            mProjectTaxa.setVisibility(View.VISIBLE);
            mAdapter = new CheckListAdapter(getActivity(), mCheckList);
            mProjectTaxa.setAdapter(mAdapter);             
        }
    }
  
    
    @Override
    public void onPause() {
        super.onPause();
        
        try {
            if (mCheckListReceiver != null) {
                getActivity().unregisterReceiver(mCheckListReceiver);
            }
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.project_details_check_list, container, false);
        
        mCheckListReceiver = new CheckListReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_CHECK_LIST_RESULT);
        getActivity().registerReceiver(mCheckListReceiver, filter);  
        
        mProgress = (ProgressBar) v.findViewById(R.id.progress);
        mProjectTaxaEmpty = (TextView) v.findViewById(R.id.project_taxa_empty);
        mProjectTaxa = (ListView) v.findViewById(R.id.project_check_list);
        
        mProgress.setVisibility(View.VISIBLE);
        mProjectTaxa.setVisibility(View.GONE);
        mProjectTaxaEmpty.setVisibility(View.GONE);
        
        Bundle bundle = getArguments();
        
        if (bundle != null) {
            mProject = (BetterJSONObject) bundle.getSerializable(KEY_PROJECT);
        }
        
        mSearchText = (EditText) v.findViewById(R.id.search_filter);
        mSearchText.setHint(R.string.search_taxa);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) {
                    mAdapter.getFilter().filter(s);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        
        return v;
    }
}
