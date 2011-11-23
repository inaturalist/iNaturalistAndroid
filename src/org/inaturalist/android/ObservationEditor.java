package org.inaturalist.android;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.inaturalist.android.ObservationProvider.Observation;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
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
    private Gallery mGallery;
    private Uri mFileUri;
    private Observation mObservation;
    
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int MEDIA_TYPE_IMAGE = 1;
    
    /**
     * LIFECYCLE CALLBACKS
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "creating...");
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        setContentView(R.layout.observation_editor);
        
        // Do some setup based on the action being performed.
        Uri uri = intent.getData();
        Log.d(TAG, "intent.getData(): " + intent.getData());
        switch (ObservationProvider.sUriMatcher.match(uri)) {
        case ObservationProvider.OBSERVATION_ID_URI_CODE:
        	getIntent().setAction(Intent.ACTION_EDIT);
        	mUri = uri;
            break;
        case ObservationProvider.OBSERVATIONS_URI_CODE:
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
        
        mSpeciesGuessTextView = (TextView) findViewById(R.id.speciesGuess);
        mDescriptionTextView = (TextView) findViewById(R.id.description);
        mSaveButton = (Button) findViewById(R.id.save);
        mAddPhotoButton = (Button) findViewById(R.id.add_photo);
        mGallery = (Gallery) findViewById(R.id.gallery);
        if (savedInstanceState != null) {
        	String fileUri = savedInstanceState.getString("mFileUri");
        	if (fileUri != null) {
        		mFileUri = Uri.parse(fileUri);
        	}
        }
        
        setUiState();
        
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
			    Log.d(TAG, "generated mFileUri: " + mFileUri);

			    // start the image capture Intent
			    startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
			}
		});
    }
    
    private void setUiState() {
    	if (mCursor == null) {
    		mCursor = managedQuery(mUri, ObservationProvider.PROJECTION, null, null, null);
    	} else {
    		mCursor.requery();
    	}
        mObservation = new Observation(mCursor);
        
        if (Intent.ACTION_EDIT.equals(getIntent().getAction())) {
        	Log.d(TAG, "mSpeciesGuessTextView.getText(): " + mSpeciesGuessTextView.getText());
        	Log.d(TAG, "mObservation.speciesGuess: " + mObservation.speciesGuess);
        	mSpeciesGuessTextView.setText(mObservation.speciesGuess);
        	mDescriptionTextView.setText(mObservation.description);
        	updateImages();
        }
    }
    
    @Override
    protected void onPause() {
    	Log.d(TAG, "pausing");
    	super.onPause();
    	if (isFinishing() && isDeleteable()) {
    		delete();
    	} else {
    		save();
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
    }
    
    @Override
    protected void onResume() {
    	Log.d(TAG, "resuming");
    	super.onResume();
    	setUiState();
    }
    
    /**
     * CRUD WRAPPERS
     */
    
    private final Boolean isDeleteable() {
    	Log.d(TAG, "mCursor: " + mCursor);
    	Log.d(TAG, "mImageCursor: " + mImageCursor);
    	if (mCursor == null) { return true; }
    	if (mImageCursor != null && mImageCursor.getCount() > 0) { return false; }
    	if (mSpeciesGuessTextView.length() == 0 && mDescriptionTextView.length() == 0) {
    		return true;
    	}
    	return false;
    }
    
    private final void save() {
    	if (mCursor == null) { return; }
    	ContentValues values = new ContentValues();
    	
    	values.put(Observation.SPECIES_GUESS, mSpeciesGuessTextView.getText().toString());
    	values.put(Observation.DESCRIPTION, mDescriptionTextView.getText().toString());
    	
    	try {
    		getContentResolver().update(mUri, values, null, null);
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
    	String name = "observation_" + mObservation.createdAt.getTime() + "_" + timeStamp;
    	Log.d(TAG, "inserting title" + name);
    	values.put(android.provider.MediaStore.Images.Media.TITLE, name);
    	return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

//    /** Create a File for saving an image or video */
//    private static File getOutputMediaFile(int type){
//    	Log.d(TAG, "getOutputMediaFile, type: " + type);
//        // To be safe, you should check that the SDCard is mounted
//        // using Environment.getExternalStorageState() before doing this.
//
//        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
//                  Environment.DIRECTORY_PICTURES), "iNaturalist");
//        // This location works best if you want the created images to be shared
//        // between applications and persist after your app has been uninstalled.
//
//        // Create the storage directory if it does not exist
//        if (!mediaStorageDir.exists()){
//            if (!mediaStorageDir.mkdirs()){
//                Log.d(TAG, "failed to create directory");
//                return null;
//            }
//        }
//
//        // Create a media file name
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        File mediaFile;
//        if (type == MEDIA_TYPE_IMAGE){
//            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
//            "IMG_"+ timeStamp + ".jpg");
//        } else {
//            return null;
//        }
//
//        return mediaFile;
//    }
    
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
                Log.d(TAG + " onActivityResult", "mFileUri: " + mFileUri);
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
    	Log.d(TAG, "updateImageOrientation, uri: " + uri);
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
    			MediaStore.MediaColumns.TITLE + " LIKE 'observation_"+ mObservation.createdAt.getTime() + "_%'", 
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
