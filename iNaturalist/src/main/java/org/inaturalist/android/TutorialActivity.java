package org.inaturalist.android;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;

public class TutorialActivity extends SherlockFragmentActivity {
	
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

        private SherlockFragmentActivity mContext;
        private int mCount;
        
        public TutorialAdapter(SherlockFragmentActivity context) {
            super(context.getSupportFragmentManager());
            mContext = context;
            
            
            String inatNetwork = mApp.getInaturalistNetworkMember();
            
            String[] images;
            
            if (inatNetwork == null) {
            	// No network selected - use default tutorial images
            	images = getResources().getStringArray(R.array.tutorial_images);
            } else {
            	// Use network specific tutorial images
            	String imagesArrayName = mApp.getStringResourceByName("inat_tutorial_images_" + inatNetwork);
            	images = mApp.getStringArrayResourceByName(imagesArrayName);
            }
            mCount = images.length;
        }

        @Override
        public Fragment getItem(int position) {
            Bundle args = new Bundle();
            args.putInt("id", position);
            Fragment fragment = Fragment.instantiate(mContext, TutorialFragment.class.getName(), args);
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
        public void onPageScrolled(int arg0, float arg1, int arg2) {
        }

        @SuppressLint("NewApi")
		@Override
        public void onPageSelected(int arg0) {
        	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        		invalidateOptionsMenu();
        	}
        }
        
    }

    private static final int ACTION_PREVIOUS = 0x100;
    private static final int ACTION_NEXT = 0x101;

    private TutorialAdapter mAdapter;
    private ViewPager mViewPager;
	private INaturalistApp mApp;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tutorial);
        
        Intent intent = getIntent();

        mViewPager = (ViewPager) findViewById(R.id.pager);
        
        final ActionBar actionBar = getSupportActionBar();
        
        if ((intent == null) || (!intent.getBooleanExtra("first_time", false))) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        actionBar.setIcon(android.R.color.transparent);

       mApp = (INaturalistApp) getApplicationContext();
       mAdapter = new TutorialAdapter(this);
       mViewPager.setAdapter(mAdapter);
       mViewPager.setOnPageChangeListener(mAdapter);

       mApp.detectUserCountryAndUpdateNetwork(this);
        
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if ((keyCode == KeyEvent.KEYCODE_BACK)) {
    		SharedPreferences preferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        	preferences.edit().putBoolean("first_time", false).apply();
    	}
    	return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	SharedPreferences preferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);

        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
        	preferences.edit().putBoolean("first_time", false).apply();
            finish();
            return true;
        case ACTION_NEXT:
            if (mViewPager.getCurrentItem() == mAdapter.getCount() - 1) {
                // Pressed the finish button
                preferences.edit().putBoolean("first_time", false).apply();
                finish();
                return true;
            }
            mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
            return true;
        case ACTION_PREVIOUS:
            mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Add either a "next" or "finish" button to the action bar, depending on which page is currently selected.
        
        if (mViewPager.getCurrentItem() > 0) {
            MenuItem item = menu.add(Menu.NONE, ACTION_PREVIOUS, Menu.NONE, R.string.previous);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
        
        MenuItem item2 = menu.add(Menu.NONE, ACTION_NEXT, Menu.NONE,
                (mViewPager.getCurrentItem() == mAdapter.getCount() - 1)
                ? R.string.finish : R.string.next);
        item2.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return true;
    }
}
