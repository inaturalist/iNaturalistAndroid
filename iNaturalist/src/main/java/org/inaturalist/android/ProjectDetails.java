package org.inaturalist.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class ProjectDetails extends AppCompatActivity implements AppBarLayout.OnOffsetChangedListener {
 	private final static String VIEW_TYPE_OBSERVATIONS = "observations";
	private final static String VIEW_TYPE_SPECIES = "species";
	private final static String VIEW_TYPE_OBSERVERS = "observers";
    private final static String VIEW_TYPE_IDENTIFIERS = "identifiers";

    public static final int RESULT_REFRESH_RESULTS = 0x1000;

    private Button mJoinLeaveProject;

    private String mViewType;

    private INaturalistApp mApp;
    private BetterJSONObject mProject;

    private ActivityHelper mHelper;
    private Button mAboutProject;
    private Button mProjectNews;

    private GridViewExtended mObservationsGrid;
    private ObservationGridAdapter mGridAdapter;
    private ProgressBar mLoadingObservationsGrid;
    private TextView mObservationsGridEmpty;

    private ListView mSpeciesList;
    private TaxonAdapter mSpeciesListAdapter;
    private ProgressBar mLoadingSpeciesList;
    private TextView mSpeciesListEmpty;

    private ListView mPeopleList;
    private ProjectUserAdapter mPeopleListAdapter;
    private ProgressBar mLoadingPeopleList;
    private ViewGroup mPeopleListHeader;
    private TextView mPeopleListEmpty;

    private ListView mIdentifiersList;
    private ProjectUserAdapter mIdentifiersListAdapter;
    private ProgressBar mLoadingIdentifiersList;
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
    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    private boolean mJoinedOrLeftProject = false;

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

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }


        if (savedInstanceState == null) {
            mProject = new BetterJSONObject(intent.getStringExtra("project"));
            mViewType = VIEW_TYPE_OBSERVATIONS;

        } else {
            mProject = new BetterJSONObject(savedInstanceState.getString("project"));
            mViewType = savedInstanceState.getString("mViewType");

            mObservations = loadListFromBundle(savedInstanceState, "mObservations");
            mSpecies = loadListFromBundle(savedInstanceState, "mSpecies");
            mObservers = loadListFromBundle(savedInstanceState, "mObservers");
            mIdentifiers = loadListFromBundle(savedInstanceState, "mIdentifiers");

            mTotalIdentifiers = savedInstanceState.getInt("mTotalIdentifiers");
            mTotalObervers = savedInstanceState.getInt("mTotalObervers");
            mTotalObservations = savedInstanceState.getInt("mTotalObservations");
            mTotalSpecies = savedInstanceState.getInt("mTotalSpecies");

            mJoinedOrLeftProject = savedInstanceState.getBoolean("mJoinedOrLeftProject");
        }

        // Tab Initialization
        initializeTabs();

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
                    if (loadedBitmap != null) {
                        imageView.setImageBitmap(ImageUtils.blur(ProjectDetails.this, ImageUtils.centerCropBitmap(loadedBitmap.copy(loadedBitmap.getConfig(), true))));
                    }
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
                mJoinedOrLeftProject = true;
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
        outState.putString("project", mProject.getJSONObject().toString());
        outState.putString("mViewType", mViewType);
        saveListToBundle(outState, mObservations, "mObservations");
        saveListToBundle(outState, mSpecies, "mSpecies");
        saveListToBundle(outState, mObservers, "mObservers");
        saveListToBundle(outState, mIdentifiers, "mIdentifiers");
        outState.putInt("mTotalIdentifiers", mTotalIdentifiers);
        outState.putInt("mTotalObervers", mTotalObervers);
        outState.putInt("mTotalObservations", mTotalObservations);
        outState.putInt("mTotalSpecies", mTotalSpecies);
        outState.putBoolean("mJoinedOrLeftProject", mJoinedOrLeftProject);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mProjectDetailsReceiver, this);
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
        BaseFragmentActivity.safeRegisterReceiver(mProjectDetailsReceiver, filter, this);

        if (mObservations == null) getProjectDetails(INaturalistService.ACTION_GET_PROJECT_OBSERVATIONS);
        if (mSpecies == null) getProjectDetails(INaturalistService.ACTION_GET_PROJECT_SPECIES);
        if (mObservers == null) getProjectDetails(INaturalistService.ACTION_GET_PROJECT_OBSERVERS);
        if (mIdentifiers == null) getProjectDetails(INaturalistService.ACTION_GET_PROJECT_IDENTIFIERS);
    }

    @Override
    public void onBackPressed() {
    	Intent intent = new Intent();
    	Bundle bundle = new Bundle();
    	bundle.putString("project", mProject.getJSONObject().toString());
    	intent.putExtras(bundle);

    	setResult(mJoinedOrLeftProject ? RESULT_REFRESH_RESULTS : RESULT_OK, intent);
        super.onBackPressed();
    }




     // Method to add a TabHost
    private void addTab(int position, View tabContent) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);
        tab.setCustomView(tabContent);
    }


    // Tabs Creation
    private void initializeTabs() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        mViewPager.setOffscreenPageLimit(3); // So we wouldn't have to recreate the views every time
        ProjectDetailsPageAdapter adapter = new ProjectDetailsPageAdapter(this);
        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);

        addTab(0, createTabContent(getString(R.string.project_observations), 1000));
        addTab(1, createTabContent(getString(R.string.project_species), 2000));
        addTab(2, createTabContent(getString(R.string.project_people), 3000));
        addTab(3, createTabContent(getString(R.string.project_identifiers), 4000));

        TabLayout.OnTabSelectedListener tabListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView tabNameText = (TextView) tab.getCustomView().findViewById(R.id.tab_name);

                tabNameText.setTypeface(null, Typeface.BOLD);
                tabNameText.setTextColor(Color.parseColor("#000000"));

                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                View tabView = tab.getCustomView();
                TextView tabNameText = (TextView) tabView.findViewById(R.id.tab_name);

                tabNameText.setTypeface(null, Typeface.NORMAL);
                tabNameText.setTextColor(Color.parseColor("#ACACAC"));
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        };
        mTabLayout.setOnTabSelectedListener(tabListener);

        ViewPager.OnPageChangeListener pageListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 3:
                        mViewType = VIEW_TYPE_IDENTIFIERS;
                        break;
                    case 2:
                        mViewType = VIEW_TYPE_OBSERVERS;
                        break;
                    case 1:
                        mViewType = VIEW_TYPE_SPECIES;
                        break;
                    case 0:
                    default:
                        mViewType = VIEW_TYPE_OBSERVATIONS;
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mViewPager.addOnPageChangeListener(pageListener);

        if (mViewType.equals(VIEW_TYPE_OBSERVATIONS)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(0));
        } else if (mViewType.equals(VIEW_TYPE_SPECIES)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(1));
        } else if (mViewType.equals(VIEW_TYPE_OBSERVERS)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(2));
        } else if (mViewType.equals(VIEW_TYPE_IDENTIFIERS)) {
            tabListener.onTabSelected(mTabLayout.getTabAt(3));
        }

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTabLayout.setTabMode(TabLayout.MODE_FIXED);
        } else {
            mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        }
    }

    private View createTabContent(String tabName, int count) {
        ViewGroup view = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.project_details_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        TextView tabNameText = (TextView) view.findViewById(R.id.tab_name);

        DecimalFormat formatter = new DecimalFormat("#,###,###");
        countText.setText(formatter.format(count));
        tabNameText.setText(tabName);

        int width;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            width = size.x;
        } else {
            width = getWindowManager().getDefaultDisplay().getWidth();
        }
        width = (int)(width * 0.283);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);

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

            JSONArray results = null;
            int totalResults = 0;

            if (resultsObject != null) {
                resultsJSON = resultsObject.getJSONArray("results");
                Integer count = resultsObject.getInt("total_results");
                if (count != null) {
                    totalResults = count;
                    results = resultsJSON.getJSONArray();
                }
            }

            if (results == null) {
                refreshViewState();
                return;
            }

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

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
        DecimalFormat formatter = new DecimalFormat("#,###,###");

        if (mLoadingObservationsGrid != null) {
            if (mObservations == null) {
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingObservationsGrid.setVisibility(View.VISIBLE);
                mObservationsGrid.setVisibility(View.GONE);
                mObservationsGridEmpty.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(0).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalObservations));
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
        }

        if (mLoadingSpeciesList != null) {
            if (mSpecies == null) {
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingSpeciesList.setVisibility(View.VISIBLE);
                mSpeciesListEmpty.setVisibility(View.GONE);
                mSpeciesList.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(1).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalSpecies));
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
        }

        if (mLoadingPeopleList != null) {
            if (mObservers == null) {
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingPeopleList.setVisibility(View.VISIBLE);
                mPeopleListEmpty.setVisibility(View.GONE);
                mPeopleList.setVisibility(View.GONE);
                mPeopleListHeader.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(2).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalObervers));
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

                mPeopleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        JSONObject item = (JSONObject) view.getTag();
                        Intent intent = new Intent(ProjectDetails.this, UserProfile.class);
                        intent.putExtra("user", new BetterJSONObject(item));
                        startActivity(intent);
                    }
                });
            }
        }

        if (mLoadingIdentifiersList != null) {
            if (mIdentifiers == null) {
                ((TextView) mTabLayout.getTabAt(3).getCustomView().findViewById(R.id.count)).setVisibility(View.GONE);
                ((ProgressBar) mTabLayout.getTabAt(3).getCustomView().findViewById(R.id.loading)).setVisibility(View.VISIBLE);
                mLoadingIdentifiersList.setVisibility(View.VISIBLE);
                mIdentifiersListEmpty.setVisibility(View.GONE);
                mIdentifiersList.setVisibility(View.GONE);
                mIdentifiersListHeader.setVisibility(View.GONE);
            } else {
                ((TextView) mTabLayout.getTabAt(3).getCustomView().findViewById(R.id.count)).setVisibility(View.VISIBLE);
                ((ProgressBar) mTabLayout.getTabAt(3).getCustomView().findViewById(R.id.loading)).setVisibility(View.GONE);
                ((TextView) mTabLayout.getTabAt(3).getCustomView().findViewById(R.id.count)).setText(formatter.format(mTotalIdentifiers));
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

                mIdentifiersList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        JSONObject item = (JSONObject) view.getTag();
                        Intent intent = new Intent(ProjectDetails.this, UserProfile.class);
                        intent.putExtra("user", new BetterJSONObject(item));
                        startActivity(intent);
                    }
                });
            }
        }
    }


    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(offset) / (float) maxScroll;

        if (percentage >= 0.9f) {
            if (!mProjectPicHidden) {
                startAlphaAnimation(mProjectPicContainer, 100, View.INVISIBLE);
                mProjectPicHidden = true;
            }
        } else {
            if (mProjectPicHidden) {
                startAlphaAnimation(mProjectPicContainer, 100, View.VISIBLE);
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


    public class ProjectDetailsPageAdapter extends PagerAdapter {
        final int PAGE_COUNT = 4;
        private Context mContext;

        public ProjectDetailsPageAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            int layoutResource;

            switch (position) {
                case 3:
                    layoutResource = R.layout.project_identifiers;
                    break;
                case 2:
                    layoutResource = R.layout.project_people;
                    break;
                case 1:
                    layoutResource = R.layout.project_species;
                    break;
                case 0:
                default:
                    layoutResource = R.layout.project_observations;
                    break;
            }

            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(layoutResource, collection, false);



            switch (position) {
                case 3:
                    mLoadingIdentifiersList = (ProgressBar) layout.findViewById(R.id.loading_identifiers_list);
                    mIdentifiersListEmpty = (TextView) layout.findViewById(R.id.identifiers_list_empty);
                    mIdentifiersList = (ListView) layout.findViewById(R.id.identifiers_list);
                    mIdentifiersListHeader = (ViewGroup) layout.findViewById(R.id.identifiers_list_header);
                    ViewCompat.setNestedScrollingEnabled(mIdentifiersList, true);
                    break;
                case 2:
                    mLoadingPeopleList = (ProgressBar) layout.findViewById(R.id.loading_people_list);
                    mPeopleListEmpty = (TextView) layout.findViewById(R.id.people_list_empty);
                    mPeopleList = (ListView) layout.findViewById(R.id.people_list);
                    mPeopleListHeader = (ViewGroup) layout.findViewById(R.id.people_list_header);
                    ViewCompat.setNestedScrollingEnabled(mPeopleList, true);
                    break;
                case 1:
                    mLoadingSpeciesList = (ProgressBar) layout.findViewById(R.id.loading_species_list);
                    mSpeciesListEmpty = (TextView) layout.findViewById(R.id.species_list_empty);
                    mSpeciesList = (ListView) layout.findViewById(R.id.species_list);
                    ViewCompat.setNestedScrollingEnabled(mSpeciesList, true);
                    mSpeciesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            Intent intent = new Intent(ProjectDetails.this, TaxonActivity.class);
                            intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item));
                            intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                            startActivity(intent);
                        }
                    });

                    break;
                case 0:
                default:
                    mLoadingObservationsGrid = (ProgressBar) layout.findViewById(R.id.loading_observations_grid);
                    mObservationsGridEmpty = (TextView) layout.findViewById(R.id.observations_grid_empty);
                    mObservationsGrid = (GridViewExtended) layout.findViewById(R.id.observations_grid);
                    mObservationsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                            JSONObject item = (JSONObject) view.getTag();
                            Intent intent = new Intent(ProjectDetails.this, ObservationViewerActivity.class);
                            intent.putExtra("observation", item.toString());
                            intent.putExtra("read_only", true);
                            intent.putExtra("reload", true);
                            startActivity(intent);

                            try {
                                JSONObject eventParams = new JSONObject();
                                eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_PROJECT_DETAILS);

                                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                    });

                    ViewCompat.setNestedScrollingEnabled(mObservationsGrid, true);
                    break;
            }



            collection.addView(layout);

            refreshViewState();

            return layout;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
