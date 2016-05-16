package org.inaturalist.android;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ProjectDetails extends AppCompatActivity implements TabHost.OnTabChangeListener, AppBarLayout.OnOffsetChangedListener {
 	private final static String VIEW_TYPE_OBSERVATIONS = "observations";
	private final static String VIEW_TYPE_SPECIES = "species";
	private final static String VIEW_TYPE_OBSERVERS = "observers";
    private final static String VIEW_TYPE_IDENTIFIERS = "identifiers";

    private Button mJoinLeaveProject;

    private String mViewType;

    private INaturalistApp mApp;
    private BetterJSONObject mProject;

    private TabHost mTabHost;
    private ActivityHelper mHelper;
    private Button mAboutProject;
    private Button mProjectNews;

    private GridViewExtended mObservationsGrid;
    private ObservationGridAdapter mGridAdapter;
    private ProgressBar mLoadingObservationsGrid;
    private ViewGroup mGridContainer;
    private TextView mObservationsGridEmpty;

    private ListView mSpeciesList;
    private TaxonAdapter mSpeciesListAdapter;
    private ProgressBar mLoadingSpeciesList;
    private ViewGroup mSpeciesContainer;
    private TextView mSpeciesListEmpty;

    private ListView mPeopleList;
    private ProjectUserAdapter mPeopleListAdapter;
    private ProgressBar mLoadingPeopleList;
    private ViewGroup mPeopleContainer;
    private ViewGroup mPeopleListHeader;
    private TextView mPeopleListEmpty;

    private ListView mIdentifiersList;
    private ProjectUserAdapter mIdentifiersListAdapter;
    private ProgressBar mLoadingIdentifiersList;
    private ViewGroup mIdentifiersContainer;
    private ViewGroup mIdentifiersListHeader;
    private TextView mIdentifiersListEmpty;

    private ArrayList<JSONObject> mObservations;
    private ArrayList<JSONObject> mSpecies;
    private ArrayList<JSONObject> mObservers;
    private ArrayList<JSONObject> mIdentifiers;

    private ProjectDetailsReceiver mProjectDetailsReceiver;

    private int mTotalObservations;
    private int mTotalSpecies;
    private int mTotalObervers;
    private int mTotalIdentifiers;

    private AppBarLayout mAppBarLayout;
    private boolean mProjectPicHidden;
    private ViewGroup mProjectPicContainer;

    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}	

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
        	this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        setContentView(R.layout.project_details);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        CollapsingToolbarLayout collapsingToolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);

        mAppBarLayout = (AppBarLayout) findViewById(R.id.project_top_bar);
        mAppBarLayout.addOnOffsetChangedListener(this);

        mLoadingObservationsGrid = (ProgressBar) findViewById(R.id.loading_observations_grid);
        mObservationsGridEmpty = (TextView) findViewById(R.id.observations_grid_empty);
        mObservationsGrid = (GridViewExtended) findViewById(R.id.observations_grid);
        mGridContainer = (ViewGroup) findViewById(R.id.grid_container);

        mLoadingSpeciesList = (ProgressBar) findViewById(R.id.loading_species_list);
        mSpeciesListEmpty = (TextView) findViewById(R.id.species_list_empty);
        mSpeciesList = (ListView) findViewById(R.id.species_list);
        mSpeciesContainer = (ViewGroup) findViewById(R.id.species_container);

        mSpeciesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                JSONObject item = (JSONObject) view.getTag();
                Intent intent = new Intent(ProjectDetails.this, GuideTaxonActivity.class);
                intent.putExtra("taxon", new BetterJSONObject(item));
                intent.putExtra("guide_taxon", false);
                intent.putExtra("show_add", false);
                intent.putExtra("download_taxon", true);
                startActivity(intent);
            }
        });

        mLoadingPeopleList = (ProgressBar) findViewById(R.id.loading_people_list);
        mPeopleListEmpty = (TextView) findViewById(R.id.people_list_empty);
        mPeopleList = (ListView) findViewById(R.id.people_list);
        mPeopleContainer = (ViewGroup) findViewById(R.id.people_container);
        mPeopleListHeader = (ViewGroup) findViewById(R.id.people_list_header);

        mLoadingIdentifiersList = (ProgressBar) findViewById(R.id.loading_identifiers_list);
        mIdentifiersListEmpty = (TextView) findViewById(R.id.identifiers_list_empty);
        mIdentifiersList = (ListView) findViewById(R.id.identifiers_list);
        mIdentifiersContainer = (ViewGroup) findViewById(R.id.identifiers_container);
        mIdentifiersListHeader = (ViewGroup) findViewById(R.id.identifiers_list_header);

        mObservationsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                JSONObject item = (JSONObject) view.getTag();
                Intent intent = new Intent(ProjectDetails.this, ObservationViewerActivity.class);
                intent.putExtra("observation", item.toString());
                intent.putExtra("read_only", true);
                intent.putExtra("reload", true);
                startActivity(intent);
            }
        });

        ViewCompat.setNestedScrollingEnabled(mObservationsGrid, true);
        ViewCompat.setNestedScrollingEnabled(mIdentifiersList, true);
        ViewCompat.setNestedScrollingEnabled(mPeopleList, true);
        ViewCompat.setNestedScrollingEnabled(mSpeciesList, true);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mProject = (BetterJSONObject) intent.getSerializableExtra("project");
            mViewType = VIEW_TYPE_OBSERVATIONS;

            getProjectDetails(INaturalistService.ACTION_GET_PROJECT_OBSERVATIONS);
            getProjectDetails(INaturalistService.ACTION_GET_PROJECT_SPECIES);
            getProjectDetails(INaturalistService.ACTION_GET_PROJECT_OBSERVERS);
            getProjectDetails(INaturalistService.ACTION_GET_PROJECT_IDENTIFIERS);

            mGridContainer.setVisibility(View.VISIBLE);
            mSpeciesContainer.setVisibility(View.GONE);
            mPeopleContainer.setVisibility(View.GONE);
            mIdentifiersContainer.setVisibility(View.GONE);

        } else {
            mProject = (BetterJSONObject) savedInstanceState.getSerializable("project");
            mViewType = savedInstanceState.getString("mViewType");

            mObservations = loadListFromBundle(savedInstanceState, "mObservations");
            mSpecies = loadListFromBundle(savedInstanceState, "mSpecies");
            mObservers = loadListFromBundle(savedInstanceState, "mObservers");
            mIdentifiers = loadListFromBundle(savedInstanceState, "mIdentifiers");

            mTotalIdentifiers = savedInstanceState.getInt("mTotalIdentifiers");
            mTotalObervers = savedInstanceState.getInt("mTotalObervers");
            mTotalObservations = savedInstanceState.getInt("mTotalObservations");
            mTotalSpecies = savedInstanceState.getInt("mTotalSpecies");
        }

        // Tab Initialization
        initialiseTabHost();

        refreshViewState();
        refreshViewType();

        mJoinLeaveProject = (Button) findViewById(R.id.join_leave_project);
        mAboutProject = (Button) findViewById(R.id.about_project);
        mProjectNews = (Button) findViewById(R.id.project_news);

        if (mProject == null) {
            finish();
            return;
        }

        mProjectNews.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ProjectDetails.this, ProjectNews.class);
                intent.putExtra("project", mProject);
                startActivity(intent);
            }
        });

        mAboutProject.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ProjectDetails.this, ProjectDetailsAbout.class);
                intent.putExtra(ProjectDetailsAbout.KEY_PROJECT, mProject);
                startActivity(intent);
            }
        });

        mProjectPicContainer = (ViewGroup) findViewById(R.id.project_pic_container);
        final ImageView projectPic = (ImageView) findViewById(R.id.project_pic);
        String iconUrl = mProject.getString("icon_url");

        if ((iconUrl != null) && (iconUrl.length() > 0)) {
            projectPic.setVisibility(View.VISIBLE);
            findViewById(R.id.project_pic_none).setVisibility(View.GONE);
            UrlImageViewHelper.setUrlDrawable(projectPic, iconUrl);

            UrlImageViewHelper.setUrlDrawable((ImageView) findViewById(R.id.project_bg), iconUrl + "?bg=1", new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    imageView.setImageBitmap(ImageUtils.blur(ProjectDetails.this, loadedBitmap.copy(loadedBitmap.getConfig(), true)));
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    return loadedBitmap;
                }
            });
        } else {
            projectPic.setVisibility(View.GONE);
            findViewById(R.id.project_pic_none).setVisibility(View.VISIBLE);
        }

        collapsingToolbar.setTitle(mProject.getString("title"));

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
                    mHelper.confirm(getString(R.string.leave_project), getString(R.string.leave_project_confirmation),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int buttonId) {
                                    // Leave the project
                                    mJoinLeaveProject.setText(R.string.join);
                                    mProject.put("joined", false);

                                    Intent serviceIntent = new Intent(INaturalistService.ACTION_LEAVE_PROJECT, null, ProjectDetails.this, INaturalistService.class);
                                    serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
                                    startService(serviceIntent);
                                }
                            }, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                }
                            }, R.string.yes, R.string.no);

                } else {
                    String terms = mProject.getString("terms");
                    if ((terms != null) && (terms.length() > 0)) {
                        mHelper.confirm(getString(R.string.do_you_agree_to_the_following), mProject.getString("terms"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                joinProject();
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        }, R.string.yes, R.string.no);
                    } else {
                        joinProject();
                    }
                }

            }
        });

    }

    private void saveListToBundle(Bundle outState, ArrayList<JSONObject> list, String key) {
        if (list != null) {
        	JSONArray arr = new JSONArray(list);
        	outState.putString(key, arr.toString());
        }
    }

    private ArrayList<JSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        ArrayList<JSONObject> results = new ArrayList<JSONObject>();

        String obsString = savedInstanceState.getString(key);
        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(arr.getJSONObject(i));
                }

                return results;
            } catch (JSONException exc) {
                exc.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private void joinProject() {
        if (!isLoggedIn()) {
            // User not logged-in - redirect to onboarding screen
            startActivity(new Intent(ProjectDetails.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }

        mJoinLeaveProject.setText(R.string.leave);
        mProject.put("joined", true);

        Intent serviceIntent = new Intent(INaturalistService.ACTION_JOIN_PROJECT, null, ProjectDetails.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
        startService(serviceIntent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("project", mProject);
        outState.putString("mViewType", mViewType);
        saveListToBundle(outState, mObservations, "mObservations");
        saveListToBundle(outState, mSpecies, "mSpecies");
        saveListToBundle(outState, mObservers, "mObservers");
        saveListToBundle(outState, mIdentifiers, "mIdentifiers");
        outState.putInt("mTotalIdentifiers", mTotalIdentifiers);
        outState.putInt("mTotalObervers", mTotalObervers);
        outState.putInt("mTotalObservations", mTotalObservations);
        outState.putInt("mTotalSpecies", mTotalSpecies);

        super.onSaveInstanceState(outState);
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

        mProjectDetailsReceiver = new ProjectDetailsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.ACTION_PROJECT_SPECIES_RESULT);
        filter.addAction(INaturalistService.ACTION_PROJECT_IDENTIFIERS_RESULT);
        filter.addAction(INaturalistService.ACTION_PROJECT_OBSERVATIONS_RESULT);
        filter.addAction(INaturalistService.ACTION_PROJECT_OBSERVERS_RESULT);
        registerReceiver(mProjectDetailsReceiver, filter);

        refreshViewState();
    }

    @Override
    public void onBackPressed() {
    	Intent intent = new Intent();
    	Bundle bundle = new Bundle();
    	bundle.putSerializable("project", mProject);
    	intent.putExtras(bundle);

    	setResult(RESULT_OK, intent);      
        super.onBackPressed();
    }




     // Method to add a TabHost
    private static void AddTab(ProjectDetails activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

    // Manages the Tab changes, synchronizing it with Pages
    public void onTabChanged(String tag) {
        mViewType = tag;
        refreshViewType();
    }

    private void refreshViewType() {
        mGridContainer.setVisibility(View.GONE);
        mSpeciesContainer.setVisibility(View.GONE);
        mPeopleContainer.setVisibility(View.GONE);
        mIdentifiersContainer.setVisibility(View.GONE);


        TabWidget tabWidget = mTabHost.getTabWidget();
        for (int i = 0; i < tabWidget.getChildCount(); i++) {
            View tab = tabWidget.getChildAt(i);
            TextView tabNameText = (TextView) tab.findViewById(R.id.tab_name);
            View bottomLine = tab.findViewById(R.id.bottom_line);

            tabNameText.setTypeface(null, Typeface.NORMAL);
            tabNameText.setTextColor(Color.parseColor("#ACACAC"));
            bottomLine.setVisibility(View.GONE);
        }

        int selectedTab = 0;

    	if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            selectedTab = 0;
    		mGridContainer.setVisibility(View.VISIBLE);
    	} else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            selectedTab = 1;
            mSpeciesContainer.setVisibility(View.VISIBLE);
        } else if (mViewType.equals(VIEW_TYPE_OBSERVERS)) {
            selectedTab = 2;
            mPeopleContainer.setVisibility(View.VISIBLE);
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFIERS)) {
            selectedTab = 3;
            mIdentifiersContainer.setVisibility(View.VISIBLE);
        }

        mTabHost.setCurrentTab(selectedTab);
        View tab = tabWidget.getChildAt(selectedTab);
        TextView tabNameText = (TextView) tab.findViewById(R.id.tab_name);
        View bottomLine = tab.findViewById(R.id.bottom_line);

        tabNameText.setTypeface(null, Typeface.BOLD);
        tabNameText.setTextColor(Color.parseColor("#000000"));
        bottomLine.setVisibility(View.VISIBLE);
    }


    // Tabs Creation
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        ProjectDetails.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_OBSERVATIONS).setIndicator(
                createTabContent(getString(R.string.project_observations), 1000)));
        ProjectDetails.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_SPECIES).setIndicator(
                createTabContent(getString(R.string.project_species), 2000)));
        ProjectDetails.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_OBSERVERS).setIndicator(
                createTabContent(getString(R.string.project_people), 3000)));
        ProjectDetails.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_IDENTIFIERS).setIndicator(
                createTabContent(getString(R.string.project_identifiers), 4000)));

        mTabHost.getTabWidget().setDividerDrawable(null);

        mTabHost.setOnTabChangedListener(this);
    }

    private View createTabContent(String tabName, int count) {
        View view = LayoutInflater.from(this).inflate(R.layout.project_details_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        TextView tabNameText = (TextView) view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        return view;
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    }


    private void getProjectDetails(String action) {
        Intent serviceIntent = new Intent(action, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
        startService(serviceIntent);
    }

    private class ProjectDetailsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_project_details), error));
                return;
            }


            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject resultsObject;
            SerializableJSONArray resultsJSON;

            if (isSharedOnApp) {
                resultsObject = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.RESULTS);
            }

            resultsJSON = resultsObject.getJSONArray("results");
            int totalResults = resultsObject.getInt("total_results");

            JSONArray results = resultsJSON.getJSONArray();
            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            if (results == null) {
                refreshViewState();
                return;
            }

            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }

            if (intent.getAction().equals(INaturalistService.ACTION_PROJECT_OBSERVATIONS_RESULT)) {
            	mObservations = resultsArray;
                mTotalObservations = totalResults;
            } else if (intent.getAction().equals(INaturalistService.ACTION_PROJECT_SPECIES_RESULT)) {
            	mSpecies = resultsArray;
                mTotalSpecies = totalResults;
            } else if (intent.getAction().equals(INaturalistService.ACTION_PROJECT_OBSERVERS_RESULT)) {
                mObservers = resultsArray;
                mTotalObervers = totalResults;
            } else if (intent.getAction().equals(INaturalistService.ACTION_PROJECT_IDENTIFIERS_RESULT)) {
                mIdentifiers = resultsArray;
                mTotalIdentifiers = totalResults;
            }

            refreshViewState();
        }
    }

    private void refreshViewState() {
        TabWidget tabWidget = mTabHost.getTabWidget();
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        if (mObservations == null) {
            ((TextView)tabWidget.getChildAt(0).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(0).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingObservationsGrid.setVisibility(View.VISIBLE);
            mObservationsGrid.setVisibility(View.GONE);
            mObservationsGridEmpty.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(0).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(0).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(0).findViewById(R.id.count)).setText(formatter.format(mTotalObservations));
            mLoadingObservationsGrid.setVisibility(View.GONE);

            if (mObservations.size() == 0) {
                mObservationsGridEmpty.setVisibility(View.VISIBLE);
            } else {
                mObservationsGridEmpty.setVisibility(View.GONE);
            }

            mObservationsGrid.post(new Runnable() {
                @Override
                public void run() {
                    if (mObservationsGrid.getColumnWidth() > 0) {
                        mGridAdapter = new ObservationGridAdapter(ProjectDetails.this, mObservationsGrid.getColumnWidth(), mObservations);
                        mObservationsGrid.setAdapter(mGridAdapter);
                    }
                }
            });

            mObservationsGrid.setVisibility(View.VISIBLE);
        }

        if (mSpecies == null) {
            ((TextView)tabWidget.getChildAt(1).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(1).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingSpeciesList.setVisibility(View.VISIBLE);
            mSpeciesListEmpty.setVisibility(View.GONE);
            mSpeciesList.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(1).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(1).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(1).findViewById(R.id.count)).setText(formatter.format(mTotalSpecies));
            mLoadingSpeciesList.setVisibility(View.GONE);

            if (mSpecies.size() == 0) {
                mSpeciesListEmpty.setVisibility(View.VISIBLE);
            } else {
                mSpeciesListEmpty.setVisibility(View.GONE);
            }

            mSpeciesListAdapter = new TaxonAdapter(ProjectDetails.this, mSpecies);
            mSpeciesList.setAdapter(mSpeciesListAdapter);
            mSpeciesList.setVisibility(View.VISIBLE);
        }

        if (mObservers == null) {
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(2).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingPeopleList.setVisibility(View.VISIBLE);
            mPeopleListEmpty.setVisibility(View.GONE);
            mPeopleList.setVisibility(View.GONE);
            mPeopleListHeader.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(2).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setText(formatter.format(mTotalObervers));
            mLoadingPeopleList.setVisibility(View.GONE);

            if (mObservers.size() == 0) {
                mPeopleListEmpty.setVisibility(View.VISIBLE);
            } else {
                mPeopleListEmpty.setVisibility(View.GONE);
            }

            mPeopleListAdapter = new ProjectUserAdapter(ProjectDetails.this, mObservers);
            mPeopleList.setAdapter(mPeopleListAdapter);
            mPeopleList.setVisibility(View.VISIBLE);
            mPeopleListHeader.setVisibility(View.VISIBLE);
        }

        if (mIdentifiers == null) {
            ((TextView)tabWidget.getChildAt(3).findViewById(R.id.count)).setVisibility(View.GONE);
            ((ProgressBar)tabWidget.getChildAt(3).findViewById(R.id.loading)).setVisibility(View.VISIBLE);
            mLoadingIdentifiersList.setVisibility(View.VISIBLE);
            mIdentifiersListEmpty.setVisibility(View.GONE);
            mIdentifiersList.setVisibility(View.GONE);
            mIdentifiersListHeader.setVisibility(View.GONE);
        } else {
            ((TextView)tabWidget.getChildAt(3).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((ProgressBar)tabWidget.getChildAt(3).findViewById(R.id.loading)).setVisibility(View.GONE);
            ((TextView)tabWidget.getChildAt(3).findViewById(R.id.count)).setText(formatter.format(mTotalIdentifiers));
            mLoadingIdentifiersList.setVisibility(View.GONE);

            if (mIdentifiers.size() == 0) {
                mIdentifiersListEmpty.setVisibility(View.VISIBLE);
            } else {
                mIdentifiersListEmpty.setVisibility(View.GONE);
            }

            mIdentifiersListAdapter = new ProjectUserAdapter(ProjectDetails.this, mIdentifiers);
            mIdentifiersList.setAdapter(mIdentifiersListAdapter);
            mIdentifiersList.setVisibility(View.VISIBLE);
            mIdentifiersListHeader.setVisibility(View.VISIBLE);
        }
    }


    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(offset) / (float) maxScroll;

        if (percentage >= 0.9f) {
            if (!mProjectPicHidden) {
                startAlphaAnimation(mProjectPicContainer, 200, View.INVISIBLE);
                mProjectPicHidden = true;
            }
        } else {
            if (mProjectPicHidden) {
                startAlphaAnimation(mProjectPicContainer, 200, View.VISIBLE);
                mProjectPicHidden = false;
            }
        }

    }

   public static void startAlphaAnimation (View v, long duration, int visibility) {
       AlphaAnimation alphaAnimation = (visibility == View.VISIBLE)
               ? new AlphaAnimation(0f, 1f)
               : new AlphaAnimation(1f, 0f);

       alphaAnimation.setDuration(duration);
       alphaAnimation.setFillAfter(true);
       v.startAnimation(alphaAnimation);
   }

}
