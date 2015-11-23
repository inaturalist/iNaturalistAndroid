package org.inaturalist.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.projection.MediaProjection;
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
import android.widget.TableLayout;
import android.widget.TextView;

public class ProjectSelectorActivity extends SherlockFragmentActivity implements OnItemClickListener {
    
    private static final String TAG = "INAT:ProjectSelectorActivity";
    public static final String PROJECT_IDS = "project_ids";
    public static final String IS_CONFIRMATION = "is_confirmation";

    private ImageButton mSaveButton;
    
    private TextView mLoadingProjects;
    private ListView mProjectList;
    private EditText mSearchText;
    
    private INaturalistApp mApp;
    private int mObservationId;
    private ArrayList<Integer> mObservationProjects;

    private ProjectReceiver mProjectReceiver;
    private boolean mIsConfirmation;
    private ProjectAdapter mAdapter;
    private ActivityHelper mHelper;
    private Hashtable<Integer, ProjectField> mProjectFields;
    private HashMap<Integer, ProjectFieldValue> mProjectFieldValues = null;

    private HashMap<Integer, List<ProjectFieldViewer>> mProjectFieldViewers;

    private class ProjectReceiver extends BroadcastReceiver {
        private ArrayList<JSONObject> mProjects;

        @Override
        public void onReceive(Context context, Intent intent) {
        	SerializableJSONArray serializableArray = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.PROJECTS_RESULT);
            JSONArray projectList = new JSONArray();
            
            if (serializableArray != null) {
            	projectList = serializableArray.getJSONArray();
            }

            List<Integer> projectIds = new ArrayList<Integer>();
            mProjects = new ArrayList<JSONObject>();

            unregisterReceiver(mProjectReceiver);

            for (int i = 0; i < projectList.length(); i++) {
                try {
                    mProjects.add(projectList.getJSONObject(i));
                    projectIds.add(projectList.getJSONObject(i).getInt("id"));
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


            ProjectFieldViewer.getProjectFields(ProjectSelectorActivity.this, projectIds, mObservationId, new ProjectFieldViewer.ProjectFieldsResults() {
                @Override
                public void onProjectFieldsResults(Hashtable<Integer, ProjectField> projectFields, HashMap<Integer, ProjectFieldValue> projectValues) {
                    mProjectFields = projectFields;

                    if (mProjectFieldValues == null) {
                        mProjectFieldValues = projectValues;
                    }
                }
            });

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

            case R.id.save_projects:
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putIntegerArrayList(PROJECT_IDS, mObservationProjects);
                intent.putExtras(bundle);

                setResult(RESULT_OK, intent);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mIsConfirmation) {
            MenuInflater inflater = getSupportMenuInflater();
            inflater.inflate(R.menu.project_selector_menu, menu);
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = new ActivityHelper(this);
        mProjectReceiver = new ProjectReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_JOINED_PROJECTS_RESULT);
        registerReceiver(mProjectReceiver, filter);

        mProjectFieldViewers = new HashMap<Integer, List<ProjectFieldViewer>>();

        final Intent intent = getIntent();
        setContentView(R.layout.project_selector);
        
        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mObservationId = (int) intent.getIntExtra(INaturalistService.OBSERVATION_ID, 0);
            mObservationProjects = intent.getIntegerArrayListExtra(INaturalistService.PROJECT_ID);
            mIsConfirmation = intent.getBooleanExtra(ProjectSelectorActivity.IS_CONFIRMATION, false);
        } else {
            mObservationId = (int) savedInstanceState.getInt(INaturalistService.OBSERVATION_ID, 0);
            mObservationProjects = savedInstanceState.getIntegerArrayList(INaturalistService.PROJECT_ID);
            mIsConfirmation = savedInstanceState.getBoolean(ProjectSelectorActivity.IS_CONFIRMATION);
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        if (!mIsConfirmation) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);

