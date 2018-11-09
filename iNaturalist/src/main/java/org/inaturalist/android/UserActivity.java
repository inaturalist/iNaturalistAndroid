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
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import javax.crypto.Mac;

public class UserActivity extends BaseFragmentActivity implements UserActivitiesAdapter.IOnUpdateViewed {


    public static String TAG = "UserActivity";

    private static final String VIEW_TYPE_MY_CONTENT = "my_content";
    private static final String VIEW_TYPE_MY_FOLLOWING = "following";
    private static final String VIEW_TYPE_NEWS = "news";

    private ActivityHelper mHelper;
    private INaturalistApp mApp;
    @State public String mViewType;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    
    private ProgressBar mLoadingNews;
    private TextView mNewsEmpty;
    private PullToRefreshListView mNewsList;
    private ProgressBar mLoadingActivities;
    private TextView mActivityEmpty;
    private PullToRefreshListView mActivityList;
    private TextView mActivityEmptySubTitle;
    private ProgressBar mLoadingFollowingActivities;
    private TextView mFollowingActivityEmpty;
    private PullToRefreshListView mFollowingActivityList;
    private TextView mFollowingActivityEmptySubTitle;


    private NewsReceiver mNewsReceiver;

    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mNews;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mActivities;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mFollowingActivities;
    private ProjectNewsAdapter mNewsListAdapter;
    private UserActivitiesAdapter mActivitiesListAdapter;
    private UserActivitiesAdapter mFollowingActivitiesListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        setContentView(R.layout.user_activity);

        setTitle(R.string.activity);

