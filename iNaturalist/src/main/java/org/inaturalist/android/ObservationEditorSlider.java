package org.inaturalist.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.tinylog.Logger;

import java.util.HashMap;
import java.util.Map;

public class ObservationEditorSlider extends AppCompatActivity {
    private ViewPager mPager;
    private PagerAdapter mPagerAdapter;

    Map<Integer, Fragment> mFragmentsByPositions = new HashMap<>();
    int mLastPosition = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.obs_editor_slider);

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
                ObservationEditor fragment = (ObservationEditor) mFragmentsByPositions.get(mLastPosition);

                if (fragment != null) {
                    // Tell the fragment it needs to save current observation
                    fragment.saveObservation();
                }

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
            ((ObservationEditor)fragment).onBack();
        }
    }


    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        private Cursor mCursor = null;

        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);

            if ((getIntent().getAction() == Intent.ACTION_INSERT) ||
                    (getIntent().getAction() == Intent.ACTION_SEND) ||
                    (getIntent().getAction() == Intent.ACTION_SEND_MULTIPLE)) {
                // New observation / share image to iNat
                return;
            }

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
            Long initialObsId = Long.valueOf(getIntent().getData().getLastPathSegment());
            mCursor.moveToFirst();
            do {
                Long obsId = mCursor.getLong(mCursor.getColumnIndexOrThrow(Observation._ID));
                if (obsId == initialObsId) {
                    break;
                }
            } while (mCursor.moveToNext());

            mLastPosition = mCursor.getPosition();
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = new ObservationEditor();

            Bundle args = new Bundle();
            if (mCursor != null) {
                mCursor.moveToPosition(position);
                Observation obs = new Observation(mCursor);
                args.putString(ObservationEditor.OBS_URI, obs.getUri().toString());
            } else if ((getIntent().getAction() != Intent.ACTION_SEND) &&
                    (getIntent().getAction() != Intent.ACTION_SEND_MULTIPLE)) {
                args.putString(ObservationEditor.OBS_URI, getIntent().getData().toString());
            }

            fragment.setArguments(args);

            mFragmentsByPositions.put(position, fragment);
            return fragment;
        }

        @Override
        public int getCount() {
            return mCursor != null ? mCursor.getCount() : 1;
        }
    }
}
