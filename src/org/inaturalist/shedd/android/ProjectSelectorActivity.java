package org.inaturalist.shedd.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.inaturalist.shedd.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ProjectSelectorActivity extends SherlockActivity implements OnItemClickListener {    
    
    private static final String TAG = "INAT:ProjectSelectorActivity";
    public static final String PROJECT_IDS = "project_ids";
    
    private ImageButton mSaveButton;
    
    private TextView mLoadingProjects;
    private ListView mProjectList;
    private EditText mSearchText;
    
    private INaturalistApp mApp;
    private int mObservationId;
    private ArrayList<Integer> mObservationProjects;

    private ProjectReceiver mProjectReceiver;

    private class ProjectReceiver extends BroadcastReceiver {
        private ProjectAdapter mAdapter;
        private ArrayList<JSONObject> mProjects;

        @Override
        public void onReceive(Context context, Intent intent) {
            JSONArray projectList = ((SerializableJSONArray) intent.getSerializableExtra(INaturalistService.PROJECTS_RESULT)).getJSONArray();
            mProjects = new ArrayList<JSONObject>();

            unregisterReceiver(mProjectReceiver);

            for (int i = 0; i < projectList.length(); i++) {
                try {
                    mProjects.add(projectList.getJSONObject(i));
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

            if (projectList.length() > 0) {
                mLoadingProjects.setVisibility(View.GONE);
                mProjectList.setVisibility(View.VISIBLE);
                
                mAdapter = new ProjectAdapter(ProjectSelectorActivity.this, mProjects);
                mProjectList.setAdapter(mAdapter);
                mProjectList.setOnItemClickListener(ProjectSelectorActivity.this);
                
            } else {
                mLoadingProjects.setText(R.string.no_projects);
                mLoadingProjects.setVisibility(View.VISIBLE);
                mProjectList.setVisibility(View.GONE);
            }
            
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        
        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.project_selector_action_bar, null);
        actionBar.setCustomView(customView);

 
        mProjectReceiver = new ProjectReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_JOINED_PROJECTS_RESULT);
        registerReceiver(mProjectReceiver, filter);  
        
        final Intent intent = getIntent();
        setContentView(R.layout.project_selector);
        
        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mObservationId = (int) intent.getIntExtra(INaturalistService.OBSERVATION_ID, 0);
            mObservationProjects = intent.getIntegerArrayListExtra(INaturalistService.PROJECT_ID);
        } else {
            mObservationId = (int) savedInstanceState.getInt(INaturalistService.OBSERVATION_ID, 0);
            mObservationProjects = savedInstanceState.getIntegerArrayList(INaturalistService.PROJECT_ID);
        }

        mSaveButton = (ImageButton) customView.findViewById(R.id.save);
        
        mSaveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putIntegerArrayList(PROJECT_IDS, mObservationProjects);
                intent.putExtras(bundle); 
                
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        
        mLoadingProjects = (TextView) findViewById(R.id.project_list_empty);
        mProjectList = (ListView) findViewById(R.id.project_list);
        
        mSearchText = (EditText) findViewById(R.id.search_filter);
        mSearchText.setHint(R.string.search_projects);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mProjectReceiver != null && mProjectReceiver.mAdapter != null) {
                    mProjectReceiver.mAdapter.getFilter().filter(s);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });
        
        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_JOINED_PROJECTS, null, ProjectSelectorActivity.this, INaturalistService.class);
        startService(serviceIntent);  
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(INaturalistService.OBSERVATION_ID, mObservationId);
        outState.putIntegerArrayList(INaturalistService.PROJECT_ID, mObservationProjects);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        if (mProjectReceiver != null) {
            try {
                unregisterReceiver(mProjectReceiver);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
    }

    public class ProjectAdapter extends ArrayAdapter<JSONObject> implements Filterable {

        private List<JSONObject> mItems;
        private List<JSONObject> mOriginalItems;
        private Context mContext;
        private Filter mFilter;

        public ProjectAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.project_selector_item, objects);

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
            View view = inflater.inflate(R.layout.project_selector_item, parent, false); 
            BetterJSONObject item = new BetterJSONObject(mItems.get(position));

            TextView projectName = (TextView) view.findViewById(R.id.project_name);
            projectName.setText(item.getString("title"));
            TextView projectDescription = (TextView) view.findViewById(R.id.project_description);
            // Strip HTML tags
            String noHTML = Html.fromHtml(item.getString("description")).toString();
            projectDescription.setText(noHTML);
            ImageView userPic = (ImageView) view.findViewById(R.id.project_pic);
            UrlImageViewHelper.setUrlDrawable(userPic, item.getString("icon_url"));
            
            ImageView projectSelected = (ImageView) view.findViewById(R.id.project_selected);
            
            int projectId = item.getInt("id");
            if (mObservationProjects.contains(Integer.valueOf(projectId))) {
                projectSelected.setImageResource(R.drawable.ic_action_accept);
                projectName.setTypeface(Typeface.DEFAULT_BOLD);
                projectDescription.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                projectSelected.setImageResource(android.R.color.transparent);
                projectName.setTypeface(Typeface.DEFAULT);
                projectDescription.setTypeface(Typeface.DEFAULT);
            }
            
            view.setTag(item);

            return view;
        }
    }


    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int arg2, long arg3) {
        BetterJSONObject project = (BetterJSONObject) view.getTag();
        Integer projectId = Integer.valueOf(project.getInt("id"));
        
        TextView projectDescription = (TextView) view.findViewById(R.id.project_description);
        ImageView projectSelected = (ImageView) view.findViewById(R.id.project_selected);
        TextView projectName = (TextView) view.findViewById(R.id.project_name);
        
        if (mObservationProjects.contains(projectId)) {
            mObservationProjects.remove(projectId);
            projectSelected.setImageResource(android.R.color.transparent);
            projectName.setTypeface(Typeface.DEFAULT);
            projectDescription.setTypeface(Typeface.DEFAULT);
        } else {
            mObservationProjects.add(projectId);
            projectSelected.setImageResource(R.drawable.ic_action_accept);
            projectName.setTypeface(Typeface.DEFAULT_BOLD);
            projectDescription.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }


}
