package org.inaturalist.android;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.inaturalist.android.ObservationProvider.Observation;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationEditor extends Activity {
	private final static String TAG = "ObservationEditor";
	private Uri mUri;
    private Cursor mCursor;
    private TextView mSpeciesGuessTextView;
    private TextView mDescriptionTextView;
    private Button mSaveButton;
    private Button mAddPhotoButton;
    private ImageView mPrimaryPhotoImageView;
    private Uri mFileUri;
    private Observation mObservation;
    
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int MEDIA_TYPE_IMAGE = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        setContentView(R.layout.observation_editor);
        
        // Do some setup based on the action being performed.
        final String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action)) {
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)) {
            mUri = getContentResolver().insert(intent.getData(), null);

            // If we were unable to create a new note, then just finish
            // this activity.  A RESULT_CANCELED will be sent back to the
            // original activity if they requested a result.
            if (mUri == null) {
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                finish();
                return;
            }

            // The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        } else {
            // Whoops, unknown action!  Bail.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }
        
        mSpeciesGuessTextView = (TextView) findViewById(R.id.speciesGuess);
        mDescriptionTextView = (TextView) findViewById(R.id.description);
        mSaveButton = (Button) findViewById(R.id.save);
        mAddPhotoButton = (Button) findViewById(R.id.add_photo);
        mPrimaryPhotoImageView = (ImageView) findViewById(R.id.primaryPhoto);
        mCursor = managedQuery(mUri, ObservationProvider.PROJECTION, null, null, null);
        mObservation = new Observation(mCursor);
        
        Log.d(TAG, "mUri: " + mUri);
        
        if (Intent.ACTION_EDIT.equals(action)) {
        	mSpeciesGuessTextView.setText(mObservation.speciesGuess);
        	mDescriptionTextView.setText(mObservation.description);
        	updateImages();
        }
        
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
        	getContentResolver().delete(mUri, null, null);
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
    	String name = "observation_" + mObservation.id + "_" + timeStamp;
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
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	Log.d(TAG, "requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data);
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Image captured and saved to mFileUri specified in the Intent
                Toast.makeText(this, "Image saved to (hopefully):\n" + mFileUri, Toast.LENGTH_LONG).show();
	            updateImages(mFileUri);
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
    
    protected void updateImages() {
    	String[] projection = {
    			MediaStore.MediaColumns._ID,
    			MediaStore.MediaColumns.TITLE,  
    			MediaStore.Images.ImageColumns.ORIENTATION};
    	Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
    			projection, 
    			MediaStore.MediaColumns.TITLE + " LIKE 'observation_"+ mObservation.id + "_%'", 
    			null, 
    			null);
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		updatePrimaryImage(
    			c.getInt(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
    		);
    	}
    }
    
    protected void updatePrimaryImage(int photoId) {
    	Bitmap bitmapImage = MediaStore.Images.Thumbnails.getThumbnail(
        		getContentResolver(), 
        		photoId, 
        		MediaStore.Images.Thumbnails.MINI_KIND, 
        		(BitmapFactory.Options) null);
    	mPrimaryPhotoImageView.setImageBitmap(bitmapImage);
    }
    
    protected void updateImages(Uri uri) {
    	updatePrimaryImage(Integer.parseInt(uri.getLastPathSegment()));
    }
}
