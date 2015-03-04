package org.inaturalist.android;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.inaturalist.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.VisibleRegion;
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
	
	private LocationClient mLocationClient;
	private double mMinx;
	private double mMaxx;
	private double mMiny;
	private double mMaxy;
	private float mZoom;
	private Intent mServiceIntent;
	private TabHost mTabHost;
	
	private final static int NO_SEARCH = -1;
	private final static int FIND_NEAR_BY_OBSERVATIONS = 0;
	private final static int FIND_MY_OBSERVATIONS = 1;
	private final static int FIND_CRITTERS = 0;
	private final static int FIND_PEOPLE = 1;
	private final static int FIND_LOCATIONS = 2;
	private final static int FIND_PROJECTS = 3;
    
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.map);
	    onDrawerCreate(savedInstanceState);
	    
	    if (savedInstanceState != null) {
	    	mCurrentSearch = savedInstanceState.getString("mCurrentSearch");
	    	mSearchType = savedInstanceState.getInt("mSearchType");

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
	    } else {
	    	mCurrentSearch = "";
	    	mSearchType = NO_SEARCH;
	    	mTaxonId = null;
	    	mUsername = null;
	    	mLocationId = null;
	    	mProjectId = null;
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
        
        mSearchResults = (ListView)findViewById(R.id.search_results);
        mSearchResults.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int index, long arg3) {
				mCurrentSearch = mSearchText.getText().toString();
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
			public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
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

        				if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterUpEvent ) {
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
        mCancelRestricToMap = (View)findViewById(R.id.cancel_restrict_to_current_map);
        
        mCancelFilters.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				// Clear out all filters
				mCurrentSearch = "";
				mTaxonId = null;
				mProjectId = null;
				mUsername = null;
				mLocationId = null;
				mSearchType = FIND_NEAR_BY_OBSERVATIONS;

				refreshActiveFilters();
				loadObservations();
			}
		});
        
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        INaturalistMapActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("map").setIndicator(getString(R.string.map)));
        INaturalistMapActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("grid").setIndicator(getString(R.string.grid)));
        INaturalistMapActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("list").setIndicator(getString(R.string.list)));

        mTabHost.setOnTabChangedListener(this);

    }
    
    @Override
    public void onTabChanged(String arg0) {
    	// TODO Auto-generated method stub

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
    			getCurrentLocationAndLoadNearbyObservations();
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
        	loadObservations();
        	refreshActiveFilters();
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString("mCurrentSearch", mCurrentSearch);
        outState.putInt("mSearchType", mSearchType);

        outState.putSerializable("mTaxonId", mTaxonId);
        outState.putString("mTaxonName", mTaxonName);

        outState.putString("mUsername", mUsername);
        outState.putString("mFullName", mFullName);

        outState.putSerializable("mLocationId", mLocationId);
        outState.putString("mLocationName", mLocationName);

        outState.putSerializable("mProjectId", mProjectId);
        outState.putString("mProjectName", mProjectName);

        VisibleRegion vr = mMap.getProjection().getVisibleRegion();
        outState.putDouble("minx", vr.farLeft.longitude);
        outState.putDouble("maxx", vr.farRight.longitude);
        outState.putDouble("miny", vr.nearLeft.latitude);
        outState.putDouble("maxy", vr.farRight.latitude);

    }

    

    @Override
    public void onPause() {
    	try {
    		unregisterReceiver(mNearbyReceiver);
    	} catch (Exception exc) {
    		exc.printStackTrace();
    	}
        super.onPause();
    }
 
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.add:
            // Launch activity to insert a new item
            startActivity(new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, this, ObservationEditor.class));
            return true;
        case R.id.layers:
            if (mMap.getMapType() == GoogleMap.MAP_TYPE_HYBRID) {
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                item.setTitle(R.string.satellite);
            } else {
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                item.setTitle(R.string.street);
            }
            return true;
        case R.id.nearby:
        	if (!isNetworkAvailable()) {
        		Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show(); 
        		return true;
        	}

            reloadObservations();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
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
                //reloadObservations();
                mMap.setMyLocationEnabled(true);
                mMap.setOnMarkerClickListener(this);
                mMap.setOnInfoWindowClickListener(this);
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                
                mMap.setOnCameraChangeListener(new OnCameraChangeListener() {
                	@Override
                	public void onCameraChange(CameraPosition arg0) {
                		mActiveSearch = false;
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
        MarkerOptions opts = new MarkerOptions()
            .position(latLng)
            //.title(o.getString("species_guess"))
            .icon(observationIcon(o));
        Marker m = mMap.addMarker(opts);
        mMarkerObservations.put(m.getId(), o);
    }
    
    private void showLoading() {
    	mRefreshView.setVisibility(View.GONE);
    	mLoading.setVisibility(View.VISIBLE);
    }
    
    private void hideLoading() {
    	mRefreshView.setVisibility(View.VISIBLE);
    	mLoading.setVisibility(View.GONE);
    }
 
    private void reloadObservations() {
       showLoading();
       
       if (mServiceIntent != null) {
    	   stopService(mServiceIntent);
       }
      
       mServiceIntent = new Intent(INaturalistService.ACTION_NEARBY, null, this, INaturalistService.class);
       VisibleRegion vr = mMap.getProjection().getVisibleRegion();
       mServiceIntent.putExtra("minx", vr.farLeft.longitude);
       mServiceIntent.putExtra("maxx", vr.farRight.longitude);
       mServiceIntent.putExtra("miny", vr.nearLeft.latitude);
       mServiceIntent.putExtra("maxy", vr.farRight.latitude);
       mServiceIntent.putExtra("zoom", mMap.getCameraPosition().zoom);
       if (mTaxonId != null) mServiceIntent.putExtra("taxon_id", mTaxonId.intValue());
       if (mUsername != null) mServiceIntent.putExtra("username", mUsername);
       if (mLocationId != null) mServiceIntent.putExtra("location_id", mLocationId.intValue());
       if (mProjectId != null) mServiceIntent.putExtra("project_id", mProjectId.intValue());
       startService(mServiceIntent);
    }
    
    private class NearbyObservationsReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            hideLoading();
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
            
            for (int i = 0; i < results.length(); i++) {
				try {
					addObservation(results.getJSONObject(i));
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }
            
            if (mActiveSearch) {
            	Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), results.length()), Toast.LENGTH_SHORT).show();
            	mActiveSearch = false;
            }
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        JSONObject o = mMarkerObservations.get(marker.getId());

    	Intent intent = new Intent(this, ObservationDetails.class);
    	intent.putExtra("observation", o.toString());
    	startActivity(intent);  

        //setAccuracyCircle(marker);
        return false;
    }
    
    private void setAccuracyCircle(Marker marker) {
    	/*
        JSONObject o = mMarkerObservations.get(marker.getId());
        if (o == null || (o.positional_accuracy == null && o.geoprivacy == null)) {
            if (mCircle != null) { mCircle.setVisible(false); }
            return;
        }
        Integer acc = o.positional_accuracy;
        if (acc == null) acc = 0;
        // TODO this is not handling observations of threatened taxa by other people. 
        // We're going to have to add another col to observations or make better use of 
        // private_positional_accuracy for that.
        if (mApp.currentUserLogin() != o.user_login && o.geoprivacy != null) {
            acc += 10000;
        }
        int strokeColor = mHelper.observationColor(o);
        int fillColor = Color.argb(70, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor));
        if (mCircle == null) {
            CircleOptions circleOptions = new CircleOptions().
                    center(marker.getPosition()).
                    fillColor(fillColor).
                    strokeColor(strokeColor).
                    strokeWidth(2).
                    radius(acc);
            mCircle = mMap.addCircle(circleOptions);
        } else {
            mCircle.setCenter(marker.getPosition());
            mCircle.setRadius(acc);
            mCircle.setFillColor(fillColor);
            mCircle.setStrokeColor(strokeColor);
        }
        mCircle.setVisible(true);
        */
    }
    
    private void showObservationDialog(Marker marker) {
    	/*
        marker.hideInfoWindow();
        JSONObject observation = mMarkerObservations.get(marker.getId());
        final Uri observationUri = observation.getUri();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(observation.species_guess)
            .setMessage(observation.description)
            .setPositiveButton(getString(R.string.close), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
        String login = mApp.currentUserLogin();
        if (login != null && login.equals(observation.user_login)) {
            dialog.setNeutralButton(getString(R.string.edit), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Intent.ACTION_EDIT, observationUri)); 
                }
            });
        } else if (observation.id != null) {
            final Observation boundObservation = observation; 
            dialog.setNeutralButton(getString(R.string.view), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(INaturalistService.HOST + "/observations/"+boundObservation.id));
                    startActivity(i); 
                }
            });
        }
        dialog.show();
        */
    }
    
    private BitmapDescriptor observationIcon(JSONObject o) {
        if (!o.has("iconic_taxon_name") || o.isNull("iconic_taxon_name")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        }
        String iconic_taxon_name;
		try {
			iconic_taxon_name = o.getString("iconic_taxon_name");
		} catch (JSONException e) {
			e.printStackTrace();
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

    @Override
    public void onInfoWindowClick(Marker marker) {
        // TODO make a decent infowindow, replace this alert with a modal fragment
        //showObservationDialog(marker);
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
			mDialog.setTitle(mTitle);
			mDialog.setContentView(R.layout.dialog_chooser);
			
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
                    UrlImageViewHelper.setUrlDrawable(image, values[2]);

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
						mTaxonName = String.format("%s (%s)", idName, taxonName);
						break;

					case FIND_PEOPLE:
						mUsername = item.getString("login");
						mFullName = (!item.has("name") || item.isNull("name")) ? null : item.getString("name");
						break;

					case FIND_LOCATIONS:
						mLocationName = item.getString("display_name");
						mLocationId = item.getInt("id");
						break;

					case FIND_PROJECTS:
						mProjectName = item.getString("title");
						mProjectId = item.getInt("id");
						break;
					}

    				reloadObservations();
    				refreshActiveFilters();
				} catch (JSONException e) {
					e.printStackTrace();
				}
 			}

 			@Override
 			public String[] getItem(JSONObject item) {
 				try {
 					switch (type) {
 					case FIND_CRITTERS:
 						String displayName = getTaxonName(item);

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
 		} else {
 			mActiveFilters.setVisibility(View.GONE);
 		}
 		
 	}
 	
 	
 	// Utility function for retrieving the Taxon's name
 	private String getTaxonName(JSONObject item) {
 		JSONObject defaultName;
 		String displayName = null;


 		// Get the taxon display name according to configuration of the current iNat network
 		String inatNetwork = mApp.getInaturalistNetworkMember();
 		String networkLexicon = mApp.getStringResourceByName("inat_lexicon_" + inatNetwork);
 		try {
 			JSONArray taxonNames = item.getJSONArray("taxon_names");
 			for (int i = 0; i < taxonNames.length(); i++) {
 				JSONObject taxonName = taxonNames.getJSONObject(i);
 				String lexicon = taxonName.getString("lexicon");
 				if (lexicon.equals(networkLexicon)) {
 					// Found the appropriate lexicon for the taxon
 					displayName = taxonName.getString("name");
 					break;
 				}
 			}
 		} catch (JSONException e3) {
 			e3.printStackTrace();
 		}

 		if (displayName == null) {
 			// Couldn't extract the display name from the taxon names list - use the default one
 			try {
 				displayName = item.getString("unique_name");
 			} catch (JSONException e2) {
 				displayName = "unknown";
 			}
 			try {
 				defaultName = item.getJSONObject("default_name");
 				displayName = defaultName.getString("name");
 			} catch (JSONException e1) {
 				// alas
 			}
 		}

 		return displayName;

 	}
 	
 	private void getCurrentLocationAndLoadNearbyObservations() {
 		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

 		// If Google Play services is available
 		if ((ConnectionResult.SUCCESS == resultCode) && ((mLocationClient == null) || (!mLocationClient.isConnected())))  {
 			// Use Google Location Services to determine location
 			mLocationClient = new LocationClient(getApplicationContext(), new ConnectionCallbacks() {
				@Override
				public void onDisconnected() {
				}
				@Override
				public void onConnected(Bundle arg0) {
					loadNearbyObservations();
				}
			}, new OnConnectionFailedListener() {
				@Override
				public void onConnectionFailed(ConnectionResult arg0) {
					// Couldn't connect to client - load by GPS
					loadNearbyObservations();
				}
			});
 			mLocationClient.connect();
 		} else {
 			// Use GPS for the location
 			loadNearbyObservations();
 			
 		}

 	}
 	
 	private void loadNearbyObservations() {
 		Location currentLocation = getLastLocation();


 		double latitude = currentLocation.getLatitude();
 		double longitude = currentLocation.getLongitude();
 		final LatLng latLng = new LatLng(latitude, longitude);

 		CameraPosition camPos = new CameraPosition.Builder()
             .target(latLng)
             .zoom(12)
             .build();

 		CameraUpdate camUpdate = CameraUpdateFactory.newCameraPosition(camPos);
 		mMap.moveCamera(camUpdate);
 		
 		reloadObservations();

 	}
 	
 	private Location getLastLocation() {
 		Location location;

 		if ((mLocationClient != null) && (mLocationClient.isConnected())) {
 			// Use location client for the latest location
 			try {
 				location = mLocationClient.getLastLocation();
 			} catch (IllegalStateException ex) {
 				ex.printStackTrace();
 				return null;
 			}
 		} else {
 			// Use GPS for current location
 			LocationManager locationManager = (LocationManager)mApp.getSystemService(Context.LOCATION_SERVICE);
 			Criteria criteria = new Criteria();
 			String provider = locationManager.getBestProvider(criteria, false);
 			location = locationManager.getLastKnownLocation(provider);
 		}
 		
 		return location;
 	}

}
