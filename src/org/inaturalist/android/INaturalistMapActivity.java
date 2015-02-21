package org.inaturalist.android;
import java.io.IOException;
import java.io.InputStreamReader;
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
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
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

public class INaturalistMapActivity extends BaseFragmentActivity implements OnMarkerClickListener, OnInfoWindowClickListener {
    public final static String TAG = "INaturalistMapActivity";
    private GoogleMap mMap;
    private Circle mCircle;
    private NearbyObservationsReceiver mNearbyReceiver;
    private ActivityHelper mHelper;
    private HashMap<String, Observation> mMarkerObservations;
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
	    
	    mCurrentSearch = "";
	    mSearchType = 0;
	    mTaxonId = null;
	    mUsername = null;
	    mLocationId = null;
	    
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
				mUsername = null;
				mLocationId = null;
				mSearchType = FIND_NEAR_BY_OBSERVATIONS;

				refreshActiveFilters();
				loadObservations();
			}
		});
    }
    
    // Loads observations according to current search criteria
    private void loadObservations() {
    	if (mCurrentSearch.length() == 0) {
    		switch (mSearchType) {
    		case FIND_NEAR_BY_OBSERVATIONS:
    			// Find observations near me
    			reloadNearbyObservations();
    			break;
    		case FIND_MY_OBSERVATIONS:
    			// Find my observations
    			loadMyObservations();
    			break;

    		}
    	} else {
    		// Find critters/people/projects/locations by name
    		if (mActiveSearch) {
    			// Show search dialog for critters/people/... first
    			findByName(mSearchType);
    		} else {
    			reloadNearbyObservations();
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
        //reloadObservations();
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

            reloadNearbyObservations();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    private void setUpMapIfNeeded() {
        if (mMarkerObservations == null) {
            mMarkerObservations = new HashMap<String, Observation>();
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
                
                mMap.setOnCameraChangeListener(new OnCameraChangeListener() {
                	@Override
                	public void onCameraChange(CameraPosition arg0) {
                		mActiveSearch = false;
                		loadObservations();
                	}
                });



                if (!mMarkerObservations.isEmpty()) {
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (Observation o: mMarkerObservations.values()) {
                        if (o.private_latitude != null && o.private_longitude != null) {
                            builder.include(new LatLng(o.private_latitude, o.private_longitude));
                        } else {
                            builder.include(new LatLng(o.latitude, o.longitude));
                        }
                    }
                    final LatLngBounds bounds = builder.build();

                   /*
                    mMap.setOnCameraChangeListener(new OnCameraChangeListener() {
                        @Override
                        public void onCameraChange(CameraPosition arg0) {
                            // Move camera.
                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
                            // Remove listener to prevent position reset on camera move.
                            mMap.setOnCameraChangeListener(null);
                        }
                    });
                    */
                }
            }
        }
    }

    private void loadMyObservations() {
        if (mMap == null) return;
        showLoading();
        String where = "(_synced_at IS NULL";
        if (mApp.currentUserLogin() != null) {
            where += " OR user_login = '" + mApp.currentUserLogin() + "'";
        }
        where += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, 
                where, // selection 
                null, // selectionArgs
                Observation.DEFAULT_SORT_ORDER);
        c.moveToFirst();
        mMap.clear();
        mMarkerObservations.clear();
        while (c.isAfterLast() == false) {
            addObservation(new Observation(c));
            c.moveToNext();
        }
        if (mActiveSearch) {
        	Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), c.getCount()), Toast.LENGTH_SHORT).show();
        	mActiveSearch = false;
        }
        hideLoading();
    }
    
    private void addObservation(Observation o) {
    	if (o == null) return;
    	
        if (o.private_latitude == null && o.latitude == null) {
            return;
        }
        LatLng latLng;
        if (o.private_latitude != null && mApp.currentUserLogin().equalsIgnoreCase(o.user_login)) {
            latLng = new LatLng(o.private_latitude, o.private_longitude);
        } else {
            latLng = new LatLng(o.latitude, o.longitude);
        }
        MarkerOptions opts = new MarkerOptions()
            .position(latLng)
            .title(o.species_guess)
            .icon(observationIcon(o));
        if (o.description != null && o.description.length() > 0) {
            opts.snippet(o.description);
        }
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
 
    private void reloadNearbyObservations() {
       showLoading();
      
       Intent serviceIntent = new Intent(INaturalistService.ACTION_NEARBY, null, this, INaturalistService.class);
       VisibleRegion vr = mMap.getProjection().getVisibleRegion();
       serviceIntent.putExtra("minx", vr.farLeft.longitude);
       serviceIntent.putExtra("maxx", vr.farRight.longitude);
       serviceIntent.putExtra("miny", vr.nearLeft.latitude);
       serviceIntent.putExtra("maxy", vr.farRight.latitude);
       if (mTaxonId != null) serviceIntent.putExtra("taxon_id", mTaxonId.intValue());
       if (mUsername != null) serviceIntent.putExtra("username", mUsername);
       if (mLocationId != null) serviceIntent.putExtra("location_id", mLocationId.intValue());
       startService(serviceIntent);
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
            	BetterJSONObject json;
				try {
					json = new BetterJSONObject(results.getJSONObject(i));
					Observation obs = new Observation(json);
					addObservation(obs);
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }
            
            /*
            Double minx = extras.getDouble("minx");
            Double maxx = extras.getDouble("maxx");
            Double miny = extras.getDouble("miny");
            Double maxy = extras.getDouble("maxy");
            String where = "(latitude BETWEEN "+miny+" AND "+maxy+") AND (longitude BETWEEN "+minx+" AND "+maxx+")"; 
            if (mTaxonId != null) {
            	where += " AND (taxon_id = " + mTaxonId + ")";
            }
            if (mUsername != null) {
            	where += " AND (user_login = '" + mUsername + "')";
            }


            Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, 
                    where, // selection 
                    null,
                    Observation.DEFAULT_SORT_ORDER);
                    */


            /*
            c.moveToFirst();
            while (c.isAfterLast() == false) {
                addObservation(new Observation(c));
                c.moveToNext();
            }
            */
            if (mActiveSearch) {
            	Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), results.length()), Toast.LENGTH_SHORT).show();
            	mActiveSearch = false;
            }
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        setAccuracyCircle(marker);
        return false;
    }
    
    private void setAccuracyCircle(Marker marker) {
        Observation o = mMarkerObservations.get(marker.getId());
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
    }
    
    private void showObservationDialog(Marker marker) {
        marker.hideInfoWindow();
        Observation observation = mMarkerObservations.get(marker.getId());
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
    }
    
    private BitmapDescriptor observationIcon(Observation o) {
        if (o.iconic_taxon_name == null) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        } else if (o.iconic_taxon_name.equals("Animalia") || 
                o.iconic_taxon_name.equals("Actinopterygii") ||
                o.iconic_taxon_name.equals("Amphibia") || 
                o.iconic_taxon_name.equals("Reptilia") || 
                o.iconic_taxon_name.equals("Aves") || 
                o.iconic_taxon_name.equals("Mammalia")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dodger_blue);
        } else if (o.iconic_taxon_name.equals("Insecta") || 
                o.iconic_taxon_name.equals("Arachnida") ||
                o.iconic_taxon_name.equals("Mollusca")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_orange_red);
        } else if (o.iconic_taxon_name.equals("Protozoa")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dark_magenta);
        } else if (o.iconic_taxon_name.equals("Plantae")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_inat_green);
        } else if (o.iconic_taxon_name.equals("Fungi")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_hot_pink);
        } else if (o.iconic_taxon_name.equals("Chromista")) {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_chromista_brown);
        } else {
            return BitmapDescriptorFactory.fromResource(R.drawable.mm_34_unknown);
        }
        
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // TODO make a decent infowindow, replace this alert with a modal fragment
        showObservationDialog(marker);
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
					}

    				reloadNearbyObservations();
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
 						return new String[] {
 								title,
 								subtitle,
 								item.getString("icon_url")
 						};
 					case FIND_LOCATIONS:
 						return new String[] {
 								item.getString("display_name"),
 								item.isNull("place_type_name") ? "" : item.getString("place_type_name"),
 								null
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
}
