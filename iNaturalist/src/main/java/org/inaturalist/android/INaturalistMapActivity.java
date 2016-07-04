package org.inaturalist.android;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;

import org.inaturalist.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.flurry.android.FlurryAgent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLoadedCallback;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.LatLngBounds.Builder;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.VisibleRegion;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class INaturalistMapActivity extends BaseFragmentActivity implements OnMarkerClickListener, OnInfoWindowClickListener, OnTabChangeListener {
    public final static String TAG = "INaturalistMapActivity";
    private GoogleMap mMap;
    private Circle mCircle;
    private NearbyObservationsReceiver mNearbyReceiver;
    private ActivityHelper mHelper;
    private HashMap<String, JSONObject> mMarkerObservations;
    private INaturalistApp mApp;
	private ActionBar mTopActionBar;
	private ListView mSearchResults;
	private EditText mSearchText;
	private View mSearchBar;
	private View mSearchToggle;
	private View mSearchBarBackground;
	protected String mCurrentSearch;
	protected int mSearchType;
	private View mActiveFilters;
	private View mRestricToMap;
	private View mRefreshView;
	private View mLoading;
	private boolean mActiveSearch;
	protected Integer mTaxonId;
	protected String mTaxonName;
	private View mCancelFilters;
	private View mCancelRestricToMap;
	private TextView mActiveFiltersDescription;
	private String mUsername;
	private String mFullName;
	private String mLocationName;
	private Integer mLocationId;
	private String mProjectName;
	private Integer mProjectId;
	
	private GoogleApiClient mLocationClient;
	private double mMinx;
	private double mMaxx;
	private double mMiny;
	private double mMaxy;
	private float mZoom;
	private Intent mServiceIntent;
	private TabHost mTabHost;
	private View mSearchToggle2;
	private View mGridContainer;
	private View mMapContainer;
	private View mListContainer;
	private String mViewType;
	private ProgressBar mLoadingObservationsGrid;
	private ProgressBar mLoadingObservationsList;
	private TextView mObservationsGridEmpty;
	private TextView mObservationsListEmpty;
	private GridViewExtended mObservationsGrid;
	private ListView mObservationsList;
	private ObservationGridAdapter mGridAdapter;
	private boolean mClearMapLimit;
	private List<JSONObject> mObservations;
	private int mPage;
	private boolean mIsLoading;
	private ObservationListAdapter mListAdapter;
	
	private final static int NO_SEARCH = -1;
	private final static int FIND_NEAR_BY_OBSERVATIONS = 0;
	private final static int FIND_MY_OBSERVATIONS = 1;
	private final static int FIND_CRITTERS = 0;
	private final static int FIND_PEOPLE = 1;
	private final static int FIND_LOCATIONS = 2;
	private final static int FIND_PROJECTS = 3;

	private final static String VIEW_TYPE_MAP = "map";
	private final static String VIEW_TYPE_GRID = "grid";
	private final static String VIEW_TYPE_LIST = "list";
	
	private final static String ID_PLEASE_TAG_TEXT_COLOR = "#85743D";
	private final static String ID_PLEASE_TAG_BACKGROUND_COLOR = "#FFEE91";
	private final static String RESEARCH_TAG_TEXT_COLOR = "#529214";
	private final static String RESEARCH_TAG_BACKGROUND_COLOR = "#DCEEA3";
    private int mObservationListIndex;
    private int mObservationListOffset;
    private int mObservationGridOffset;
    private int mObservationGridIndex;

	private static final int VIEW_OBSERVATION_REQUEST_CODE = 0x100;
	private View mProjectInfo;
    private BetterJSONObject mProject;

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

    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.NoActionBarShadowTheme);
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.map);
	    onDrawerCreate(savedInstanceState);

		getSupportActionBar().setElevation(0);

	    mLoadingObservationsList = (ProgressBar) findViewById(R.id.loading_observations_list);
	    mObservationsListEmpty = (TextView) findViewById(R.id.observations_list_empty);
	    mObservationsList = (ListView) findViewById(R.id.observations_list);

	    mLoadingObservationsGrid = (ProgressBar) findViewById(R.id.loading_observations_grid);
	    mObservationsGridEmpty = (TextView) findViewById(R.id.observations_grid_empty);
	    mObservationsGrid = (GridViewExtended) findViewById(R.id.observations_grid);

		mObservations = new ArrayList<JSONObject>();

	    if (savedInstanceState != null) {
	    	mCurrentSearch = savedInstanceState.getString("mCurrentSearch");
	    	mSearchType = savedInstanceState.getInt("mSearchType");
	    	mViewType = savedInstanceState.getString("mViewType");
	    	mClearMapLimit = savedInstanceState.getBoolean("mClearMapLimit");

	    	mTaxonId = (Integer) savedInstanceState.getSerializable("mTaxonId");
	    	mTaxonName = savedInstanceState.getString("mTaxonName");

	    	mUsername = savedInstanceState.getString("mUsername");
	    	mFullName = savedInstanceState.getString("mFullName");

	    	mLocationId = (Integer) savedInstanceState.getSerializable("mLocationId");
	    	mLocationName = savedInstanceState.getString("mLocationName");

	    	mProjectId = (Integer) savedInstanceState.getSerializable("mProjectId");
	    	mProjectName = savedInstanceState.getString("mProjectName");

	    	mMinx = savedInstanceState.getDouble("minx");
	    	mMaxx = savedInstanceState.getDouble("maxx");
	    	mMiny = savedInstanceState.getDouble("miny");
	    	mMaxy = savedInstanceState.getDouble("maxy");
	    	mZoom = savedInstanceState.getFloat("zoom");

	    	mPage = savedInstanceState.getInt("mPage");
	    	mIsLoading = false;

	    	try {
	    		String obsString = savedInstanceState.getString("mObservations");
				JSONArray arr = new JSONArray();
				if (obsString != null) arr = new JSONArray(obsString);
				if (mObservations == null) {
					mObservations = new ArrayList<JSONObject>();
				}
				mObservations.clear();
				for (int i = 0; i < arr.length(); i++) {
					mObservations.add(arr.getJSONObject(i));
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}

            if (mViewType.equals(VIEW_TYPE_LIST)) {
                mObservationListIndex = savedInstanceState.getInt("mObservationListIndex");
                mObservationListOffset = savedInstanceState.getInt("mObservationListOffset");
            } else if (mViewType.equals(VIEW_TYPE_GRID)) {
                mObservationGridIndex = savedInstanceState.getInt("mObservationGridIndex");
                mObservationGridOffset = savedInstanceState.getInt("mObservationGridOffset");
            }
	    	
	    	mLoadingObservationsGrid.setVisibility(View.GONE);
	    	mLoadingObservationsList.setVisibility(View.GONE);

	    	if (mObservations.size() == 0) {
	    		mObservationsGridEmpty.setVisibility(View.VISIBLE);
	    		mObservationsListEmpty.setVisibility(View.VISIBLE);
	    	} else {
	    		mObservationsGridEmpty.setVisibility(View.GONE);
	    		mObservationsListEmpty.setVisibility(View.GONE);
	    	}

	    } else {
	    	mCurrentSearch = "";
	    	mSearchType = NO_SEARCH;
	    	mTaxonId = null;
	    	mUsername = null;
	    	mLocationId = null;
	    	mProjectId = null;
	    	mViewType = VIEW_TYPE_MAP;
	    	mClearMapLimit = false;
	    	mPage = 1;
	    	mIsLoading = false;
	    }
	    
        mTopActionBar = getSupportActionBar();
        mTopActionBar.setDisplayShowCustomEnabled(true);
        mTopActionBar.setCustomView(R.layout.explore_action_bar);
        
        mSearchBar = (View)findViewById(R.id.search_bar);
        mSearchBarBackground = (View)findViewById(R.id.search_bar_background);
        mSearchBarBackground.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mSearchToggle.performClick();
			}
		});

        mRefreshView = (View) mTopActionBar.getCustomView().findViewById(R.id.refresh);
        mRefreshView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mActiveSearch = false;
				mPage = 1;
				loadObservations();
			}
		});
        mLoading = (View) mTopActionBar.getCustomView().findViewById(R.id.loading);
        
        mSearchToggle = (View) mTopActionBar.getCustomView().findViewById(R.id.search);
        mSearchToggle.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mSearchBar.getVisibility() == View.GONE) {
					mSearchBar.setVisibility(View.VISIBLE);
					mSearchBarBackground.setVisibility(View.VISIBLE);
				} else {
					mSearchBar.setVisibility(View.GONE);
					mSearchBarBackground.setVisibility(View.GONE);
					mSearchText.clearFocus();
					InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
					mSearchText.setText("");
				}
			}
		});
        mSearchToggle2 = (View) mTopActionBar.getCustomView().findViewById(R.id.middle_bar);
        mSearchToggle2.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mSearchToggle.performClick();
			}
        });
	       
        mSearchResults = (ListView)findViewById(R.id.search_results);
        mSearchResults.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int index, long arg3) {
				mCurrentSearch = mSearchText.getText().toString().trim();
				mSearchType = index;

				mSearchToggle.performClick();
				mActiveSearch = true;
				loadObservations();
			}
		});

        prepareSearchResults("");

        mSearchText = (EditText)findViewById(R.id.search_filter);
        mSearchText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				prepareSearchResults(s.toString());
			}
		});
        mSearchText.setOnEditorActionListener(
				new EditText.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						final boolean isEnterEvent = event != null
								&& event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
						final boolean isEnterUpEvent = isEnterEvent && event.getAction() == KeyEvent.ACTION_UP;
						final boolean isEnterDownEvent = isEnterEvent && event.getAction() == KeyEvent.ACTION_DOWN;

						if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterUpEvent) {
							// Do your action here
							mSearchResults.performItemClick(null, 0, 0);
							return true;
						} else if (isEnterDownEvent) {
							// Capture this event to receive ACTION_UP
							return true;
						} else {
							// We do not care on other actions
							return false;
						}
					}
				});

        mActiveFilters = (View)findViewById(R.id.active_filters);
        mActiveFiltersDescription = (TextView)findViewById(R.id.filter_name);
        mRestricToMap = (View)findViewById(R.id.restric_to_map);
        
        mCancelFilters = (View)findViewById(R.id.cancel_filters);
		mProjectInfo = (View)findViewById(R.id.project_info);
        mCancelRestricToMap = (View)findViewById(R.id.cancel_restrict_to_current_map);
        
        mCancelRestricToMap.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mClearMapLimit = true;
				mPage = 1;

				refreshActiveFilters();
				loadObservations();
			}
		});

		mProjectInfo.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
                if ((mProjectId == null) || (mProject == null)) {
                    return;
                }

                mProject.put("joined", INaturalistService.hasJoinedProject(INaturalistMapActivity.this, mProjectId));

                Intent intent = new Intent(INaturalistMapActivity.this, ProjectDetails.class);
                intent.putExtra("project", mProject);
                startActivity(intent);
			}
		});
        
        mCancelFilters.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				// Clear out all filters
				mCurrentSearch = "";
				mTaxonId = null;
				mProjectId = null;
                mProject = null;
				mUsername = null;
				mLocationId = null;
				mSearchType = FIND_NEAR_BY_OBSERVATIONS;
				mPage = 1;

				refreshActiveFilters();
				loadObservations();
			}
		});
        
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        
        INaturalistMapActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_MAP).setIndicator("", getResources().getDrawable(R.drawable.ic_map_black_24dp)));
        INaturalistMapActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_GRID).setIndicator("", getResources().getDrawable(R.drawable.ic_view_module_black_24dp)));
        INaturalistMapActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec(VIEW_TYPE_LIST).setIndicator("", getResources().getDrawable(R.drawable.ic_list_black_24dp)));

        mTabHost.getTabWidget().getChildAt(0).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(1).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));
        mTabHost.getTabWidget().getChildAt(2).setBackgroundDrawable(getResources().getDrawable(R.drawable.inatapptheme_tab_indicator_holo));

		mTabHost.getTabWidget().setDividerDrawable(null);

        mTabHost.setOnTabChangedListener(this);
        
        mGridContainer = findViewById(R.id.grid_container);
        mListContainer = findViewById(R.id.list_container);
        mMapContainer = findViewById(R.id.map_container);
        
        mObservationsList.setOnItemClickListener(new OnItemClickListener() {
        	@Override
        	public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
        		JSONObject item = (JSONObject) view.getTag();
				Intent intent = new Intent(INaturalistMapActivity.this, ObservationViewerActivity.class);
				intent.putExtra("observation", item.toString());
				intent.putExtra("read_only", true);
				startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);
        	}
        });


        mObservationsGrid.setOnItemClickListener(new OnItemClickListener() {
        	@Override
        	public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
        		JSONObject item = (JSONObject) view.getTag();
				Intent intent = new Intent(INaturalistMapActivity.this, ObservationViewerActivity.class);
				intent.putExtra("observation", item.toString());
				intent.putExtra("read_only", true);
				startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);
        	}
        });

        mObservationsList.setOnScrollListener(new OnScrollListener() {
        	@Override
        	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        		if((firstVisibleItem + visibleItemCount >= totalItemCount) && (totalItemCount > 0)) {
        			// End has been reached - load more observations
        			if ((mObservations != null) && (!mIsLoading) && (mPage >= 1)) {
        				mPage++;
        				reloadObservations();
        			}
        		}
        	}

        	@Override
        	public void onScrollStateChanged(AbsListView view, int scrollState){ }
        }); 

        
        mObservationsGrid.setOnScrollListener(new OnScrollListener() {
        	@Override
        	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        		if((firstVisibleItem + visibleItemCount >= totalItemCount) && (totalItemCount > 0)) {
        			// End has been reached - load more observations
        			if ((mObservations != null) && (!mIsLoading) && (mPage >= 1)) {
        				mPage++;
        				reloadObservations();
        			}
        		}
        	}

        	@Override
        	public void onScrollStateChanged(AbsListView view, int scrollState){ }
        }); 


    }
    
    @Override
    public void onTabChanged(String tag) {
    	mViewType = tag;

    	refreshViewType();
    }
    
    private void refreshViewType() {
    	if (mViewType.equals(VIEW_TYPE_MAP)) {
    		mTabHost.setCurrentTab(0);
    		mMapContainer.setVisibility(View.VISIBLE);
    		mGridContainer.setVisibility(View.GONE);
    		mListContainer.setVisibility(View.GONE);

    		if (mClearMapLimit) {
    			// Switched back from other view type, after the user removed the "restrict-to-current-map-area"
    			// filter - now we reset that filter and need to reload the observations
    			mClearMapLimit = false;
    			mActiveSearch = false;
    			mPage = 1;
    			loadObservations();
    		}

    	} else if (mViewType.equals(VIEW_TYPE_GRID)) {
    		mTabHost.setCurrentTab(1);
    		mMapContainer.setVisibility(View.GONE);
    		mGridContainer.setVisibility(View.VISIBLE);
    		mListContainer.setVisibility(View.GONE);

    	} else if (mViewType.equals(VIEW_TYPE_LIST)) {
    		mTabHost.setCurrentTab(2);
    		mMapContainer.setVisibility(View.GONE);
    		mGridContainer.setVisibility(View.GONE);
    		mListContainer.setVisibility(View.VISIBLE);
    	}
    	
    	refreshActiveFilters();
   	
    }

     // Method to add a TabHost
    private static void AddTab(INaturalistMapActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new MyTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

   
    
    // Loads observations according to current search criteria
    private void loadObservations() {
    	if (mCurrentSearch.length() == 0) {
    		switch (mSearchType) {
    		case NO_SEARCH:
    			mSearchType = FIND_NEAR_BY_OBSERVATIONS;
    			
    			String inatNetwork = mApp.getInaturalistNetworkMember();
    			final String countryCoordinates = mApp.getStringResourceByName("inat_country_coordinates_" + inatNetwork);

    			if ((countryCoordinates != null) && (countryCoordinates.length() > 0)) {
    				// Change initial view according to the iNat network settings (e.g. show Mexico)
    				String[] parts = countryCoordinates.split(",");
    				mMinx = Double.valueOf(parts[1]); // swlng
    				mMiny = Double.valueOf(parts[0]); // swlat
    				mMaxx = Double.valueOf(parts[3]); // nelng
    				mMaxy = Double.valueOf(parts[2]); // nelat

    				LatLngBounds bounds = new LatLngBounds(new LatLng(mMiny, mMinx), new LatLng(mMaxy, mMaxx));
    				mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));

    			} else {
    				getCurrentLocationAndLoadNearbyObservations();
    			}
    			break;

    		case FIND_NEAR_BY_OBSERVATIONS:
    			// Find observations near me
    			if (mActiveSearch) {
    				// Find out current location
    				getCurrentLocationAndLoadNearbyObservations();
    			} else {
    				// Load near by observations according to current map coordinates
    				reloadObservations();
    			}
    			break;
    		case FIND_MY_OBSERVATIONS:
    			// Find my observations
				if (!isLoggedIn()) {
					// User not logged-in - redirect to onboarding screen
					startActivity(new Intent(INaturalistMapActivity.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
					return;
				}

    			loadMyObservations();
    			if (mActiveSearch) {
    				refreshActiveFilters();
    			}

    			break;

    		}
    	} else {
    		// Find critters/people/projects/locations by name
    		if (mActiveSearch) {
    			// Show search dialog for critters/people/... first
    			findByName(mSearchType);
    		} else {
    			reloadObservations();
    		}
    	}
    }
    
    private void prepareSearchResults(String text) {
    	String[] results;
    	
    	if (text.length() == 0) {
    		results = getResources().getStringArray(R.array.explore_results_empty);
    	} else {
    		results = getResources().getStringArray(R.array.explore_results_with_text);
    		for (int i = 0; i < results.length; i++) {
    			results[i] = String.format(results[i], text);
    		}
    	}
    	
    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results) {
               @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View row;

                    if (null == convertView) {
                    	LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    	row = inflater.inflate(android.R.layout.simple_list_item_1, null);
                    } else {
                    	row = convertView;
                    }

                    TextView tv = (TextView) row.findViewById(android.R.id.text1);
                    tv.setTextColor(Color.BLACK);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    tv.setText(Html.fromHtml(getItem(position)));

                    return row;
                }    		
    	};
    	mSearchResults.setAdapter(adapter);
    	
    }

    @Override 
    public void onResume() {
        super.onResume();
        mHelper = new ActivityHelper(this);
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
        mNearbyReceiver = new NearbyObservationsReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_NEARBY);
        registerReceiver(mNearbyReceiver, filter);
        
        setUpMapIfNeeded();
       
        if (mSearchType != NO_SEARCH) {
	    	loadExistingObservations(true);
        	refreshActiveFilters();
        	refreshViewType();
        }

        if (mViewType.equals(VIEW_TYPE_LIST)) {
            mObservationsList.setSelectionFromTop(mObservationListIndex, mObservationListOffset);
        } else if (mViewType.equals(VIEW_TYPE_GRID)) {
            mObservationsGrid.setSelection(mObservationGridIndex);
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString("mCurrentSearch", mCurrentSearch);
        outState.putInt("mSearchType", mSearchType);
        outState.putString("mViewType", mViewType);
        outState.putBoolean("mClearMapLimit", mClearMapLimit);

        outState.putSerializable("mTaxonId", mTaxonId);
        outState.putString("mTaxonName", mTaxonName);

        outState.putString("mUsername", mUsername);
        outState.putString("mFullName", mFullName);

        outState.putSerializable("mLocationId", mLocationId);
        outState.putString("mLocationName", mLocationName);

        outState.putSerializable("mProjectId", mProjectId);
        outState.putSerializable("mProject", mProject);
        outState.putString("mProjectName", mProjectName);

		if (mMap != null) {
			VisibleRegion vr = mMap.getProjection().getVisibleRegion();
			outState.putDouble("minx", vr.farLeft.longitude);
			outState.putDouble("maxx", vr.farRight.longitude);
			outState.putDouble("miny", vr.nearLeft.latitude);
			outState.putDouble("maxy", vr.farRight.latitude);
		}

        if (mObservations != null) {
        	JSONArray arr = new JSONArray(mObservations);
        	outState.putString("mObservations", arr.toString());
        }

        outState.putInt("mPage", mPage);


        if (mViewType.equals(VIEW_TYPE_LIST)) {
            View firstVisibleRow = mObservationsList.getChildAt(0);

			if (firstVisibleRow != null && mObservationsList != null) {
				mObservationListOffset = firstVisibleRow.getTop() - mObservationsList.getPaddingTop();
				mObservationListIndex = mObservationsList.getFirstVisiblePosition();

				outState.putInt("mObservationListIndex", mObservationListIndex);
				outState.putInt("mObservationListOffset", mObservationListOffset);
			}

		} else if (mViewType.equals(VIEW_TYPE_GRID)) {
            View firstVisibleRow = mObservationsGrid.getChildAt(0);

			if (firstVisibleRow != null && mObservationsGrid != null) {

				mObservationGridOffset = firstVisibleRow.getTop() - mObservationsGrid.getPaddingTop();
				mObservationGridIndex = mObservationsGrid.getFirstVisiblePosition();

				outState.putInt("mObservationGridIndex", mObservationGridIndex);
				outState.putInt("mObservationGridOffset", mObservationGridOffset);
			}
		}

        super.onSaveInstanceState(outState);
    }

    
    private void setUpMapIfNeeded() {
        if (mMarkerObservations == null) {
            mMarkerObservations = new HashMap<String, JSONObject>();
        }
        if (mMap == null) {
            mMap = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                // The Map is verified. It is now safe to manipulate the map.
                mMap.setMyLocationEnabled(true);
                mMap.setOnMarkerClickListener(this);
                mMap.setOnInfoWindowClickListener(this);
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                
                mMap.setOnCameraChangeListener(new OnCameraChangeListener() {
                	@Override
                	public void onCameraChange(CameraPosition arg0) {
                		mClearMapLimit = false;
                		mActiveSearch = false;
                		mPage = 1;
                		loadObservations();
                	}
                });

                if ((mMiny != 0) && (mMinx != 0) && (mMaxy != 0) && (mMaxx != 0)) {
                	mMap.setOnMapLoadedCallback(new OnMapLoadedCallback() {
                		@Override
                		public void onMapLoaded() {
                			LatLngBounds bounds = new LatLngBounds(new LatLng(mMiny, mMinx), new LatLng(mMaxy, mMaxx));
                			mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
                		}
                	});
                } else {
                	mMap.setOnMapLoadedCallback(new OnMapLoadedCallback() {
                		@Override
                		public void onMapLoaded() {
                			if (mSearchType == NO_SEARCH) {
                				loadObservations();
                				refreshActiveFilters();
                			}
                		}
                	});

                }


            }
            
        }
        
        
    }


    private void loadMyObservations() {
        if (mMap == null) return;
        
        
	    SharedPreferences preferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
	    String username = preferences.getString("username", null);
	    
	    if (username == null) {
	    	Toast.makeText(getApplicationContext(), R.string.must_login_to_show_my_observations, Toast.LENGTH_LONG).show();
	    	return;
	    }
	    
	    mUsername = username;
	    mFullName = null;
	    mSearchType = FIND_PEOPLE;
 		reloadObservations();
       
    }
    
    private void addObservation(JSONObject o) throws JSONException {
    	if (o == null) return;
    	
        if ((!o.has("private_latitude") || o.isNull("private_latitude")) && (!o.has("latitude") || o.isNull("latitude"))) {
            return;
        }

        LatLng latLng;
        if ((o.has("private_latitude") && !o.isNull("private_latitude")) && mApp.currentUserLogin().equalsIgnoreCase(o.getString("user_login"))) {
            latLng = new LatLng(o.getDouble("private_latitude"), o.getDouble("private_longitude"));
        } else {
            latLng = new LatLng(o.getDouble("latitude"), o.getDouble("longitude"));
        }
        String iconicTaxonName = o.has("iconic_taxon_name") ? o.getString("iconic_taxon_name") : null;

        MarkerOptions opts = new MarkerOptions()
            .position(latLng)
            .icon(INaturalistMapActivity.observationIcon(iconicTaxonName));
        Marker m = mMap.addMarker(opts);
        mMarkerObservations.put(m.getId(), o);
    }
    
    private void showLoading() {
    	mIsLoading = true;
    	mRefreshView.setVisibility(View.GONE);
    	mLoading.setVisibility(View.VISIBLE);
    	
    	if (mPage == 1) {
    		// Only hide the grid/list views if this is the first observations result page being fetched
    		mObservationsGrid.setVisibility(View.GONE);
    		mObservationsGridEmpty.setVisibility(View.GONE);
    		mLoadingObservationsGrid.setVisibility(View.VISIBLE);
    		mObservationsList.setVisibility(View.GONE);
    		mLoadingObservationsList.setVisibility(View.VISIBLE);
    		mObservationsListEmpty.setVisibility(View.GONE);
    	}
    }
    
    private void hideLoading() {
    	mIsLoading = false;
    	mRefreshView.setVisibility(View.VISIBLE);
    	mLoading.setVisibility(View.GONE);

    	mLoadingObservationsGrid.setVisibility(View.GONE);
    	mLoadingObservationsList.setVisibility(View.GONE);

    	if (mPage == 1) {
    		// Only re-show the grid/list views if this is the first observations result page being fetched
    		if (mObservations.size() == 0) {
    			mObservationsGrid.setVisibility(View.GONE);
    			mObservationsGridEmpty.setVisibility(View.VISIBLE);
    			mObservationsList.setVisibility(View.GONE);
    			mObservationsListEmpty.setVisibility(View.VISIBLE);
    		} else {
    			mObservationsGrid.setVisibility(View.VISIBLE);
    			mObservationsGridEmpty.setVisibility(View.GONE);
    			mObservationsList.setVisibility(View.VISIBLE);
    			mObservationsListEmpty.setVisibility(View.GONE);
    		}
    	}
    }

    private void reloadObservations() {
        showLoading();

        if (mServiceIntent != null) {
            stopService(mServiceIntent);
        }

        mServiceIntent = new Intent(INaturalistService.ACTION_NEARBY, null, this, INaturalistService.class);

        /* prevent crash on devices without Google services installed */
        if (mMap == null) {
            return;
        }

        VisibleRegion vr = mMap.getProjection().getVisibleRegion();
        mServiceIntent.putExtra("minx", vr.farLeft.longitude);
        mServiceIntent.putExtra("maxx", vr.farRight.longitude);
        mServiceIntent.putExtra("miny", vr.nearLeft.latitude);
        mServiceIntent.putExtra("maxy", vr.farRight.latitude);
        mServiceIntent.putExtra("zoom", mMap.getCameraPosition().zoom);
        mServiceIntent.putExtra("page", mPage);
        if (mTaxonId != null) mServiceIntent.putExtra("taxon_id", mTaxonId.intValue());
        if (mUsername != null) mServiceIntent.putExtra("username", mUsername);
        if (mLocationId != null) mServiceIntent.putExtra("location_id", mLocationId.intValue());
        if (mProjectId != null) mServiceIntent.putExtra("project_id", mProjectId.intValue());
        mServiceIntent.putExtra("clear_map_limit", mClearMapLimit);

        startService(mServiceIntent);
    }

    private class NearbyObservationsReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_nearby_observations), error));
                return;
            }

            mMap.clear();
            mMarkerObservations.clear();
            
            SerializableJSONArray resultsJSON = (SerializableJSONArray) mApp.getServiceResult(INaturalistService.ACTION_NEARBY);
            JSONArray results = resultsJSON.getJSONArray();
            List<JSONObject> resultsArray = new ArrayList<JSONObject>();
            
            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }
            
            if (mActiveSearch) {
            	Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), results.length()), Toast.LENGTH_SHORT).show();
            	mActiveSearch = false;
            }
            
            if (mPage == 1) {
            	mObservations = resultsArray;
            	loadExistingObservations(true);
           
            } else {
            	// Append to existing observations list
            	if (resultsArray.size() < INaturalistService.NEAR_BY_OBSERVATIONS_PER_PAGE) {
            		// No more pages to fetch
            		mPage = -1;
            	} else {
                    // Prevent duplicate observation results
                    for (int i = 0; i < resultsArray.size(); i++) {
                        boolean found = false;
                        JSONObject currentResult = resultsArray.get(i);
                        for (int c = 0; c < mObservations.size(); c++) {
                            if (mObservations.get(c).optInt("id", -1) == currentResult.optInt("id", -1)) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            mObservations.add(currentResult);
                        }
                    }
            	}
            	loadExistingObservations(false);
           
            }
            
            hideLoading();
        }
    }
    
    private void loadExistingObservations(boolean refreshAdapters) {
    	if ((refreshAdapters) || (mGridAdapter == null) || (mListAdapter == null)) {
    		mGridAdapter = new ObservationGridAdapter(INaturalistMapActivity.this, mObservationsGrid.getColumnWidth(), mObservations);
    		mObservationsGrid.setAdapter(mGridAdapter);

    		mListAdapter = new ObservationListAdapter(INaturalistMapActivity.this, mObservations);
    		mObservationsList.setAdapter(mListAdapter);
    	} else {
    		mGridAdapter.notifyDataSetChanged();
    		mListAdapter.notifyDataSetChanged();
    	}

    	mMap.clear();
        mMarkerObservations.clear();
    	for (int i = 0; i < mObservations.size(); i++) {
    		JSONObject item = mObservations.get(i);
    		try {
				addObservation(item);
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	}

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        JSONObject o = mMarkerObservations.get(marker.getId());

    	Intent intent = new Intent(this, ObservationViewerActivity.class);
    	intent.putExtra("observation", o.toString());
		intent.putExtra("read_only", true);
    	startActivityForResult(intent, VIEW_OBSERVATION_REQUEST_CODE);

        return false;
    }


	public static BitmapDescriptor observationIcon(String iconic_taxon_name) {
        if (iconic_taxon_name == null) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        }

        if (iconic_taxon_name.equals("Animalia") || 
                iconic_taxon_name.equals("Actinopterygii") ||
                iconic_taxon_name.equals("Amphibia") || 
                iconic_taxon_name.equals("Reptilia") || 
                iconic_taxon_name.equals("Aves") || 
                iconic_taxon_name.equals("Mammalia")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dodger_blue);
        } else if (iconic_taxon_name.equals("Insecta") || 
                iconic_taxon_name.equals("Arachnida") ||
                iconic_taxon_name.equals("Mollusca")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_orange_red);
        } else if (iconic_taxon_name.equals("Protozoa")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dark_magenta);
        } else if (iconic_taxon_name.equals("Plantae")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_inat_green);
        } else if (iconic_taxon_name.equals("Fungi")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_hot_pink);
        } else if (iconic_taxon_name.equals("Chromista")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_chromista_brown);
        } else {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        }
        
    }

 	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}	
 	
 	public interface DialogChooserCallbacks {
 		/** Returns an array of title, sub title, image URL for the input JSON object */
 		String[] getItem(JSONObject object);
 		/** When an item was selected from the list */
 		void onItemSelected(JSONObject object);
        /** Whether or not should we display the image on the left as circular */
        boolean isImageCircular();
 	}

 	/** Helper class for creating a pop up dialog with a list of results and a cancel button */
 	private class DialogChooser {
 		private DialogChooserCallbacks mCallbacks;
 		private String mTitle;
 		private JSONArray mResults;
 		private Dialog mDialog;
		private ListView mResultsList;
		private Button mCancel;

		private DialogChooser(int title, JSONArray results, DialogChooserCallbacks callbacks) {
			mCallbacks = callbacks;
			mTitle = getResources().getString(title);
			mResults = results;
			
			mDialog = new Dialog(INaturalistMapActivity.this);
            mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			mDialog.setContentView(R.layout.dialog_chooser);
            ((TextView)mDialog.findViewById(R.id.title)).setText(mTitle);

			mResultsList = (ListView) mDialog.findViewById(R.id.search_results);
			
			List<JSONObject> res = new ArrayList<JSONObject>(mResults.length());
			for (int i = 0; i < mResults.length(); i++) {
				try {
					res.add(mResults.getJSONObject(i));
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			ArrayAdapter<JSONObject> adapter = new ArrayAdapter<JSONObject>(INaturalistMapActivity.this, R.layout.dialog_chooser_result_item, res) {
               @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View row;

                    if (null == convertView) {
                    	LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    	row = inflater.inflate(R.layout.dialog_chooser_result_item, null);
                    } else {
                    	row = convertView;
                    }
                    
                    JSONObject object;
					try {
						object = mResults.getJSONObject(position);
					} catch (JSONException e) {
						e.printStackTrace();
						return row;
					}

                    String[] values = mCallbacks.getItem(object);

                    TextView title = (TextView) row.findViewById(R.id.title);
                    TextView subtitle = (TextView) row.findViewById(R.id.subtitle);
                    ImageView image = (ImageView) row.findViewById(R.id.pic);

                    title.setText(values[0]);
                    subtitle.setText(values[1]);
                    UrlImageViewHelper.setUrlDrawable(image, values[2], new UrlImageViewCallback() {
						@Override
						public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
							// Nothing to do here
						}

						@Override
						public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                            if (mCallbacks.isImageCircular()) {
                                // Return a circular version of the profile picture
                                return ImageUtils.getCircleBitmap(loadedBitmap);
                            } else {
                                // Return original, unmodified image
                                return loadedBitmap;
                            }
						}
					});

                    return row;
               }    		
			};

			mResultsList.setAdapter(adapter);
			mResultsList.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
					try {
						mDialog.cancel();
						mCallbacks.onItemSelected(mResults.getJSONObject(position));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			});

			mCancel = (Button) mDialog.findViewById(R.id.cancel);
			mCancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View arg0) {
					mDialog.cancel();
				}
			});
		}
		
		public void show() {
			mDialog.show();
		}
		
		public void cancel() {
			mDialog.cancel();
		}
 		
 	}
 	
 	private void getPlace(final int placeId) {
 		new Thread(new Runnable() {
 			@Override
 			public void run() {
 				HttpURLConnection conn = null;
 				StringBuilder jsonResults = new StringBuilder();
 				try {
 					String urlString = String.format("%s/places/%d.json", INaturalistService.HOST, placeId);
 					URL url = new URL(urlString.toString());
 					conn = (HttpURLConnection) url.openConnection();
 					InputStreamReader in = new InputStreamReader(conn.getInputStream());

 					// Load the results into a StringBuilder
 					int read;
 					char[] buff = new char[1024];
 					while ((read = in.read(buff)) != -1) {
 						jsonResults.append(buff, 0, read);
 					}

 				} catch (MalformedURLException e) {
 					Log.e(TAG, "Error processing Places API URL", e);
 					return;
 				} catch (IOException e) {
 					Log.e(TAG, "Error connecting to Places API", e);
 					return;
 				} finally {
 					if (conn != null) {
 						conn.disconnect();
 					}
 				}

 				try {
 					JSONObject place = new JSONObject(jsonResults.toString());
 					if (place != null) {
 						if (!place.isNull("swlat") && !place.isNull("swlat") && !place.isNull("swlat") && !place.isNull("swlat")) {
 							mMinx = Double.valueOf(place.getString("swlng"));
 							mMaxx = Double.valueOf(place.getString("nelng"));
 							mMiny = Double.valueOf(place.getString("swlat"));
 							mMaxy = Double.valueOf(place.getString("nelat"));

 							final LatLngBounds bounds = new LatLngBounds(new LatLng(mMiny, mMinx), new LatLng(mMaxy, mMaxx));
 							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
								}
							});
 						}
 					}

 				} catch (JSONException e) {
 					Log.e(TAG, "Cannot process JSON result", e);
 				}

 				return;

			}
		}).start();

 	}
 
 	private JSONArray find(String type, String search) {
 		HttpURLConnection conn = null;
 		StringBuilder jsonResults = new StringBuilder();
 		try {
 			StringBuilder sb = new StringBuilder(INaturalistService.HOST + "/" + type + "/search.json?per_page=25&q=");
 			sb.append(URLEncoder.encode(search, "utf8"));

 			URL url = new URL(sb.toString());
 			conn = (HttpURLConnection) url.openConnection();
 			InputStreamReader in = new InputStreamReader(conn.getInputStream());

 			// Load the results into a StringBuilder
 			int read;
 			char[] buff = new char[1024];
 			while ((read = in.read(buff)) != -1) {
 				jsonResults.append(buff, 0, read);
 			}

 		} catch (MalformedURLException e) {
 			Log.e(TAG, "Error processing Places API URL", e);
 			return null;
 		} catch (IOException e) {
 			Log.e(TAG, "Error connecting to Places API", e);
 			return null;
 		} finally {
 			if (conn != null) {
 				conn.disconnect();
 			}
 		}

 		try {
 			JSONArray predsJsonArray = new JSONArray(jsonResults.toString());
 			return predsJsonArray;

 		} catch (JSONException e) {
 			Log.e(TAG, "Cannot process JSON results", e);
 		}

 		return null;

 	}
 
 
 	private void findByName(final int type) {
 		int loading = 0;
 		final int noResults;
 		final String typeName;
 		
 		switch (type) {
 		case FIND_CRITTERS:
 			loading = R.string.searching_for_critters;
 			noResults = R.string.no_critters_found;
 			typeName = "taxa";
 			break;
 		case FIND_PEOPLE:
 			loading = R.string.searching_for_people;
 			noResults = R.string.no_person_found;
 			typeName = "people";
 			break;
 		case FIND_LOCATIONS:
 			loading = R.string.searching_for_places;
 			noResults = R.string.no_place_found;
 			typeName = "places";
 			break;
 		case FIND_PROJECTS:
 			loading = R.string.searching_for_projects;
 			noResults = R.string.no_project_found;
 			typeName = "projects";
 			break;
 		default:
 			noResults = 0;
 			typeName = "";
 		}

 		mHelper.loading(getResources().getString(loading));

 		new Thread(new Runnable() {
 			@Override
 			public void run() {	
 				final JSONArray results = find(typeName, mCurrentSearch);
 				mHelper.stopLoading();

 				if ((results == null) || (results.length() == 0)) {
 					runOnUiThread(new Runnable() {
 						@Override
 						public void run() {
 							mHelper.alert(getResources().getString(noResults));
 						}
 					});
 					return;
 				} else {
 					runOnUiThread(new Runnable() {
 						@Override
 						public void run() {
 							showChooserDialog(type, results);
 						}
 					});
 				}

 			}
 		}).start();
		
 	}
 	
 	
 	private void showChooserDialog(final int type, JSONArray results) {
 		int title = 0;
 		switch (type) {
 		case FIND_CRITTERS:
 			title = R.string.which_critter;
 			break;
 		case FIND_PEOPLE:
 			title = R.string.which_person;
 			break;
 		case FIND_LOCATIONS:
 			title = R.string.which_place;
 			break;
 		case FIND_PROJECTS:
 			title = R.string.which_project;
 			break;
 		}

 		DialogChooser chooser = new DialogChooser(title, results, new DialogChooserCallbacks() {
 			@Override
 			public void onItemSelected(JSONObject item) {
 				try {
					switch (type) {
					case FIND_CRITTERS:
						mTaxonId = item.getInt("id");
						String taxonName = item.getString("name");
						String idName = getTaxonName(item);
						if (idName == null) idName = getResources().getString(R.string.unknown);
						mTaxonName = String.format("%s (%s)", idName, taxonName);
						break;

					case FIND_PEOPLE:
						mUsername = item.getString("login");
						mFullName = (!item.has("name") || item.isNull("name")) ? null : item.getString("name");
						break;

					case FIND_LOCATIONS:
						mLocationName = item.getString("display_name");
						mLocationId = item.getInt("id");
						if (!item.isNull("latitude") && !item.isNull("longitude")) {
							if (!item.isNull("swlat") && !item.isNull("swlat") && !item.isNull("swlat") && !item.isNull("swlat")) {
								mMinx = Double.valueOf(item.getString("swlng"));
								mMaxx = Double.valueOf(item.getString("nelng"));
								mMiny = Double.valueOf(item.getString("swlat"));
								mMaxy = Double.valueOf(item.getString("nelat"));

								LatLngBounds bounds = new LatLngBounds(new LatLng(mMiny, mMinx), new LatLng(mMaxy, mMaxx));
								mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
							}
						}
                        break;

					case FIND_PROJECTS:
						mProjectName = item.getString("title");
						mProjectId = item.getInt("id");
                        mProject = new BetterJSONObject(item);

						if (!item.isNull("place_id")) {
							// Project has a place associated to it - get its coordinates
							int placeId = item.getInt("place_id");
							getPlace(placeId);
						}
						break;
					}

    				reloadObservations();
    				refreshActiveFilters();
				} catch (JSONException e) {
					e.printStackTrace();
				}
 			}

            @Override
            public boolean isImageCircular() {
                switch (type) {
                    case FIND_PEOPLE:
                        return true; // Return a circular image for users
                    case FIND_CRITTERS:
                    case FIND_LOCATIONS:
                    case FIND_PROJECTS:
                    default:
                        return false;
                }
            }

            @Override
 			public String[] getItem(JSONObject item) {
 				try {
 					switch (type) {
 					case FIND_CRITTERS:
 						String displayName = getTaxonName(item);
						if (displayName == null) displayName = getResources().getString(R.string.unknown);

 						return new String[] {
 								displayName,
 								item.getString("name"),
 								item.getString("image_url")
 						};

 					case FIND_PEOPLE:
 						String title;
 						String subtitle;

 						if ((!item.has("name")) || (item.isNull("name")) || (item.getString("name").length() == 0)) {
 							title = item.getString("login");
 							subtitle = "";
 						} else {
 							title = item.getString("name");
 							subtitle = item.getString("login");
 						}
 						String url;
 						if (!item.isNull("icon_url")) {
 							url = item.getString("icon_url");
 						} else {
 							url = "http://www.inaturalist.org/attachment_defaults/users/icons/defaults/thumb.png";
 						}
 						return new String[] {
 								title,
 								subtitle,
 								url
 						};
 					case FIND_LOCATIONS:
 						return new String[] {
 								item.getString("display_name"),
 								item.isNull("place_type_name") ? "" : item.getString("place_type_name"),
 								null
 						};

 					case FIND_PROJECTS:
 						return new String[] {
 								item.getString("title"),
 								String.format(getResources().getString(R.string.observed_taxa), item.getInt("observed_taxa_count")),
 								item.getString("icon_url")
 						};

 					}

 				} catch (JSONException e) {
 					e.printStackTrace();
 				}

 				return new String[] { "", "", "" };
 			}
 		});
 		
 		chooser.show();

 	}
 	
 	
 	// Refreshes the active filters bar
 	private void refreshActiveFilters() {
 		String filterText = "";
 		
 		if (mTaxonId != null) {
 			filterText = String.format(getResources().getString(R.string.named), mTaxonName);
 		}
  		if (mUsername != null) {
  			if (filterText.length() > 0) filterText += " " + getResources().getString(R.string.and) + " ";
 			filterText += String.format(getResources().getString(R.string.seen_by), ((mFullName != null) && (mFullName.length() > 0) ? mFullName : mUsername));
 		}
   		if (mLocationId != null) {
  			if (filterText.length() > 0) filterText += " " + getResources().getString(R.string.and) + " ";
 			filterText += String.format(getResources().getString(R.string.seen_at), mLocationName);
 		}
   		if (mProjectId != null) {
  			if (filterText.length() > 0) filterText += " " + getResources().getString(R.string.and) + " ";
 			filterText += String.format(getResources().getString(R.string.in_project), mProjectName);
 		}
 		
 		if (filterText.length() > 0) {
 			filterText = Character.toUpperCase(filterText.charAt(0)) + filterText.substring(1); // Upper case first letter
 			mActiveFiltersDescription.setText(filterText);
 			mActiveFilters.setVisibility(View.VISIBLE);

            if (mProjectId != null) {
                mProjectInfo.setVisibility(View.VISIBLE);
            } else {
                mProjectInfo.setVisibility(View.GONE);
            }
 		} else {
 			mActiveFilters.setVisibility(View.GONE);
 		}
 		
 		if (mViewType.equals(VIEW_TYPE_MAP)) {
 			mRestricToMap.setVisibility(View.GONE);
 		} else {
 			mRestricToMap.setVisibility(mClearMapLimit ? View.GONE : View.VISIBLE);
 		}
 		
 	}
 	
 	
 	private String getTaxonName(JSONObject item) {
 		JSONObject defaultName;
 		String displayName = null;


		// Get the taxon display name according to device locale
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Locale deviceLocale = getResources().getConfiguration().locale;
		String deviceLexicon =   deviceLocale.getLanguage();

 		try {
 			JSONArray taxonNames = item.getJSONArray("taxon_names");
 			for (int i = 0; i < taxonNames.length(); i++) {
 				JSONObject taxonName = taxonNames.getJSONObject(i);
 				String lexicon = taxonName.getString("lexicon");
 				if (lexicon.equals(deviceLexicon)) {
 					// Found the appropriate lexicon for the taxon
 					displayName = taxonName.getString("name");
 					break;
 				}
 			}
 		} catch (JSONException e3) {
 			//e3.printStackTrace();
 		}

 		if (displayName == null) {
 			// Couldn't extract the display name from the taxon names list - use the default one
 			try {
 				displayName = item.getString("unique_name");
 			} catch (JSONException e2) {
 				displayName = null;
 			}
 			try {
 				defaultName = item.getJSONObject("default_name");
 				displayName = defaultName.getString("name");
 			} catch (JSONException e1) {
 				// alas
 				JSONObject commonName = item.optJSONObject("common_name");
 				if (commonName != null) {
 					displayName = commonName.optString("name");
 				} else {
 					displayName = item.optString("name");
 				}
 			}
 		}

 		return displayName;

 	}
		
 	private void getCurrentLocationAndLoadNearbyObservations() {
 		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

 		// If Google Play services is available
 		if ((ConnectionResult.SUCCESS == resultCode) && ((mLocationClient == null) || (!mLocationClient.isConnected())))  {
 			// Use Google Location Services to determine location
            mLocationClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(new ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            loadNearbyObservations();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {

                        }
                    })
                    .addOnConnectionFailedListener(new OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            // Couldn't connect to client - load by GPS
                            loadNearbyObservations();
                        }
                    })
                    .build();
 			mLocationClient.connect();
 		} else {
 			// Use GPS for the location
 			loadNearbyObservations();
 			
 		}

 	}
 	
 	private void loadNearbyObservations() {
 		Location currentLocation = getLastLocation();

 		if (currentLocation != null) {
 			double latitude = currentLocation.getLatitude();
 			double longitude = currentLocation.getLongitude();
 			final LatLng latLng = new LatLng(latitude, longitude);

 			CameraPosition camPos = new CameraPosition.Builder()
 			.target(latLng)
 			.zoom(13)
 			.build();

 			CameraUpdate camUpdate = CameraUpdateFactory.newCameraPosition(camPos);
 			mMap.moveCamera(camUpdate);
 		}

 		reloadObservations();

 	}
 	
 	private Location getLastLocation() {
 		Location location = null;

 		if ((mLocationClient != null) && (mLocationClient.isConnected())) {
 			// Use location client for the latest location
 			try {
 				location = LocationServices.FusedLocationApi.getLastLocation(mLocationClient);
 			} catch (IllegalStateException ex) {
 				ex.printStackTrace();
 			}
 		}

 		if (location == null) {
 			// Use GPS for current location
 			LocationManager locationManager = (LocationManager)mApp.getSystemService(Context.LOCATION_SERVICE);
 			Criteria criteria = new Criteria();
 			String provider = locationManager.getBestProvider(criteria, false);
 			location = locationManager.getLastKnownLocation(provider);
 		}
 		
 		return location;
 	}

 	
 	private class ObservationListAdapter extends ArrayAdapter<JSONObject> {

 		private List<JSONObject> mItems;
 		private Context mContext;
 		private ArrayList<JSONObject> mOriginalItems;

 		public ObservationListAdapter(Context context, List<JSONObject> objects) {
 			super(context, R.layout.explore_list_item, objects);

 			mItems = objects;
 			mOriginalItems = new ArrayList<JSONObject>(mItems);
 			mContext = context;

 		}

 		public void addItemAtBeginning(JSONObject newItem) {
 			mItems.add(0, newItem);
 		}

 		@Override
 		public int getCount() {
 			return mItems.size();
 		}

 		@Override
 		public JSONObject getItem(int index) {
 			return mItems.get(index);
 		}

 		@SuppressLint("NewApi")
		@Override
 		public View getView(int position, View convertView, ViewGroup parent) { 
 			LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
 			final View view = inflater.inflate(R.layout.explore_list_item, parent, false); 
 			JSONObject item = mItems.get(position);
 			
 			
 			TextView username = (TextView) view.findViewById(R.id.username);
 			username.setText(item.optString("user_login"));

 			TextView idName = (TextView) view.findViewById(R.id.id_name);
 			TextView taxonName = (TextView) view.findViewById(R.id.id_taxon_name);

 			idName.setTextColor(mHelper.observationColor(new Observation(new BetterJSONObject(item))));
 			final JSONObject taxon = item.optJSONObject("taxon");

 			if (taxon != null) {
 				String idNameString = getTaxonName(taxon);
 				if (idNameString != null) {
 					idName.setText(idNameString);
 					taxonName.setText(taxon.optString("name", ""));
 				} else {
 					idName.setText(taxon.optString("name", getResources().getString(R.string.unknown)));
 					taxonName.setText("");
 					idName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
 				}

 				String rank = (taxon.isNull("rank") ? null : taxon.optString("rank", null));
 				if (rank != null) {
 					if ((rank.equalsIgnoreCase("genus")) || (rank.equalsIgnoreCase("species"))) {
 						taxonName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
 					}
 				}

 			} else {
 				String idNameStr = item.isNull("species_guess") ?
 						getResources().getString(R.string.unknown) :
 							item.optString("species_guess", getResources().getString(R.string.unknown));
 						idName.setText(idNameStr);
 						taxonName.setText("");
 			}
 			

 			ImageView taxonPic = (ImageView) view.findViewById(R.id.image);

 			JSONArray observationPhotos;
			try {
				observationPhotos = item.getJSONArray("observation_photos");
			} catch (JSONException e1) {
				e1.printStackTrace();
				observationPhotos = new JSONArray();
			}

 			if (observationPhotos.length() > 0) {
 				JSONObject observationPhoto;
 				try {
 					observationPhoto = observationPhotos.getJSONObject(0);
 					JSONObject innerPhoto = observationPhoto.optJSONObject("photo");
 					String url = (innerPhoto.isNull("small_url") ? innerPhoto.optString("original_url") : innerPhoto.optString("small_url"));
 					UrlImageViewHelper.setUrlDrawable(taxonPic, url, ObservationPhotosViewer.observationIcon(item));
 				} catch (JSONException e) {
 					e.printStackTrace();
 				} catch (Exception e) {
 					// Could happen if user scrolls really fast and there a LOT of thumbnails being downloaded at once (too many threads at once)
 					e.printStackTrace();
 				}
 			}
 			
 			TextView observedOnDate = (TextView) view.findViewById(R.id.observed_on);
 			BetterJSONObject json = new BetterJSONObject(item);
 			Timestamp observedOn = json.getTimestamp("time_observed_at");

            if (item.optString("user_login").equals("budowski")) {
                int i = 0;
                i++;
            }

 			if (observedOn != null) {
 				observedOnDate.setText(mApp.formatDate(observedOn));
 			} else {
                if (!item.isNull("observed_on")) {
                    observedOnDate.setText(item.optString("observed_on", ""));
                } else {
                    observedOnDate.setText("");
                }
 			}

 			TextView tag = (TextView) view.findViewById(R.id.tag);
 			String qualityGrade = item.isNull("quality_grade") ? "" : item.optString("quality_grade", "");
 			
 			if (item.optBoolean("id_please", false)) {
 				tag.setText(R.string.id_please_tag);
 				tag.setTextColor(Color.parseColor(ID_PLEASE_TAG_TEXT_COLOR));
 				tag.setBackgroundColor(Color.parseColor(ID_PLEASE_TAG_BACKGROUND_COLOR));
 				tag.setVisibility(View.VISIBLE);

 			} else if (qualityGrade.equals("research")) {
 				tag.setText(R.string.research_tag);
 				tag.setTextColor(Color.parseColor(RESEARCH_TAG_TEXT_COLOR));
 				tag.setBackgroundColor(Color.parseColor(RESEARCH_TAG_BACKGROUND_COLOR));
 				tag.setVisibility(View.VISIBLE);

 			} else {
 				tag.setVisibility(View.INVISIBLE);
 			}


 			view.setTag(item);

 			return view;
 		}
 	}

	@Override
	public void onInfoWindowClick(Marker arg0) {
		// TODO Auto-generated method stub
		
	}

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        return prefs.getString("username", null) != null;
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == VIEW_OBSERVATION_REQUEST_CODE) {
			if (resultCode == ObservationViewerActivity.RESULT_FLAGGED_AS_CAPTIVE) {
				// Refresh the results (since the user flagged the result as captive)
                loadObservations();
				return;
			}
		}
	}
 }
