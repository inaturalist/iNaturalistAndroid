package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;

import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;

public class ProjectNews extends BaseFragmentActivity {
    private static final String TAG = "ProjectNews";
    private INaturalistApp mApp;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mProject;

    private ActivityHelper mHelper;

    private ListView mNewsList;
    private ProjectNewsAdapter mNewsListAdapter;
    private ProgressBar mLoadingNewsList;
    private TextView mNewsListEmpty;

    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mNews;
    private ProjectNewsReceiver mProjectNewsReceiver;
    @State public Boolean mIsUserFeed;



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mIsUserFeed) {
            switch (item.getItemId()) {
                // Respond to the action bar's Up/Home button
                case android.R.id.home:
                    this.onBackPressed();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    } 
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        setContentView(R.layout.project_news);

        ActionBar actionBar = getSupportActionBar();


        mLoadingNewsList = (ProgressBar) findViewById(R.id.loading_news_list);
        mNewsListEmpty = (TextView) findViewById(R.id.news_list_empty);
        mNewsList = (ListView) findViewById(R.id.news_list);

        mNewsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                JSONObject item = (JSONObject) view.getTag();

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_ARTICLE_TITLE, item.optString("title", ""));
                    eventParams.put(AnalyticsClient.EVENT_PARAM_PARENT_TYPE, item.optString("parent_type", ""));
                    JSONObject parent = item.optJSONObject("parent");
                    if (parent == null) parent = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_PARENT_NAME, parent.optString("title", parent.optString("name", "")));

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEWS_OPEN_ARTICLE, eventParams);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }

                Intent intent = new Intent(ProjectNews.this, NewsArticle.class);
                intent.putExtra(NewsArticle.KEY_ARTICLE, new BetterJSONObject(item));
                intent.putExtra(NewsArticle.KEY_IS_USER_FEED, mIsUserFeed);
                startActivity(intent);
            }
        });

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mProject = (BetterJSONObject) intent.getSerializableExtra("project");
            mIsUserFeed = intent.getBooleanExtra("is_user_feed", false);
        }

        if ((mProject == null) && (!mIsUserFeed)) {
            finish();
            return;
        }

        actionBar.setTitle(R.string.news);

        if (!mIsUserFeed) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setLogo(R.drawable.ic_arrow_back);
        } else {
            onDrawerCreate(savedInstanceState);
        }

        refreshViewState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        safeUnregisterReceiver(mProjectNewsReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mProjectNewsReceiver = new ProjectNewsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(mIsUserFeed ? INaturalistService.ACTION_NEWS_RESULT : INaturalistService.ACTION_PROJECT_NEWS_RESULT);
        safeRegisterReceiver(mProjectNewsReceiver, filter);

        if (mNews == null) {
            Intent serviceIntent;
            if (mIsUserFeed) {
                // Get the user's news feed
                serviceIntent = new Intent(INaturalistService.ACTION_GET_NEWS, null, ProjectNews.this, INaturalistService.class);
            } else {
                // Get the project's news list
                serviceIntent = new Intent(INaturalistService.ACTION_GET_PROJECT_NEWS, null, ProjectNews.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
            }
            ContextCompat.startForegroundService(this, serviceIntent);
        }
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
					Logger.tag(TAG).error(e);
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

            mNewsListAdapter = new ProjectNewsAdapter(ProjectNews.this, mProject != null ? mProject.getJSONObject() : null, mNews);
            mNewsList.setAdapter(mNewsListAdapter);
            mNewsList.setVisibility(View.VISIBLE);
        }
    }
}
