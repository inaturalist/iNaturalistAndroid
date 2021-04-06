package org.inaturalist.android;

import java.io.Serializable;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.Settings;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public abstract class BaseTab extends Fragment implements ProjectsAdapter.OnLoading, INaturalistApp.OnLocationStatus {

    private ProjectsAdapter mAdapter;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mProjects = null;
    private Button mLogin;
    private Button mSettings;

    private boolean mLoadingProjects = false;

    private static final int REQUEST_CODE_LOGIN = 0x1000;
    private ActivityHelper mHelper;
    private Context mContext;

    private class ProjectsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.tag(TAG).info("GOT " + getFilterResultName());

            BaseFragmentActivity.safeUnregisterReceiver(mProjectsReceiver, getActivity());

            Boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            
            SerializableJSONArray serializableArray;
            if (!isSharedOnApp) {
                Serializable sarr = intent.getSerializableExtra(getFilterResultParamName());
                if (sarr instanceof SerializableJSONArray) {
                    serializableArray = (SerializableJSONArray) sarr;
                } else {
                    Logger.tag(TAG).error("Got invalid non array convertible response from server: " + sarr);
                    serializableArray = null;
                }
            } else {
            	// Get results from app context
            	serializableArray = (SerializableJSONArray) mApp.getServiceResult(getFilterResultName());
            	mApp.setServiceResult(getFilterResultName(), null); // Clear data afterwards
            }
            Logger.tag(TAG).debug("Response for: " + getFilterResultName() + ":" + serializableArray);

            if (serializableArray == null) {
            	mProjects = new ArrayList<JSONObject>();
            	loadProjectsIntoUI();
            	return;
            }

            JSONArray projects = serializableArray.getJSONArray();
            Logger.tag(TAG).debug("Response (2) for: " + getFilterResultName() + ":" + projects);
            mProjects = new ArrayList<JSONObject>();
            
            if (projects == null) {
                loadProjectsIntoUI();
                return;
            }

            for (int i = 0; i < projects.length(); i++) {
                try {
                    mProjects.add(projects.getJSONObject(i));
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }

            loadProjectsIntoUI();

        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    private void loadProjectsIntoUI() {
        Logger.tag(TAG).debug("loadProjectsIntoUI 1");
        if (mContext ==  null) return;
        Logger.tag(TAG).debug("loadProjectsIntoUI 2 - " + mProjects);
        if (mProjects != null) Logger.tag(TAG).debug("loadProjectsIntoUI 3 - " + mProjects.size());

        mAdapter = new ProjectsAdapter(mContext, null, this, mProjects, getDefaultIcon());
        mProjectList.setAdapter(mAdapter);

        mProjectList.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        
        if (mProjects.size() > 0) {
            mEmptyListLabel.setVisibility(View.GONE);
            mSearchText.setEnabled(true);
        } else {
            mEmptyListLabel.setVisibility(View.VISIBLE);
            mProjectList.setVisibility(View.GONE);

            if (!mApp.isNetworkAvailable()) {
            	// No projects due to no Internet connection
            	mEmptyListLabel.setText(getNoInternetText());
            } else if (requiresLocation() && (!mApp.isLocationPermissionGranted() || !mApp.isLocationEnabled(this))) {
            	// No projects due to no place services enabled
            	mEmptyListLabel.setText(getLocationRequiredText());
                mSettings.setVisibility(View.VISIBLE);
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

    private static final String TAG = "INAT";

    private ListView mProjectList;
    private ProjectsReceiver mProjectsReceiver;
    private TextView mEmptyListLabel;
    private EditText mSearchText;
    private ProgressBar mProgressBar;
	protected INaturalistApp mApp;
	private boolean mAskedForLocationPermission = false;

    
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

    /** Returns the text to display when no projects/guides are found */
    abstract protected String getNoItemsFoundText();

    /** Returns the text to display when no Internet connection is available */
    abstract protected String getNoInternetText();

    /** Returns the text to display when no place services are available */
    protected String getLocationRequiredText() { return getResources().getString(R.string.please_enable_location_services); }

    /** Whether or not the tab requires user login (e.g. for "Joined projects") */
    protected boolean requiresLogin() { return false; }

    /** Whether or not the tab requires place services (e.g. "Nearby Projects") */
    protected boolean requiresLocation() { return false; }

    /** Returns the text to display when a user login is required */
    protected String getUserLoginRequiredText() { return getResources().getString(R.string.please_sign_in); }
    
    /** If true - in case the search filter returns no text, should re-call the original intent/action from
     * the iNat service class */
    abstract protected boolean recallServiceActionIfNoResults();

    /** The default icon used for project items */
    protected int getDefaultIcon() { return R.drawable.ic_work_black_24dp; }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    public void onPause() {
        super.onPause();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        Logger.tag(TAG).info("onCreate - " + getActionName() + ":" + getClass().getName());

        if (savedInstanceState == null) {
            mProjects = null;
        }
    }
    
    @Override
    public void onResume() {
        mProjectsReceiver = new ProjectsReceiver();
        IntentFilter filter = new IntentFilter(getFilterResultName());
        Logger.tag(TAG).info("Registering " + getFilterResultName());
        BaseFragmentActivity.safeRegisterReceiver(mProjectsReceiver, filter, getActivity());
        
        super.onResume();
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mContext = null;
        BaseFragmentActivity.safeUnregisterReceiver(mProjectsReceiver, getActivity());
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
        Logger.tag(TAG).info("onCreateView: " + getActionName() + ":" + getClass().getName() + (mProjects != null ? mProjects.toString() : "null"));
        
        mApp = (INaturalistApp) getActivity().getApplication();
        mHelper = new ActivityHelper(getActivity());

        View v = inflater.inflate(R.layout.project_list, container, false);

        mSettings = (Button) v.findViewById(R.id.settings);
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(myIntent);
            }
        });

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
        mSearchText.setVisibility(View.GONE);

        return v;
    }

    public void loadProjects() {
        if (mLoadingProjects) return;

        mLoadingProjects = true;

        if (mProjects == null) {
            // Get the user's projects
            if (requiresLocation() && !mApp.isLocationPermissionGranted()) {
                mEmptyListLabel.setText(getLocationRequiredText());
                mSettings.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                mEmptyListLabel.setVisibility(View.VISIBLE);
                mProjectList.setVisibility(View.GONE);


                if (!mAskedForLocationPermission) {
                    mAskedForLocationPermission = true;

                    mApp.requestLocationPermission(getActivity(), new INaturalistApp.OnRequestPermissionResult() {
                        @Override
                        public void onPermissionGranted() {
                            Logger.tag(TAG).info("Calling " + getActionName());
                            Intent serviceIntent = new Intent(getActionName(), null, getActivity(), INaturalistService.class);
                            ContextCompat.startForegroundService(getActivity(), serviceIntent);

                            mEmptyListLabel.setVisibility(View.GONE);
                            mSettings.setVisibility(View.GONE);
                            mProgressBar.setVisibility(View.VISIBLE);
                            mProjectList.setVisibility(View.GONE);
                        }

                        @Override
                        public void onPermissionDenied() {

                        }
                    });
                }
            } else if (getActivity() != null){
                Logger.tag(TAG).info("Calling " + getActionName());
                Intent serviceIntent = new Intent(getActionName(), null, getActivity(), INaturalistService.class);
                ContextCompat.startForegroundService(getActivity(), serviceIntent);
            }
        } else {
            // Load previously downloaded projects
            Logger.tag(TAG).info("Previously loaded projects: " + mProjects.toString());
            loadProjectsIntoUI();
        }
    }


    private void toggleLoading(final boolean isLoading, int count) {
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


    
    @Override
    public void onStop() {
        Logger.tag(TAG).info("onStop");
       
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // User logged-in - Refresh list
            refresh();
        }
    }

    public void refresh() {
        mEmptyListLabel.setVisibility(View.GONE);
        mLogin.setVisibility(View.GONE);

        toggleLoading(true, 0);
        getProjects();
    }

    private void getProjects() {
        BaseFragmentActivity.safeUnregisterReceiver(mProjectsReceiver, getActivity());

        mProjectsReceiver = new ProjectsReceiver();
        IntentFilter filter = new IntentFilter(getFilterResultName());
        Logger.tag(TAG).info("Registering " + getFilterResultName());
        BaseFragmentActivity.safeRegisterReceiver(mProjectsReceiver, filter, getActivity());

        Logger.tag(TAG).info("Re-Calling " + getActionName());
        Intent serviceIntent = new Intent(getActionName(), null, getActivity(), INaturalistService.class);
        ContextCompat.startForegroundService(getActivity(), serviceIntent);
    }

    @Override
    public void onLoading(Boolean loading, int count) {
        toggleLoading(loading, count);
    }


    @Override
    public void onLocationStatus(boolean isEnabled) {
        if (!isEnabled) {
            // No projects due to no place services enabled
            mEmptyListLabel.setText(getLocationRequiredText());
            mSettings.setVisibility(View.VISIBLE);
        }
    }
}
