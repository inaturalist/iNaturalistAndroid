package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;

import com.actionbarsherlock.app.ActionBar;
import com.flurry.android.FlurryAgent;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabWidget;
import android.widget.TextView;

public class ProjectsActivity extends BaseFragmentActivity implements OnTabChangeListener, OnPageChangeListener {
    MyPageAdapter mPageAdapter;
    private ViewPager mViewPager;
    private TabHost mTabHost;   
    
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
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.NoActionBarShadowTheme);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.projects);
	    onDrawerCreate(savedInstanceState);
        
        mViewPager = (ViewPager) findViewById(R.id.viewpager);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));

        // Tab Initialization
        initialiseTabHost();

        // Fragments and ViewPager Initialization
        List<Fragment> fragments = getFragments();
        mPageAdapter = new MyPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(mPageAdapter);
        mViewPager.setOnPageChangeListener(this);
    }
    
    // Method to add a TabHost
    private static void AddTab(ProjectsActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

    private View createTabContent(int titleRes) {
        View view = LayoutInflater.from(this).inflate(R.layout.tab, null);
        TextView tabTitle = (TextView) view.findViewById(R.id.tab_title);
        tabTitle.setText(titleRes);

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

        JoinedProjectsTab f1 = new JoinedProjectsTab();
        NearByProjectsTab f2 = new NearByProjectsTab();
        FeaturedProjectsTab f3 = new FeaturedProjectsTab();
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

        ProjectsActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("joined_projects").setIndicator(createTabContent(R.string.joined_projects)));
        ProjectsActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("nearby_projects").setIndicator(createTabContent(R.string.nearby_projects)));
        ProjectsActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("featured_projects").setIndicator(createTabContent(R.string.featured_projects)));

        mTabHost.getTabWidget().setDividerDrawable(null);

        mTabHost.setOnTabChangedListener(this);

        refreshTabs(0);
    }
  
}
