package org.inaturalist.android;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.co.senab.photoview.HackyViewPager;
import uk.co.senab.photoview.PhotoViewAttacher;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.BaseTarget;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.LayoutParams;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
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
	@State(AndroidStateBundlers.JSONObjectBundler.class) public JSONObject mObservation;
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

    @State public boolean mIsNewObservation;
    @State public int mObservationId;
    @State public int mCurrentPhotoIndex;
    private View mDeletePhoto;
    @State public boolean mReadOnly;
    @State public int mObservationIdInternal;
    @State public boolean mIsTaxon;

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
        	}
        } catch (JSONException e) {
        	e.printStackTrace();
        }


        if (mIsTaxon) {
            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name first, before common name
                actionBar.setTitle(TaxonUtils.getTaxonScientificName(mObservation));
            } else {
                actionBar.setTitle(TaxonUtils.getTaxonName(this, mObservation));
            }

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
        mCurrentPhotoIndex = mViewPager.getCurrentItem();

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
 
    
 	public static class IdPicsPagerAdapter extends PagerAdapter {
        public static interface OnZoomListener {
            void onZoomedIn();
            void onZoomOriginal();
        }
 		int mDefaultTaxonIcon;
 		List<String> mImages;
        List<String> mImageThumbnails;
        Activity mActivity;
        ViewPager mViewPager;
        private OnClickListener mClickListener;
        private OnZoomListener mZoomListener = null;

        public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, int observationId, int _observationId, OnClickListener listener) {
            this(activity, viewPager, observationId, _observationId);
            mClickListener = listener;
        }

        public void setOnZoomListener(OnZoomListener listener) {
            mZoomListener = listener;
        }

        // Load offline photos for a new observation
        public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, int observationId, int _observationId) {
            mActivity = activity;
            mViewPager = viewPager;
            mImages = new ArrayList<String>();
            mImageThumbnails = new ArrayList<String>();

            Cursor imageCursor = activity.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_observation_id=? or observation_id=?",
                    new String[]{String.valueOf(_observationId), String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            imageCursor.moveToFirst();

            if (imageCursor.getCount() == 0) return;

            do {
                String photoFileName = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
                String originalPhotoFilename = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.ORIGINAL_PHOTO_FILENAME));

                if ((photoFileName != null) && (!(new File(photoFileName).exists()))) {
                    // Our local copy file was deleted (probably user deleted cache or similar) - try and use original filename from gallery
                    photoFileName = originalPhotoFilename;
                }

                if ((originalPhotoFilename != null) && (new File(originalPhotoFilename).exists())) {
                    // Prefer to show original photo file, if still exists, since it has the original resolution (not
                    // resized down to 2048x2048)
                    photoFileName = originalPhotoFilename;
                }

                String imageUrl = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                mImages.add(imageUrl != null ? imageUrl : photoFileName);

                if (imageUrl != null) {
                    // Deduct the URL of the thumbnail
                    String thumbnailUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/')) + "/small" + imageUrl.substring(imageUrl.lastIndexOf('.'));
                    mImageThumbnails.add(thumbnailUrl);
                } else {
                    mImageThumbnails.add(null);
                }
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
            mImageThumbnails = new ArrayList<String>();
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
 							if ((photoUrl != null) && (photoUrl.length() > 0)) {
 								mImages.add(photoUrl);
                                mImageThumbnails.add(innerPhoto.has("thumb_url") ? innerPhoto.optString("thumb_url") : innerPhoto.optString("small_url"));
 							} else {
 							    String url = innerPhoto.optString("url");
 							    if (url != null) {
                                    String extension = url.substring(url.lastIndexOf(".") + 1);
                                    mImages.add(url.substring(0, url.lastIndexOf('/') + 1) + "original." + extension);
                                    mImageThumbnails.add(url.substring(0, url.lastIndexOf('/') + 1) + "square." + extension);
                                }
                            }
 						}
 					}
 				}
 			} else {
 				// Show taxon icon
 				mImages.add(null);
                mImageThumbnails.add(null);
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
 			final ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

            String imagePath = mImages.get(position);
            PhotoViewAttacher attacher = null;

            if (FileUtils.isLocal(imagePath)) {
                // Offline photo
                try {
                    attacher = new PhotoViewAttacher(imageView);
                    final PhotoViewAttacher finalAttacher2 = attacher;
                    GlideApp.with(mActivity)
                            .load(new File(imagePath))
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    imageView.setImageDrawable(resource);
                                    finalAttacher2.update();
                                    return true;
                                }
                            })
                            .into(imageView);

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
                    attacher = new PhotoViewAttacher(imageView);

                    // Deduct the original-sized URL
                    imageUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/')) + "/original" + imageUrl.substring(imageUrl.lastIndexOf('.'));

                    // Show a photo
                    
                    String thumbnailUrl = mImageThumbnails.get(position);

                    final PhotoViewAttacher finalAttacher = attacher;

                    RequestBuilder<Drawable> imageRequest = Glide.with(mActivity)
                            .load(imageUrl);

                    if (thumbnailUrl != null) {
                        // Load a scaled down version (thumbnail) of the image first
                        RequestBuilder<Drawable> thumbnailRequest = Glide.
                                with(mActivity).
                                load(thumbnailUrl).
                                apply(new RequestOptions().placeholder(mDefaultTaxonIcon));
                        imageRequest = imageRequest.thumbnail(thumbnailRequest);
                    } else {
                        imageRequest = imageRequest.
                                apply(new RequestOptions().placeholder(mDefaultTaxonIcon));
;
                    }

                    BaseTarget target = new BaseTarget<Drawable>() {
                        @Override
                        public void onResourceReady(Drawable bitmap, Transition<? super Drawable> transition) {
                            imageView.setImageDrawable(bitmap);
                            loading.setVisibility(View.GONE);
                            imageView.setVisibility(View.VISIBLE);
                            finalAttacher.update();
                        }

                        @Override
                        public void getSize(SizeReadyCallback cb) {
                            cb.onSizeReady(SIZE_ORIGINAL, SIZE_ORIGINAL);
                        }

                        @Override
                        public void removeCallback(SizeReadyCallback cb) {}
                    };


                    imageRequest.into(target);
                }
            }


            if ((mClickListener != null) && (attacher != null)) {
                attacher.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
                    @Override
                    public void onPhotoTap(View view, float x, float y) {
                        mClickListener.onClick(view);
                    }
                });
            }
            if (attacher != null) {
                attacher.setMaximumScale(7.0f);
                final PhotoViewAttacher finalAttacher1 = attacher;
                attacher.setOnMatrixChangeListener(new PhotoViewAttacher.OnMatrixChangedListener() {
                    @Override
                    public void onMatrixChanged(RectF rect) {
                        float scale = finalAttacher1.getScale();
                        if (mZoomListener != null) {
                            if (scale > 1.0f) {
                                mZoomListener.onZoomedIn();
                            } else {
                                mZoomListener.onZoomOriginal();
                            }
                        }
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
}
