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

    UserSpeciesAdapter mMissionsAdapter;
    private PullToRefreshGridViewExtended mMissionsGrid;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private ArrayList<JSONObject> mMissions;
    private ProgressBar mLoading;
    private MissionsReceiver mMissionsReceiver;

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
                intent.putExtra("mission", new BetterJSONObject(mMissions.get(position)));
                startActivity(intent);
            }
        });

        mLoading = (ProgressBar) findViewById(R.id.loading);

        mApp = (INaturalistApp)getApplication();
        mHelper = new ActivityHelper(this);

        if (savedInstanceState == null) {
            if (intent.hasExtra("taxon_id")) {
                // Load recommended missions by taxon ID - start the service requesting the missions for that taxon ID
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_MISSIONS_BY_TAXON, null, this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.USERNAME, mApp.currentUserLogin());
                serviceIntent.putExtra(INaturalistService.TAXON_ID, intent.getIntExtra("taxon_id", 0));
                startService(serviceIntent);

            } else {
                // Load recommended missions (already loaded in the previous screen - the main missions activity)
                loadMissions(INaturalistService.RECOMMENDED_MISSIONS_RESULT);
            }

        } else {
            mMissions = loadListFromBundle(savedInstanceState, "mMissions");
        }

        refreshViewState();
    }

    private void loadMissions(String actionName) {
        Object object = mApp.getServiceResult(actionName);
        BetterJSONObject resultsObject;
        JSONArray results = null;

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
                e.printStackTrace();
            }
        }

        mMissions = resultsArray;
    }

    private void refreshViewState() {
        if (mMissions == null) {
            mMissionsGrid.setVisibility(View.INVISIBLE);
            mLoading.setVisibility(View.VISIBLE);
        } else {
            mLoading.setVisibility(View.GONE);
            mMissionsGrid.setVisibility(View.VISIBLE);

            mMissionsAdapter = new UserSpeciesAdapter(this, mMissions, UserSpeciesAdapter.VIEW_TYPE_CARDS, mMissionsGrid);
            mMissionsGrid.setAdapter(mMissionsAdapter);
            mMissionsGrid.setOnScrollListener(mMissionsAdapter);
        }
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
        registerReceiver(mMissionsReceiver, filter);
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

            loadMissions(INaturalistService.MISSIONS_BY_TAXON_RESULT);
            refreshViewState();
        }
    }
}

