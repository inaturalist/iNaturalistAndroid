package org.inaturalist.android;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.inaturalist.android.BaseTab.ProjectsAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.SherlockFragment;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.app.Application;
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
import android.widget.Button;
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
    private Button mLogin;

    private static final int REQUEST_CODE_LOGIN = 0x1000;

    private class ProjectsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "GOT " + getFilterResultName());

            try {
                getActivity().unregisterReceiver(mProjectsReceiver);
            } catch (Exception exc) {
                exc.printStackTrace();
            }

            Boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            
            SerializableJSONArray serializableArray;
            if (!isSharedOnApp) {
            	serializableArray = ((SerializableJSONArray) intent.getSerializableExtra(getFilterResultParamName()));
            } else {
            	// Get results from app context
            	serializableArray = (SerializableJSONArray) mApp.getServiceResult(getFilterResultName());
            	mApp.setServiceResult(getFilterResultName(), null); // Clear data afterwards
            }
            
            if (serializableArray == null) {
            	mProjects = new ArrayList<JSONObject>();
            	loadProjectsIntoUI();
            	return;
            }

            JSONArray projects = serializableArray.getJSONArray();
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
            
            loadProjectsIntoUI();

        }
    }
    
    private void loadProjectsIntoUI() {
        mAdapter = new ProjectsAdapter(getActivity(), mProjects);
        mProjectList.setAdapter(mAdapter);

        mProjectList.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        
        if (mProjects.size() > 0) {
            mEmptyListLabel.setVisibility(View.GONE);
            mSearchText.setEnabled(true);
        } else {
            mEmptyListLabel.setVisibility(View.VISIBLE);
            mProjectList.setVisibility(View.GONE);

            if (!isNetworkAvailable()) {
            	// No projects due to no Internet connection
            	mEmptyListLabel.setText(getNoInternetText());
            } else if (requiresLogin() && !mApp.loggedIn()) {
            	// Required user login
            	mEmptyListLabel.setText(getUserLoginRequiredText());
                mLogin.setVisibility(View.VISIBLE);
            } else {
            	// No projects found
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
	protected INaturalistApp mApp; 	
    
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
    abstract protected void onItemSelected(BetterJSONObject item, int index);

    /** Returns the search filter EditText hint */
    abstract protected String getSearchFilterTextHint();

    /** Returns the text to display when no projects/guides are found */
    abstract protected String getNoItemsFoundText();

    /** Returns the text to display when no Internet connection is available */
    abstract protected String getNoInternetText();

    /** Whether or not the tab requires user login (e.g. for "Joined projects") */
    protected boolean requiresLogin() { return false; }

    /** Returns the text to display when a user login is required */
    protected String getUserLoginRequiredText() { return getResources().getString(R.string.please_sign_in); }
    
    /** Returns the URL to be used when searching for projects/guides */
    abstract protected String getSearchUrl();

    /** If true - in case the search filter returns no text, should re-call the original intent/action from
     * the iNat service class */
    abstract protected boolean recallServiceActionIfNoResults();

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
    
    /**
     * Updates an existing project (in memory)
     * @param index
     * @param project
     */
    protected void updateProject(int index, BetterJSONObject project) {
    	if (mAdapter != null) mAdapter.updateItem(index, project.getJSONObject());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: " + getActionName() + ":" + getClass().getName() + (mProjects != null ? mProjects.toString() : "null"));
        
        mProjects = null;
        mApp = (INaturalistApp) getActivity().getApplication();
        
        View v = inflater.inflate(R.layout.project_list, container, false);

        mLogin = (Button) v.findViewById(R.id.login);
        mLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(new Intent(getActivity(), OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), REQUEST_CODE_LOGIN);
            }
        });
        mLogin.setVisibility(View.GONE);
        
        mProjectList = (ListView) v.findViewById(android.R.id.list);
        mProjectList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int index, long arg3) {
                BetterJSONObject project = new BetterJSONObject(mAdapter.getItem(index));
                
                onItemSelected(project, index);
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


    private ArrayList<JSONObject> autocomplete(String input) {
    	// Retrieve the autocomplete results.
    	String search = input.toString().toLowerCase();

    	ArrayList<JSONObject> resultList = null;

    	HttpURLConnection conn = null;
    	StringBuilder jsonResults = new StringBuilder();
    	try {
    		StringBuilder sb = new StringBuilder(getSearchUrl());
    		sb.append("?q=");
    		sb.append(URLEncoder.encode(search, "utf8"));

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
    		Log.e(TAG, "Error processing searc API URL", e);
    	} catch (IOException e) {
    		Log.e(TAG, "Error connecting to search API", e);
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
    		Log.e(TAG, "Cannot process JSON results", e);
    	}

    	return resultList;
    }

    private void toggleLoading(final boolean isLoading) {
    	getActivity().runOnUiThread(new Runnable() {
    		@Override
    		public void run() {
    			if (isLoading) {
    				mProjectList.setVisibility(View.GONE);
    				mProgressBar.setVisibility(View.VISIBLE);
    			} else {
    				mProgressBar.setVisibility(View.GONE);
    				mProjectList.setVisibility(View.VISIBLE);
    			}
    		}
    	});
    }

    public class ProjectsAdapter extends ArrayAdapter<JSONObject> implements Filterable {

        private List<JSONObject> mItems;
        private List<JSONObject> mOriginalItems;
        private Context mContext;
        private Filter mFilter;
		protected String mCurrentSearchString;
        
        public void updateItem(int index, JSONObject object) {
        	mItems.set(index, object);
        }

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
                            if (results == null) {
                                results = new ArrayList<JSONObject>();
                            }

                            filterResults.values = results;
                            filterResults.count = results.size();

                        }

                        if ((((ArrayList<JSONObject>)filterResults.values).size() == 0) && (recallServiceActionIfNoResults())) {
                            // Re-call the service intent/action to retrieve some default results
                            getProjects();

                            return filterResults;
                        }
                    }
                    
                    toggleLoading(false);
                    
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        mItems = (List<JSONObject>) results.values;
                        notifyDataSetChanged();
                    } else {
                        if (results != null) {
                            mItems = (ArrayList<JSONObject>) results.values;
                        }
                        
                        notifyDataSetInvalidated();
                    }
                }
            };

        }

        public void addItemAtBeginning(JSONObject newItem) {
            mItems.add(0, newItem);
        }
        
        @Override
        public int getCount() {
            return (mItems != null ? mItems.size() : 0);
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
        	if (description == null) return "";
        	
            String noHTML = Html.fromHtml(description).toString();
            
            return noHTML;
        }
    }
    
    
    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
       
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // User logged-in - Refresh list
            mEmptyListLabel.setVisibility(View.GONE);
            mLogin.setVisibility(View.GONE);

            toggleLoading(true);
            getProjects();
        }
    }

    private void getProjects() {
        mProjectsReceiver = new ProjectsReceiver();
        IntentFilter filter = new IntentFilter(getFilterResultName());
        Log.i(TAG, "Registering " + getFilterResultName());
        getActivity().registerReceiver(mProjectsReceiver, filter);

        Log.i(TAG, "Re-Calling " + getActionName());
        Intent serviceIntent = new Intent(getActionName(), null, getActivity(), INaturalistService.class);
        getActivity().startService(serviceIntent);
    }

}
