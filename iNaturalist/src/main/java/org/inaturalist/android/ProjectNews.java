package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ProjectNews extends SherlockFragmentActivity {
    private INaturalistApp mApp;
    private BetterJSONObject mProject;

    private ActivityHelper mHelper;

    private ListView mNewsList;
    private ProjectNewsAdapter mNewsListAdapter;
    private ProgressBar mLoadingNewsList;
    private TextView mNewsListEmpty;

    private ArrayList<JSONObject> mNews;
    private ProjectNewsReceiver mProjectNewsReceiver;

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
        setContentView(R.layout.project_news);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));
        actionBar.setLogo(R.drawable.ic_arrow_back_gray_24dp);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.news);

        mLoadingNewsList = (ProgressBar) findViewById(R.id.loading_news_list);
        mNewsListEmpty = (TextView) findViewById(R.id.news_list_empty);
        mNewsList = (ListView) findViewById(R.id.news_list);

        mNewsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                JSONObject item = (JSONObject) view.getTag();
                Intent intent = new Intent(ProjectNews.this, NewsArticle.class);
                intent.putExtra(NewsArticle.KEY_ARTICLE, new BetterJSONObject(item));
                startActivity(intent);
            }
        });

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mProject = (BetterJSONObject) intent.getSerializableExtra("project");

            // Get the project's news list
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_PROJECT_NEWS, null, ProjectNews.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
            startService(serviceIntent);

        } else {
            mProject = (BetterJSONObject) savedInstanceState.getSerializable("project");
            mNews = loadListFromBundle(savedInstanceState, "mNews");
        }

        if (mProject == null) {
            finish();
            return;
        }

        refreshViewState();
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
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("project", mProject);
        saveListToBundle(outState, mNews, "mNews");

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
    	try {
            if (mProjectNewsReceiver != null) unregisterReceiver(mProjectNewsReceiver);
    	} catch (Exception exc) {
    		exc.printStackTrace();
    	}
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mProjectNewsReceiver = new ProjectNewsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.ACTION_PROJECT_NEWS_RESULT);
        registerReceiver(mProjectNewsReceiver, filter);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    private class ProjectNewsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_project_news), error));
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            SerializableJSONArray resultsJSON;

            if (isSharedOnApp) {
                resultsJSON = (SerializableJSONArray) mApp.getServiceResult(intent.getAction());
            } else {
                resultsJSON = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.RESULTS);
            }

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

            mNews = resultsArray;
            refreshViewState();
        }
    }

    private void refreshViewState() {
        if (mNews == null) {
            mLoadingNewsList.setVisibility(View.VISIBLE);
            mNewsList.setVisibility(View.GONE);
            mNewsListEmpty.setVisibility(View.GONE);
        } else {
            mLoadingNewsList.setVisibility(View.GONE);

            if (mNews.size() == 0) {
                mNewsListEmpty.setVisibility(View.VISIBLE);
            } else {
                mNewsListEmpty.setVisibility(View.GONE);
            }

            mNewsListAdapter = new ProjectNewsAdapter(ProjectNews.this, mProject.getJSONObject(), mNews);
            mNewsList.setAdapter(mNewsListAdapter);
            mNewsList.setVisibility(View.VISIBLE);
        }
    }
}
