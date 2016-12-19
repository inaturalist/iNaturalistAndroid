package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabWidget;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MissionsActivity extends BaseFragmentActivity {
    // Each category is comprised of: Name (string resource), Icon (drawable resource), Background color, taxon ID
    private final static int[][] CATEGORIES = {
            { R.string.plants, R.drawable.iconic_taxon_plantae, Color.parseColor("#F1F8EA"), 47126 },
            { R.string.mammals, R.drawable.iconic_taxon_mammalia, Color.parseColor("#E9F0FB"), 40151 },
            { R.string.insects, R.drawable.iconic_taxon_insecta, Color.parseColor("#FDEAE6"), 47158 },
            { R.string.reptiles, R.drawable.iconic_taxon_reptilia, Color.parseColor("#E9F0FB"), 26036 },
            { R.string.fish, R.drawable.iconic_taxon_actinopterygii, Color.parseColor("#E9F0FB"), 47178 },
            { R.string.mollusks, R.drawable.iconic_taxon_mollusca, Color.parseColor("#FDEAE6"), 47115 },
            { R.string.amphibians, R.drawable.iconic_taxon_amphibia, Color.parseColor("#E9F0FB"), 20978 },
            { R.string.birds, R.drawable.iconic_taxon_aves, Color.parseColor("#E9F0FB"), 3 },
            { R.string.arachnids, R.drawable.iconic_taxon_arachnida, Color.parseColor("#FDEAE6"), 47119 }
    };

    MissionsPagerAdapter mPageAdapter;
    private ViewPager mMissionsViewPager;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private MissionsReceiver mMissionsReceiver;
    private ArrayList<JSONObject> mMissions;
    private ProgressBar mLoading;
    private GridViewExtended mCategories;
    private TextView mViewAll;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.missions);
	    onDrawerCreate(savedInstanceState);

        mMissionsViewPager = (ViewPager) findViewById(R.id.recommended_missions);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mCategories = (GridViewExtended) findViewById(R.id.categories);
        mCategories.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // Load the missions for that taxon id
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

                Intent intent = new Intent(MissionsActivity.this, MissionsGridActivity.class);
                startActivity(intent);
            }
        });

        mApp = (INaturalistApp)getApplication();
        mHelper = new ActivityHelper(this);

        if (savedInstanceState == null) {
            // Ask for the recommended missions
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_RECOMMENDED_MISSIONS, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
            startService(serviceIntent);

        } else {
            mMissions = loadListFromBundle(savedInstanceState, "mMissions");
        }


        mCategories.setAdapter(new CategoriesAdapter(this, CATEGORIES, mCategories));
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                setGridViewHeightBasedOnItems(mCategories);
            }
        }, 100);


        refreshViewState();
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

            int totalResults = 0;

            if (object == null) {
                // Network error of some kind
                mMissions = new ArrayList<>();
                refreshViewState();
                return;
            }

            // Species count result
            resultsObject = (BetterJSONObject) object;
            totalResults = resultsObject.getInt("total_results");
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
					e.printStackTrace();
				}
            }

            mMissions = resultsArray;

            refreshViewState();
        }
    }

    private void refreshViewState() {
        if (mMissions == null) {
            mMissionsViewPager.setVisibility(View.INVISIBLE);
            mLoading.setVisibility(View.VISIBLE);
        } else {
            mLoading.setVisibility(View.GONE);
            mMissionsViewPager.setVisibility(View.VISIBLE);

            mPageAdapter = new MissionsPagerAdapter(mMissions);
            mMissionsViewPager.setAdapter(mPageAdapter);
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
        registerReceiver(mMissionsReceiver, filter);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveListToBundle(outState, mMissions, "mMissions");

        super.onSaveInstanceState(outState);
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


    private class MissionsPagerAdapter extends PagerAdapter {
        private UserSpeciesAdapter mAdapter;

        public MissionsPagerAdapter(ArrayList<JSONObject> missions) {
            mAdapter = new UserSpeciesAdapter(MissionsActivity.this, missions, UserSpeciesAdapter.VIEW_TYPE_CARDS, null);
        }

        @Override
        public int getCount() {
            return mAdapter.getCount();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == (View)object;
        }

        @Override
        public float getPageWidth(int position) {
            return 0.32f;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            View view = mAdapter.getView(position, null, container);
            ((ViewPager)container).addView(view, 0);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            ((ViewPager) container).removeView((View) object);
        }
    }


    public int setGridViewHeightBasedOnItems(final GridViewExtended gridView) {
    	ListAdapter adapter = gridView.getAdapter();
    	if (adapter != null) {
            int numberOfItems = adapter.getCount();
            int numberOfColumns = gridView.getNumColumns();
            int numberOfRows = (int)Math.ceil(numberOfItems / numberOfColumns);

            int spacing = gridView.getVerticalSpacing();
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
}
