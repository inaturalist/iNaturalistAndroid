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

import com.evernote.android.state.State;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
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
import android.widget.Toast;

public class ProjectSelectorActivity extends AppCompatActivity implements OnItemClickListener {
    
    private static final String TAG = "INAT:ProjectSelectorAct";
    public static final String PROJECT_IDS = "project_ids";
    public static final String PROJECT_FIELDS = "project_fields";
    public static final String IS_CONFIRMATION = "is_confirmation";
    public static final String UMBRELLA_PROJECT_IDs = "umbrella_project_ids";

    private ImageButton mSaveButton;
    
    private TextView mLoadingProjects;
    private ListView mProjectList;
    private EditText mSearchText;
    
    private INaturalistApp mApp;
    @State public int mObservationId;
    @State public ArrayList<Integer> mObservationProjects;

    private ProjectReceiver mProjectReceiver;
    @State public boolean mIsConfirmation;
    private ProjectAdapter mAdapter;
    private ActivityHelper mHelper;
    private ArrayList mProjectFields;
    @State(AndroidStateBundlers.SerializableBundler.class) public HashMap<Integer, ProjectFieldValue> mProjectFieldValues = null;

    private HashMap<Integer, List<ProjectFieldViewer>> mProjectFieldViewers;

    @State public boolean mShownSearchBox = true;

    private int mLastProjectFieldFocused = -1;
    private int mLastProjectIdFocused = -1;
    private int mLastProjectFieldIndex;
    private int mLastProjectFieldTop;
    @State public ArrayList<Integer> mUmbrellaProjects;

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

            // Separate into sub lists - "regular" projects and umbrella/collection projects
            ArrayList<JSONObject> regularProjects = new ArrayList<>();
            ArrayList<JSONObject> collectionProjects = new ArrayList<>();

            for (JSONObject project : mProjects) {
                String projectType = project.optString("project_type", "");
                if ((projectType.equals(Project.PROJECT_TYPE_COLLECTION)) || (projectType.equals(Project.PROJECT_TYPE_UMBRELLA))) {
                    collectionProjects.add(project);
                } else {
                    regularProjects.add(project);
                }
            }

