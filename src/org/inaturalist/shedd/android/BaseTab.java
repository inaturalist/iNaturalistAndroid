package org.inaturalist.shedd.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.inaturalist.shedd.android.R;
import org.inaturalist.shedd.android.BaseTab.ProjectsAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.SherlockFragment;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public abstract class BaseTab extends SherlockFragment {

    private ProjectsAdapter mAdapter;
    private ArrayList<JSONObject> mProjects = null;

    private class ProjectsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "GOT " + getFilterResultName());
            
            getActivity().unregisterReceiver(mProjectsReceiver);
            
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

        mProgressBar.setVisibility(View.GONE);
        
        if (mProjects.size() > 0) {
            mEmptyListLabel.setVisibility(View.GONE);
            mSearchText.setEnabled(true);
        } else {
            mEmptyListLabel.setVisibility(View.VISIBLE);
            
            if (!isNetworkAvailable()) {
            	// No projects due to no Internet connection
            	mEmptyListLabel.setText(getNoInternetText());
            } else {
            	mEmptyListLabel.setText(getNoItemsFoundText());
            }

            mSearchText.setEnabled(false);
        }       
    }
    
    private boolean isNetworkAvailable() {
    	ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
    	NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    	return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    } 

    private static final String TAG = "INAT";

    private ListView mProjectList;
    private ProjectsReceiver mProjectsReceiver;
    private TextView mEmptyListLabel;
    private EditText mSearchText;
    private ProgressBar mProgressBar; 	
    
    /*
     * Methods that should be overriden by subclasses
     */
    
    /** What action name should be used when communicating with the iNat service (e.g. ACTION_GET_JOINED_PROJECTS) */
    abstract protected String getActionName();
    
    /** What filter result name should be used when communicating with the iNat service (e.g. ACTION_PROJECTS_RESULT) */
    abstract protected String getFilterResultName();
    
    /** What result param name should be used when communicating with the iNat service (e.g. PROJECTS_RESULT) */
    abstract protected String getFilterResultParamName();

    /** When an item (project/guide) is clicked */
    abstract protected void onItemSelected(BetterJSONObject item);

    /** Returns the search filter EditText hint */
    abstract protected String getSearchFilterTextHint();

    /** Returns the text to display when no projects/guides are found */
    abstract protected String getNoItemsFoundText();

    /** Returns the text to display when no Internet connection is available */
    abstract protected String getNoInternetText();

    @Override
    public void onPause() {
        super.onPause();

        try {
            if (mProjectsReceiver != null) {
                Log.i(TAG, "unregisterReceiver " + getFilterResultName());
                getActivity().unregisterReceiver(mProjectsReceiver);
            }
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "onCreate - " + getActionName() + ":" + getClass().getName());
        

    }
    
    @Override
    public void onResume() {
        mProjectsReceiver = new ProjectsReceiver();
        IntentFilter filter = new IntentFilter(getFilterResultName());
        Log.i(TAG, "Registering " + getFilterResultName());
        getActivity().registerReceiver(mProjectsReceiver, filter);  
        
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: " + getActionName() + ":" + getClass().getName() + (mProjects != null ? mProjects.toString() : "null"));
        
        mProjects = null;
        
        View v = inflater.inflate(R.layout.project_list, container, false);
        
        mProjectList = (ListView) v.findViewById(android.R.id.list);
        mProjectList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                BetterJSONObject project = (BetterJSONObject) arg1.getTag();
                
                onItemSelected(project);
            }
        });
        
        
        mProgressBar = (ProgressBar) v.findViewById(R.id.progress);
        
        mEmptyListLabel = (TextView) v.findViewById(android.R.id.empty);
        mEmptyListLabel.setVisibility(View.GONE);
        
        mSearchText = (EditText) v.findViewById(R.id.search_filter);
        mSearchText.setHint(getSearchFilterTextHint());
        mSearchText.setEnabled(false);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) mAdapter.getFilter().filter(s);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        
        
        // Hide keyboard
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN); 
        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0); 
        
        if (mProjects == null) {
            // Get the user's projects
            Log.i(TAG, "Calling " + getActionName());
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
            ImageView projectPic = (ImageView) view.findViewById(R.id.project_pic);
            String iconUrl = item.getString("icon_url");
            if ((iconUrl == null) || (iconUrl.length() == 0)) {
                projectPic.setVisibility(View.GONE);
            } else {
                projectPic.setVisibility(View.VISIBLE);
                UrlImageViewHelper.setUrlDrawable(projectPic, iconUrl);
            }
            TextView projectDescription = (TextView) view.findViewById(R.id.project_description);
            projectDescription.setText(getShortDescription(item.getString("description")));
            
            view.setTag(item);

            return view;
        }
        
        private String getShortDescription(String description) {
            // Strip HTML tags
            String noHTML = Html.fromHtml(description).toString();
            
            return noHTML;
        }
    }
    
    
    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
       
        super.onStop();
    }

}
