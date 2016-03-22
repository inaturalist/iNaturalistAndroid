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
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.cocosw.bottomsheet.BottomSheet;
import com.crashlytics.android.Crashlytics;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
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

public class ObservationViewerActivity extends SherlockFragmentActivity {
    private static final int NEW_ID_REQUEST_CODE = 0x101;
    private static final int REQUEST_CODE_LOGIN = 0x102;
    private static final int REQUEST_CODE_EDIT_OBSERVATION = 0x103;

    public static final int RESULT_FLAGGED_AS_CAPTIVE = 0x300;

    private static String TAG = "ObservationViewerActivity";

    public final static String SHOW_COMMENTS = "show_comments";

    private static int DATA_QUALITY_CASUAL_GRADE = 0;
    private static int DATA_QUALITY_NEEDS_ID = 1;
    private static int DATA_QUALITY_RESEARCH_GRADE = 2;

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

    private PhotosViewPagerAdapter mPhotosAdapter;
    private ArrayList<BetterJSONObject> mProjects;
    private ImageView mIdArrow;
    private ViewGroup mUnknownLocationContainer;
    private boolean mReadOnly;
    private String mObsJson;
    private boolean mShowComments;
    private int mCommentCount;

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
                            "_observation_id=? or observation_id=?",
                            new String[]{mObservation._id.toString(), mObservation.id.toString()},
                            ObservationPhoto.DEFAULT_SORT_ORDER);
                } else {
                    mImageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                            ObservationPhoto.PROJECTION,
                            "_observation_id=?",
                            new String[]{mObservation._id.toString()},
                            ObservationPhoto.DEFAULT_SORT_ORDER);
                }
                mImageCursor.moveToFirst();
            }
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
            int photoId = 0;
            String imageUrl = null;

            if (!mReadOnly) {
                photoId = mImageCursor.getInt(mImageCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
                imageId = mImageCursor.getInt(mImageCursor.getColumnIndexOrThrow(ObservationPhoto._ID));
                imageUrl = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
            } else {
                imageUrl = mObservation.photos.get(position).photo_url;
            }

            if (imageUrl != null) {
                // Online photo
            	imageView.setLayoutParams(new TwoWayView.LayoutParams(TwoWayView.LayoutParams.MATCH_PARENT, TwoWayView.LayoutParams.WRAP_CONTENT));
                UrlImageViewHelper.setUrlDrawable(imageView, imageUrl);
            } else {
                // Offline photo
                Cursor pc = findPhotoInStorage(photoId);
                if (pc.getCount() == 0) {
                    // photo has been deleted, delete the corresponding db row
                    getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_id = " + imageId, null);
                } else {
                    int orientation = pc.getInt(pc.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION));

                    Bitmap bitmapImage = null;
                    try {
                        MediaStore.Images.Thumbnails.getThumbnail(
                                getContentResolver(),
                                photoId,
                                MediaStore.Images.Thumbnails.MINI_KIND,
                                (BitmapFactory.Options) null);
                    } catch (Exception exc) {
                        // In case of unsupported thumbnail kind
                    }

                    int newHeight = mPhotosViewPager.getMeasuredHeight();
                    int newWidth = mPhotosViewPager.getMeasuredWidth();

                    if (bitmapImage == null) {
                        // Couldn't retrieve the thumbnail - get the original image
                        try {
                            bitmapImage = ImageUtils.decodeSampledBitmapFromUri(getContentResolver(),
                                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId),
                                    newWidth, newHeight);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (bitmapImage != null) {
                       try {
                            if (orientation != 0) {
                                int height = bitmapImage.getHeight();
                                int width = bitmapImage.getWidth();
                                Matrix matrix = new Matrix();
                                matrix.setRotate((float) orientation, width, height);
                                bitmapImage = Bitmap.createBitmap(bitmapImage, 0, 0, width, height, matrix, true);
                            }
                            imageView.setImageBitmap(bitmapImage);
                        } catch (OutOfMemoryError exception) {
                            // Nothing we can do in this case...
                            Crashlytics.logException(exception);
                        }
                    }
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
        reloadObservation(null, true);
        loadObservationIntoUI();
        refreshDataQuality();
        refreshProjectList();
        setupMap();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));
        actionBar.setLogo(R.drawable.ic_arrow_back_gray_24dp);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.observation);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.observation_viewer);
        mHelper = new ActivityHelper(this);

        reloadObservation(savedInstanceState, false);

        mTaxon = null;

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
        mIdPicBig = (ImageView) findViewById(R.id.id_icon_big);
        mIdArrow = (ImageView) findViewById(R.id.id_arrow);

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

        loadObservationIntoUI();
        refreshDataQuality();
    }

    private void reloadObservation(Bundle savedInstanceState, boolean forceReload) {

        Intent intent = getIntent();

		if (savedInstanceState == null) {
			// Do some setup based on the action being performed.
			Uri uri = intent.getData();
            mShowComments = intent.getBooleanExtra(SHOW_COMMENTS, false);
			if (uri == null) {
                String obsJson = intent.getStringExtra("observation");
                mReadOnly = intent.getBooleanExtra("read_only", false);
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
            mObsJson = savedInstanceState.getString("mObsJson");
            mFlagAsCaptive = savedInstanceState.getBoolean("mFlagAsCaptive");
		}

        if (mCursor == null) {
            if (!mReadOnly) mCursor = managedQuery(mUri, Observation.PROJECTION, null, null, null);
        } else {
            mCursor.requery();
        }

        if ((mObservation == null) || (forceReload)) {
            if (!mReadOnly) mObservation = new Observation(mCursor);
        }

        if (mReadOnly) {
            // The content description used to locate the overflow button
            final String overflowDesc = getString(R.string.overflow_menu);
            // The top-level window
            final ViewGroup decor = (ViewGroup) getWindow().getDecorView();
            // Wait a moment to ensure the overflow button can be located
            decor.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // The List that contains the matching views
                    final ArrayList<View> outViews = new ArrayList<>();
                    // Traverse the view-hierarchy and locate the overflow button
                    findViewsWithText(outViews, decor, overflowDesc);
                    // Guard against any errors
                    if (outViews.isEmpty()) {
                        return;
                    }
                    // Do something with the view
                    final ImageButton overflow = (ImageButton) outViews.get(0);
                    overflow.setImageResource(R.drawable.ic_more_vert_black_24dp);
                }

            }, 1000);
        }
    }

    static void findViewsWithText(List<View> outViews, ViewGroup parent, String targetDescription) {
        if (parent == null || TextUtils.isEmpty(targetDescription)) {
            return;
        }
        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = parent.getChildAt(i);
            final CharSequence desc = child.getContentDescription();
            if (!TextUtils.isEmpty(desc) && targetDescription.equals(desc.toString())) {
                outViews.add(child);
            } else if (child instanceof ViewGroup && child.getVisibility() == View.VISIBLE) {
                findViewsWithText(outViews, (ViewGroup) child, targetDescription);
            }
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
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis()); // No need to sync
            getContentResolver().update(mObservation.getUri(), cv, null, null);
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

        mAdapter = new CommentsIdsAdapter(this, mCommentsIds, mObservation.taxon_id == null ? 0 : mObservation.taxon_id , new CommentsIdsAdapter.OnIDAdded() {
            @Override
            public void onIdentificationAdded(BetterJSONObject taxon) {
                try {
                    // After calling the added ID API - we'll refresh the comment/ID list
                    IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                    registerReceiver(mObservationReceiver, filter);

                    Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_ID, null, ObservationViewerActivity.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                    serviceIntent.putExtra(INaturalistService.TAXON_ID, taxon.getJSONObject("taxon").getInt("id"));
                    startService(serviceIntent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onIdentificationRemoved(BetterJSONObject taxon) {
                // After calling the remove API - we'll refresh the comment/ID list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                registerReceiver(mObservationReceiver, filter);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_REMOVE_ID, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_ID, taxon.getInt("id"));
                startService(serviceIntent);
            }

            @Override
            public void onCommentRemoved(BetterJSONObject comment) {
                 // After calling the remove API - we'll refresh the comment/ID list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                registerReceiver(mObservationReceiver, filter);

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

                mHelper.confirm(R.string.update_comment, input,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String commentBody = input.getText().toString();

                                // After calling the update API - we'll refresh the comment/ID list
                                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                                registerReceiver(mObservationReceiver, filter);

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
        }, true);
        mCommentsIdsList.setAdapter(mAdapter);

        mAddId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ObservationViewerActivity.this, IdentificationActivity.class);
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

                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                // Refresh the comment/id list
                                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                                registerReceiver(mObservationReceiver, filter);
                                Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationViewerActivity.this, INaturalistService.class);
                                serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                                startService(serviceIntent2);
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
        mMap.setMyLocationEnabled(false);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setAllGesturesEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(false);
        Double lat, lon;
        Integer acc;
        lat = mObservation.private_latitude == null ? mObservation.latitude : mObservation.private_latitude;
        lon = mObservation.private_longitude == null ? mObservation.longitude : mObservation.private_longitude;
        acc = mObservation.positional_accuracy;

        if (lat != null && lon != null) {
            mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    Intent intent = new Intent(ObservationViewerActivity.this, LocationDetailsActivity.class);
                    intent.putExtra(LocationDetailsActivity.OBSERVATION, mObservation);
                    startActivity(intent);
                }
            });

            LatLng latLng = new LatLng(lat, lon);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

            // Add the marker
            mMap.clear();
            MarkerOptions opts = new MarkerOptions().position(latLng).icon(INaturalistMapActivity.observationIcon(mObservation.iconic_taxon_name));
            Marker m = mMap.addMarker(opts);

            mLocationMapContainer.setVisibility(View.VISIBLE);
            mUnknownLocationIcon.setVisibility(View.GONE);
            if ((mObservation.place_guess == null) || (mObservation.place_guess.length() == 0)) {
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
                mLocationText.setText(mObservation.place_guess);
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
            mUnknownLocationContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setupTabs() {
        mTabHost.setup();

        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_INFO).setIndicator("", getResources().getDrawable(R.drawable.ic_info_tab)));
        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_COMMENTS_IDS).setIndicator("", getResources().getDrawable(R.drawable.ic_forum_tab)));
        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_FAVS).setIndicator("", getResources().getDrawable(R.drawable.ic_star_tab)));

        mTabHost.getTabWidget().setDividerDrawable(null);

        mTabHost.getTabWidget().getChildAt(0).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(1).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(2).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));

        if (mShowComments) {
            mInfoTabContainer.setVisibility(View.GONE);
            mActivityTabContainer.setVisibility(View.VISIBLE);
            mFavoritesTabContainer.setVisibility(View.GONE);
            mTabHost.setCurrentTab(1);
        } else {
            mInfoTabContainer.setVisibility(View.VISIBLE);
            mActivityTabContainer.setVisibility(View.GONE);
            mFavoritesTabContainer.setVisibility(View.GONE);
        }

        mTabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tag) {
                mInfoTabContainer.setVisibility(View.GONE);
                mActivityTabContainer.setVisibility(View.GONE);
                mFavoritesTabContainer.setVisibility(View.GONE);

                if (tag.equals(VIEW_TYPE_INFO)) {
                    mInfoTabContainer.setVisibility(View.VISIBLE);
                } else if (tag.equals(VIEW_TYPE_COMMENTS_IDS)) {
                    mActivityTabContainer.setVisibility(View.VISIBLE);
                } else if (tag.equals(VIEW_TYPE_FAVS)) {
                    mFavoritesTabContainer.setVisibility(View.VISIBLE);
                }
            }
        });

    }

    private void addTab(TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(this));
        tabHost.addTab(tabSpec);
    }

    private void getCommentIdList() {
        if (mObservation.id != null) {
            mObservationReceiver = new ObservationReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            Log.i(TAG, "Registering ACTION_OBSERVATION_RESULT");
            registerReceiver(mObservationReceiver, filter);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            startService(serviceIntent);
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
        } else if ((mObservation.id_please) && (mIdCount <= 1)) {
            dataQuality = DATA_QUALITY_NEEDS_ID;
            reasonText = R.string.needs_id_more_ids;
        } else {
            dataQuality = DATA_QUALITY_RESEARCH_GRADE;
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


        if (reasonText != 0) {
            mTipText.setText(Html.fromHtml(getString(reasonText)));
            mDataQualityReason.setVisibility(View.VISIBLE);
        } else {
            mDataQualityReason.setVisibility(View.GONE);
        }
    }


    private void loadObservationIntoUI() {
        String userIconUrl = null;

        if (mReadOnly) {
            BetterJSONObject obs = new BetterJSONObject(mObsJson);
            JSONObject userObj = obs.getJSONObject("user");
            userIconUrl = userObj.has("user_icon_url") && !userObj.isNull("user_icon_url") ? userObj.optString("user_icon_url", null) : null;
            mUserName.setText(userObj.optString("login"));
        } else {
            SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            String username = pref.getString("username", null);
            userIconUrl = pref.getString("user_icon_url", null);
            mUserName.setText(username);
        }

        if (userIconUrl != null) {
            UrlImageViewHelper.setUrlDrawable(mUserPic, userIconUrl, new UrlImageViewCallback() {
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

        mObservedOn.setText(formatObservedOn(mObservation.observed_on, mObservation.time_observed_at));

        mPhotosAdapter = new PhotosViewPagerAdapter();
        mPhotosViewPager.setAdapter(mPhotosAdapter);
        mIndicator.setViewPager(mPhotosViewPager);

        if (mPhotosAdapter.getCount() <= 1) {
            mIndicator.setVisibility(View.GONE);
            mNoPhotosContainer.setVisibility(View.GONE);
            if (mPhotosAdapter.getCount() == 0) {
                // No photos at all
                mNoPhotosContainer.setVisibility(View.VISIBLE);
                mIdPicBig.setImageResource(ObservationPhotosViewer.observationIcon(mObservation.toJSONObject()));
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
                        String obsUrl = "http://" + inatHost + "/observations/" + mObservation.id;

                        switch (which) {
                            case R.id.share:
                                Intent shareIntent = new Intent();
                                shareIntent.setAction(Intent.ACTION_SEND);
                                shareIntent.setType("text/plain");
                                shareIntent.putExtra(Intent.EXTRA_TEXT, obsUrl);
                                startActivity(shareIntent);
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

        mIdRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTaxon == null) {
                    // No taxon - don't show the taxon details page
                    return;
                }

                Intent intent = new Intent(ObservationViewerActivity.this, GuideTaxonActivity.class);
                intent.putExtra("taxon", new BetterJSONObject(mTaxon));
                intent.putExtra("guide_taxon", false);
                intent.putExtra("show_add", false);
                startActivity(intent);
            }
        });

        mIdPic.setImageResource(ObservationPhotosViewer.observationIcon(mObservation.toJSONObject()));
        mIdName.setText(mObservation.species_guess);
        mTaxonicName.setVisibility(View.GONE);


        if (mObservation.id == null) {
            mSharePhoto.setVisibility(View.GONE);
        }


        if (mObservation.taxon_id == null) {
            mIdName.setText(R.string.unknown_species);
            mIdArrow.setVisibility(View.GONE);
        } else {
            mIdArrow.setVisibility(View.VISIBLE);

            String inatNetwork = mApp.getInaturalistNetworkMember();
            String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
            final String idUrl = "http://" + inatHost + "/taxa/" + mObservation.taxon_id + ".json";

            // Download the taxon image URL
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final JSONObject taxon = downloadJson(idUrl);
                    mTaxon = taxon;
                    if (taxon != null) {
                        try {
                            final String imageUrl = taxon.getString("image_url");

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        UrlImageViewHelper.setUrlDrawable(mIdPic, imageUrl);

                                        if (taxon.has("default_name")) {
                                            mIdName.setText(taxon.getJSONObject("default_name").getString("name"));
                                        } else if (taxon.has("common_name")) {
                                            mIdName.setText(taxon.getJSONObject("common_name").getString("name"));
                                        }
                                        mTaxonicName.setText(taxon.getString("name"));
                                        mTaxonicName.setVisibility(View.VISIBLE);
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

        getCommentIdList();

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

        refreshProjectList();

        mIncludedInProjectsContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ObservationViewerActivity.this, ObservationProjectsViewer.class);
                intent.putExtra(ObservationProjectsViewer.PROJECTS, mProjects);
                startActivity(intent);
            }
        });

        if ((mObservation.description != null) && (mObservation.description.length() > 0)) {
            mNotesContainer.setVisibility(View.VISIBLE);
            mNotes.setText(mObservation.description);
        } else {
            mNotesContainer.setVisibility(View.GONE);
        }
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
            startActivityForResult(new Intent(Intent.ACTION_EDIT, mUri, this, ObservationEditor.class), REQUEST_CODE_EDIT_OBSERVATION);
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
        getSupportMenuInflater().inflate(mReadOnly ? R.menu.observation_viewer_read_only_menu : R.menu.observation_viewer_menu, menu);
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
        outState.putString("mObsJson", mObsJson);
        outState.putBoolean("mFlagAsCaptive", mFlagAsCaptive);
        super.onSaveInstanceState(outState);
    }

    private class ObservationReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            try {
                unregisterReceiver(mObservationReceiver);
            } catch (Exception exc) {
                // Continue
            }

	        Observation observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);


            if (mObservation == null) {
                reloadObservation(null, false);
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

	        int taxonId = (observation.taxon_id == null ? 0 : observation.taxon_id);
            refreshActivity();
            refreshFavorites();
            resizeActivityList();
            resizeFavList();
            refreshProjectList();


            refreshDataQuality();
	    }

	}

    private void resizeFavList() {
        final Handler handler = new Handler();
        if ((mFavoritesList.getVisibility() == View.VISIBLE) && (mFavoritesList.getWidth() == 0)) {
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
        if ((mCommentsIdsList.getVisibility() == View.VISIBLE) && (mCommentsIdsList.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeActivityList();
                }
            }, 100);

            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int height = setListViewHeightBasedOnItems(mCommentsIdsList);

                View background = findViewById(R.id.comment_id_list_background);
                ViewGroup.LayoutParams params2 =  background.getLayoutParams();
                params2.height = height;
                background.requestLayout();
            }
        }, 100);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_EDIT_OBSERVATION) {
            if (resultCode == ObservationEditor.RESULT_DELETED) {
                // User deleted the observation
                finish();
                return;
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


    			// Show a loading progress until the new comments/IDs are loaded
    			mCommentsIds = null;
    			refreshActivity();

    			try {
    				Thread.sleep(1000);
    			} catch (InterruptedException e) {
    				e.printStackTrace();
    			}

    			// Refresh the comment/id list
    			IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
    			registerReceiver(mObservationReceiver, filter);
    			Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
    			serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
    			startService(serviceIntent2);

    		}
    	} else if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // Show a loading progress until the new comments/IDs are loaded
            mCommentsIds = null;
            mFavorites = null;
            refreshActivity();
            refreshFavorites();

            // Refresh the comment/id list
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            registerReceiver(mObservationReceiver, filter);
            Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
            serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            startService(serviceIntent2);
        }
    }

    private void refreshProjectList() {
        if ((mProjects != null) && (mProjects.size() > 0)) {
            mIncludedInProjectsContainer.setVisibility(View.VISIBLE);
            mIncludedInProjects.setText(String.format(getString(mProjects.size() > 1 ? R.string.included_in_projects : R.string.included_in_projects_singular), mProjects.size()));
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
    		int paddingHeight = (int)getResources().getDimension(R.dimen.abs__action_bar_default_height);
    		params.height = totalItemsHeight + totalDividersHeight;
    		listView.setLayoutParams(params);
    		listView.requestLayout();

    		return params.height;

    	} else {
    		return 0;
    	}
    }

}
