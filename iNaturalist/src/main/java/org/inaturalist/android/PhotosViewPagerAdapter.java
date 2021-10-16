package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.ablanco.zoomy.TapListener;
import com.ablanco.zoomy.ZoomListener;
import com.ablanco.zoomy.Zoomy;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class PhotosViewPagerAdapter extends PagerAdapter {
    private static final String TAG = "PhotosViewPagerAdapter";
    private final Observation mObservation;
    private final Context mContext;
    private final String mObservationJson;
    private final INaturalistApp mApp;
    private Cursor mImageCursor = null;
    private boolean mIsExternal = false;

    private HashMap<Integer, Bitmap> mBitmaps = new HashMap<>();

    public PhotosViewPagerAdapter(Context context, Observation observation, String obsJson) {
        mObservation = observation;
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mObservationJson = obsJson;
        
        if (mObservation != null) {
            if (mObservation.uuid != null) {
                mImageCursor = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                        ObservationPhoto.PROJECTION,
                        "(observation_uuid=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                        new String[]{mObservation.uuid},
                        mApp.isLayoutRTL() ? ObservationPhoto.REVERSE_DEFAULT_SORT_ORDER : ObservationPhoto.DEFAULT_SORT_ORDER);
            } else if (mApp.loggedIn()) {
                if (mObservation.user_login.toLowerCase().equals(mApp.currentUserLogin().toLowerCase())) {
                    mImageCursor = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                            ObservationPhoto.PROJECTION,
                            "(observation_id=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                            new String[]{String.valueOf(mObservation.id)},
                            mApp.isLayoutRTL() ? ObservationPhoto.REVERSE_DEFAULT_SORT_ORDER : ObservationPhoto.DEFAULT_SORT_ORDER);
                }
            }

            if (mImageCursor != null) {
                mIsExternal = mImageCursor.getCount() == 0;
                mImageCursor.moveToFirst();
            }
        }
    }

    public Cursor getCursor() {
        return mImageCursor;
    }

    @Override
    public int getCount() {
        return mIsExternal ?
                mObservation.photos.size()
                : (mImageCursor != null ? mImageCursor.getCount() : 0);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, final int position) {
        ImageView imageView = new ImageView(mContext);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        String photoFilename = null;
        String imageUrl = null;

        if (mIsExternal) {
            photoFilename = null;
            imageUrl = mObservation.photos.get(position).photo_url;
        } else {
            mImageCursor.moveToPosition(position);

            imageUrl = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
            photoFilename = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
        }

        if (imageUrl != null) {
            // Online photo
            imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            String extension = imageUrl.substring(imageUrl.lastIndexOf('.'));

            String thumbnailUrl, largeSizeUrl;

            // Deduce the original-sized URL
            if (imageUrl.substring(0, imageUrl.lastIndexOf('/')).endsWith("assets")) {
                // It's an assets default URL - e.g. https://www.inaturalist.org/assets/copyright-infringement-square.png
                largeSizeUrl = imageUrl.substring(0, imageUrl.lastIndexOf('-') + 1) + "original" + extension;
                thumbnailUrl = imageUrl.substring(0, imageUrl.lastIndexOf('-') + 1) + "small" + extension;
            } else {
                // "Regular" observation photo
                largeSizeUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/') + 1) + "original" + extension;
                thumbnailUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/') + 1) + "small" + extension;
            }

            RequestBuilder thumbnailRequest = Glide.with(mContext).load(thumbnailUrl);


            Glide.with(mContext)
                    .load(largeSizeUrl)
                    .thumbnail(thumbnailRequest)
                    .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.AUTOMATIC))
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            // Save downloaded bitmap into local file
                            imageView.setImageDrawable(resource);

                            if (resource instanceof BitmapDrawable) {
                                mBitmaps.put(position, ((BitmapDrawable)resource).getBitmap());
                            } else if (resource instanceof GifDrawable) {
                                mBitmaps.put(position, ((GifDrawable) resource).getFirstFrame());
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            // Failed to load observation photo
                            try {
                                JSONObject eventParams = new JSONObject();
                                eventParams.put(AnalyticsClient.EVENT_PARAM_SIZE, AnalyticsClient.EVENT_PARAM_VALUE_MEDIUM);

                                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_PHOTO_FAILED_TO_LOAD, eventParams);
                            } catch (JSONException e) {
                                Logger.tag(TAG).error(e);
                            }
                        }
                    });
        } else {
            // Offline photo
            Bitmap bitmapImage = null;

            try {
                BitmapFactory.Options options = new BitmapFactory.Options();

                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false;
                // This decreases in-memory byte-storage per pixel
                options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                bitmapImage = BitmapFactory.decodeFile(photoFilename, options);
                bitmapImage = ImageUtils.rotateAccordingToOrientation(bitmapImage, photoFilename);
                imageView.setImageBitmap(bitmapImage);
                mBitmaps.put(position, bitmapImage);
            } catch (Exception e) {
                Logger.tag(TAG).error(e);
            }
        }

        ((ViewPager)container).addView(imageView, 0);

        new Zoomy.Builder((Activity)mContext)
                .target(imageView)
                .zoomListener(new ZoomListener() {
                    @Override
                    public void onViewBeforeStartedZooming(View view) {
                        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        imageView.setImageBitmap(mBitmaps.get(position));
                    }
                    @Override
                    public void onViewStartedZooming(View view) {
                    }
                    @Override
                    public void onViewEndedZooming(View view) {
                        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    }
                })
                .tapListener((TapListener) v -> {
                    Intent intent = new Intent(mContext, ObservationPhotosViewer.class);
                    intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, position);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObservation.id);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID_INTERNAL, mObservation._id);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_UUID, mObservation.uuid);
                    intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, !mIsExternal);
                    intent.putExtra(ObservationPhotosViewer.READ_ONLY, true);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION, mObservationJson);
                    ((Activity)mContext).startActivity(intent);
                })
                .register();

        return imageView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        ((ViewPager) container).removeView((View) object);
    }

}

