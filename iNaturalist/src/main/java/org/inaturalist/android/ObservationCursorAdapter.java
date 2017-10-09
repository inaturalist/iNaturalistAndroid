package org.inaturalist.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
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

import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

class ObservationCursorAdapter extends SimpleCursorAdapter implements AbsListView.OnScrollListener {
    private int mDimension;
    private HashMap<String, String[]> mPhotoInfo = new HashMap<String, String[]>();
    private boolean mIsGrid;

    private final Activity mContext;
    private INaturalistApp mApp;
    private PullToRefreshGridViewExtended mGrid;

    private HashMap<Integer, Boolean> mObservationLoaded;

    private CircularProgressBar mCurrentProgressBar = null;

    public ObservationCursorAdapter(Context context, Cursor c) {
        this(context, c, false, null);
    }

    public ObservationCursorAdapter(Context context, Cursor c, boolean isGrid, PullToRefreshGridViewExtended grid) {
        super(context, isGrid ? R.layout.observation_grid_item : R.layout.list_item, c, new String[] {}, new int[] {});
        mIsGrid = isGrid;
        mGrid = grid;
        mContext = (Activity)context;
        mApp = (INaturalistApp) mContext.getApplicationContext();

        mObservationLoaded = new HashMap<>();

        getPhotoInfo();
    }

