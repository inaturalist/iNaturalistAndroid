package org.inaturalist.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import uk.co.senab.photoview.HackyViewPager;
import uk.co.senab.photoview.PhotoViewAttacher;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.BaseTarget;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.evernote.android.state.State;

import com.livefront.bridge.Bridge;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropFragment;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.LayoutParams;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

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
    public static final String OBSERVATION_UUID = "observation_uuid";
    public static final String CURRENT_PHOTO_INDEX = "current_photo_index";
    public static final String READ_ONLY = "read_only";
    public static final String IS_TAXON = "is_taxon";

    public static final String REPLACED_PHOTOS = "replaced_photos";
    public static final String SET_DEFAULT_PHOTO_INDEX = "set_default_photo_index";
    public static final String DELETE_PHOTO_INDEX = "delete_photo_index";
    public static final String DUPLICATE_PHOTO_INDEX = "duplicate_photo_index";

    @State public boolean mIsNewObservation;
    @State public int mObservationId;
    @State public int mCurrentPhotoIndex;
    private View mDeletePhoto;
    private View mDuplicatePhoto;
    private View mEditPhoto;
    private View mActionContainer;
    @State public boolean mReadOnly;
    @State public int mObservationIdInternal;
    @State public String mObservationUUID;
    @State public boolean mIsTaxon;
    @State(AndroidStateBundlers.ListPairBundler.class) public List<Pair<Uri, Long>> mReplacedPhotos = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.observation_photos);

        mDeletePhoto = findViewById(R.id.delete_photo);
        mDuplicatePhoto = findViewById(R.id.duplicate_photo);
        mEditPhoto = findViewById(R.id.edit_photo);
        mActionContainer = findViewById(R.id.action_container);

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
                    mObservationUUID = intent.getStringExtra(OBSERVATION_UUID);
                }

                mReadOnly = intent.getBooleanExtra(READ_ONLY, false);
                mIsTaxon = intent.getBooleanExtra(IS_TAXON, false);
        	}
        } catch (JSONException e) {
        	Logger.tag(TAG).error(e);
        }


        if (mIsTaxon) {
            if (mApp.getShowScientificNameFirst()) {
                // Show scientific name first, before common name
                actionBar.setTitle(TaxonUtils.getTaxonScientificName(mApp, mObservation));
            } else {
                actionBar.setTitle(TaxonUtils.getTaxonName(this, mObservation));
            }

        } else {
            actionBar.setTitle(R.string.observation_photos);
        }

        mViewPager = (HackyViewPager) findViewById(R.id.id_pic_view_pager);
		if ((mObservation != null) && (!mIsNewObservation)) {
            mViewPager.setAdapter(new IdPicsPagerAdapter(this, mViewPager, mObservation, mIsTaxon));
            mEditPhoto.setVisibility(View.GONE);
            mDuplicatePhoto.setVisibility(View.GONE);
            mDeletePhoto.setVisibility(View.GONE);
		} else if (mIsNewObservation) {
		    IdPicsPagerAdapter adapter = new IdPicsPagerAdapter(this, mViewPager, mObservationId, mObservationIdInternal, mObservationUUID);
            mViewPager.setAdapter(adapter);
            if (mReplacedPhotos.size() > 0) {
                // Update with any modified/cropped photos
                for (Pair<Uri, Long> replacedPhoto : mReplacedPhotos) {
                    adapter.setImageUri(replacedPhoto.second.intValue(), replacedPhoto.first);
                }
            }

            if (!mReadOnly) mActionContainer.setVisibility(View.VISIBLE);
            mDeletePhoto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent data = new Intent();
                    data.putExtra(DELETE_PHOTO_INDEX, mViewPager.getCurrentItem());
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
            mDuplicatePhoto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent data = new Intent();
                    data.putExtra(DUPLICATE_PHOTO_INDEX, mViewPager.getCurrentItem());
                    if (mReplacedPhotos.size() > 0) {
                        data.putExtra(REPLACED_PHOTOS, replacedPhotosToString());
                    }
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
            mEditPhoto.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    editPhoto(mViewPager.getCurrentItem());
                }
            });
        }
        mViewPager.setCurrentItem(mCurrentPhotoIndex);

    }

    private void editPhoto(int photoIndex) {
        IdPicsPagerAdapter adapter = (IdPicsPagerAdapter) mViewPager.getAdapter();
        String sourceImage = adapter.getImageUri(photoIndex);

        if (sourceImage == null) {
            Toast.makeText(getApplicationContext(), getString(R.string.couldnt_edit_photo), Toast.LENGTH_SHORT).show();
            return;
        }

        Uri sourceUri = sourceImage.startsWith("http") ? Uri.parse(sourceImage) : Uri.fromFile(new File(sourceImage));

        mCurrentPhotoIndex = photoIndex;

        Intent intent = new Intent(ObservationPhotosViewer.this, ObservationPhotoEditor.class);
        intent.putExtra(ObservationPhotoEditor.PHOTO_URI, sourceUri.toString());
        startActivityForResult(intent, UCrop.REQUEST_CROP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == Activity.RESULT_OK) {

                // Replace current photo with edited photo
                Uri uri = data.getParcelableExtra(UCrop.EXTRA_OUTPUT_URI);
                IdPicsPagerAdapter adapter = (IdPicsPagerAdapter) mViewPager.getAdapter();

                adapter.setImageUri(mCurrentPhotoIndex, uri);

                for (int i = 0; i < mReplacedPhotos.size(); i++) {
                    Pair<Uri, Long> pair = mReplacedPhotos.get(i);
                    if ((pair.second == mCurrentPhotoIndex) && (mCurrentPhotoIndex < mReplacedPhotos.size())) {
                        // User already edited this photo before - just replace the edit
                        mReplacedPhotos.set(mCurrentPhotoIndex, new Pair<Uri, Long>(uri, Long.valueOf(mCurrentPhotoIndex)));
                        return;
                    }
                }

                // New edit of this photo - save it
                mReplacedPhotos.add(new Pair<>(uri, Long.valueOf(mCurrentPhotoIndex)));
            }
        }
    }

    @Override
    public void onBackPressed() {
        checkForReplacedPhotos();

        super.onBackPressed();
    }

    private void checkForReplacedPhotos() {
        if (mReplacedPhotos.size() > 0) {
            Intent data = new Intent();
            data.putExtra(REPLACED_PHOTOS, replacedPhotosToString());
            setResult(RESULT_OK, data);
        } else {
            setResult(RESULT_CANCELED);
        }
    }

    private String replacedPhotosToString() {
        // On some devices (like older Android 6 Samsung), string representation of a list of pairs
        // is not the same as other devices - convert it to a standard representation)
        StringBuilder builder = new StringBuilder();

        builder.append('[');

        for (int i = 0; i < mReplacedPhotos.size(); i++) {
            Pair<Uri, Long> pair = mReplacedPhotos.get(i);

            builder.append("Pair{");
            builder.append(pair.first.toString());
            builder.append(' ');
            builder.append(pair.second.toString());

            if (i < mReplacedPhotos.size() - 1) {
                builder.append("}, ");
            } else {
                builder.append('}');
            }
        }

        builder.append(']');

        return builder.toString();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                checkForReplacedPhotos();
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
        List<ImageView> mImageViews;
        List<PhotoViewAttacher> mImageViewAttachers;
        List<Long> mPhotoIds;
        List<String> mImageThumbnails;
        Activity mActivity;
        ViewPager mViewPager;
        private OnClickListener mClickListener;
        private OnZoomListener mZoomListener = null;

        private Integer mObservationId = null;
        private Integer mInternalObservationId = null;
        private String mObservationUUID = null;

        public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, int observationId, int _observationId, String uuid, OnClickListener listener) {
            this(activity, viewPager, observationId, _observationId, uuid);
            mClickListener = listener;
        }

        public void setOnZoomListener(OnZoomListener listener) {
            mZoomListener = listener;
        }

        // Load offline photos for a new observation
        public IdPicsPagerAdapter(Activity activity, ViewPager viewPager, int observationId, int _observationId, String uuid) {
            mActivity = activity;
            mViewPager = viewPager;
            mImages = new ArrayList<String>();
            mImageViews = new ArrayList<>();
            mImageViewAttachers = new ArrayList<>();
            mPhotoIds = new ArrayList<>();
            mImageThumbnails = new ArrayList<String>();
            mObservationId = observationId;
            mInternalObservationId = _observationId;
            mObservationUUID = uuid;

            Cursor imageCursor = activity.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    ObservationPhoto.PROJECTION,
                    "(observation_uuid=?) AND ((is_deleted = 0) OR (is_deleted IS NULL))",
                    new String[]{mObservationUUID},
                    ObservationPhoto.DEFAULT_SORT_ORDER);

            imageCursor.moveToFirst();

            if (imageCursor.getCount() == 0) return;

            for (int i = 0; i < imageCursor.getCount(); i++) {
                mImageViews.add(null);
                mImageViewAttachers.add(null);
            }

            do {
                String photoFileName = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));

                if ((photoFileName != null) && (!(new File(photoFileName).exists()))) {
                    // Our local copy file was deleted
                    photoFileName = null;
                }

                String imageUrl = imageCursor.getString(imageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                mImages.add(imageUrl != null ? imageUrl : photoFileName);
                mPhotoIds.add(imageCursor.getLong(imageCursor.getColumnIndexOrThrow(ObservationPhoto._ID)));

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
            mImageViews = new ArrayList<>();
            mImageViewAttachers = new ArrayList<>();
            mPhotoIds = new ArrayList<>();
            mImageThumbnails = new ArrayList<String>();
 			mDefaultTaxonIcon = TaxonUtils.observationIcon(observation);



 			JSONArray photos = observation.optJSONArray(isTaxon ? "taxon_photos" : "observation_photos");
 			if ((photos != null) && (photos.length() > 0)) {
                for (int i = 0; i < photos.length(); i++) {
                    mImageViews.add(null);
                    mImageViewAttachers.add(null);
                    mPhotoIds.add(null);
                }

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
 							    if ((url != null) && (url.length() > 0)) {
                                    String extension = url.substring(url.lastIndexOf("."));

                                    // Deduce the original-sized URL
                                    if (url.substring(0, url.lastIndexOf('/')).endsWith("assets")) {
                                        // It's an assets default URL - e.g. https://www.inaturalist.org/assets/copyright-infringement-square.png
                                        mImages.add(url.substring(0, url.lastIndexOf('-') + 1) + "original" + extension);
                                        mImageThumbnails.add(url.substring(0, url.lastIndexOf('-') + 1) + "square" + extension);
                                    } else {
                                        // "Regular" observation photo
                                        mImages.add(url.substring(0, url.lastIndexOf('/') + 1) + "original" + extension);
                                        mImageThumbnails.add(url.substring(0, url.lastIndexOf('/') + 1) + "square" + extension);
                                    }
                                }
                            }
 						}
 					}
 				}
 			} else {
 				// Show taxon icon
 				mImages.add(null);
                mImageThumbnails.add(null);

                mImageViews.add(null);
                mImageViewAttachers.add(null);
            }
        }

 		@Override
 		public int getCount() {
 			return mImages.size();
 		}

 		public String getImageUri(int index) {
            if ((index < 0) || (index >= mImages.size())) return null;

            return mImages.get(index);
        }

        public Long getImageId(int index) {
            if ((index < 0) || (index >= mImages.size())) return null;

            return mPhotoIds.get(index);
        }

        // Sets a new image URI (assumes it's an offline image) - basically replaces existing photo
        public void setImageUri(int index, Uri uri) {
            if ((index < 0) || (index >= mImages.size())) return;
            if ((mInternalObservationId == null) || (mObservationId == null)) return; // Can't edit a read-only observation's photos

            // Refresh UI
            mImages.set(index, uri.getPath());
            ImageView imageView = mImageViews.get(index);
            if (imageView != null) {
                imageView.setImageBitmap(BitmapFactory.decodeFile(uri.getPath()));
                mImageViewAttachers.get(index).update();
            }
        }

 		@Override
 		public View instantiateItem(ViewGroup container, int position) {
 			View layout = (View) mActivity.getLayoutInflater().inflate(R.layout.observation_photo, null, false);
 			container.addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
 			final ImageView imageView = (ImageView) layout.findViewById(R.id.id_pic);
 			mImageViews.set(position, imageView);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.id_pic_loading);

            String imagePath = mImages.get(position);
            PhotoViewAttacher attacher = null;

            if (FileUtils.isLocal(imagePath)) {
                // Offline photo
                try {
                    attacher = new PhotoViewAttacher(imageView);
                    mImageViewAttachers.set(position, attacher);
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
                    Logger.tag(TAG).error(e);
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
                    mImageViewAttachers.set(position, attacher);

                    // Deduce the original-sized URL
                    String extension = imageUrl.substring(imageUrl.lastIndexOf('.'));

                    // Deduce the original-sized URL
                    if (imageUrl.substring(0, imageUrl.lastIndexOf('/')).endsWith("assets")) {
                        // It's an assets default URL - e.g. https://www.inaturalist.org/assets/copyright-infringement-square.png
                        imageUrl = imageUrl.substring(0, imageUrl.lastIndexOf('-') + 1) + "original" + extension;
                    } else {
                        // "Regular" observation photo
                        imageUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/') + 1) + "original" + extension;
                    }

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
