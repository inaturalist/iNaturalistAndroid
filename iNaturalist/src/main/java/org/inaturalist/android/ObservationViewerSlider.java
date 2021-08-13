package org.inaturalist.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.HashMap;
import java.util.Map;

import uk.co.senab.photoview.log.Logger;

public class ObservationViewerSlider extends AppCompatActivity {
    private ViewPager mPager;
    private ScreenSlidePagerAdapter mPagerAdapter;

    Map<Integer, Fragment> mFragmentsByPositions = new HashMap<>();
    int mLastPosition = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.obs_viewer_slider);

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
        String obsUri = data != null ? data.getStringExtra(ObservationEditor.OBS_URI) : null;
        Uri currentObsUri = mPagerAdapter.getObsUriByPosition(mPager.getCurrentItem());
        boolean shouldRefresh = (obsUri != null && !currentObsUri.toString().equals(obsUri));

        if (shouldRefresh) {
            // Moved back from obs editor into a different observation
            mPager.setCurrentItem(mPagerAdapter.getObsPosition(Uri.parse(obsUri)), false);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        private Cursor mCursor;
        private boolean mIsReadOnly;

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
            Integer obsId = null;
            boolean isExternalId = false;

            if (uri != null) {
                obsId = Integer.valueOf(uri.getLastPathSegment());
            } else if (obsJson != null) {
                BetterJSONObject obs = new BetterJSONObject(obsJson);
                obsId = obs.getInt("id");
                isExternalId = true;
            }

            if (obsId != null) {
                Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[]{String.valueOf(obsId)}, Observation.DEFAULT_SORT_ORDER);
                if (c.getCount() > 0) {
                    mIsReadOnly = false;
                }
                c.close();
            }

            if (mIsReadOnly) {
                // Show only one observation (don't allow swiping)
                mLastPosition = 0;
            } else if (!mIsReadOnly) {
                mIsReadOnly = false;
                SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
                String login = prefs.getString("username", null);
                String conditions = "(_synced_at IS NULL";
                if (login != null) {
                    conditions += " OR user_login = '" + login + "'";
                }
                conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

                mCursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                        conditions, null, Observation.DEFAULT_SORT_ORDER);

                // Find initial position according to the URI the activity was launched with
                mLastPosition = isExternalId ? getObsPosition(obsId, true) : getObsPosition(getIntent().getData());
            }
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

            if (!mIsReadOnly) {
                Uri obsUri = getObsUriByPosition(position);
                Bundle args = new Bundle();
                args.putString(ObservationViewerFragment.OBS_URI, obsUri.toString());
                fragment.setArguments(args);
            }

            mFragmentsByPositions.put(position, fragment);
            return fragment;
        }

        @Override
        public int getCount() {
            return mIsReadOnly ? 1 : mCursor.getCount();
        }
    }
}
