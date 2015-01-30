package org.inaturalist.android;

import org.json.JSONException;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class ProjectDetails extends SherlockFragmentActivity {
    
    private Button mJoinLeaveProject;
    private TextView mProjectTitle;
    
    private INaturalistApp mApp;
    private BetterJSONObject mProject;

    private TabsAdapter mTabsAdapter;
    private ViewPager mViewPager;

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
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        
       
        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.project_details_action_bar, null);
        actionBar.setCustomView(customView);
        
        final Intent intent = getIntent();
        setContentView(R.layout.project_details);
        
        // Add the tabs
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mTabsAdapter = new TabsAdapter(this, actionBar, mViewPager);

        
        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mProject = (BetterJSONObject) intent.getSerializableExtra("project");
        } else {
            mProject = (BetterJSONObject) savedInstanceState.getSerializable("project");
        }
        
        Bundle bundle = new Bundle();
        bundle.putSerializable(ProjectDetailsAbout.KEY_PROJECT, mProject);
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.about),
                ProjectDetailsAbout.class, bundle);
        mTabsAdapter.addTab(actionBar.newTab().setText(R.string.check_list),
                ProjectDetailsCheckList.class, bundle); 

        mJoinLeaveProject = (Button) customView.findViewById(R.id.join_leave_project);
        mProjectTitle = (TextView) customView.findViewById(R.id.project_title);
        
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
                	AlertDialog.Builder dialog = new AlertDialog.Builder(ProjectDetails.this);
                	dialog.setTitle(R.string.leave_project);
                	dialog.setMessage(R.string.leave_project_confirmation);
                	dialog.setCancelable (false);
                	dialog.setNegativeButton(R.string.no, null);
                	dialog.setPositiveButton(R.string.yes,
                			new DialogInterface.OnClickListener () {
                		public void onClick (DialogInterface dialog, int buttonId) {
                			// Leave the project
                			mJoinLeaveProject.setText(R.string.join);
                			mJoinLeaveProject.setBackgroundResource(R.drawable.actionbar_join_btn);
                			mProject.put("joined", false);

                			Intent serviceIntent = new Intent(INaturalistService.ACTION_LEAVE_PROJECT, null, ProjectDetails.this, INaturalistService.class);
                			serviceIntent.putExtra(INaturalistService.PROJECT_ID, mProject.getInt("id"));
                			startService(serviceIntent);
                		}
                	});
                	dialog.setIcon (android.R.drawable.ic_dialog_alert);
                	dialog.show();


                } else {
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
 
}
