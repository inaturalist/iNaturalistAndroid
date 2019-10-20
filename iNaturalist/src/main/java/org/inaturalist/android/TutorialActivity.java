package org.inaturalist.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;


import com.viewpagerindicator.CirclePageIndicator;

public class TutorialActivity extends BaseFragmentActivity {

    private ViewGroup mSwipe;

    @Override
    protected void onStart()
    {
        super.onStart();


    }

    @Override
    protected void onStop()
    {
        super.onStop();

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

            logOnboardingEvent(position);

            fragment = Fragment.instantiate(mContext, TutorialFragment.class.getName(), args);
            return fragment;
        }

        private void logOnboardingEvent(int position) {
            String eventName;

            switch (position) {
                case 5:
                    eventName = AnalyticsClient.EVENT_NAME_ONBOARDING_LOGIN;
                    break;
                case 4:
                    eventName = AnalyticsClient.EVENT_NAME_ONBOARDING_CONTRIBUTE;
                    break;
                case 3:
                    eventName = AnalyticsClient.EVENT_NAME_ONBOARDING_LEARN;
                    break;
                case 2:
                    eventName = AnalyticsClient.EVENT_NAME_ONBOARDING_SHARE;
                    break;
                case 1:
                    eventName = AnalyticsClient.EVENT_NAME_ONBOARDING_OBSERVE;
                    break;
                case 0:
                default:
                    eventName = AnalyticsClient.EVENT_NAME_ONBOARDING_LOGO;
                    break;
            }

            AnalyticsClient.getInstance().logEvent(eventName);
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
            mSwipe.setVisibility(position == 0 ? View.VISIBLE : View.GONE);

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

        mSwipe = (ViewGroup) findViewById(R.id.swipe_container);
        mSwipe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
            }
        });
        mSwipe.setOnTouchListener(new OnSwipeTouchListener(this) {
            public boolean onSwipeLeft() {
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
                return true;
            }
        });

        TextView swipeIndicator = (TextView) findViewById(R.id.swipe_indicator);
        TranslateAnimation animation = new TranslateAnimation(0.0f, 20.0f, 0.0f, 0.0f);
        animation.setDuration(700);
        animation.setRepeatCount(5);
        animation.setRepeatMode(Animation.REVERSE);

        animation.setFillAfter(false);
        swipeIndicator .startAnimation(animation);

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
