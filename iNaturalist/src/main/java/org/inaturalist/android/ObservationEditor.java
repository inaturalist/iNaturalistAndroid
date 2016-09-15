package org.inaturalist.android;

import com.cocosw.bottomsheet.BottomSheet;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
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
import org.apache.commons.collections4.functors.ExceptionClosure;
import org.apache.commons.lang3.StringUtils;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TagInfo;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputField;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.lucasr.twowayview.TwoWayView;

import com.ptashek.widgets.datetimepicker.DateTimePicker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
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
import android.media.MediaScannerConnection;
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
import android.support.v4.content.CursorLoader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
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

public class ObservationEditor extends AppCompatActivity {
    private final static String TAG = "INAT: ObservationEditor";
    public final static String TAKE_PHOTO = "take_photo";
    public final static String CHOOSE_PHOTO = "choose_photo";
    public final static String RETURN_TO_OBSERVATION_LIST = "return_to_observation_list";
    public static final int RESULT_DELETED = 0x1000;
    public static final int RESULT_RETURN_TO_OBSERVATION_LIST = 0x1001;
    public static final int RESULT_REFRESH_OBS = 0x1002;
    private Uri mUri;
    private Cursor mCursor;
    private Cursor mImageCursor;
    private EditText mSpeciesGuessTextView;
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
    private TextView mProjectSelector;
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

    private ArrayList<String> mPhotosAdded;
    private ArrayList<ObservationPhoto> mPhotosRemoved;

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
    private ProjectReceiver mProjectReceiver;
        
    
    private ArrayList<BetterJSONObject> mProjects = null;
    
