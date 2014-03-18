package org.inaturalist.tpwd.android;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.inaturalist.tpwd.android.R;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class TutorialActivity extends SherlockFragmentActivity {
	
	private static final String TOS_FILENAME = "tos.html";
    
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

        @SuppressLint("NewApi")
		@Override
        public void onPageSelected(int arg0) {
        	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
        		invalidateOptionsMenu();
        	}
        }
        
    }

    private static final int ACTION_PREVIOUS = 0x100;
    private static final int ACTION_NEXT = 0x101;

    private TutorialAdapter mAdapter;
    private ViewPager mViewPager;
    
    private boolean mShowTOS = false;
    
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
        } else {
        	// First time using the app - show the TOS dialog at the end of the tutorial
        	mShowTOS = true;
        }
        
       mAdapter = new TutorialAdapter(this);
       mViewPager.setAdapter(mAdapter);
       mViewPager.setOnPageChangeListener(mAdapter);
       

    }
    
    /** Shows the TOS dialog */
    private void showTOSDialog() {
    	final ScrollView tosContainer = new ScrollView(this);
    	
    	// Read TOS text
		try {
			InputStream is;
			is = getAssets().open(TOS_FILENAME);
			int size = is.available();
			byte[] buffer = new byte[size];
			is.read(buffer);
			is.close();
			String text = new String(buffer);

			TextView tosText = new TextView(this);
			tosText.setText(Html.fromHtml(text));
			tosText.setMovementMethod(LinkMovementMethod.getInstance()); 

			tosContainer.addView(tosText, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		} catch (IOException e) {
			e.printStackTrace();
		}

    	FragmentManager fm = getSupportFragmentManager();
    	DialogFragment dialog = new DialogFragment(){
    		@Override
    		public Dialog onCreateDialog(Bundle savedInstanceState) {
    			// Use the Builder class for convenient dialog construction
    			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    			builder
    			.setTitle(R.string.terms_of_service)
    			.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					setResult(RESULT_OK);
    					finish();
    				}
    			})
    			.setNegativeButton(R.string.decline,  new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int id) {
    					// Notify caller that we should close the app
    					setResult(RESULT_CANCELED);
    					finish();
    				}
    			})
    			.setView(tosContainer);

    			// Create the AlertDialog object and return it
    			return builder.create();
    		}
    	};
    	dialog.setCancelable(false);
    	dialog.show(fm, "TOS_DIALOG_FRAGMENT");    	
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
            	// Finished tutorial

            	if (mShowTOS) {
            		// Show TOS dialog
            		showTOSDialog();
            	} else {
            		// Pressed the finish button
            		setResult(RESULT_OK);
            		finish();
            	}
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
