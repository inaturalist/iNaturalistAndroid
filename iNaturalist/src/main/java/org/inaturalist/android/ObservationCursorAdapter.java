package org.inaturalist.android;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

@SuppressWarnings("WeakerAccess")
class ObservationCursorAdapter extends SimpleCursorAdapter implements AbsListView.OnScrollListener {
    private static final String TAG = "SimpleCursorAdapter";
    private final ActivityHelper mHelper;

    private int mDimension;
    private HashMap<String, String[]> mPhotoInfo = new HashMap<>();
    private HashMap<String, Boolean> mHasSounds = new HashMap<>();
    private boolean mIsGrid;

    private final Activity mContext;
    private INaturalistApp mApp;
    private GridView mGrid;

    private CircularProgressBar mCurrentProgressBar = null;
    private boolean mLoadingAdditionalObs = false;
    private OnLoadingMoreResultsListener mOnLoadingMoreResultsListener = null;
    private boolean mNoMoreObsLeft = false;

    private Set<Long> mSelectedObservations = new HashSet<>();

    private boolean mMultiSelectionMode = false;

    public interface OnLoadingMoreResultsListener {
        void onLoadingMoreResultsStart();
        void onLoadingMoreResultsFinish();
        void onLoadingMoreResultsFailed();
    }

    public void setSelectedObservations(Set<Long> observations) {
        mSelectedObservations = observations;
    }

    public void setOnLoadingMoreResultsListener(OnLoadingMoreResultsListener listener) {
        mOnLoadingMoreResultsListener = listener;
    }

    public void setMultiSelectionMode(boolean mode) {
        mMultiSelectionMode = mode;
    }

    public ObservationCursorAdapter(Context context, Cursor c) {
        this(context, c, false, null);
    }

