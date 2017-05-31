package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshGridView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MissionsGridActivity extends AppCompatActivity {

    public static final String MISSIONS_EXPANSION_LEVEL = "missions_expansion_level";
    public static final String TAXON_ID = "taxon_id";

    UserSpeciesAdapter mMissionsAdapter;
    private PullToRefreshGridViewExtended mMissionsGrid;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private ArrayList<JSONObject> mMissions;
    private ProgressBar mLoading;
    private TextView mLoadingDescription;
    private ViewGroup mNoMissionsContainer;

    private MissionsReceiver mMissionsReceiver;

    private int mMissionsCurrentExpansionLevel = 0;
    private int mTaxonId = -1;

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

        Intent intent = getIntent();

        setContentView(R.layout.missions_grid);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (intent.hasExtra("taxon_name")) {
            getSupportActionBar().setTitle(intent.getStringExtra("taxon_name"));
        } else {
            getSupportActionBar().setTitle(R.string.recommended_for_you);
        }

        mMissionsGrid = (PullToRefreshGridViewExtended) findViewById(R.id.missions);
        mMissionsGrid.setMode(PullToRefreshBase.Mode.DISABLED);
        mMissionsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // Load the missions details screen
                Intent intent = new Intent(MissionsGridActivity.this, MissionDetails.class);
                intent.putExtra(MissionDetails.MISSION, new BetterJSONObject(mMissions.get(position)));
                intent.putExtra(MissionDetails.LOCATION_EXPANSION, MissionsActivity.RECOMMENDED_MISSIONS_EXPANSION[mMissionsCurrentExpansionLevel]);
                startActivity(intent);
            }
        });

        mLoading = (ProgressBar) findViewById(R.id.loading);
        mLoadingDescription = (TextView) findViewById(R.id.loading_description);
        mNoMissionsContainer = (ViewGroup) findViewById(R.id.no_recommended_missions);

        mApp = (INaturalistApp)getApplication();
        mHelper = new ActivityHelper(this);

        if (savedInstanceState == null) {
            mMissionsCurrentExpansionLevel = intent.getIntExtra(MissionsGridActivity.MISSIONS_EXPANSION_LEVEL, 0);
            mTaxonId = intent.getIntExtra(TAXON_ID, -1);

        } else {
            mMissions = loadListFromBundle(savedInstanceState, "mMissions");
            mMissionsCurrentExpansionLevel = savedInstanceState.getInt("mMissionsCurrentExpansionLevel");
            mTaxonId = savedInstanceState.getInt("mTaxonId");
        }
    }

    private void loadMissions(String actionName) {
        Object object = mApp.getServiceResult(actionName);
        BetterJSONObject resultsObject;
        JSONArray results = null;

        resultsObject = (BetterJSONObject) object;
        if ((resultsObject == null) || (resultsObject.getJSONArray("results") == null)) {
            refreshViewState();
            return;
        }

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

        if (mMissions.size() == 0) {
            // No missions - see if we can expand our search grid to find more
            mMissionsCurrentExpansionLevel++;
            if (mMissionsCurrentExpansionLevel < MissionsActivity.RECOMMENDED_MISSIONS_EXPANSION.length) {
                // Still more search expansions left to try out

                mMissions = null; // So it'll show up in the UI as still loading missions
                float nextExpansion = MissionsActivity.RECOMMENDED_MISSIONS_EXPANSION[mMissionsCurrentExpansionLevel];

                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_MISSIONS_BY_TAXON, null, MissionsGridActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
                serviceIntent.putExtra(INaturalistService.EXPAND_LOCATION_BY_DEGREES, nextExpansion);
                if (mTaxonId > -1) serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxonId);
                startService(serviceIntent);
            }
        }

    }

    private void refreshViewState() {
        if (mMissions == null) {
            mMissionsGrid.setVisibility(View.INVISIBLE);
            mLoading.setVisibility(View.VISIBLE);
            mLoadingDescription.setVisibility(View.VISIBLE);
            mNoMissionsContainer.setVisibility(View.GONE);

            if (mMissionsCurrentExpansionLevel == 0) {
                mLoadingDescription.setText(R.string.searching_your_area);
            } else {
                mLoadingDescription.setText(R.string.expanding_your_search_area);
            }
        } else {
            mLoading.setVisibility(View.GONE);
            mLoadingDescription.setVisibility(View.GONE);

            if (mMissions.size() == 0) {
                // No missions found
                mMissionsGrid.setVisibility(View.GONE);
                mNoMissionsContainer.setVisibility(View.VISIBLE);

            } else {
                mMissionsGrid.setVisibility(View.VISIBLE);
                mNoMissionsContainer.setVisibility(View.GONE);

                mMissionsAdapter = new UserSpeciesAdapter(this, mMissions, UserSpeciesAdapter.VIEW_TYPE_CARDS, mMissionsGrid);
                mMissionsGrid.setAdapter(mMissionsAdapter);
            }
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveListToBundle(outState, mMissions, "mMissions");
        outState.putInt("mMissionsCurrentExpansionLevel", mMissionsCurrentExpansionLevel);
        outState.putInt("mTaxonId", mTaxonId);

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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return true;
    }


    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mMissionsReceiver = new MissionsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.MISSIONS_BY_TAXON_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mMissionsReceiver, filter, this);

        if (mMissions == null) {
            if (mTaxonId > -1) {
                // Load recommended missions by taxon ID - start the service requesting the missions for that taxon ID
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_MISSIONS_BY_TAXON, null, this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
                serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxonId);
                startService(serviceIntent);

            } else {
                // Load recommended missions (already loaded in the previous screen - the main missions activity)
                loadMissions(INaturalistService.RECOMMENDED_MISSIONS_RESULT);
            }
        }

        refreshViewState();
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

            int taxonId = extras.getInt(INaturalistService.TAXON_ID, 0);
            if (mTaxonId != taxonId) {
                // Result returned for other taxon - don't show it (could happen when switching quickly
                // between taxon-mission screens)
                return;
            }

            loadMissions(INaturalistService.MISSIONS_BY_TAXON_RESULT);
            refreshViewState();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mMissionsReceiver, this);
    }

}

