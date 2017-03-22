package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.amplitude.api.Amplitude;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class UserActivity extends BaseFragmentActivity implements UserActivitiesAdapter.IOnUpdateViewed {


    public static String TAG = "UserActivity";

    private static final String VIEW_TYPE_MY_CONTENT = "my_content";
    private static final String VIEW_TYPE_NEWS = "news";

    private ActivityHelper mHelper;
    private INaturalistApp mApp;
    private String mViewType;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    
    private ProgressBar mLoadingNews;
    private TextView mNewsEmpty;
    private PullToRefreshListView mNewsList;
    private ProgressBar mLoadingActivities;
    private TextView mActivityEmpty;
    private PullToRefreshListView mActivityList;
    private TextView mActivityEmptySubTitle;

    private NewsReceiver mNewsReceiver;

    private ArrayList<JSONObject> mNews;
    private ArrayList<JSONObject> mActivities;
    private ProjectNewsAdapter mNewsListAdapter;
    private UserActivitiesAdapter mActivitiesListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_activity);

        setTitle(R.string.activity);

        getSupportActionBar().setElevation(0);

        Amplitude.getInstance().initialize(this, getString(R.string.amplitude_api_key)).enableForegroundTracking(getApplication());

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_USER_ACTIVITY);

        mHelper = new ActivityHelper(this);

        mApp = (INaturalistApp)getApplication();

        if (savedInstanceState != null) {
            mViewType = savedInstanceState.getString("mViewType");
            mNews = loadListFromBundle(savedInstanceState, "mNews");
            mActivities = loadListFromBundle(savedInstanceState, "mActivities");

        } else {
            SharedPreferences settings = mApp.getPrefs();
            mViewType = VIEW_TYPE_MY_CONTENT;

            // Get the user's news feed
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_NEWS, null, UserActivity.this, INaturalistService.class);
            startService(serviceIntent);
            if (mApp.loggedIn()) {
                // Get the user's activities
                Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, UserActivity.this, INaturalistService.class);
                startService(serviceIntent2);
            } else {
                // Only works if user is logged in
                mActivities = new ArrayList<>();
            }
        }

        SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String username = pref.getString("username", null);

        onDrawerCreate(savedInstanceState);

        initializeTabs();
        refreshViewState();
    }

    private void addTab(int position, String title) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);

        tab.setText(title);
        /*
        View view = LayoutInflater.from(this).inflate(R.layout.tab, null);
        TextView tabTitle = (TextView) view.findViewById(R.id.tab_title);
        tabTitle.setText(title);

        tab.setCustomView(view);
        */
    }

    private void refreshTabs(int pos) {
        for (int i = 0; i < 2; i++) {
            View view = mTabLayout.getTabAt(i).getCustomView();
            view.findViewById(R.id.bottom_line).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.tab_title)).setTextColor(Color.parseColor("#84000000"));
        }

        View view = mTabLayout.getTabAt(pos).getCustomView();
        view.findViewById(R.id.bottom_line).setVisibility(View.VISIBLE);
        ((TextView)view.findViewById(R.id.tab_title)).setTextColor(Color.parseColor("#000000"));
    }


    private void initializeTabs() {
        mTabLayout = (TabLayout) findViewById(R.id.tabs);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        mViewPager.setOffscreenPageLimit(3); // So we wouldn't have to recreate the views every time
        ActivityPageAdapter adapter = new ActivityPageAdapter(this);
        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);

        addTab(0, getString(R.string.my_content));
        addTab(1, getString(R.string.news));
        //refreshTabs(0);

        ViewPager.OnPageChangeListener pageListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 1:
                        mViewType = VIEW_TYPE_NEWS;
                        break;
                    case 0:
                    default:
                        mViewType = VIEW_TYPE_MY_CONTENT;
                        break;
                }

                //refreshTabs(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mViewPager.addOnPageChangeListener(pageListener);
    }


    public class ActivityPageAdapter extends PagerAdapter {
        final int PAGE_COUNT = 2;
        private Context mContext;

        public ActivityPageAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.news_list, collection, false);

            switch (position) {
                case 1:
                    // News (site/project news)
                    mLoadingNews = (ProgressBar) layout.findViewById(R.id.loading);
                    mNewsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mNewsEmpty.setText(R.string.no_news_yet);
                    ((TextView) layout.findViewById(R.id.empty_sub_title)).setVisibility(View.GONE);
                    mNewsList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mNewsList.setMode(PullToRefreshBase.Mode.DISABLED);

                    AdapterView.OnItemClickListener onNewsClick = new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            JSONObject item = (JSONObject) view.getTag();
                            if (item == null) return;

                            try {
                                JSONObject eventParams = new JSONObject();
                                eventParams.put(AnalyticsClient.EVENT_PARAM_ARTICLE_TITLE, item.optString("title", ""));
                                eventParams.put(AnalyticsClient.EVENT_PARAM_PARENT_TYPE, item.optString("parent_type", ""));
                                JSONObject parent = item.optJSONObject("parent");
                                if (parent == null) parent = new JSONObject();
                                eventParams.put(AnalyticsClient.EVENT_PARAM_PARENT_NAME, parent.optString("title", parent.optString("name", "")));

                                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEWS_OPEN_ARTICLE, eventParams);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }


                            Intent intent = new Intent(UserActivity.this, NewsArticle.class);
                            intent.putExtra(NewsArticle.KEY_ARTICLE, new BetterJSONObject(item));
                            intent.putExtra(NewsArticle.KEY_IS_USER_FEED, true);
                            startActivity(intent);
                        }
                    };

                    mNewsList.setOnItemClickListener(onNewsClick);

                    break;

                case 0:
                    // User Activity (My Content)
                    mLoadingActivities = (ProgressBar) layout.findViewById(R.id.loading);
                    mActivityEmpty = (TextView) layout.findViewById(R.id.empty);
                    mActivityEmpty.setText(R.string.no_updates_yet);
                    mActivityEmptySubTitle = (TextView) layout.findViewById(R.id.empty_sub_title);
                    mActivityEmptySubTitle.setText(R.string.you_will_only_receive_updates);
                    mActivityList = (PullToRefreshListView) layout.findViewById(R.id.list);
                    mActivityList.setMode(PullToRefreshBase.Mode.DISABLED);

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

    private void refreshViewState() {
        if (mLoadingNews != null) {
            if (mNews == null) {
                mLoadingNews.setVisibility(View.VISIBLE);
                mNewsList.setVisibility(View.GONE);
                mNewsEmpty.setVisibility(View.GONE);
            } else {
                mLoadingNews.setVisibility(View.GONE);

                if (mNews.size() == 0) {
                    mNewsEmpty.setVisibility(View.VISIBLE);
                } else {
                    mNewsEmpty.setVisibility(View.GONE);
                }

                mNewsListAdapter = new ProjectNewsAdapter(UserActivity.this, null, mNews);
                mNewsList.setAdapter(mNewsListAdapter);
                mNewsList.setVisibility(View.VISIBLE);
            }
        }

        if (mLoadingActivities != null) {
            if (mActivities == null) {
                mLoadingActivities.setVisibility(View.VISIBLE);
                mActivityList.setVisibility(View.GONE);
                mActivityEmpty.setVisibility(View.GONE);
                mActivityEmptySubTitle.setVisibility(View.GONE);
            } else {
                mLoadingActivities.setVisibility(View.GONE);

                if (mActivities.size() == 0) {
                    mActivityEmpty.setVisibility(View.VISIBLE);
                    mActivityEmptySubTitle.setVisibility(View.VISIBLE);
                } else {
                    mActivityEmpty.setVisibility(View.GONE);
                    mActivityEmptySubTitle.setVisibility(View.GONE);
                }

                mActivitiesListAdapter = new UserActivitiesAdapter(UserActivity.this, mActivities, UserActivity.this);
                mActivityList.setAdapter(mActivitiesListAdapter);
                mActivityList.setVisibility(View.VISIBLE);
            }
        }
    }


    @Override
    protected void onPause() {
        try {
            if (mNewsReceiver != null) unregisterReceiver(mNewsReceiver);
        } catch (Exception exc) {
            exc.printStackTrace();
        }
        super.onPause();
    }


    private class NewsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
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

            if (intent.getAction().equals(INaturalistService.UPDATES_RESULT)) {
                mActivities = resultsArray;
            } else {
                mNews = resultsArray;
            }

            refreshViewState();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mNewsReceiver = new NewsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.ACTION_NEWS_RESULT);
        filter.addAction(INaturalistService.UPDATES_RESULT);
        registerReceiver(mNewsReceiver, filter);
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
        outState.putString("mViewType", mViewType);
        saveListToBundle(outState, mNews, "mNews");
        saveListToBundle(outState, mActivities, "mActivities");

        super.onSaveInstanceState(outState);
    }


    @Override
    public void onUpdateViewed(Observation obs, int position) {
        try {
            JSONObject item = mActivities.get(position);
            item.put("viewed", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
