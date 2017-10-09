package org.inaturalist.android;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.flurry.android.FlurryAgent;

import java.sql.Timestamp;

public class IdentificationActivity extends AppCompatActivity {
    public static final String ID_REMARKS = "id_remarks";
    public static final String SUGGEST_ID = "suggest_id";
    protected static final int TAXON_SEARCH_REQUEST_CODE = 301;
    public static final String TAXON_ID = "taxon_id";
    public static final String SPECIES_GUESS = "species_guess";
    public static final String ICONIC_TAXON_NAME = "iconic_taxon_name";
    public static final String OBS_PHOTO_FILENAME = "obs_photo_filename";
    public static final String OBS_PHOTO_URL = "obs_photo_url";
    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String OBSERVED_ON = "observed_on";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
    public static final String OBSERVATION = "observation";


    private ActionBar mTopActionBar;
    private EditText mRemarks;
    private int mTaxonId = 0;
    private String mIconicTaxonName;
    private TextView mTaxonName;
    private TextView mIdName;
    private ImageView mIdPic;
    private boolean mSuggestId;
    private String mObsPhotoFilename;
    private String mObsPhotoUrl;
    private double mLatitude;
    private double mLongitude;
    private Timestamp mObservedOn;
    private int mObsId;
    private int mObsIdInternal;

    private INaturalistApp mApp;
    private String mObservationJson;

    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());

	}

	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}	
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    // Respond to the action bar's Up/Home button
	    case android.R.id.home:
            setResult(RESULT_CANCELED);
            finish();
	        return true;
	    }
	    return super.onOptionsItemSelected(item);
	} 


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.new_identification);

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            mSuggestId = intent.getBooleanExtra(SUGGEST_ID, false);
            mObsPhotoFilename = intent.getStringExtra(OBS_PHOTO_FILENAME);
            mObsPhotoUrl = intent.getStringExtra(OBS_PHOTO_URL);
            mLongitude = intent.getDoubleExtra(LONGITUDE, 0);
            mLatitude = intent.getDoubleExtra(LATITUDE, 0);
            mObservedOn = (Timestamp) intent.getSerializableExtra(OBSERVED_ON);
            mObsId = intent.getIntExtra(OBSERVATION_ID, 0);
            mObsIdInternal = intent.getIntExtra(OBSERVATION_ID_INTERNAL, -1);
            mObservationJson = intent.getStringExtra(OBSERVATION);
        } else {
            mSuggestId = savedInstanceState.getBoolean(SUGGEST_ID, false);
            mObsPhotoFilename = savedInstanceState.getString(OBS_PHOTO_FILENAME);
            mObsPhotoUrl = savedInstanceState.getString(OBS_PHOTO_URL);
            mLongitude = savedInstanceState.getDouble(LONGITUDE, 0);
            mLatitude = savedInstanceState.getDouble(LATITUDE, 0);
            mObservedOn = (Timestamp) savedInstanceState.getSerializable(OBSERVED_ON);
            mObsId = savedInstanceState.getInt(OBSERVATION_ID);
            mObsIdInternal = savedInstanceState.getInt(OBSERVATION_ID_INTERNAL);
            mObservationJson = savedInstanceState.getString(OBSERVATION);
        }

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mRemarks = (EditText) findViewById(R.id.remarks);
        
        
        mTopActionBar = getSupportActionBar();
        mTopActionBar.setHomeButtonEnabled(true);
        mTopActionBar.setDisplayShowCustomEnabled(true);
        mTopActionBar.setDisplayHomeAsUpEnabled(true);
        mTopActionBar.setCustomView(R.layout.add_id_top_action_bar);
        mTopActionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar_background));
        mTopActionBar.setIcon(android.R.color.transparent);
        mTopActionBar.setLogo(R.drawable.up_icon);
        
        mTaxonName = (TextView) findViewById(R.id.id_taxon_name);
        mIdName = (TextView) findViewById(R.id.id_name);
        mIdPic = (ImageView) findViewById(R.id.id_pic);
        
        View saveId = (View) mTopActionBar.getCustomView().findViewById(R.id.save_id);
        saveId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putInt(TAXON_ID, mTaxonId);
                bundle.putString(ID_REMARKS, mRemarks.getText().toString());
                bundle.putString(SPECIES_GUESS, mIdName.getText().toString());
                bundle.putString(ICONIC_TAXON_NAME, mIconicTaxonName);
                intent.putExtras(bundle);

                setResult(RESULT_OK, intent);
                finish();
            }
        });
        
        
        View changeId = (View) findViewById(R.id.id_change);
        changeId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                suggestId();
            }
        });
        
        // When loaded for the first time - show the taxon search dialog
        suggestId();

    }

    private void suggestId() {
        if ((!mApp.getSuggestSpecies()) || ((mObsPhotoFilename == null) && (mObsPhotoUrl == null))) {
            Intent intent = new Intent(IdentificationActivity.this, TaxonSearchActivity.class);
            intent.putExtra(SUGGEST_ID, true);
            startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
        } else {
            Intent intent = new Intent(IdentificationActivity.this, TaxonSuggestionsActivity.class);
            intent.putExtra(TaxonSuggestionsActivity.OBS_PHOTO_FILENAME, mObsPhotoFilename);
            intent.putExtra(TaxonSuggestionsActivity.OBS_PHOTO_URL, mObsPhotoUrl);
            intent.putExtra(TaxonSuggestionsActivity.LONGITUDE, mLongitude);
            intent.putExtra(TaxonSuggestionsActivity.LATITUDE, mLatitude);
            intent.putExtra(TaxonSuggestionsActivity.OBSERVED_ON, mObservedOn);
            intent.putExtra(TaxonSuggestionsActivity.OBSERVATION_ID, mObsId);
            if (mObsIdInternal == -1) {
                intent.putExtra(TaxonSuggestionsActivity.OBSERVATION, mObservationJson);
            } else {
                intent.putExtra(TaxonSuggestionsActivity.OBSERVATION_ID_INTERNAL, mObsIdInternal);
            }
            startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                mTaxonId = data.getIntExtra(TaxonSearchActivity.TAXON_ID, 0);
                mIconicTaxonName = data.getStringExtra(TaxonSearchActivity.ICONIC_TAXON_NAME);
                mTaxonName.setText(data.getStringExtra(TaxonSearchActivity.TAXON_NAME));
                mTaxonName.setTypeface(null, Typeface.ITALIC);
                mIdName.setText(data.getStringExtra(TaxonSearchActivity.ID_NAME));
                UrlImageViewHelper.setUrlDrawable(mIdPic, data.getStringExtra(TaxonSearchActivity.ID_PIC_URL));
            } else {
                if (mTaxonId == 0) {
                    // User never selected a taxon (even once) - close this window as well
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(SUGGEST_ID, mSuggestId);
        outState.putString(OBS_PHOTO_FILENAME, mObsPhotoFilename);
        outState.putString(OBS_PHOTO_URL, mObsPhotoUrl);
        outState.putDouble(LONGITUDE, mLongitude);
        outState.putDouble(LATITUDE, mLatitude);
        outState.putSerializable(OBSERVED_ON, mObservedOn);
        outState.putInt(OBSERVATION_ID, mObsId);
        outState.putInt(OBSERVATION_ID_INTERNAL, mObsIdInternal);
        outState.putString(OBSERVATION, mObservationJson);

        super.onSaveInstanceState(outState);
    }

        
}

