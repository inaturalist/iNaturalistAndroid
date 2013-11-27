package org.inaturalist.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.inaturalist.android.BaseProjectsTab.ProjectsAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public abstract class BaseProjectsTab extends Fragment {

    private ProjectsAdapter mAdapter;
    private ArrayList<JSONObject> mProjects = null;

    private class ProjectsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            JSONArray projects = ((SerializableJSONArray) intent.getSerializableExtra(getFilterResultParamName())).getJSONArray();
            mProjects = new ArrayList<JSONObject>();
            
            if (projects == null) {
                loadProjectsIntoUI();
                return;
            }
            
            for (int i = 0; i < projects.length(); i++) {
                try {
                    mProjects.add(projects.getJSONObject(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            
            Collections.sort(mProjects, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject lhs, JSONObject rhs) {
                    try {
                        return lhs.getString("title").compareTo(rhs.getString("title"));
                    } catch (JSONException e) {
                        return 0;
                    }
                }
            });
 
            
            
            loadProjectsIntoUI();

        }
    }
    
    private void loadProjectsIntoUI() {
        mAdapter = new ProjectsAdapter(getActivity(), mProjects);
        mProjectList.setAdapter(mAdapter);

        if (mProjects.size() > 0) {
            mEmptyListLabel.setVisibility(View.GONE);
            mSearchText.setEnabled(true);
        } else {
            mEmptyListLabel.setText(R.string.no_projects);
            mSearchText.setEnabled(false);
        }       
    }

    private static final String TAG = "INAT";

    private ListView mProjectList;
    private ProjectsReceiver mProjectsReceiver;
    private TextView mEmptyListLabel;
    private EditText mSearchText; 	
    
    /*
     * Methods that should be overriden by subclasses
     */
    
    /** What action name should be used when communicating with the iNat service (e.g. ACTION_GET_JOINED_PROJECTS) */
    abstract protected String getActionName();
    
    /** What filter result name should be used when communicating with the iNat service (e.g. ACTION_PROJECTS_RESULT) */
    abstract protected String getFilterResultName();
    
    /** What result param name should be used when communicating with the iNat service (e.g. PROJECTS_RESULT) */
    abstract protected String getFilterResultParamName();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "onCreate");
        
        mProjectsReceiver = new ProjectsReceiver();
        IntentFilter filter = new IntentFilter(getFilterResultName());
        Log.i(TAG, "Registering " + getFilterResultName());
        getActivity().registerReceiver(mProjectsReceiver, filter);  
        

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: " + (mProjects != null ? mProjects.toString() : "null"));
        
        mProjects = null;
        
        View v = inflater.inflate(R.layout.project_list, container, false);
        
        mProjectList = (ListView) v.findViewById(android.R.id.list);
        mProjectList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                BetterJSONObject project = (BetterJSONObject) arg1.getTag();
                
                // Show project details
                Intent intent = new Intent(getActivity(), ProjectDetails.class);
                intent.putExtra("project", project);
                startActivity(intent);  
            }
        });
        
        mEmptyListLabel = (TextView) v.findViewById(android.R.id.empty);
        mEmptyListLabel.setText(R.string.loading_projects);
        
        mSearchText = (EditText) v.findViewById(R.id.search_filter);
        mSearchText.setEnabled(false);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.getFilter().filter(s);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        
        if (mProjects == null) {
            // Get the user's projects
            Intent serviceIntent = new Intent(getActionName(), null, getActivity(), INaturalistService.class);
            getActivity().startService(serviceIntent);  
        } else {
            // Load previously downloaded projects
            Log.i(TAG, "Previosly loaded projects: " + mProjects.toString());
            loadProjectsIntoUI();
        }
        
        return v;
    }

    public class ProjectsAdapter extends ArrayAdapter<JSONObject> implements Filterable {

        private List<JSONObject> mItems;
        private List<JSONObject> mOriginalItems;
        private Context mContext;
        private Filter mFilter;

        public ProjectsAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.project_item, objects);

            mItems = objects;
            mOriginalItems = new ArrayList<JSONObject>(mItems);
            mContext = context;
            
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
                                if (item.getString("title").toLowerCase().indexOf(search) > -1) {
                                    results.add(item);
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
            View view = inflater.inflate(R.layout.project_item, parent, false); 
            final BetterJSONObject item = new BetterJSONObject(mItems.get(position));

            TextView projectName = (TextView) view.findViewById(R.id.project_name);
            projectName.setText(item.getString("title"));
            ImageView userPic = (ImageView) view.findViewById(R.id.project_pic);
            UrlImageViewHelper.setUrlDrawable(userPic, item.getString("icon_url"));
            
            view.setTag(item);

            return view;
        }
    }
    
    
    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
        
        try {
            //getActivity().unregisterReceiver(mProjectsReceiver);  
        } catch (Exception exc) {
            // In case the receiver wasn't registered
            exc.printStackTrace();
        }
        
        super.onStop();
    }

}
