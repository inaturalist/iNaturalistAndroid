package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;

public class MissionsActivity extends BaseFragmentActivity {
    // Each category is comprised of: Name (string resource), Icon (drawable resource), Background color, taxon ID
    private final static int[][] CATEGORIES = {
            { R.string.plants, R.drawable.ic_taxa_plants, Color.parseColor("#F1F8EA"), 47126 },
            { R.string.mammals, R.drawable.ic_taxa_mammals, Color.parseColor("#E9F0FB"), 40151 },
            { R.string.insects, R.drawable.ic_taxa_insects, Color.parseColor("#FDEAE6"), 47158 },
            { R.string.reptiles, R.drawable.ic_taxa_reptiles, Color.parseColor("#E9F0FB"), 26036 },
            { R.string.fish, R.drawable.ic_taxa_fish, Color.parseColor("#E9F0FB"), 47178 },
            { R.string.mollusks, R.drawable.ic_taxa_mollusks, Color.parseColor("#FDEAE6"), 47115 },
            { R.string.amphibians, R.drawable.ic_taxa_amphibians, Color.parseColor("#E9F0FB"), 20978 },
            { R.string.fungi, R.drawable.ic_taxa_fungi, Color.parseColor("#FDEAE6"), 47170 },
            { R.string.birds, R.drawable.ic_taxa_birds, Color.parseColor("#E9F0FB"), 3 },
            { R.string.arachnids, R.drawable.ic_taxa_arachnids, Color.parseColor("#FDEAE6"), 47119 }
    };

    private static final int MISSION_ONBOARDING_REQUEST_CODE = 0x1000;

    // How much to expand the recommended missions search by (in terms of degrees), in case
    // a previous search yielded no results.
    public final static float[] RECOMMENDED_MISSIONS_EXPANSION = { 0, 0.25f, 0.5f, 1.0f };
    private static final String TAG = "MissionsActivity";

    MissionsPagerAdapter mPageAdapter;
    private ViewPager mMissionsViewPager;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private MissionsReceiver mMissionsReceiver;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mMissions;
    private ProgressBar mLoading;
    private GridViewExtended mCategories;
    private TextView mViewAll;
    private TextView mLoadingDescription;
    private ViewGroup mRecommendedForYouContainer;
    private ViewGroup mNoConnectivityContainer;
    private ViewGroup mNoMissionsContainer;
    private ViewGroup mMissionsByCategoryContainer;

