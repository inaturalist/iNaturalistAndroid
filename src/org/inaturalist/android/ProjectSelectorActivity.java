package org.inaturalist.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.inaturalist.android.ProjectSelectorActivity.ProjectAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ProjectSelectorActivity extends SherlockActivity implements OnItemClickListener {    
    
    private static final String TAG = "INAT:ProjectSelectorActivity";
    public static final String PROJECT_IDS = "project_ids";
    
    private ImageButton mSaveButton;
    
    private TextView mLoadingProjects;
    private ListView mProjectList;
    
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
    }

    public class ProjectAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;

        public ProjectAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.project_selector_item, objects);

            mItems = objects;
            mContext = context;
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
