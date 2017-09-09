package org.inaturalist.android;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.co.senab.photoview.HackyViewPager;
import uk.co.senab.photoview.PhotoViewAttacher;

import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.LayoutParams;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class ObservationPhotosViewer extends AppCompatActivity {
    private static String TAG = "ObservationPhotosViewer";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private JSONObject mObservation;
	private HackyViewPager mViewPager;

	public static final String IS_NEW_OBSERVATION = "is_new_observation";
    public static final String OBSERVATION = "observation";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
    public static final String CURRENT_PHOTO_INDEX = "current_photo_index";
    public static final String READ_ONLY = "read_only";
    public static final String IS_TAXON = "is_taxon";

    public static final String SET_DEFAULT_PHOTO_INDEX = "set_default_photo_index";
    public static final String DELETE_PHOTO_INDEX = "delete_photo_index";

    private boolean mIsNewObservation;
    private int mObservationId;
    private int mCurrentPhotoIndex;
    private View mDeletePhoto;
    private boolean mReadOnly;
    private int mObservationIdInternal;
    private boolean mIsTaxon;

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
        actionBar.setLogo(R.drawable.ic_arrow_back);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.observation_photos);

        mDeletePhoto = findViewById(R.id.delete_photo);
        
        Intent intent = getIntent();

        try {
        	if (savedInstanceState == null) {
                mIsNewObservation = intent.getBooleanExtra(IS_NEW_OBSERVATION, false);
                mCurrentPhotoIndex = intent.getIntExtra(CURRENT_PHOTO_INDEX, 0);

                if (!mIsNewObservation) {
                    String observationString = intent.getStringExtra(OBSERVATION);
                    if (observationString != null) mObservation = new JSONObject(observationString);
                } else {
                    mObservationId = intent.getIntExtra(OBSERVATION_ID, 0);
                    mObservationIdInternal = intent.getIntExtra(OBSERVATION_ID_INTERNAL, 0);
                }

                mReadOnly = intent.getBooleanExtra(READ_ONLY, false);
                mIsTaxon = intent.getBooleanExtra(IS_TAXON, false);
        	} else {
                mIsNewObservation = savedInstanceState.getBoolean("mIsNewObservation");
        		if (!mIsNewObservation) {
                    mObservation = new JSONObject(savedInstanceState.getString("observation"));
                } else {
                    mObservationId = savedInstanceState.getInt("mObservationId");
                    mObservationIdInternal = savedInstanceState.getInt("mObservationIdInternal");
                }
                mReadOnly = savedInstanceState.getBoolean("mReadOnly");
                mIsTaxon = savedInstanceState.getBoolean("mIsTaxon");
        	}
        } catch (JSONException e) {
        	e.printStackTrace();
        }


        if (mIsTaxon) {
            actionBar.setTitle(TaxonUtils.getTaxonName(this, mObservation));
        } else {
            actionBar.setTitle(R.string.observation_photos);
        }

        mViewPager = (HackyViewPager) findViewById(R.id.id_pic_view_pager);
		if ((mObservation != null) && (!mIsNewObservation)) {
            mViewPager.setAdapter(new IdPicsPagerAdapter(this, mViewPager, mObservation, mIsTaxon));
		} else if (mIsNewObservation) {
            mViewPager.setAdapter(new IdPicsPagerAdapter(this, mViewPager, mObservationId, mObservationIdInternal));
            if (!mReadOnly) mDeletePhoto.setVisibility(View.VISIBLE);
            mDeletePhoto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent data = new Intent();
                    data.putExtra(DELETE_PHOTO_INDEX, mViewPager.getCurrentItem());
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
        }
        mViewPager.setCurrentItem(mCurrentPhotoIndex);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.set_as_first:
                Intent data = new Intent();
                data.putExtra(SET_DEFAULT_PHOTO_INDEX, mViewPager.getCurrentItem());
                setResult(RESULT_OK, data);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mIsNewObservation && !mReadOnly) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.observation_photos_viewer_menu, menu);
            return true;
        } else {
            return super.onCreateOptionsMenu(menu);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (!mIsNewObservation && mObservation != null) {
            outState.putString("observation", mObservation.toString());
        }

        outState.putBoolean("mIsNewObservation", mIsNewObservation);
        if (mIsNewObservation) {
            outState.putInt("mObservationId", mObservationId);
            outState.putInt("mObservationIdInternal", mObservationIdInternal);
        }

        mCurrentPhotoIndex = mViewPager.getCurrentItem();
        outState.putInt("mCurrentPhotoIndex", mCurrentPhotoIndex);

        outState.putBoolean("mReadOnly", mReadOnly);
        outState.putBoolean("mIsTaxon", mIsTaxon);

        super.onSaveInstanceState(outState);
    }
 
    
 	public static class IdPicsPagerAdapter extends PagerAdapter {
 		int mDefaultTaxonIcon;
 		List<String> mImages;
        Activity mActivity;
        ViewPager mViewPager;
        private OnClickListener mClickListener;

        public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, int observationId, int _observationId, OnClickListener listener) {
            this(activity, viewPager, observationId, _observationId);
            mClickListener = listener;
        }

        // Load offline photos for a new observation
        public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, int observationId, int _observationId) {
            mActivity = activity;
            mViewPager = viewPager;
            mImages = new ArrayList<String>();

            Cursor imageCursor = activity.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_observation_id=? or observation_id=?",
                    new String[]{String.valueOf(_observationId), String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            imageCursor.moveToFirst();

            if (imageCursor.getCount() == 0) return;

            do {
                String photoFileName = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
                if ((photoFileName != null) && (!(new File(photoFileName).exists()))) {
                    // Our local copy file was deleted (probably user deleted cache or similar) - try and use original filename from gallery
                    String originalPhotoFilename = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.ORIGINAL_PHOTO_FILENAME));
                    photoFileName = originalPhotoFilename;
                }

                String imageUrl = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                mImages.add(imageUrl != null ? imageUrl : photoFileName);
            } while (imageCursor.moveToNext());
        }
 		public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, JSONObject observation, boolean isTaxon, OnClickListener listener) {
            this(activity, viewPager, observation, isTaxon);
            mClickListener = listener;
        }

        // Load online photos for an existing observation
 		public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, JSONObject observation, boolean isTaxon) {
            mActivity = activity;
            mViewPager = viewPager;
 			mImages = new ArrayList<String>();
 			mDefaultTaxonIcon = TaxonUtils.observationIcon(observation);

 			JSONArray photos = observation.optJSONArray(isTaxon ? "taxon_photos" : "observation_photos");
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
 			View layout = (View) mActivity.getLayoutInflater().inflate(R.layout.observation_photo, null, false);
 			container.addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
 			ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

            String imagePath = mImages.get(position);

            if (FileUtils.isLocal(imagePath)) {
                // Offline photo
                Bitmap bitmapImage = null;
                try {
                    int newHeight = mViewPager.getMeasuredHeight();
                    int newWidth = mViewPager.getMeasuredHeight();

                    bitmapImage = ImageUtils.decodeSampledBitmapFromUri(mActivity.getContentResolver(),
                            Uri.fromFile(new File(imagePath)), newWidth, newHeight);

                    // Scale down the image if it's too big for the GL renderer
                    bitmapImage = ImageUtils.scaleDownBitmapIfNeeded(mActivity, bitmapImage);
                    bitmapImage = ImageUtils.rotateAccordingToOrientation(bitmapImage, imagePath);
                    imageView.setImageBitmap(bitmapImage);
                    final PhotoViewAttacher attacher = new PhotoViewAttacher(imageView);
                    attacher.update();

                    if (mClickListener != null) {
                        attacher.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
                            @Override
                            public void onPhotoTap(View view, float x, float y) {
                                mClickListener.onClick(view);
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                // Online photo

                String imageUrl = mImages.get(position);
                if (imageUrl == null) {
                    // Show a default taxon image
                    imageView.setImageResource(mDefaultTaxonIcon);
                } else {
                    loading.setVisibility(View.VISIBLE);
                    imageView.setVisibility(View.INVISIBLE);
                    final PhotoViewAttacher attacher = new PhotoViewAttacher(imageView);

                    if (mClickListener != null) {
                        attacher.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
                            @Override
                            public void onPhotoTap(View view, float x, float y) {
                                mClickListener.onClick(view);
                            }
                        });
                    }
                    // Show a photo

                    UrlImageViewHelper.setUrlDrawable(imageView, imageUrl, mDefaultTaxonIcon, new UrlImageViewCallback() {
                        @Override
                        public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            loading.setVisibility(View.GONE);
                            imageView.setVisibility(View.VISIBLE);
                            attacher.update();
                        }

                        @Override
                        public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            // Scale down the image if it's too big for the GL renderer
                            loadedBitmap = ImageUtils.scaleDownBitmapIfNeeded(mActivity, loadedBitmap);
                            return loadedBitmap;
                        }
                    });
                }
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
}
