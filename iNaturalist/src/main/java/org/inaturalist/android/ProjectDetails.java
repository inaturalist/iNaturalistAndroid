package org.inaturalist.android;

import org.json.JSONException;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ProjectDetails extends SherlockFragmentActivity implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {
    
    private Button mJoinLeaveProject;
    private TextView mProjectTitle;
    
    private INaturalistApp mApp;
    private BetterJSONObject mProject;

    MyPageAdapter mPageAdapter;
    private ViewPager mViewPager;
    private TabHost mTabHost;
    private ActivityHelper mHelper;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
        	this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = new ActivityHelper(this);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        
       
        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.project_details_action_bar, null);
        actionBar.setCustomView(customView);
        actionBar.setLogo(R.drawable.up_icon);
        
        final Intent intent = getIntent();
        setContentView(R.layout.project_details);
        
        // Add the tabs
        mViewPager = (ViewPager) findViewById(R.id.viewpager);

        // Tab Initialization
        initialiseTabHost();


        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mProject = (BetterJSONObject) intent.getSerializableExtra("project");
        } else {
            mProject = (BetterJSONObject) savedInstanceState.getSerializable("project");
        }

        // Fragments and ViewPager Initialization
        List<Fragment> fragments = getFragments();
        mPageAdapter = new MyPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(mPageAdapter);
        mViewPager.setOnPageChangeListener(this);


        mJoinLeaveProject = (Button) customView.findViewById(R.id.join_leave_project);
        mProjectTitle = (TextView) customView.findViewById(R.id.project_title);

        if (mProject == null) {
            finish();
            return;
        }
        
        mProjectTitle.setText(mProject.getString("title"));
        
        Boolean isJoined = mProject.getBoolean("joined");
        if ((isJoined != null) && (isJoined == true)) {
            mJoinLeaveProject.setText(R.string.leave);
            mJoinLeaveProject.setBackgroundResource(R.drawable.actionbar_leave_btn);
        } else {
            mJoinLeaveProject.setText(R.string.join);
            mJoinLeaveProject.setBackgroundResource(R.drawable.actionbar_join_btn);
        }
        
        mJoinLeaveProject.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Boolean isJoined = mProject.getBoolean("joined");
                if ((isJoined != null) && (isJoined == true)) {
                    mHelper.confirm(getString(R.string.leave_project), getString(R.string.leave_project_confirmation),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int buttonId) {
                                    // Leave the project
                                    mJoinLeaveProject.setText(R.string.join);
                                    mJoinLeaveProject.setBackgroundResource(R.drawable.actionbar_join_btn);
                                    mProject.put("joined", false);

                                    Intent serviceIntent = new Intent(INaturalistService.ACTION_LEAVE_PROJECT, null, ProjectDetails.this, INaturalistService.class);
                                    serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
                                    startService(serviceIntent);
                                }
                            }, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                }
                            }, R.string.yes, R.string.no);

                } else {
                    if (!isLoggedIn()) {
                        // User not logged-in - redirect to onboarding screen
                        startActivity(new Intent(ProjectDetails.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        return;
                    }

                    mJoinLeaveProject.setText(R.string.leave);
                    mJoinLeaveProject.setBackgroundResource(R.drawable.actionbar_leave_btn);
                    mProject.put("joined", true);
                    
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_JOIN_PROJECT, null, ProjectDetails.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
                    startService(serviceIntent);
                }

            }
        });
        
        try {
            // Get the project's taxa list
            int checkListId = mProject.getJSONObject("project_list").getInt("id");
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_CHECK_LIST, null, ProjectDetails.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.CHECK_LIST_ID, checkListId);
            startService(serviceIntent);  
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("project", mProject);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
    }

    @Override
    public void onBackPressed() {
    	Intent intent = new Intent();
    	Bundle bundle = new Bundle();
    	bundle.putSerializable("project", mProject);
    	intent.putExtras(bundle);

    	setResult(RESULT_OK, intent);      
        super.onBackPressed();
    }




     // Method to add a TabHost
    private static void AddTab(ProjectDetails activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
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

        Bundle bundle = new Bundle();
        bundle.putSerializable(ProjectDetailsAbout.KEY_PROJECT, mProject);

        ProjectDetailsAbout f1 = new ProjectDetailsAbout();
        f1.setArguments(bundle);
        ProjectDetailsCheckList f2 = new ProjectDetailsCheckList();
        f2.setArguments(bundle);
        fList.add(f1);
        fList.add(f2);

        return fList;
    }

    // Tabs Creation
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        ProjectDetails.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("about").setIndicator(getString(R.string.about)));
        ProjectDetails.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("check_list").setIndicator(getString(R.string.check_list)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            ((TextView) mTabHost.getTabWidget().getChildAt(0).findViewById(android.R.id.title)).setAllCaps(false);
            ((TextView) mTabHost.getTabWidget().getChildAt(1).findViewById(android.R.id.title)).setAllCaps(false);
        }

        mTabHost.getTabWidget().getChildAt(0).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(1).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));


        mTabHost.setOnTabChangedListener(this);
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    }

}