            LayoutInflater li = LayoutInflater.from(this);
            View customView = li.inflate(R.layout.project_selector_action_bar, null);
            actionBar.setCustomView(customView);
            actionBar.setLogo(R.drawable.up_icon);
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
        } else {
            actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.inatapptheme_color)));
            actionBar.setLogo(R.drawable.ic_arrow_back_white_24dp);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setTitle(Html.fromHtml("<font color=\"#ffffff\">" + getString(R.string.select_projects) + "</font>"));
        }


        mLoadingProjects = (TextView) findViewById(R.id.project_list_empty);
        mProjectList = (ListView) findViewById(R.id.project_list);
        
        mSearchText = (EditText) findViewById(R.id.search_filter);
        mSearchText.setHint(R.string.search_projects);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mProjectReceiver != null && mAdapter != null) {
                    mAdapter.getFilter().filter(s);
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
        outState.putBoolean(ProjectSelectorActivity.IS_CONFIRMATION, mIsConfirmation);

        for (List<ProjectFieldViewer> fields : mProjectFieldViewers.values()) {
            for (ProjectFieldViewer viewer : fields) {
                String value = viewer.getValue();
                ProjectFieldValue fieldValue = mProjectFieldValues.get(viewer.getField().field_id);
                if (fieldValue == null) {
                    fieldValue = new ProjectFieldValue();
                }
                fieldValue.value = value;
                mProjectFieldValues.put(viewer.getField().field_id, fieldValue);
            }
        }
        outState.putSerializable("mProjectFieldValues", mProjectFieldValues);

        super.onSaveInstanceState(outState);
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
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(mIsConfirmation ? R.layout.project_selector_confirmation_item : R.layout.project_selector_item, parent, false);
            BetterJSONObject item = new BetterJSONObject(mItems.get(position));

            TextView projectName = (TextView) view.findViewById(R.id.project_name);
            projectName.setText(item.getString("title"));

            TextView projectDescription = (TextView) view.findViewById(R.id.project_description);
            final String noHTMLDescription = Html.fromHtml(item.getString("description")).toString();
            if (!mIsConfirmation) {
                // Strip HTML tags
                projectDescription.setText(noHTMLDescription);
            }
            ImageView projectPic = (ImageView) view.findViewById(R.id.project_pic);
            String iconUrl = item.getString("icon_url");

            if ((iconUrl != null) && (iconUrl.length() > 0)) {
                projectPic.setVisibility(View.VISIBLE);
                if (mIsConfirmation) {
                    view.findViewById(R.id.project_pic_none).setVisibility(View.GONE);
                }
                UrlImageViewHelper.setUrlDrawable(projectPic, iconUrl);
            } else {
                projectPic.setVisibility(View.GONE);
                if (mIsConfirmation) {
                    view.findViewById(R.id.project_pic_none).setVisibility(View.VISIBLE);
                }
            }

            if (mIsConfirmation) {
                ((ViewGroup)view.findViewById(R.id.project_pic_container)).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mHelper.alert(noHTMLDescription);
                    }
                });
            }

            view.setTag(item);
            final int projectId = item.getInt("id");
            if (mObservationProjects.contains(Integer.valueOf(projectId))) {
                // Checked on
                if (mIsConfirmation) {
                    view.findViewById(R.id.project_selected_icon).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.project_unselected_icon).setVisibility(View.GONE);
                    view.setBackgroundColor(Color.parseColor("#f1f6e8"));

                    // Show the project fields
                    TableLayout projectFieldsTable = (TableLayout) view.findViewById(R.id.project_fields);
                    List<ProjectField> fields = ProjectFieldViewer.sortProjectFields(projectId, mProjectFields);

                    List<ProjectFieldViewer> viewers = new ArrayList<ProjectFieldViewer>();
                    mProjectFieldViewers.put(projectId, viewers);

                    if (fields.size() > 0) {
                        projectFieldsTable.setVisibility(View.VISIBLE);
                        ((ViewGroup)view.findViewById(R.id.project_top_container)).setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View currentView) {
                                mProjectList.performItemClick(
                                        view,
                                        position,
                                        projectId);
                            }
                        });
                    } else {
                        projectFieldsTable.setVisibility(View.GONE);
                    }

                    for (ProjectField field : fields) {
                        ProjectFieldValue fieldValue = mProjectFieldValues.get(field.field_id);
                        ProjectFieldViewer fieldViewer = new ProjectFieldViewer(ProjectSelectorActivity.this, field, fieldValue, true);

                        viewers.add(fieldViewer);

                        if (field.is_required) {
                            view.findViewById(R.id.is_required).setVisibility(View.VISIBLE);
                        }

                        projectFieldsTable.addView(fieldViewer.getView());
                    }

                } else {
                    ImageView projectSelected = (ImageView) view.findViewById(R.id.project_selected);
                    projectSelected.setImageResource(R.drawable.ic_action_accept);
                    projectName.setTypeface(Typeface.DEFAULT_BOLD);
                    projectDescription.setTypeface(Typeface.DEFAULT_BOLD);
                }
            } else {
                // Checked off
                if (mIsConfirmation) {
                    view.findViewById(R.id.project_selected_icon).setVisibility(View.GONE);
                    view.findViewById(R.id.project_unselected_icon).setVisibility(View.VISIBLE);
                    view.setBackgroundColor(Color.parseColor("#ffffff"));
                } else {
                    ImageView projectSelected = (ImageView) view.findViewById(R.id.project_selected);
                    projectSelected.setImageResource(android.R.color.transparent);
                    projectName.setTypeface(Typeface.DEFAULT);
                    projectDescription.setTypeface(Typeface.DEFAULT);
                }
            }

            return view;
        }
    }


    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int arg2, long arg3) {
        BetterJSONObject project = (BetterJSONObject) view.getTag();
        Integer projectId = Integer.valueOf(project.getInt("id"));
        
        if (mObservationProjects.contains(projectId)) {
            mObservationProjects.remove(projectId);
        } else {
            mObservationProjects.add(projectId);
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ProjectFieldViewer.PROJECT_FIELD_TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Notify the project fields that we returned from a taxon search
                for (List<ProjectFieldViewer> fields : mProjectFieldViewers.values()) {
                    for (ProjectFieldViewer viewer : fields) {
                        viewer.onTaxonSearchResult(data);
                    }
                }
            }
        }
    }

}
