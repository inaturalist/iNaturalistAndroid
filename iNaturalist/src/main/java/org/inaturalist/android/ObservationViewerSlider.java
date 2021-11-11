package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ObservationViewerSlider extends AppCompatActivity {
    private static final String TAG = "ObservationViewerSlider";
    private ViewPager mPager;
    private ScreenSlidePagerAdapter mPagerAdapter;

    Map<Integer, Fragment> mFragmentsByPositions = new HashMap<>();
    int mLastPosition = 0;

    private static final int OBS_RESULTS_BUFFER = 5;
    private INaturalistApp mApp;

    @Override
    public void onPause() {
        super.onPause();
        if (mPagerAdapter != null) {
            mPagerAdapter.onPause();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (mPagerAdapter != null) {
            mPagerAdapter.onResume();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.obs_viewer_slider);

        mApp = (INaturalistApp) getApplicationContext();

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        mPager.setCurrentItem(mLastPosition);

        mPager.clearOnPageChangeListeners();
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mLastPosition = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    @Override
    public void onBackPressed() {
        int position = mPager.getCurrentItem();
        Fragment fragment = mFragmentsByPositions.get(position);
        if (fragment != null) {
            ((ObservationViewerFragment)fragment).onBack();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        int position = mPager.getCurrentItem();
        Fragment fragment = mFragmentsByPositions.get(position);

        if (fragment == null) return;

        switch (requestCode) {
            // Just pass through these types of activity results
            case ObservationViewerFragment.OBSERVATION_PHOTOS_REQUEST_CODE:
            case ObservationViewerFragment.REQUEST_CODE_LOGIN:
            case ObservationViewerFragment.NEW_ID_REQUEST_CODE:
            case ObservationViewerFragment.SHARE_REQUEST_CODE:
                fragment.onActivityResult(requestCode, resultCode, data);
                return;

            case ObservationViewerFragment.REQUEST_CODE_EDIT_OBSERVATION:
                String obsUri = data != null ? data.getStringExtra(ObservationEditor.OBS_URI) : null;
                Uri currentObsUri = mPagerAdapter.getObsUriByPosition(mPager.getCurrentItem());
                boolean shouldRefresh = (obsUri != null && !currentObsUri.toString().equals(obsUri));

                if (shouldRefresh) {
                    // Moved back from obs editor into a different observation
                    mPager.setCurrentItem(mPagerAdapter.getObsPosition(Uri.parse(obsUri)), false);
                } else {
                    // Same observation - just pass through the activity result
                    fragment.onActivityResult(requestCode, resultCode, data);
                }
        }


    }


    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        private Cursor mCursor;
        private boolean mIsReadOnly;
        private List<JSONObject> mObsResults = null;
        private boolean mLoadingNextResults = false;
        private String mLatestSearchUuid = null;
        private ExploreSearchFilters mSearchFilters;
        private int mTotalResults;
        private int mCurrentResultsPage;
        private ExploreResultsReceiver mExploreResultsReceiver;
        private GetAdditionalObsReceiver mGetAdditionalObsReceiver;
        private boolean mNoMoreObsLeft = false;

        public void onPause() {
            BaseFragmentActivity.safeUnregisterReceiver(mExploreResultsReceiver, ObservationViewerSlider.this);
            BaseFragmentActivity.safeUnregisterReceiver(mGetAdditionalObsReceiver, ObservationViewerSlider.this);
        }


        public void onResume() {
            if (mSearchFilters != null) {
                mExploreResultsReceiver = new ExploreResultsReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(INaturalistService.EXPLORE_GET_OBSERVATIONS_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mExploreResultsReceiver, filter, ObservationViewerSlider.this);
            }

            if (!mIsReadOnly) {
                mGetAdditionalObsReceiver = new GetAdditionalObsReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(INaturalistService.ACTION_GET_ADDITIONAL_OBS_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mGetAdditionalObsReceiver, filter, ObservationViewerSlider.this);
            }
        }


        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);


            Intent intent = getIntent();
            Uri uri = intent.getData();
            mIsReadOnly = false;

            if ((uri != null) && (uri.getScheme().equals("https"))) {
                String path = uri.getPath();
                if (path.toLowerCase().startsWith("/observations/")) {
                    mIsReadOnly = true;
                }
            } else if (intent.getBooleanExtra("read_only", false)) {
                mIsReadOnly = true;
            }


            String obsJson = intent.getStringExtra("observation");
            String obsResults = intent.getStringExtra("observation_results");
            Integer obsIndex = intent.getIntExtra("observation_index", 0);
            Integer totalResults = intent.getIntExtra("total_results", 0);
            Integer resultsPage = intent.getIntExtra("results_page", 0);
            Integer obsId = null;
            boolean isExternalId = false;

            if (uri != null) {
                obsId = Integer.valueOf(uri.getLastPathSegment());
            } else if (obsJson != null) {
                BetterJSONObject obs = new BetterJSONObject(obsJson);
                obsId = obs.getInt("id");
                isExternalId = true;
            }

            String currentUser = mApp.currentUserLogin();
            if ((obsId != null) && (currentUser != null)) {
                Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ? and user_login = ?", new String[]{String.valueOf(obsId), currentUser}, Observation.DEFAULT_SORT_ORDER);
                if (c.getCount() > 0) {
                    mIsReadOnly = false;
                }
                c.close();
            }

            if (obsResults != null) {
                mLastPosition = obsIndex;
                try {
                    JSONArray array = new JSONArray(obsResults);
                    mObsResults = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        mObsResults.add(array.getJSONObject(i));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mTotalResults = totalResults;
                mCurrentResultsPage = resultsPage;
                mSearchFilters = (ExploreSearchFilters) intent.getSerializableExtra("search_filters");

                mExploreResultsReceiver = new ExploreResultsReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(INaturalistService.EXPLORE_GET_OBSERVATIONS_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mExploreResultsReceiver, filter, ObservationViewerSlider.this);

            } else if (mIsReadOnly) {
                // Show only one observation (don't allow swiping)
                mLastPosition = 0;
            } else if (!mIsReadOnly) {
                refreshCursor();

                // Find initial position according to the URI the activity was launched with
                mLastPosition = isExternalId ? getObsPosition(obsId, true) : getObsPosition(getIntent().getData());

                mGetAdditionalObsReceiver = new GetAdditionalObsReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(INaturalistService.ACTION_GET_ADDITIONAL_OBS_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mGetAdditionalObsReceiver, filter, ObservationViewerSlider.this);
            }
        }

        private void refreshCursor() {
            SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
            String login = prefs.getString("username", null);
            String conditions = "(_synced_at IS NULL";
            if (login != null) {
                conditions += " OR user_login = '" + login + "'";
            }
            conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

            mCursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                    conditions, null, Observation.DEFAULT_SORT_ORDER);

        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);
            mFragmentsByPositions.remove(position);
        }

        public int getObsPosition(Uri uri) {
            Integer initialObsId = Integer.valueOf(uri.getLastPathSegment());
            return getObsPosition(initialObsId, false);
        }
        public int getObsPosition(int initialObsId, boolean isExternal) {
            mCursor.moveToFirst();
            do {
                Long obsId = mCursor.getLong(mCursor.getColumnIndexOrThrow(isExternal ? Observation.ID : Observation._ID));
                if (obsId == initialObsId) {
                    break;
                }
            } while (mCursor.moveToNext());

            return mCursor.getPosition();
        }

        public Uri getObsUriByPosition(int position) {
            mCursor.moveToPosition(position);
            Observation obs = new Observation(mCursor);
            return obs.getUri();
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = new ObservationViewerFragment();

            if (mObsResults != null) {
                Bundle args = new Bundle();
                args.putString(ObservationViewerFragment.OBSERVATION, mObsResults.get(position).toString());
                fragment.setArguments(args);

                if (position >= mObsResults.size() - OBS_RESULTS_BUFFER) {
                    // Reaching the end of the result list - download the next page
                    loadNextResultsPage();
                }
            } else if (!mIsReadOnly) {
                Uri obsUri = getObsUriByPosition(position);
                Bundle args = new Bundle();
                args.putString(ObservationViewerFragment.OBS_URI, obsUri.toString());
                fragment.setArguments(args);

                if (position >= mCursor.getCount() - OBS_RESULTS_BUFFER) {
                    // Reaching the end of the result list - download the next page
                    loadNextResultsPage();
                }
            }

            mFragmentsByPositions.put(position, fragment);
            return fragment;
        }

        @Override
        public int getCount() {
            if (mObsResults != null) {
                return mObsResults.size();
            } else if (mIsReadOnly) {
                return 1;
            } else {
                return mCursor.getCount();
            }
        }

        private void loadNextResultsPage() {
            if (!mApp.isNetworkAvailable()) return;

            if ((mObsResults == null) && (!mLoadingNextResults && !mNoMoreObsLeft && mApp.loggedIn())) {
                mLoadingNextResults = true;
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_ADDITIONAL_OBS, null, ObservationViewerSlider.this, INaturalistService.class);
                ContextCompat.startForegroundService(ObservationViewerSlider.this, serviceIntent);

            } else if ((mObsResults != null) && (!mLoadingNextResults && mObsResults.size() < mTotalResults)) {
                mLoadingNextResults = true;

                String action = INaturalistService.ACTION_EXPLORE_GET_OBSERVATIONS;

                Intent serviceIntent = new Intent(action, null, ObservationViewerSlider.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.FILTERS, mSearchFilters);
                serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, mCurrentResultsPage + 1);
                mLatestSearchUuid = UUID.randomUUID().toString();
                serviceIntent.putExtra(INaturalistService.UUID, mLatestSearchUuid);
                ContextCompat.startForegroundService(ObservationViewerSlider.this, serviceIntent);

            }
        }

        private class ExploreResultsReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();

                Logger.tag(TAG).debug("ExploreResultsReceiver - ObservationViewerSlider");

                String uuid = intent.getStringExtra(INaturalistService.UUID);

                if ((uuid == null) || (mLatestSearchUuid == null)) {
                    Logger.tag(TAG).debug("Null UUID or latest search UUID");
                    return;
                }

                if (!mLatestSearchUuid.equals(uuid)) {
                    Logger.tag(TAG).debug("UUID Mismatch %s - %s", uuid, mLatestSearchUuid);
                    return;
                }

                mLoadingNextResults = false;

                String error = extras.getString("error");
                if (error != null) {
                    ActivityHelper helper = new ActivityHelper(ObservationViewerSlider.this);
                    helper.alert(String.format(getString(R.string.couldnt_load_results), error));
                    return;
                }
                Logger.tag(TAG).debug("ExploreResultsReceiver - ObservationViewerSlider - loading results");

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
                    mCurrentResultsPage = resultsObject.getInt("page");
                    if (count != null) {
                        totalResults = count;
                        results = resultsJSON.getJSONArray();
                    }
                }

                if (results == null) {
                    return;
                }

                ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

                for (int i = 0; i < results.length(); i++) {
                    try {
                        JSONObject item = results.getJSONObject(i);
                        resultsArray.add(item);
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                    }
                }

                Logger.tag(TAG).debug("ExploreResultsReceiver - ObservationViewerSlider - refreshing results: " + resultsArray.size());

                // Paginated results - append to old ones
                mObsResults.addAll(resultsArray);
                mTotalResults = totalResults;

                notifyDataSetChanged();
            }
        }

        private class GetAdditionalObsReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();

                refreshCursor();
                notifyDataSetChanged();

                mLoadingNextResults = false;

                if (extras != null) {
                    int obsCount = extras.getInt(INaturalistService.OBSERVATION_COUNT);
                    if (obsCount == 0) {
                        // No more observations left to download
                        mNoMoreObsLeft = true;
                    }
                }
            }
        }
    }



}
