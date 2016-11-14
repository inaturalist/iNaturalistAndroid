package org.inaturalist.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.apache.commons.lang3.StringUtils;

import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

class ObservationCursorAdapter extends SimpleCursorAdapter implements AbsListView.OnScrollListener {
    private int mDimension;
    private HashMap<Long, String[]> mPhotoInfo = new HashMap<Long, String[]>();
    private boolean mIsGrid;

    private final Activity mContext;
    private INaturalistApp mApp;
    private PullToRefreshGridViewExtended mGrid;
    private boolean mIsScrolling = false;

    public ObservationCursorAdapter(Context context, Cursor c) {
        this(context, c, false, null);
    }

    public ObservationCursorAdapter(Context context, Cursor c, boolean isGrid, PullToRefreshGridViewExtended grid) {
        super(context, isGrid ? R.layout.observation_grid_item : R.layout.list_item, c, new String[] {}, new int[] {});
        mIsGrid = isGrid;
        mGrid = grid;
        mContext = (Activity)context;
        mApp = (INaturalistApp) mContext.getApplicationContext();

        mObservationPhotoNames = new HashMap<>();
        mImageViews = new HashMap<>();
        mObservationLoaded = new HashMap<>();

        getPhotoInfo();
    }

    public void refreshCursor() {
        refreshCursor(null);
    }

