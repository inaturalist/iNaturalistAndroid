package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.viewpagerindicator.CirclePageIndicator;

public class TutorialActivity extends BaseFragmentActivity {

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


    private class TutorialAdapter extends FragmentPagerAdapter implements OnPageChangeListener {

        private AppCompatActivity mContext;
        private int mCount;

        public TutorialAdapter(AppCompatActivity context) {
            super(context.getSupportFragmentManager());
            mContext = context;

            String[] images = getResources().getStringArray(R.array.tutorial_images);
            mCount = images.length + 1; // +1 for the final "Let's get started" page
        }

        @Override
        public Fragment getItem(int position) {
            // Determine appropriate image/title/description for current page

            Resources res = getResources();
            Fragment fragment;

            Bundle args = new Bundle();
            String[] images = res.getStringArray(R.array.tutorial_images);

            if (position == images.length) {
                // Final page ("Let's get started")
                args.putBoolean("final_page", true);
            } else {
                int imageResId = res.getIdentifier("@drawable/" + images[position], "drawable", getApplicationContext().getPackageName());
                args.putInt("image", imageResId);

                args.putString("title", res.getStringArray(R.array.tutorial_titles)[position]);
                args.putString("description", res.getStringArray(R.array.tutorial_descriptions)[position]);
            }

            fragment = Fragment.instantiate(mContext, TutorialFragment.class.getName(), args);
            return fragment;
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
        }

        @Override
        public void onPageScrolled(int position, float arg1, int arg2) {
        }

        @Override
        public void onPageSelected(int position) {
            if (position == mCount - 1) {
                // Final page ("Let's get started")
                mIndicator.setVisibility(View.GONE);
            } else {
                mIndicator.setVisibility(View.VISIBLE);
            }
        }

    }

    private TutorialAdapter mAdapter;
    private ViewPager mViewPager;
    private CirclePageIndicator mIndicator;

    private INaturalistApp mApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tutorial);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#aaaaaa"));
        }

        Intent intent = getIntent();

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mIndicator = (CirclePageIndicator) findViewById(R.id.tutorial_indicator);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        mApp = (INaturalistApp) getApplicationContext();
        mAdapter = new TutorialAdapter(this);
        mViewPager.setAdapter(mAdapter);
        mViewPager.addOnPageChangeListener(mAdapter);
        mIndicator.setViewPager(mViewPager);

        mApp.detectUserCountryAndUpdateNetwork(this);

        SharedPreferences preferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        preferences.edit().putBoolean("first_time", false).apply();
    }
}

