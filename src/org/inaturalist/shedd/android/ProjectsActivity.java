package org.inaturalist.shedd.android;

import java.util.ArrayList;

import org.inaturalist.shedd.android.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

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
import android.content.Context;

public class ProjectsActivity extends SherlockFragmentActivity {
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.projects);
        
        mViewPager = (ViewPager) findViewById(R.id.pager);
        
        setupUI();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
    
   
    private void setupUI() {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Add the tabs
        mTabsAdapter = new TabsAdapter(this, actionBar, mViewPager);
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.joined_projects),
                JoinedProjectsTab.class, null);
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.nearby_projects),
                NearByProjectsTab.class, null); 
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.featured_projects),
                FeaturedProjectsTab.class, null); 
    }
    
}
