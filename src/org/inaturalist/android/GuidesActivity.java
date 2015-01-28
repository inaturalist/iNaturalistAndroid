package org.inaturalist.android;

import java.util.ArrayList;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTabHost;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

public class GuidesActivity extends BaseFragmentActivity {
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;
    
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
        
        mViewPager = (ViewPager) findViewById(R.id.pager);
        
        setupUI();
    }
    
   
    private void setupUI() {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Add the tabs
        mTabsAdapter = new TabsAdapter(this, actionBar, mViewPager);
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.all_guides),
                AllGuidesTab.class, null);
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.my_guides),
                MyGuidesTab.class, null); 
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.nearby_guides),
                NearByGuidesTab.class, null); 
    }
    
}
