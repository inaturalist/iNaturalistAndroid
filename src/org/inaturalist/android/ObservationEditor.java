package org.inaturalist.android;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
    private Button mAddPhotoButton;
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
    private SimpleDateFormat mDateFormat;
    private SimpleDateFormat mTimeFormat;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private Location mCurrentLocation;
    private Long mLocationRequestedAt;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int DATE_DIALOG_ID = 0;
    private static final int TIME_DIALOG_ID = 1;
    private static final int ONE_MINUTE = 60 * 1000;

    /**
     * LIFECYCLE CALLBACKS
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        setContentView(R.layout.observation_editor);

        // Do some setup based on the action being performed.
        Uri uri = intent.getData();
        switch (Observation.URI_MATCHER.match(uri)) {
        case Observation.OBSERVATION_ID_URI_CODE:
            getIntent().setAction(Intent.ACTION_EDIT);
            mUri = uri;
            break;
        case Observation.OBSERVATIONS_URI_CODE:
            mUri = getContentResolver().insert(intent.getData(), null);
            if (mUri == null) {
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                finish();
                return;
            }
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
            getIntent().setAction(Intent.ACTION_INSERT);
            break;
        default:
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        mDateFormat = new SimpleDateFormat("MMM d, yyyy");
        mTimeFormat = new SimpleDateFormat("h:mm a z");

        mSpeciesGuessTextView = (TextView) findViewById(R.id.speciesGuess);
        mDescriptionTextView = (TextView) findViewById(R.id.description);
        mSaveButton = (Button) findViewById(R.id.save);
        mAddPhotoButton = (Button) findViewById(R.id.add_photo);
        mObservedOnButton = (Button) findViewById(R.id.observed_on);
        mTimeObservedAtButton = (Button) findViewById(R.id.time_observed_at);
        mGallery = (Gallery) findViewById(R.id.gallery);
        mLatitudeView = (TextView) findViewById(R.id.latitude);
        mLongitudeView = (TextView) findViewById(R.id.longitude);
        mAccuracyView = (TextView) findViewById(R.id.accuracy);
        mLocationProgressView = (ProgressBar) findViewById(R.id.locationProgress);
        mLocationRefreshButton = (ImageButton) findViewById(R.id.locationRefreshButton);
        mLocationStopRefreshButton = (ImageButton) findViewById(R.id.locationStopRefreshButton);

        if (savedInstanceState != null) {
            String fileUri = savedInstanceState.getString("mFileUri");
            if (fileUri != null) {
                mFileUri = Uri.parse(fileUri);
            }
            mObservation = (Observation) savedInstanceState.getSerializable("mObservation");
            Log.d(TAG, "deserialized observation: " + mObservation);
        }

        initUi();

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                finish();
            }
        });

        mAddPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // create Intent to take a picture and return control to the calling application
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                mFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri); // set the image file name

                // start the image capture Intent
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
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
    }

    private void initUi() {
        if (mCursor == null) {
            mCursor = managedQuery(mUri, Observation.PROJECTION, null, null, null);
        } else {
            mCursor.requery();
        }
        if (mObservation == null) {
            mObservation = new Observation(mCursor);
            Log.d(TAG, "created new observation from cursor: " + mObservation);
        }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            mObservation.observed_on = new Timestamp(System.currentTimeMillis());
            mObservation.time_observed_at = mObservation.observed_on;
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
        // date and time already set
        mObservation.latitude = Double.parseDouble(mLatitudeView.getText().toString());
        mObservation.longitude = Double.parseDouble(mLongitudeView.getText().toString());
        mObservation.positional_accuracy = Float.parseFloat(mAccuracyView.getText().toString());
    }
    
    private void observationToUi() {
        mSpeciesGuessTextView.setText(mObservation.species_guess);
        mDescriptionTextView.setText(mObservation.description);
        if (mObservation.observed_on == null) {
            mObservedOnButton.setText("Set date");
        } else {
            mObservedOnButton.setText(mDateFormat.format(mObservation.observed_on));
        }
        if (mObservation.time_observed_at == null) {
            mTimeObservedAtButton.setText("Set time");
        } else {
            mTimeObservedAtButton.setText(mTimeFormat.format(mObservation.time_observed_at));
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

    @Override
    protected void onPause() {
        Log.d(TAG, "pausing");
        super.onPause();
        stopGetLocation();
        //        if (isFinishing() && isDeleteable()) {
        //            delete();
        //        } else {
        //            save();
        //        }
        if (isFinishing()) {
            if (isDeleteable()) {
                delete();
            } else {
                save();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState...");
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        if (mFileUri != null) {
            outState.putString("mFileUri", mFileUri.toString());
        }
        uiToObservation();
        outState.putSerializable("mObservation", mObservation);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "resuming");
        super.onResume();
        initUi();
    }

    /**
     * CRUD WRAPPERS
     */

    private final Boolean isDeleteable() {
        if (mCursor == null) { return true; }
        if (mImageCursor != null && mImageCursor.getCount() > 0) { return false; }
        if (mSpeciesGuessTextView.length() == 0 && mDescriptionTextView.length() == 0) {
            return true;
        }
        return false;
    }

    private final void save() {
        if (mCursor == null) { return; }

        uiToObservation();

        Log.d(TAG, "saving mObservation.latitude: " + mObservation.latitude);

        try {
            getContentResolver().update(mUri, mObservation.getContentValues(), null, null);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private final void delete() {
        if (mCursor == null) { return; }
        try {
            getContentResolver().delete(mUri, null, null);
        } catch (NullPointerException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * MENUS
     */

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
            delete();
            Toast.makeText(this, R.string.observation_deleted, Toast.LENGTH_SHORT).show();
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
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
    private DatePickerDialog.OnDateSetListener mDateSetListener =
            new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, int year, int month, int day) {
            Log.d(TAG, "year: " + year);
            mObservation.observed_on = new Timestamp(year - 1900, month, day, 0, 0, 0, 0);
            updateUi();
        }
    };

    private TimePickerDialog.OnTimeSetListener mTimeSetListener =
            new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(TimePicker view, int hour, int minute) {
            Timestamp refDate;
            if (mObservation.time_observed_at != null) {
                refDate = mObservation.time_observed_at; 
            } else if (mObservation.observed_on != null) { 
                refDate = mObservation.observed_on;
            } else {
                refDate = new Timestamp(System.currentTimeMillis());
            }
            mObservation.time_observed_at = new Timestamp(refDate.getYear(), 
                    refDate.getMonth(), refDate.getDate(), hour, minute, 0, 0);
            updateUi();
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

        mLocationRequestedAt = System.currentTimeMillis();
        mLocationProgressView.setVisibility(View.VISIBLE);
        mLocationRefreshButton.setVisibility(View.GONE);
        mLocationStopRefreshButton.setVisibility(View.VISIBLE);

        // Register the listener with the Location Manager to receive location updates
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    }

    private void handleNewLocation(Location location) {
        Log.d(TAG, "prv: " + location.getProvider());
        Log.d(TAG, "lat: " + location.getLatitude());
        Log.d(TAG, "lon: " + location.getLongitude());
        Log.d(TAG, "acu: " + location.getAccuracy());

        if (isBetterLocation(location, mCurrentLocation)) {
            //            mCurrentLocation = location;
            setCurrentLocation(location);
        }

        if (locationIsGood(mCurrentLocation)) {
            Log.d(TAG, "location was good, removing updates.  mCurrentLocation: " + mCurrentLocation);
            stopGetLocation();
        }

        if (locationRequestIsOld() && locationIsGoodEnough(mCurrentLocation)) {
            Log.d(TAG, "location request was old and location was good enough, removing updates.  mCurrentLocation: " + mCurrentLocation);
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
        mLongitudeView.setText(Double.toString(location.getLatitude()));
        mObservation.latitude = location.getLatitude();
        Log.d(TAG, "set mObservation.latitude to " + mObservation.latitude);
        mObservation.longitude = location.getLongitude();
        if (location.hasAccuracy()) {
            mAccuracyView.setText(Float.toString(location.getAccuracy()));
            mObservation.positional_accuracy = location.getAccuracy();
        }
    }

    private boolean locationRequestIsOld() {
        long delta = System.currentTimeMillis() - mLocationRequestedAt;
        Log.d(TAG, "locationr request running for " + delta / 1000 + " seconds");
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
        if (location.getAccuracy() <= 20) {
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
        Log.d(TAG, "requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data);
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Image captured and saved to mFileUri specified in the Intent
                Toast.makeText(this, "Image saved to (hopefully):\n" + mFileUri, Toast.LENGTH_LONG).show();
                updateImageOrientation(mFileUri);
                updateImages();
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
                Log.d(TAG, "cancelled camera");
            } else {
                // Image capture failed, advise user
                Toast.makeText(this, "Crap something went wrong:\n" +
                        mFileUri, Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data.getData());
            }
            mFileUri = null; // don't let this hang around
        }
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
            Log.d(TAG, "EXIF orientation: " + degrees);
            Log.d(TAG, "MediaStore orientation: " + c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION)));
            getContentResolver().update(uri, values, null, null);
        } catch (IOException e) {
            Log.e(TAG, "couldn't find " + imgFilePath);
        }
    }

    protected void updateImages() {
        mImageCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                new String[] {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.Images.ImageColumns.ORIENTATION}, 
                MediaStore.MediaColumns.TITLE + " LIKE 'observation_"+ mObservation._created_at.getTime() + "_%'", 
                null, 
                null);
        if (mImageCursor.getCount() > 0) {
            mImageCursor.moveToFirst();
            mGallery.setAdapter(new GalleryCursorAdapter(this, mImageCursor));
        }
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
            return position;
        }

        public long getItemId(int position) {
            return position;
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
            Log.d(TAG, "orientation for " + position + ": " + orientation);
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
