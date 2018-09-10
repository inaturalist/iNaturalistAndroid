package org.inaturalist.android;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager.LayoutParams;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import uk.co.senab.photoview.HackyViewPager;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ProfilePhotoViewer extends AppCompatActivity {
    private static String TAG = "ProfilePhotoViewer";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	@State(AndroidStateBundlers.JSONObjectBundler.class) public JSONObject mUser;
	private HackyViewPager mViewPager;

    public static final String USER = "observation";

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.profile_photo);

        Intent intent = getIntent();

        try {
        	if (savedInstanceState == null) {
                mUser = new JSONObject(intent.getStringExtra(USER));
			}

			String fullName = mUser.getString("name");
			if ((fullName == null) || (fullName.length() == 0)) {
				actionBar.setTitle(mUser.getString("login"));
			} else {
				actionBar.setTitle(fullName);
			}

        } catch (JSONException e) {
        	e.printStackTrace();
        }

        mViewPager = (HackyViewPager) findViewById(R.id.user_pic_view_pager);
		if (mUser != null) {
            mViewPager.setAdapter(new ProfilePhotoPagerAdapter(mUser));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
		Bridge.saveInstanceState(this, outState);
    }
 
    
 	class ProfilePhotoPagerAdapter extends PagerAdapter {
 		List<String> mImages;

        // Load online photos for profile
 		public ProfilePhotoPagerAdapter(JSONObject profile) {
 			mImages = new ArrayList<String>();
			String url;
			if (profile.has("original_user_icon_url") && !profile.isNull("original_user_icon_url")) {
				url = profile.optString("original_user_icon_url");
			} else if (profile.has("medium_user_icon_url") && !profile.isNull("medium_user_icon_url")) {
				url = profile.optString("medium_user_icon_url");
			} else if (profile.has("icon_url") && !profile.isNull("icon_url")) {
				url = profile.optString("icon_url");
			} else {
				url = profile.optString("user_icon_url");
			}
			mImages.add(url);
 		}

 		@Override
 		public int getCount() {
 			return mImages.size();
 		}

 		@Override
 		public View instantiateItem(ViewGroup container, int position) {
 			View layout = (View) getLayoutInflater().inflate(R.layout.observation_photo, null, false);
 			container.addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
 			ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

			String imageUrl = mImages.get(position);
			loading.setVisibility(View.VISIBLE);
			imageView.setVisibility(View.INVISIBLE);
			final PhotoViewAttacher attacher = new PhotoViewAttacher(imageView);
			// Show a photo
			UrlImageViewHelper.setUrlDrawable(imageView, imageUrl, R.drawable.ic_account_circle_black_48dp, new UrlImageViewCallback() {
				@Override
				public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
					loading.setVisibility(View.GONE);
					imageView.setVisibility(View.VISIBLE);
					attacher.update();
				}

				@Override
				public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
					// Scale down the image if it's too big for the GL renderer
					loadedBitmap = ImageUtils.scaleDownBitmapIfNeeded(ProfilePhotoViewer.this, loadedBitmap);
					return loadedBitmap;
				}
			});

 			return layout;
 		}

 		@Override
 		public void destroyItem(ViewGroup container, int position, Object object) {
 			container.removeView((View) object);
 		}

 		@Override
 		public boolean isViewFromObject(View view, Object object) {
 			return view == object;
 		}



 	}


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