    public ObservationCursorAdapter(Context context, Cursor c, boolean isGrid, GridView grid) {
        super(context, isGrid ? R.layout.observation_grid_item : R.layout.observation_list_item, c, new String[] {}, new int[] {});

        Logger.tag(TAG).debug("initialize");

        mIsGrid = isGrid;
        mGrid = grid;
        mContext = (Activity)context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mHelper = new ActivityHelper(mContext);

        getPhotoInfo(true);

        GetAdditionalObsReceiver mGetAdditionalObsReceiver = new GetAdditionalObsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.ACTION_GET_ADDITIONAL_OBS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mGetAdditionalObsReceiver, filter, mContext);
    }

    private class GetAdditionalObsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            refreshCursor();
            refreshPhotoInfo();

            mLoadingAdditionalObs = false;
            if (mOnLoadingMoreResultsListener != null) {
                boolean success = false;
                if (extras != null) {
                    success = extras.getBoolean(INaturalistService.SUCCESS);
                }

                if (success) {
                    mOnLoadingMoreResultsListener.onLoadingMoreResultsFinish();
                } else {
                    mOnLoadingMoreResultsListener.onLoadingMoreResultsFailed();
                }
            }

            if (extras != null) {
                int obsCount = extras.getInt(INaturalistService.OBSERVATION_COUNT);
                if (obsCount == 0) {
                    // No more observations left to download
                    mNoMoreObsLeft = true;
                }
            }
        }
    }

    // Loads the photo info map from a cached file (for faster loading)
    private void loadPhotoInfo() {
        mPhotoInfo = new HashMap<>();
        mHasSounds = new HashMap<>();

        File file = new File(mContext.getFilesDir(), "observations_photo_info.dat");
        try {
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
            mPhotoInfo = (HashMap<String, String[]>) inputStream.readObject();
            mHasSounds = (HashMap<String, Boolean>) inputStream.readObject();
            inputStream.close();
        } catch (IOException | ClassCastException | ClassNotFoundException e) {
            Logger.tag(TAG).error(e);
        }
    }

    // Save the photo info map into a file (for caching and faster loading)
    private void savePhotoInfo() {
        File file = new File(mContext.getFilesDir(), "observations_photo_info.dat");
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(mPhotoInfo);
            outputStream.writeObject(mHasSounds);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            Logger.tag(TAG).error(e);
        }
    }

    public void refreshCursor() {
        refreshCursor(null);
    }

    private List<Object> getQuery(String speciesGuess) {
        SharedPreferences prefs = mContext.getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations
        conditions += " AND ((id >= " + mApp.getPrefs().getInt("last_downloaded_id", 0) + ")"; // Don't show obs that was downloaded through activity screen, etc. (not "naturally" by user)
        conditions += " OR (_synced_at IS NULL))";

        String[] selectionArgs = null;

        if (speciesGuess != null) {
            conditions += " AND (" +
                    "(species_guess LIKE ?) OR " +
                    "((species_guess IS NULL) AND (preferred_common_name like ?)))";
            selectionArgs = new String[] { "%" + speciesGuess + "%", "%" + speciesGuess + "%" };
        }

        ArrayList<Object> list = new ArrayList<>();
        list.add(conditions);
        list.add(selectionArgs);
        return list;
    }

    public void refreshCursor(String speciesGuess) {
        List<Object> results = getQuery(speciesGuess);
        String conditions = (String) results.get(0);
        String[] selectionArgs = (String[]) results.get(1);

        Cursor newCursor = mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                conditions, selectionArgs, Observation.DEFAULT_SORT_ORDER);

        Cursor oldCursor = swapCursor(newCursor);
        if ((oldCursor != null) && (!oldCursor.isClosed())) oldCursor.close();

        getPhotoInfo(true);
    }

    private Cursor getNewCursor() {
        List<Object> results = getQuery(null);
        String conditions = (String) results.get(0);
        String[] selectionArgs = (String[]) results.get(1);

        return mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                conditions, selectionArgs, Observation.DEFAULT_SORT_ORDER);
    }

    /**
     * Retrieves photo ids and orientations for photos associated with the listed observations.
     */
    public void getPhotoInfo(boolean loadFromCache) {
        if (loadFromCache) loadPhotoInfo();

        Cursor c = getNewCursor();
        int originalPosition = c.getPosition();
        if (c.getCount() == 0) {
            c.close();
            return;
        }

        ArrayList<Long> obsIds = new ArrayList<>();
        ArrayList<Long> externalObsIds = new ArrayList<>();
        HashMap<Long, String> obsUUIDs = new HashMap<>();
        ArrayList<String> obsUUIDsList = new ArrayList<>();
        HashMap<Long, String> externalObsUUIDs = new HashMap<>();

        c.moveToFirst();
        while (!c.isAfterLast()) {
            long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
            long obsExternalId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
            String obsUUID = c.getString(c.getColumnIndexOrThrow(Observation.UUID));

            obsIds.add(obsId);
            externalObsIds.add(obsExternalId);
            obsUUIDs.put(obsId, obsUUID);
            obsUUIDsList.add('"' + obsUUID + '"');
            externalObsUUIDs.put(obsExternalId, obsUUID);

            c.moveToNext();
        }

        c.moveToPosition(originalPosition);
        c.close();

        // Add any photos that were added/changed
        Cursor onlinePc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                new String[]{ ObservationPhoto._OBSERVATION_ID, ObservationPhoto.OBSERVATION_ID, ObservationPhoto._PHOTO_ID, ObservationPhoto.PHOTO_URL, ObservationPhoto.PHOTO_FILENAME, ObservationPhoto.ORIGINAL_PHOTO_FILENAME, ObservationPhoto.POSITION },
                "(observation_uuid IN (" + StringUtils.join(obsUUIDsList, ",") + "))",
                null,
                ObservationPhoto.DEFAULT_SORT_ORDER);

        if (onlinePc != null) {
            onlinePc.moveToFirst();
            while (!onlinePc.isAfterLast()) {
                String photoUrl = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                String photoFilename = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
                Long obsId = onlinePc.getLong(onlinePc.getColumnIndexOrThrow(ObservationPhoto._OBSERVATION_ID));
                String obsUUID = obsUUIDs.get(obsId);

                if ((photoFilename != null) && (!(new File(photoFilename).exists()))) {
                    // Our local copy file was deleted (probably user deleted cache or similar) - try and use original filename from gallery
                    photoFilename = onlinePc.getString(onlinePc.getColumnIndexOrThrow(ObservationPhoto.ORIGINAL_PHOTO_FILENAME));
                }

                onlinePc.moveToNext();

                if (mPhotoInfo.containsKey(obsUUID)) {
                    continue;
                }

                mPhotoInfo.put(
                        obsUUID,
                        new String[]{
                                photoFilename,
                                null,
                                photoUrl,
                                null,
                                null
                        });
            }
            onlinePc.close();
        }

                // Add any photos that were added/changed
        Cursor soundCursor = mContext.getContentResolver().query(ObservationSound.CONTENT_URI,
                new String[]{ ObservationSound._OBSERVATION_ID, ObservationSound.OBSERVATION_ID },
                "((_observation_id IN (" + StringUtils.join(obsIds, ",") + ") OR observation_id IN (" + StringUtils.join(externalObsIds, ",") + "))) AND " +
                        "(is_deleted IS NULL OR is_deleted = 0)",
                null,
                ObservationSound.DEFAULT_SORT_ORDER);

        if (soundCursor != null) {
            soundCursor.moveToFirst();
            while (!soundCursor.isAfterLast()) {
                Long obsId = soundCursor.getLong(soundCursor.getColumnIndexOrThrow(ObservationSound._OBSERVATION_ID));
                Long externalObsId = soundCursor.getLong(soundCursor.getColumnIndexOrThrow(ObservationSound.OBSERVATION_ID));
                String obsUUID = obsUUIDs.get(obsId);

                if (obsUUID == null) {
                    obsUUID = externalObsUUIDs.get(externalObsId);
                }

                soundCursor.moveToNext();

                if (mHasSounds.containsKey(obsUUID)) {
                    continue;
                }

                mHasSounds.put(obsUUID, true);
            }
            soundCursor.close();
        }

        savePhotoInfo();
    }

    public void refreshPhotoInfo() {
        mPhotoInfo = new HashMap<>();
        mHasSounds = new HashMap<>();
        getPhotoInfo(false);
    }

    private static class ViewHolder {
        public long obsId;

        public ViewGroup checkboxContainer;
        public View checkboxBackground;
        public ImageView checkbox;
        public ViewGroup container;

        public ImageView obsImage;
        public ImageView obsIconicImage;
        public TextView speciesGuess;
        public TextView dateObserved;
        public View commentIdContainer;
        public View progress;
        public View progressInner;
        public View soundsIndicator;

        public ImageView commentIcon;
        public ImageView idIcon;
        public ImageView locationIcon;

        public TextView commentCount;
        public TextView idCount;
        public TextView placeGuess;
        public String photoFilename;
        public Boolean syncNeeded;
        public boolean hasErrors;
        public boolean isBeingSynced;
        public Long updatedAt;
        public Observation observation;

        public ViewHolder(ViewGroup view) {
            obsId = -1;

            checkboxContainer = view.findViewById(R.id.checkbox_container);
            checkboxBackground = view.findViewById(R.id.checkbox_background);
            checkbox = view.findViewById(R.id.checkbox);
            container = view.findViewById(R.id.container);
            obsImage = view.findViewById(R.id.observation_pic);
            obsIconicImage = view.findViewById(R.id.observation_iconic_pic);
            speciesGuess = view.findViewById(R.id.species_guess);
            dateObserved = view.findViewById(R.id.date);
            commentIdContainer = view.findViewById(R.id.comment_id_container);

            commentIcon = view.findViewById(R.id.comment_pic);
            idIcon = view.findViewById(R.id.id_pic);
            commentCount = view.findViewById(R.id.comment_count);
            idCount = view.findViewById(R.id.id_count);

            placeGuess = view.findViewById(R.id.place_guess);
            locationIcon = view.findViewById(R.id.location_icon);

            progress = view.findViewById(R.id.progress);
            progressInner = view.findViewById(R.id.progress_inner);

            soundsIndicator = view.findViewById(R.id.has_sounds);
        }

    }

    // TODO This uses INVISIBLE in multiple locations where we should be using GONE
    // to maximize screen real estate. I've confirmed the current mix works in list mode, but
    // before we set everything to GONE we need to debug the grid mode and make sure removing
    // views does not break the layout
    public View getView(int position, View convertView, ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);
        ViewHolder holder;
        Cursor c = this.getCursor();

        Logger.tag(TAG).debug("getView " + position);

        if (c.getCount() == 0) {
            return view;
        }
        c.moveToPosition(position);

        final long obsId = c.getLong(c.getColumnIndexOrThrow(Observation._ID));
        final long externalObsId = c.getLong(c.getColumnIndexOrThrow(Observation.ID));
        long updatedAt = c.getLong(c.getColumnIndexOrThrow(Observation._UPDATED_AT));
        final String obsUUID = c.getString(c.getColumnIndexOrThrow(Observation.UUID));
        String speciesGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.SPECIES_GUESS));
        String[] photoInfo = obsUUID != null ? mPhotoInfo.get(obsUUID) : null;
        boolean hasSounds = (obsUUID != null && mHasSounds.get(obsUUID) != null);
        boolean hasErrors = (mApp.getErrorsForObservation((int) externalObsId).length() > 0);

        if (convertView == null) {
            holder = new ViewHolder((ViewGroup) view);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        ViewGroup checkboxContainer = holder.checkboxContainer;
        View checkboxBackground = holder.checkboxBackground;
        ImageView checkbox = holder.checkbox;
        ViewGroup container = holder.container;
        final ImageView obsImage = holder.obsImage;
        ImageView obsIconicImage = holder.obsIconicImage;
        TextView speciesGuess = holder.speciesGuess;
        TextView dateObserved = holder.dateObserved;

        View commentIdContainer = holder.commentIdContainer;
        if (mIsGrid && (commentIdContainer == null)) return view;

        ImageView commentIcon = holder.commentIcon;
        ImageView idIcon = holder.idIcon;
        TextView commentCount = holder.commentCount;
        TextView idCount = holder.idCount;

        if (!mIsGrid) {
            // !isGrid uses a constraintlayout which has no concept of view groups, so we manually
            // build one. Note: androidx.constraintlayout.widget.Group will not work here
             commentIdContainer = new DelegatingConstraintViewGroup(mContext,
                    commentIcon, commentCount, idIcon, idCount);
        }

        TextView placeGuess = holder.placeGuess;
        ImageView locationIcon = holder.locationIcon;

        View progress = holder.progress;
        View progressInner = holder.progressInner;

        View soundsIndicator = holder.soundsIndicator;

        String placeGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.PLACE_GUESS));
        String privatePlaceGuessValue = c.getString(c.getColumnIndexOrThrow(Observation.PRIVATE_PLACE_GUESS));
        double latitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LATITUDE));
        double longitude = c.getDouble(c.getColumnIndexOrThrow(Observation.LONGITUDE));
        double privateLatitude = c.getDouble(c.getColumnIndexOrThrow(Observation.PRIVATE_LATITUDE));
        double privateLongitude = c.getDouble(c.getColumnIndexOrThrow(Observation.PRIVATE_LONGITUDE));

        (mIsGrid ? checkboxContainer : checkbox).setVisibility(mMultiSelectionMode ? View.VISIBLE : View.GONE);

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

            if (hasSounds && (photoInfo != null)) {
                soundsIndicator.setVisibility(View.VISIBLE);
            } else {
                soundsIndicator.setVisibility(View.GONE);
            }
        }

        String iconicTaxonName = c.getString(c.getColumnIndexOrThrow(Observation.ICONIC_TAXON_NAME));

        int iconResource = getIconicTaxonDrawable(iconicTaxonName);

        obsIconicImage.setVisibility(View.VISIBLE);
        obsIconicImage.setImageResource(iconResource);
        obsImage.setVisibility(View.INVISIBLE);

        if (photoInfo != null) {
            String photoFilename = photoInfo[2] != null ? photoInfo[2] : photoInfo[0];

            if (mIsGrid && (convertView == null)) {
                obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));

                view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        mDimension = mGrid.getColumnWidth();
                        obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                    }
                });
            }

            loadObsImage(position, obsImage, photoFilename, photoInfo[2] != null, false);

            holder.photoFilename = photoFilename;
        } else {
            obsImage.setVisibility(View.INVISIBLE);
            holder.photoFilename = null;
            mImageViewToUrlExpected.put(obsImage, null);

            if (hasSounds) {
                obsIconicImage.setImageResource(R.drawable.sound);
            }
        }

        long observationTimestamp = 0L;
        if (c.isNull(c.getColumnIndexOrThrow(Observation.TIME_OBSERVED_AT))) {
            if (!c.isNull(c.getColumnIndexOrThrow(Observation.OBSERVED_ON))) {
                observationTimestamp = c.getLong(c.getColumnIndexOrThrow(Observation.OBSERVED_ON));
            }
        } else {
            observationTimestamp = c.getLong(c.getColumnIndexOrThrow(Observation.TIME_OBSERVED_AT));
        }

        if (!mIsGrid) {
            if (observationTimestamp == 0) {
                // No observation date set - don't show it
                dateObserved.setVisibility(View.GONE);
            } else {
                dateObserved.setVisibility(View.VISIBLE);
                Timestamp observationDate = new Timestamp(observationTimestamp);
                dateObserved.setText(CommentsIdsAdapter.formatIdDate(mContext, observationDate));
            }
        }

        long commentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.COMMENTS_COUNT));
        long idsCount = c.getLong(c.getColumnIndexOrThrow(Observation.IDENTIFICATIONS_COUNT));
        long lastCommentsCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_COMMENTS_COUNT));
        long lastIdCount = c.getLong(c.getColumnIndexOrThrow(Observation.LAST_IDENTIFICATIONS_COUNT));

        if (commentsCount + idsCount == 0) {
            // No comments/IDs - don't display the indicator
            commentIdContainer.setVisibility(View.GONE);
            commentIdContainer.setClickable(false);
        } else {
            commentIdContainer.setClickable(true);
            commentIdContainer.setVisibility(View.VISIBLE);

            if (lastCommentsCount < commentsCount || lastIdCount < idsCount) {
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

            commentIdContainer.setOnClickListener(v -> {
                if (!isNetworkAvailable()) {
                    Toast.makeText(mContext.getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return;
                }

                // Show the comments/IDs for the observation
                Uri uri = ContentUris.withAppendedId(Observation.CONTENT_URI, obsId);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri, mContext, ObservationViewerActivity.class);
                intent.putExtra(ObservationViewerActivity.SHOW_COMMENTS, true);
                mContext.startActivity(intent);
            });
        }

        long syncedAt = c.getLong(c.getColumnIndexOrThrow(Observation._SYNCED_AT));
        boolean syncNeeded = updatedAt > syncedAt;

        if (syncedAt == 0) {
            Logger.tag(TAG).debug(String.format(Locale.ENGLISH,
                    "getView %d: %s: Sync needed - syncedAt == null", position, speciesGuessValue));
        } else if (updatedAt > syncedAt) {
            Logger.tag(TAG).debug(String.format(Locale.ENGLISH,
                    "getView %d: %s: Sync needed - updatedAt (%s) > sycnedAt (%s)", position, speciesGuessValue, updatedAt, syncedAt));
        }

        // if there's a photo and it is local
        if (!syncNeeded &&
                photoInfo != null &&
                photoInfo[2] == null &&
                photoInfo[3] != null) {
            if (photoInfo[4] == null) {
                syncNeeded = true;
                Logger.tag(TAG).debug(String.format(Locale.ENGLISH,
                        "getView %d: %s: Sync needed - photoInfo == null - %s / %s / %s / %s / %s", position, speciesGuessValue, photoInfo[0], photoInfo[1], photoInfo[2], photoInfo[3], photoInfo[4]));
            } else {
                Long photoSyncedAt = Long.parseLong(photoInfo[4]);
                Long photoUpdatedAt = Long.parseLong(photoInfo[3]);
                if (photoUpdatedAt > photoSyncedAt) {
                    Logger.tag(TAG).debug(String.format(Locale.ENGLISH,
                            "getView %d: %s: Sync needed - photoUpdatedAt (%d) > photoSyncedAt (%d)", position, speciesGuessValue, photoUpdatedAt, photoSyncedAt));
                    syncNeeded = true;
                }
            }
        }

        if (!syncNeeded) {
            // See if it's an existing observation with a new photo or an updated photo

            Cursor opc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                    new String[]{
                            ObservationPhoto._ID,
                            ObservationPhoto._OBSERVATION_ID,
                            ObservationPhoto._PHOTO_ID,
                            ObservationPhoto.PHOTO_URL,
                            ObservationPhoto._UPDATED_AT,
                            ObservationPhoto._SYNCED_AT
                    },
                    "(_observation_id = ?) AND ((photo_url IS NULL AND _synced_at IS NULL) OR (_updated_at > _synced_at AND _synced_at IS NOT NULL AND id IS NOT NULL))",
                    new String[] { String.valueOf(obsId) },
                    ObservationPhoto._ID);
            if (opc != null && opc.getCount() > 0) {
                syncNeeded = true;
                Logger.tag(TAG).debug(String.format(Locale.ENGLISH,
                        "getView %d: %s: Sync needed - new/updated photos: %d", position, speciesGuessValue, opc.getCount()));
                opc.close();
            }
        }


        if (!mIsGrid) {
            if (((placeGuessValue == null) || (placeGuessValue.length() == 0)) &&
                ((privatePlaceGuessValue == null) || (privatePlaceGuessValue.length() == 0))) {
                if ((longitude != 0f) || (latitude != 0f) || (privateLatitude != 0f) || (privateLongitude != 0f)) {
                    // Show coordinates instead
                    placeGuess.setText(String.format(mContext.getString(R.string.location_coords_no_acc),
                            String.format(Locale.getDefault(), "%.4f...", latitude != 0f ? latitude : privateLatitude),
                            String.format(Locale.getDefault(), "%.4f...", longitude != 0f ? longitude : privateLongitude)));
                } else {
                    // No place at all
                    placeGuess.setText(R.string.no_location);
                }
            } else {
                placeGuess.setText((privatePlaceGuessValue != null) && (privatePlaceGuessValue.length() > 0) ?
                    privatePlaceGuessValue : placeGuessValue);
            }
        }

        
        holder.syncNeeded = syncNeeded;

        String description = c.getString(c.getColumnIndexOrThrow(Observation.DESCRIPTION));
        String preferredCommonName = c.getString(c.getColumnIndexOrThrow(Observation.PREFERRED_COMMON_NAME));

        progress.setVisibility(View.GONE);
        if (!mIsGrid) {
            placeGuess.setTextColor(Color.parseColor("#666666"));
            dateObserved.setVisibility(View.VISIBLE);
            speciesGuess.setTextColor(Color.parseColor("#000000"));
        }

        String scientificName = c.getString(c.getColumnIndexOrThrow(Observation.SCIENTIFIC_NAME));
        speciesGuess.setTypeface(null, Typeface.NORMAL);

        if (mApp.getShowScientificNameFirst() && (scientificName != null)) {
            // Show scientific name instead of common name
            Integer rankLevel = c.getInt(c.getColumnIndexOrThrow(Observation.RANK_LEVEL));
            String rank = c.getString(c.getColumnIndexOrThrow(Observation.RANK));
            JSONObject taxon = new JSONObject();
            try {
                taxon.put("name", scientificName);
                taxon.put("rank", rank);
                taxon.put("rank_level", rankLevel);

                TaxonUtils.setTaxonScientificName(speciesGuess, taxon);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }
        } else {
            if (preferredCommonName != null) {
                speciesGuess.setText(preferredCommonName);
            } else if ((speciesGuessValue != null) && (speciesGuessValue.trim().length() > 0)) {
                speciesGuess.setText("\"" + speciesGuessValue + "\"");
            } else if ((description != null) && (description.length() > 0)) {
                speciesGuess.setText(description);
            } else {
                speciesGuess.setText(R.string.unknown_species);
            }
        }

        holder.hasErrors = hasErrors;
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

        holder.isBeingSynced = (mApp.getObservationIdBeingSynced() == obsId);
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
            commentIdContainer.setVisibility(View.GONE);

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


        if (mSelectedObservations.contains(obsId)) {
            checkbox.setImageResource(R.drawable.baseline_check_circle_24);
            checkbox.setAlpha(1.0f);
            checkbox.setColorFilter(Color.parseColor("#74Ac00"));

            if (!mIsGrid) {
                view.setBackgroundColor(Color.parseColor("#DDDDDD"));
            } else {
                checkboxBackground.setVisibility(View.VISIBLE);
                int padding = (int)mHelper.dpToPx(10);
                container.setPadding(padding, padding, padding, padding);
                view.setBackgroundColor(Color.parseColor("#CCCCCC"));
                obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension - padding * 2, mDimension - padding * 2));
            }
        } else {
            checkbox.setImageResource(R.drawable.baseline_radio_button_unchecked_24);
            checkbox.setAlpha(0.5f);

            if (mIsGrid) {
                checkbox.setColorFilter(Color.parseColor("#FFFFFF"));
                checkboxBackground.setVisibility(View.GONE);
                container.setPadding(0, 0, 0, 0);
                obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
            } else {
                checkbox.setColorFilter(Color.parseColor("#000000"));
            }
        }

        holder.obsId = obsId;
        holder.updatedAt = updatedAt;
        holder.observation = new Observation(c);


        return view;
    }

    // Used to animate moving into/out a specific item from a multi-observation mode
    public void setItemSelected(View view, boolean selected) {

        View checkboxBackground = view.findViewById(R.id.checkbox_background);
        ViewGroup checkboxContainer = view.findViewById(R.id.checkbox_container);
        ImageView checkbox = view.findViewById(R.id.checkbox);
        ImageView obsImage = view.findViewById(R.id.observation_pic);
        ViewGroup container = view.findViewById(R.id.container);

        (mIsGrid ? checkboxContainer : checkbox).setVisibility(mMultiSelectionMode ? View.VISIBLE : View.GONE);

        ValueAnimator animator = null;
        int padding = (int)mHelper.dpToPx(10);


        if (selected) {
            checkbox.setImageResource(R.drawable.baseline_check_circle_24);
            checkbox.setColorFilter(Color.parseColor("#74Ac00"));
            checkbox.setAlpha(1.0f);

            if (!mIsGrid) {
                view.setBackgroundColor(Color.parseColor("#DDDDDD"));
            } else {
                checkboxBackground.setVisibility(View.VISIBLE);
                animator = ValueAnimator.ofInt(0, padding);
                view.setBackgroundColor(Color.parseColor("#CCCCCC"));
            }
        } else {
            checkbox.setImageResource(R.drawable.baseline_radio_button_unchecked_24);
            checkbox.setAlpha(0.5f);

            if (!mIsGrid) {
                view.setBackgroundColor(Color.parseColor("#FFFFFF"));
                checkbox.setColorFilter(Color.parseColor("#000000"));
            } else {
                checkbox.setColorFilter(Color.parseColor("#FFFFFF"));
                checkboxBackground.setVisibility(View.GONE);
                animator = ValueAnimator.ofInt(padding, 0);
            }
        }

        if (mIsGrid) {
            if (animator != null) {
                animator.addUpdateListener(valueAnimator -> {
                    Integer currentPadding = (Integer) valueAnimator.getAnimatedValue();
                    container.setPadding(currentPadding, currentPadding, currentPadding, currentPadding);
                    obsImage.setLayoutParams(new RelativeLayout.LayoutParams(mDimension - currentPadding * 2, mDimension - currentPadding * 2));
                });
                animator.setDuration(75);
                animator.start();
            } else {
                Logger.tag(TAG).warn("No animator in grid mode");
            }
        }


    }

    // Should the specified observation be locked for editing (e.g. it's currently being uploaded)
    public boolean isLocked(Uri uri) {
        Cursor c = mContext.managedQuery(uri, Observation.PROJECTION, null, null, null);
        Observation obs = new Observation(c);

        Integer obsId = obs._id;
        String[] photoInfo = mPhotoInfo.get(obs.uuid);
        Timestamp syncedAt = obs._synced_at;
        Timestamp updatedAt = obs._updated_at;
        boolean syncNeeded = (syncedAt == null) || (updatedAt.after(syncedAt));

        // if there's a photo and it is local
        if (!syncNeeded &&
                photoInfo != null &&
                photoInfo[2] == null &&
                photoInfo[3] != null) {
            if (photoInfo[4] == null) syncNeeded = true;
            else {
                Long photoSyncedAt = Long.parseLong(photoInfo[4]);
                Long photoUpdatedAt = Long.parseLong(photoInfo[3]);
                if (photoUpdatedAt > photoSyncedAt) syncNeeded = true;
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
            if (opc != null && opc.getCount() > 0) {
                syncNeeded = true;
                opc.close();
            }
        }

        if (mApp.getObservationIdBeingSynced() == obsId) {
            // Observation is currently being uploaded - is locked!
            return true;
        } else {
            if (!syncNeeded) {
                // Item hasn't changed (shouldn't be locked)
                return false;
            }

            // Allow editing if not in auto sync mode or when network is not available
            return mApp.getAutoSync() && isNetworkAvailable();
        }
    }

    // For caching observation thumbnails
    private HashMap<String, Bitmap> mObservationThumbnails = new HashMap<>();

    // Used for loading and processing the observation photo in the background (as to not block the UI)
    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private String mFilename = null;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mImageViewReference = new WeakReference<>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            mFilename = params[0];

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
            if (bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                    mImageViewToUrlAfterLoading.put(imageView, mFilename);
                }
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    private Map<ImageView, String> mImageViewToUrlAfterLoading = new HashMap<>();
    private Map<ImageView, String> mImageViewToUrlExpected = new HashMap<>();

    private void loadObsImage(final int position, final ImageView imageView, final String name, boolean isOnline, final boolean largeVersion) {
        boolean isLowMemory = mApp.isLowMemory();
        mImageViewToUrlExpected.put(imageView, name);

        //noinspection ConstantConditions
        if (mImageViewToUrlAfterLoading.containsKey(imageView) && mImageViewToUrlAfterLoading.get(imageView).equals(name)){
            imageView.setVisibility(View.VISIBLE);
            return;
        }

        if (name == null) {
            return;
        }

        String imageUrl = name;

        if (!isOnline) {
            File file = new File(name);
            if (!file.exists()) {
                // Local file - but it was deleted for some reason (probably user cleared cache)

                // See if the obs has a remote URL
                Cursor c = this.getCursor();
                int oldPosition = c.getPosition();
                c.moveToPosition(position);
                Observation obs = new Observation(c);
                c.moveToPosition(oldPosition);
                String[] photoInfo = mPhotoInfo.get(obs.uuid);

                if ((photoInfo == null) || (photoInfo[2] == null)) {
                    // No remote image
                    Logger.tag(TAG).debug("Local file deleted: " + position + ":" + name);
                    return;
                } else {
                    // Try and load remote image instead
                    imageUrl = photoInfo[2];
                    Logger.tag(TAG).debug("Local file deleted - using remote URL: " + position + ":" + imageUrl);
                    isOnline = true;
                }
            }
        }

        if (isOnline) {
            // Online image

            // Use the small image instead of a large image (default) - to load images quickly
            String extension = imageUrl.substring(imageUrl.lastIndexOf(".") + 1);
            final String newImageUrl = largeVersion ? imageUrl : imageUrl.substring(0, imageUrl.lastIndexOf('/') + 1) + "square." + extension;

            RequestCreator request = Picasso.with(mContext)
                    .load(newImageUrl)
                    .fit()
                    .centerCrop();

            if (largeVersion) {
                request = request.placeholder(imageView.getDrawable());
            }

            request.into(imageView, new Callback() {
                        @Override
                        public void onSuccess() {
                            if (mImageViewToUrlExpected.containsKey(imageView)) {
                                String expectedUrl = mImageViewToUrlExpected.get(imageView);
                                if ((expectedUrl == null) || (!expectedUrl.equals(name))) {
                                    // This ImageView has already been re-used for another URL (happens when scrolling fast)
                                    return;
                                }
                            }

                            imageView.setVisibility(View.VISIBLE);

                            mImageViewToUrlAfterLoading.put(imageView, newImageUrl);

                            if (!largeVersion && !isLowMemory) {
                                loadObsImage(position, imageView, name, true, true);
                            }
                        }

                        @Override
                        public void onError() {
                        }
                    });
        } else {
            // Offline image

            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            task.execute(name);
        }
    }

    private void downloadRemoteObsPhoto(int position) {
        Cursor c = this.getCursor();
        int oldPosition = c.getPosition();
        c.moveToPosition(position);
        Observation obs = new Observation(c);
        c.moveToPosition(oldPosition);

        if (obs.id == null) {
            // Observation hasn't been uploaded yet to server - nothing we can do here
            Logger.tag(TAG).debug("downloadRemoteObsPhoto - Observation hasn't been synced yet - " + obs._id);
            return;
        }

        Logger.tag(TAG).debug("downloadRemoteObsPhoto - Downloading observation JSON - " + obs.id);
        JSONObject json = getObservationJson(obs.id);

        if (json != null) {
            Observation remoteObs = new Observation(new BetterJSONObject(json));
            if (remoteObs.photos.size() > 0) {
                // Get the URL for the first photo of the obs
                Collections.sort(remoteObs.photos, (o1, o2) -> {
                    if ((o1.position == null) || (o2.position == null)) return 0;

                    return o1.position.compareTo(o2.position);
                });

                String photoUrl = remoteObs.photos.get(0).photo_url;
                Logger.tag(TAG).debug("downloadRemoteObsPhoto - Remote obs URL - " + obs.id + ":" + photoUrl);

                // Update the DB
                String[] photoInfo = mPhotoInfo.get(obs.uuid);
                if (photoInfo != null) photoInfo[2] = photoUrl;

                Cursor pc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI,
                        ObservationPhoto.PROJECTION,
                        "(id = " + remoteObs.photos.get(0).id + ")",
                        null,
                        ObservationPhoto.DEFAULT_SORT_ORDER);
                if (pc != null && pc.getCount() > 0) {
                    ObservationPhoto photo = new ObservationPhoto(pc);
                    Logger.tag(TAG).debug("downloadRemoteObsPhoto - Updating DB - " + obs.id + ":" + photo.id + ":" + photoUrl);
                    photo.photo_url = photoUrl;
                    ContentValues cv = photo.getContentValues();
                    cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                    mContext.getContentResolver().update(photo.getUri(), cv, null, null);
                    pc.close();
                }

                savePhotoInfo();
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private JSONObject getObservationJson(int id) {
        Locale deviceLocale = mContext.getResources().getConfiguration().locale;
        String deviceLanguage = deviceLocale.getLanguage();

        URL url;
        try {
            url = new URL(String.format("%s/observations/%d.json?locale=%s", INaturalistService.HOST, id, deviceLanguage));
        } catch (MalformedURLException e) {
            Logger.tag(TAG).error(e);
            return null;
        }


        JSONObject json = null;

        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            StringBuilder result = new StringBuilder();

            while ((read = in.read(buff)) != -1) {
                result.append(buff, 0, read);
            }

            json = new JSONObject(result.toString());

            conn.disconnect();
        } catch (IOException | JSONException e) {
            Logger.tag(TAG).error(e);
        }

        return json;
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
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if((firstVisibleItem + visibleItemCount >= totalItemCount - 6) && (totalItemCount > 0)) {
            // The end has been reached - load more results
            loadMoreObservations();
        }
    }

    private void loadMoreObservations() {
        if (mNoMoreObsLeft) return;
        if (mLoadingAdditionalObs) return;
        if (!mApp.loggedIn()) return;
        if (!mApp.isNetworkAvailable()) {
            (new Handler()).postDelayed(() -> Toast.makeText(mContext,
                    mContext.getResources().getString(R.string.must_be_connected_to_load_more_obs),
                    Toast.LENGTH_SHORT).show(), 100);
            return;
        }

        mLoadingAdditionalObs = true;

        if (mOnLoadingMoreResultsListener != null) mOnLoadingMoreResultsListener.onLoadingMoreResultsStart();

        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_ADDITIONAL_OBS, null, mContext, INaturalistService.class);
        ContextCompat.startForegroundService(mContext, serviceIntent);
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

    public void updateProgress(float progress) {
        if (mCurrentProgressBar != null) mCurrentProgressBar.setProgressWithAnimation(progress);
    }

    /**
     * Holds a list of Views and passes some View API calls down to each sub-View. Used for
     * ConstraintLayout (which does not have ViewGroups) so we can keep the same logic for
     * commentIdContainer in list and grid versions
     */
    private static class DelegatingConstraintViewGroup extends View {
        private final View[] mDelegateViews;

        public DelegatingConstraintViewGroup(Context context, View... views) {
            super(context);
            mDelegateViews = views;
        }

        @Override
        public void setOnClickListener(OnClickListener listener) {
            for (View v : mDelegateViews) {
                v.setOnClickListener(listener);
            }
        }

        @Override
        public void setVisibility(int visibility) {
            for (View v : mDelegateViews) {
                v.setVisibility(visibility);
            }
        }

        @Override
        public void setClickable(boolean state) {
            for (View v : mDelegateViews) {
                v.setClickable(state);
            }
        }
    }
}