    public void refreshCursor(String speciesGuess) {
        SharedPreferences prefs = mContext.getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        String login = prefs.getString("username", null);
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

        String[] selectionArgs = null;

        if (speciesGuess != null) {
            conditions += " AND (" +
                    "(species_guess LIKE ?) OR " +
                    "((species_guess IS NULL) AND (preferred_common_name like ?)))";
            selectionArgs = new String[] { "%" + speciesGuess + "%", "%" + speciesGuess + "%" };
        }


        Cursor newCursor = mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                conditions, selectionArgs, Observation.DEFAULT_SORT_ORDER);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB){
            Cursor oldCursor = swapCursor(newCursor);
            if ((oldCursor != null) && (!oldCursor.isClosed())) oldCursor.close();
        } else {
            changeCursor(newCursor);
        }
    }

    /**
     * Retrieves photo ids and orientations for photos associated with the listed observations.
     */
    public void getPhotoInfo() {
        Cursor c = getCursor();
        int originalPosition = c.getPosition();
        if (c.getCount() == 0) return;

        c.moveToFirst();
        ArrayList<Long> obsIds = new ArrayList<Long>();
        ArrayList<Long> obsExternalIds = new ArrayList<Long>();
        ArrayList<Long> photoIds = new ArrayList<Long>();
        while (!c.isAfterLast()) {
            obsIds.add(c.getLong(c.getColumnIndexOrThrow(Observation._ID)));
            try {
                obsExternalIds.add(c.getLong(c.getColumnIndexOrThrow(Observation.ID)));
            } catch (Exception exc) { }
            c.moveToNext();
        }

        c.moveToPosition(originalPosition);

        // Add any online-only photos
        Cursor onlinePc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                new String[]{ObservationPhoto._ID, ObservationPhoto._OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL, ObservationPhoto.PHOTO_FILENAME},
                "(_observation_id IN (" + StringUtils.join(obsIds, ',') + ") OR observation_id IN (" + StringUtils.join(obsExternalIds, ',') + ")  )",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);
        onlinePc.moveToFirst();
        while (!onlinePc.isAfterLast()) {
            Long obsId = onlinePc.getLong(onlinePc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
            String photoUrl = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
            String photoFilename = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));

            if (!mPhotoInfo.containsKey(obsId)) {
                mPhotoInfo.put(
                        obsId,
                        new String[] {
                                photoFilename,
                                null,
                                photoUrl,
                                null,
                                null
                        });
            }
            onlinePc.moveToNext();
        }

        onlinePc.close();
    }

    public void refreshPhotoInfo() {
        mPhotoInfo = new HashMap<Long, String[]>();
        getPhotoInfo();
    }

    public void refreshPhotoInfo(long obsId) {
        if (mPhotoInfo.containsKey(obsId)) mPhotoInfo.remove(obsId);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        Cursor c = this.getCursor();
        if (c.getCount() == 0) {
            return view;
        }
        c.moveToPosition(position);

        final ImageView obsImage = (ImageView) view.findViewById(R.id.observation_pic);
        final ImageView obsIconicImage = (ImageView) view.findViewById(R.id.observation_iconic_pic);
        final TextView speciesGuess = (TextView) view.findViewById(R.id.species_guess);
        TextView dateObserved = (TextView) view.findViewById(R.id.date);
        ViewGroup commentIdContainer = (ViewGroup) view.findViewById(R.id.comment_id_container);

        ImageView commentIcon = (ImageView) view.findViewById(R.id.comment_pic);
        ImageView idIcon = (ImageView) view.findViewById(R.id.id_pic);
        TextView commentCount = (TextView) view.findViewById(R.id.comment_count);
        TextView idCount = (TextView) view.findViewById(R.id.id_count);

        TextView placeGuess = (TextView) view.findViewById(R.id.place_guess);
        ImageView locationIcon = (ImageView) view.findViewById(R.id.location_icon);

        View progress = view.findViewById(R.id.progress);


        final Long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
        final Long externalObsId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
        String placeGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.PLACE_GUESS));
        Double latitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LATITUDE));
        Double longitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LONGITUDE));

        if (mIsGrid) {
            mDimension = mGrid.getColumnWidth();
            obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
            progress.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));

            int newDimension = (int) (mDimension * 0.48); // So final image size will be 48% of original size
            int speciesGuessHeight = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, mContext.getResources().getDisplayMetrics());
            int leftRightMargin = (mDimension - newDimension) / 2;
            int topBottomMargin = (mDimension - speciesGuessHeight - newDimension) / 2;
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newDimension, newDimension);
            layoutParams.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, 0);
            obsIconicImage.setLayoutParams(layoutParams);
        }

        refreshPhotoInfo(obsId);
        getPhotoInfo();

        String[] photoInfo = mPhotoInfo.get(obsId);

        if (photoInfo == null) {
            // Try getting the external observation photo info
            photoInfo = mPhotoInfo.get(externalObsId);
        }

        String iconicTaxonName = c.getString(c.getColumnIndexOrThrow(Observation.ICONIC_TAXON_NAME));
        int iconResource = 0;
        if (iconicTaxonName == null) {
            iconResource = R.drawable.iconic_taxon_unknown;
        } else if (iconicTaxonName.equals("Animalia")) {
            iconResource = R.drawable.iconic_taxon_animalia;
        } else if (iconicTaxonName.equals("Plantae")) {
            iconResource = R.drawable.iconic_taxon_plantae;
        } else if (iconicTaxonName.equals("Chromista")) {
            iconResource = R.drawable.iconic_taxon_chromista;
        } else if (iconicTaxonName.equals("Fungi")) {
            iconResource = R.drawable.iconic_taxon_fungi;
        } else if (iconicTaxonName.equals("Protozoa")) {
            iconResource = R.drawable.iconic_taxon_protozoa;
        } else if (iconicTaxonName.equals("Actinopterygii")) {
            iconResource = R.drawable.iconic_taxon_actinopterygii;
        } else if (iconicTaxonName.equals("Amphibia")) {
            iconResource = R.drawable.iconic_taxon_amphibia;
        } else if (iconicTaxonName.equals("Reptilia")) {
            iconResource = R.drawable.iconic_taxon_reptilia;
        } else if (iconicTaxonName.equals("Aves")) {
            iconResource = R.drawable.iconic_taxon_aves;
        } else if (iconicTaxonName.equals("Mammalia")) {
            iconResource = R.drawable.iconic_taxon_mammalia;
        } else if (iconicTaxonName.equals("Mollusca")) {
            iconResource = R.drawable.iconic_taxon_mollusca;
        } else if (iconicTaxonName.equals("Insecta")) {
            iconResource = R.drawable.iconic_taxon_insecta;
        } else if (iconicTaxonName.equals("Arachnida")) {
            iconResource = R.drawable.iconic_taxon_arachnida;
        } else {
            iconResource = R.drawable.iconic_taxon_unknown;
        }

        obsIconicImage.setVisibility(View.VISIBLE);
        obsIconicImage.setImageResource(iconResource);
        obsImage.setVisibility(View.INVISIBLE);

        if (photoInfo != null) {
            String photoFilename = photoInfo[2] != null ? photoInfo[2] : photoInfo[0];

            if (!mIsScrolling) {
                // Only load image if user is not scrolling
                loadObsImage(position, obsImage, photoFilename, photoInfo[2] != null);
            }

            mObservationPhotoNames.put(position, photoFilename);
            mImageViews.put(position, obsImage);
        } else {
            obsImage.setVisibility(View.INVISIBLE);
            mObservationPhotoNames.put(position, null);
            mImageViews.put(position, null);
        }


        Long observationTimestamp = c.getLong(c.getColumnIndexOrThrow(Observation.OBSERVED_ON));

        if (!mIsGrid) {
            if (observationTimestamp == 0) {
                // No observation date set - don't show it
                dateObserved.setVisibility(View.INVISIBLE);
            } else {
                dateObserved.setVisibility(View.VISIBLE);
                Timestamp observationDate = new Timestamp(observationTimestamp);
                dateObserved.setText(CommentsIdsAdapter.formatIdDate(observationDate));
            }
        }

        Long commentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.COMMENTS_COUNT));
        Long idsCount = c.getLong(c.getColumnIndexOrThrow(Observation.IDENTIFICATIONS_COUNT));
        Long lastCommentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_COMMENTS_COUNT));
        Long lastIdCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_IDENTIFICATIONS_COUNT));

        if (commentsCount + idsCount == 0) {
            // No comments/IDs - don't display the indicator
            commentIdContainer.setVisibility(View.INVISIBLE);
            commentIdContainer.setClickable(false);
        } else {
            commentIdContainer.setClickable(true);
            commentIdContainer.setVisibility(View.VISIBLE);

            if ((lastCommentsCount == null) || (lastCommentsCount < commentsCount) ||
                    (lastIdCount == null) || (lastIdCount < idsCount)) {
                // There are unread comments/IDs
                commentIdContainer.setVisibility(View.VISIBLE);
                if (mIsGrid) {
                    commentIdContainer.setBackgroundColor(Color.parseColor("#EA118D"));
                } else {
                    commentCount.setTextColor(Color.parseColor("#EA118D"));
                    idCount.setTextColor(Color.parseColor("#EA118D"));

                    commentIcon.setColorFilter(Color.parseColor("#EA118D"));
                    idIcon.setColorFilter(Color.parseColor("#EA118D"));
                }
            } else {
                if (mIsGrid) {
                    // Don't show comment/id count if no unread ones are available
                    commentIdContainer.setVisibility(View.INVISIBLE);
                } else {
                    commentCount.setTextColor(Color.parseColor("#959595"));
                    idCount.setTextColor(Color.parseColor("#959595"));

                    commentIcon.setColorFilter(Color.parseColor("#707070"));
                    idIcon.setColorFilter(Color.parseColor("#707070"));
                }
            }

            if (commentsCount > 0) {
                commentCount.setText(String.valueOf(commentsCount));
                commentCount.setVisibility(View.VISIBLE);
                commentIcon.setVisibility(View.VISIBLE);
            } else {
                commentCount.setVisibility(View.GONE);
                commentIcon.setVisibility(View.GONE);
            }

            if (idsCount > 0) {
                idCount.setText(String.valueOf(idsCount));
                idCount.setVisibility(View.VISIBLE);
                idIcon.setVisibility(View.VISIBLE);
            } else {
                idCount.setVisibility(View.GONE);
                idIcon.setVisibility(View.GONE);
            }

            commentIdContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isNetworkAvailable()) {
                        Toast.makeText(mContext.getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Show the comments/IDs for the observation
                    Uri uri = ContentUris.withAppendedId(Observation.CONTENT_URI, obsId);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri, mContext, ObservationViewerActivity.class);
                    intent.putExtra(ObservationViewerActivity.SHOW_COMMENTS, true);
                    mContext.startActivity(intent);
                }
            });
        }

        Long syncedAt = c.getLong(c.getColumnIndexOrThrow(Observation._SYNCED_AT));
        Long updatedAt = c.getLong(c.getColumnIndexOrThrow(Observation._UPDATED_AT));
        Boolean syncNeeded = (syncedAt == null) || (updatedAt > syncedAt);

        // if there's a photo and it is local
        if (syncNeeded == false &&
                photoInfo != null &&
                photoInfo[2] == null &&
                photoInfo[3] != null) {
            if (photoInfo[4] == null) {
                syncNeeded = true;
            } else {
                Long photoSyncedAt = Long.parseLong(photoInfo[4]);
                Long photoUpdatedAt = Long.parseLong(photoInfo[3]);
                if (photoUpdatedAt > photoSyncedAt) {
                    syncNeeded = true;
                }
            }
        }

        if (!syncNeeded) {
            // See if it's an existing observation with a new photo:w

            Cursor opc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    new String[]{
                            ObservationPhoto._ID,
                            ObservationPhoto._OBSERVATION_ID,
                            ObservationPhoto._PHOTO_ID,
                            ObservationPhoto.PHOTO_URL,
                            ObservationPhoto._UPDATED_AT,
                            ObservationPhoto._SYNCED_AT
                    },
                    "_observation_id = ? AND photo_url IS NULL AND _synced_at IS NULL",
                    new String[] { String.valueOf(obsId) },
                    ObservationPhoto._ID);
            if (opc.getCount() > 0) {
                syncNeeded = true;
            }
            opc.close();
        }


        if (!mIsGrid) {
            if ((placeGuessValue == null) || (placeGuessValue.length() == 0)) {
                if ((longitude == null) || (latitude == null)) {
                    // Show coordinates instead
                    placeGuess.setText(String.format(mContext.getString(R.string.location_coords_no_acc),
                            String.format("%.4f...", latitude), String.format("%.4f...", longitude)));
                } else {
                    // No location at all
                    placeGuess.setText(R.string.no_location);
                }
            } else {
                placeGuess.setText(placeGuessValue);
            }
        }


        String speciesGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.SPECIES_GUESS));
        String preferredCommonName = c.getString(c.getColumnIndexOrThrow(Observation.PREFERRED_COMMON_NAME));
        progress.setVisibility(View.GONE);
        if (!mIsGrid) {
            placeGuess.setTextColor(Color.parseColor("#666666"));
            dateObserved.setVisibility(View.VISIBLE);
            speciesGuess.setTextColor(Color.parseColor("#000000"));
        }

        if (preferredCommonName != null) {
            speciesGuess.setText(preferredCommonName);
        } else if ((speciesGuessValue != null) && (speciesGuessValue.trim().length() > 0)) {
            speciesGuess.setText("\"" + speciesGuessValue + "\"");
        } else {
            speciesGuess.setText(R.string.unknown_species);
        }


        boolean hasErrors = (mApp.getErrorsForObservation(externalObsId.intValue()).length() > 0);
        if (hasErrors)  {
            view.setBackgroundColor(Color.parseColor("#F3D3DA"));
            if (!mIsGrid) {
                placeGuess.setText(R.string.needs_your_attention);
                locationIcon.setVisibility(View.GONE);
            }
        } else {
            if (!mIsGrid) {
                locationIcon.setVisibility(View.VISIBLE);
                view.setBackgroundColor(Color.parseColor("#FFFFFF"));
            } else {
                view.setBackgroundColor(Color.parseColor("#DDDDDD"));
            }
        }

        if (syncNeeded) {
            // This observations needs to be synced

            if (mApp.getObservationIdBeingSynced() == obsId) {
                // Observation is currently being uploaded
                view.setBackgroundColor(Color.parseColor("#E3EDCD"));

                if (!mIsGrid) {
                    placeGuess.setText(R.string.uploading);
                    placeGuess.setTextColor(Color.parseColor("#74Ac00"));
                    locationIcon.setVisibility(View.GONE);
                    dateObserved.setVisibility(View.GONE);
                }

                progress.setVisibility(View.VISIBLE);
                commentIdContainer.setVisibility(View.INVISIBLE);
            } else {
                // Observation is waiting to be uploaded
                if (!hasErrors) {
                    view.setBackgroundColor(Color.parseColor("#E3EDCD"));
                    if (!mIsGrid) {
                        placeGuess.setText(R.string.waiting_to_upload);
                        locationIcon.setVisibility(View.GONE);
                    }
                }
            }
        } else {
            if (!hasErrors) {
                if (!mIsGrid) {
                    view.setBackgroundColor(Color.parseColor("#FFFFFF"));
                } else {
                    view.setBackgroundColor(Color.parseColor("#DDDDDD"));
                }
            }
        }

        return view;
    }

    // Should the specified observation be locked for editing (e.g. it's currently being uploaded)
    public boolean isLocked(Uri uri) {
        Cursor c = mContext.managedQuery(uri, Observation.PROJECTION, null, null, null);
        Observation obs = new Observation(c);

        Integer obsId = obs._id;
        String[] photoInfo = mPhotoInfo.get(obsId);
        Timestamp syncedAt = obs._synced_at;
        Timestamp updatedAt = obs._updated_at;
        Boolean syncNeeded = (syncedAt == null) || (updatedAt.after(syncedAt));

        // if there's a photo and it is local
        if (syncNeeded == false &&
                photoInfo != null &&
                photoInfo[2] == null &&
                photoInfo[3] != null) {
            if (photoInfo[4] == null) {
                syncNeeded = true;
            } else {
                Long photoSyncedAt = Long.parseLong(photoInfo[4]);
                Long photoUpdatedAt = Long.parseLong(photoInfo[3]);
                if (photoUpdatedAt > photoSyncedAt) {
                    syncNeeded = true;
                }
            }
        }

        if (!syncNeeded) {
            // See if it's an existing observation with a new photo
            Cursor opc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    new String[]{
                            ObservationPhoto._ID,
                            ObservationPhoto._OBSERVATION_ID,
                            ObservationPhoto._PHOTO_ID,
                            ObservationPhoto.PHOTO_URL,
                            ObservationPhoto._UPDATED_AT,
                            ObservationPhoto._SYNCED_AT
                    },
                    "_observation_id = ? AND photo_url IS NULL AND _synced_at IS NULL",
                    new String[] { String.valueOf(obsId) },
                    ObservationPhoto._ID);
            if (opc.getCount() > 0) {
                syncNeeded = true;
            }
            opc.close();
        }

        if (mApp.getObservationIdBeingSynced() == obsId) {
            // Observation is currently being uploaded - is locked!
            return true;
        } else {
            if (!syncNeeded) {
                // Item hasn't changed (shouldn't be locked)
                return false;
            }

            if (!mApp.getAutoSync() || !isNetworkAvailable()) {
                // Allow editing if not in auto sync mode or when network is not available
                return false;
            } else {
                return true;
            }
        }
    }

    private void refreshCommentsIdSize(final TextView view, Long value) {
        ViewTreeObserver observer = view.getViewTreeObserver();
        // Make sure the height and width of the rectangle are the same (i.e. a square)
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onGlobalLayout() {
                int dimension = view.getHeight();
                ViewGroup.LayoutParams params = view.getLayoutParams();

                if (dimension > view.getWidth()) {
                    // Only resize if there's enough room
                    params.width = dimension;
                    view.setLayoutParams(params);
                }

                ViewTreeObserver observer = view.getViewTreeObserver();
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    observer.removeGlobalOnLayoutListener(this);
                } else {
                    observer.removeOnGlobalLayoutListener(this);
                }
            }
        });

        view.setText(value.toString());
    }


    // For caching observation thumbnails
    private HashMap<String, Bitmap> mObservationThumbnails = new HashMap<>();

    // Used for loading and processing the observation photo in the background (as to not block the UI)
    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private String mFilename = null;
        private int mPosition;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mImageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            mFilename = params[0];
            mPosition = Integer.valueOf(params[1]);

            Bitmap bitmapImage;
            if (mObservationThumbnails.containsKey(mFilename)) {
                // Load from cache
                bitmapImage = mObservationThumbnails.get(mFilename);
            } else {
                // Decode into a thumbnail
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = ImageUtils.calculateInSampleSize(options, 100, 100);

                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false;
                // This decreases in-memory byte-storage per pixel
                options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                bitmapImage = BitmapFactory.decodeFile(mFilename, options);

                if (bitmapImage != null) {
                    bitmapImage = ImageUtils.rotateAccordingToOrientation(bitmapImage, mFilename);
                    bitmapImage = ImageUtils.centerCropBitmap(bitmapImage);
                    bitmapImage = ImageUtils.getRoundedCornerBitmap(bitmapImage);

                    mObservationThumbnails.put(mFilename, bitmapImage);
                }
            }

            return bitmapImage;
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (mImageViewReference != null && bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);

                    if ((!mObservationLoaded.containsKey(mPosition)) || (mObservationLoaded.get(mPosition) == false)) {
                        Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.slow_fade_in);
                        imageView.startAnimation(animation);
                        mObservationLoaded.put(mPosition, true);
                    }
                }
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    private void loadObsImage(final int position, ImageView imageView, String name, boolean isOnline) {

        if (isOnline) {
            // Online image
            UrlImageViewCallback callback = new UrlImageViewCallback() {
                float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, mContext.getResources().getDisplayMetrics());

                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    if (mIsGrid) {
                        imageView.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                    }

                    imageView.setVisibility(View.VISIBLE);
                    if ((!mObservationLoaded.containsKey(position)) || (mObservationLoaded.get(position) == false)) {
                        Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.slow_fade_in);
                        imageView.startAnimation(animation);
                        mObservationLoaded.put(position, true);
                    }
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    return ImageUtils.getRoundedCornerBitmap(ImageUtils.centerCropBitmap(loadedBitmap), px);
                }
            };

            UrlImageViewHelper.setUrlDrawable(imageView, name, callback);

        } else {
            // Offline image

            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            task.execute(name, String.valueOf(position));
        }
    }

    private HashMap<Integer, ImageView> mImageViews;
    private HashMap<Integer, String> mObservationPhotoNames;
    private HashMap<Integer, Boolean> mObservationLoaded;

    // Load an observation image for one of the views
    private void loadImageByPosition(int position) {
        if (!mImageViews.containsKey(position) || !mObservationPhotoNames.containsKey(position)) return;

        ImageView imageView = mImageViews.get(position);
        String photoName = mObservationPhotoNames.get(position);

        if ((photoName == null) || (imageView == null)) return;

        loadObsImage(position, imageView, photoName, photoName.startsWith("http://"));
    }


    @Override
    public void onScrollStateChanged(AbsListView listView, int state) {
        switch (state) {
            case SCROLL_STATE_FLING:
            case SCROLL_STATE_TOUCH_SCROLL:
                mIsScrolling = true;
                break;
            case SCROLL_STATE_IDLE:
                mIsScrolling = false;
                for (int visiblePosition = listView.getFirstVisiblePosition(); visiblePosition <= listView.getLastVisiblePosition(); visiblePosition++) {
                    loadImageByPosition(visiblePosition);
                }
                break;
        }
    }
    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {

    }
}