        getSupportActionBar().setElevation(0);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_USER_ACTIVITY);

        mHelper = new ActivityHelper(this);

        mApp = (INaturalistApp)getApplication();

        if (savedInstanceState == null) {
            mViewType = VIEW_TYPE_MY_CONTENT;
        }

        onDrawerCreate(savedInstanceState);

        initializeTabs();
        refreshViewState();
    }

    private void addTab(int position, String title) {
        TabLayout.Tab tab = mTabLayout.getTabAt(position);

        tab.setText(title);
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
        addTab(1, getString(R.string.following));
        addTab(2, getString(R.string.news));

        ViewPager.OnPageChangeListener pageListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 2:
                        mViewType = VIEW_TYPE_NEWS;
                        break;
                    case 1:
                        mViewType = VIEW_TYPE_MY_FOLLOWING;
                        break;
                    case 0:
                    default:
                        mViewType = VIEW_TYPE_MY_CONTENT;
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mViewPager.addOnPageChangeListener(pageListener);
    }


    public class ActivityPageAdapter extends PagerAdapter {
        final int PAGE_COUNT = 3;
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
                case 2:
                    // News (site/project news)
                    mLoadingNews = (ProgressBar) layout.findViewById(R.id.loading);
                    mNewsEmpty = (TextView) layout.findViewById(R.id.empty);
                    mNewsEmpty.setText(R.string.no_news_yet);
                    ((TextView) layout.findViewById(R.id.empty_sub_title)).setVisibility(View.GONE);
                    mNewsList = (PullToRefreshListView) layout.findViewById(R.id.list);

                    initPullToRefreshList(mNewsList, layout);

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

                case 1:
                    // User Activity (Following)
                    mLoadingFollowingActivities = (ProgressBar) layout.findViewById(R.id.loading);
                    mFollowingActivityEmpty = (TextView) layout.findViewById(R.id.empty);
                    mFollowingActivityEmpty.setText(R.string.no_updates_yet);
                    mFollowingActivityEmptySubTitle = (TextView) layout.findViewById(R.id.empty_sub_title);
                    mFollowingActivityEmptySubTitle.setText(R.string.you_will_only_receive_updates_when_following);
                    mFollowingActivityList = (PullToRefreshListView) layout.findViewById(R.id.list);

                    initPullToRefreshList(mFollowingActivityList, layout);

                    break;

                case 0:
                    // User Activity (My Content)
                    mLoadingActivities = (ProgressBar) layout.findViewById(R.id.loading);
                    mActivityEmpty = (TextView) layout.findViewById(R.id.empty);
                    mActivityEmpty.setText(R.string.no_updates_yet);
                    mActivityEmptySubTitle = (TextView) layout.findViewById(R.id.empty_sub_title);
                    mActivityEmptySubTitle.setText(R.string.you_will_only_receive_updates_when_creating);
                    mActivityList = (PullToRefreshListView) layout.findViewById(R.id.list);

                    initPullToRefreshList(mActivityList, layout);

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

                if (mNewsListAdapter == null) {
                    mNewsListAdapter = new ProjectNewsAdapter(UserActivity.this, null, mNews);
                    mNewsList.setAdapter(mNewsListAdapter);
                    mNewsList.setVisibility(View.VISIBLE);
                }
            }
        }

        if (mLoadingActivities != null) {
            TabLayout.Tab myContentTab = mTabLayout.getTabAt(0);

            if (mActivities == null) {
                mLoadingActivities.setVisibility(View.VISIBLE);
                mActivityList.setVisibility(View.GONE);
                mActivityEmpty.setVisibility(View.GONE);
                mActivityEmptySubTitle.setVisibility(View.GONE);

                myContentTab.setText(R.string.my_content);
            } else {
                mLoadingActivities.setVisibility(View.GONE);

                if (mActivities.size() == 0) {
                    mActivityEmpty.setVisibility(View.VISIBLE);
                    mActivityEmptySubTitle.setVisibility(View.VISIBLE);
                } else {
                    mActivityEmpty.setVisibility(View.GONE);
                    mActivityEmptySubTitle.setVisibility(View.GONE);
                }

                SharedPreferences settings = mApp.getPrefs();
                int unreadActivities = settings.getInt("unread_activities", 0);
                if (unreadActivities == 0) {
                    myContentTab.setText(R.string.my_content);
                } else {
                    myContentTab.setText(String.format("%s (%d)", getString(R.string.my_content), unreadActivities));
                }

                if (mActivitiesListAdapter == null) {
                    mActivitiesListAdapter = new UserActivitiesAdapter(UserActivity.this, mActivities, UserActivity.this);
                    mActivityList.setAdapter(mActivitiesListAdapter);
                    mActivityList.setVisibility(View.VISIBLE);
                }
            }
        }

        if (mLoadingFollowingActivities != null) {
            if (mFollowingActivities == null) {
                mLoadingFollowingActivities.setVisibility(View.VISIBLE);
                mFollowingActivityList.setVisibility(View.GONE);
                mFollowingActivityEmpty.setVisibility(View.GONE);
                mFollowingActivityEmptySubTitle.setVisibility(View.GONE);
            } else {
                mLoadingFollowingActivities.setVisibility(View.GONE);

                if (mFollowingActivities.size() == 0) {
                    mFollowingActivityEmpty.setVisibility(View.VISIBLE);
                    mFollowingActivityEmptySubTitle.setVisibility(View.VISIBLE);
                } else {
                    mFollowingActivityEmpty.setVisibility(View.GONE);
                    mFollowingActivityEmptySubTitle.setVisibility(View.GONE);
                }

                if (mFollowingActivitiesListAdapter == null) {
                    mFollowingActivitiesListAdapter = new UserActivitiesAdapter(UserActivity.this, mFollowingActivities, UserActivity.this);
                    mFollowingActivityList.setAdapter(mFollowingActivitiesListAdapter);
                    mFollowingActivityList.setVisibility(View.VISIBLE);
                }
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        safeUnregisterReceiver(mNewsReceiver);
        if (mActivitiesListAdapter != null) mActivitiesListAdapter.unregisterReceivers();
        if (mFollowingActivitiesListAdapter != null) mFollowingActivitiesListAdapter.unregisterReceivers();
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

            if (resultsJSON == null) {
                refreshViewState();
                return;
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
                mActivityList.onRefreshComplete();
                mActivityList.refreshDrawableState();
                mActivitiesListAdapter = null;

                // Count how many unread activities are there
                int unreadActivities = 0;

                for (int i = 0; i < mActivities.size(); i++) {
                    JSONObject activity = mActivities.get(i);
                    try {
                        if (!activity.getBoolean("viewed")) unreadActivities++;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                SharedPreferences settings = mApp.getPrefs();
                settings.edit().putInt("unread_activities", unreadActivities).commit();

            } else if (intent.getAction().equals(INaturalistService.UPDATES_FOLLOWING_RESULT)) {
                mFollowingActivities = resultsArray;
                mFollowingActivityList.onRefreshComplete();
                mFollowingActivityList.refreshDrawableState();
                mFollowingActivitiesListAdapter = null;
            } else {
                mNews = resultsArray;
                mNewsList.onRefreshComplete();
                mNewsList.refreshDrawableState();
                mNewsListAdapter = null;
            }

            refreshViewState();
        }
    }

    private void initPullToRefreshList(PullToRefreshListView pullToRefresh, ViewGroup layout) {
        pullToRefresh.getLoadingLayoutProxy().setPullLabel(getResources().getString(R.string.pull_to_refresh));
        pullToRefresh.getLoadingLayoutProxy().setReleaseLabel(getResources().getString(R.string.release_to_refresh));
        pullToRefresh.getLoadingLayoutProxy().setRefreshingLabel(getResources().getString(R.string.refreshing));
        pullToRefresh.setReleaseRatio(2.5f);

        pullToRefresh.setEmptyView(layout.findViewById(R.id.empty));

        pullToRefresh.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<ListView>() {
            @Override
            public void onRefresh(PullToRefreshBase<ListView> refreshView) {
                Intent serviceIntent;

                if (refreshView == mNewsList) {
                    // Get the user's news
                    serviceIntent = new Intent(INaturalistService.ACTION_GET_NEWS, null, UserActivity.this, INaturalistService.class);
                } else {
                    // Get the user's activities
                    serviceIntent = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, UserActivity.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.FOLLOWING, refreshView == mActivityList ? false : true);
                }

                ContextCompat.startForegroundService(UserActivity.this, serviceIntent);
            }
        });
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
        filter.addAction(INaturalistService.UPDATES_FOLLOWING_RESULT);
        safeRegisterReceiver(mNewsReceiver, filter);


        // Get the user's news feed
        if (mNews == null) {
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_NEWS, null, UserActivity.this, INaturalistService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }

        if (mApp.loggedIn()) {
            if (mActivities == null) {
                // Get the user's activities
                Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, UserActivity.this, INaturalistService.class);
                serviceIntent2.putExtra(INaturalistService.FOLLOWING, false);
                ContextCompat.startForegroundService(this, serviceIntent2);
            }
            if (mFollowingActivities == null) {
                // Get the user's activities (following obs)
                Intent serviceIntent3 = new Intent(INaturalistService.ACTION_GET_USER_UPDATES, null, UserActivity.this, INaturalistService.class);
                serviceIntent3.putExtra(INaturalistService.FOLLOWING, true);
                ContextCompat.startForegroundService(this, serviceIntent3);
            }
        } else {
            // Only works if user is logged in
            mActivities = new ArrayList<>();
            mFollowingActivities = new ArrayList<>();
        }


        TabLayout.Tab myContentTab = mTabLayout.getTabAt(0);
        SharedPreferences settings = mApp.getPrefs();
        int unreadActivities = settings.getInt("unread_activities", 0);
        if (unreadActivities == 0) {
            myContentTab.setText(R.string.my_content);
        } else {
            myContentTab.setText(String.format("%s (%d)", getString(R.string.my_content), unreadActivities));
        }

        if (mFollowingActivitiesListAdapter != null) mFollowingActivitiesListAdapter.registerReceivers();
        if (mActivitiesListAdapter != null) mActivitiesListAdapter.registerReceivers();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }


    @Override
    public void onUpdateViewed(Observation obs, int position) {
        try {
            if (mActivities == null) return;
            if (position >= mActivities.size()) return;

            ArrayList<JSONObject> activities = (obs.id ==  mActivities.get(position).getInt("resource_id")) ? mActivities : mFollowingActivities;
            JSONObject item = activities.get(position);

            SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            int unreadActivities = prefs.getInt("unread_activities", 1);

            if (!item.getBoolean("viewed")) {
                // Find all other activities that have the same resource ID (e.g. if there are several updates
                // for the same observation) and mark them all as read/viewed.
                for (int i = 0; i < activities.size(); i++) {
                    JSONObject activity = activities.get(i);

                    if (activity.getInt("resource_id") == item.getInt("resource_id")) {
                        if (!activity.getBoolean("viewed")) {
                            activity.put("viewed", true);
                            unreadActivities--;
                        }
                    }
                }
            }

            prefs.edit().putInt("unread_activities", unreadActivities > 0 ? unreadActivities : 0).commit();

            if (activities == mActivities) {
                mActivitiesListAdapter.notifyDataSetChanged();
            } else {
                mFollowingActivitiesListAdapter.notifyDataSetChanged();
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
