package org.inaturalist.android;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTabHost;

public class ProjectsActivity extends FragmentActivity {
    private FragmentTabHost mTabHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.projects);
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getSupportFragmentManager(), R.id.tabFrameLayout);

        mTabHost.addTab(
                mTabHost.newTabSpec("joined").setIndicator(getString(R.string.joined_projects),
                        getResources().getDrawable(android.R.drawable.star_on)),
                        JoinedProjectsTab.class, null);
        mTabHost.addTab(
                mTabHost.newTabSpec("nearby").setIndicator(getString(R.string.nearby_projects),
                        getResources().getDrawable(android.R.drawable.star_on)),
                        NearByProjectsTab.class, null);
        mTabHost.addTab(
                mTabHost.newTabSpec("featured").setIndicator(getString(R.string.featured_projects),
                        getResources().getDrawable(android.R.drawable.star_on)),
                        FeaturedProjectsTab.class, null);
    }
}