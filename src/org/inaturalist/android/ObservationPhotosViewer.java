package org.inaturalist.android;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.inaturalist.android.INaturalistService.LoginType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.co.senab.photoview.HackyViewPager;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager.LayoutParams;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.*;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ObservationPhotosViewer extends SherlockActivity {
    private static String TAG = "ObservationPhotosViewer";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private JSONObject mObservation;
	private HackyViewPager mViewPager;

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
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        actionBar.setLogo(R.drawable.up_icon);
        actionBar.setTitle(R.string.observation_photos);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.observation_photos);
        
        Intent intent = getIntent();

        try {
        	if (savedInstanceState == null) {
        		String observationString = intent.getStringExtra("observation");
        		if (observationString != null) mObservation = new JSONObject(observationString);
        	} else {
        		mObservation = new JSONObject(savedInstanceState.getString("observation"));
        	}
        } catch (JSONException e) {
        	e.printStackTrace();
        }

        mViewPager = (HackyViewPager) findViewById(R.id.id_pic_view_pager);
        mViewPager.setAdapter(new IdPicsPagerAdapter(mObservation));
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
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("observation", mObservation.toString());
    }
 
    
 	class IdPicsPagerAdapter extends PagerAdapter {
 		int mDefaultTaxonIcon;
 		List<String> mImages;
 		
 		public IdPicsPagerAdapter(JSONObject observation) {
 			mImages = new ArrayList<String>();
 			mDefaultTaxonIcon = observationIcon(observation);

 			JSONArray photos = observation.optJSONArray("observation_photos");
 			if ((photos != null) && (photos.length() > 0)) {
 				// Show the photos
 				for (int i = 0; i < photos.length(); i++) {
 					JSONObject photo = photos.optJSONObject(i);
 					if (photo != null) {
 						JSONObject innerPhoto = photo.optJSONObject("photo");
 						if (innerPhoto != null) {
 							String photoUrl = innerPhoto.has("original_url") ? innerPhoto.optString("original_url") : innerPhoto.optString("large_url");
 							if (photoUrl != null) {
 								mImages.add(photoUrl);
 							}
 						}
 					}
 				}
 			} else {
 				// Show taxon icon
 				mImages.add(null);
 			}
 		}

 		@Override
 		public int getCount() {
 			return mImages.size();
 		}

 		@Override
 		public View instantiateItem(ViewGroup container, int position) {

 			String imageUrl = mImages.get(position);
 			View layout = (View) getLayoutInflater().inflate(R.layout.observation_photo, null, false);
 			container.addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
 			ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

 			if (imageUrl == null) {
 				// Show a default taxon image
 				imageView.setImageResource(mDefaultTaxonIcon);
 			} else {
 				loading.setVisibility(View.VISIBLE);
 				imageView.setVisibility(View.INVISIBLE);
 				final PhotoViewAttacher attacher = new PhotoViewAttacher(imageView);
 				// Show a photo
 				UrlImageViewHelper.setUrlDrawable(imageView, imageUrl, mDefaultTaxonIcon, new UrlImageViewCallback() {
					@Override
					public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
						loading.setVisibility(View.GONE);
						imageView.setVisibility(View.VISIBLE);
						attacher.update();
					}
				});
 			}

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

 	public static int observationIcon(JSONObject o) {
 		if (!o.has("iconic_taxon_name") || o.isNull("iconic_taxon_name")) {
 			return R.drawable.unknown_large;
 		}
 		String iconicTaxonName;
 		try {
 			iconicTaxonName = o.getString("iconic_taxon_name");
 		} catch (JSONException e) {
 			e.printStackTrace();
 			return R.drawable.unknown_large;
 		}

 		if (iconicTaxonName == null) {
 			return R.drawable.unknown_large;
 		} else if (iconicTaxonName.equals("Animalia")) {
 			return R.drawable.animalia_large;
 		} else if (iconicTaxonName.equals("Plantae")) {
 			return R.drawable.plantae_large;
 		} else if (iconicTaxonName.equals("Chromista")) {
 			return R.drawable.chromista_large;
 		} else if (iconicTaxonName.equals("Fungi")) {
 			return R.drawable.fungi_large;
 		} else if (iconicTaxonName.equals("Protozoa")) {
 			return R.drawable.protozoa_large;
 		} else if (iconicTaxonName.equals("Actinopterygii")) {
 			return R.drawable.actinopterygii_large;
 		} else if (iconicTaxonName.equals("Amphibia")) {
 			return R.drawable.amphibia_large;
 		} else if (iconicTaxonName.equals("Reptilia")) {
 			return R.drawable.reptilia_large;
 		} else if (iconicTaxonName.equals("Aves")) {
 			return R.drawable.aves_large;
 		} else if (iconicTaxonName.equals("Mammalia")) {
 			return R.drawable.mammalia_large;
 		} else if (iconicTaxonName.equals("Mollusca")) {
 			return R.drawable.mollusca_large;
 		} else if (iconicTaxonName.equals("Insecta")) {
 			return R.drawable.insecta_large;
 		} else if (iconicTaxonName.equals("Arachnida")) {
 			return R.drawable.arachnida_large;
 		} else {
 			return R.drawable.unknown_large;
 		}


 	}


}
