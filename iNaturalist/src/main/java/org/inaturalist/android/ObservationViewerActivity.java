package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.service.chooser.ChooserTarget;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.cocosw.bottomsheet.BottomSheet;
import com.crashlytics.android.Crashlytics;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.maps.android.SphericalUtil;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.viewpagerindicator.CirclePageIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lucasr.twowayview.TwoWayView;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ObservationViewerActivity extends AppCompatActivity {
    private static final int NEW_ID_REQUEST_CODE = 0x101;
    private static final int REQUEST_CODE_LOGIN = 0x102;
    private static final int REQUEST_CODE_EDIT_OBSERVATION = 0x103;
    private static final int SHARE_REQUEST_CODE = 0x104;

    public static final int RESULT_FLAGGED_AS_CAPTIVE = 0x300;

    private static String TAG = "ObservationViewerActivity";

    public final static String SHOW_COMMENTS = "show_comments";
    public final static String SCROLL_TO_COMMENTS_BOTTOM = "scroll_to_comments_bottom";

    private static int DATA_QUALITY_CASUAL_GRADE = 0;
    private static int DATA_QUALITY_NEEDS_ID = 1;
    private static int DATA_QUALITY_RESEARCH_GRADE = 2;

    private static String QUALITY_GRADE_RESEARCH = "research";
    private static String QUALITY_GRADE_NEEDS_ID = "needs_id";
    private static String QUALITY_GRADE_CASUAL_GRADE = "casual";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private Observation mObservation;
    private boolean mFlagAsCaptive;

    private Uri mUri;
    private Cursor mCursor;

    private TextView mUserName;
    private ImageView mUserPic;
    private TextView mObservedOn;
    private ViewPager mPhotosViewPager;
    private CirclePageIndicator mIndicator;
    private ImageView mSharePhoto;
    private ImageView mIdPic;
    private TextView mIdName;
    private TextView mTaxonicName;
    private ViewGroup mIdRow;
    private JSONObject mTaxon;
    private TabHost mTabHost;

	private final static String VIEW_TYPE_INFO = "info";
	private final static String VIEW_TYPE_COMMENTS_IDS = "comments_ids";
	private final static String VIEW_TYPE_FAVS = "favs";
    private GoogleMap mMap;
    private ViewGroup mLocationMapContainer;
    private ImageView mUnknownLocationIcon;
    private TextView mLocationText;
    private ImageView mLocationPrivate;
    private TextView mCasualGradeText;
    private ImageView mCasualGradeIcon;
    private View mNeedsIdLine;
    private View mResearchGradeLine;
    private TextView mNeedsIdText;
    private ImageView mNeedsIdIcon;
    private TextView mResearchGradeText;
    private ImageView mResearchGradeIcon;
    private TextView mTipText;
    private ViewGroup mDataQualityReason;
    private TextView mIncludedInProjects;
    private ViewGroup mIncludedInProjectsContainer;
    private ProgressBar mLoadingPhotos;
    private ProgressBar mLoadingMap;

    private ObservationReceiver mObservationReceiver;

    private ArrayList<BetterJSONObject> mFavorites = null;
    private ArrayList<BetterJSONObject> mCommentsIds = null;
    private int mIdCount = 0;
    private ArrayList<Integer> mProjectIds;
    private ViewGroup mActivityTabContainer;
    private ViewGroup mInfoTabContainer;
    private ListView mCommentsIdsList;
    private ProgressBar mLoadingActivity;
    private CommentsIdsAdapter mAdapter;
    private ViewGroup mAddId;
    private ViewGroup mActivityButtons;
    private ViewGroup mAddComment;
    private ViewGroup mFavoritesTabContainer;
    private ProgressBar mLoadingFavs;
    private ListView mFavoritesList;
    private ViewGroup mAddFavorite;
    private FavoritesAdapter mFavoritesAdapter;
    private ViewGroup mRemoveFavorite;
    private int mFavIndex;
    private TextView mNoFavsMessage;
    private TextView mNoActivityMessage;
    private ViewGroup mNotesContainer;
    private TextView mNotes;
    private TextView mLoginToAddCommentId;
    private Button mActivitySignUp;
    private Button mActivityLogin;
    private ViewGroup mActivityLoginSignUpButtons;
    private TextView mLoginToAddFave;
    private Button mFavesSignUp;
    private Button mFavesLogin;
    private ViewGroup mFavesLoginSignUpButtons;
    private TextView mSyncToAddCommentsIds;
    private TextView mSyncToAddFave;
    private ImageView mIdPicBig;
    private ViewGroup mNoPhotosContainer;
    private ViewGroup mLocationLabelContainer;

    private PhotosViewPagerAdapter mPhotosAdapter = null;
    private ArrayList<BetterJSONObject> mProjects;
    private ImageView mIdArrow;
    private ViewGroup mUnknownLocationContainer;
    private boolean mReadOnly;
    private boolean mLoadingObservation;
    private String mObsJson;
    private boolean mShowComments;
    private int mCommentCount;
    private String mTaxonImage;
    private String mTaxonIdName;
    private String mTaxonName;
    private int mTaxonRankLevel;
    private String mActiveTab;
    private boolean mReloadObs;
    private ViewGroup mPhotosContainer;
    private boolean mReloadTaxon;
    private boolean mScrollToCommentsBottom;
    private ScrollView mScrollView;

    @Override
	protected void onStart() {
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop() {
		super.onStop();
		FlurryAgent.onEndSession(this);
	}


    private class PhotosViewPagerAdapter extends PagerAdapter {
        private Cursor mImageCursor = null;

        public PhotosViewPagerAdapter() {
            if (!mReadOnly) {
                if (mObservation.id != null) {
                    mImageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                            ObservationPhoto.PROJECTION,
                            "(_observation_id=? or observation_id=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                            new String[]{mObservation._id.toString(), mObservation.id.toString()},
                            ObservationPhoto.DEFAULT_SORT_ORDER);
                } else {
                    mImageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                            ObservationPhoto.PROJECTION,
                            "_observation_id=? and ((is_deleted = 0) OR (is_deleted IS NULL))",
                            new String[]{mObservation._id.toString()},
                            ObservationPhoto.DEFAULT_SORT_ORDER);
                }
                mImageCursor.moveToFirst();
            }
        }

        public Cursor getCursor() {
            return mImageCursor;
        }

        @Override
        public int getCount() {
            return mReadOnly ? mObservation.photos.size() : mImageCursor.getCount();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == (ImageView)object;
        }

        private Cursor findPhotoInStorage(Integer photoId) {
            Cursor imageCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.Images.ImageColumns.ORIENTATION},
                    MediaStore.MediaColumns._ID + " = " + photoId, null, null);

            imageCursor.moveToFirst();
            return imageCursor;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            if (!mReadOnly) mImageCursor.moveToPosition(position);

            ImageView imageView = new ImageView(ObservationViewerActivity.this);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            int imageId = 0;
            String photoFilename = null;
            String imageUrl = null;

            if (!mReadOnly) {
                imageId = mImageCursor.getInt(mImageCursor.getColumnIndexOrThrow(ObservationPhoto._ID));
                imageUrl = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                photoFilename = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
                if ((photoFilename != null) && (!(new File(photoFilename).exists()))) {
                    // Our local copy file was deleted (probably user deleted cache or similar) - try and use original filename from gallery
                    String originalPhotoFilename = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.ORIGINAL_PHOTO_FILENAME));
                    photoFilename = originalPhotoFilename;
                }
            } else {
                imageUrl = mObservation.photos.get(position).photo_url;
            }

            if (imageUrl != null) {
                // Online photo
            	imageView.setLayoutParams(new TwoWayView.LayoutParams(TwoWayView.LayoutParams.MATCH_PARENT, TwoWayView.LayoutParams.WRAP_CONTENT));

                Picasso.with(ObservationViewerActivity.this)
                        .load(imageUrl)
                        .fit()
                        .centerCrop()
                        .into(imageView, new Callback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onError() {
                                // Failed to load observation photo
                                try {
                                    JSONObject eventParams = new JSONObject();
                                    eventParams.put(AnalyticsClient.EVENT_PARAM_SIZE, AnalyticsClient.EVENT_PARAM_VALUE_MEDIUM);

                                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_PHOTO_FAILED_TO_LOAD, eventParams);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });

            } else {
                // Offline photo
                int newHeight = mPhotosViewPager.getMeasuredHeight();
                int newWidth = mPhotosViewPager.getMeasuredWidth();
                Bitmap bitmapImage = null;

                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = ImageUtils.calculateInSampleSize(options, newWidth, newHeight);

                    // Decode bitmap with inSampleSize set
                    options.inJustDecodeBounds = false;
                    // This decreases in-memory byte-storage per pixel
                    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                    bitmapImage = BitmapFactory.decodeFile(photoFilename, options);
                    bitmapImage = ImageUtils.rotateAccordingToOrientation(bitmapImage, photoFilename);
                    imageView.setImageBitmap(bitmapImage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            imageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ObservationViewerActivity.this, ObservationPhotosViewer.class);
                    intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, position);

                    if (!mReadOnly) {
                        intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObservation.id);
                        intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID_INTERNAL, mObservation._id);
                        intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, true);
                        intent.putExtra(ObservationPhotosViewer.READ_ONLY, true);
                    } else {
                        intent.putExtra(ObservationPhotosViewer.OBSERVATION, mObsJson);
                    }
                    startActivity(intent);
                }
            });

            ((ViewPager)container).addView(imageView, 0);
            return imageView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            ((ViewPager) container).removeView((ImageView) object);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadObservationIntoUI();
        refreshDataQuality();
        refreshProjectList();
        setupMap();
        resizeActivityList();
        resizeFavList();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.observation);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.observation_viewer);
        mHelper = new ActivityHelper(this);

        reloadObservation(savedInstanceState, false);

        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mUserName = (TextView) findViewById(R.id.user_name);
        mObservedOn = (TextView) findViewById(R.id.observed_on);
        mUserPic = (ImageView) findViewById(R.id.user_pic);
        mPhotosViewPager = (ViewPager) findViewById(R.id.photos);
        mIndicator = (CirclePageIndicator)findViewById(R.id.photos_indicator);
        mSharePhoto = (ImageView)findViewById(R.id.share_photo);
        mIdPic = (ImageView)findViewById(R.id.id_icon);
        mIdName = (TextView) findViewById(R.id.id_name);
        mTaxonicName = (TextView) findViewById(R.id.id_sub_name);
        mIdRow = (ViewGroup) findViewById(R.id.id_row);
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mMap = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.location_map)).getMap();
        mLocationMapContainer = (ViewGroup) findViewById(R.id.location_map_container);
        mUnknownLocationContainer = (ViewGroup) findViewById(R.id.unknown_location_container);
        mUnknownLocationIcon = (ImageView) findViewById(R.id.unknown_location);
        mLocationText = (TextView) findViewById(R.id.location_text);
        mLocationPrivate = (ImageView) findViewById(R.id.location_private);
        mCasualGradeText = (TextView) findViewById(R.id.casual_grade_text);
        mCasualGradeIcon = (ImageView) findViewById(R.id.casual_grade_icon);
        mNeedsIdLine = (View) findViewById(R.id.needs_id_line);
        mResearchGradeLine = (View) findViewById(R.id.research_grade_line);
        mNeedsIdText = (TextView) findViewById(R.id.needs_id_text);
        mNeedsIdIcon = (ImageView) findViewById(R.id.needs_id_icon);
        mResearchGradeText = (TextView) findViewById(R.id.research_grade_text);
        mResearchGradeIcon = (ImageView) findViewById(R.id.research_grade_icon);
        mTipText = (TextView) findViewById(R.id.tip_text);
        mDataQualityReason = (ViewGroup) findViewById(R.id.data_quality_reason);
        mIncludedInProjects = (TextView) findViewById(R.id.included_in_projects);
        mIncludedInProjectsContainer = (ViewGroup) findViewById(R.id.included_in_projects_container);
        mActivityTabContainer = (ViewGroup) findViewById(R.id.activity_tab_content);
        mInfoTabContainer = (ViewGroup) findViewById(R.id.info_tab_content);
        mLoadingActivity = (ProgressBar) findViewById(R.id.loading_activity);
        mCommentsIdsList = (ListView) findViewById(R.id.comment_id_list);
        mActivityButtons = (ViewGroup) findViewById(R.id.activity_buttons);
        mAddComment = (ViewGroup) findViewById(R.id.add_comment);
        mAddId = (ViewGroup) findViewById(R.id.add_id);
        mFavoritesTabContainer = (ViewGroup) findViewById(R.id.favorites_tab_content);
        mLoadingFavs = (ProgressBar) findViewById(R.id.loading_favorites);
        mFavoritesList = (ListView) findViewById(R.id.favorites_list);
        mAddFavorite = (ViewGroup) findViewById(R.id.add_favorite);
        mRemoveFavorite = (ViewGroup) findViewById(R.id.remove_favorite);
        mNoFavsMessage = (TextView) findViewById(R.id.no_favs);
        mNoActivityMessage = (TextView) findViewById(R.id.no_activity);
        mNotesContainer = (ViewGroup) findViewById(R.id.notes_container);
        mNotes = (TextView) findViewById(R.id.notes);
        mLoginToAddCommentId = (TextView) findViewById(R.id.login_to_add_comment_id);
        mActivitySignUp = (Button) findViewById(R.id.activity_sign_up);
        mActivityLogin = (Button) findViewById(R.id.activity_login);
        mActivityLoginSignUpButtons = (ViewGroup) findViewById(R.id.activity_login_signup);
        mLoginToAddFave = (TextView) findViewById(R.id.login_to_add_fave);
        mFavesSignUp = (Button) findViewById(R.id.faves_sign_up);
        mFavesLogin = (Button) findViewById(R.id.faves_login);
        mFavesLoginSignUpButtons = (ViewGroup) findViewById(R.id.faves_login_signup);
        mSyncToAddCommentsIds = (TextView) findViewById(R.id.sync_to_add_comments_ids);
        mSyncToAddFave = (TextView) findViewById(R.id.sync_to_add_fave);
        mNoPhotosContainer = (ViewGroup) findViewById(R.id.no_photos);
        mLocationLabelContainer = (ViewGroup) findViewById(R.id.location_label_container);
        mIdPicBig = (ImageView) findViewById(R.id.id_icon_big);
        mIdArrow = (ImageView) findViewById(R.id.id_arrow);
        mPhotosContainer = (ViewGroup) findViewById(R.id.photos_container);
        mLoadingPhotos = (ProgressBar) findViewById(R.id.loading_photos);
        mLoadingMap = (ProgressBar) findViewById(R.id.loading_map);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mPhotosContainer.getLayoutParams();
        params.height = (int) (display.getHeight() * 0.37);
        mPhotosContainer.setLayoutParams(params);

        View.OnClickListener onLogin = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ObservationViewerActivity.this, OnboardingActivity.class);
                intent.putExtra(OnboardingActivity.LOGIN, true);

                startActivityForResult(intent, REQUEST_CODE_LOGIN);
            }
        };
        View.OnClickListener onSignUp = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(ObservationViewerActivity.this, OnboardingActivity.class), REQUEST_CODE_LOGIN);
            }
        };

        mActivityLogin.setOnClickListener(onLogin);
		mActivitySignUp.setOnClickListener(onSignUp);
        mFavesLogin.setOnClickListener(onLogin);
        mFavesSignUp.setOnClickListener(onSignUp);

        mLocationPrivate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.alert(R.string.geoprivacy, R.string.geoprivacy_explanation);
            }
        });

        setupTabs();
        setupMap();
        refreshActivity();
        refreshFavorites();

        reloadPhotos();
        loadObservationIntoUI();
        getCommentIdList();
        refreshDataQuality();
    }

    private void reloadObservation(Bundle savedInstanceState, boolean forceReload) {

        Intent intent = getIntent();

		if (savedInstanceState == null) {
			// Do some setup based on the action being performed.
			Uri uri = intent.getData();
            mShowComments = intent.getBooleanExtra(SHOW_COMMENTS, false);
            mScrollToCommentsBottom = intent.getBooleanExtra(SCROLL_TO_COMMENTS_BOTTOM, false);
			if (uri == null) {
                String obsJson = intent.getStringExtra("observation");
                mReadOnly = intent.getBooleanExtra("read_only", false);
                mReloadObs = intent.getBooleanExtra("reload", false);
                mObsJson = obsJson;

                if (obsJson == null) {
                    Log.e(TAG, "Null URI from intent.getData");
                    finish();
                    return;
                }

                mObservation = new Observation(new BetterJSONObject(obsJson));
			}

			mUri = uri;
		} else {
            String obsUri = savedInstanceState.getString("mUri");
            if (obsUri != null) {
                mUri = Uri.parse(obsUri);
            } else {
                mUri = intent.getData();
            }

            mObservation = (Observation) savedInstanceState.getSerializable("mObservation");
            mIdCount = savedInstanceState.getInt("mIdCount");
            mCommentCount = savedInstanceState.getInt("mCommentCount");
            mReadOnly = savedInstanceState.getBoolean("mReadOnly");
            mReloadTaxon = savedInstanceState.getBoolean("mReloadTaxon");
            mObsJson = savedInstanceState.getString("mObsJson");
            mFlagAsCaptive = savedInstanceState.getBoolean("mFlagAsCaptive");
            mTaxonName = savedInstanceState.getString("mTaxonName");
            mTaxonRankLevel = savedInstanceState.getInt("mTaxonRankLevel");
            mTaxonIdName = savedInstanceState.getString("mTaxonIdName");
            mTaxonImage = savedInstanceState.getString("mTaxonImage");
            try {
                String taxonJson = savedInstanceState.getString("mTaxon");
                if (taxonJson != null) mTaxon = new JSONObject(savedInstanceState.getString("mTaxon"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mActiveTab = savedInstanceState.getString("mActiveTab");

            mCommentsIds = loadListFromBundle(savedInstanceState, "mCommentsIds");
            mFavorites = loadListFromBundle(savedInstanceState, "mFavorites");
            mProjects = loadListFromBundle(savedInstanceState, "mProjects");
        }

        if (mCursor == null) {
            if (!mReadOnly) mCursor = managedQuery(mUri, Observation.PROJECTION, null, null, null);
        } else {
            mCursor.requery();
        }

        if ((mObservation == null) || (forceReload)) {
            if (!mReadOnly) mObservation = new Observation(mCursor);
        }

    }

    private int getFavoritedByUsername(String username) {
        for (int i = 0; i < mFavorites.size(); i++) {
            BetterJSONObject currentFav = mFavorites.get(i);
            BetterJSONObject user = new BetterJSONObject(currentFav.getJSONObject("user"));

            if (user.getString("login").equals(username)) {
                // Current user has favorited this observation
                return i;
            }
        }

        return -1;
    }

    private void refreshFavorites() {
        SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        final String username = pref.getString("username", null);

        TabWidget tabWidget = mTabHost.getTabWidget();

        if ((mFavorites == null) || (mFavorites.size() == 0)) {
            ((TextView) tabWidget.getChildAt(2).findViewById(R.id.count)).setVisibility(View.GONE);
        } else {
            ((TextView) tabWidget.getChildAt(2).findViewById(R.id.count)).setVisibility(View.VISIBLE);
            ((TextView) tabWidget.getChildAt(2).findViewById(R.id.count)).setText(String.valueOf(mFavorites.size()));
        }

        if (username == null) {
            // Not logged in
            mAddFavorite.setVisibility(View.GONE);
            mLoginToAddFave.setVisibility(View.VISIBLE);
            mFavesLoginSignUpButtons.setVisibility(View.VISIBLE);
            mLoadingFavs.setVisibility(View.GONE);
            mFavoritesList.setVisibility(View.GONE);
            mNoFavsMessage.setVisibility(View.GONE);
            mSyncToAddFave.setVisibility(View.GONE);
            return;
        }

        if (mObservation.id == null) {
            // Observation not synced
            mSyncToAddFave.setVisibility(View.VISIBLE);
            mLoginToAddFave.setVisibility(View.GONE);
            mFavesLoginSignUpButtons.setVisibility(View.GONE);
            mLoadingFavs.setVisibility(View.GONE);
            mFavoritesList.setVisibility(View.GONE);
            mAddFavorite.setVisibility(View.GONE);
            mRemoveFavorite.setVisibility(View.GONE);
            mNoFavsMessage.setVisibility(View.GONE);
            return;
        }

        mSyncToAddFave.setVisibility(View.GONE);
        mLoginToAddFave.setVisibility(View.GONE);
        mFavesLoginSignUpButtons.setVisibility(View.GONE);

        if (mFavorites == null) {
            // Still loading
            mLoadingFavs.setVisibility(View.VISIBLE);
            mFavoritesList.setVisibility(View.GONE);
            mAddFavorite.setVisibility(View.GONE);
            mRemoveFavorite.setVisibility(View.GONE);
            mNoFavsMessage.setVisibility(View.GONE);
            return;
        }

        mLoadingFavs.setVisibility(View.GONE);
        mFavoritesList.setVisibility(View.VISIBLE);

        if (mFavorites.size() == 0) {
            mNoFavsMessage.setVisibility(View.VISIBLE);
        } else {
            mNoFavsMessage.setVisibility(View.GONE);
        }

        mFavIndex = getFavoritedByUsername(username);

        if (mFavIndex > -1) {
            // User has favorited the observation
            mAddFavorite.setVisibility(View.GONE);
            mRemoveFavorite.setVisibility(View.VISIBLE);
        } else {
            mAddFavorite.setVisibility(View.VISIBLE);
            mRemoveFavorite.setVisibility(View.GONE);
        }

        mFavoritesAdapter = new FavoritesAdapter(this, mFavorites);
        mFavoritesList.setAdapter(mFavoritesAdapter);

        mRemoveFavorite.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_UNFAVE);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_REMOVE_FAVORITE, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                startService(serviceIntent);

                mFavIndex = getFavoritedByUsername(username);

                if (mFavIndex > -1) mFavorites.remove(mFavIndex);
                mFavoritesAdapter.notifyDataSetChanged();

                mAddFavorite.setVisibility(View.VISIBLE);
                mRemoveFavorite.setVisibility(View.GONE);

                if (mFavorites.size() == 0) {
                    mNoFavsMessage.setVisibility(View.VISIBLE);
                } else {
                    mNoFavsMessage.setVisibility(View.GONE);
                }

            }
        });

        mAddFavorite.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_FAVE);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_FAVORITE, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                startService(serviceIntent);

                SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                String username = pref.getString("username", null);
                String userIconUrl = pref.getString("user_icon_url", null);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
                String dateStr = dateFormat.format(new Date());

                BetterJSONObject newFav = new BetterJSONObject(String.format(
                        "{ \"user\": { \"login\": \"%s\", \"user_icon_url\": \"%s\" }, \"created_at\": \"%s\" }",
                        username, userIconUrl, dateStr));
                mFavorites.add(newFav);
                mFavoritesAdapter.notifyDataSetChanged();

                mRemoveFavorite.setVisibility(View.VISIBLE);
                mAddFavorite.setVisibility(View.GONE);

                if (mFavorites.size() == 0) {
                    mNoFavsMessage.setVisibility(View.VISIBLE);
                } else {
                    mNoFavsMessage.setVisibility(View.GONE);
                }

            }
        });
    }

    private void refreshActivity() {
        SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String username = pref.getString("username", null);

        mLoadingPhotos.setVisibility(View.GONE);

        if (username == null) {
            // Not logged in
            mActivityButtons.setVisibility(View.GONE);
            mLoginToAddCommentId.setVisibility(View.VISIBLE);
            mActivityLoginSignUpButtons.setVisibility(View.VISIBLE);
            mLoadingActivity.setVisibility(View.GONE);
            mCommentsIdsList.setVisibility(View.GONE);
            mNoActivityMessage.setVisibility(View.GONE);
            mSyncToAddCommentsIds.setVisibility(View.GONE);
            return;
        }

        if (mObservation.id == null) {
            // Observation not synced
            mSyncToAddCommentsIds.setVisibility(View.VISIBLE);
            mLoginToAddCommentId.setVisibility(View.GONE);
            mActivityLoginSignUpButtons.setVisibility(View.GONE);
            return;
        }

        // Update observation comment/id count for signed in users observations
        mObservation.comments_count = mObservation.last_comments_count = mCommentCount;
        mObservation.identifications_count = mObservation.last_identifications_count = mIdCount;
        if (mObservation.getUri() != null) {
            ContentValues cv = mObservation.getContentValues();
            if (!((mObservation._synced_at == null) || ((mObservation._updated_at != null) && (mObservation._updated_at.after(mObservation._synced_at))))) {
                cv.put(Observation._SYNCED_AT, System.currentTimeMillis()); // No need to sync
            }
            getContentResolver().update(mObservation.getUri(), cv, null, null);
            Log.d(TAG, "ObservationViewerActivity - refreshActivity - update obs: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
        }
        mLoginToAddCommentId.setVisibility(View.GONE);
        mActivityLoginSignUpButtons.setVisibility(View.GONE);
        mSyncToAddCommentsIds.setVisibility(View.GONE);

        if (mCommentsIds == null) {
            // Still loading
            mLoadingActivity.setVisibility(View.VISIBLE);
            mCommentsIdsList.setVisibility(View.GONE);
            mActivityButtons.setVisibility(View.GONE);
            mNoActivityMessage.setVisibility(View.GONE);
            return;
        }

        mLoadingActivity.setVisibility(View.GONE);
        mCommentsIdsList.setVisibility(View.VISIBLE);
        mActivityButtons.setVisibility(View.VISIBLE);

        if (mCommentsIds.size() == 0) {
            mNoActivityMessage.setVisibility(View.VISIBLE);
        } else {
            mNoActivityMessage.setVisibility(View.GONE);
        }

        if (mScrollToCommentsBottom) {
            mScrollView.post(new Runnable() {
                @Override
                public void run() {
                    mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }

        mAdapter = new CommentsIdsAdapter(this, mObsJson != null ? new BetterJSONObject(mObsJson) : new BetterJSONObject(mObservation.toJSONObject()), mCommentsIds, mObservation.taxon_id == null ? 0 : mObservation.taxon_id , new CommentsIdsAdapter.OnIDAdded() {
            @Override
            public void onIdentificationAdded(BetterJSONObject taxon) {
                try {
                    // After calling the added ID API - we'll refresh the comment/ID list
                    IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                    BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                    Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_ID, null, ObservationViewerActivity.this, INaturalistService.class);
                    mReloadTaxon = true;
                    serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                    serviceIntent.putExtra(INaturalistService.TAXON_ID, taxon.getJSONObject("taxon").getInt("id"));
                    startService(serviceIntent);

                    try {
                        JSONObject eventParams = new JSONObject();
                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_VIEW_OBS_AGREE);

                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_ADD_ID, eventParams);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onIdentificationRemoved(BetterJSONObject taxon) {
                // After calling the remove API - we'll refresh the comment/ID list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_REMOVE_ID, null, ObservationViewerActivity.this, INaturalistService.class);
                mReloadTaxon = true;
                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_ID, taxon.getInt("id"));
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                startService(serviceIntent);
            }

            @Override
            public void onIdentificationUpdated(final BetterJSONObject id) {
                // Set up the input
                final EditText input = new EditText(ObservationViewerActivity.this);
                // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
                input.setText(id.getString("body"));
                input.setSelection(input.getText().length());

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        input.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 100);


                mHelper.confirm(R.string.update_id_description, input,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String body = input.getText().toString();

                                // After calling the update API - we'll refresh the comment/ID list
                                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_ID, null, ObservationViewerActivity.this, INaturalistService.class);
                                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_ID, id.getInt("id"));
                                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_BODY, body);
                                serviceIntent.putExtra(INaturalistService.TAXON_ID, id.getInt("taxon_id"));
                                startService(serviceIntent);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }

            @Override
            public void onIdentificationRestored(BetterJSONObject id) {
                // After calling the restore ID API - we'll refresh the comment/ID list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_RESTORE_ID, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_ID, id.getInt("id"));
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                startService(serviceIntent);
            }


            @Override
            public void onCommentRemoved(BetterJSONObject comment) {
                 // After calling the remove API - we'll refresh the comment/ID list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_COMMENT, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.COMMENT_ID, comment.getInt("id"));
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                startService(serviceIntent);
            }

            @Override
            public void onCommentUpdated(final BetterJSONObject comment) {
                // Set up the input
                final EditText input = new EditText(ObservationViewerActivity.this);
                // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
                input.setText(comment.getString("body"));
                input.setSelection(input.getText().length());

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        input.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 100);


                mHelper.confirm(R.string.update_comment, input,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String commentBody = input.getText().toString();

                                // After calling the update API - we'll refresh the comment/ID list
                                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_COMMENT, null, ObservationViewerActivity.this, INaturalistService.class);
                                serviceIntent.putExtra(INaturalistService.COMMENT_ID, comment.getInt("id"));
                                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                                serviceIntent.putExtra(INaturalistService.COMMENT_BODY, commentBody);
                                startService(serviceIntent);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }
        }, true, mReadOnly);
        mCommentsIdsList.setAdapter(mAdapter);

        mAddId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ObservationViewerActivity.this, IdentificationActivity.class);
                intent.putExtra(IdentificationActivity.SUGGEST_ID, true);
                intent.putExtra(IdentificationActivity.OBSERVATION_ID, mObservation.id);
                intent.putExtra(IdentificationActivity.OBSERVATION_ID_INTERNAL, mObservation._id);
                intent.putExtra(IdentificationActivity.OBSERVED_ON, mObservation.observed_on);
                intent.putExtra(IdentificationActivity.LONGITUDE, mObservation.longitude);
                intent.putExtra(IdentificationActivity.LATITUDE, mObservation.latitude);
                if (mObservation._id != null) {
                    if (((PhotosViewPagerAdapter)mPhotosViewPager.getAdapter()).getCount() > 0) {
                        Cursor imageCursor = ((PhotosViewPagerAdapter) mPhotosViewPager.getAdapter()).getCursor();

                        int pos = imageCursor.getPosition();
                        imageCursor.moveToFirst();
                        intent.putExtra(IdentificationActivity.OBS_PHOTO_FILENAME,
                                imageCursor.getString(imageCursor.getColumnIndex(ObservationPhoto.PHOTO_FILENAME)));
                        intent.putExtra(IdentificationActivity.OBS_PHOTO_URL,
                                imageCursor.getString(imageCursor.getColumnIndex(ObservationPhoto.PHOTO_URL)));
                        imageCursor.move(pos);
                    }
                } else {
                    if ((mObservation.photos != null) && (mObservation.photos.size() > 0)) {
                        intent.putExtra(IdentificationActivity.OBS_PHOTO_FILENAME, mObservation.photos.get(0).photo_filename);
                        intent.putExtra(IdentificationActivity.OBS_PHOTO_URL, mObservation.photos.get(0).photo_url);
                    }
                }
                intent.putExtra(IdentificationActivity.OBSERVATION, mObsJson);
                startActivityForResult(intent, NEW_ID_REQUEST_CODE);
            }
        });

        mAddComment.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                // Set up the input
                final EditText input = new EditText(ObservationViewerActivity.this);
                // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        input.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 100);


                mHelper.confirm(R.string.add_comment, input,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String comment = input.getText().toString();

                                // Add the comment
                                Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_COMMENT, null, ObservationViewerActivity.this, INaturalistService.class);
                                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                                serviceIntent.putExtra(INaturalistService.COMMENT_BODY, comment);
                                startService(serviceIntent);

                                mCommentsIds = null;
                                refreshActivity();

                                // Refresh the comment/id list
                                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }
        });
    }

    private void setupMap() {
        if (mMap == null) return;

        mMap.setMyLocationEnabled(false);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setAllGesturesEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(false);
        Double lat, lon;
        Integer acc;
        lat = mObservation.private_latitude == null ? mObservation.latitude : mObservation.private_latitude;
        lon = mObservation.private_longitude == null ? mObservation.longitude : mObservation.private_longitude;
        acc = mObservation.positional_accuracy;

        if (!mLoadingObservation) {
            mLoadingMap.setVisibility(View.GONE);
            mLocationLabelContainer.setVisibility(View.VISIBLE);
        }

        if (lat != null && lon != null) {
            mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    Intent intent = new Intent(ObservationViewerActivity.this, LocationDetailsActivity.class);
                    intent.putExtra(LocationDetailsActivity.OBSERVATION, mObservation);
                    intent.putExtra(LocationDetailsActivity.OBSERVATION_JSON, mObsJson);
                    intent.putExtra(LocationDetailsActivity.READ_ONLY, true);
                    startActivity(intent);
                }
            });

            // Add the marker
            mMap.clear();

            mHelper.addMapPosition(mMap, mObservation, mObsJson != null ? new BetterJSONObject(mObsJson) : null);

            mLocationMapContainer.setVisibility(View.VISIBLE);
            mUnknownLocationIcon.setVisibility(View.GONE);
            if (((mObservation.place_guess == null) || (mObservation.place_guess.length() == 0)) &&
                ((mObservation.private_place_guess == null) || (mObservation.private_place_guess.length() == 0))) {
                // No place guess - show coordinates instead
                if (acc == null) {
                    mLocationText.setText(String.format(getString(R.string.location_coords_no_acc),
                            String.format("%.3f...", lat),
                            String.format("%.3f...", lon)));
                } else {
                    mLocationText.setText(String.format(getString(R.string.location_coords),
                            String.format("%.3f...", lat),
                            String.format("%.3f...", lon),
                            acc > 999 ? ">1 km" : String.format("%dm", (int) acc)));
                }
            } else{
                mLocationText.setText((mObservation.place_guess != null) && (mObservation.place_guess.length() > 0) ?
                        mObservation.place_guess : mObservation.private_place_guess);
            }

            mLocationText.setGravity(View.TEXT_ALIGNMENT_TEXT_END);

            if ((mObservation.geoprivacy == null) || (mObservation.geoprivacy.equals("open"))) {
                mLocationPrivate.setVisibility(View.GONE);
            } else if (mObservation.geoprivacy.equals("private")) {
                mLocationPrivate.setVisibility(View.VISIBLE);
                mLocationPrivate.setImageResource(R.drawable.ic_visibility_off_black_24dp);
            } else if (mObservation.geoprivacy.equals("obscured")) {
                mLocationPrivate.setVisibility(View.VISIBLE);
                mLocationPrivate.setImageResource(R.drawable.ic_filter_tilt_shift_black_24dp);
            }

            mUnknownLocationContainer.setVisibility(View.GONE);
        } else {
            // Unknown location
            mLocationMapContainer.setVisibility(View.GONE);
            mUnknownLocationIcon.setVisibility(View.VISIBLE);
            mLocationText.setText(R.string.unable_to_acquire_location);
            mLocationText.setGravity(View.TEXT_ALIGNMENT_CENTER);
            mLocationPrivate.setVisibility(View.GONE);

            if (!mLoadingObservation) mUnknownLocationContainer.setVisibility(View.VISIBLE);
        }
    }

    private View createTabContent(int tabIconResource) {
        View view = LayoutInflater.from(this).inflate(R.layout.observation_viewer_tab, null);
        TextView countText = (TextView) view.findViewById(R.id.count);
        ImageView tabIcon = (ImageView) view.findViewById(R.id.tab_icon);

        tabIcon.setImageResource(tabIconResource);

        return view;
    }



    private void setupTabs() {
        mTabHost.setup();

        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_INFO).setIndicator(createTabContent(R.drawable.ic_info_black_48dp)));
        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_COMMENTS_IDS).setIndicator(createTabContent(R.drawable.ic_forum_black_48dp)));
        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_FAVS).setIndicator(createTabContent(R.drawable.ic_star_black_48dp)));

        mTabHost.getTabWidget().setDividerDrawable(null);

        if ((mActiveTab == null) && (mShowComments)) {
            mTabHost.setCurrentTab(1);
            refreshTabs(VIEW_TYPE_COMMENTS_IDS);
        } else {
            if (mActiveTab == null) {
                mTabHost.setCurrentTab(0);
                refreshTabs(VIEW_TYPE_INFO);
            } else {
                int i = 0;
                if (mActiveTab.equals(VIEW_TYPE_INFO)) i = 0;
                if (mActiveTab.equals(VIEW_TYPE_COMMENTS_IDS)) i = 1;
                if (mActiveTab.equals(VIEW_TYPE_FAVS)) i = 2;

                mTabHost.setCurrentTab(i);
                refreshTabs(mActiveTab);
            }
        }

        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tag) {
                refreshTabs(tag);

            }
        });

    }

    private void refreshTabs(String tag) {
        mActiveTab = tag;
        mInfoTabContainer.setVisibility(View.GONE);
        mActivityTabContainer.setVisibility(View.GONE);
        mFavoritesTabContainer.setVisibility(View.GONE);

        TabWidget tabWidget = mTabHost.getTabWidget();
        tabWidget.getChildAt(0).findViewById(R.id.bottom_line).setVisibility(View.GONE);
        tabWidget.getChildAt(1).findViewById(R.id.bottom_line).setVisibility(View.GONE);
        tabWidget.getChildAt(2).findViewById(R.id.bottom_line).setVisibility(View.GONE);

        ((ImageView)tabWidget.getChildAt(0).findViewById(R.id.tab_icon)).setColorFilter(Color.parseColor("#757575"));
        ((ImageView)tabWidget.getChildAt(1).findViewById(R.id.tab_icon)).setColorFilter(Color.parseColor("#757575"));
        ((ImageView)tabWidget.getChildAt(2).findViewById(R.id.tab_icon)).setColorFilter(Color.parseColor("#757575"));
        ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setTextColor(Color.parseColor("#757575"));

        int i = 0;
        if (tag.equals(VIEW_TYPE_INFO)) {
            mInfoTabContainer.setVisibility(View.VISIBLE);
            i = 0;
        } else if (tag.equals(VIEW_TYPE_COMMENTS_IDS)) {
            mActivityTabContainer.setVisibility(View.VISIBLE);
            i = 1;
        } else if (tag.equals(VIEW_TYPE_FAVS)) {
            mFavoritesTabContainer.setVisibility(View.VISIBLE);
            ((TextView)tabWidget.getChildAt(2).findViewById(R.id.count)).setTextColor(getResources().getColor(R.color.inatapptheme_color));
            i = 2;
        }

        tabWidget.getChildAt(i).findViewById(R.id.bottom_line).setVisibility(View.VISIBLE);
        ((ImageView)tabWidget.getChildAt(i).findViewById(R.id.tab_icon)).setColorFilter(getResources().getColor(R.color.inatapptheme_color));
    }

    private void addTab(TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(this));
        tabHost.addTab(tabSpec);
    }

    private void getCommentIdList() {
        if ((mObservation.id != null) && (mCommentsIds == null)) {
            BaseFragmentActivity.safeUnregisterReceiver(mObservationReceiver, this);
            mObservationReceiver = new ObservationReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            Log.i(TAG, "Registering ACTION_OBSERVATION_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            startService(serviceIntent);

            if (mReadOnly) {
                // Show loading progress bars for the photo and map
                mLoadingObservation = true;
                mLoadingPhotos.setVisibility(View.VISIBLE);
                mNoPhotosContainer.setVisibility(View.GONE);

                mLoadingMap.setVisibility(View.VISIBLE);
                mUnknownLocationContainer.setVisibility(View.GONE);
                mLocationLabelContainer.setVisibility(View.GONE);
            }
        }
    }

    private void refreshDataQuality() {
        int dataQuality = DATA_QUALITY_CASUAL_GRADE;
        int reasonText = 0;

        if (((mObservation.latitude == null) && (mObservation.longitude == null)) && ((mObservation.private_latitude == null) && (mObservation.private_longitude == null))) {
            // No location
            dataQuality = DATA_QUALITY_CASUAL_GRADE;
            reasonText = R.string.casual_grade_add_location;
        } else if (mObservation.observed_on == null) {
            // No observed on date
            dataQuality = DATA_QUALITY_CASUAL_GRADE;
            reasonText = R.string.casual_grade_add_date;
        } else if (((PhotosViewPagerAdapter)mPhotosViewPager.getAdapter()).getCount() == 0) {
            // No photos
            dataQuality = DATA_QUALITY_CASUAL_GRADE;
            reasonText = R.string.casual_grade_add_photo;
        } else if (mObservation.captive || mFlagAsCaptive) {
            // Captive
            dataQuality = DATA_QUALITY_CASUAL_GRADE;
            reasonText = R.string.casual_grade_captive;
        } else if (mIdCount <= 1) {
            dataQuality = DATA_QUALITY_NEEDS_ID;
            reasonText = R.string.needs_id_more_ids;
        } else {
            dataQuality = DATA_QUALITY_RESEARCH_GRADE;
        }
        if (mObservation.quality_grade != null) {
            int observedDataQuality = -1;
            if (mObservation.quality_grade.equals(QUALITY_GRADE_CASUAL_GRADE)) {
                observedDataQuality = DATA_QUALITY_CASUAL_GRADE;
            } else if (mObservation.quality_grade.equals(QUALITY_GRADE_NEEDS_ID)) {
                observedDataQuality = DATA_QUALITY_NEEDS_ID;
            } else if (mObservation.quality_grade.equals(QUALITY_GRADE_RESEARCH)) {
                observedDataQuality = DATA_QUALITY_RESEARCH_GRADE;
            }

            if ((observedDataQuality != dataQuality) && (observedDataQuality != -1)) {
                // This observation was synced and got a different data quality score - prefer
                // to use what the server deducted through analysis / more advanced algorithm
                dataQuality = observedDataQuality;
                // Remove the reasoning
                reasonText = 0;
            }
        }


        // TODO - "Observation is casual grade because the community voted that they cannot identify it from the photo."
        // TODO - "Observation needs finer identifications from the community to become "Research Grade" status.

        int gray = Color.parseColor("#CBCBCB");
        int green = Color.parseColor("#8DBA30");
        if (dataQuality == DATA_QUALITY_CASUAL_GRADE) {
            mNeedsIdLine.setBackgroundColor(gray);
            mResearchGradeLine.setBackgroundColor(gray);
            mNeedsIdText.setTextColor(gray);
            mResearchGradeText.setTextColor(gray);
            mNeedsIdIcon.setBackgroundResource(R.drawable.circular_border_thick_gray);
            mResearchGradeIcon.setBackgroundResource(R.drawable.circular_border_thick_gray);
            mNeedsIdIcon.setImageResource(R.drawable.transparent);
            mResearchGradeIcon.setImageResource(R.drawable.transparent);
        } else if (dataQuality == DATA_QUALITY_NEEDS_ID) {
            mNeedsIdLine.setBackgroundColor(green);
            mResearchGradeLine.setBackgroundColor(green);
            mNeedsIdText.setTextColor(green);
            mNeedsIdIcon.setBackgroundResource(R.drawable.circular_border_thick_green);
            mNeedsIdIcon.setImageResource(R.drawable.ic_done_black_24dp);
            mResearchGradeLine.setBackgroundColor(gray);
            mResearchGradeText.setTextColor(gray);
            mResearchGradeIcon.setBackgroundResource(R.drawable.circular_border_thick_gray);
            mResearchGradeIcon.setImageResource(R.drawable.transparent);
        } else {
            mNeedsIdLine.setBackgroundColor(green);
            mResearchGradeLine.setBackgroundColor(green);
            mNeedsIdText.setTextColor(green);
            mNeedsIdIcon.setBackgroundResource(R.drawable.circular_border_thick_green);
            mNeedsIdIcon.setImageResource(R.drawable.ic_done_black_24dp);
            mResearchGradeLine.setBackgroundColor(green);
            mResearchGradeText.setTextColor(green);
            mResearchGradeIcon.setBackgroundResource(R.drawable.circular_border_thick_green);
            mResearchGradeIcon.setImageResource(R.drawable.ic_done_black_24dp);
        }


        if ((reasonText != 0) && (!mReadOnly)) {
            mTipText.setText(Html.fromHtml(getString(reasonText)));
            mDataQualityReason.setVisibility(View.VISIBLE);
        } else {
            mDataQualityReason.setVisibility(View.GONE);
        }
    }


    private void loadObservationIntoUI() {
        String userIconUrl = null;

        if (mReadOnly) {
            if (mObsJson == null) {
                finish();
                return;
            }

            BetterJSONObject obs = new BetterJSONObject(mObsJson);
            JSONObject userObj = obs.getJSONObject("user");
            if (userObj != null) {
                userIconUrl = userObj.has("user_icon_url") && !userObj.isNull("user_icon_url") ? userObj.optString("user_icon_url", null) : null;
                if (userIconUrl == null) {
                    userIconUrl = userObj.has("icon_url") && !userObj.isNull("icon_url") ? userObj.optString("icon_url", null) : null;
                }
                mUserName.setText(userObj.optString("login"));
                mUserPic.setVisibility(View.VISIBLE);
                mUserName.setVisibility(View.VISIBLE);
            } else {
                mUserPic.setVisibility(View.INVISIBLE);
                mUserName.setVisibility(View.INVISIBLE);
            }
        } else {
            SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            String username = pref.getString("username", null);
            userIconUrl = pref.getString("user_icon_url", null);
            mUserName.setText(username);

            // Display the errors for the observation, if any
            TextView errorsDescription = (TextView) findViewById(R.id.errors);
            if (mObservation.id != null) {
                JSONArray errors = mApp.getErrorsForObservation(mObservation.id);

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
            } else {
                errorsDescription.setVisibility(View.GONE);
            }
        }

        if ((userIconUrl != null) && (userIconUrl.length() > 0)) {
            String extension = userIconUrl.substring(userIconUrl.lastIndexOf(".") + 1);
            userIconUrl = userIconUrl.substring(0, userIconUrl.lastIndexOf('/') + 1) + "medium." + extension;
        }

        if ((userIconUrl != null) && (userIconUrl.length() > 0)) {
            UrlImageViewHelper.setUrlDrawable(mUserPic, userIconUrl, R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) { }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    // Return a circular version of the profile picture
                    Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                    return ImageUtils.getCircleBitmap(centerCrop);
                }
            });
        } else {
            mUserPic.setImageResource(R.drawable.ic_account_circle_black_24dp);
        }


        if (mReadOnly) {
            if (mObsJson == null) return;

            BetterJSONObject obs = new BetterJSONObject(mObsJson);
            final JSONObject userObj = obs.getJSONObject("user");

            OnClickListener showUser = new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ObservationViewerActivity.this, UserProfile.class);
                    intent.putExtra("user", new BetterJSONObject(userObj));
                    startActivity(intent);
                }
            };

            mUserName.setOnClickListener(showUser);
            mUserPic.setOnClickListener(showUser);
        }

        mObservedOn.setText(formatObservedOn(mObservation.observed_on, mObservation.time_observed_at));

        if (mPhotosAdapter.getCount() <= 1) {
            mIndicator.setVisibility(View.GONE);
            mNoPhotosContainer.setVisibility(View.GONE);
            if (mPhotosAdapter.getCount() == 0) {
                // No photos at all
                mNoPhotosContainer.setVisibility(View.VISIBLE);
                mIdPicBig.setImageResource(TaxonUtils.observationIcon(mObservation.toJSONObject()));
            }
        } else {
            mIndicator.setVisibility(View.VISIBLE);
            mNoPhotosContainer.setVisibility(View.GONE);
        }

        mSharePhoto.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                final DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String inatNetwork = mApp.getInaturalistNetworkMember();
                        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
                        String obsUrl = inatHost + "/observations/" + mObservation.id;

                        switch (which) {
                            case R.id.share:
                                Intent shareIntent = new Intent();
                                shareIntent.setAction(Intent.ACTION_SEND);
                                shareIntent.setType("text/plain");
                                shareIntent.putExtra(Intent.EXTRA_TEXT, obsUrl);
                                startActivityForResult(shareIntent, SHARE_REQUEST_CODE);

                                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_SHARE_STARTED);
                                break;

                            case R.id.view_on_inat:
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setData(Uri.parse(obsUrl));
                                startActivity(i);
                                break;
                        }
                    }
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    PopupMenu popup = new PopupMenu(ObservationViewerActivity.this, mSharePhoto);
                    popup.getMenuInflater().inflate(R.menu.share_photo_menu, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(android.view.MenuItem menuItem) {
                            onClick.onClick(null, menuItem.getItemId());
                            return true;
                        }
                    });

                    popup.show();
                } else {
                    new BottomSheet.Builder(ObservationViewerActivity.this).sheet(R.menu.share_photo_menu).listener(onClick).show();
                }

            }
        });

        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTaxon == null) {
                    // No taxon - don't show the taxon details page
                    return;
                }

                Intent intent = new Intent(ObservationViewerActivity.this, TaxonActivity.class);
                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(mTaxon));
                intent.putExtra(TaxonActivity.OBSERVATION, mObsJson != null ? new BetterJSONObject(mObsJson) : new BetterJSONObject(mObservation.toJSONObject()));
                startActivity(intent);
            }
        };
        mIdRow.setOnClickListener(listener);
        mIdName.setOnClickListener(listener);
        mTaxonicName.setOnClickListener(listener);

        mIdPic.setImageResource(TaxonUtils.observationIcon(mObservation.toJSONObject()));
        if (mObservation.preferred_common_name != null && mObservation.preferred_common_name.length() > 0) {
            mIdName.setText(mObservation.preferred_common_name);
            mTaxonicName.setText(mObservation.preferred_common_name);
        } else {
            mIdName.setText(mObservation.species_guess);
            mTaxonicName.setText(mObservation.species_guess);
        }

        if (mObservation.id == null) {
            mSharePhoto.setVisibility(View.GONE);
        }


        if ((mObservation.taxon_id == null) && (mObservation.species_guess == null)) {
            mIdName.setText(R.string.unknown_species);
            mIdArrow.setVisibility(View.GONE);
        } else if (mObservation.taxon_id != null) {
            mIdArrow.setVisibility(View.VISIBLE);

            if ((mTaxonName == null) || (mTaxonIdName == null) || (mTaxonImage == null)) {
                downloadTaxon();
            } else {
                UrlImageViewHelper.setUrlDrawable(mIdPic, mTaxonImage);

                if ((mTaxonIdName == null) || (mTaxonIdName.length() == 0)) {
                    mIdName.setText(mTaxonName);
                    mTaxonicName.setText(mTaxonName);
                } else {
                    mIdName.setText(mTaxonIdName);
                    mTaxonicName.setText(mTaxonName);
                    mTaxonicName.setVisibility(View.VISIBLE);
                    if (mTaxonRankLevel <= 20) {
                        mTaxonicName.setTypeface(null, Typeface.ITALIC);
                    } else {
                        mTaxonicName.setTypeface(null, Typeface.NORMAL);
                    }
                }
            }
        }

        if (!mReadOnly) {
            // Get IDs of project-observations
            int obsId = (mObservation.id == null ? mObservation._id : mObservation.id);
            Cursor c = getContentResolver().query(ProjectObservation.CONTENT_URI, ProjectObservation.PROJECTION,
                    "(observation_id = " + obsId + ") AND ((is_deleted = 0) OR (is_deleted is NULL))",
                    null, ProjectObservation.DEFAULT_SORT_ORDER);
            mProjectIds = new ArrayList<Integer>();
            while (c.isAfterLast() == false) {
                ProjectObservation projectObservation = new ProjectObservation(c);
                mProjectIds.add(projectObservation.project_id);
                c.moveToNext();
            }
            c.close();

            mProjects = new ArrayList<BetterJSONObject>();
            for (int projectId : mProjectIds) {
                c = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION,
                        "(id = " + projectId + ")", null, Project.DEFAULT_SORT_ORDER);
                if (c.getCount() > 0) {
                    Project project = new Project(c);
                    BetterJSONObject projectJson = new BetterJSONObject();
                    projectJson.put("project", project.toJSONObject());
                    mProjects.add(projectJson);
                }
                c.close();
            }
        }

        refreshProjectList();

        mIncludedInProjectsContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ObservationViewerActivity.this, ObservationProjectsViewer.class);
                intent.putExtra(ObservationProjectsViewer.PROJECTS, mProjects);
                startActivity(intent);
            }
        });

        if ((mObservation.description != null) && (mObservation.description.trim().length() > 0)) {
            mNotesContainer.setVisibility(View.VISIBLE);
            mNotes.setText(mObservation.description);
        } else {
            mNotesContainer.setVisibility(View.GONE);
        }
    }

    private void downloadTaxon() {
        String inatNetwork = mApp.getInaturalistNetworkMember();
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage =   deviceLocale.getLanguage();
        final String idUrl = INaturalistService.API_HOST + "/taxa/" + mObservation.taxon_id + "?locale=" + deviceLanguage;

        // Download the taxon image URL
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject results = downloadJson(idUrl);

                if (results == null) return;

                final JSONObject taxon;
                try {
                    taxon = results.getJSONArray("results").getJSONObject(0);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                mTaxon = taxon;
                if (taxon != null) {
                    try {
                        JSONObject defaultPhoto = taxon.getJSONObject("default_photo");
                        final String imageUrl = defaultPhoto.getString("square_url");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mTaxonImage = imageUrl;
                                    UrlImageViewHelper.setUrlDrawable(mIdPic, mTaxonImage);

                                    mTaxonIdName = TaxonUtils.getTaxonName(ObservationViewerActivity.this, mTaxon);
                                    mTaxonName = taxon.getString("name");

                                    mIdName.setText(mTaxonIdName);
                                    mTaxonicName.setText(mTaxonName);
                                    mTaxonicName.setVisibility(View.VISIBLE);

                                    mTaxonRankLevel = taxon.getInt("rank_level");
                                    if (mTaxonRankLevel <= 20) {
                                        mTaxonicName.setTypeface(null, Typeface.ITALIC);
                                    } else {
                                        mTaxonicName.setTypeface(null, Typeface.NORMAL);
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private JSONObject downloadJson(String uri) {
        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            URL url = new URL(uri);
            conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }

        } catch (MalformedURLException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            return new JSONObject(jsonResults.toString());
        } catch (JSONException e) {
            return null;
        }

    }

    private String formatObservedOn(Timestamp date, Timestamp time) {
        StringBuilder format = new StringBuilder();

        if (date != null) {
            // Format the date part

            Calendar today = Calendar.getInstance();
            today.setTime(new Date());
            Calendar calDate = Calendar.getInstance();
            calDate.setTimeInMillis(date.getTime());

            String dateFormatString;
            if (today.get(Calendar.YEAR) > calDate.get(Calendar.YEAR)) {
                // Previous year(s)
                dateFormatString = "MM/dd/yy";
            } else {
                // Current year
                dateFormatString = "MMM d";
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString);
            format.append(dateFormat.format(date));
        }
        if (time != null) {
            // Format the time part
            if (date != null) {
                format.append(" • ");
            }

            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mma");
            format.append(timeFormat.format(time));
        }

        return format.toString();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            prepareToExit();
            return true;
        case R.id.edit_observation:
            Intent intent = new Intent(Intent.ACTION_EDIT, mUri, this, ObservationEditor.class);
            if (mTaxon != null) intent.putExtra(ObservationEditor.TAXON, mTaxon.toString());
            startActivityForResult(intent, REQUEST_CODE_EDIT_OBSERVATION);
            return true;
        case R.id.flag_captive:
            mFlagAsCaptive = !mFlagAsCaptive;
            refreshDataQuality();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        prepareToExit();
    }

    private void prepareToExit() {
        if (!mReadOnly || !mFlagAsCaptive) {
            finish();
            return;
        }

        // Ask the user if he really wants to mark observation as captive
        mHelper.confirm(getString(R.string.flag_as_captive), getString(R.string.are_you_sure_you_want_to_flag_as_captive),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Flag as captive
                        Intent serviceIntent = new Intent(INaturalistService.ACTION_FLAG_OBSERVATION_AS_CAPTIVE, null, ObservationViewerActivity.this, INaturalistService.class);
                        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                        startService(serviceIntent);

                        Toast.makeText(getApplicationContext(), R.string.observation_flagged_as_captive, Toast.LENGTH_LONG).show();
                        setResult(RESULT_FLAGGED_AS_CAPTIVE);
                        finish();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }, R.string.yes, R.string.no);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(mReadOnly ? R.menu.observation_viewer_read_only_menu : R.menu.observation_viewer_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mReadOnly) {
            menu.findItem(R.id.flag_captive).setChecked(mFlagAsCaptive);
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mObservation", mObservation);
        outState.putInt("mIdCount", mIdCount);
        outState.putInt("mCommentCount", mCommentCount);
        outState.putBoolean("mReadOnly", mReadOnly);
        outState.putBoolean("mReloadTaxon", mReloadTaxon);
        outState.putString("mObsJson", mObsJson);
        outState.putBoolean("mFlagAsCaptive", mFlagAsCaptive);

        outState.putString("mTaxonIdName", mTaxonIdName);
        outState.putString("mTaxonName", mTaxonName);
        outState.putInt("mTaxonRankLevel", mTaxonRankLevel);
        outState.putString("mTaxonImage", mTaxonImage);
        outState.putString("mTaxon", mTaxon != null ? mTaxon.toString() : null);

        outState.putString("mActiveTab", mActiveTab);

        saveListToBundle(outState, mCommentsIds, "mCommentsIds");
        saveListToBundle(outState, mFavorites, "mFavorites");
        saveListToBundle(outState, mProjects, "mProjects");

        super.onSaveInstanceState(outState);
    }

    private void saveListToBundle(Bundle outState, ArrayList<BetterJSONObject> list, String key) {
        if (list != null) {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                arr.put(list.get(i).getJSONObject().toString());
            }
            outState.putString(key, arr.toString());
        }
    }

    private ArrayList<BetterJSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();

        String obsString = savedInstanceState.getString(key);
        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(new BetterJSONObject(arr.getString(i)));
                }

                return results;
            } catch (JSONException exc) {
                exc.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    private class ObservationReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "ObservationReceiver - OBSERVATION_RESULT");

            BaseFragmentActivity.safeUnregisterReceiver(mObservationReceiver, ObservationViewerActivity.this);

            mLoadingObservation = false;

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            Observation observation;
            if (isSharedOnApp) {
                observation = (Observation) mApp.getServiceResult(INaturalistService.ACTION_OBSERVATION_RESULT);
            } else {
                observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);
            }

            if (mObservation == null) {
                reloadObservation(null, false);
                mObsJson = null;
            }

	        if (observation == null) {
	            // Couldn't retrieve observation details (probably deleted)
	            mCommentsIds = new ArrayList<BetterJSONObject>();
                mFavorites = new ArrayList<BetterJSONObject>();
	            refreshActivity();
                refreshFavorites();
	            return;
	        }

            JSONArray projects = observation.projects.getJSONArray();
	        JSONArray comments = observation.comments.getJSONArray();
	        JSONArray ids = observation.identifications.getJSONArray();
            JSONArray favs = observation.favorites.getJSONArray();
	        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> favResults = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> projectResults = new ArrayList<BetterJSONObject>();

            mIdCount = 0;
            mCommentCount = 0;

	        try {
                for (int i = 0; i < projects.length(); i++) {
	                BetterJSONObject project = new BetterJSONObject(projects.getJSONObject(i));
	                projectResults.add(project);
	            }
	            for (int i = 0; i < comments.length(); i++) {
	                BetterJSONObject comment = new BetterJSONObject(comments.getJSONObject(i));
	                comment.put("type", "comment");
	                results.add(comment);
                    mCommentCount++;
	            }
	            for (int i = 0; i < ids.length(); i++) {
	                BetterJSONObject id = new BetterJSONObject(ids.getJSONObject(i));
	                id.put("type", "identification");
	                results.add(id);
                    mIdCount++;
	            }
	            for (int i = 0; i < favs.length(); i++) {
	                BetterJSONObject fav = new BetterJSONObject(favs.getJSONObject(i));
	                favResults.add(fav);
	            }
	        } catch (JSONException e) {
	            e.printStackTrace();
	        }

            Comparator<BetterJSONObject> comp = new Comparator<BetterJSONObject>() {
                @Override
                public int compare(BetterJSONObject lhs, BetterJSONObject rhs) {
                    Timestamp date1 = lhs.getTimestamp("created_at");
                    Timestamp date2 = rhs.getTimestamp("created_at");
                    return date1.compareTo(date2);
                }
            };
	        Collections.sort(results, comp);
            Collections.sort(favResults, comp);

	        mCommentsIds = results;
            mFavorites = favResults;
            mProjects = projectResults;

            if (mReloadObs) {
                // Reload entire observation details (not just the comments/favs)
                mObservation = observation;

                if (isSharedOnApp) {
                    mObsJson = (String) mApp.getServiceResult(INaturalistService.OBSERVATION_JSON_RESULT);
                } else {
                    mObsJson = intent.getStringExtra(INaturalistService.OBSERVATION_JSON_RESULT);
                }
            }

            if (mReloadTaxon) {
                // Reload just the taxon part, if changed
                if (((mObservation.taxon_id == null) && (observation.taxon_id != null)) ||
                    ((mObservation.taxon_id != null) && (observation.taxon_id == null)) ||
                    (mObservation.taxon_id != observation.taxon_id)) {

                    Log.d(TAG, "ObservationViewerActivity - ObservationReceiver: Updated taxon: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
                    Log.d(TAG, "ObservationViewerActivity - ObservationReceiver: Updated taxon (new): " + observation.id + ":" + observation.preferred_common_name + ":" + observation.taxon_id);

                    mObservation.species_guess = observation.species_guess;
                    mObservation.taxon_id = observation.taxon_id;
                    mObservation.preferred_common_name = observation.preferred_common_name;
                    mObservation.iconic_taxon_name = observation.iconic_taxon_name;

                    mTaxonName = null;
                    mTaxonIdName = null;
                    mTaxonImage = null;
                }

                mReloadTaxon = false;

                if (!mReadOnly) {
                    // Update observation's taxon in DB
                    ContentValues cv = mObservation.getContentValues();
                    getContentResolver().update(mUri, cv, null, null);
                    Log.d(TAG, "ObservationViewerActivity - ObservationReceiver - update obs: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
                }
            }

            reloadPhotos();
            loadObservationIntoUI();
            setupMap();
            refreshActivity();
            refreshFavorites();
            resizeActivityList();
            resizeFavList();
            refreshProjectList();
            refreshDataQuality();
	    }

	}

    private void reloadPhotos() {
        mLoadingPhotos.setVisibility(View.GONE);
        mPhotosAdapter = new PhotosViewPagerAdapter();
        mPhotosViewPager.setAdapter(mPhotosAdapter);
        mIndicator.setViewPager(mPhotosViewPager);
    }

    private void resizeFavList() {
        final Handler handler = new Handler();
        if ((mFavoritesTabContainer.getVisibility() == View.VISIBLE) && (mFavoritesList.getVisibility() == View.VISIBLE) && (mFavoritesList.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeFavList();
                }
            }, 100);

            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                setListViewHeightBasedOnItems(mFavoritesList);
            }
        }, 100);
    }

    private void resizeActivityList() {
        final Handler handler = new Handler();
        if ((mCommentsIdsList.getVisibility() == View.VISIBLE) && (mActivityTabContainer.getVisibility() == View.VISIBLE) && (mCommentsIdsList.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeActivityList();
                }
            }, 100);

            return;
        }

        mCommentsIdsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                int height = setListViewHeightBasedOnItems(mCommentsIdsList);
                View background = findViewById(R.id.comment_id_list_background);
                ViewGroup.LayoutParams params = background.getLayoutParams();
                if (params.height != height) {
                    params.height = height;
                    background.requestLayout();
                }

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SHARE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // TODO - RESULT_OK is never returned + need to add "destination" param (what type of share was performed)
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_SHARE_FINISHED);
            } else {
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_SHARE_CANCELLED);
            }
        } else if (requestCode == REQUEST_CODE_EDIT_OBSERVATION) {
            if ((resultCode == ObservationEditor.RESULT_DELETED) || (resultCode == ObservationEditor.RESULT_RETURN_TO_OBSERVATION_LIST)) {
                // User deleted the observation (or did a batch-edit)
                finish();
                return;
            } else if (resultCode == ObservationEditor.RESULT_REFRESH_OBS) {
                // User made changes to observation - refresh the view

                reloadObservation(null, true);

                reloadPhotos();
                loadObservationIntoUI();
                getCommentIdList();
                setupMap();
                refreshActivity();
                refreshFavorites();
                resizeActivityList();
                resizeFavList();
                refreshProjectList();
                refreshDataQuality();

            }
        } if (requestCode == NEW_ID_REQUEST_CODE) {
    		if (resultCode == RESULT_OK) {
    			// Add the ID
    			Integer taxonId = data.getIntExtra(IdentificationActivity.TAXON_ID, 0);
    			String idRemarks = data.getStringExtra(IdentificationActivity.ID_REMARKS);

    			Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_IDENTIFICATION, null, this, INaturalistService.class);
    			serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
    			serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
    			serviceIntent.putExtra(INaturalistService.IDENTIFICATION_BODY, idRemarks);
    			startService(serviceIntent);

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_VIEW_OBS_ADD);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_ADD_ID, eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }


    			// Show a loading progress until the new comments/IDs are loaded
    			mCommentsIds = null;
    			refreshActivity();

    			// Refresh the comment/id list
                mReloadTaxon = true;
    			IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
    			BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, this);

    		}
    	} else if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // Show a loading progress until the new comments/IDs are loaded
            mCommentsIds = null;
            mFavorites = null;
            refreshActivity();
            refreshFavorites();

            // Refresh the comment/id list
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, this);
            Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
            serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            startService(serviceIntent2);
        }
    }

    private void refreshProjectList() {
        if ((mProjects != null) && (mProjects.size() > 0)) {
            mIncludedInProjectsContainer.setVisibility(View.VISIBLE);
            int count = mProjects.size();
            mIncludedInProjects.setText(String.format(getString(count > 1 ? R.string.included_in_projects : R.string.included_in_projects_singular), count));
        } else {
            mIncludedInProjectsContainer.setVisibility(View.GONE);
        }
    }


    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param listView to be resized
     */
    public int setListViewHeightBasedOnItems(final ListView listView) {
    	ListAdapter listAdapter = listView.getAdapter();
    	if (listAdapter != null) {

            int numberOfItems = listAdapter.getCount();

            // Get total height of all items.
            int totalItemsHeight = 0;
            for (int itemPos = 0; itemPos < numberOfItems; itemPos++) {
                View item = listAdapter.getView(itemPos, null, listView);
                item.measure(MeasureSpec.makeMeasureSpec(listView.getWidth(), MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                totalItemsHeight += item.getMeasuredHeight();
            }

            // Get total height of all item dividers.
            int totalDividersHeight = listView.getDividerHeight() *
                    (numberOfItems - 1);

            // Set list height.
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            int paddingHeight = (int) getResources().getDimension(R.dimen.actionbar_height);
            int newHeight = totalItemsHeight + totalDividersHeight;
            if (params.height != newHeight) {
                params.height = totalItemsHeight + totalDividersHeight;
                listView.setLayoutParams(params);
                listView.requestLayout();
            }

    		return params.height;

    	} else {
    		return 0;
    	}
    }


    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mObservationReceiver, this);
    }

}
