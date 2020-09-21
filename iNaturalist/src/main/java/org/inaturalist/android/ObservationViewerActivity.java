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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.Pair;
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
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.ablanco.zoomy.TapListener;
import com.ablanco.zoomy.ZoomListener;
import com.ablanco.zoomy.Zoomy;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.cocosw.bottomsheet.BottomSheet;
import com.evernote.android.state.State;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.viewpagerindicator.CirclePageIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObservationViewerActivity extends AppCompatActivity implements AnnotationsAdapter.OnAnnotationActions {
    private static final int NEW_ID_REQUEST_CODE = 0x101;
    private static final int REQUEST_CODE_LOGIN = 0x102;
    private static final int REQUEST_CODE_EDIT_OBSERVATION = 0x103;
    private static final int SHARE_REQUEST_CODE = 0x104;
    private static final int OBSERVATION_PHOTOS_REQUEST_CODE = 0x105;

    public static final int RESULT_FLAGGED_AS_CAPTIVE = 0x300;
    public static final int RESULT_OBSERVATION_CHANGED = 0x301;

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
	@State(AndroidStateBundlers.SerializableBundler.class) public Observation mObservation;
    @State public boolean mFlagAsCaptive;

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
    @State(AndroidStateBundlers.JSONObjectBundler.class) public JSONObject mTaxon;
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
    private ViewGroup mDataQualityGraph;
    private TextView mIncludedInProjects;
    private ViewGroup mIncludedInProjectsContainer;
    private ProgressBar mLoadingPhotos;
    private ProgressBar mLoadingMap;

    private ObservationReceiver mObservationReceiver;

    @State(AndroidStateBundlers.BetterJSONListBundler.class) public ArrayList<BetterJSONObject> mFavorites = null;
    @State(AndroidStateBundlers.BetterJSONListBundler.class) public ArrayList<BetterJSONObject> mCommentsIds = null;
    @State public int mIdCount = 0;
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
    @State(AndroidStateBundlers.BetterJSONListBundler.class) public ArrayList<BetterJSONObject> mProjects;
    private ImageView mIdArrow;
    private ViewGroup mUnknownLocationContainer;
    @State public boolean mReadOnly;
    private boolean mLoadingObservation;
    @State public String mObsJson;
    @State public String mTaxonJson;
    private boolean mShowComments;
    @State public int mCommentCount;
    @State public String mTaxonImage;
    @State public String mTaxonIdName;
    @State public String mTaxonScientificName;
    @State public int mTaxonRankLevel;
    @State public String mTaxonRank;
    @State public String mActiveTab;
    private boolean mReloadObs;
    private boolean mLoadObsJson = false;
    private ViewGroup mPhotosContainer;
    @State public boolean mReloadTaxon;
    private boolean mScrollToCommentsBottom;
    private ScrollView mScrollView;
    private ViewGroup mTaxonInactive;
    private View mAddCommentBackground;
    private ViewGroup mAddCommentContainer;
    private EditText mAddCommentText;
    private ImageView mAddCommentDone;
    private MentionsAutoComplete mCommentMentions;
    private AttributesReceiver mAttributesReceiver;
    private ChangeAttributesReceiver mChangeAttributesReceiver;
    @State public SerializableJSONArray mAttributes;

    private ViewGroup mAnnotationSection;
    private ListView mAnnotationsList;
    private ProgressBar mLoadingAnnotations;
    private ViewGroup mAnnotationsContent;
    private DownloadObservationReceiver mDownloadObservationReceiver;
    private Menu mMenu;

    @Override
	protected void onStart() {
		super.onStart();


	}

	@Override
	protected void onStop() {
		super.onStop();

	}


    @Override
    public void onAnnotationCollapsedExpanded() {
        // Annotation has been expanded / collapsed - resize the list to show it
        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                ActivityHelper.setListViewHeightBasedOnItems(mAnnotationsList);
                mAnnotationsList.requestLayout();
            }
        }, 50);
    }

    @Override
    public void onDeleteAnnotationValue(String uuid) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_ANNOTATION, null, ObservationViewerActivity.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.UUID, uuid);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onAnnotationAgree(String uuid) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_ANNOTATION, null, ObservationViewerActivity.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.UUID, uuid);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onAnnotationDisagree(String uuid) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_DISAGREE_ANNOTATION, null, ObservationViewerActivity.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.UUID, uuid);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onAnnotationVoteDelete(String uuid) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_ANNOTATION_VOTE, null, ObservationViewerActivity.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.UUID, uuid);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onSetAnnotationValue(int annotationId, int valueId) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_SET_ANNOTATION_VALUE, null, ObservationViewerActivity.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.ATTRIBUTE_ID, annotationId);
        serviceIntent.putExtra(INaturalistService.VALUE_ID, valueId);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private class PhotosViewPagerAdapter extends PagerAdapter implements SoundPlayer.OnPlayerStatusChange {
        private Cursor mImageCursor = null;
        private Cursor mSoundCursor = null;

        private List<SoundPlayer> mPlayers = new ArrayList<>();
        private HashMap<Integer, Bitmap> mBitmaps = new HashMap<>();

        public void refreshPhotoPositions(Integer position, boolean doNotUpdate) {
            int currentPosition = position == null ? 0 : 1;
            int count = mImageCursor.getCount();

            if (count == 0) return;

            mImageCursor.moveToPosition(0);

            do {
                if ((position == null) || (mImageCursor.getPosition() != position.intValue()))  {
                    ObservationPhoto currentOp = new ObservationPhoto(mImageCursor);
                    currentOp.position = currentPosition;
                    ContentValues cv = currentOp.getContentValues();
                    if (doNotUpdate && currentOp._synced_at != null) {
                        cv.put(ObservationPhoto._SYNCED_AT, currentOp._synced_at.getTime());
                    }

                    if (currentOp.photo_filename != null) {
                        getContentResolver().update(ObservationPhoto.CONTENT_URI, cv, "photo_filename = '" + currentOp.photo_filename + "'", null);
                    } else {
                        getContentResolver().update(ObservationPhoto.CONTENT_URI, cv, "photo_url = '" + currentOp.photo_url + "'", null);
                    }

                    currentPosition++;
                }
            } while (mImageCursor.moveToNext());
        }

        public PhotosViewPagerAdapter() {
            if (!mReadOnly && mObservation != null && mObservation.uuid != null) {
                mImageCursor = getContentResolver().query(ObservationPhoto.CONTENT_URI,
                        ObservationPhoto.PROJECTION,
                        "(observation_uuid=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                        new String[]{mObservation.uuid},
                        ObservationPhoto.DEFAULT_SORT_ORDER);
                mSoundCursor = getContentResolver().query(ObservationSound.CONTENT_URI,
                        ObservationSound.PROJECTION,
                        "(observation_uuid=?) and ((is_deleted = 0) OR (is_deleted IS NULL))",
                        new String[]{mObservation.uuid},
                        ObservationSound.DEFAULT_SORT_ORDER);

                mImageCursor.moveToFirst();
            }
        }

        public Cursor getCursor() {
            return mImageCursor;
        }

        @Override
        public int getCount() {
            return mReadOnly ?
                    (mObservation.photos.size() + mObservation.sounds.size()) :
                    (mImageCursor != null ? mImageCursor.getCount() : 0) +
                    (mSoundCursor != null ? mSoundCursor.getCount() : 0);
        }

        public int getPhotoCount() {
            return mReadOnly ? mObservation.photos.size() : mImageCursor.getCount();
        }

        public void setAsFirstPhoto(int position) {
            // Set current photo to be positioned first
            mImageCursor.moveToPosition(position);
            ObservationPhoto op = new ObservationPhoto(mImageCursor);
            op.position = 0;
            if (op.photo_filename != null) {
                getContentResolver().update(ObservationPhoto.CONTENT_URI, op.getContentValues(), "photo_filename = '" + op.photo_filename + "'", null);
            } else {
                getContentResolver().update(ObservationPhoto.CONTENT_URI, op.getContentValues(), "photo_url = '" + op.photo_url + "'", null);
            }

            // Update the rest of the photos to be positioned afterwards
            refreshPhotoPositions(position, false);

            reloadPhotos();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            ImageView imageView = new ImageView(ObservationViewerActivity.this);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            int imageId = 0;
            String photoFilename = null;
            String imageUrl = null;
            ObservationSound sound = null;

            if (!mReadOnly) {
                if (position >= mImageCursor.getCount()) {
                    // Sound
                    mSoundCursor.moveToPosition(position - mImageCursor.getCount());

                    sound = new ObservationSound(mSoundCursor);

                    Logger.tag(TAG).info("Observation: " + mObservation);
                    Logger.tag(TAG).info("Sound: " + sound);

                } else {
                    // Photo
                    mImageCursor.moveToPosition(position);

                    imageUrl = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));
                    photoFilename = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_FILENAME));
                }

            } else {
                if (position >= mObservation.photos.size()) {
                    // Show sound
                    sound = mObservation.sounds.get(position - mObservation.photos.size());
                } else {
                    imageUrl = mObservation.photos.get(position).photo_url;
                }
            }

            if (sound != null) {
                // Sound - show a sound player interface
                SoundPlayer player = new SoundPlayer(ObservationViewerActivity.this, container, sound, this);
                View view = player.getView();
                ((ViewPager)container).addView(view, 0);

                view.setTag(player);
                mPlayers.add(player);
                return view;
            }

            if (imageUrl != null) {
                // Online photo
                imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            	String extension = imageUrl.substring(imageUrl.lastIndexOf('.'));

            	String thumbnailUrl, largeSizeUrl;

                // Deduce the original-sized URL
                if (imageUrl.substring(0, imageUrl.lastIndexOf('/')).endsWith("assets")) {
                    // It's an assets default URL - e.g. https://www.inaturalist.org/assets/copyright-infringement-square.png
                    largeSizeUrl = imageUrl.substring(0, imageUrl.lastIndexOf('-') + 1) + "original" + extension;
                    thumbnailUrl = imageUrl.substring(0, imageUrl.lastIndexOf('-') + 1) + "small" + extension;
                } else {
                    // "Regular" observation photo
                    largeSizeUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/') + 1) + "original" + extension;
                    thumbnailUrl = imageUrl.substring(0, imageUrl.lastIndexOf('/') + 1) + "small" + extension;
                }

                RequestBuilder thumbnailRequest = Glide.with(ObservationViewerActivity.this)
                        .load(thumbnailUrl);


                Glide.with(ObservationViewerActivity.this)
                        .load(largeSizeUrl)
                        .thumbnail(thumbnailRequest)
                        .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.AUTOMATIC))
                        .into(new CustomTarget<Drawable>() {
                            @Override
                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                // Save downloaded bitmap into local file
                                imageView.setImageDrawable(resource);

                                if (resource instanceof BitmapDrawable) {
                                    mBitmaps.put(position, ((BitmapDrawable)resource).getBitmap());
                                } else if (resource instanceof GifDrawable) {
                                    mBitmaps.put(position, ((GifDrawable) resource).getFirstFrame());
                                }
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                // Failed to load observation photo
                                try {
                                    JSONObject eventParams = new JSONObject();
                                    eventParams.put(AnalyticsClient.EVENT_PARAM_SIZE, AnalyticsClient.EVENT_PARAM_VALUE_MEDIUM);

                                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_PHOTO_FAILED_TO_LOAD, eventParams);
                                } catch (JSONException e) {
                                    Logger.tag(TAG).error(e);
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

                    // Decode bitmap with inSampleSize set
                    options.inJustDecodeBounds = false;
                    // This decreases in-memory byte-storage per pixel
                    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                    bitmapImage = BitmapFactory.decodeFile(photoFilename, options);
                    bitmapImage = ImageUtils.rotateAccordingToOrientation(bitmapImage, photoFilename);
                    imageView.setImageBitmap(bitmapImage);
                    mBitmaps.put(position, bitmapImage);
                } catch (Exception e) {
                    Logger.tag(TAG).error(e);
                }
            }

            ((ViewPager)container).addView(imageView, 0);

            new Zoomy.Builder(ObservationViewerActivity.this)
                    .target(imageView)
                    .zoomListener(new ZoomListener() {
                        @Override
                        public void onViewBeforeStartedZooming(View view) {
                            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            imageView.setImageBitmap(mBitmaps.get(position));
                        }
                        @Override
                        public void onViewStartedZooming(View view) {
                        }
                        @Override
                        public void onViewEndedZooming(View view) {
                            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        }
                    })
                    .tapListener(new TapListener() {
                        @Override
                        public void onTap(View v) {
                            Intent intent = new Intent(ObservationViewerActivity.this, ObservationPhotosViewer.class);
                            intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, position);

                            if (!mReadOnly) {
                                intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObservation.id);
                                intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID_INTERNAL, mObservation._id);
                                intent.putExtra(ObservationPhotosViewer.OBSERVATION_UUID, mObservation.uuid);
                                intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, true);
                                intent.putExtra(ObservationPhotosViewer.READ_ONLY, false);
                                startActivityForResult(intent, OBSERVATION_PHOTOS_REQUEST_CODE);
                            } else {
                                intent.putExtra(ObservationPhotosViewer.OBSERVATION, mObsJson);
                                startActivity(intent);
                            }
                        }
                    })
                    .register();

            return imageView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            ((ViewPager) container).removeView((View) object);
        }

        public void destroy() {
            for (SoundPlayer player : mPlayers) {
                if (player != null) {
                    player.destroy();
                }
            }
        }

        public void pause() {
            for (SoundPlayer player : mPlayers) {
                if (player != null) {
                    player.pause();
                }
            }
        }

        @Override
        public void onPlay(SoundPlayer player) {
            // Pause all other players
            for (SoundPlayer p : mPlayers) {
                if ((p != null) && (!p.equals(player))) {
                    p.pause();
                }
            }
        }

        @Override
        public void onPause(SoundPlayer player) {

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
        refreshAttributes();

        mAttributesReceiver = new AttributesReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.GET_ATTRIBUTES_FOR_TAXON_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mAttributesReceiver, filter, ObservationViewerActivity.this);

        mChangeAttributesReceiver = new ChangeAttributesReceiver();
        IntentFilter filter2 = new IntentFilter(INaturalistService.DELETE_ANNOTATION_RESULT);
        filter2.addAction(INaturalistService.AGREE_ANNOTATION_RESULT);
        filter2.addAction(INaturalistService.DISAGREE_ANNOTATION_RESULT);
        filter2.addAction(INaturalistService.DELETE_ANNOTATION_VOTE_RESULT);
        filter2.addAction(INaturalistService.SET_ANNOTATION_VALUE_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mChangeAttributesReceiver, filter2, ObservationViewerActivity.this);

        mDownloadObservationReceiver = new DownloadObservationReceiver();
        IntentFilter filter3 = new IntentFilter(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mDownloadObservationReceiver, filter3, this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.observation);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());
        setContentView(R.layout.observation_viewer);
        mHelper = new ActivityHelper(this);

        reloadObservation(savedInstanceState, false);

        mAnnotationSection = (ViewGroup) findViewById(R.id.annotations_section);
        mAnnotationsList = (ListView) findViewById(R.id.annotations_list);
        mLoadingAnnotations = (ProgressBar) findViewById(R.id.loading_annotations);
        mAnnotationsContent = (ViewGroup) findViewById(R.id.annotations_content);

        mAddCommentBackground = (View) findViewById(R.id.add_comment_background);
        mAddCommentContainer = (ViewGroup) findViewById(R.id.add_comment_container);
        mAddCommentDone = (ImageView) findViewById(R.id.add_comment_done);
        mAddCommentText = (EditText) findViewById(R.id.add_comment_text);
        mCommentMentions = new MentionsAutoComplete(ObservationViewerActivity.this, mAddCommentText);

        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mUserName = (TextView) findViewById(R.id.user_name);
        mObservedOn = (TextView) findViewById(R.id.observed_on);
        mUserPic = (ImageView) findViewById(R.id.user_pic);
        mPhotosViewPager = (ViewPager) findViewById(R.id.photos);
        mIndicator = (CirclePageIndicator)findViewById(R.id.photos_indicator);
        mSharePhoto = findViewById(R.id.share_photo);
        mIdPic = (ImageView)findViewById(R.id.id_icon);
        mIdName = (TextView) findViewById(R.id.id_name);
        mTaxonicName = (TextView) findViewById(R.id.id_sub_name);
        mIdRow = (ViewGroup) findViewById(R.id.id_row);
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.location_map)).getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                setupMap();
            }
        });
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
        mDataQualityGraph = (ViewGroup) findViewById(R.id.data_quality_graph);
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
        mTaxonInactive = (ViewGroup) findViewById(R.id.taxon_inactive);

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
        refreshActivity();
        refreshFavorites();

        reloadPhotos();
        getCommentIdList();
        refreshDataQuality();
        refreshAttributes();

        // Mark observation updates as viewed
        if (mObservation != null && mObservation._synced_at != null) {
            Intent serviceIntent = new Intent(INaturalistService.ACTION_VIEWED_UPDATE, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    private void reloadObservation(Bundle savedInstanceState, boolean forceReload) {

        Intent intent = getIntent();

		if (savedInstanceState == null) {
			// Do some setup based on the action being performed.
			Uri uri = intent.getData();

			if ((uri != null) && (uri.getScheme().equals("https"))) {
			    // User clicked on an observation link (e.g. https://www.inaturalist.org/observations/1234)
                String path = uri.getPath();
                Logger.tag(TAG).info("Launched from external URL: " + uri);

                if (path.toLowerCase().startsWith("/observations/")) {
                    Pattern pattern = Pattern.compile("/observations/([0-9]+)");
                    Matcher matcher = pattern.matcher(path);
                    if (matcher.find()) {
                        int obsId = Integer.valueOf(matcher.group(1));
                        mReadOnly = true;
                        mShowComments = false;
                        mScrollToCommentsBottom = false;
                        mReloadObs = true;
                        mObsJson = String.format(Locale.ENGLISH, "{ \"id\": %d }", obsId);
                        mObservation = new Observation(new BetterJSONObject(mObsJson));
                    } else {
                        Logger.tag(TAG).error("Invalid URL");
                        finish();
                        return;
                    }
                } else {
                    Logger.tag(TAG).error("Invalid URL");
                    finish();
                    return;
                }
            } else {
                mShowComments = intent.getBooleanExtra(SHOW_COMMENTS, false);
                mScrollToCommentsBottom = intent.getBooleanExtra(SCROLL_TO_COMMENTS_BOTTOM, false);
                if (uri == null) {
                    String obsJson = intent.getStringExtra("observation");
                    mReadOnly = intent.getBooleanExtra("read_only", false);
                    mReloadObs = intent.getBooleanExtra("reload", false);
                    mObsJson = obsJson;

                    if (obsJson == null) {
                        Logger.tag(TAG).error("Null URI from intent.getData");
                        finish();
                        return;
                    }

                    mObservation = new Observation(new BetterJSONObject(obsJson));

                    if (mReadOnly && mObservation.id != null && mObservation.user_login != null && mApp.loggedIn()) {
                        // See if this read-only observation is in fact our own observation (e.g. viewed from explore screen)
                        if (mObservation.user_login.toLowerCase().equals(mApp.currentUserLogin().toLowerCase())) {
                            // Our own observation
                            Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[]{String.valueOf(mObservation.id)}, Observation.DEFAULT_SORT_ORDER);
                            if (c.getCount() > 0) {
                                // Observation available locally in the DB - just show/edit it
                                mReadOnly = false;
                                c.moveToFirst();
                                mObservation = new Observation(c);
                                mReloadObs = false;
                                uri = mObservation.getUri();
                                intent.setData(uri);
                            } else {
                                // Observation not downloaded yet - download and save it
                                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION, null, this, INaturalistService.class);
                                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                                ContextCompat.startForegroundService(this, serviceIntent);

                                mReadOnly = false;
                                mReloadObs = true;
                                uri = ContentUris.withAppendedId(Observation.CONTENT_URI, mObservation.id);
                            }
                            c.close();
                        }
                    }
                }

                mUri = uri;
            }

        } else {
            String obsUri = savedInstanceState.getString("mUri");
            if (obsUri != null) {
                mUri = Uri.parse(obsUri);
            } else {
                mUri = intent.getData();
            }
        }

        if (mCursor != null) {
            if (!mCursor.isClosed()) mCursor.close();
            mCursor = null;
        }

        if ((!mReadOnly) && (mUri != null)) mCursor = getContentResolver().query(mUri, Observation.PROJECTION, null, null, null);

        if ((mObservation == null) || (forceReload)) {
            if (!mReadOnly) {
                if (mCursor == null || mCursor.getCount() == 0) {
                    Logger.tag(TAG).error("Cursor count is zero - finishing activity: " + mCursor);
                    finish();
                    return;
                }

                mObservation = new Observation(mCursor);
            }
        }

        if ((mObservation != null) && (mObsJson == null)) {
            mObservationReceiver = new ObservationReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            Logger.tag(TAG).info("Registering ACTION_OBSERVATION_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, this);

            mLoadObsJson = true;

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            serviceIntent.putExtra(INaturalistService.GET_PROJECTS, true);
            ContextCompat.startForegroundService(this, serviceIntent);
        }

    }

    @Override
    public void onDestroy() {
        if ((mCursor != null) && (!mCursor.isClosed())) mCursor.close();

        super.onDestroy();
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

        if ((mObservation == null) || (mObservation.id == null)) {
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
                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

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
                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

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


        if (mObservation == null) return;

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
            if (!((mObservation._synced_at == null) || (mObservation.id == null) || ((mObservation._updated_at != null) && (mObservation._updated_at.after(mObservation._synced_at))))) {
                cv.put(Observation._SYNCED_AT, System.currentTimeMillis()); // No need to sync
            }
            getContentResolver().update(mObservation.getUri(), cv, null, null);
            Logger.tag(TAG).debug("ObservationViewerActivity - refreshActivity - update obs: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
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
                    serviceIntent.putExtra(INaturalistService.FROM_VISION, false);
                    ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

                    try {
                        JSONObject eventParams = new JSONObject();
                        eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_VIEW_OBS_AGREE);
                        eventParams.put(AnalyticsClient.EVENT_PARAM_FROM_VISION_SUGGESTION, false);

                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_ADD_ID, eventParams);
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
                    }

                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
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
                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);
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
                                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);
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
                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);
            }


            @Override
            public void onCommentRemoved(BetterJSONObject comment) {
                 // After calling the remove API - we'll refresh the comment/ID list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_COMMENT, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.COMMENT_ID, comment.getInt("id"));
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);
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
                                ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
            }
        }, mReadOnly);
        mCommentsIdsList.setAdapter(mAdapter);

        mAddId.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ObservationViewerActivity.this, IdentificationActivity.class);
                intent.putExtra(IdentificationActivity.SUGGEST_ID, true);
                intent.putExtra(IdentificationActivity.OBSERVATION_ID, mObservation.id);
                intent.putExtra(IdentificationActivity.OBSERVATION_ID_INTERNAL, mObservation._id);
                intent.putExtra(IdentificationActivity.OBSERVATION_UUID, mObservation.uuid);
                intent.putExtra(IdentificationActivity.OBSERVED_ON, mObservation.observed_on);
                intent.putExtra(IdentificationActivity.LONGITUDE, mObservation.longitude);
                intent.putExtra(IdentificationActivity.LATITUDE, mObservation.latitude);
                if (mObservation._id != null) {
                    if (((PhotosViewPagerAdapter)mPhotosViewPager.getAdapter()).getPhotoCount() > 0) {
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
                mAddCommentBackground.setVisibility(View.VISIBLE);
                mAddCommentContainer.setVisibility(View.VISIBLE);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mAddCommentText.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(mAddCommentText, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 100);

                mAddCommentText.setText("");

                SharedPreferences pref = mApp.getPrefs();
                String username = pref.getString("username", null);
                String userIconUrl = pref.getString("user_icon_url", null);

                mAddCommentDone.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String comment = mAddCommentText.getText().toString();

                        // Add the comment
                        Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_COMMENT, null, ObservationViewerActivity.this, INaturalistService.class);
                        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                        serviceIntent.putExtra(INaturalistService.COMMENT_BODY, comment);
                        ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

                        mCommentsIds = null;
                        refreshActivity();

                        // Refresh the comment/id list
                        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                        BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                        mAddCommentContainer.setVisibility(View.GONE);
                        mAddCommentBackground.setVisibility(View.GONE);

                        // Hide keyboard
                        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(mAddCommentText.getWindowToken(), 0);
                    }
                });

                mAddCommentBackground.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        discardAddComment();
                    }
                });

            }
        });
    }

    private void discardAddComment() {
        mHelper.confirm((View) null, getString(R.string.discard_comment), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAddCommentContainer.setVisibility(View.GONE);
                mAddCommentBackground.setVisibility(View.GONE);
                dialog.dismiss();
                mCommentMentions.dismiss();

                // Hide keyboard
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }, R.string.yes, R.string.no);
    }

    private void setupMap() {
        if (mMap == null) return;
        if (mObservation == null) return;

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
                mLocationText.setText((mObservation.private_place_guess != null) && (mObservation.private_place_guess.length() > 0) ?
                        mObservation.private_place_guess : mObservation.place_guess);
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
            // Unknown place
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
        if ((mObservation != null) && (mObservation.id != null) && (mCommentsIds == null)) {
            BaseFragmentActivity.safeUnregisterReceiver(mObservationReceiver, this);
            mObservationReceiver = new ObservationReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            Logger.tag(TAG).info("Registering ACTION_OBSERVATION_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            serviceIntent.putExtra(INaturalistService.GET_PROJECTS, false);
            ContextCompat.startForegroundService(this, serviceIntent);

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

        if (mObservation == null) {
            return;
        }

        Logger.tag(TAG).debug("refreshDataQuality: " + mObservation.id);

        if (((mObservation.latitude == null) && (mObservation.longitude == null)) && ((mObservation.private_latitude == null) && (mObservation.private_longitude == null))) {
            // No place
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

        Logger.tag(TAG).debug("refreshDataQuality 2: " + dataQuality + ":" + (reasonText != 0 ? getString(reasonText) : "N/A"));

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

            Logger.tag(TAG).debug("refreshDataQuality 3: " + dataQuality);
        }

        Logger.tag(TAG).debug("refreshDataQuality 4: " + mObservation.quality_grade + ":" + mIdCount + ":" + mObservation.captive + ":" + mFlagAsCaptive + ":" +
            ((PhotosViewPagerAdapter)mPhotosViewPager.getAdapter()).getCount() + ":" + mObservation.observed_on + ":" + mObservation.latitude + ":" + mObservation.private_latitude +
            ":" + mObservation.longitude + ":" + mObservation.private_longitude);


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

        OnClickListener showDataQualityAssessment = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return;
                }

                if (mObsJson != null) {
                    try {
                        Intent intent = new Intent(ObservationViewerActivity.this, DataQualityAssessment.class);
                        intent.putExtra(DataQualityAssessment.OBSERVATION, new BetterJSONObject(getMinimalObservation(new JSONObject(mObsJson))));
                        startActivity(intent);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        mDataQualityReason.setOnClickListener(showDataQualityAssessment);
        mDataQualityGraph.setOnClickListener(showDataQualityAssessment);
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
            }
        } else {
            SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            String username = pref.getString("username", null);
            userIconUrl = pref.getString("user_icon_url", null);
            mUserName.setText(username);

            // Display the errors for the observation, if any
            TextView errorsDescription = (TextView) findViewById(R.id.errors);
            if (mObservation.id != null) {
                JSONArray errors = mApp.getErrorsForObservation(mObservation.id != null ? mObservation.id : mObservation._id);

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
                        Logger.tag(TAG).error(e);
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
                    if (userObj == null) return;
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
                                mHelper.openUrlInBrowser(obsUrl);
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

            if ((mTaxonScientificName == null) || (mTaxonIdName == null) || (mTaxonImage == null)) {
                downloadObsTaxonAndUpdate();
            } else {
                UrlImageViewHelper.setUrlDrawable(mIdPic, mTaxonImage);

                if (mTaxon == null) {
                    if ((mTaxonIdName == null) || (mTaxonIdName.length() == 0)) {
                        mIdName.setText(mTaxonScientificName);
                        mTaxonicName.setText(mTaxonScientificName);
                    } else {
                        mTaxonicName.setVisibility(View.VISIBLE);

                        if (mApp.getShowScientificNameFirst()) {
                            // Show scientific name first, before common name
                            TaxonUtils.setTaxonScientificName(mApp, mIdName, mTaxonScientificName, mTaxonRankLevel, mTaxonRank);
                            mTaxonicName.setText(mTaxonIdName);
                        } else {
                            TaxonUtils.setTaxonScientificName(mApp, mTaxonicName, mTaxonScientificName, mTaxonRankLevel, mTaxonRank);
                            mIdName.setText(mTaxonIdName);
                        }

                    }
                } else {
                    if (mApp.getShowScientificNameFirst()) {
                        // Show scientific name first, before common name
                        TaxonUtils.setTaxonScientificName(mApp, mIdName, mTaxon);
                        mTaxonicName.setText(TaxonUtils.getTaxonName(this, mTaxon));
                    } else {
                        TaxonUtils.setTaxonScientificName(mApp, mTaxonicName, mTaxon);
                        mIdName.setText(TaxonUtils.getTaxonName(this, mTaxon));
                    }

                }
            }
        }


        mTaxonInactive.setVisibility(mTaxon == null || mTaxon.optBoolean("is_active", true) ? View.GONE : View.VISIBLE);

        mTaxonInactive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                Locale deviceLocale = getResources().getConfiguration().locale;
                String deviceLanguage =   deviceLocale.getLanguage();
                String taxonUrl = String.format(Locale.ENGLISH, "%s/taxon_changes?taxon_id=%d&locale=%s", inatHost, mTaxon.optInt("id"), deviceLanguage);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(taxonUrl));
                startActivity(i);
            }
        });

        if (!mReadOnly && (mProjects == null)) {
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
            HtmlUtils.fromHtml(mNotes, mObservation.description);
        } else {
            mNotesContainer.setVisibility(View.GONE);
        }
    }


    private JSONObject downloadTaxon(int taxonId) {
        Locale deviceLocale = getResources().getConfiguration().locale;
        String deviceLanguage =   deviceLocale.getLanguage();
        final String idUrl = INaturalistService.API_HOST + "/taxa/" + taxonId + "?locale=" + deviceLanguage;

        JSONObject results = downloadJson(idUrl);

        if (results == null) return null;

        return results.optJSONArray("results").optJSONObject(0);
    }

    private void downloadObsTaxonAndUpdate() {
        // Download the taxon URL
        new Thread(new Runnable() {
            @Override
            public void run() {
                final JSONObject taxon = downloadTaxon(mObservation.taxon_id);

                if (taxon == null) return;

                mTaxon = taxon;
                if (taxon != null) {
                    try {
                        JSONObject defaultPhoto = taxon.getJSONObject("default_photo");
                        final String imageUrl = defaultPhoto.getString("square_url");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTaxonImage = imageUrl;
                                UrlImageViewHelper.setUrlDrawable(mIdPic, mTaxonImage);

                                mTaxonIdName = TaxonUtils.getTaxonName(ObservationViewerActivity.this, mTaxon);
                                mTaxonScientificName = TaxonUtils.getTaxonScientificName(mApp, taxon);
                                mTaxonRankLevel = taxon.optInt("rank_level", 0);
                                mTaxonRank = taxon.optString("rank");

                                if (mApp.getShowScientificNameFirst()) {
                                    // Show scientific name first, before common name
                                    mTaxonicName.setText(mTaxonIdName);
                                    TaxonUtils.setTaxonScientificName(mApp, mIdName, taxon);
                                } else {
                                    mIdName.setText(mTaxonIdName);
                                    TaxonUtils.setTaxonScientificName(mApp, mTaxonicName, taxon);
                                }

                                mTaxonicName.setVisibility(View.VISIBLE);
                            }
                        });
                    } catch (JSONException e) {
                        Logger.tag(TAG).error(e);
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
            String jwtToken = mApp.getJWTToken();
            if (mApp.loggedIn() && (jwtToken != null)) conn.setRequestProperty ("Authorization", jwtToken);

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
                dateFormatString = getString(R.string.date_short);
            } else {
                // Current year
                dateFormatString = getString(R.string.date_short_this_year);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString);
            format.append(dateFormat.format(date));
        }
        if (time != null) {
            // Format the time part
            if (date != null) {
                format.append(" • ");
            }

            String timeFormatString;
            if (DateFormat.is24HourFormat(getApplicationContext())) {
                timeFormatString = getString(R.string.time_short_24_hour);
            } else {
                timeFormatString = getString(R.string.time_short_12_hour);
            }
            SimpleDateFormat timeFormat = new SimpleDateFormat(timeFormatString);

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
            if (mTaxon != null) mApp.setServiceResult(ObservationEditor.TAXON, mTaxon.toString());
            if (mObsJson != null) intent.putExtra(ObservationEditor.OBSERVATION_JSON, mObsJson);
            startActivityForResult(intent, REQUEST_CODE_EDIT_OBSERVATION);
            return true;
        case R.id.flag_captive:
            PopupMenu popup = new PopupMenu(ObservationViewerActivity.this, findViewById(R.id.flag_captive));
            popup.getMenuInflater().inflate(R.menu.flag_captive_menu, popup.getMenu());
            MenuItem flagCaptive = popup.getMenu().findItem(R.id.flag_captive);
            flagCaptive.setChecked(mFlagAsCaptive);
            popup.setOnMenuItemClickListener(menuItem -> {
                mFlagAsCaptive = !mFlagAsCaptive;
                refreshDataQuality();
                return true;
            });

            popup.show();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (mAddCommentContainer.getVisibility() == View.VISIBLE) {
            // Currently showing the add comment dialog
            discardAddComment();
        } else {
            prepareToExit();
        }
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
                        ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

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
        getMenuInflater().inflate(R.menu.observation_viewer_menu, menu);
        mMenu = menu;
        refreshMenu();
        
        return true;
    }
    
    private void refreshMenu() {
        MenuItem flagCaptive = mMenu.findItem(R.id.flag_captive);
        MenuItem edit = mMenu.findItem(R.id.edit_observation);

        if (mReadOnly) {
            flagCaptive.setVisible(true);
            edit.setVisible(false);
            Drawable icon = flagCaptive.getIcon();
            icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#848484"), PorterDuff.Mode.SRC_IN));
            flagCaptive.setIcon(icon);
        } else {
            flagCaptive.setVisible(false);
            edit.setVisible(true);
        }       
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    private class ChangeAttributesReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            Logger.tag(TAG).error("ChangeAttributesReceiver");

            // Re-download the observation JSON so we'll refresh the annotations

            mObservationReceiver = new ObservationReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
            Logger.tag(TAG).info("Registering ACTION_OBSERVATION_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

            mLoadObsJson = true;

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationViewerActivity.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
            serviceIntent.putExtra(INaturalistService.GET_PROJECTS, false);
            ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);
	    }

	}

    private class AttributesReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            Logger.tag(TAG).error("AttributesReceiver");

            BaseFragmentActivity.safeUnregisterReceiver(mAttributesReceiver, ObservationViewerActivity.this);

            BetterJSONObject resultsObj = (BetterJSONObject) mApp.getServiceResult(INaturalistService.GET_ATTRIBUTES_FOR_TAXON_RESULT);

            if (resultsObj == null) {
                mAttributes = new SerializableJSONArray();
                refreshAttributes();
                return;
            }

            mAttributes  = resultsObj.getJSONArray("results");

            refreshAttributes();
	    }

	}

    private class DownloadObservationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.tag(TAG).error("DownloadObservationReceiver - OBSERVATION_RESULT");

            BaseFragmentActivity.safeUnregisterReceiver(mDownloadObservationReceiver, ObservationViewerActivity.this);

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

            if (!observation.id.equals(mObservation.id)) {
                Logger.tag(TAG).error(String.format("DownloadObservationReceiver: Got old observation result for id %d, while current observation is %d", observation.id, mObservation.id));
                return;
            }

            JSONArray projects = observation.projects.getJSONArray();
            JSONArray comments = observation.comments.getJSONArray();
            JSONArray ids = observation.identifications.getJSONArray();
            JSONArray favs = observation.favorites.getJSONArray();
            ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> favResults = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> projectResults = new ArrayList<BetterJSONObject>();

            // #560 - refresh data quality grade
            mObservation.captive = observation.captive;
            mObservation.quality_grade = observation.quality_grade;

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
                Logger.tag(TAG).error(e);
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
                if (!mReadOnly) {
                    mUri = mObservation.getUri();
                    getIntent().setData(mUri);
                }
            }

            if (mReloadObs || mLoadObsJson) {
                if (isSharedOnApp) {
                    mObsJson = (String) mApp.getServiceResult(INaturalistService.OBSERVATION_JSON_RESULT);
                } else {
                    mObsJson = intent.getStringExtra(INaturalistService.OBSERVATION_JSON_RESULT);
                }

                if (mTaxonJson == null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            downloadCommunityTaxon();
                        }
                    }).start();
                }
            }

            mLoadObsJson = false;

            if (mReloadTaxon) {
                // Reload just the taxon part, if changed
                if (((mObservation.taxon_id == null) && (observation.taxon_id != null)) ||
                        ((mObservation.taxon_id != null) && (observation.taxon_id == null)) ||
                        (mObservation.taxon_id != observation.taxon_id)) {

                    Logger.tag(TAG).debug("ObservationViewerActivity - ObservationReceiver: Updated taxon: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
                    Logger.tag(TAG).debug("ObservationViewerActivity - ObservationReceiver: Updated taxon (new): " + observation.id + ":" + observation.preferred_common_name + ":" + observation.taxon_id);

                    mObservation.species_guess = observation.species_guess;
                    mObservation.taxon_id = observation.taxon_id;
                    mObservation.preferred_common_name = observation.preferred_common_name;
                    mObservation.iconic_taxon_name = observation.iconic_taxon_name;

                    mTaxonScientificName = null;
                    mTaxonIdName = null;
                    mTaxonImage = null;
                }

                mReloadTaxon = false;

                if (!mReadOnly) {
                    // Update observation's taxon in DB
                    ContentValues cv = mObservation.getContentValues();
                    getContentResolver().update(mUri, cv, null, null);
                    Logger.tag(TAG).debug("ObservationViewerActivity - ObservationReceiver - update obs: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
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
            refreshAttributes();
        }

    }

    private class ObservationReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            Logger.tag(TAG).error("ObservationReceiver - OBSERVATION_RESULT");

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

            if (!observation.id.equals(mObservation.id)) {
                Logger.tag(TAG).error(String.format("ObservationReceiver: Got old observation result for id %d, while current observation is %d", observation.id, mObservation.id));
                return;
            }

            JSONArray projects = observation.projects.getJSONArray();
	        JSONArray comments = observation.comments.getJSONArray();
	        JSONArray ids = observation.identifications.getJSONArray();
            JSONArray favs = observation.favorites.getJSONArray();
	        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> favResults = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> projectResults = new ArrayList<BetterJSONObject>();

            // #560 - refresh data quality grade
            mObservation.captive = observation.captive;
            mObservation.quality_grade = observation.quality_grade;

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
	            Logger.tag(TAG).error(e);
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
            }

            if (mReloadObs || mLoadObsJson) {
                if (isSharedOnApp) {
                    mObsJson = (String) mApp.getServiceResult(INaturalistService.OBSERVATION_JSON_RESULT);
                } else {
                    mObsJson = intent.getStringExtra(INaturalistService.OBSERVATION_JSON_RESULT);
                }

                if (mTaxonJson == null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            downloadCommunityTaxon();
                        }
                    }).start();
                }
            }

            mLoadObsJson = false;

            if (mReloadTaxon) {
                // Reload just the taxon part, if changed
                if (((mObservation.taxon_id == null) && (observation.taxon_id != null)) ||
                    ((mObservation.taxon_id != null) && (observation.taxon_id == null)) ||
                    (mObservation.taxon_id != observation.taxon_id)) {

                    Logger.tag(TAG).debug("ObservationViewerActivity - ObservationReceiver: Updated taxon: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
                    Logger.tag(TAG).debug("ObservationViewerActivity - ObservationReceiver: Updated taxon (new): " + observation.id + ":" + observation.preferred_common_name + ":" + observation.taxon_id);

                    mObservation.species_guess = observation.species_guess;
                    mObservation.taxon_id = observation.taxon_id;
                    mObservation.preferred_common_name = observation.preferred_common_name;
                    mObservation.iconic_taxon_name = observation.iconic_taxon_name;

                    mTaxonScientificName = null;
                    mTaxonIdName = null;
                    mTaxonImage = null;
                }

                mReloadTaxon = false;

                if (!mReadOnly) {
                    // Update observation's taxon in DB
                    ContentValues cv = mObservation.getContentValues();
                    getContentResolver().update(mUri, cv, null, null);
                    Logger.tag(TAG).debug("ObservationViewerActivity - ObservationReceiver - update obs: " + mObservation.id + ":" + mObservation.preferred_common_name + ":" + mObservation.taxon_id);
                }
            }

            if (mReloadObs && mReadOnly && mObservation.id != null && mApp.loggedIn()) {
                // See if this read-only observation is in fact our own observation (e.g. viewed from explore screen)
                if (mObservation.user_login.toLowerCase().equals(mApp.currentUserLogin().toLowerCase())) {
                    // Our own observation
                    Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[]{String.valueOf(mObservation.id)}, Observation.DEFAULT_SORT_ORDER);
                    if (c.getCount() > 0) {
                        // Observation available locally in the DB - just show/edit it
                        mReadOnly = false;
                        c.moveToFirst();
                        mObservation = new Observation(c);
                        mReloadObs = false;
                        mUri = mObservation.getUri();
                        getIntent().setData(mUri);
                    } else {
                        // Observation not downloaded yet - download and save it
                        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION, null, ObservationViewerActivity.this, INaturalistService.class);
                        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                        ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

                        mReadOnly = false;
                        mReloadObs = true;
                    }
                    c.close();

                    refreshMenu();
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
            refreshAttributes();
	    }

	}

    private void downloadCommunityTaxon() {
        JSONObject observation;
        try {
            observation = new JSONObject(mObsJson);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return;
        }

        int taxonId = -1;

        // Use current taxon
        if (observation.has("taxon")) {
            JSONObject taxon = observation.optJSONObject("taxon");
            if (taxon != null) {
                taxonId = taxon.optInt("id");
            }
        }

        if (taxonId == -1) {
            // No taxon - get the generic attributes (no taxon)
            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_ATTRIBUTES_FOR_TAXON, null, ObservationViewerActivity.this, INaturalistService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
            return;
        }

        JSONObject taxon = downloadTaxon(taxonId);

        if (taxon == null) {
            mAttributes = new SerializableJSONArray();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshAttributes();
                }
            });
            return;
        }

        mTaxonJson = taxon.toString();


        // Now that we have full taxon details, we can retrieve the annotations/attributes for that taxon
        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_ATTRIBUTES_FOR_TAXON, null, ObservationViewerActivity.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.TAXON, new BetterJSONObject(mTaxonJson));
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void reloadPhotos() {
        mLoadingPhotos.setVisibility(View.GONE);
        mPhotosAdapter = new PhotosViewPagerAdapter();
        mPhotosViewPager.setAdapter(mPhotosAdapter);
        mIndicator.setViewPager(mPhotosViewPager);
    }

    private void refreshAttributes() {
        if ((mObservation != null) && (mObservation.id == null)) {
            // Don't show attributes for non-synced observations
            mAnnotationSection.setVisibility(View.GONE);
            return;
        }
        if (mAttributes == null) {
            mAnnotationSection.setVisibility(View.VISIBLE);
            mLoadingAnnotations.setVisibility(View.VISIBLE);
            mAnnotationsContent.setVisibility(View.GONE);
            return;

        } else if (mAttributes.getJSONArray().length() == 0) {
            mAnnotationSection.setVisibility(View.GONE);
            return;
        }

        mAnnotationsContent.setVisibility(View.VISIBLE);
        mAnnotationSection.setVisibility(View.VISIBLE);
        mLoadingAnnotations.setVisibility(View.GONE);

        JSONArray obsAnnotations = null;

        if (mObsJson != null) {
            try {
                JSONObject obs = new JSONObject(mObsJson);
                obsAnnotations = obs.getJSONArray("annotations");
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }
        }

        try {
            mAnnotationsList.setAdapter(new AnnotationsAdapter(this, this, mObservation.toJSONObject(), mTaxonJson != null ? new JSONObject(mTaxonJson) : null, mAttributes.getJSONArray(), obsAnnotations));
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

        ActivityHelper.resizeList(mAnnotationsList);

        if (mAnnotationsList.getAdapter().getCount() == 0) {
            // No visible annotation - hide the entire section
            mAnnotationSection.setVisibility(View.GONE);
        }
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

        Logger.tag(TAG).debug("onActivityResult - " + requestCode + ":" + resultCode);
        if (requestCode == SHARE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // TODO - RESULT_OK is never returned + need to add "destination" param (what type of share was performed)
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_SHARE_FINISHED);
            } else {
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_SHARE_CANCELLED);
            }
        } else if (requestCode == REQUEST_CODE_EDIT_OBSERVATION) {
            Logger.tag(TAG).debug("onActivityResult - EDIT_OBS: " + requestCode + ":" + resultCode);
            if ((resultCode == ObservationEditor.RESULT_DELETED) || (resultCode == ObservationEditor.RESULT_RETURN_TO_OBSERVATION_LIST)) {
                // User deleted the observation (or did a batch-edit)
                Logger.tag(TAG).debug("onActivityResult - EDIT_OBS: Finish");
                setResult(RESULT_OBSERVATION_CHANGED);
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
                refreshAttributes();

                setResult(RESULT_OBSERVATION_CHANGED);
            }
        } if (requestCode == NEW_ID_REQUEST_CODE) {
    		if (resultCode == RESULT_OK) {


    			// Add the ID
    			final Integer taxonId = data.getIntExtra(IdentificationActivity.TAXON_ID, 0);
                String taxonName = data.getStringExtra(IdentificationActivity.TAXON_NAME);
                String speciesGuess = data.getStringExtra(IdentificationActivity.SPECIES_GUESS);
    			final String idRemarks = data.getStringExtra(IdentificationActivity.ID_REMARKS);
                final boolean fromSuggestion = data.getBooleanExtra(IdentificationActivity.FROM_SUGGESTION, false);

    			checkForTaxonDisagreement(taxonId, taxonName, speciesGuess, new onDisagreement() {
                    @Override
                    public void onDisagreement(boolean disagreement) {
                        Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_IDENTIFICATION, null, ObservationViewerActivity.this, INaturalistService.class);
                        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                        serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
                        serviceIntent.putExtra(INaturalistService.IDENTIFICATION_BODY, idRemarks);
                        serviceIntent.putExtra(INaturalistService.DISAGREEMENT, disagreement);
                        serviceIntent.putExtra(INaturalistService.FROM_VISION, fromSuggestion);
                        ContextCompat.startForegroundService(ObservationViewerActivity.this, serviceIntent);

                        try {
                            JSONObject eventParams = new JSONObject();
                            eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_VIEW_OBS_ADD);
                            eventParams.put(AnalyticsClient.EVENT_PARAM_FROM_VISION_SUGGESTION, fromSuggestion);

                            AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_ADD_ID, eventParams);
                        } catch (JSONException e) {
                            Logger.tag(TAG).error(e);
                        }


                        // Show a loading progress until the new comments/IDs are loaded
                        mCommentsIds = null;
                        refreshActivity();

                        // Refresh the comment/id list
                        mReloadTaxon = true;
                        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                        BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, ObservationViewerActivity.this);

                    }
                });


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
            serviceIntent2.putExtra(INaturalistService.GET_PROJECTS, false);
            ContextCompat.startForegroundService(this, serviceIntent2);

        } else if (requestCode == OBSERVATION_PHOTOS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Integer setFirstPhotoIndex = data.getIntExtra(ObservationPhotosViewer.SET_DEFAULT_PHOTO_INDEX, -1);
                Integer deletePhotoIndex = data.getIntExtra(ObservationPhotosViewer.DELETE_PHOTO_INDEX, -1);
                Integer duplicatePhotoIndex = data.getIntExtra(ObservationPhotosViewer.DUPLICATE_PHOTO_INDEX, -1);

                if (data.hasExtra(ObservationPhotosViewer.REPLACED_PHOTOS)) {
                    // Photos the user has edited
                    String rawReplacedPhotos = data.getStringExtra(ObservationPhotosViewer.REPLACED_PHOTOS);
                    rawReplacedPhotos = rawReplacedPhotos.substring(1, rawReplacedPhotos.length() - 1);
                    String parts[] = rawReplacedPhotos.split(",");
                    List<Pair<Uri, Long>> replacedPhotos = new ArrayList<>();
                    for (String value : parts) {
                        value = value.trim();
                        String[] innerParts = value.substring(5, value.length() - 1).split(" ", 2);
                        replacedPhotos.add(new Pair<Uri, Long>(Uri.parse(innerParts[0]), Long.valueOf(innerParts[1])));
                    }

                    for (Pair<Uri, Long> replacedPhoto : replacedPhotos) {
                        int index = replacedPhoto.second.intValue();
                        Uri photoUri = replacedPhoto.first;

                        // Delete old photo
                        Logger.tag(TAG).debug(String.format(Locale.ENGLISH, "Replacing/deleting previous photo at index: %d: %s", index, photoUri));
                        deletePhoto(index, false);

                        // Add new photo instead
                        Uri createdUri = createObservationPhotoForPhoto(photoUri, index, false);
                    }

                    reloadPhotos();
                }

                if (setFirstPhotoIndex > -1) {
                    // Set photo as first
                    if (setFirstPhotoIndex < mPhotosAdapter.getPhotoCount()) {
                        mPhotosAdapter.setAsFirstPhoto(setFirstPhotoIndex);
                    }

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_NEW_DEFAULT_PHOTO);
                } else if (deletePhotoIndex > -1) {
                    // Delete photo
                    deletePhoto(deletePhotoIndex, true);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_OBS_DELETE_PHOTO);
                } else if (duplicatePhotoIndex > -1) {
                    // Duplicate photo
                    duplicatePhoto(duplicatePhotoIndex);
                }
            }
        }
    }


    private interface  onDisagreement {
        void onDisagreement(boolean disagreement);
    }

    private void checkForTaxonDisagreement(int taxonId, String name, String scientificName, final onDisagreement cb) {
        if ((mTaxonJson == null) || (mObsJson == null)) {
            // We don't have the JSON structures for the observation and the taxon - cannot check for possible disagreement
            cb.onDisagreement(false);
            return;
        }

        BetterJSONObject taxon = new BetterJSONObject(mTaxonJson);
        int communityTaxonId = taxon.getInt("id");
        JSONArray ancestors = taxon.getJSONArray("ancestor_ids").getJSONArray();

        // See if the current ID taxon is an ancestor of the current observation taxon / community taxon
        boolean disagreement = false;
        for (int i = 0; i < ancestors.length(); i++) {
            int currentTaxonId = ancestors.optInt(i);

            if (currentTaxonId == taxonId) {
                disagreement = true;
                break;
            }
        }

        if ((!disagreement) || (communityTaxonId == taxonId))  {
            // No disagreement here
            cb.onDisagreement(false);
            return;
        }

        // Show disagreement dialog
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup dialogContent = (ViewGroup) inflater.inflate(R.layout.explicit_disagreement, null, false);

        TextView questionText = (TextView) dialogContent.findViewById(R.id.question);
        final RadioButton disagreementRadioButton = (RadioButton) dialogContent.findViewById(R.id.disagreement);
        final RadioButton noDisagreemenRadioButton = (RadioButton) dialogContent.findViewById(R.id.no_disagreement);

        String taxonName = String.format("%s (%s)", name, scientificName);
        String communityTaxonName = String.format("%s (%s)", TaxonUtils.getTaxonName(this, taxon.getJSONObject()), TaxonUtils.getTaxonScientificName(mApp, taxon.getJSONObject()));

        questionText.setText(Html.fromHtml(String.format(getString(R.string.do_you_think_this_could_be), communityTaxonName)));
        noDisagreemenRadioButton.setText(Html.fromHtml(String.format(getString(R.string.i_dont_know_but), taxonName)));
        disagreementRadioButton.setText(Html.fromHtml(String.format(getString(R.string.no_but_it_is_a_member_of_taxon), taxonName)));

        mHelper.confirm(getString(R.string.potential_disagreement), dialogContent, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean isDisagreement = disagreementRadioButton.isChecked();
                cb.onDisagreement(isDisagreement);
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Canceled dialog - nothing to do here
            }
        }, R.string.submit, R.string.cancel);
    }

    private void refreshProjectList() {
        if ((mProjects != null) && (mProjects.size() > 0)) {
            mIncludedInProjectsContainer.setVisibility(View.VISIBLE);
            int count = mProjects.size();
            mIncludedInProjects.setText(getResources().getQuantityString(R.plurals.included_in_projects, count, count));
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
        BaseFragmentActivity.safeUnregisterReceiver(mAttributesReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mChangeAttributesReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mDownloadObservationReceiver, this);

        if (mPhotosAdapter != null) {
            mPhotosAdapter.pause();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    private void deletePhoto(int position, boolean refreshPositions) {
        PhotosViewPagerAdapter adapter = mPhotosAdapter;
        if (position >= adapter.getPhotoCount()) {
            return;
        }

        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(position);

        ObservationPhoto op = new ObservationPhoto(cursor);

        // Mark photo as deleted
        Logger.tag(TAG).debug(String.format("Marking photo for deletion: %s", op.toString()));
        ContentValues cv = new ContentValues();
        cv.put(ObservationPhoto.IS_DELETED, 1);
        int updateCount = getContentResolver().update(op.getUri(), cv, null, null);

        reloadPhotos();

        if (refreshPositions) {
            // Refresh the positions of all other photos
            adapter = mPhotosAdapter;
            adapter.refreshPhotoPositions(null, false);
        }
    }

    private void duplicatePhoto(int position) {
        PhotosViewPagerAdapter adapter = mPhotosAdapter;
        if (position >= adapter.getPhotoCount()) {
            return;
        }

        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(position);

        ObservationPhoto op = new ObservationPhoto(cursor);

        // Add a duplicate of this photo (in a position ahead of this one)

        // Copy file
        String photoUrl = op.photo_url;
        String photoFileName = op.photo_filename;
        final File destFile = new File(getFilesDir(), UUID.randomUUID().toString() + ".jpeg");

        Logger.tag(TAG).info("Duplicate: " + op + ":" + photoFileName + ":" + photoUrl);

        if (photoFileName != null) {
            // Local file - copy it
            try {
                FileUtils.copyFile(new File(photoFileName), destFile);
                addDuplicatedPhoto(op, destFile);
            } catch (IOException e) {
                Logger.tag(TAG).error(e);
                Toast.makeText(getApplicationContext(), getString(R.string.couldnt_duplicate_photo), Toast.LENGTH_SHORT).show();
                return;
            }

        } else {
            // Online only - need to download it and then copy
            Glide.with(this)
                    .asBitmap()
                    .load(photoUrl)
                    .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.AUTOMATIC))
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            // Save downloaded bitmap into local file
                            try {
                                OutputStream outStream = new FileOutputStream(destFile);
                                resource.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
                                outStream.close();
                                addDuplicatedPhoto(op, destFile);
                            } catch (Exception e) {
                                Logger.tag(TAG).error(e);
                                Toast.makeText(getApplicationContext(), getString(R.string.couldnt_duplicate_photo), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Logger.tag(TAG).error("onLoadedFailed");
                            Toast.makeText(getApplicationContext(), getString(R.string.couldnt_duplicate_photo), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void addDuplicatedPhoto(ObservationPhoto originalPhoto, File duplicatedPhotoFile) {
        // Create new photo observation with the duplicated photo file

        Uri createdUri = createObservationPhotoForPhoto(Uri.fromFile(duplicatedPhotoFile), originalPhoto.position, true);

        if (createdUri == null) {
            Logger.tag(TAG).error("addDuplicatedPhoto - couldn't create duplicate OP");
            Toast.makeText(getApplicationContext(), getString(R.string.couldnt_duplicate_photo), Toast.LENGTH_SHORT).show();
            return;
        }

        reloadPhotos();

        // Refresh the positions of all other photos
        mPhotosAdapter.refreshPhotoPositions(originalPhoto.position, false);
    }


    private Uri createObservationPhotoForPhoto(Uri photoUri, int position, boolean isDuplicated) {
        String path = FileUtils.getPath(this, photoUri);
        String extension = FileUtils.getExtension(this, photoUri);

        if ((extension == null) && (path != null)) {
            int i = path.lastIndexOf('.');
            if (i >= 0) {
                extension = path.substring(i + 1).toLowerCase();
            }
        }

        if ((extension == null) || (
                (!extension.toLowerCase().equals("jpg")) &&
                        (!extension.toLowerCase().equals("jpeg")) &&
                        (!extension.toLowerCase().equals("png"))
        )) {
            return null;
        }

        // Resize photo to 2048x2048 max
        String resizedPhoto = ImageUtils.resizeImage(this, path, photoUri, 2048);

        if (resizedPhoto == null) {
            return null;
        }


        ObservationPhoto op = new ObservationPhoto();

        op.uuid = UUID.randomUUID().toString();

        ContentValues cv = op.getContentValues();
        cv.put(ObservationPhoto._OBSERVATION_ID, mObservation._id);
        cv.put(ObservationPhoto.OBSERVATION_ID, mObservation.id);
        cv.put(ObservationPhoto.PHOTO_FILENAME, resizedPhoto);
        cv.put(ObservationPhoto.ORIGINAL_PHOTO_FILENAME, (String)null);
        cv.put(ObservationPhoto.POSITION, position);
        cv.put(ObservationPhoto.OBSERVATION_UUID, mObservation.uuid);

        return getContentResolver().insert(ObservationPhoto.CONTENT_URI, cv);
    }


    // Returns a minimal version of an observation JSON (used to lower memory usage)
    private JSONObject getMinimalObservation(JSONObject observation) {
        JSONObject minimaldObs = new JSONObject();

        try {
            minimaldObs.put("id", observation.optInt("id"));
            if (observation.has("votes") && !observation.isNull("votes")) minimaldObs.put("votes", observation.optJSONArray("votes"));
            if (observation.has("observed_on") && !observation.isNull("observed_on")) minimaldObs.put("observed_on", observation.optString("observed_on"));
            if (observation.has("location") && !observation.isNull("location")) minimaldObs.put("location", observation.optString("location"));
            if (observation.has("photos") && !observation.isNull("photos")) {
                minimaldObs.put("photo_count", observation.optJSONArray("photos").length());
            } else {
                minimaldObs.put("photo_count", 0);
            }
            if (observation.has("identifications") && !observation.isNull("identifications")) {
                minimaldObs.put("identification_count", observation.optJSONArray("identifications").length());
            } else {
                minimaldObs.put("identification_count", 0);
            }
            if (observation.has("community_taxon") && !observation.isNull("community_taxon")) minimaldObs.put("community_taxon", observation.optJSONObject("community_taxon"));

        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
            return null;
        }

        return minimaldObs;
    }
}
