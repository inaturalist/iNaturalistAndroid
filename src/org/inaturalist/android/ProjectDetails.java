package org.inaturalist.android;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.inaturalist.android.ProjectDetails.CheckListAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ProjectDetails extends Activity {    
    
    private ImageButton mBackButton;
    private Button mJoinLeaveProject;
    private TextView mProjectTitle;
    private TextView mProjectDetailsChecklist;
    private ImageView mProjectIcon;
    private TextView mProjectDescription;
    private ScrollView mProjectDescriptionContainer;
    private ListView mProjectTaxa;
    
    private INaturalistApp mApp;
    private BetterJSONObject mProject;

    private boolean mShowChecklist = false;
    private RelativeLayout mProjectChecklistContainer;
    private CheckListReceiver mCheckListReceiver;
    private TextView mProjectTaxaEmpty;

    private class CheckListReceiver extends BroadcastReceiver {
        private CheckListAdapter mAdapter;
        private ArrayList<JSONObject> mCheckList;

        @Override
        public void onReceive(Context context, Intent intent) {
            JSONArray checkList = ((SerializableJSONArray) intent.getSerializableExtra(INaturalistService.CHECK_LIST_RESULT)).getJSONArray();
            mCheckList = new ArrayList<JSONObject>();

            for (int i = 0; i < checkList.length(); i++) {
                try {
                    mCheckList.add(checkList.getJSONObject(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (checkList.length() > 0) {
                mProjectTaxaEmpty.setVisibility(View.GONE);
                mProjectTaxa.setVisibility(View.VISIBLE);
                
                mAdapter = new CheckListAdapter(ProjectDetails.this, mCheckList);
                mProjectTaxa.setAdapter(mAdapter);
                
            } else {
                mProjectTaxaEmpty.setText(R.string.no_check_list);
                mProjectTaxaEmpty.setVisibility(View.VISIBLE);
                mProjectTaxa.setVisibility(View.GONE);
            }
            
        }
    }

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mCheckListReceiver = new CheckListReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_CHECK_LIST_RESULT);
        registerReceiver(mCheckListReceiver, filter);  
        
        final Intent intent = getIntent();
        setContentView(R.layout.project_details);
        
        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mProject = (BetterJSONObject) intent.getSerializableExtra("project");
        } else {
            mProject = (BetterJSONObject) savedInstanceState.getSerializable("project");
        }

        mBackButton = (ImageButton) findViewById(R.id.back);
        mJoinLeaveProject = (Button) findViewById(R.id.join_leave_project);
        mProjectTitle = (TextView) findViewById(R.id.project_title);
        mProjectIcon = (ImageView) findViewById(R.id.project_pic);
        mProjectDescription = (TextView) findViewById(R.id.project_description);
        mProjectDescriptionContainer = (ScrollView) findViewById(R.id.project_description_container);
        mProjectDetailsChecklist = (TextView) findViewById(R.id.project_details_taxa);
        mProjectTaxaEmpty = (TextView) findViewById(R.id.project_taxa_empty);
        mProjectChecklistContainer = (RelativeLayout) findViewById(R.id.project_check_list_container);
        mProjectTaxa = (ListView) findViewById(R.id.project_check_list);
        
        mProjectDetailsChecklist.setPaintFlags(mProjectDetailsChecklist.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        mProjectDetailsChecklist.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mShowChecklist) {
                    mProjectDescriptionContainer.setVisibility(View.GONE);
                    mProjectChecklistContainer.setVisibility(View.VISIBLE);
                    mProjectDetailsChecklist.setText(R.string.project_details);
                } else {
                    mProjectDescriptionContainer.setVisibility(View.VISIBLE);
                    mProjectChecklistContainer.setVisibility(View.GONE);
                    mProjectDetailsChecklist.setText(R.string.project_taxa);
                }
                
                mShowChecklist = !mShowChecklist;
            }
        });
        
        mProjectChecklistContainer.setVisibility(View.GONE);
        
        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        mProjectTitle.setText(mProject.getString("title"));
        UrlImageViewHelper.setUrlDrawable(mProjectIcon, mProject.getString("icon_url"));
        String description = mProject.getString("description");
        description = description.replace("\n", "\n<br>");
        mProjectDescription.setText(Html.fromHtml(description));
        mProjectDescription.setMovementMethod(LinkMovementMethod.getInstance()); 
        
        Boolean isJoined = mProject.getBoolean("joined");
        if ((isJoined != null) && (isJoined == true)) {
            mJoinLeaveProject.setText(R.string.leave);
        } else {
            mJoinLeaveProject.setText(R.string.join);
        }
        
        mJoinLeaveProject.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean isJoined = mProject.getBoolean("joined");
                if ((isJoined != null) && (isJoined == true)) {
                    mJoinLeaveProject.setText(R.string.join);
                    mProject.put("joined", false);
                    
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_LEAVE_PROJECT, null, ProjectDetails.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
                    startService(serviceIntent);

                } else {
                    mJoinLeaveProject.setText(R.string.leave);
                    mProject.put("joined", true);
                    
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_JOIN_PROJECT, null, ProjectDetails.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
                    startService(serviceIntent);
                }

            }
        });
        
        try {
            // Get the project's taxa list
            int checkListId = mProject.getJSONObject("project_list").getInt("id");
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_CHECK_LIST, null, ProjectDetails.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.CHECK_LIST_ID, checkListId);
            startService(serviceIntent);  
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("project", mProject);
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

    public class CheckListAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;

        public CheckListAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.taxon_item, objects);

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
            final View view = inflater.inflate(R.layout.taxon_item, parent, false); 
            BetterJSONObject item = null;
            try {
                item = new BetterJSONObject(mItems.get(position).getJSONObject("taxon"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            TextView idName = (TextView) view.findViewById(R.id.id_name);
            idName.setText(item.getString("unique_name"));
            TextView taxonName = (TextView) view.findViewById(R.id.taxon_name);
            taxonName.setText(item.getString("name"));
            taxonName.setTypeface(null, Typeface.ITALIC);
            ImageView taxonPic = (ImageView) view.findViewById(R.id.taxon_pic);
            UrlImageViewHelper.setUrlDrawable(taxonPic, item.getString("photo_url"));
            
            Button addObservation = (Button) view.findViewById(R.id.add_observation);
            addObservation.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    BetterJSONObject item = (BetterJSONObject) view.getTag();
                    Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, ProjectDetails.this, ObservationEditor.class);
                    intent.putExtra(ObservationEditor.SPECIES_GUESS, String.format("%s (%s)", item.getString("name"), item.getString("unique_name")));
                    startActivity(intent);
                }
            });

            view.setTag(item);

            return view;
        }
    }


}
