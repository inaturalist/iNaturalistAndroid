package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.cocosw.bottomsheet.BottomSheet;
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;

public class ObservationViewerActivity extends SherlockFragmentActivity {
    private static final int PROJECT_SELECTOR_REQUEST_CODE = 0x100;
    private static final int NEW_ID_REQUEST_CODE = 0x101;

    private static String TAG = "ObservationViewerActivity";

    private static int DATA_QUALITY_CASUAL_GRADE = 0;
    private static int DATA_QUALITY_NEEDS_ID = 1;
    private static int DATA_QUALITY_RESERCH_GRADE = 2;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private Observation mObservation;

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
        private Cursor mImageCursor;

        public PhotosViewPagerAdapter() {
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

        @Override
        public int getCount() {
            return mImageCursor.getCount();
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
            mImageCursor.moveToPosition(position);

            ImageView imageView = new ImageView(ObservationViewerActivity.this);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            int imageId = mImageCursor.getInt(mImageCursor.getColumnIndexOrThrow(ObservationPhoto._ID));
            int photoId = mImageCursor.getInt(mImageCursor.getColumnIndexOrThrow(ObservationPhoto._PHOTO_ID));
            String imageUrl = mImageCursor.getString(mImageCursor.getColumnIndexOrThrow(ObservationPhoto.PHOTO_URL));

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
                        imageView.setImageBitmap(bitmapImage);
                    }
                }
            }

            imageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(ObservationViewerActivity.this, ObservationPhotosViewer.class);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObservation._id);
                    intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, true);
                    intent.putExtra(ObservationPhotosViewer.READ_ONLY, true);
                    intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, position);
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

        Intent intent = getIntent();


		if (savedInstanceState == null) {
			// Do some setup based on the action being performed.
			Uri uri = intent.getData();
			if (uri == null) {
				Log.e(TAG, "Null URI from intent.getData");
				finish();
				return;
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
		}

        if (mCursor == null) {
            mCursor = managedQuery(mUri, Observation.PROJECTION, null, null, null);
        } else {
            mCursor.requery();
        }

        if (mObservation == null) {
            mObservation = new Observation(mCursor);
        }

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

    private void refreshFavorites() {
        if (mFavorites == null) {
            // Still loading
            mLoadingFavs.setVisibility(View.VISIBLE);
            mFavoritesList.setVisibility(View.GONE);
            mAddFavorite.setVisibility(View.GONE);
            return;
        }

        mLoadingFavs.setVisibility(View.GONE);
        mFavoritesList.setVisibility(View.VISIBLE);
        mAddFavorite.setVisibility(View.VISIBLE);
        
        mFavoritesAdapter = new FavoritesAdapter(this, mFavorites);
        mFavoritesList.setAdapter(mFavoritesAdapter);

        mAddFavorite.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_FAVORITE, null, ObservationViewerActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.id);
                startService(serviceIntent);
            }
        });
    }

    private void refreshActivity() {
        if (mCommentsIds == null) {
            // Still loading
            mLoadingActivity.setVisibility(View.VISIBLE);
            mCommentsIdsList.setVisibility(View.GONE);
            mActivityButtons.setVisibility(View.GONE);
            return;
        }

        mLoadingActivity.setVisibility(View.GONE);
        mCommentsIdsList.setVisibility(View.VISIBLE);
        mActivityButtons.setVisibility(View.VISIBLE);

        mAdapter = new CommentsIdsAdapter(this, mCommentsIds, mObservation.taxon_id, new CommentsIdsAdapter.OnIDAdded() {
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
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.getUiSettings().setAllGesturesEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(false);

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                Log.e("AAA", "CLICK");
            }
        });

        if ((mObservation.latitude != null) && (mObservation.longitude != null)) {
            LatLng latLng = new LatLng(mObservation.latitude, mObservation.longitude);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

            // Add the marker
            mMap.clear();
            MarkerOptions opts = new MarkerOptions().position(latLng).icon(INaturalistMapActivity.observationIcon(mObservation.iconic_taxon_name));
            Marker m = mMap.addMarker(opts);

            mLocationMapContainer.setVisibility(View.VISIBLE);
            mUnknownLocationIcon.setVisibility(View.GONE);
            // TODO: What if no place guess?
            mLocationText.setText(mObservation.place_guess);
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
        } else {
            // Unknown location
            mLocationMapContainer.setVisibility(View.GONE);
            mUnknownLocationIcon.setVisibility(View.VISIBLE);
            mLocationText.setText(R.string.unable_to_acquire_location);
            mLocationText.setGravity(View.TEXT_ALIGNMENT_CENTER);
            mLocationPrivate.setVisibility(View.GONE);
        }
    }

    private void setupTabs() {
        mTabHost.setup();

        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_INFO).setIndicator("", getResources().getDrawable(R.drawable.ic_info_tab)));
        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_COMMENTS_IDS).setIndicator("", getResources().getDrawable(R.drawable.ic_forum_tab)));
        addTab(mTabHost, mTabHost.newTabSpec(VIEW_TYPE_FAVS).setIndicator("", getResources().getDrawable(R.drawable.ic_star_tab)));

        mTabHost.getTabWidget().getChildAt(0).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(1).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(2).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));


        mInfoTabContainer.setVisibility(View.VISIBLE);
        mActivityTabContainer.setVisibility(View.GONE);

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

        if ((mObservation.longitude == null) || (mObservation.latitude == null)) {
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
        } else if (mObservation.captive) {
            // Captive
            dataQuality = DATA_QUALITY_CASUAL_GRADE;
            reasonText = R.string.casual_grade_captive;
        } else if ((mObservation.id_please) && (mIdCount <= 1)) {
            dataQuality = DATA_QUALITY_NEEDS_ID;
            reasonText = R.string.needs_id_more_ids;
        } else {
            dataQuality = DATA_QUALITY_RESERCH_GRADE;
        }

        // TODO - "Observation is casual grade because the community voted that they cannot identify it from the photo."
        // TODO - "Observation needs finer identifications from the community to become "Research Grade" status.

        int gray = Color.parseColor("#CBCBCB");
        int green = Color.parseColor("#CBCBCB");
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
            mResearchGradeLine.setBackgroundColor(gray);
            mResearchGradeText.setTextColor(gray);
            mResearchGradeIcon.setBackgroundResource(R.drawable.circular_border_thick_gray);
            mResearchGradeIcon.setImageResource(R.drawable.transparent);
        }


        if (reasonText != 0) {
            mTipText.setText(Html.fromHtml(getString(reasonText)));
            mDataQualityReason.setVisibility(View.VISIBLE);
        } else {
            mDataQualityReason.setVisibility(View.GONE);
        }
    }


    private void loadObservationIntoUI() {
        SharedPreferences pref = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String username = pref.getString("username", null);
        String userIconUrl = pref.getString("user_icon_url", null);

        mUserName.setText(mObservation.user_login);
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
        }

        mObservedOn.setText(formatObservedOn(mObservation.observed_on, mObservation.time_observed_at));

        mPhotosViewPager.setAdapter(new PhotosViewPagerAdapter());
        mIndicator.setViewPager(mPhotosViewPager);

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
                Intent intent = new Intent(ObservationViewerActivity.this, GuideTaxonActivity.class);
                if (mTaxon == null) {
                    // Could't download the taxon details - use whatever we currently got
                    mTaxon = new JSONObject();
                    try {
                        mTaxon.put("id", mObservation.taxon_id);
                        mTaxon.put("common_name", mObservation.species_guess);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                intent.putExtra("taxon", new BetterJSONObject(mTaxon));
                intent.putExtra("guide_taxon", false);
                startActivity(intent);
            }
        });

        mIdPic.setImageResource(ObservationPhotosViewer.observationIcon(mObservation.toJSONObject()));
        mIdName.setText(mObservation.species_guess);
        mTaxonicName.setVisibility(View.GONE);

        if (mObservation.taxon_id == null) {
            mIdName.setText(R.string.unknown_species);
        } else {
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
                Intent intent = new Intent(ObservationViewerActivity.this, ProjectSelectorActivity.class);
                intent.putExtra(INaturalistService.OBSERVATION_ID, (mObservation.id == null ? mObservation._id : mObservation.id));
                intent.putExtra(ProjectSelectorActivity.IS_CONFIRMATION, true);
                intent.putExtra(ProjectSelectorActivity.PROJECT_FIELDS, new HashMap<Integer, ProjectFieldValue>());
                intent.putIntegerArrayListExtra(INaturalistService.PROJECT_ID, mProjectIds);
                startActivityForResult(intent, PROJECT_SELECTOR_REQUEST_CODE);
            }
        });
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
            finish();
            return true;
        case R.id.edit_observation:
            startActivity(new Intent(Intent.ACTION_EDIT, mUri, this, ObservationEditor.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.observation_viewer_menu, menu);
        return true;
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mObservation", mObservation);
        outState.putInt("mIdCount", mIdCount);
        super.onSaveInstanceState(outState);
    }

    private class ObservationReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mObservationReceiver);

	        Observation observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);

	        if (observation == null) {
	            // Couldn't retrieve observation details (probably deleted)
	            mCommentsIds = new ArrayList<BetterJSONObject>();
                mFavorites = new ArrayList<BetterJSONObject>();
	            refreshActivity();
                refreshFavorites();
	            return;
	        }

	        JSONArray comments = observation.comments.getJSONArray();
	        JSONArray ids = observation.identifications.getJSONArray();
            JSONArray favs = observation.favorites.getJSONArray();
	        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();
            ArrayList<BetterJSONObject> favResults = new ArrayList<BetterJSONObject>();

            mIdCount = 0;

	        try {
	            for (int i = 0; i < comments.length(); i++) {
	                BetterJSONObject comment = new BetterJSONObject(comments.getJSONObject(i));
	                comment.put("type", "comment");
	                results.add(comment);
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

	        int taxonId = (observation.taxon_id == null ? 0 : observation.taxon_id);
            refreshActivity();
            refreshFavorites();

	        Handler handler = new Handler();
	        handler.postDelayed(new Runnable() {
	        	@Override
	        	public void run() {
	        		int height = setListViewHeightBasedOnItems(mCommentsIdsList);

                    View background = findViewById(R.id.comment_id_list_background);
                    ViewGroup.LayoutParams params2 =  background.getLayoutParams();
                    params2.height = height;
                    background.requestLayout();

                    setListViewHeightBasedOnItems(mFavoritesList);
	        	}
	        }, 100);

            refreshDataQuality();
	    }

	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PROJECT_SELECTOR_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                ArrayList<Integer> projectIds = data.getIntegerArrayListExtra(ProjectSelectorActivity.PROJECT_IDS);
                HashMap<Integer, ProjectFieldValue> values = (HashMap<Integer, ProjectFieldValue>) data.getSerializableExtra(ProjectSelectorActivity.PROJECT_FIELDS);
                mProjectIds = projectIds;

                refreshProjectList();
            }
        } else if (requestCode == NEW_ID_REQUEST_CODE) {
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
    	}
    }

    private void refreshProjectList() {
        if ((mProjectIds != null) && (mProjectIds.size() > 0)) {
            mIncludedInProjectsContainer.setVisibility(View.VISIBLE);
            mIncludedInProjects.setText(String.format(getString(R.string.included_in_projects), mProjectIds.size()));
        } else {
            mIncludedInProjectsContainer.setVisibility(View.GONE);
        }
    }


    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param listView to be resized
     */
    public int setListViewHeightBasedOnItems(ListView listView) {

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
