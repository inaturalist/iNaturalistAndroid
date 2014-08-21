package org.inaturalist.shedd.android;

import java.util.ArrayList;
import java.util.List;

import org.inaturalist.shedd.android.R;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.widget.ImageView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class TutorialActivity extends SherlockFragmentActivity {
    
    private class TutorialAdapter extends FragmentPagerAdapter implements OnPageChangeListener {

        private SherlockFragmentActivity mContext;
        private int mCount;
        
        public TutorialAdapter(SherlockFragmentActivity context) {
            super(context.getSupportFragmentManager());
            mContext = context;
            String[] images = getResources().getStringArray(R.array.tutorial_images);
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

        @Override
        public void onPageSelected(int arg0) {
            invalidateOptionsMenu();
        }
        
    }

    private static final int ACTION_PREVIOUS = 0x100;
    private static final int ACTION_NEXT = 0x101;

    private TutorialAdapter mAdapter;
    private ViewPager mViewPager;
    
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
        
       mAdapter = new TutorialAdapter(this);
       mViewPager.setAdapter(mAdapter);
       mViewPager.setOnPageChangeListener(mAdapter);
       

    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            finish();
            return true;
        case ACTION_NEXT:
            if (mViewPager.getCurrentItem() == mAdapter.getCount() - 1) {
                // Pressed the finish button
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
