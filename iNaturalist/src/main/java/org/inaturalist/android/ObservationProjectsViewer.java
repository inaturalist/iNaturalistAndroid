package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;

public class ObservationProjectsViewer extends AppCompatActivity {
    
    private static final String TAG = "INAT:ObservationProjectsViewer";
    public static final String PROJECTS = "projects";

    private TextView mLoadingProjects;
    private ListView mProjectList;
    private EditText mSearchText;
    
    private INaturalistApp mApp;
    @State(AndroidStateBundlers.SerializableBundler.class) public ArrayList<BetterJSONObject> mObservationProjects;

    private ProjectAdapter mAdapter;
    private ActivityHelper mHelper;

    private boolean mShownSearchBox;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        mApp = (INaturalistApp)getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.project_selector);
        
        if (savedInstanceState == null) {
            mObservationProjects = (ArrayList<BetterJSONObject>) intent.getSerializableExtra(ObservationProjectsViewer.PROJECTS);
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));
        actionBar.setLogo(R.drawable.ic_arrow_back_gray_24dp);
        actionBar.setTitle(R.string.projects);

        mLoadingProjects = (TextView) findViewById(R.id.project_list_empty);
        mLoadingProjects.setVisibility(View.GONE);
        mProjectList = (ListView) findViewById(R.id.project_list);

        mSearchText = (EditText) findViewById(R.id.search_filter);
        mSearchText.setHint(R.string.search_projects);
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

        if (mShownSearchBox) {
            mSearchText.setVisibility(View.VISIBLE);
        }

        mAdapter = new ProjectAdapter(this, mObservationProjects);
        mProjectList.setAdapter(mAdapter);
        mHelper.resizeList(mProjectList);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
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

    public class ProjectAdapter extends ArrayAdapter<BetterJSONObject> implements Filterable {

        private List<BetterJSONObject> mItems;
        private List<BetterJSONObject> mOriginalItems;
        private Context mContext;
        private Filter mFilter;

        public ProjectAdapter(Context context, List<BetterJSONObject> objects) {
            super(context, R.layout.project_selector_item, objects);

            mItems = objects;
            mOriginalItems = new ArrayList<BetterJSONObject>(mItems);
            mContext = context;
            
            mFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        String search = constraint.toString().toLowerCase();
                        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>(mOriginalItems.size());
                        for (BetterJSONObject item : mOriginalItems) {
                            try {
                                if (item.getJSONObject("project").getString("title").toLowerCase().indexOf(search) > -1) {
                                    results.add(item);
                                }
                            } catch (JSONException e) {
                                Logger.tag(TAG).error(e);
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
                    mItems = (List<BetterJSONObject>) results.values;
                    notifyDataSetChanged();
                }
            };
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public BetterJSONObject getItem(int index) {
            return mItems.get(index);
        }
        
        @Override
        public Filter getFilter() {
            return mFilter;
        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.observation_project_item, parent, false);
            BetterJSONObject item = null;
            item = new BetterJSONObject(mItems.get(position).getJSONObject("project"));

            TextView projectName = (TextView) view.findViewById(R.id.project_name);
            final String projectTitle = item.getString("title");
            projectName.setText(projectTitle);

            ImageView projectPic = (ImageView) view.findViewById(R.id.project_pic);
            String iconUrl = item.getString("icon_url");

            if ((iconUrl == null) && (item.has("project"))){
                JSONObject project = item.getJSONObject("project");
                iconUrl = project.optString("icon");
            }
            if ((iconUrl == null) && (item.has("icon"))){
                iconUrl = item.getString("icon");
            }


            if ((iconUrl != null) && (iconUrl.length() > 0)) {
                projectPic.setVisibility(View.VISIBLE);
                view.findViewById(R.id.project_pic_none).setVisibility(View.GONE);
                UrlImageViewHelper.setUrlDrawable(projectPic, iconUrl);
            } else {
                projectPic.setVisibility(View.GONE);
                view.findViewById(R.id.project_pic_none).setVisibility(View.VISIBLE);
            }

            final String description = item.getString("description");
            if (description.length() > 0) {
                ((ViewGroup) view.findViewById(R.id.project_pic_container)).setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mHelper.alert(projectTitle, description.replace("\n", "<br/>"));
                    }
                });
            } else {
                // No description - Hide the info button
                view.findViewById(R.id.project_pic_info).setVisibility(View.GONE);
            }

            return view;
        }
    }

}