    // Loads the photo info map from a cached file (for faster loading)
    private void loadPhotoInfo() {
        mPhotoInfo = new HashMap<>();

        File file = new File(mContext.getFilesDir(), "observations_photo_info.dat");
        try {
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
            mPhotoInfo = (HashMap<String, String[]>) inputStream.readObject();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Save the photo info map into a file (for caching and faster loading)
    private void savePhotoInfo() {
        File file = new File(mContext.getFilesDir(), "observations_photo_info.dat");
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(mPhotoInfo);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void refreshCursor() {
        refreshCursor(null);
    }

    public void refreshCursor(String speciesGuess) {
        SharedPreferences prefs = mContext.getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
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

        getPhotoInfo();
    }

    /**
     * Retrieves photo ids and orientations for photos associated with the listed observations.
     */
    public void getPhotoInfo() {
        loadPhotoInfo();
        Cursor c = getCursor();
        int originalPosition = c.getPosition();
        if (c.getCount() == 0) return;

        ArrayList<Long> obsIds = new ArrayList<>();
        ArrayList<Long> externalObsIds = new ArrayList<>();
        HashMap<Long, String> obsUUIDs = new HashMap<>();

        c.moveToFirst();
        while (!c.isAfterLast()) {
            long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
            long obsExternalId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
            String obsUUID = c.getString(c.getColumnIndexOrThrow(Observation.UUID));

            obsIds.add(obsId);
            externalObsIds.add(obsExternalId);
            obsUUIDs.put(obsId, obsUUID);

            c.moveToNext();
        }

        c.moveToPosition(originalPosition);

        // Add any photos that were added/changed
        Cursor onlinePc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                new String[]{ ObservationPhoto._OBSERVATION_ID, ObservationPhoto.OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL, ObservationPhoto.PHOTO_FILENAME, ObservationPhoto.ORIGINAL_PHOTO_FILENAME, ObservationPhoto.POSITION },
                "(_observation_id IN (" + StringUtils.join(obsIds, ",") + ") OR observation_id IN (" + StringUtils.join(externalObsIds, ",") + "))",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);

        onlinePc.moveToFirst();
        while (!onlinePc.isAfterLast()) {
            String photoUrl = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
            String photoFilename = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
            Long obsId = onlinePc.getLong(onlinePc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
            String obsUUID = obsUUIDs.get(obsId);

            if ((photoFilename != null) && (!(new File(photoFilename).exists()))) {
                // Our local copy file was deleted (probably user deleted cache or similar) - try and use original filename from gallery
                String originalPhotoFilename = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.ORIGINAL_PHOTO_FILENAME));
                photoFilename = originalPhotoFilename;
            }

            onlinePc.moveToNext();

            if (mPhotoInfo.containsKey(obsUUID)) {
                continue;
            }

            mPhotoInfo.put(
                    obsUUID,
                    new String[] {
                            photoFilename,
                            null,
                            photoUrl,
                            null,
                            null
                    });
        }
        onlinePc.close();

        savePhotoInfo();
    }

    public void refreshPhotoInfo() {
        mPhotoInfo = new HashMap<String, String[]>();
        getPhotoInfo();
    }

    private static class ViewHolder {
        public ImageView obsImage;
        public ImageView obsIconicImage;
        public TextView speciesGuess;
        public TextView dateObserved;
        public ViewGroup commentIdContainer;
        public ViewGroup leftContainer;
        public View progress;
        public View progressInner;

        public ImageView commentIcon;
        public ImageView idIcon;
        public ImageView locationIcon;

        public TextView commentCount;
        public TextView idCount;
        public TextView placeGuess;

        public ViewHolder(ViewGroup view) {
            obsImage = (ImageView) view.findViewById(R.id.observation_pic);
            obsIconicImage = (ImageView) view.findViewById(R.id.observation_iconic_pic);
            speciesGuess = (TextView) view.findViewById(R.id.species_guess);
            dateObserved = (TextView) view.findViewById(R.id.date);
            commentIdContainer = (ViewGroup) view.findViewById(R.id.comment_id_container);
            leftContainer = (ViewGroup) view.findViewById(R.id.left_container);

            commentIcon = (ImageView) view.findViewById(R.id.comment_pic);
            idIcon = (ImageView) view.findViewById(R.id.id_pic);
            commentCount = (TextView) view.findViewById(R.id.comment_count);
            idCount = (TextView) view.findViewById(R.id.id_count);

            placeGuess = (TextView) view.findViewById(R.id.place_guess);
            locationIcon = (ImageView) view.findViewById(R.id.location_icon);

            progress = view.findViewById(R.id.progress);
            progressInner = view.findViewById(R.id.progress_inner);
        }

    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);
        ViewHolder holder;
        Cursor c = this.getCursor();
        if (c.getCount() == 0) {
            return view;
        }
        c.moveToPosition(position);

        final Long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
        final String obsUUID = c.getString(c.getColumnIndexOrThrow(Observation.UUID));
        String speciesGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.SPECIES_GUESS));

        if (convertView == null) {
            holder = new ViewHolder((ViewGroup) view);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        final ImageView obsImage = holder.obsImage;
        ImageView obsIconicImage = holder.obsIconicImage;
        TextView speciesGuess = holder.speciesGuess;
        TextView dateObserved = holder.dateObserved;
        ViewGroup commentIdContainer = holder.commentIdContainer;
        ViewGroup leftContainer = holder.leftContainer;

        ImageView commentIcon = holder.commentIcon;
        ImageView idIcon = holder.idIcon;
        TextView commentCount = holder.commentCount;
        TextView idCount = holder.idCount;

        TextView placeGuess = holder.placeGuess;
        ImageView locationIcon = holder.locationIcon;

        View progress = holder.progress;
        View progressInner = holder.progressInner;

        final Long externalObsId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
        String placeGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.PLACE_GUESS));
        String privatePlaceGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.PRIVATE_PLACE_GUESS));
        Double latitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LATITUDE));
        Double longitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LONGITUDE));
        Double privateLatitude = c.getDouble(c.getColumnIndexOrThrow(Observation.PRIVATE_LATITUDE));
        Double privateLongitude = c.getDouble(c.getColumnIndexOrThrow(Observation.PRIVATE_LONGITUDE));

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


        String iconicTaxonName = c.getString(c.getColumnIndexOrThrow(Observation.ICONIC_TAXON_NAME));

        int iconResource = getIconicTaxonDrawable(iconicTaxonName);

        obsIconicImage.setVisibility(View.VISIBLE);
        obsIconicImage.setImageResource(iconResource);
        obsImage.setVisibility(View.INVISIBLE);

        String[] photoInfo = mPhotoInfo.get(obsUUID);

        if (photoInfo != null) {
            String photoFilename = photoInfo[2] != null ? photoInfo[2] : photoInfo[0];

            if (mIsGrid && (convertView == null)) {
                obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));

                view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } else {
                            view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }

                        mDimension = mGrid.getColumnWidth();
                        obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                    }
                });
            }

            loadObsImage(position, obsImage, photoFilename, photoInfo[2] != null);
        } else {
            obsImage.setVisibility(View.INVISIBLE);
        }

        Long observationTimestamp = c.getLong(c.getColumnIndexOrThrow(Observation.TIME_OBSERVED_AT));

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


            if (!mIsGrid) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) leftContainer.getLayoutParams();
                if (dateObserved.getText().length() > String.format("  %d  %d", idsCount, commentsCount).length()) {
                    params.addRule(RelativeLayout.LEFT_OF, R.id.date);
                } else {
                    params.addRule(RelativeLayout.LEFT_OF, R.id.comment_id_container);
                }

                leftContainer.setLayoutParams(params);
            }
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


        if (!mIsGrid) {
            if (((placeGuessValue == null) || (placeGuessValue.length() == 0)) &&
                ((privatePlaceGuessValue == null) || (privatePlaceGuessValue.length() == 0))) {
                if ((longitude != 0f) || (latitude != 0f) || (privateLatitude != 0f) || (privateLongitude != 0f)) {
                    // Show coordinates instead
                    placeGuess.setText(String.format(mContext.getString(R.string.location_coords_no_acc),
                            String.format("%.4f...", latitude != 0f ? latitude : privateLatitude), String.format("%.4f...", longitude != 0f ? longitude : privateLongitude)));
                } else {
                    // No location at all
                    placeGuess.setText(R.string.no_location);
                }
            } else {
                placeGuess.setText((placeGuessValue != null) && (placeGuessValue.length() > 0) ?
                    placeGuessValue : privatePlaceGuessValue);
            }
        }


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
            view.setBackgroundResource(R.drawable.observation_item_error_background);
            if (!mIsGrid) {
                placeGuess.setText(R.string.needs_your_attention);
                locationIcon.setVisibility(View.GONE);
            }
        } else {
            if (!mIsGrid) {
                locationIcon.setVisibility(View.VISIBLE);
                view.setBackgroundResource(R.drawable.observation_item_background);
            } else {
                view.setBackgroundColor(Color.parseColor("#DDDDDD"));
            }
        }

        if (mApp.getObservationIdBeingSynced() == obsId) {
            CircularProgressBar currentProgressBar = (CircularProgressBar) (progressInner != null ? progressInner : progress);
            if (currentProgressBar != mCurrentProgressBar) {
                currentProgressBar.setProgress(0);
            }

            mCurrentProgressBar = currentProgressBar;

            // Observation is currently being uploaded
            view.setBackgroundResource(R.drawable.observation_item_uploading_background);

            if (!mIsGrid) {
                placeGuess.setText(R.string.uploading);
                placeGuess.setTextColor(Color.parseColor("#74Ac00"));
                locationIcon.setVisibility(View.GONE);
                dateObserved.setVisibility(View.GONE);
            }

            progress.setVisibility(View.VISIBLE);
            commentIdContainer.setVisibility(View.INVISIBLE);

        } else if (syncNeeded && (mApp.getObservationIdBeingSynced() != obsId)) {
            // This observation needs to be synced (and waiting to be synced)
            if (!hasErrors) {
                view.setBackgroundResource(R.drawable.observation_item_uploading_background);
                if (!mIsGrid) {
                    placeGuess.setText(R.string.waiting_to_upload);
                    locationIcon.setVisibility(View.GONE);
                }
            }
        } else {
            if (!hasErrors) {
                if (!mIsGrid) {
                    view.setBackgroundResource(R.drawable.observation_item_background);
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
        String[] photoInfo = mPhotoInfo.get(obs.uuid);
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

            mUrlToImageView.remove(mFilename);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    private Map<String, ImageView> mUrlToImageView = new HashMap<>();

    private void loadObsImage(final int position, final ImageView imageView, final String name, boolean isOnline) {

        if (mUrlToImageView.containsKey(name) && mUrlToImageView.get(name).equals(imageView)){
            return;
        }

        mUrlToImageView.put(name, imageView);

        if (isOnline) {
            // Online image
            Picasso.with(mContext)
                    .load(name)
                    .fit()
                    .centerCrop()
                    .into(imageView, new Callback() {
                        @Override
                        public void onSuccess() {
                            imageView.setVisibility(View.VISIBLE);
                            mUrlToImageView.remove(name);
                        }

                        @Override
                        public void onError() {
                            mUrlToImageView.remove(name);
                        }
                    });

        } else {
            // Offline image

            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            task.execute(name, String.valueOf(position));
        }
    }


    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        final Picasso picasso = Picasso.with(mContext);

        if (scrollState == SCROLL_STATE_IDLE || scrollState == SCROLL_STATE_TOUCH_SCROLL) {
            picasso.resumeTag(mContext);
        } else {
            picasso.pauseTag(mContext);
        }
    }

    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {

    }

    public static int getIconicTaxonDrawable(String iconicTaxonName) {
        int iconResource;

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

        return iconResource;
    }

    public void updateProgress(int observationId, float progress) {
        if (mCurrentProgressBar != null) mCurrentProgressBar.setProgressWithAnimation(progress);
    }
}

