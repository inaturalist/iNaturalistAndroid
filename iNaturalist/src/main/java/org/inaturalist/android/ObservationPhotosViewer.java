package org.inaturalist.android;

import java.io.IOException;
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
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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

	public static final String IS_NEW_OBSERVATION = "is_new_observation";
    public static final String OBSERVATION = "observation";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
    public static final String CURRENT_PHOTO_INDEX = "current_photo_index";
    public static final String READ_ONLY = "read_only";

    public static final String SET_DEFAULT_PHOTO_INDEX = "set_default_photo_index";
    public static final String DELETE_PHOTO_INDEX = "delete_photo_index";

    private boolean mIsNewObservation;
    private int mObservationId;
    private int mCurrentPhotoIndex;
    private View mDeletePhoto;
    private boolean mReadOnly;
    private int mObservationIdInternal;

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
        	} else {
                mIsNewObservation = savedInstanceState.getBoolean("mIsNewObservation");
        		if (!mIsNewObservation) {
                    mObservation = new JSONObject(savedInstanceState.getString("observation"));
                } else {
                    mObservationId = savedInstanceState.getInt("mObservationId");
                    mObservationIdInternal = savedInstanceState.getInt("mObservationIdInternal");
                }
                mReadOnly = savedInstanceState.getBoolean("mReadOnly");
        	}
        } catch (JSONException e) {
        	e.printStackTrace();
        }

        mViewPager = (HackyViewPager) findViewById(R.id.id_pic_view_pager);
		if ((mObservation != null) && (!mIsNewObservation)) {
            mViewPager.setAdapter(new IdPicsPagerAdapter(mObservation));
		} else if (mIsNewObservation) {
            mViewPager.setAdapter(new IdPicsPagerAdapter(mObservationId, mObservationIdInternal));
            mViewPager.setCurrentItem(mCurrentPhotoIndex);

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
            MenuInflater inflater = getSupportMenuInflater();
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
            mCurrentPhotoIndex = mViewPager.getCurrentItem();
            outState.putInt("mCurrentPhotoIndex", mCurrentPhotoIndex);
        }

        outState.putBoolean("mReadOnly", mReadOnly);

        super.onSaveInstanceState(outState);
    }
 
    
 	class IdPicsPagerAdapter extends PagerAdapter {
 		int mDefaultTaxonIcon;
 		List<String> mImages;
        List<Integer> mImageIds;
        boolean mIsOffline = false;

        private Cursor findPhotoInStorage(Integer photoId) {
            Cursor imageCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.Images.ImageColumns.ORIENTATION},
                    MediaStore.MediaColumns._ID + " = " + photoId, null, null);

            imageCursor.moveToFirst();
            return imageCursor;
        }

        // Load offline photos for a new observation
        public IdPicsPagerAdapter(int observationId, int _observationId) {
            mIsOffline = true;
            mImageIds = new ArrayList<Integer>();
            mImages = new ArrayList<String>();

            Cursor imageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "_observation_id=? or observation_id=?",
                    new String[]{String.valueOf(_observationId), String.valueOf(observationId)},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            imageCursor.moveToFirst();

            do {
                int photoId = imageCursor.getInt(imageCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
                Cursor pc = findPhotoInStorage(photoId);
                if (pc.getCount() > 0) {
                    mImageIds.add(photoId);
                    pc.close();
                } else {
                    String imageUrl = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                    if (imageUrl != null) {
                        // Online photo
                        mImages.add(imageUrl);
                        mIsOffline = false;
                    }
                }
            } while (imageCursor.moveToNext());
        }

        // Load online photos for an existing observation
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
 			return (mIsOffline ? mImageIds.size() : mImages.size());
 		}

 		@Override
 		public View instantiateItem(ViewGroup container, int position) {
 			View layout = (View) getLayoutInflater().inflate(R.layout.observation_photo, null, false);
 			container.addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
 			ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

            if (mIsOffline) {
                // Offline photos
                int photoId = mImageIds.get(position);
                Cursor pc = findPhotoInStorage(photoId);
                if (pc.getCount() > 0) {
                    int orientation = pc.getInt(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION));
                    Bitmap bitmapImage = null;
                    try {
                        bitmapImage = MediaStore.Images.Media.getBitmap(
                                getContentResolver(),
                                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId));
                        if (orientation != 0) {
                            Matrix matrix = new Matrix();
                            matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
                            bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
                        }
                        // Scale down the image if it's too big for the GL renderer
                        bitmapImage = ImageUtils.scaleDownBitmapIfNeeded(ObservationPhotosViewer.this, bitmapImage);
                        imageView.setImageBitmap(bitmapImage);
                        final PhotoViewAttacher attacher = new PhotoViewAttacher(imageView);
                        attacher.update();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // Online photos

                String imageUrl = mImages.get(position);
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

                        @Override
                        public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            // Scale down the image if it's too big for the GL renderer
                            loadedBitmap = ImageUtils.scaleDownBitmapIfNeeded(ObservationPhotosViewer.this, loadedBitmap);
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

 	public static int observationIcon(JSONObject o) {
 		if (o == null) return R.drawable.unknown_large;
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
