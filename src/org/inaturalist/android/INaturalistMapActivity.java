package org.inaturalist.android;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
	private boolean mShowToast;
	
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
            	mShowToast = true;
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

        mActiveFilters = (View)findViewById(R.id.active_filters);
        mRestricToMap = (View)findViewById(R.id.restric_to_map);
        
    }
    
    // Loads observations according to current search criteria
    private void loadObservations() {
    	mActiveFilters.setVisibility(View.GONE);
    	mRestricToMap.setVisibility(View.GONE);

    	if (mSearchText.length() == 0) {
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
    		switch (mSearchType) {
    		case FIND_CRITTERS:
    			// Find critters by name
    			reloadNearbyObservations();
    			break;
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
                		mShowToast = false;
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
        if (mShowToast) {
        	Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), c.getCount()), Toast.LENGTH_SHORT).show();
        	mShowToast = false;
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
            Double minx = extras.getDouble("minx");
            Double maxx = extras.getDouble("maxx");
            Double miny = extras.getDouble("miny");
            Double maxy = extras.getDouble("maxy");
            String where = "(latitude BETWEEN "+miny+" AND "+maxy+") AND (longitude BETWEEN "+minx+" AND "+maxx+")"; 
            Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, 
                    where, // selection 
                    null,
                    Observation.DEFAULT_SORT_ORDER);
            c.moveToFirst();
            while (c.isAfterLast() == false) {
                addObservation(new Observation(c));
                c.moveToNext();
            }
            if (mShowToast) {
            	Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), c.getCount()), Toast.LENGTH_SHORT).show();
            	mShowToast = false;
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

		private DialogChooser(int title, DialogChooserCallbacks callbacks, JSONArray results) {
			mCallbacks = callbacks;
			mTitle = getResources().getString(title);
			mResults = results;
			
			mDialog = new Dialog(INaturalistMapActivity.this);
			mDialog.setTitle(mTitle);
			mDialog.setContentView(R.layout.dialog_chooser);
			
			mResultsList = (ListView) mDialog.findViewById(R.id.search_results);
			
			List<JSONObject> res = null;
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
 		
 	}
 
}