    @State public int mMissionsCurrentExpansionLevel = 0;
    @State public boolean mAskedForLocationPermissions = false;

@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mApp = (INaturalistApp)getApplication();
        mApp.applyLocaleSettings(getBaseContext());
        setContentView(R.layout.missions);
	    onDrawerCreate(savedInstanceState);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_MISSIONS);

        mMissionsByCategoryContainer = (ViewGroup) findViewById(R.id.missions_by_category_container);
        mRecommendedForYouContainer = (ViewGroup) findViewById(R.id.recommended_for_you_container);
        mNoConnectivityContainer = (ViewGroup) findViewById(R.id.no_connectivity_container);
        mNoMissionsContainer = (ViewGroup) findViewById(R.id.no_recommended_missions);
        mMissionsViewPager = (ViewPager) findViewById(R.id.recommended_missions);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mLoadingDescription = (TextView) findViewById(R.id.loading_description);
        mCategories = (GridViewExtended) findViewById(R.id.categories);
        mCategories.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // Load the missions for that taxon id
                AnalyticsClient.getInstance().logEvent(String.format(AnalyticsClient.EVENT_NAME_MISSIONS_CATEGORY, getString(CATEGORIES[position][0])));

                Intent intent = new Intent(MissionsActivity.this, MissionsGridActivity.class);
                intent.putExtra("taxon_id", CATEGORIES[position][3]);
                intent.putExtra("taxon_name", getString(CATEGORIES[position][0]));
                startActivity(intent);
            }
        });


        mViewAll = (TextView) findViewById(R.id.view_all);
        mViewAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMissions == null) return;

                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_MISSIONS_CATEGORY_ALL);

                Intent intent = new Intent(MissionsActivity.this, MissionsGridActivity.class);
                startActivity(intent);
            }
        });

        mHelper = new ActivityHelper(this);

        mApp.setStringResourceForView(this, R.id.view_all, "view_all_all_caps", "view_all");

        mCategories.setAdapter(new CategoriesAdapter(this, CATEGORIES, mCategories));
        resizeMissionCategories();

        SharedPreferences settings = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        if (!settings.getBoolean("shown_missions_onboarding", false)) {
            // Show the missions onboarding screen
            Intent intent = new Intent(MissionsActivity.this, MissionsOnboardingActivity.class);
            startActivityForResult(intent, MISSION_ONBOARDING_REQUEST_CODE);
        }

    }

    private class CategoriesAdapter extends ArrayAdapter<String> {

        private Context mContext;
        private int[][] mCategories;
        private GridViewExtended mGrid;

        public CategoriesAdapter(Context context, int[][] categories, GridViewExtended grid) {
            super(context, android.R.layout.simple_list_item_1);

            mContext = context;
            mCategories = categories;
            mGrid = grid;
        }

        @Override
        public int getCount() {
            return mCategories.length;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View view = inflater.inflate(R.layout.mission_category, parent, false);

            ImageView categoryIcon = (ImageView) view.findViewById(R.id.category_icon);
            TextView categoryName = (TextView) view.findViewById(R.id.category_name);

            categoryIcon.setImageResource(mCategories[position][1]);
            categoryName.setText(mCategories[position][0]);

            view.setBackgroundColor(mCategories[position][2]);

            view.setMinimumHeight(mGrid.getColumnWidth());

            return view;
        }
    }

    private class MissionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_recommended_missions), error));
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            Object object = null;
            BetterJSONObject resultsObject;
            JSONArray results = null;

            if (isSharedOnApp) {
                object = mApp.getServiceResult(intent.getAction());
            } else {
                object = intent.getSerializableExtra(INaturalistService.RECOMMENDED_MISSIONS_RESULT);
            }

            if (object == null) {
                // Network error of some kind
                mMissions = new ArrayList<>();
                refreshViewState();
                return;
            }

            // Species count result
            resultsObject = (BetterJSONObject) object;
            results = resultsObject.getJSONArray("results").getJSONArray();

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
					Logger.tag(TAG).error(e);
				}
            }

            mMissions = resultsArray;

            if (mMissions.size() == 0) {
                // No recommended missions - see if we can expand our search grid to find more
                mMissionsCurrentExpansionLevel++;
                if (mMissionsCurrentExpansionLevel < RECOMMENDED_MISSIONS_EXPANSION.length) {
                    // Still more search expansions left to try out

                    mMissions = null; // So it'll show up in the UI as still loading missions
                    float nextExpansion = RECOMMENDED_MISSIONS_EXPANSION[mMissionsCurrentExpansionLevel];

                    Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_RECOMMENDED_MISSIONS, null, MissionsActivity.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
                    serviceIntent.putExtra(INaturalistService.EXPAND_LOCATION_BY_DEGREES, nextExpansion);
                    ContextCompat.startForegroundService(MissionsActivity.this, serviceIntent);
                }
            }

            refreshViewState();
        }
    }

    private void refreshViewState() {
        if (!isNetworkAvailable()) {
            mNoConnectivityContainer.setVisibility(View.VISIBLE);
            mRecommendedForYouContainer.setVisibility(View.GONE);
            mNoMissionsContainer.setVisibility(View.GONE);
            mMissionsByCategoryContainer.setVisibility(View.GONE);
            return;
        }

        mNoConnectivityContainer.setVisibility(View.GONE);
        mNoMissionsContainer.setVisibility(View.GONE);
        mRecommendedForYouContainer.setVisibility(View.VISIBLE);
        mMissionsByCategoryContainer.setVisibility(View.VISIBLE);

        if (mMissions == null) {
            mMissionsViewPager.setVisibility(View.INVISIBLE);
            mLoading.setVisibility(View.VISIBLE);
            mLoadingDescription.setVisibility(View.VISIBLE);

            if (mMissionsCurrentExpansionLevel == 0) {
                mLoadingDescription.setText(R.string.searching_your_area);
            } else {
                mLoadingDescription.setText(R.string.expanding_your_search_area);
            }
        } else {
            mLoading.setVisibility(View.GONE);
            mLoadingDescription.setVisibility(View.GONE);

            if (mMissions.size() == 0) {
                mRecommendedForYouContainer.setVisibility(View.GONE);
                mMissionsByCategoryContainer.setVisibility(View.GONE);
                mNoMissionsContainer.setVisibility(View.VISIBLE);

            } else {
                mMissionsViewPager.setVisibility(View.VISIBLE);
                mMissionsByCategoryContainer.setVisibility(View.VISIBLE);

                mPageAdapter = new MissionsPagerAdapter(this, mMissions, mMissionsCurrentExpansionLevel, false);
                mMissionsViewPager.setAdapter(mPageAdapter);
            }
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mMissionsReceiver = new MissionsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.RECOMMENDED_MISSIONS_RESULT);
        safeRegisterReceiver(mMissionsReceiver, filter);

        if (mMissions == null) {
            // Ask for the recommended missions
            if (mApp.isLocationPermissionGranted()) {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_RECOMMENDED_MISSIONS, null, this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
                ContextCompat.startForegroundService(this, serviceIntent);
            } else if (!mAskedForLocationPermissions) {
                SharedPreferences settings = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                if (settings.getBoolean("shown_missions_onboarding", false)) {
                    askForLocationPermissions();
                }
            }
        }

        refreshViewState();
    }

    private void askForLocationPermissions() {
        mAskedForLocationPermissions = true;

        mApp.requestLocationPermission(this, new INaturalistApp.OnRequestPermissionResult() {
            @Override
            public void onPermissionGranted() {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_RECOMMENDED_MISSIONS, null, MissionsActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
                ContextCompat.startForegroundService(MissionsActivity.this, serviceIntent);
            }

            @Override
            public void onPermissionDenied() {
                mMissions = new ArrayList<>();
                refreshViewState();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }


    private void resizeMissionCategories() {
        final Handler handler = new Handler();
        if ((mCategories.getVisibility() == View.VISIBLE) && (mCategories.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeMissionCategories();
                }
            }, 100);

            return;
        }

        mCategories.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        int height = setGridViewHeightBasedOnItems(mCategories);
                    }
                }, 100);
            }
        });
    }



    public int setGridViewHeightBasedOnItems(final GridViewExtended gridView) {
    	ListAdapter adapter = gridView.getAdapter();
    	if (adapter != null) {
            int numberOfItems = adapter.getCount();
            int numberOfColumns = gridView.getNumColumns();
            int numberOfRows = (int)Math.ceil((float)numberOfItems / numberOfColumns);

            int spacing = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                spacing = gridView.getVerticalSpacing();
            }
            int columnWidth = gridView.getColumnWidth();


            int newHeight = (numberOfRows * columnWidth) + ((numberOfRows - 1) * spacing);


            ViewGroup.LayoutParams params = gridView.getLayoutParams();

            if (params.height != newHeight) {
                params.height = newHeight;
                gridView.setLayoutParams(params);
                gridView.requestLayout();
            }

            return newHeight;

    	} else {
    		return 0;
    	}
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mMissionsReceiver, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        mApp.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MISSION_ONBOARDING_REQUEST_CODE) {
            if (!mAskedForLocationPermissions) {
                askForLocationPermissions();
            }
        }
    }

}
