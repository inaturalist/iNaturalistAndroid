package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;



import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabWidget;
import android.widget.TextView;

public class GuidesActivity extends BaseFragmentActivity implements OnTabChangeListener, OnPageChangeListener {
    MyPageAdapter mPageAdapter;
    private ViewPager mViewPager;
    private TabHost mTabHost;
    private List<Fragment> mFragments;
    private INaturalistApp mApp;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        getSupportFragmentManager().putFragment(outState, "all_guides", mFragments.get(0));
        getSupportFragmentManager().putFragment(outState, "my_guides", mFragments.get(1));
        getSupportFragmentManager().putFragment(outState, "nearby_guides", mFragments.get(2));

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.NoActionBarShadowTheme);
        super.onCreate(savedInstanceState);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.guides);
	    onDrawerCreate(savedInstanceState);

        getSupportActionBar().setElevation(0);

        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        mViewPager.setOffscreenPageLimit(3);

        // Tab Initialization
        initialiseTabHost();

        // Fragments and ViewPager Initialization
        if (savedInstanceState == null) {
            mFragments = getFragments();
        } else {
            mFragments = new ArrayList<Fragment>();
            mFragments.add(getSupportFragmentManager().getFragment(savedInstanceState, "all_guides"));
            mFragments.add(getSupportFragmentManager().getFragment(savedInstanceState, "my_guides"));
            mFragments.add(getSupportFragmentManager().getFragment(savedInstanceState, "nearby_guides"));
        }

        mPageAdapter = new MyPageAdapter(getSupportFragmentManager(), mFragments);
        mViewPager.setAdapter(mPageAdapter);
        mViewPager.setOnPageChangeListener(this);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ((BaseTab) mPageAdapter.getItem(mViewPager.getCurrentItem())).loadProjects();
            }
        }, 1000);
    }
    
     
    // Method to add a TabHost
    private static void AddTab(GuidesActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

    private View createTabContent(String titleRes, String fallbackRes) {
        View view = LayoutInflater.from(this).inflate(R.layout.tab, null);
        TextView tabTitle = (TextView) view.findViewById(R.id.tab_title);
        tabTitle.setText(mApp.getStringResourceByName(titleRes, fallbackRes));

        return view;
    }



    // Manages the Tab changes, synchronizing it with Pages
    public void onTabChanged(String tag) {
        int pos = this.mTabHost.getCurrentTab();
        this.mViewPager.setCurrentItem(pos);

        refreshTabs(pos);
    }

    private void refreshTabs(int pos) {
        TabWidget tabWidget = mTabHost.getTabWidget();
        for (int i = 0; i < 3; i++) {
            tabWidget.getChildAt(i).findViewById(R.id.bottom_line).setVisibility(View.GONE);
            ((TextView) tabWidget.getChildAt(i).findViewById(R.id.tab_title)).setTextColor(Color.parseColor("#84000000"));
        }

        tabWidget.getChildAt(pos).findViewById(R.id.bottom_line).setVisibility(View.VISIBLE);
        ((TextView)tabWidget.getChildAt(pos).findViewById(R.id.tab_title)).setTextColor(Color.parseColor("#000000"));

        if (mPageAdapter != null) {
            ((BaseTab) mPageAdapter.getItem(pos)).loadProjects();
        }
    }

    @Override
    public void onPageScrollStateChanged(int arg0) {
    }

    // Manages the Page changes, synchronizing it with Tabs
    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2) {
        int pos = this.mViewPager.getCurrentItem();
        this.mTabHost.setCurrentTab(pos);
    }

    @Override
    public void onPageSelected(int arg0) {
    }

    private List<Fragment> getFragments(){
        List<Fragment> fList = new ArrayList<Fragment>();

        AllGuidesTab f1 = new AllGuidesTab();
        MyGuidesTab f2 = new MyGuidesTab();
        NearByGuidesTab f3 = new NearByGuidesTab();
        fList.add(f1);
        fList.add(f2);
        fList.add(f3);

        return fList;
    }

    // Tabs Creation
    @SuppressLint("NewApi")
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        GuidesActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("all_guides").setIndicator(createTabContent("all_guides_all_caps", "all_guides")));
        GuidesActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("my_guides").setIndicator(createTabContent("my_guides_all_caps", "my_guides")));
        GuidesActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("nearby_guides").setIndicator(createTabContent("nearby_guides_all_caps", "nearby_guides")));

        mTabHost.getTabWidget().setDividerDrawable(null);

        mTabHost.setOnTabChangedListener(this);

        refreshTabs(0);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search:
                Intent intent = new Intent(this, ItemSearchActivity.class);
                intent.putExtra(ItemSearchActivity.RESULT_VIEWER_ACTIVITY, GuideDetails.class);
                intent.putExtra(ItemSearchActivity.RESULT_VIEWER_ACTIVITY_PARAM_NAME, "guide");
                intent.putExtra(ItemSearchActivity.SEARCH_HINT_TEXT, BaseGuidesTab.getSearchFilterTextHint(this));
                intent.putExtra(ItemSearchActivity.SEARCH_URL, BaseGuidesTab.getSearchUrl((INaturalistApp) getApplicationContext()));
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        ((INaturalistApp) getApplicationContext()).onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