            // Create a final list, first regular projects, a separator/header and then collection/umbrella projects
            mProjects = regularProjects;
            try {
                mProjects.add(new JSONObject(String.format("{ \"is_header\": true, \"title\": \"%s\" }", getString(R.string.collection_and_umbrella_projects))));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mProjects.addAll(collectionProjects);


            if (projectList.length() > 0) {
                mLoadingProjects.setVisibility(View.GONE);
                mProjectList.setVisibility(View.VISIBLE);

                if (mIsConfirmation) {
                    mProjectList.setDividerHeight(0);
                }
                
                mAdapter = new ProjectAdapter(ProjectSelectorActivity.this, mProjects);
                mProjectList.setAdapter(mAdapter);
                mProjectList.setOnItemClickListener(ProjectSelectorActivity.this);
                mProjectList.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView absListView, int i) {
                        if (mSearchText.getVisibility() != View.VISIBLE) {
                            mSearchText.setVisibility(View.VISIBLE);
                            mSearchText.startAnimation(AnimationUtils.loadAnimation(ProjectSelectorActivity.this, R.anim.slide_in_from_top));
                            mShownSearchBox = true;
                        }
                    }

                    @Override
                    public void onScroll(AbsListView absListView, int i, int i1, int i2) { }
                });
                
            } else {
                mLoadingProjects.setText(R.string.no_projects);
                mLoadingProjects.setVisibility(View.VISIBLE);
                mProjectList.setVisibility(View.GONE);
            }


            ProjectFieldViewer.getProjectFields(ProjectSelectorActivity.this, projectIds, mObservationId, new ProjectFieldViewer.ProjectFieldsResults() {
                @Override
                public void onProjectFieldsResults(ArrayList projectFields, HashMap<Integer, ProjectFieldValue> projectValues) {
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
                saveProjectFieldValues();

                if (!validateProjectFields()) {
                    return false;
                }

                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putIntegerArrayList(PROJECT_IDS, mObservationProjects);
                bundle.putSerializable(PROJECT_FIELDS, mProjectFieldValues);
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
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.project_selector_menu, menu);
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);

        mProjectFieldViewers = new HashMap<Integer, List<ProjectFieldViewer>>();

        final Intent intent = getIntent();
        setContentView(R.layout.project_selector);
        
        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mObservationId = (int) intent.getIntExtra(INaturalistService.OBSERVATION_ID, 0);
            mObservationProjects = intent.getIntegerArrayListExtra(INaturalistService.PROJECT_ID);
            mUmbrellaProjects = intent.getIntegerArrayListExtra(UMBRELLA_PROJECT_IDs);
            mIsConfirmation = intent.getBooleanExtra(ProjectSelectorActivity.IS_CONFIRMATION, false);
            mProjectFieldValues = (HashMap<Integer, ProjectFieldValue>) intent.getSerializableExtra(ProjectSelectorActivity.PROJECT_FIELDS);
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);

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
            actionBar.setLogo(R.drawable.ic_arrow_back);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(Html.fromHtml(getString(R.string.select_projects)));
        }


        mLoadingProjects = (TextView) findViewById(R.id.project_list_empty);
        mProjectList = (ListView) findViewById(R.id.project_list);

        mSearchText = (EditText) findViewById(R.id.search_filter);
        mSearchText.setHint(R.string.search_projects_youve_joined);
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

        if (mShownSearchBox) {
            mSearchText.setVisibility(View.VISIBLE);
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveProjectFieldValues();

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mProjectReceiver, this);
        if (mProjectFieldViewers != null) {
            for (Integer projectId : mProjectFieldViewers.keySet()) {
                List<ProjectFieldViewer> fieldViewers = mProjectFieldViewers.get(projectId);
                if (fieldViewers == null) continue;

                for (ProjectFieldViewer fieldViewer : fieldViewers) {
                    fieldViewer.unregisterReceivers();
                }
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mProjectReceiver = new ProjectReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_JOINED_PROJECTS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mProjectReceiver, filter, this);

        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_JOINED_PROJECTS, null, ProjectSelectorActivity.this, INaturalistService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    // Update project field values from UI
    private void saveProjectFieldValues() {
        for (Integer projectId : mProjectFieldViewers.keySet()) {
            List<ProjectFieldViewer> viewers = mProjectFieldViewers.get(projectId);

            if (viewers == null) continue;

            for (ProjectFieldViewer viewer : viewers) {
                String newValue = viewer.getValue();
                int fieldId = viewer.getField().field_id;
                ProjectFieldValue fieldValue = mProjectFieldValues.get(fieldId);
                if (fieldValue == null) {
                    fieldValue = new ProjectFieldValue();
                    fieldValue.field_id = fieldId;
                    mProjectFieldValues.put(fieldId, fieldValue);
                }
                fieldValue.value = newValue;
            }
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
                                if ((item.optBoolean("is_header", false)) || (item.getString("title").toLowerCase().indexOf(search) > -1)) {
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
            BetterJSONObject item = new BetterJSONObject(mItems.get(position));
            boolean isHeader = ((item.getBoolean("is_header") != null) && (item.getBoolean("is_header") == true));
            String projectType = item.getString("project_type");
            boolean isUmbrellaProject = ((projectType != null) && ((projectType.equals(Project.PROJECT_TYPE_COLLECTION)) || (projectType.equals(Project.PROJECT_TYPE_UMBRELLA))));

            View view = null;
            if (isHeader) {
                view = inflater.inflate(R.layout.project_header, parent, false);
            } else {
                view = inflater.inflate(mIsConfirmation ? R.layout.project_selector_confirmation_item : R.layout.project_selector_item, parent, false);
            }

            saveProjectFieldValues();

            TextView projectName = (TextView) view.findViewById(isHeader ? R.id.header_title : R.id.project_name);
            final String projectTitle = item.getString("title");
            projectName.setText(projectTitle);

            if (isHeader) {
                return view;
            }

            TextView projectDescription = (TextView) view.findViewById(R.id.project_description);
            final String noHTMLDescription = Html.fromHtml(item.getString("description")).toString();
            if (!mIsConfirmation) {
                // Strip HTML tags
                projectDescription.setText(noHTMLDescription);
            }
            ImageView projectPic = (ImageView) view.findViewById(R.id.project_pic);
            String iconUrl = item.getString("icon_url");

            if ((iconUrl == null) && (item.has("project"))){
                JSONObject project = item.getJSONObject("project");
                iconUrl = project.optString("icon");
            }

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
                if (noHTMLDescription.length() > 0) {
                    ((ViewGroup) view.findViewById(R.id.project_pic_container)).setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            mHelper.alert(projectTitle, noHTMLDescription);
                        }
                    });
                } else {
                    // No description - Hide the info button
                    view.findViewById(R.id.project_pic_info).setVisibility(View.GONE);
                }
            }

            view.setTag(R.id.TAG_PROJECT, item);

            final int projectId = item.getInt("id");

            if (isUmbrellaProject) {
                boolean contains = mUmbrellaProjects.contains(Integer.valueOf(projectId));

                view.findViewById(R.id.project_selected_icon).setVisibility(View.GONE);
                view.findViewById(R.id.project_unselected_icon).setVisibility(View.GONE);

                if (contains) {
                    // Checked on
                    view.findViewById(R.id.umbrella_project_selected).setVisibility(View.VISIBLE);
                } else {
                    // Checked off
                    view.findViewById(R.id.umbrella_project_selected).setVisibility(View.GONE);
                }

            } else {
                view.setTag(R.id.TAG_IS_CHECKED, mObservationProjects.contains(Integer.valueOf(projectId)));
                view.findViewById(R.id.umbrella_project_selected).setVisibility(View.GONE);
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
                            final View finalView = view;
                            ((ViewGroup) view.findViewById(R.id.project_top_container)).setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View currentView) {
                                    mProjectList.performItemClick(
                                            finalView,
                                            position,
                                            projectId);
                                }
                            });
                        } else {
                            projectFieldsTable.setVisibility(View.GONE);
                        }

                        for (final ProjectField field : fields) {
                            ProjectFieldValue fieldValue = mProjectFieldValues.get(field.field_id);
                            final ProjectFieldViewer fieldViewer = new ProjectFieldViewer(ProjectSelectorActivity.this, field, fieldValue, true);

                            viewers.add(fieldViewer);

                            if (field.is_required) {
                                view.findViewById(R.id.is_required).setVisibility(View.VISIBLE);
                            }

                            fieldViewer.setOnFocusedListener(new ProjectFieldViewer.FocusedListener() {
                                @Override
                                public void onFocused() {
                                    mLastProjectFieldFocused = field.field_id;
                                    mLastProjectIdFocused = projectId;

                                    mLastProjectFieldIndex = mProjectList.getFirstVisiblePosition();
                                    View v = mProjectList.getChildAt(0);
                                    mLastProjectFieldTop = (v == null) ? 0 : (v.getTop() - mProjectList.getPaddingTop());
                                }
                            });

                            projectFieldsTable.addView(fieldViewer.getView());
                        }

                        focusProjectField();

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
            }

            return view;
        }
    }

    private void focusProjectField() {
        if ((mLastProjectIdFocused == -1) || (mLastProjectFieldFocused == -1)) {
            return;
        }

        for (final int projectId : mProjectFieldViewers.keySet()) {
            List<ProjectFieldViewer> fields = mProjectFieldViewers.get(projectId);
            for (final ProjectFieldViewer fieldViewer : fields) {
                final ProjectField field = fieldViewer.getField();
                if ((mLastProjectFieldFocused == field.field_id) && (mLastProjectIdFocused == projectId)) {
                    mLastProjectIdFocused = -1;
                    mLastProjectFieldFocused = -1;

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            fieldViewer.setFocus();
                            mProjectList.setSelectionFromTop(mLastProjectFieldIndex, mLastProjectFieldTop);
                        }
                    }, 5);
                }
            }
        }
    }


    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int arg2, long arg3) {
        BetterJSONObject project = (BetterJSONObject) view.getTag(R.id.TAG_PROJECT);

        if (project == null) return;

        String projectType = project.getString("project_type");
        boolean isUmbrellaProject = ((projectType != null) && ((projectType.equals(Project.PROJECT_TYPE_COLLECTION)) || (projectType.equals(Project.PROJECT_TYPE_UMBRELLA))));

        if (isUmbrellaProject) {
            // Umbrella/collection projects cannot be selected / expanded
            return;
        }

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

    private boolean validateProjectFields() {
        if (mIsConfirmation) {
            HashMap<Integer, List<ProjectFieldViewer>> finalProjectFields = new HashMap<Integer, List<ProjectFieldViewer>>();
            // Only save the checked-on (selected) project fields
            for (int projectId : mObservationProjects) {
                finalProjectFields.put(projectId, mProjectFieldViewers.get(projectId));
            }
            for (int projectId : finalProjectFields.keySet()) {
                List<ProjectFieldViewer> fields = finalProjectFields.get(projectId);
                if (fields == null) break;
                for (ProjectFieldViewer fieldViewer : fields) {
                    if (!fieldViewer.isValid()) {
                        Toast.makeText(this, String.format(getString(R.string.invalid_project_field), fieldViewer.getField().name), Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            }

            mProjectFieldViewers = finalProjectFields;
        }

        return true;
    }

}