	private boolean mProjectFieldsUpdated = false;
	private boolean mDeleted = false;
    private boolean mIsConfirmation;
    private boolean mPictureTaken;
    private ImageView mSpeciesGuessIcon;
    private String mPreviousTaxonSearch = "";
    private String mTaxonPicUrl;
    private boolean mIsTaxonUnknown;
    private boolean mIsCustomTaxon;
    private TextView mProjectCount;
    private String mFirstPositionPhotoId;
    private boolean mGettingLocation;
    private ImageView mLocationIcon;
    private TextView mLocationGuess;
    private TextView mFindingCurrentLocation;
    private boolean mLocationManuallySet;
    private boolean mReturnToObservationList;
    private boolean mTaxonTextChanged = false;
    private boolean mTaxonSearchStarted = false;
    private boolean mPhotosChanged = false;
    private ArrayList<String> mCameraPhotos;

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
        if (mProjectIds.size() == 0) {
            mProjectCount.setVisibility(View.GONE);
            mProjectSelector.setTextColor(Color.parseColor("#8A000000"));
            mProjectSelector.setText(R.string.add_to_projects);
        } else {
            mProjectCount.setVisibility(View.VISIBLE);
            mProjectCount.setText(String.valueOf(mProjectIds.size()));
            mProjectSelector.setTextColor(Color.parseColor("#000000"));
            mProjectSelector.setText(R.string.projects);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

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

        mCameraPhotos = new ArrayList<String>();

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

            mPhotosAdded = new ArrayList<String>();
            mPhotosRemoved = new ArrayList<ObservationPhoto>();

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
            mFirstPositionPhotoId = savedInstanceState.getString("mFirstPositionPhotoId");
            mGettingLocation = savedInstanceState.getBoolean("mGettingLocation");
            mLocationManuallySet = savedInstanceState.getBoolean("mLocationManuallySet");
            mReturnToObservationList = savedInstanceState.getBoolean("mReturnToObservationList");
            mPhotosChanged = savedInstanceState.getBoolean("mPhotosChanged");
            mPhotosAdded = savedInstanceState.getStringArrayList("mPhotosAdded");
            mPhotosRemoved = (ArrayList<ObservationPhoto>) savedInstanceState.getSerializable("mPhotosRemoved");
            mCameraPhotos = savedInstanceState.getStringArrayList("mCameraPhotos");
        }


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
        mSpeciesGuessTextView = (EditText) findViewById(R.id.speciesGuess);
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
        mTopActionBar = getSupportActionBar();
        mDeleteButton = (ImageButton) findViewById(R.id.delete_observation);
        mViewOnInat = (ImageButton) findViewById(R.id.view_on_inat);
        mObservationCommentsIds = (TextView) findViewById(R.id.commentIdCount);
        mProjectSelector = (TextView) findViewById(R.id.select_projects);
        mProjectCount = (TextView) findViewById(R.id.project_count);
        mProjectFieldsTable = (TableLayout) findViewById(R.id.project_fields);
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
                mTaxonSearchStarted = true;
                Intent intent = new Intent(ObservationEditor.this, TaxonSearchActivity.class);
                intent.putExtra(TaxonSearchActivity.SPECIES_GUESS, mSpeciesGuessTextView.getText().toString());
                intent.putExtra(TaxonSearchActivity.SHOW_UNKNOWN, true);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        });

        mSpeciesGuessTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                return;
                /*
                String newTaxon = mSpeciesGuessTextView.getText().toString();
                if ((!mTaxonTextChanged) && (!mTaxonSearchStarted)) {
                    mTaxonSearchStarted = true;
                    Intent intent = new Intent(ObservationEditor.this, TaxonSearchActivity.class);
                    intent.putExtra(TaxonSearchActivity.SPECIES_GUESS, newTaxon);
                    intent.putExtra(TaxonSearchActivity.SHOW_UNKNOWN, true);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // Special material design animation
                        View sharedView = mSpeciesGuessTextView;
                        String transitionName = "search_taxon";
                        ActivityOptions transitionActivityOptions = ActivityOptions.makeSceneTransitionAnimation(ObservationEditor.this, sharedView, transitionName);
                        try {
                            startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE, transitionActivityOptions.toBundle());
                        } catch (Exception exc) {
                            // Internal Android bug when rotating screen of activity opened this way
                            exc.printStackTrace();
                            startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
                        }
                    } else {
                        startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
                    }
                }
                */

            }
        });

        mTopActionBar.setHomeButtonEnabled(true);
        mTopActionBar.setDisplayHomeAsUpEnabled(true);
        View takePhoto;

        mTopActionBar.setLogo(R.drawable.ic_arrow_back);
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
                            mLocationManuallySet = true;
                            getLocation();
                        } else {
                            // Edit location
                            Intent intent = new Intent(ObservationEditor.this, LocationChooserActivity.class);
                            Double lat, lon;
                            lat = mObservation.private_latitude == null ? mObservation.latitude : mObservation.private_latitude;
                            lon = mObservation.private_longitude == null ? mObservation.longitude : mObservation.private_longitude;
                            intent.putExtra(LocationChooserActivity.LONGITUDE, lon);
                            intent.putExtra(LocationChooserActivity.LATITUDE,  lat);
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


        if (intent != null) {
            mReturnToObservationList =  intent.getBooleanExtra(RETURN_TO_OBSERVATION_LIST, false);
        }

        updateObservationVisibilityDescription();

        ((SwipeableLinearLayout)findViewById(R.id.swipeable_layout)).setOnSwipeListener(new SwipeableLinearLayout.SwipeListener() {
            @Override
            public void onSwipeRight() {
                editNextObservation(-1);
            }

            @Override
            public void onSwipeLeft() {
                editNextObservation(1);
            }
        });
    }

    private void editNextObservation(int direction) {
        Log.v("ObservationEditor", "editNextObservation: Direction = " + direction);
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

        Cursor cursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                conditions, null, Observation.DEFAULT_SORT_ORDER);

        // Find next observation
        Long obsId, externalObsId;
        cursor.moveToFirst();
        Log.e("ObservationEditor", "Current obs id: " + mObservation._id + ", " + mObservation.id);
        do {
            obsId = cursor.getLong(cursor.getColumnIndexOrThrow(Observation._ID));
            externalObsId = cursor.getLong(cursor.getColumnIndexOrThrow(Observation.ID));
            if (((mObservation._id != null) && (obsId.equals(mObservation._id.longValue()))) ||
                ((mObservation.id != null) && (externalObsId.equals(mObservation.id.longValue())))) {
                Log.e("ObservationEditor", "Found current obs with " + obsId + ", " + externalObsId);
                break;
            }
        } while (cursor.moveToNext());

        if (
                ((direction == 1) && !cursor.isLast() && !cursor.isAfterLast()) ||
                ((direction == -1) && !cursor.isFirst() && !cursor.isBeforeFirst())
                ) {

            // Edit the next observation (if one is available)
            if (direction == 1) {
                Log.v("ObservationEditor", "Moving to previous observation");
                cursor.moveToNext();
            } else {
                Log.v("ObservationEditor", "Moving to next observation");
                cursor.moveToPrevious();
            }
            obsId = cursor.getLong(cursor.getColumnIndexOrThrow(Observation._ID));
            externalObsId = cursor.getLong(cursor.getColumnIndexOrThrow(Observation.ID));
            Log.e("ObservationEditor", "Next obs ID: " + obsId + ", " + externalObsId);
            cursor.close();
            Uri uri = ContentUris.withAppendedId(Observation.CONTENT_URI, obsId != null ? obsId : externalObsId);
            Intent intent = new Intent(Intent.ACTION_EDIT, uri, this, ObservationEditor.class);
            intent.putExtra(RETURN_TO_OBSERVATION_LIST, true);
            startActivity(intent);

            uiToProjectFieldValues();
            if (save()) {
                setResult(RESULT_RETURN_TO_OBSERVATION_LIST);
                finish();
            }
        }

    }

    private void takePhoto() {
        /*
        mFileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE); // create a file to save the image
        mFileUri = getPath(ObservationEditor.this, mFileUri);
        */
        // Temp file for the photo
        mFileUri = Uri.fromFile(new File(getExternalCacheDir(), UUID.randomUUID().toString() + ".jpeg"));

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
        if (!mObservation.isDirty() && !mPhotosChanged) {
            // User hasn't changed anything - no need to display confirmation dialog
            mCanceled = true;
            setResult(mReturnToObservationList ? RESULT_RETURN_TO_OBSERVATION_LIST : RESULT_CANCELED);
            finish();
            return false;
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
                        revertPhotos();
                        setResult(mReturnToObservationList ? RESULT_RETURN_TO_OBSERVATION_LIST : RESULT_CANCELED);
                        finish();
                    }
                },
                null);

        return true;
    }

    // User canceled - revert any changes made to the observation photos
    private void revertPhotos() {
        // Add any photos that were deleted
        for (ObservationPhoto photo : mPhotosRemoved) {
            ContentValues cv = photo.getContentValues();
            getContentResolver().insert(ObservationPhoto.CONTENT_URI, cv);
        }

        // Delete any photos that were added
        for (String uriString : mPhotosAdded) {
            Uri uri = Uri.parse(uriString);
            getContentResolver().delete(uri, null, null);
        }

        // Restore the positions of all photos
    	updateImages();
        GalleryCursorAdapter adapter = (GalleryCursorAdapter) mGallery.getAdapter();
        adapter.refreshPhotoPositions(null);
    }


    private boolean hasNoCoords() {
        if (mLatitudeView.getText() == null || mLatitudeView.getText().length() == 0) {
            return true;
        }
        if (mLongitudeView.getText() == null || mLongitudeView.getText().length() == 0) {
            return true;
        }

        return false;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return onBack();
            case R.id.save_observation:
                if (hasNoCoords()) {
                    // Confirm with the user that he's about to save an observation with no coordinates
                    confirm(ObservationEditor.this, R.string.save_observation, R.string.are_you_sure_you_want_to_save_obs_without_coords,
                            R.string.yes, R.string.no,
                            new Runnable() {
                                @Override
                                public void run() {
                                    uiToProjectFieldValues();
                                    if (save()) {
                                        setResult(mReturnToObservationList ? RESULT_RETURN_TO_OBSERVATION_LIST : RESULT_REFRESH_OBS);
                                        finish();
                                    }
                                }
                            }, null);

                    return true;
                }

                uiToProjectFieldValues();
                if (save()) {
                    setResult(mReturnToObservationList ? RESULT_RETURN_TO_OBSERVATION_LIST : RESULT_REFRESH_OBS);
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

                                setResult(mReturnToObservationList ? RESULT_RETURN_TO_OBSERVATION_LIST : RESULT_DELETED);
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
        outState.putString("mFirstPositionPhotoId", mFirstPositionPhotoId);
        outState.putBoolean("mGettingLocation", mGettingLocation);
        outState.putBoolean("mLocationManuallySet", mLocationManuallySet);
        outState.putBoolean("mReturnToObservationList", mReturnToObservationList);
        outState.putBoolean("mPhotosChanged", mPhotosChanged);
        outState.putStringArrayList("mPhotosAdded", mPhotosAdded);
        outState.putSerializable("mPhotosRemoved", mPhotosRemoved);
        outState.putStringArrayList("mCameraPhotos", mCameraPhotos);
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
            mLocationProgressView.setVisibility(View.VISIBLE);
            mFindingCurrentLocation.setVisibility(View.VISIBLE);
            mLocationRefreshButton.setVisibility(View.GONE);
            mLocationIcon.setVisibility(View.GONE);

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
        if ((((mObservation.species_guess == null) && (mSpeciesGuessTextView.getText().length() > 0) && (!mIsTaxonUnknown)) || (mObservation.species_guess != null)) && (!mTaxonSearchStarted)) mObservation.species_guess = mSpeciesGuessTextView.getText().toString();
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

        mTaxonTextChanged = true;
        mSpeciesGuessTextView.setText(mIsTaxonUnknown ? "Unknown" : mObservation.species_guess);
        mTaxonTextChanged = false;
        mDescriptionTextView.setText(mObservation.description);
        if (mObservation.observed_on == null) {
            mObservedOnButton.setText(getString(R.string.set_date));
            mObservedOnButton.setTextColor(Color.parseColor("#757575"));
        } else {
            mObservedOnButton.setText(app.shortFormatDate(mObservation.observed_on));
            mObservedOnButton.setTextColor(Color.parseColor("#000000"));
        }
        if (mObservation.observed_on_string != null) {
            mObservedOnStringTextView.setText(mObservation.observed_on_string);
            mObservedOnStringTextView.setTextColor(Color.parseColor("#000000"));
            mDateSetByUser = mObservation.observed_on;
        }
        if (mObservation.time_observed_at == null) {
            mTimeObservedAtButton.setText(getString(R.string.set_time));
            mTimeObservedAtButton.setTextColor(Color.parseColor("#757575"));
        } else {
            mTimeObservedAtButton.setText(app.shortFormatTime(mObservation.time_observed_at));
            mTimeSetByUser = mObservation.time_observed_at;
            mTimeObservedAtButton.setTextColor(Color.parseColor("#000000"));
        }

        if ((mObservation.latitude != null) || (mObservation.private_latitude != null)) {
            mLatitudeView.setText(mObservation.latitude != null ? mObservation.latitude.toString() : mObservation.private_latitude.toString() );
            findViewById(R.id.coordinates).setVisibility(View.VISIBLE);
        } else {
            mLatitudeView.setText("");
        }
        if ((mObservation.longitude != null) || (mObservation.private_longitude != null)) {
            mLongitudeView.setText(mObservation.longitude != null ? mObservation.longitude.toString() : mObservation.private_longitude.toString() );
        } else {
            mLongitudeView.setText("");
        }
        if ((mObservation.positional_accuracy == null) && (mObservation.private_positional_accuracy == null)) {
            mAccuracyView.setText("");

            findViewById(R.id.accuracy_prefix).setVisibility(View.GONE);
            findViewById(R.id.accuracy).setVisibility(View.GONE);
        } else {
            mAccuracyView.setText(mObservation.positional_accuracy != null ? mObservation.positional_accuracy.toString() : mObservation.private_positional_accuracy.toString() );
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
            mLocationGuess.setTextColor(Color.parseColor("#000000"));
        } else {
            mLocationGuess.setText(getString(R.string.set_location));
            mLocationGuess.setTextColor(Color.parseColor("#757575"));
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
                mObservedOnStringTextView.setTextColor(Color.parseColor("#000000"));
                mObservedOnButton.setText(app.shortFormatDate(date));
            } catch (ParseException dateTimeException) {
                date = new Timestamp(year - 1900, month, day, 0, 0, 0, 0);
                if (date.getTime() > System.currentTimeMillis()) {
                    date = new Timestamp(System.currentTimeMillis());
                }
                mObservedOnStringTextView.setText(app.formatDate(date));
                mObservedOnStringTextView.setTextColor(Color.parseColor("#000000"));
                mObservedOnButton.setText(app.shortFormatDate(date));
            }

            mDateSetByUser = date;
            mObservedOnButton.setTextColor(Color.parseColor("#000000"));
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
            mObservedOnStringTextView.setTextColor(Color.parseColor("#000000"));
            mTimeObservedAtButton.setText(app.shortFormatTime(datetime));
            mTimeObservedAtButton.setTextColor(Color.parseColor("#000000"));
            mTimeSetByUser = datetime;
        }
    };
    private ArrayList<Integer> mProjectIds;
    private ArrayList<ProjectField> mProjectFields;
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
            mLocationGuess.setTextColor(Color.parseColor("#000000"));
            mObservation.place_guess = placeGuess;
        } else {
            mLocationGuess.setText(R.string.set_location);
            mLocationGuess.setTextColor(Color.parseColor("#757575"));
            mObservation.place_guess = null;
        }
    }

    private void setCurrentLocation(Location location) {
        mCurrentLocation = location;

        // Update any external photos taken through the app with the coordinates
        for (String filename : mCameraPhotos) {
            try {
                ExifInterface exif;
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                exif = new ExifInterface(filename);
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GPSEncoder.convert(latitude));
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GPSEncoder.latitudeRef(latitude));
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPSEncoder.convert(longitude));
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GPSEncoder.longitudeRef(longitude));
                exif.saveAttributes();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

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
                mLocationManuallySet = true;
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
            mTaxonSearchStarted = false;
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
                    mTaxonTextChanged = true;
                    mSpeciesGuessTextView.setText("Unknown");
                    mTaxonTextChanged = false;
                    mPreviousTaxonSearch = "Unknown";
                    mObservation.preferred_common_name = null;
                    mTaxonPicUrl = null;
                    mIsTaxonUnknown = true;
                    mIsCustomTaxon = false;

                    mSpeciesGuessIcon.setImageResource(R.drawable.ic_species_guess_black_24dp);
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                        mSpeciesGuessIcon.setAlpha(0.6f);
                    }
                } else {
                    String speciesGuess = String.format("%s", idName);
                    mObservation.preferred_common_name = isCustomTaxon ? null : idName;
                    mSpeciesGuess = speciesGuess;
                    mObservation.species_guess = speciesGuess;
                    mObservation.taxon_id = isCustomTaxon ? null : taxonId;
                    mTaxonTextChanged = true;
                    mSpeciesGuessTextView.setText(mSpeciesGuess);
                    mTaxonTextChanged = false;
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
                                if (loadedBitmap != null) imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(loadedBitmap));
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
            } else {
                // Restore original taxon guess
                mTaxonTextChanged = true;
                mSpeciesGuessTextView.setText(mIsTaxonUnknown ? "Unknown" : mObservation.species_guess);
                mTaxonTextChanged = false;
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
                    }
                }

                Log.v(TAG, String.format("%s: %s", isCamera, selectedImageUri));

                if (isCamera) {
                    // Image captured and saved to mFileUri specified in the Intent
                    Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_LONG).show();
                }

                Uri createdUri = createObservationPhotoForPhoto(selectedImageUri);
                mPhotosAdded.add(createdUri.toString());

                if (isCamera)  {
                    // Make a copy of the image into the phone's camera folder
                    String path = FileUtils.getPath(this, selectedImageUri);
                    String copyPath = addPhotoToGallery(path);

                    if (copyPath != null) {
                        mCameraPhotos.add(copyPath);
                    }

                    // Delete original photo (before resize)
                    File f = new File(path);
                    f.delete();
                }

                if (createdUri == null) {
                	mHelper.alert(getResources().getString(R.string.alert_unsupported_media_type));
                	mFileUri = null;
                	return;
                }

                updateImages();
                if (!isCamera) {
                    // Import photo metadata (e.g. location) only when the location hasn't been set
                    // by the user before (whether manually or by importing previous images)
                    if ((!mLocationManuallySet) && (mObservation.latitude == null) && (mObservation.longitude == null)) {
                        stopGetLocation();
                        mLocationManuallySet = true;
                        importPhotoMetadata(selectedImageUri);
                    }
               } else {
                    // Retrieve current coordinates (since we can't launch the camera intent with GPS coordinates)
                    if (!mLocationManuallySet && !mGettingLocation) {
                        getLocation();
                    }
                }

            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
                Toast.makeText(this,  String.format(getString(R.string.something_went_wrong), mFileUri.toString()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + (data == null ? "null" : data.getData()));
            }
            mFileUri = null; // don't let this hang around
            
        }

        if (Intent.ACTION_INSERT.equals(getIntent().getAction())) {
            // Returned from activity AND it's a new observation
            if ((mObservation.longitude == null) && (mGettingLocation)) {
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
        mPhotosChanged = true;

        String path = FileUtils.getPath(this, photoUri);
        // Resize photo to 2048x2048 max
        String resizedPhoto = resizeImage(path, photoUri);

        if (resizedPhoto == null) {
            return null;
        }


        ObservationPhoto op = new ObservationPhoto();

        op.uuid = UUID.randomUUID().toString();

        ContentValues cv = op.getContentValues();
        cv.put(ObservationPhoto._OBSERVATION_ID, mObservation._id);
        cv.put(ObservationPhoto.OBSERVATION_ID, mObservation.id);
        cv.put(ObservationPhoto.PHOTO_FILENAME, resizedPhoto);
        if (mGallery.getCount() == 0) {
            cv.put(ObservationPhoto.POSITION, 0);
        } else {
            cv.put(ObservationPhoto.POSITION, mGallery.getCount());
        }

        return getContentResolver().insert(ObservationPhoto.CONTENT_URI, cv);
    }

    private void importPhotoMetadata(Uri photoUri) {
        String imgFilePath = FileUtils.getPath(this, photoUri);
        
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
                // No coordinates - don't override the observation coordinates
                return;
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
                    mObservedOnStringTextView.setTextColor(Color.parseColor("#000000"));
                    mTimeObservedAtButton.setText(app.shortFormatTime(timestamp));
                    mTimeObservedAtButton.setTextColor(Color.parseColor("#000000"));
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
        mPhotosChanged = true;
    	GalleryCursorAdapter adapter = (GalleryCursorAdapter) mGallery.getAdapter();

        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(position);

        ObservationPhoto op = new ObservationPhoto(cursor);
        mPhotosRemoved.add(op);

    	String photoId = adapter.getItemIdString(position);
        String photoFilename = cursor.getString(cursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
        if (photoFilename != null) {
            getContentResolver().delete(ObservationPhoto.CONTENT_URI, "photo_filename = '" + photoId + "'", null);
        } else {
            getContentResolver().delete(ObservationPhoto.CONTENT_URI, "photo_url = '" + photoId + "'", null);
        }
    	updateImages();
        // Refresh the positions of all other photos
        adapter = (GalleryCursorAdapter) mGallery.getAdapter();
        adapter.refreshPhotoPositions(null);
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
        String imgFilePath = FileUtils.getPath(this, uri);
        
        if (imgFilePath == null) return;
        
        ContentValues values = new ContentValues();
        int degrees = -1;
        try {
            degrees = ImageUtils.getImageOrientation(imgFilePath);
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, degrees);
            getContentResolver().update(uri, values, null, null);
        } catch (Exception e) {
        	Log.e(TAG, "Couldn't update image orientation for path: " + uri);
        }
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

        public Cursor getCursor() {
            return mCursor;
        }

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
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(ObservationPhoto.ID));
        }

        public String getItemIdString(int position) {
            mCursor.moveToPosition(position);
            String id = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
            if (id == null) {
                return mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
            } else {
                return id;
            }
        }

        public void setAsFirstPhoto(int position) {
            mPhotosChanged = true;

            String photoId = getItemIdString(position);
            mFirstPositionPhotoId = photoId;

            // Set current photo to be positioned first
            mCursor.moveToPosition(position);
            ObservationPhoto op = new ObservationPhoto(mCursor);
            op.position = 0;
            if (op.photo_filename != null) {
                getContentResolver().update(ObservationPhoto.CONTENT_URI, op.getContentValues(), "photo_filename = '" + op.photo_filename + "'", null);
            } else {
                getContentResolver().update(ObservationPhoto.CONTENT_URI, op.getContentValues(), "photo_url = '" + op.photo_url + "'", null);
            }

            // Update the rest of the photos to be positioned afterwards
            refreshPhotoPositions(position);

            updateImages();
        }

        public void refreshPhotoPositions(Integer position) {
            int currentPosition = position == null ? 0 : 1;
            int count = mCursor.getCount();

            if (count == 0) return;

            mCursor.moveToPosition(0);

            do {
                if ((position == null) || (mCursor.getPosition() != position.intValue()))  {
                    ObservationPhoto currentOp = new ObservationPhoto(mCursor);
                    currentOp.position = currentPosition;
                    if (currentOp.photo_filename != null) {
                        getContentResolver().update(ObservationPhoto.CONTENT_URI, currentOp.getContentValues(), "photo_filename = '" + currentOp.photo_filename + "'", null);
                    } else {
                        getContentResolver().update(ObservationPhoto.CONTENT_URI, currentOp.getContentValues(), "photo_url = '" + currentOp.photo_url + "'", null);
                    }

                    currentPosition++;
                }
            } while (mCursor.moveToNext());

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
            String imageUrl = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
            String photoFileName = mCursor.getString(mCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));

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
                int orientation = ImageUtils.getImageOrientation(photoFileName);
                Bitmap bitmapImage = null;

                try {

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    FileInputStream is = new FileInputStream(photoFileName);
                    Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);

                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, options);
                    is.close();

                    // Decode into a thumbnail
                    options.inSampleSize = ImageUtils.calculateInSampleSize(options, 200, 200);

                    // Decode bitmap with inSampleSize set
                    options.inJustDecodeBounds = false;
                    // This decreases in-memory byte-storage per pixel
                    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                    bitmapImage = BitmapFactory.decodeFile(photoFileName, options);

                    if (orientation != 0) {
                        // Rotate the image
                        Matrix matrix = new Matrix();
                        matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
                        bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
                    }

                    imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(ImageUtils.centerCropBitmap(bitmapImage)));
                    bitmap.recycle();
                } catch (FileNotFoundException exc) {
                    exc.printStackTrace();
                } catch (IOException exc) {
                    exc.printStackTrace();
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
                }
            });

            isFirst.setTag(new Integer(position));
            isFirst.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Integer position = (Integer) view.getTag();
                    String photoId = getItemIdString(position);

                    if ((mFirstPositionPhotoId == null) || (!mFirstPositionPhotoId.equals(photoId))) {
                        setAsFirstPhoto(position);
                    }
                }
            });

            mViews.put(position, container);
            return container;
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
            public void onProjectFieldsResults(ArrayList projectFields, HashMap<Integer, ProjectFieldValue> projectValues) {
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
        Collections.sort(mProjectFields, new Comparator<ProjectField>() {
            @Override
            public int compare(ProjectField field1, ProjectField field2) {
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


    /**
     * Resizes an image to max size of 2048x2048
     * @param path the path to the image filename (optional)
     * @param photoUri the original Uri of the image
     * @return the resized image - or original image if smaller than 2048x2048
     */
    private String resizeImage(String path, Uri photoUri) {
        InputStream is = null;
        BitmapFactory.Options options = new BitmapFactory.Options();

        try {
            if (path == null) {
                is = getContentResolver().openInputStream(photoUri);
            } else {
                is = new FileInputStream(path);
            }

            Bitmap bitmap = BitmapFactory.decodeStream(is,null,options);
            int originalHeight = options.outHeight;
            int originalWidth = options.outWidth;
            int newHeight, newWidth;

            // BitmapFactory.decodeStream moves the reading cursor
            is.close();
            if (path == null) {
                is = getContentResolver().openInputStream(photoUri);
            } else {
                is = new FileInputStream(path);
            }


            if (Math.max(originalHeight, originalWidth) < 2048) {
                if (path != null) {
                    // Original file is smaller than 2048x2048 - no need to resize
                    return path;
                } else {
                    // Don't resize because image is smaller than 2048x2048 - however, make a local copy of it
                    newHeight = originalHeight;
                    newWidth = originalWidth;
                }
            } else {
                // Resize but make sure we have the same width/height aspect ratio
                if (originalHeight > originalWidth) {
                    newHeight = 2048;
                    newWidth = (int) (2048 * ((float) originalWidth / originalHeight));
                } else {
                    newWidth = 2048;
                    newHeight = (int) (2048 * ((float) originalHeight / originalWidth));
                }
            }

            Log.d(TAG, "Bitmap h:" + options.outHeight + "; w:" + options.outWidth);
            Log.d(TAG, "Resized Bitmap h:" + newHeight + "; w:" + newWidth);

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

            // Save resized image
            File imageFile = new File(getExternalCacheDir(), UUID.randomUUID().toString() + ".jpeg");
            OutputStream os = new FileOutputStream(imageFile);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();

            // Copy all EXIF data from original image into resized image
            copyExifData(is, new File(imageFile.getAbsolutePath()), null);

            return imageFile.getAbsolutePath();

        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return path;
    }

    // EXIF-copying code taken from: https://bricolsoftconsulting.com/copying-exif-metadata-using-sanselan/
    public static boolean copyExifData(InputStream sourceFileStream, File destFile, List<TagInfo> excludedFields) {
        String tempFileName = destFile.getAbsolutePath() + ".tmp";
        File tempFile = null;
        OutputStream tempStream = null;

        try {
            tempFile = new File (tempFileName);

            TiffOutputSet sourceSet = getSanselanOutputSet(sourceFileStream, TiffConstants.DEFAULT_TIFF_BYTE_ORDER);
            TiffOutputSet destSet = getSanselanOutputSet(destFile, sourceSet.byteOrder);

            // If the EXIF data endianess of the source and destination files
            // differ then fail. This only happens if the source and
            // destination images were created on different devices. It's
            // technically possible to copy this data by changing the byte
            // order of the data, but handling this case is outside the scope
            // of this implementation
            if (sourceSet.byteOrder != destSet.byteOrder) return false;

            destSet.getOrCreateExifDirectory();

            // Go through the source directories
            List<?> sourceDirectories = sourceSet.getDirectories();
            for (int i=0; i<sourceDirectories.size(); i++) {
                TiffOutputDirectory sourceDirectory = (TiffOutputDirectory)sourceDirectories.get(i);
                TiffOutputDirectory destinationDirectory = getOrCreateExifDirectory(destSet, sourceDirectory);

                if (destinationDirectory == null) continue; // failed to create

                // Loop the fields
                List<?> sourceFields = sourceDirectory.getFields();
                for (int j=0; j<sourceFields.size(); j++) {
                    // Get the source field
                    TiffOutputField sourceField = (TiffOutputField) sourceFields.get(j);

                    // Check exclusion list
                    if (excludedFields != null && excludedFields.contains(sourceField.tagInfo)) {
                        destinationDirectory.removeField(sourceField.tagInfo);
                        continue;
                    }

                    // Remove any existing field
                    destinationDirectory.removeField(sourceField.tagInfo);

                    // Add field
                    destinationDirectory.add(sourceField);
                }
            }

            // Save data to destination
            tempStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            new ExifRewriter().updateExifMetadataLossless(destFile, tempStream, destSet);
            tempStream.close();

            // Replace file
            if (destFile.delete()) {
                tempFile.renameTo(destFile);
            }

            return true;

        } catch (ImageReadException exception) {
            exception.printStackTrace();

        } catch (ImageWriteException exception) {
            exception.printStackTrace();

        } catch (IOException exception) {
            exception.printStackTrace();

        } finally {
            if (tempStream != null) {
                try {
                    tempStream.close();
                } catch (IOException e) {
                }
            }

            if (tempFile != null) {
                if (tempFile.exists()) tempFile.delete();
            }
        }

        return false;
    }

    private static TiffOutputDirectory getOrCreateExifDirectory(TiffOutputSet outputSet, TiffOutputDirectory outputDirectory) {
        TiffOutputDirectory result = outputSet.findDirectory(outputDirectory.type);
        if (result != null)
            return result;
        result = new TiffOutputDirectory(outputDirectory.type);
        try {
            outputSet.addDirectory(result);
        } catch (ImageWriteException e) {
            return null;
        }
        return result;
    }


    private static TiffOutputSet getSanselanOutputSet(InputStream stream, int defaultByteOrder)
            throws IOException, ImageReadException, ImageWriteException {
        TiffImageMetadata exif = null;
        TiffOutputSet outputSet = null;

        IImageMetadata metadata = Sanselan.getMetadata(stream, null);
        JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        if (jpegMetadata != null) {
            exif = jpegMetadata.getExif();

            if (exif != null) {
                outputSet = exif.getOutputSet();
            }
        }

        // If JPEG file contains no EXIF metadata, create an empty set
        // of EXIF metadata. Otherwise, use existing EXIF metadata to
        // keep all other existing tags
        if (outputSet == null)
            outputSet = new TiffOutputSet(exif==null?defaultByteOrder:exif.contents.header.byteOrder);

        return outputSet;
    }

    private static TiffOutputSet getSanselanOutputSet(File jpegImageFile, int defaultByteOrder)
            throws IOException, ImageReadException, ImageWriteException {
        TiffImageMetadata exif = null;
        TiffOutputSet outputSet = null;

        IImageMetadata metadata = Sanselan.getMetadata(jpegImageFile);
        JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        if (jpegMetadata != null) {
            exif = jpegMetadata.getExif();

            if (exif != null) {
                outputSet = exif.getOutputSet();
            }
        }

        // If JPEG file contains no EXIF metadata, create an empty set
        // of EXIF metadata. Otherwise, use existing EXIF metadata to
        // keep all other existing tags
        if (outputSet == null)
            outputSet = new TiffOutputSet(exif==null?defaultByteOrder:exif.contents.header.byteOrder);

        return outputSet;
    }

    private String addPhotoToGallery(String path) {
        // Copy the file into the camera folder
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis());
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/");
        if (!storageDir.exists()) storageDir.mkdirs();
        String outputPath;
        try {
            File image = File.createTempFile(
                    timeStamp,                   /* prefix */
                    ".jpeg",                     /* suffix */
                    storageDir                   /* directory */
            );
            outputPath = image.getPath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        try {
            FileInputStream inStream = null;
            inStream = new FileInputStream(path);
            FileOutputStream outStream = new FileOutputStream(outputPath);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();
        } catch (Exception exc) {
            exc.printStackTrace();
            return null;
        }

        // Tell the OS to scan the file (will add it to the gallery and create a thumbnail for it)
        MediaScannerConnection.scanFile(this,
                new String[] { outputPath }, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                    }
                });

        return outputPath;
    }

}
