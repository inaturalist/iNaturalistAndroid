package org.inaturalist.android;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.cocosw.bottomsheet.BottomSheet;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jraf.android.backport.switchwidget.Switch;
import org.json.JSONArray;
import org.json.JSONException;
import org.lucasr.twowayview.TwoWayView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import com.ptashek.widgets.datetimepicker.DateTimePicker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class ObservationEditor extends SherlockFragmentActivity {
    private final static String TAG = "INAT: ObservationEditor";
    public final static String TAKE_PHOTO = "take_photo";
    public final static String CHOOSE_PHOTO = "choose_photo";
    public static final int RESULT_DELETED = 0x1000;
    private Uri mUri;
    private Cursor mCursor;
    private Cursor mImageCursor;
    private TextView mSpeciesGuessTextView;
    private TextView mDescriptionTextView;
    private TextView mSaveButton;
    private TextView mObservedOnStringTextView;
    private TextView mObservedOnButton;
    private TextView mTimeObservedAtButton;
    private TwoWayView mGallery;
    private TextView mLatitudeView;
    private TextView mLongitudeView;
    private TextView mAccuracyView;
    private ProgressBar mLocationProgressView;
    private View mLocationRefreshButton;
    private ImageButton mLocationStopRefreshButton;
    private View mProjectSelector;
    private Uri mFileUri;
    private Observation mObservation;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private Location mCurrentLocation;
    private Long mLocationRequestedAt;
    private INaturalistApp app;
    private ActivityHelper mHelper;
    private boolean mCanceled = false;
    private boolean mIsCaptive = false;
    
    private ActionBar mTopActionBar;
    private ImageButton mDeleteButton;
    private ImageButton mViewOnInat;
    private TextView mObservationCommentsIds;
    private TableLayout mProjectFieldsTable;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int COMMENTS_IDS_REQUEST_CODE = 101;
    private static final int PROJECT_SELECTOR_REQUEST_CODE = 102;
    private static final int LOCATION_CHOOSER_REQUEST_CODE = 103;
    private static final int OBSERVATION_PHOTOS_REQUEST_CODE = 104;
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int DATE_DIALOG_ID = 0;
    private static final int TIME_DIALOG_ID = 1;
    private static final int ONE_MINUTE = 60 * 1000;
    
    private static final int TAXON_SEARCH_REQUEST_CODE = 302;
    public static final String SPECIES_GUESS = "species_guess";
	public static final String OBSERVATION_PROJECT = "observation_project";
    
    private List<ProjectFieldViewer> mProjectFieldViewers;
    private CompoundButton mIdPlease;
    private Spinner mGeoprivacy;
    private String mSpeciesGuess;
    private TableLayout mProjectsTable;
    private ProjectReceiver mProjectReceiver;
        
    
    private ArrayList<BetterJSONObject> mProjects = null;
    
	private boolean mProjectFieldsUpdated = false;
	private boolean mDeleted = false;
	private ImageView mTaxonSelector;
    private boolean mIsConfirmation;
    private boolean mPictureTaken;
    private ImageView mSpeciesGuessIcon;
    private String mPreviousTaxonSearch = "";
    private String mTaxonPicUrl;
    private boolean mIsTaxonUnknown;
    private boolean mIsCustomTaxon;
    private TextView mProjectCount;
    private Long mFirstPositionPhotoId;
    private boolean mGettingLocation;
    private ImageView mLocationIcon;
    private TextView mLocationGuess;
    private TextView mFindingCurrentLocation;

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
        // http://stackoverflow.com/questions/20776925/failed-binder-transaction-after-starting-an-activity#20803653
        // Remove what could be a giant array for users with a lot of projects. If you don't,
        // activities launched from this view (e.g. TaxonSearchActivity) are likely to experience a
        // lag before interactivity due to a FAILED BINDER TRANSACTION
        if (mProjects != null) {
            mProjects.removeAll(mProjects);
        }
        super.onStop();
        FlurryAgent.onEndSession(this);
	}

    private class ProjectReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mProjects = new ArrayList<BetterJSONObject>();
        	SerializableJSONArray serializableArray = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.PROJECTS_RESULT);
            JSONArray projectList = new JSONArray();
        	
        	if (serializableArray != null) {
        		projectList = serializableArray.getJSONArray();
        	}

            for (int i = 0; i < projectList.length(); i++) {
                try {
                    mProjects.add(new BetterJSONObject(projectList.getJSONObject(i)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            Collections.sort(mProjects, new Comparator<BetterJSONObject>() {
                @Override
                public int compare(BetterJSONObject lhs, BetterJSONObject rhs) {
                    return lhs.getString("title").compareTo(rhs.getString("title"));
                }
            });

            refreshProjectList();
        }
    }

    private void refreshProjectList() {
        mProjectCount.setText(String.valueOf(mProjectIds.size()));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();

        if (mIsConfirmation) {
            inflater.inflate(R.menu.observation_confirmation_menu, menu);
        } else {
            inflater.inflate(R.menu.observation_editor_menu, menu);
        }

        return true;
    }

    /**
     * LIFECYCLE CALLBACKS
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();

        if ((savedInstanceState == null) && (intent != null) && (intent.getData() != null)) {
            int uriMatch = ObservationProvider.URI_MATCHER.match(intent.getData());
            if ((uriMatch == Observation.OBSERVATIONS_URI_CODE) || (uriMatch == ObservationPhoto.OBSERVATION_PHOTOS_URI_CODE)) {
                // Show the confirmation screen
                mIsConfirmation = true;
            } else {
                mIsConfirmation = false;
            }
        } else {
            // Show the observation editor screen
            mIsConfirmation = savedInstanceState.getBoolean("mIsConfirmation", false);
        }

        setContentView(R.layout.observation_confirmation);

        if (mIsConfirmation) {
            setTitle(R.string.details);
        } else {
            setTitle(R.string.edit_observation);
        }

        if (app == null) {
            app = (INaturalistApp) getApplicationContext();
        }
        if (mHelper == null) {
            mHelper = new ActivityHelper(this);
        }


        if (savedInstanceState == null) {
            // Do some setup based on the action being performed.
            Uri uri = intent.getData();
            if (uri == null) {
                Log.e(TAG, "Null URI from intent.getData");
                finish();
                return;
            }
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
                mFileUri = getPath(this, mFileUri);
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
            mProjects = (ArrayList<BetterJSONObject>) savedInstanceState.getSerializable("mProjects");
            mProjectIds = savedInstanceState.getIntegerArrayList("mProjectIds");
            mProjectFieldValues = (HashMap<Integer, ProjectFieldValue>) savedInstanceState.getSerializable("mProjectFieldValues");
            mProjectFieldsUpdated = savedInstanceState.getBoolean("mProjectFieldsUpdated");
            mPictureTaken = savedInstanceState.getBoolean("mPictureTaken", false);
            mPreviousTaxonSearch = savedInstanceState.getString("mPreviousTaxonSearch");
            mTaxonPicUrl = savedInstanceState.getString("mTaxonPicUrl");
            mIsCaptive = savedInstanceState.getBoolean("mIsCaptive", false);
            mFirstPositionPhotoId = savedInstanceState.getLong("mFirstPositionPhotoId");
            mGettingLocation = savedInstanceState.getBoolean("mGettingLocation");
        }


        mTaxonSelector = (ImageView) findViewById(R.id.taxonSelector);

        findViewById(R.id.locationVisibility).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.selection(getString(R.string.location_visibility), getResources().getStringArray(R.array.geoprivacy_items), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGeoprivacy.setSelection(which);
                        updateObservationVisibilityDescription();
                    }
                });
            }
        });


        findViewById(R.id.is_captive_checkbox).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mIsCaptive = !mIsCaptive;
                if (mIsCaptive) {
                    findViewById(R.id.is_captive_on_icon).setVisibility(View.VISIBLE);
                    findViewById(R.id.is_captive_off_icon).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.is_captive_on_icon).setVisibility(View.GONE);
                    findViewById(R.id.is_captive_off_icon).setVisibility(View.VISIBLE);
                }
            }
            });

        mIdPlease = (CompoundButton) findViewById(R.id.id_please);
        mGeoprivacy = (Spinner) findViewById(R.id.geoprivacy);
        mSpeciesGuessTextView = (TextView) findViewById(R.id.speciesGuess);
        mSpeciesGuessIcon = (ImageView) findViewById(R.id.species_guess_icon);
        mDescriptionTextView = (TextView) findViewById(R.id.description);
        mSaveButton = (TextView) findViewById(R.id.save_observation);
        mObservedOnButton = (TextView) findViewById(R.id.observed_on);
        mObservedOnStringTextView = (TextView) findViewById(R.id.observed_on_string);
        mTimeObservedAtButton = (TextView) findViewById(R.id.time_observed_at);
        mGallery = (TwoWayView) findViewById(R.id.gallery);
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics());
        mGallery.setItemMargin((int)px);
        mLatitudeView = (TextView) findViewById(R.id.latitude);
        mLongitudeView = (TextView) findViewById(R.id.longitude);
        mAccuracyView = (TextView) findViewById(R.id.accuracy);
        mLocationProgressView = (ProgressBar) findViewById(R.id.locationProgress);
        mLocationRefreshButton = (View) findViewById(R.id.locationRefreshButton);
        mLocationStopRefreshButton = (ImageButton) findViewById(R.id.locationStopRefreshButton);
        mTopActionBar = getSupportActionBar();
        mDeleteButton = (ImageButton) findViewById(R.id.delete_observation);
        mViewOnInat = (ImageButton) findViewById(R.id.view_on_inat);
        mObservationCommentsIds = (TextView) findViewById(R.id.commentIdCount);
        mProjectSelector = findViewById(R.id.select_projects);
        mProjectCount = (TextView) findViewById(R.id.project_count);
        mProjectFieldsTable = (TableLayout) findViewById(R.id.project_fields);
        mProjectsTable = (TableLayout) findViewById(R.id.projects);
        mLocationIcon = (ImageView) findViewById(R.id.location_icon);
        mLocationGuess = (TextView) findViewById(R.id.location_guess);
        mFindingCurrentLocation = (TextView) findViewById(R.id.finding_current_location);

        mProjectSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ObservationEditor.this, ProjectSelectorActivity.class);
                intent.putExtra(INaturalistService.OBSERVATION_ID, (mObservation.id == null ? mObservation._id : mObservation.id));
                intent.putExtra(ProjectSelectorActivity.IS_CONFIRMATION, true);
                intent.putExtra(ProjectSelectorActivity.PROJECT_FIELDS, mProjectFieldValues);
                intent.putIntegerArrayListExtra(INaturalistService.PROJECT_ID, mProjectIds);
                startActivityForResult(intent, PROJECT_SELECTOR_REQUEST_CODE);
            }
        });

        findViewById(R.id.coordinates).setVisibility(View.GONE);
        if (mTaxonPicUrl != null) {
            UrlImageViewHelper.setUrlDrawable(mSpeciesGuessIcon, mTaxonPicUrl, R.drawable.ic_species_guess_black_24dp, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                        mSpeciesGuessIcon.setAlpha(1.0f);
                    }
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    return loadedBitmap;
                }
            });
        }

        mSpeciesGuessTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ObservationEditor.this, TaxonSearchActivity.class);
                intent.putExtra(TaxonSearchActivity.SPECIES_GUESS, mSpeciesGuessTextView.getText().toString());
                intent.putExtra(TaxonSearchActivity.SHOW_UNKNOWN, true);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        });

        mTopActionBar.setHomeButtonEnabled(true);
        mTopActionBar.setDisplayHomeAsUpEnabled(true);
        View takePhoto;

        mTopActionBar.setLogo(R.drawable.ic_arrow_back);
        mTopActionBar.setDisplayHomeAsUpEnabled(false);
        mTopActionBar.setTitle(getString(R.string.details));
        takePhoto = findViewById(R.id.take_photo);


        takePhoto.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
                mFileUri = getPath(ObservationEditor.this, mFileUri);
                openImageIntent(ObservationEditor.this, mFileUri, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
            }
        });

        mSpeciesGuess = intent.getStringExtra(SPECIES_GUESS);

        initUi();

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
                AlertDialog.Builder builder = new AlertDialog.Builder(ObservationEditor.this);
                // Set the adapter
                String[] items = {
                        getResources().getString(R.string.get_current_location),
                        getResources().getString(R.string.edit_location)
                };
                builder.setAdapter(
                        new ArrayAdapter<String>(ObservationEditor.this,
                                android.R.layout.simple_list_item_1, items), null);

                final AlertDialog alertDialog = builder.create();

                ListView listView = alertDialog.getListView();
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        alertDialog.dismiss();

                        if (position == 0) {
                            // Get current location
                            getLocation();
                        } else {
                            // Edit location
                            Intent intent = new Intent(ObservationEditor.this, LocationChooserActivity.class);
                            intent.putExtra(LocationChooserActivity.LONGITUDE, mObservation.private_longitude != null ? mObservation.private_longitude : mObservation.longitude);
                            intent.putExtra(LocationChooserActivity.LATITUDE,  mObservation.private_latitude != null ? mObservation.private_latitude : mObservation.latitude);
                            intent.putExtra(LocationChooserActivity.ACCURACY, (mObservation.positional_accuracy != null ? mObservation.positional_accuracy.doubleValue() : 0));
                            intent.putExtra(LocationChooserActivity.ICONIC_TAXON_NAME, mObservation.iconic_taxon_name);

                            startActivityForResult(intent, LOCATION_CHOOSER_REQUEST_CODE);
                        }
                    }
                });

                alertDialog.show();
            }
        });

        if (getCurrentFocus() != null) {
            // Hide keyboard
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        
        
        if (mProjectIds == null) {
        	if ((intent != null) && (intent.hasExtra(OBSERVATION_PROJECT))) {
        		Integer projectId = intent.getIntExtra(OBSERVATION_PROJECT, 0);
        		mProjectIds = new ArrayList<Integer>();
        		mProjectIds.add(projectId);

        	} else {
        		// Get IDs of project-observations
        		int obsId = (mObservation.id == null ? mObservation._id : mObservation.id);
        		Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI, ProjectObservation.PROJECTION,
        				"(observation_id = " + obsId + ") AND ((is_deleted = 0) OR (is_deleted is NULL))",
        				null, ProjectObservation.DEFAULT_SORT_ORDER);
        		c.moveToFirst();
        		mProjectIds = new ArrayList<Integer>();
        		while (c.isAfterLast() == false) {
        			ProjectObservation projectObservation = new ProjectObservation(c);
        			mProjectIds.add(projectObservation.project_id);
        			c.moveToNext();
        		}
        		c.close();
        	}
        }

        refreshProjectFields();

        mProjectReceiver = new ProjectReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_JOINED_PROJECTS_RESULT);
        registerReceiver(mProjectReceiver, filter);  
        
        if (mProjects == null) {
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_JOINED_PROJECTS, null, this, INaturalistService.class);
            startService(serviceIntent);  
        } else {
            refreshProjectList();
        }


        if ((intent != null) && (!mPictureTaken)) {
            if (intent.getBooleanExtra(TAKE_PHOTO, false)) {
                // Immediately take a photo
                takePhoto();

            } else if (intent.getBooleanExtra(CHOOSE_PHOTO, false)) {
                // Immediately choose an existing photo
                choosePhoto();
            }
        }

        updateObservationVisibilityDescription();
    }

    private void takePhoto() {
        mFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
        mFileUri = getPath(ObservationEditor.this, mFileUri);

        final Intent galleryIntent = new Intent();

        galleryIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        galleryIntent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);
        this.startActivityForResult(galleryIntent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);

        // In case a new/existing photo was taken - make sure we won't retake it in case the activity pauses/resumes.
        mPictureTaken = true;
    }

    private void choosePhoto() {
        mFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
        mFileUri = getPath(ObservationEditor.this, mFileUri);

        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
        this.startActivityForResult(galleryIntent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);

        // In case a new/existing photo was taken - make sure we won't retake it in case the activity pauses/resumes.
        mPictureTaken = true;
    }
    
    @Override
    public void onBackPressed() {
        onBack();
    }
    
    private boolean onBack() {
        Observation observationCopy = new Observation(mCursor);
        uiToObservation();
        if (!mObservation.isDirty()) {
            // User hasn't changed anything - no need to display confirmation dialog
            mCanceled = true;
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }

        // Restore the old observation (since uiToObservation has overwritten it)
        mObservation = observationCopy;

        // Display a confirmation dialog
        confirm(ObservationEditor.this, R.string.edit_observation, R.string.discard_changes,
                R.string.yes, R.string.no,
                new Runnable() {
                    public void run() {
                        // Get back to the observations list (consider this as canceled)
                        mCanceled = true;
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                },
                null);

        return false;
    }

    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return onBack();
            case R.id.save_observation:
                uiToProjectFieldValues();
                if (save()) {
                    setResult(RESULT_OK);
                    finish();
                }
                return true;
            case R.id.delete_observation:
                // Display a confirmation dialog
                confirm(ObservationEditor.this, R.string.delete_observation, R.string.delete_confirmation,
                        R.string.yes, R.string.no,
                        new Runnable() {
                            public void run() {
                                delete((mObservation == null) || (mObservation.id == null));
                                Toast.makeText(ObservationEditor.this, R.string.observation_deleted, Toast.LENGTH_SHORT).show();
                                if (app.getAutoSync() && !app.getIsSyncing()) {
                                    // Trigger a sync
                                    Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationEditor.this, INaturalistService.class);
                                    startService(serviceIntent);
                                }

                                setResult(RESULT_DELETED);
                                finish();
                            }
                        },
                        null);
                return true;
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        if (mFileUri != null) { outState.putString("mFileUri", mFileUri.toString()); }
        if (mUri != null) { outState.putString("mUri", mUri.toString()); }
        uiToObservation();
        outState.putSerializable("mObservation", mObservation);
        outState.putSerializable("mProjects", mProjects);
        outState.putIntegerArrayList("mProjectIds", mProjectIds);
        uiToProjectFieldValues();
        outState.putSerializable("mProjectFieldValues", mProjectFieldValues);
        outState.putBoolean("mProjectFieldsUpdated", mProjectFieldsUpdated);
        outState.putBoolean("mIsConfirmation", mIsConfirmation);
        outState.putBoolean("mPictureTaken", mPictureTaken);
        outState.putString("mPreviousTaxonSearch", mPreviousTaxonSearch);
        outState.putString("mTaxonPicUrl", mTaxonPicUrl);
        outState.putBoolean("mIsCaptive", mIsCaptive);
        outState.putLong("mFirstPositionPhotoId", mFirstPositionPhotoId != null ? mFirstPositionPhotoId : -1);
        outState.putBoolean("mGettingLocation", mGettingLocation);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        if (mProjectReceiver != null) {
            try {
                unregisterReceiver(mProjectReceiver);  
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }
        
        stopGetLocation();
        uiToProjectFieldValues();
        if (isFinishing()) {
        	
        	if (!mDeleted) {
        		if (isDeleteable()) {
        			delete(true);
        		} else if (!mCanceled) {
        			save();
        		}
        	}
        }
    }
    
    private void uiToProjectFieldValues() {
        int obsId = (mObservation.id == null ? mObservation._id : mObservation.id);

        for (int fieldId : mProjectFieldValues.keySet()) {
            ProjectFieldValue fieldValue = mProjectFieldValues.get(fieldId);
            fieldValue.observation_id = obsId;
            mProjectFieldsUpdated = true;
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
        
        if ((mSpeciesGuess != null) && (mObservation.species_guess == null)) {
            mObservation.species_guess = mSpeciesGuess;
        }


        mLocationProgressView.setVisibility(View.GONE);
        mFindingCurrentLocation.setVisibility(View.GONE);
        mLocationRefreshButton.setVisibility(View.VISIBLE);
        mLocationIcon.setVisibility(View.VISIBLE);

        if (mGettingLocation) {
            getLocation();
        }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            if (mObservation.observed_on == null) {
                mObservation.observed_on = mObservation.observed_on_was = new Timestamp(System.currentTimeMillis());
                mObservation.time_observed_at = mObservation.time_observed_at_was = mObservation.observed_on;
                mObservation.observed_on_string = mObservation.observed_on_string_was = app.formatDatetime(mObservation.time_observed_at);
                if (mObservation.latitude == null && mCurrentLocation == null) {
                    getLocation();
                }
            }
        }
        
        if ((mObservation != null) && (mObservation.id != null)) {
            // Display the errors for the observation, if any
            JSONArray errors = app.getErrorsForObservation(mObservation.id);
            TextView errorsDescription = (TextView) findViewById(R.id.errors);

            if (errors.length() == 0) {
                errorsDescription.setVisibility(View.GONE);
            } else {
                errorsDescription.setVisibility(View.VISIBLE);
                StringBuilder errorsHtml = new StringBuilder();
                try {
                    for (int i = 0; i < errors.length(); i++) {
                        errorsHtml.append("&#8226; ");
                        errorsHtml.append(errors.getString(i));
                        if (i < errors.length() - 1)
                            errorsHtml.append("<br/>");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                errorsDescription.setText(Html.fromHtml(errorsHtml.toString()));
            }
        }


        updateUi();
    }

    private void updateUi() {
        observationToUi();

        updateImages();
    }

    private void uiToObservation() {
        if (((mObservation.species_guess == null) && (mSpeciesGuessTextView.getText().length() > 0) && (!mIsTaxonUnknown)) || (mObservation.species_guess != null)) mObservation.species_guess = mSpeciesGuessTextView.getText().toString();
        if (((mObservation.description == null) && (mDescriptionTextView.getText().length() > 0)) || (mObservation.description != null)) mObservation.description = mDescriptionTextView.getText().toString();
        if (mObservedOnStringTextView.getText() == null || mObservedOnStringTextView.getText().length() == 0) {
            mObservation.observed_on_string = null; 
        } else {
            mObservation.observed_on_string = mObservedOnStringTextView.getText().toString();
            mObservation.observed_on = mDateSetByUser;
            mObservation.time_observed_at = mTimeSetByUser;
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

        if (mObservation.uuid == null) {
            mObservation.uuid = UUID.randomUUID().toString();
        }

        mObservation.id_please = mIdPlease.isChecked();

        List<String> values = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_values));
        String selectedValue = values.get(mGeoprivacy.getSelectedItemPosition());
        if ((mObservation.geoprivacy != null) || (mGeoprivacy.getSelectedItemPosition() != 0)) {
            mObservation.geoprivacy = selectedValue;

            if (selectedValue.equals("private") || selectedValue.equals("obscured")) {
                if ((mObservation.longitude != null) && (mObservation.latitude != null)) {
                    mObservation.private_longitude = mObservation.longitude;
                    mObservation.private_latitude = mObservation.latitude;
                }
            }
        }

        if ((mObservation.captive != null) || ((mObservation.captive == null) && (mIsCaptive))) {
            mObservation.captive = mIsCaptive;
        }
    }

    private void updateObservationVisibilityDescription() {
        List<String> names = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_items));
        String selectedName = names.get(mGeoprivacy.getSelectedItemPosition());
        ((TextView)findViewById(R.id.location_visibility_description)).setText(getString(R.string.location_is) + " " + selectedName);
    }

    private void observationToUi() {
        List<String> values = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_values));

        if (mObservation.geoprivacy != null) {
            mGeoprivacy.setSelection(values.indexOf(mObservation.geoprivacy));
        } else {
            mGeoprivacy.setSelection(0);
        }
        updateObservationVisibilityDescription();

        mIdPlease.setChecked(mObservation.id_please);

        mSpeciesGuessTextView.setText(mIsTaxonUnknown ? "Unknown" : mObservation.species_guess);
        mDescriptionTextView.setText(mObservation.description);
        if (mObservation.observed_on == null) {
            mObservedOnButton.setText(getString(R.string.set_date));
        } else {
            mObservedOnButton.setText(app.shortFormatDate(mObservation.observed_on));
        }
        if (mObservation.observed_on_string != null) {
            mObservedOnStringTextView.setText(mObservation.observed_on_string);
            mDateSetByUser = mObservation.observed_on;
        }
        if (mObservation.time_observed_at == null) {
            mTimeObservedAtButton.setText(getString(R.string.set_time));
        } else {
            mTimeObservedAtButton.setText(app.shortFormatTime(mObservation.time_observed_at));
            mTimeSetByUser = mObservation.time_observed_at;
        }

        if (mObservation.latitude != null) {
            mLatitudeView.setText(mObservation.latitude.toString());
            findViewById(R.id.coordinates).setVisibility(View.VISIBLE);
        } else {
            mLatitudeView.setText("");
        }
        if (mObservation.longitude != null) {
            mLongitudeView.setText(mObservation.longitude.toString());
        } else {
            mLongitudeView.setText("");
        }
        if (mObservation.positional_accuracy == null) {
            mAccuracyView.setText("");

            findViewById(R.id.accuracy_prefix).setVisibility(View.GONE);
            findViewById(R.id.accuracy).setVisibility(View.GONE);
        } else {
            mAccuracyView.setText(mObservation.positional_accuracy.toString());
            findViewById(R.id.accuracy_prefix).setVisibility(View.VISIBLE);
            findViewById(R.id.accuracy).setVisibility(View.VISIBLE);
        }

        mIsCaptive = mObservation.captive;
        if (mIsCaptive) {
            findViewById(R.id.is_captive_on_icon).setVisibility(View.VISIBLE);
            findViewById(R.id.is_captive_off_icon).setVisibility(View.GONE);
        } else {
            findViewById(R.id.is_captive_on_icon).setVisibility(View.GONE);
            findViewById(R.id.is_captive_off_icon).setVisibility(View.VISIBLE);
        }

        if (mObservation.place_guess != null) {
            mLocationGuess.setText(mObservation.place_guess);
        } else {
            mLocationGuess.setText(getString(R.string.set_location));
        }
    }

    /**
     * CRUD WRAPPERS
     */

    private final Boolean isDeleteable() {
        if (mCursor == null) { return true; }
        Cursor c = getContentResolver().query(mUri, new String[]{Observation._ID}, null, null, null);
        if (c.getCount() == 0) { return true; }
        //if (mImageCursor != null && mImageCursor.getCount() > 0) { return false; }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction()) && mCanceled) return true;
        return false;
    }

    private final boolean save() {
        if (mCursor == null) { return true; }
        
        uiToObservation();
        
        boolean updatedProjects = saveProjects();
        saveProjectFields();
        
        if ((Intent.ACTION_INSERT.equals(getIntent().getAction())) || (mObservation.isDirty()) || (mProjectFieldsUpdated) || (updatedProjects)) {
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

        if (app.getAutoSync() && !app.getIsSyncing()) {
            // Trigger a sync
            Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, ObservationEditor.this, INaturalistService.class);
            startService(serviceIntent);
        }
        
        return true;
    }

    private final void delete(boolean deleteLocal) {
        if (mCursor == null) { return; }
        
        if (deleteLocal) {
            try {
                getContentResolver().delete(mUri, null, null);

                if (mImageCursor != null && mImageCursor.getCount() > 0) {
                    // Delete any observation photos taken with it
                    getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_observation_id=?", new String[]{mObservation._id.toString()});
                }
            } catch (NullPointerException e) {
                Log.e(TAG, "Failed to delete observation: " + e);
            }
        } else {
            // Only mark as deleted (so we'll later on sync the deletion)
            ContentValues cv = mObservation.getContentValues();
            cv.put(Observation.IS_DELETED, 1);
            getContentResolver().update(mUri, cv, null, null);
        }
        
        mDeleted = true;
        
        app.checkSyncNeeded();
    }

    /**
     * MENUS
     */

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

            mDateSetByUser = date;
        }
    };

    private Timestamp mDateSetByUser;
    private Timestamp mTimeSetByUser;

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
            mTimeSetByUser = datetime;
        }
    };
    private ArrayList<Integer> mProjectIds;
    private Hashtable<Integer, ProjectField> mProjectFields;
    private HashMap<Integer, ProjectFieldValue> mProjectFieldValues = null;

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

    // Kicks off location service
    private void getLocation() {
        if (mLocationListener != null) {
            return;
        }

        mLocationProgressView.setVisibility(View.VISIBLE);
        mFindingCurrentLocation.setVisibility(View.VISIBLE);
        mLocationRefreshButton.setVisibility(View.GONE);
        mLocationIcon.setVisibility(View.GONE);

        mGettingLocation = true;

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

        mLocationProgressView.setVisibility(View.GONE);
        mFindingCurrentLocation.setVisibility(View.GONE);
        mLocationRefreshButton.setVisibility(View.VISIBLE);
        mLocationIcon.setVisibility(View.VISIBLE);
    }

    private void stopGetLocation() {
        if (mLocationManager != null && mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }

        mLocationListener = null;
        mGettingLocation = false;
    }


    private void guessLocation() {
        if ((mObservation.latitude == null) || (mObservation.longitude == null)) {
            return;
        }

        (new Thread(new Runnable() {
            @Override
            public void run() {

                Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(mObservation.latitude, mObservation.longitude, 1);
                    if((null != addresses) && (addresses.size() > 0)) {
                        Address address = addresses.get(0);
                        final StringBuilder location = new StringBuilder();
                        for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                            location.append(address.getAddressLine(i));
                            location.append(" ");
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setPlaceGuess(location.toString());
                            }
                        });

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        })).start();
    }

    private void setPlaceGuess(String placeGuess) {
        if (placeGuess != null) {
            placeGuess = placeGuess.trim();   
        }
        if ((placeGuess != null) && (placeGuess.length() > 0)) {
            mLocationGuess.setText(placeGuess);
            mObservation.place_guess = placeGuess;
        } else {
            mLocationGuess.setText(R.string.set_location);
            mObservation.place_guess = null;
        }
    }

    private void setCurrentLocation(Location location) {
        mCurrentLocation = location;

        mObservation.latitude = location.getLatitude();
        mObservation.longitude = location.getLongitude();

        if ((mObservation.geoprivacy != null) && ((mObservation.geoprivacy.equals("private") || mObservation.geoprivacy.equals("obscured")))) {
            mObservation.private_longitude = mObservation.longitude;
            mObservation.private_latitude = mObservation.latitude;
        }

        mLatitudeView.setText(Double.toString(location.getLatitude()));
        mLongitudeView.setText(Double.toString(location.getLongitude()));

        findViewById(R.id.coordinates).setVisibility(View.VISIBLE);

        if (location.hasAccuracy()) {
            mAccuracyView.setText(Float.toString(location.getAccuracy()));
            mObservation.positional_accuracy = ((Float) location.getAccuracy()).intValue();
            findViewById(R.id.accuracy_prefix).setVisibility(View.VISIBLE);
            findViewById(R.id.accuracy).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.accuracy_prefix).setVisibility(View.GONE);
            findViewById(R.id.accuracy).setVisibility(View.GONE);
        }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            mObservation.latitude_was = mObservation.latitude;
            mObservation.longitude_was = mObservation.longitude;
            mObservation.positional_accuracy_was = mObservation.positional_accuracy;
        }

        if (isNetworkAvailable()) {
            guessLocation();
        } else {
            setPlaceGuess(null);
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
    
    private void saveProjectFields() {
        for (ProjectFieldValue fieldValue : mProjectFieldValues.values()) {
            if (fieldValue.value == null) {
                continue;
            }
            
            if (fieldValue._id == null) {
                // New field value
                ContentValues cv = fieldValue.getContentValues();
                cv.put(ProjectFieldValue._SYNCED_AT, System.currentTimeMillis() - 100);
                Uri newRow = getContentResolver().insert(ProjectFieldValue.CONTENT_URI, cv);
                getContentResolver().update(newRow, fieldValue.getContentValues(), null, null);
            } else {
                // Update field value
                getContentResolver().update(fieldValue.getUri(), fieldValue.getContentValues(), null, null);
            }
        }
    }
     
   
    private boolean saveProjects() {
    	Boolean updatedProjects = false; // Indicates whether or not *any* projects were changed
        String joinedIds = StringUtils.join(mProjectIds, ",");
        
        int obsId = (mObservation.id == null ? mObservation._id : mObservation.id);

        // First, mark for deletion any projects that are no longer associated with this observation
        Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI, ProjectObservation.PROJECTION,
                "(observation_id = " + obsId + ") AND (project_id NOT IN (" + joinedIds + "))",
                null, ProjectObservation.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        while (c.isAfterLast() == false) {
        	updatedProjects = true;
            ProjectObservation projectObservation = new ProjectObservation(c);
            projectObservation.is_deleted = true;
            getContentResolver().update(projectObservation.getUri(), projectObservation.getContentValues(), null, null);
            c.moveToNext();
        }
        c.close();


        // Next, unmark for deletion any project-observations which were re-added
        c = getContentResolver().query(ProjectObservation.CONTENT_URI, ProjectObservation.PROJECTION,
                "(observation_id = " + obsId + ") AND (project_id IN (" + joinedIds + "))",
                null, ProjectObservation.DEFAULT_SORT_ORDER);

        c.moveToFirst();

        ArrayList<Integer> existingIds = new ArrayList<Integer>();

        while (c.isAfterLast() == false) {
            ProjectObservation projectObservation = new ProjectObservation(c);
            if (projectObservation.is_deleted == true) {
            	updatedProjects = true;
            }
            projectObservation.is_deleted = false;
            existingIds.add(projectObservation.project_id);
            getContentResolver().update(projectObservation.getUri(), projectObservation.getContentValues(), null, null);
            c.moveToNext();
        }
        c.close();

        // Finally, add new project-observation records
        ArrayList<Integer> newIds = (ArrayList<Integer>) CollectionUtils.subtract(mProjectIds, existingIds);

        for (int i = 0; i < newIds.size(); i++) {
        	updatedProjects = true;
            int projectId = newIds.get(i);
            ProjectObservation projectObservation = new ProjectObservation();
            projectObservation.project_id = projectId;
            projectObservation.observation_id = obsId;
            projectObservation.is_new = true;
            projectObservation.is_deleted = false;

            getContentResolver().insert(ProjectObservation.CONTENT_URI, projectObservation.getContentValues());
        }
        
        return updatedProjects;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                ((EditText) mSpeciesGuessTextView).clearFocus();
                mDescriptionTextView.clearFocus();

                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (getCurrentFocus() != null)
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        }, 10);


        if (requestCode == OBSERVATION_PHOTOS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Integer setFirstPhotoIndex = data.getIntExtra(ObservationPhotosViewer.SET_DEFAULT_PHOTO_INDEX, -1);
                Integer deletePhotoIndex = data.getIntExtra(ObservationPhotosViewer.DELETE_PHOTO_INDEX, -1);
                if (setFirstPhotoIndex > -1) {
                    ((GalleryCursorAdapter)mGallery.getAdapter()).setAsFirstPhoto(setFirstPhotoIndex);
                } else if (deletePhotoIndex > -1) {
                    deletePhoto(deletePhotoIndex);
                }
            }
        } else if (requestCode == LOCATION_CHOOSER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
            	stopGetLocation();
            	
                double longitude = data.getDoubleExtra(LocationChooserActivity.LONGITUDE, 0);
                double latitude = data.getDoubleExtra(LocationChooserActivity.LATITUDE, 0);
                double accuracy = data.getDoubleExtra(LocationChooserActivity.ACCURACY, 0);
                mObservation.latitude = latitude;
                mObservation.longitude = longitude;
                mObservation.positional_accuracy = (int) Math.ceil(accuracy);

                if ((mObservation.geoprivacy != null) && ((mObservation.geoprivacy.equals("private") || mObservation.geoprivacy.equals("obscured")))) {
                    mObservation.private_longitude = mObservation.longitude;
                    mObservation.private_latitude = mObservation.latitude;
                }


                mLatitudeView.setText(Double.toString(latitude));
                mLongitudeView.setText(Double.toString(longitude));
                mAccuracyView.setText(mObservation.positional_accuracy.toString());
                findViewById(R.id.coordinates).setVisibility(View.VISIBLE);
                findViewById(R.id.accuracy_prefix).setVisibility(View.VISIBLE);
                findViewById(R.id.accuracy).setVisibility(View.VISIBLE);

                if (isNetworkAvailable()) {
                    guessLocation();
                } else {
                    setPlaceGuess(null);
                }

            }
         } else if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String iconicTaxonName = data.getStringExtra(TaxonSearchActivity.ICONIC_TAXON_NAME);
                String taxonName = data.getStringExtra(TaxonSearchActivity.TAXON_NAME);
                String idName = data.getStringExtra(TaxonSearchActivity.ID_NAME);
                String idPicUrl = data.getStringExtra(TaxonSearchActivity.ID_PIC_URL);
                Integer taxonId = data.getIntExtra(TaxonSearchActivity.TAXON_ID, 0);
                boolean isCustomTaxon = data.getBooleanExtra(TaxonSearchActivity.IS_CUSTOM, false);

                if (taxonId == TaxonSearchActivity.UNKNOWN_TAXON_ID) {
                    mSpeciesGuess = null;
                    mObservation.species_guess = null;
                    mObservation.taxon_id = null;
                    mSpeciesGuessTextView.setText("Unknown");
                    mPreviousTaxonSearch = "Unknown";
                    mTaxonPicUrl = null;
                    mIsTaxonUnknown = true;
                    mIsCustomTaxon = false;

                    mSpeciesGuessIcon.setImageResource(R.drawable.ic_species_guess_black_24dp);
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                        mSpeciesGuessIcon.setAlpha(0.6f);
                    }
                } else {
                    String speciesGuess = String.format("%s", idName);
                    mSpeciesGuess = speciesGuess;
                    mObservation.species_guess = speciesGuess;
                    mObservation.taxon_id = isCustomTaxon ? null : taxonId;
                    mSpeciesGuessTextView.setText(mSpeciesGuess);
                    mPreviousTaxonSearch = mSpeciesGuess;
                    mTaxonPicUrl = isCustomTaxon ? null : idPicUrl;
                    mIsTaxonUnknown = false;
                    mIsCustomTaxon = isCustomTaxon;
                    mObservation.iconic_taxon_name = isCustomTaxon ? null : iconicTaxonName;

                    ((EditText)mSpeciesGuessTextView).clearFocus();
                    mDescriptionTextView.clearFocus();

                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                    if (!mIsCustomTaxon) {
                        UrlImageViewHelper.setUrlDrawable(mSpeciesGuessIcon, mTaxonPicUrl, R.drawable.ic_species_guess_black_24dp, new UrlImageViewCallback() {
                            @Override
                            public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                                    mSpeciesGuessIcon.setAlpha(1.0f);
                                }
                                imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(loadedBitmap));
                            }

                            @Override
                            public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                                return loadedBitmap;
                            }
                        });
                    } else {
                        mSpeciesGuessIcon.setImageResource(R.drawable.iconic_taxon_unknown);
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                            mSpeciesGuessIcon.setAlpha(1.0f);
                        }
                    }
                }
            }

        } else if (requestCode == ProjectFieldViewer.PROJECT_FIELD_TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Notify the project fields that we returned from a taxon search
                for (ProjectFieldViewer viewer : mProjectFieldViewers) {
                    viewer.onTaxonSearchResult(data);
                }
            }
        } else if (requestCode == PROJECT_SELECTOR_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                ArrayList<Integer> projectIds = data.getIntegerArrayListExtra(ProjectSelectorActivity.PROJECT_IDS);
                HashMap<Integer, ProjectFieldValue> values = (HashMap<Integer, ProjectFieldValue>) data.getSerializableExtra(ProjectSelectorActivity.PROJECT_FIELDS);
                mProjectIds = projectIds;
                mProjectFieldValues = values;

                refreshProjectFields();
                refreshProjectList();

            }
        } else if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {

            int random = (new Random()).nextInt();

            if (resultCode == RESULT_OK) {
                final boolean isCamera;

                if ((data == null) || (data.getScheme() == null)) {
                    isCamera = true;
                } else {
                    final String action = data.getAction();
                    if(action == null) {
                        isCamera = false;
                    } else {
                        isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    }
                }

                Uri selectedImageUri;
                if(isCamera) {
                    selectedImageUri = mFileUri;
                } else {
                    selectedImageUri = data == null ? null : data.getData();
                    if (selectedImageUri == null) {
                        selectedImageUri = mFileUri;
                    } else {
                        selectedImageUri = getPath(this, selectedImageUri);
                    }
                }

                Log.v(TAG, String.format("%s: %s", isCamera, selectedImageUri));

                if (isCamera) {
                    // Image captured and saved to mFileUri specified in the Intent
                    Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_LONG).show();
                }
                
                updateImageOrientation(selectedImageUri);
                Uri createdUri = createObservationPhotoForPhoto(selectedImageUri);

                if (createdUri == null) {
                	mHelper.alert(getResources().getString(R.string.alert_unsupported_media_type));
                	mFileUri = null;
                	return;
                }

                updateImages();
                if (!isCamera) {
                    promptImportPhotoMetadata(selectedImageUri);
               }

            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
                Toast.makeText(this,  String.format(getString(R.string.something_went_wrong), mFileUri.toString()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + (data == null ? "null" : data.getData()));
            }
            mFileUri = null; // don't let this hang around
            
        } else if (requestCode == COMMENTS_IDS_REQUEST_CODE) {
            
            // We know that the user now viewed all of the comments needed to be viewed (no new comments/ids)
            mObservation.comments_count += data.getIntExtra(CommentsIdsActivity.NEW_COMMENTS, 0);
            mObservation.identifications_count += data.getIntExtra(CommentsIdsActivity.NEW_IDS, 0);
            mObservation.last_comments_count = mObservation.comments_count;
            mObservation.last_identifications_count = mObservation.identifications_count;
            mObservation.taxon_id = data.getIntExtra(CommentsIdsActivity.TAXON_ID, 0);
            
            String speciesGuess = data.getStringExtra(CommentsIdsActivity.SPECIES_GUESS);
            if (speciesGuess != null) {
            	mSpeciesGuess = speciesGuess;
            	mObservation.species_guess = speciesGuess;
            	mSpeciesGuessTextView.setText(mSpeciesGuess);
            }
            String iconicTaxonName = data.getStringExtra(CommentsIdsActivity.ICONIC_TAXON_NAME);
            if (iconicTaxonName != null) mObservation.iconic_taxon_name = iconicTaxonName;

            // Only update the last_comments/id_count fields
            ContentValues cv = mObservation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis()); // No need to sync
            getContentResolver().update(mUri, cv, null, null);

            mObservationCommentsIds.setBackgroundResource(R.drawable.comments_ids_background);

            Integer totalCount = mObservation.comments_count + mObservation.identifications_count;
            refreshCommentsIdSize(totalCount);
        }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            // Returned from activity AND it's a new observation
            if (mObservation.longitude == null) {
                // Got stopped in the middle of retrieving GPS coordinates - try again
                getLocation();
            }
        }
    }
    
    private void refreshCommentsIdSize(Integer value) {
        ViewTreeObserver observer = mObservationCommentsIds.getViewTreeObserver();
        // Make sure the height and width of the rectangle are the same (i.e. a square)
        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
			@Override
            public void onGlobalLayout() {
                int dimension = mObservationCommentsIds.getHeight();
                ViewGroup.LayoutParams params = mObservationCommentsIds.getLayoutParams();
                
                if (dimension > mObservationCommentsIds.getWidth()) {
                    // Only resize if there's enough room
                    params.width = dimension;
                    mObservationCommentsIds.setLayoutParams(params);
                }
                
                ViewTreeObserver observer = mObservationCommentsIds.getViewTreeObserver();
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    observer.removeGlobalOnLayoutListener(this);
                } else {
                    observer.removeOnGlobalLayoutListener(this);
                }  

            }
        });
        
        mObservationCommentsIds.setText(value.toString());
    }

    private Uri createObservationPhotoForPhoto(Uri photoUri) {
        ObservationPhoto op = new ObservationPhoto();
        Long photoId = null;
        int random = (new Random()).nextInt();
        try {
        	photoId = ContentUris.parseId(photoUri);
        } catch (Exception exc) {
        	// Not a supported media type (e.g. Google Drive)
        	exc.printStackTrace();
        	return null;
        }

        op.uuid = UUID.randomUUID().toString();

        ContentValues cv = op.getContentValues();
        cv.put(ObservationPhoto._OBSERVATION_ID, mObservation._id);
        cv.put(ObservationPhoto.OBSERVATION_ID, mObservation.id);
        if (photoId > -1) {
            cv.put(ObservationPhoto._PHOTO_ID, photoId.intValue());
        }
        if (mGallery.getCount() == 0) {
            cv.put(ObservationPhoto.POSITION, 0);
        } else {
            cv.put(ObservationPhoto.POSITION, mGallery.getCount());
        }
        return getContentResolver().insert(ObservationPhoto.CONTENT_URI, cv);
    }
    
    private void promptImportPhotoMetadata(Uri photoUri) {
        final Uri uri = photoUri;
        confirm(ObservationEditor.this, R.string.import_metadata, R.string.import_metadata_desc, 
                R.string.yes, R.string.no, 
                new Runnable() { public void run() {
                    importPhotoMetadata(uri);
                }}, 
                null);
    }
    
    private void importPhotoMetadata(Uri photoUri) {
        String imgFilePath = imageFilePathFromUri(photoUri);
        
        if (imgFilePath == null) return;
        
        try {
            ExifInterface exif = new ExifInterface(imgFilePath);
            float[] latLng = new float[2];
            uiToObservation();
            if (exif.getLatLong(latLng)) {
                stopGetLocation();
                mObservation.latitude = (double) latLng[0];
                mObservation.longitude = (double) latLng[1];
                mObservation.positional_accuracy = null;
            } else {
                // Nullify the GPS coordinates
                mObservation.latitude = null;
                mObservation.longitude = null;
                mObservation.positional_accuracy = null;
                mObservation.private_longitude = mObservation.longitude;
                mObservation.private_latitude = mObservation.latitude;
            }

            if ((mObservation.geoprivacy != null) && ((mObservation.geoprivacy.equals("private") || mObservation.geoprivacy.equals("obscured")))) {
                mObservation.private_longitude = mObservation.longitude;
                mObservation.private_latitude = mObservation.latitude;
            }

            if (mObservation.latitude_changed()) {
                if (isNetworkAvailable()) {
                    guessLocation();
                } else {
                    setPlaceGuess(null);
                }
            }

            String datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (datetime != null) {
                SimpleDateFormat exifDateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                try {
                    Date date = exifDateFormat.parse(datetime);
                    Timestamp timestamp = new Timestamp(date.getTime());
                    mObservation.observed_on = timestamp;
                    mObservation.time_observed_at = timestamp;
                    mObservation.observed_on_string = app.formatDatetime(timestamp);

                    mObservedOnStringTextView.setText(app.formatDatetime(timestamp));
                    mTimeObservedAtButton.setText(app.shortFormatTime(timestamp));
                    mDateSetByUser = timestamp;
                    mTimeSetByUser = timestamp;
                } catch (ParseException e) {
                    Log.d(TAG, "Failed to parse " + datetime + ": " + e);
                }
            }
            observationToUi();
        } catch (IOException e) {
            Log.e(TAG, "couldn't find " + imgFilePath);
        }
    }
    
    private void deletePhoto(int position) {
    	GalleryCursorAdapter adapter = (GalleryCursorAdapter) mGallery.getAdapter();
    	Long photoId = adapter.getItemId(position);
    	getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_photo_id = " + photoId, null);
    	updateImages();
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
    	return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
    	return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
    	return "com.android.providers.media.documents".equals(uri.getAuthority());
    } 
    
    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
    		String[] selectionArgs) {

    	Cursor cursor = null;
    	final String column = "_data";
    	final String[] projection = {
    			column
    	};

    	try {
    		cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
    				null);
    		if (cursor != null && cursor.moveToFirst()) {
    			final int column_index = cursor.getColumnIndexOrThrow(column);
    			return cursor.getString(column_index);
    		}
    	} finally {
    		if (cursor != null)
    			cursor.close();
    	}
    	return null;
    } 
    
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    @SuppressLint("NewApi")
	private Uri getPath(final Context context, final Uri uri) {
    	
    	final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    	// DocumentProvider
    	if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
    		// ExternalStorageProvider
    		if (isExternalStorageDocument(uri)) {
    			final String docId = DocumentsContract.getDocumentId(uri);
    			final String[] split = docId.split(":");
    			final String type = split[0];

    			if ("primary".equalsIgnoreCase(type)) {
    				return Uri.parse(Environment.getExternalStorageDirectory() + "/" + split[1]);
    			}

    		}
    		// DownloadsProvider
    		else if (isDownloadsDocument(uri)) {

    			final String id = DocumentsContract.getDocumentId(uri);
    			final Uri contentUri = ContentUris.withAppendedId(
    					Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

    			return contentUri;
    		}
    		// MediaProvider
    		else if (isMediaDocument(uri)) {
    			final String docId = DocumentsContract.getDocumentId(uri);
    			final String[] split = docId.split(":");
    			final String type = split[0];

    			Uri contentUri = null;
    			if ("image".equals(type)) {
    				contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    			} else if ("video".equals(type)) {
    				contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    			} else if ("audio".equals(type)) {
    				contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    			}

    			final String selection = "_id=?";
    			final String[] selectionArgs = new String[] {
    					split[1]
    			};


    			return ContentUris.withAppendedId(contentUri, Long.valueOf(split[1]));
    		}
    	}

    	// MediaStore (and general)
    	if ("content".equalsIgnoreCase(uri.getScheme())) {
    		return uri;
    	}
    	// File
    	else if ("file".equalsIgnoreCase(uri.getScheme())) {
    		return uri;
    	}
    	
    	return null;
    }
    

    private void updateImageOrientation(Uri uri) {
        String imgFilePath = imageFilePathFromUri(uri);
        
        if (imgFilePath == null) return;
        
        ContentValues values = new ContentValues();
        int degrees = -1;
        try {
            ExifInterface exif = new ExifInterface(imgFilePath);
            degrees = exifOrientationToDegrees(
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 
                            ExifInterface.ORIENTATION_NORMAL));
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, degrees);
            getContentResolver().update(uri, values, null, null);
            
            String lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String d = exif.getAttribute(ExifInterface.TAG_DATETIME);
            Log.d(TAG, "lat: " + lat + ", d: " + d);
        } catch (IOException e) {
            Log.e(TAG, "couldn't find " + imgFilePath);
        } catch (Exception e) {
        	Log.e(TAG, "Couldn't update image orientation for path: " + uri);
            SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            String username = pref.getString("username", null);
        }
    }
    
    private String imageFilePathFromUri(Uri uri) {
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.Images.Media.DATA
        };
        

        Cursor c;
        try {
        	c = getContentResolver().query(uri, projection, null, null, null);
        } catch (Exception exc) {
        	exc.printStackTrace();
        	return null;
        }
        
        if ((c == null) || (c.getCount() == 0)) {
        	return null;
        }

        c.moveToFirst();
        String path = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
        c.close();
        
        return path;
    }

    protected void updateImages() {
    	if (mObservation.id != null) {
    		mImageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
    				ObservationPhoto.PROJECTION, 
    				"_observation_id=? or observation_id=?", 
    				new String[]{mObservation._id.toString(), mObservation.id.toString()}, 
    				ObservationPhoto.DEFAULT_SORT_ORDER);
    	} else {
     		mImageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
    				ObservationPhoto.PROJECTION, 
    				"_observation_id=?", 
    				new String[]{mObservation._id.toString()}, 
    				"id ASC, _id ASC");
    	}
        mImageCursor.moveToFirst();
        mGallery.setAdapter(new GalleryCursorAdapter(this, mImageCursor));
    }

    public class GalleryCursorAdapter extends BaseAdapter {
        private Context mContext;
        private Cursor mCursor;
        private HashMap<Integer, View> mViews;

        public GalleryCursorAdapter(Context c, Cursor cur) {
            mContext = c;
            mCursor = cur;
            mViews = new HashMap<Integer, View>();
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
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
        }

        public Uri getItemUri(int position) {
            mCursor.moveToPosition(position);
            int imageId = mCursor.getInt(mCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
            String imageUrl = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));

            if (imageUrl != null) {
                // Online photo
                return Uri.parse(imageUrl);
            }

            // Offline (local storage) photo
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
        }

        private Cursor findPhotoInStorage(Integer photoId) {
            Cursor imageCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.Images.ImageColumns.ORIENTATION},
                    MediaStore.MediaColumns._ID + " = " + photoId, null, null);

            imageCursor.moveToFirst();
            return imageCursor;
        }

        public void setAsFirstPhoto(int position) {
            Long photoId = getItemId(position);
            mFirstPositionPhotoId = photoId;


            // Set current photo to be positioned first
            mCursor.moveToPosition(position);
            ObservationPhoto op = new ObservationPhoto(mCursor);
            op.position = 0;
            getContentResolver().update(ObservationPhoto.CONTENT_URI, op.getContentValues(), "_photo_id = " + photoId, null);

            // Update the rest of the photos to be positioned afterwards
            int currentPosition = 1;
            int count = mCursor.getCount();
            mCursor.moveToPosition(0);

            do {
                if (mCursor.getPosition() != position) {
                    ObservationPhoto currentOp = new ObservationPhoto(mCursor);
                    currentOp.position = currentPosition;
                    getContentResolver().update(ObservationPhoto.CONTENT_URI, currentOp.getContentValues(), "_photo_id = " + currentOp._photo_id, null);
                    currentPosition++;
                }
            } while (mCursor.moveToNext());

            updateImages();
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            if (mViews.containsKey(position)) {
                return mViews.get(position);
            }

            mCursor.moveToPosition(position);

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ViewGroup container = null;
            ImageView imageView;

            container = (ViewGroup) inflater.inflate(R.layout.observation_photo_gallery_item, null, false);
            imageView = (ImageView) container.findViewById(R.id.observation_photo);

            int imageId = mCursor.getInt(mCursor.getColumnIndexOrThrow(ObservationPhoto._ID));
            int photoId = mCursor.getInt(mCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
            String imageUrl = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));

            if (imageUrl != null) {
                // Online photo
            	imageView.setLayoutParams(new LinearLayout.LayoutParams(getResources().getDimensionPixelOffset(R.dimen.obs_viewer_photo_thumb_size), getResources().getDimensionPixelOffset(R.dimen.obs_viewer_photo_thumb_size)));
                UrlImageViewHelper.setUrlDrawable(imageView, imageUrl, new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        return ImageUtils.getRoundedCornerBitmap(ImageUtils.centerCropBitmap(loadedBitmap));
                    }
                });
            } else {
                // Offline photo
                Cursor pc = findPhotoInStorage(photoId);
                if (pc.getCount() == 0) {
                    // photo has been deleted, delete the corresponding db row
                    getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_id = " + imageId, null);
                } else {
                    int orientation = pc.getInt(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION));
                    Bitmap bitmapImage = MediaStore.Images.Thumbnails.getThumbnail(
                            getContentResolver(),
                            photoId,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            (BitmapFactory.Options) null);

                    if (bitmapImage == null) {
                        // Couldn't retrieve the thumbnail - get the original image
                        try {
                            bitmapImage = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(),
                                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (bitmapImage != null) {
                        if (orientation != 0) {
                            Matrix matrix = new Matrix();
                            matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
                            bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
                        }
                        imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(ImageUtils.centerCropBitmap(bitmapImage)));
                    }
                }
            }

            View isFirst = container.findViewById(R.id.observation_is_first);
            Integer obsPhotoPosition = mCursor.getInt(mCursor.getColumnIndexOrThrow(ObservationPhoto.POSITION));

            if ((obsPhotoPosition != null) && (obsPhotoPosition == 0)) {
                container.findViewById(R.id.is_first_on).setVisibility(View.VISIBLE);
                container.findViewById(R.id.is_first_off).setVisibility(View.GONE);
                container.findViewById(R.id.is_first_text).setVisibility(View.VISIBLE);
            } else {
                container.findViewById(R.id.is_first_on).setVisibility(View.GONE);
                container.findViewById(R.id.is_first_off).setVisibility(View.VISIBLE);
                container.findViewById(R.id.is_first_text).setVisibility(View.GONE);
            }

            imageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ObservationEditor.this, ObservationPhotosViewer.class);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObservation.id);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID_INTERNAL, mObservation._id);
                    intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, true);
                    intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, position);
                    startActivityForResult(intent, OBSERVATION_PHOTOS_REQUEST_CODE);

                    startActivity(intent);
                }
            });

            isFirst.setTag(new Integer(position));
            isFirst.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Integer position = (Integer) view.getTag();
                    Long photoId = getItemId(position);

                    if ((mFirstPositionPhotoId == null) || (mFirstPositionPhotoId != photoId)) {
                        setAsFirstPhoto(position);
                    }
                }
            });

            mViews.put(position, container);
            return container;
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
    
    /**
     * Display a confirm dialog. 
     * @param activity
     * @param title
     * @param message
     * @param positiveLabel
     * @param negativeLabel
     * @param onPositiveClick runnable to call (in UI thread) if positive button pressed. Can be null
     * @param onNegativeClick runnable to call (in UI thread) if negative button pressed. Can be null
     */
    public final void confirm(
            final Activity activity, 
            final int title, 
            final int message,
            final int positiveLabel, 
            final int negativeLabel,
            final Runnable onPositiveClick,
            final Runnable onNegativeClick) {
        mHelper.confirm(getString(title), getString(message),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (onPositiveClick != null) onPositiveClick.run();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (onNegativeClick != null) onNegativeClick.run();
                    }
                },
                positiveLabel, negativeLabel);
    }

    
    private void refreshProjectFields() {
        ProjectFieldViewer.getProjectFields(this, mProjectIds, (mObservation.id == null ? mObservation._id : mObservation.id), new ProjectFieldViewer.ProjectFieldsResults() {
            @Override
            public void onProjectFieldsResults(Hashtable<Integer, ProjectField> projectFields, HashMap<Integer, ProjectFieldValue> projectValues) {
                mProjectFields = projectFields;

                if (mProjectFieldValues == null) {
                    mProjectFieldValues = projectValues;
                }

                addProjectFieldViewers();
            }
        });
    }

    private void addProjectFieldViewers() {
        // Prepare the fields for display
        
        ArrayList<Map.Entry<Integer, ProjectField>> fields = new ArrayList(mProjectFields.entrySet());
        Collections.sort(fields, new Comparator<Map.Entry<Integer, ProjectField>>() {
            @Override
            public int compare(Entry<Integer, ProjectField> lhs, Entry<Integer, ProjectField> rhs) {
                ProjectField field1 = lhs.getValue();
                ProjectField field2 = rhs.getValue();
                
                Integer projectId1 = (field1.project_id != null ? field1.project_id : Integer.valueOf(-1));
                Integer projectId2 = (field2.project_id != null ? field2.project_id : Integer.valueOf(-1));
                
                if (projectId1 == projectId2) {
                    // Same project - sort by position
                    Integer position1 = (field1.position != null ? field1.position : Integer.valueOf(0));
                    Integer position2 = (field2.position != null ? field2.position : Integer.valueOf(0));
                    
                    return position1.compareTo(position2);
                } else {
                    // Group fields together in the same project
                    return projectId1.compareTo(projectId2);
                }
            }
        });

    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }	

    
    private void openImageIntent(final Activity activity, Uri captureImageOutputFile, int requestCode) {
        new BottomSheet.Builder(activity).sheet(R.menu.observation_confirmation_photo_menu).listener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent;
                switch (which) {
                    case R.id.camera:
                        takePhoto();
                        break;
                    case R.id.upload_photo:
                        choosePhoto();
                        break;
                }
            }
        }).show();
    }
}
