package org.inaturalist.manitoba.android;

import java.util.ArrayList;
import java.util.List;

import com.flurry.android.FlurryAgent;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

import org.inaturalist.manitoba.android.R;

public class GuidesActivity extends BaseFragmentActivity implements OnTabChangeListener, OnPageChangeListener {
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
        super.onCreate(savedInstanceState);

        setContentView(R.layout.guides);
	    onDrawerCreate(savedInstanceState);
        
        mViewPager = (ViewPager) findViewById(R.id.viewpager);
        
        // Tab Initialization
        initialiseTabHost();

        // Fragments and ViewPager Initialization
        List<Fragment> fragments = getFragments();
        mPageAdapter = new MyPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(mPageAdapter);
        mViewPager.setOnPageChangeListener(this);
    }
    
     
    // Method to add a TabHost
    private static void AddTab(GuidesActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

    // Manages the Tab changes, synchronizing it with Pages
    public void onTabChanged(String tag) {
        int pos = this.mTabHost.getCurrentTab();
        this.mViewPager.setCurrentItem(pos);
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
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        GuidesActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("all_guides").setIndicator(getString(R.string.all_guides)));
        GuidesActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("my_guides").setIndicator(getString(R.string.my_guides)));
        GuidesActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("nearby_guides").setIndicator(getString(R.string.nearby_guides)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            ((TextView) mTabHost.getTabWidget().getChildAt(0).findViewById(android.R.id.title)).setAllCaps(false);
            ((TextView) mTabHost.getTabWidget().getChildAt(1).findViewById(android.R.id.title)).setAllCaps(false);
            ((TextView) mTabHost.getTabWidget().getChildAt(2).findViewById(android.R.id.title)).setAllCaps(false);
        }

        mTabHost.setOnTabChangedListener(this);
    }
   
}
