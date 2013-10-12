package org.inaturalist.android;

import com.markupartist.android.widget.ActionBar.IntentAction;
import com.markupartist.android.widget.ActionBar.AbstractAction;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

import com.markupartist.android.widget.ActionBar;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class ObservationEditor extends Activity {
    private final static String TAG = "INAT: ObservationEditor";
    private Uri mUri;
    private Cursor mCursor;
    private Cursor mImageCursor;
    private TextView mSpeciesGuessTextView;
    private TextView mDescriptionTextView;
    private Button mSaveButton;
    private TextView mObservedOnStringTextView;
    private Button mObservedOnButton;
    private Button mTimeObservedAtButton;
    private Gallery mGallery;
    private TextView mLatitudeView;
    private TextView mLongitudeView;
    private TextView mAccuracyView;
    private ProgressBar mLocationProgressView;
    private ImageButton mLocationRefreshButton;
    private ImageButton mLocationStopRefreshButton;
    private Uri mFileUri;
    private Observation mObservation;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private Location mCurrentLocation;
    private Long mLocationRequestedAt;
    private INaturalistApp app;
    private ActivityHelper mHelper;
    private boolean mCanceled = false;
    
    private ActionBar mTopActionBar;
    private ImageButton mDeleteButton;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int DATE_DIALOG_ID = 0;
    private static final int TIME_DIALOG_ID = 1;
    private static final int ONE_MINUTE = 60 * 1000;
    
    private class DeleteObservationAction extends AbstractAction {

        public DeleteObservationAction() {
            super(R.drawable.delete_observation);
        }

        @Override
        public void performAction(View view) {
            delete(false);
            Toast.makeText(ObservationEditor.this, R.string.observation_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }

    }    

  
    
    private class TakePhotoAction extends AbstractAction {

        public TakePhotoAction() {
            super(R.drawable.take_photo);
        }

        @Override
        public void performAction(View view) {
            // create Intent to take a picture and return control to the calling application
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            mFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri); // set the image file name

            // start the image capture Intent
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }

    }    

    
    private class BackAction extends AbstractAction {

        public BackAction() {
            super(R.drawable.back);
        }

        @Override
        public void performAction(View view) {
            // Get back to the observations list (consider this as canceled)
            mCanceled = true;
            Intent intent = ObservationListActivity.createIntent(ObservationEditor.this);
            startActivity(intent);
            finish();
        }

    }    

    /**
     * LIFECYCLE CALLBACKS
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        setContentView(R.layout.observation_editor);
        if (app == null) {
            app = (INaturalistApp) getApplicationContext();
        }
        if (mHelper == null) {
            mHelper = new ActivityHelper(this);
        }

        if (savedInstanceState == null) {
            // Do some setup based on the action being performed.
            Uri uri = intent.getData();
            switch (ObservationProvider.URI_MATCHER.match(uri)) {
            case Observation.OBSERVATION_ID_URI_CODE:
                getIntent().setAction(Intent.ACTION_EDIT);
                mUri = uri;
                break;
            case Observation.OBSERVATIONS_URI_CODE:
                mUri = getContentResolver().insert(uri, null);
                if (mUri == null) {
                    Log.e(TAG, "Failed to insert new observation into " + uri);
                    finish();
                    return;
                }
                setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
                getIntent().setAction(Intent.ACTION_INSERT);
                break;
            case ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE:
                mFileUri = (Uri) intent.getExtras().get("photoUri");
                if (mFileUri == null) {
                    Toast.makeText(getApplicationContext(), getString(R.string.photo_not_specified), Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                mUri = getContentResolver().insert(Observation.CONTENT_URI, null);
                if (mUri == null) {
                    Log.e(TAG, "Failed to insert new observation into " + uri);
                    finish();
                    return;
                }
                mCursor = managedQuery(mUri, Observation.PROJECTION, null, null, null);
                mObservation = new Observation(mCursor);
                updateImageOrientation(mFileUri);
                createObservationPhotoForPhoto(mFileUri);
                setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
                getIntent().setAction(Intent.ACTION_INSERT);
                mFileUri = null;
                break;
            default:
                Log.e(TAG, "Unknown action, exiting");
                finish();
                return;
            }
        } else {
            String fileUri = savedInstanceState.getString("mFileUri");
            if (fileUri != null) {mFileUri = Uri.parse(fileUri);}
            String obsUri = savedInstanceState.getString("mUri");
            if (obsUri != null) {
                mUri = Uri.parse(obsUri);
            } else {
                mUri = intent.getData();
            }

            mObservation = (Observation) savedInstanceState.getSerializable("mObservation");
        }

        mSpeciesGuessTextView = (TextView) findViewById(R.id.speciesGuess);
        mDescriptionTextView = (TextView) findViewById(R.id.description);
        mSaveButton = (Button) findViewById(R.id.save_observation);
        mObservedOnStringTextView = (TextView) findViewById(R.id.observed_on_string);
        mObservedOnButton = (Button) findViewById(R.id.observed_on);
        mTimeObservedAtButton = (Button) findViewById(R.id.time_observed_at);
        mGallery = (Gallery) findViewById(R.id.gallery);
        mLatitudeView = (TextView) findViewById(R.id.latitude);
        mLongitudeView = (TextView) findViewById(R.id.longitude);
        mAccuracyView = (TextView) findViewById(R.id.accuracy);
        mLocationProgressView = (ProgressBar) findViewById(R.id.locationProgress);
        mLocationRefreshButton = (ImageButton) findViewById(R.id.locationRefreshButton);
        mLocationStopRefreshButton = (ImageButton) findViewById(R.id.locationStopRefreshButton);
        mTopActionBar = (ActionBar) findViewById(R.id.top_actionbar);
        mDeleteButton = (ImageButton) findViewById(R.id.delete_observation);
        
        mTopActionBar.setHomeAction(new BackAction());
        mTopActionBar.addAction(new TakePhotoAction());
        

        registerForContextMenu(mGallery);

        initUi();
        
        RelativeLayout commentWrapper = (RelativeLayout) findViewById(R.id.commentCountWrapper);
        TextView commentIdCountText = (TextView) findViewById(R.id.observationCommentIdCount);
        Integer totalCount = mObservation.comments_count + mObservation.identifications_count;
        commentIdCountText.setText(totalCount.toString());

        if ((mObservation.last_comments_count == null) || (mObservation.last_comments_count != mObservation.comments_count) ||
                (mObservation.last_identifications_count == null) || (mObservation.last_identifications_count != mObservation.identifications_count)) {
            // There are unread comments/IDs
            commentWrapper.setBackgroundResource(R.drawable.id_comment_count_highlighted);
        }

        
        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                delete(false);
                Toast.makeText(ObservationEditor.this, R.string.observation_deleted, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                finish();
            }
        });


        mObservedOnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(DATE_DIALOG_ID);
            }
        });

        mTimeObservedAtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(TIME_DIALOG_ID);
            }
        });

        mLocationRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLocation();
            }
        });

        mLocationStopRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGetLocation();
            }
        });

        mGallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                Gallery g = (Gallery) parent;
                Uri uri = ((GalleryCursorAdapter) g.getAdapter()).getItemUri(position);
                Log.d(TAG, "uri: " + uri);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, MediaStore.Images.Media.CONTENT_TYPE);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "Failed to view photo: " + e);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        if (mFileUri != null) { outState.putString("mFileUri", mFileUri.toString()); }
        if (mUri != null) { outState.putString("mUri", mUri.toString()); }
        uiToObservation();
        outState.putSerializable("mObservation", mObservation);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopGetLocation();
        if (isFinishing()) {
            if (isDeleteable()) {
                delete(true);
            } else if (!mCanceled) {
                save();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initUi();
        if (app == null) {
            app = (INaturalistApp) getApplicationContext();
        }
    }

    private void initUi() {
        if (mCursor == null) {
            mCursor = managedQuery(mUri, Observation.PROJECTION, null, null, null);
        } else {
            mCursor.requery();
        }
        if (mObservation == null) {
            mObservation = new Observation(mCursor);
        }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            mObservation.observed_on = new Timestamp(System.currentTimeMillis());
            mObservation.time_observed_at = mObservation.observed_on;
            mObservation.observed_on_string = app.formatDatetime(mObservation.time_observed_at);
            if (mObservation.latitude == null && mCurrentLocation == null) {
                getLocation();
            }
        }
        updateUi();
    }

    private void updateUi() {
        observationToUi();
        updateImages();
    }

    private void uiToObservation() {
        mObservation.species_guess = mSpeciesGuessTextView.getText().toString();
        mObservation.description = mDescriptionTextView.getText().toString();
        if (mObservedOnStringTextView.getText() == null || mObservedOnStringTextView.getText().length() == 0) {
            mObservation.observed_on_string = null; 
        } else {
            mObservation.observed_on_string = mObservedOnStringTextView.getText().toString();
        }
        if (mLatitudeView.getText() == null || mLatitudeView.getText().length() == 0) {
            mObservation.latitude = null;
        } else {
            mObservation.latitude = Double.parseDouble(mLatitudeView.getText().toString());
        }
        if (mLongitudeView.getText() == null || mLongitudeView.getText().length() == 0) {
            mObservation.longitude = null;
        } else {
            mObservation.longitude = Double.parseDouble(mLongitudeView.getText().toString());
        }
        if (mAccuracyView.getText() == null || mAccuracyView.getText().length() == 0) {
            mObservation.positional_accuracy = null;
        } else {
            mObservation.positional_accuracy = ((Float) Float.parseFloat(mAccuracyView.getText().toString())).intValue();
        }
    }

    private void observationToUi() {
        mSpeciesGuessTextView.setText(mObservation.species_guess);
        mDescriptionTextView.setText(mObservation.description);
        if (mObservation.observed_on == null) {
            mObservedOnButton.setText(getString(R.string.set_date));
        } else {
            mObservedOnButton.setText(app.shortFormatDate(mObservation.observed_on));
        }
        if (mObservation.observed_on_string != null) {
            mObservedOnStringTextView.setText(mObservation.observed_on_string);
        }
        if (mObservation.time_observed_at == null) {
            mTimeObservedAtButton.setText(getString(R.string.set_time));
        } else {
            mTimeObservedAtButton.setText(app.shortFormatTime(mObservation.time_observed_at));
        }
        if (mObservation.latitude != null) {
            mLatitudeView.setText(mObservation.latitude.toString());
        }
        if (mObservation.longitude != null) {
            mLongitudeView.setText(mObservation.longitude.toString());
        }
        if (mObservation.positional_accuracy != null) {
            mAccuracyView.setText(mObservation.positional_accuracy.toString());
        }
    }

    /**
     * CRUD WRAPPERS
     */

    private final Boolean isDeleteable() {
        if (mCursor == null) { return true; }
        Cursor c = getContentResolver().query(mUri, new String[] {Observation._ID}, null, null, null);
        if (c.getCount() == 0) { return true; }
        if (mImageCursor != null && mImageCursor.getCount() > 0) { return false; }
        if (mSpeciesGuessTextView.length() == 0 
                && mDescriptionTextView.length() == 0
                && mObservedOnButton.length() == 0
                && mTimeObservedAtButton.length() == 0
                && mLatitudeView.length() == 0
                && mLongitudeView.length() == 0) {
            return true;
        }
        if (Intent.ACTION_INSERT.equals(getIntent().getAction()) && mCanceled) return true;
        return false;
    }

    private final void save() {
        if (mCursor == null) { return; }

        uiToObservation();
        if (mObservation.isDirty()) {
            try {
                ContentValues cv = mObservation.getContentValues();
                if (mObservation.latitude_changed()) {
                    cv.put(Observation.POSITIONING_METHOD, "gps");
                    cv.put(Observation.POSITIONING_DEVICE, "gps");
                }
                getContentResolver().update(mUri, cv, null, null);
            } catch (NullPointerException e) {
                Log.e(TAG, "failed to save observation:" + e);
            }
        }
        app.checkSyncNeeded();
    }

    private final void delete(boolean deleteLocal) {
        if (mCursor == null) { return; }
        
        if (deleteLocal) {
            try {
                getContentResolver().delete(mUri, null, null);
            } catch (NullPointerException e) {
                Log.e(TAG, "Failed to delete observation: " + e);
            }
        } else {
            // Only mark as deleted (so we'll later on sync the deletion)
            ContentValues cv = mObservation.getContentValues();
            cv.put(Observation.IS_DELETED, 1);
            getContentResolver().update(mUri, cv, null, null);
        }
        
        app.checkSyncNeeded();
    }

    /**
     * MENUS
     */

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem viewItem = menu.findItem(R.id.view);
        if (mObservation.id == null) {
            viewItem.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.observation_editor_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.delete:
            delete(false);
            Toast.makeText(this, R.string.observation_deleted, Toast.LENGTH_SHORT).show();
            finish();
            return true;
        case R.id.view:
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(INaturalistService.HOST + "/observations/"+mObservation.id));
            startActivity(i);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.gallery_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
        case R.id.delete:
            Log.d(TAG, "selected delete from menu");
            deletePhoto(info.position);
        default:
            return super.onContextItemSelected(item);
        }
    }

    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(int type){
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String name = "observation_" + mObservation._created_at.getTime() + "_" + timeStamp;
        values.put(android.provider.MediaStore.Images.Media.TITLE, name);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    /**
     * Date/Time Pickers
     */
    private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, int year, int month, int day) {
            Timestamp refDate;
            Timestamp date;
            try {
                refDate = new Timestamp(INaturalistApp.DATETIME_FORMAT.parse(mObservedOnStringTextView.getText().toString()).getTime());
                date = new Timestamp(year - 1900, month, day, refDate.getHours(), refDate.getMinutes(), refDate.getSeconds(), refDate.getNanos());
                if (date.getTime() > System.currentTimeMillis()) {
                    date = new Timestamp(System.currentTimeMillis());
                }
                mObservedOnStringTextView.setText(INaturalistApp.DATETIME_FORMAT.format(date));
                mObservedOnButton.setText(app.shortFormatDate(date));
            } catch (ParseException dateTimeException) {
                date = new Timestamp(year - 1900, month, day, 0, 0, 0, 0);
                if (date.getTime() > System.currentTimeMillis()) {
                    date = new Timestamp(System.currentTimeMillis());
                }
                mObservedOnStringTextView.setText(app.formatDate(date));
                mObservedOnButton.setText(app.shortFormatDate(date));
            }
        }
    };

    private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(TimePicker view, int hour, int minute) {
            Timestamp refDate;
            Date date;
            try {
                date = INaturalistApp.DATETIME_FORMAT.parse(mObservedOnStringTextView.getText().toString());
                refDate = new Timestamp(date.getTime());
            } catch (ParseException dateTimeException) {
                try {
                    date = INaturalistApp.DATE_FORMAT.parse(mObservedOnStringTextView.getText().toString());
                    refDate = new Timestamp(date.getTime());
                } catch (ParseException dateException) {
                    if (mObservation.time_observed_at != null) {
                        refDate = mObservation.time_observed_at; 
                    } else if (mObservation.observed_on != null) { 
                        refDate = mObservation.observed_on;
                    } else {
                        refDate = new Timestamp(System.currentTimeMillis());
                    }
                }
            }
            Timestamp datetime = new Timestamp(refDate.getYear(), refDate.getMonth(), refDate.getDate(), hour, minute, 0, 0);
            if (datetime.getTime() > System.currentTimeMillis()) {
                datetime = new Timestamp(System.currentTimeMillis());
            }
            mObservedOnStringTextView.setText(app.formatDatetime(datetime));
            mTimeObservedAtButton.setText(app.shortFormatTime(datetime));
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DATE_DIALOG_ID:
            Timestamp refDate;
            if (mObservation.observed_on != null) {
                refDate = mObservation.observed_on;
            } else {
                refDate = new Timestamp(Long.valueOf(System.currentTimeMillis()));
            }
            try {
                return new DatePickerDialog(this, mDateSetListener, 
                        refDate.getYear() + 1900,
                        refDate.getMonth(),
                        refDate.getDate());
            } catch (IllegalArgumentException e) {
                refDate = new Timestamp(Long.valueOf(System.currentTimeMillis()));
                return new DatePickerDialog(this, mDateSetListener, 
                        refDate.getYear() + 1900,
                        refDate.getMonth(),
                        refDate.getDate());   
            }
        case TIME_DIALOG_ID:
            if (mObservation.time_observed_at != null) {
                refDate = mObservation.time_observed_at;
            } else {
                refDate = new Timestamp(Long.valueOf(System.currentTimeMillis()));
            }
            return new TimePickerDialog(this, mTimeSetListener, 
                    refDate.getHours(),
                    refDate.getMinutes(),
                    false);
        }
        return null;
    }

    /**
     * Location
     */

    // Kicks of location service
    private void getLocation() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        }

        if (mLocationListener == null) {
            // Define a listener that responds to location updates
            mLocationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    // Called when a new location is found by the network location provider.
                    handleNewLocation(location);
                }

                public void onStatusChanged(String provider, int status, Bundle extras) {}
                public void onProviderEnabled(String provider) {}
                public void onProviderDisabled(String provider) {}
            };
        }

        // Register the listener with the Location Manager to receive location updates
        if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);   
        }
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        }

        mLocationRequestedAt = System.currentTimeMillis();
        mLocationProgressView.setVisibility(View.VISIBLE);
        mLocationRefreshButton.setVisibility(View.GONE);
        mLocationStopRefreshButton.setVisibility(View.VISIBLE);
    }

    private void handleNewLocation(Location location) {
        if (isBetterLocation(location, mCurrentLocation)) {
            setCurrentLocation(location);
        }

        if (locationIsGood(mCurrentLocation)) {
            // Log.d(TAG, "location was good, removing updates.  mCurrentLocation: " + mCurrentLocation);
            stopGetLocation();
        }

        if (locationRequestIsOld() && locationIsGoodEnough(mCurrentLocation)) {
            // Log.d(TAG, "location request was old and location was good enough, removing updates.  mCurrentLocation: " + mCurrentLocation);
            stopGetLocation();
        }
    }

    private void stopGetLocation() {
        mLocationProgressView.setVisibility(View.GONE);
        mLocationRefreshButton.setVisibility(View.VISIBLE);
        mLocationStopRefreshButton.setVisibility(View.GONE);
        if (mLocationManager != null && mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }
    }

    private void setCurrentLocation(Location location) {
        mCurrentLocation = location;
        mLatitudeView.setText(Double.toString(location.getLatitude()));
        mLongitudeView.setText(Double.toString(location.getLongitude()));
        mObservation.latitude = location.getLatitude();
        mObservation.longitude = location.getLongitude();
        if (location.hasAccuracy()) {
            mAccuracyView.setText(Float.toString(location.getAccuracy()));
            mObservation.positional_accuracy = ((Float) location.getAccuracy()).intValue();
        }
    }

    private boolean locationRequestIsOld() {
        long delta = System.currentTimeMillis() - mLocationRequestedAt;
        return delta > ONE_MINUTE;
    }

    private boolean isBetterLocation(Location newLocation, Location currentLocation) {
        if (currentLocation == null) {
            return true;
        }
        if (newLocation.hasAccuracy() && !currentLocation.hasAccuracy()) {
            return true;
        }
        if (!newLocation.hasAccuracy() && currentLocation.hasAccuracy()) {
            return false;
        }
        return newLocation.getAccuracy() < currentLocation.getAccuracy();
    }

    private boolean locationIsGood(Location location) {
        if (!locationIsGoodEnough(location)) { return false; }
        if (location.getAccuracy() <= 10) {
            return true;
        }
        return false;
    }

    private boolean locationIsGoodEnough(Location location) {
        if (location == null || !location.hasAccuracy()) { return false; }
        if (location.getAccuracy() <= 500) { return true; }
        return false;
    }

    /**
     * MISC
     */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Image captured and saved to mFileUri specified in the Intent
                Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_LONG).show();
                updateImageOrientation(mFileUri);
                createObservationPhotoForPhoto(mFileUri);
                updateImages();
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
                Toast.makeText(this,  String.format(getString(R.string.something_went_wrong), mFileUri.toString()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data.getData());
            }
            mFileUri = null; // don't let this hang around
        }
    }

    private Uri createObservationPhotoForPhoto(Uri photoUri) {
        ObservationPhoto op = new ObservationPhoto();
        Long photoId = ContentUris.parseId(photoUri);
        ContentValues cv = op.getContentValues();
        cv.put(ObservationPhoto._OBSERVATION_ID, mObservation._id);
        cv.put(ObservationPhoto.OBSERVATION_ID, mObservation.id);
        if (photoId > -1) {
            cv.put(ObservationPhoto._PHOTO_ID, photoId.intValue());
        }
        return getContentResolver().insert(ObservationPhoto.CONTENT_URI, cv);
    }
    
    private void deletePhoto(int position) {
        GalleryCursorAdapter adapter = (GalleryCursorAdapter) mGallery.getAdapter();
        Long photoId = adapter.getItemId(position);
        getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_photo_id = " + photoId, null);
        updateImages();
    }

    private void updateImageOrientation(Uri uri) {
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.Images.ImageColumns.ORIENTATION,
                MediaStore.Images.Media.DATA
        };
        Cursor c = getContentResolver().query(uri, projection, null, null, null);
        c.moveToFirst();
        String imgFilePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
        ContentValues values = new ContentValues();
        try {
            ExifInterface exif = new ExifInterface(imgFilePath);
            int degrees = exifOrientationToDegrees(
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 
                            ExifInterface.ORIENTATION_NORMAL));
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, degrees);
            getContentResolver().update(uri, values, null, null);
        } catch (IOException e) {
            Log.e(TAG, "couldn't find " + imgFilePath);
        }
    }

    protected void updateImages() {
        Cursor opCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
                ObservationPhoto.PROJECTION, 
                "_observation_id=?", 
                new String[]{mObservation._id.toString()}, 
                ObservationPhoto.DEFAULT_SORT_ORDER);
        ArrayList<Integer> photoIds = new ArrayList<Integer>();
        opCursor.moveToFirst();
        while (opCursor.isAfterLast() == false) {
            photoIds.add(opCursor.getInt(opCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID)));
            opCursor.moveToNext();
        }
        mImageCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.Images.ImageColumns.ORIENTATION},
                MediaStore.MediaColumns._ID + " IN ("+StringUtils.join(photoIds, ",")+")", null, null);
        mImageCursor.moveToFirst();
        mGallery.setAdapter(new GalleryCursorAdapter(this, mImageCursor));
    }

    public class GalleryCursorAdapter extends BaseAdapter {
        private Context mContext;
        private Cursor mCursor;
        private HashMap<Integer, ImageView> mViews;

        public GalleryCursorAdapter(Context c, Cursor cur) {
            mContext = c;
            mCursor = cur;
            mViews = new HashMap<Integer, ImageView>();
        }

        public int getCount() {
            return mCursor.getCount();
        }

        public Object getItem(int position) {
            mCursor.moveToPosition(position);
            return mCursor;
        }

        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
        }

        public Uri getItemUri(int position) {
            mCursor.moveToPosition(position);
            int imageId = mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (mViews.containsKey(position)) {
                return (ImageView) mViews.get(position);
            }
            ImageView imageView = new ImageView(mContext);
            mCursor.moveToPosition(position);
            int imageId = mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            int orientation = mCursor.getInt(mCursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION));
            Bitmap bitmapImage = MediaStore.Images.Thumbnails.getThumbnail(
                    getContentResolver(), 
                    imageId, 
                    MediaStore.Images.Thumbnails.MINI_KIND, 
                    (BitmapFactory.Options) null);
            if (orientation != 0) {
                Matrix matrix = new Matrix();
                matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
                bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
            }
            imageView.setImageBitmap(bitmapImage);
            mViews.put(position, imageView);
            return imageView;
        }
    }

    private int exifOrientationToDegrees(int orientation) {
        switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_90:
            return 90;
        case ExifInterface.ORIENTATION_ROTATE_180:
            return 180;
        case ExifInterface.ORIENTATION_ROTATE_270:
            return -90;
        default:
            return 0;
        }

    }
}
