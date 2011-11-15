package org.inaturalist.android;

import org.inaturalist.android.ObservationProvider.ObservationColumns;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationEditor extends Activity {
	private final static String TAG = "ObservationEditor";
	private Uri mUri;
    private Cursor mCursor;
    private TextView mSpeciesGuessTextView;
    private TextView mDescriptionTextView;
    private Button mSaveButton;
    
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
        mCursor = managedQuery(mUri, INaturalistActivity.PROJECTION, null, null, null);
        mCursor.moveToFirst();
        
        Log.d(TAG, "mUri: " + mUri);
        
        if (Intent.ACTION_EDIT.equals(action)) {
//        	mCursor.get(ObservationColumns.SPECIES_GUESS); // this seems like much nicer syntax.  Jesus
        	String speciesGuessText = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationColumns.SPECIES_GUESS));
        	mSpeciesGuessTextView.setText(speciesGuessText);
        	String descriptionText = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationColumns.DESCRIPTION));
        	mDescriptionTextView.setText(descriptionText);
        }
        
        mSaveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				save();
				finish();
			}
		});
    }
    
    private final void save() {
    	if (mCursor == null) { return; }
    	ContentValues values = new ContentValues();
    	
    	values.put(ObservationColumns.SPECIES_GUESS, mSpeciesGuessTextView.getText().toString());
    	values.put(ObservationColumns.DESCRIPTION, mDescriptionTextView.getText().toString());
    	
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
        	// todo toast this shit!
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
