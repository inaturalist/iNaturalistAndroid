package org.inaturalist.android;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.tinylog.Logger;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.evernote.android.state.State;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.livefront.bridge.Bridge;

public class LocationChooserActivity extends AppCompatActivity implements LocationListener {
    public final static String TAG = "LocationChooserActivity";
	protected static final String LATITUDE = "latitude";
	protected static final String LONGITUDE = "longitude";
	protected static final String ACCURACY = "accuracy";
    protected static final String ICONIC_TAXON_NAME = "iconic_taxon_name";
    protected static final String GEOPRIVACY = "geoprivacy";
    protected static final String PLACE_GUESS = "place_guess";

    private static final float MY_LOCATION_ZOOM_LEVEL = 10;

    private GoogleMap mMap;
    private HashMap<String, Observation> mMarkerObservations;
    private INaturalistApp mApp;
	@State public double mLatitude;
	@State public double mLongitude;
	private boolean mZoomToLocation = false;
	private LocationManager mLocationManager;
	@State public double mAccuracy;
    private ActivityHelper mHelper;
    @State public String mIconicTaxonName;
    private ImageView mObservationsMapMyLocation;
    private LocationReceiver mLocationReceiver;
    private ImageView mObservationsChangeMapLayers;
    @State public int mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
    private ImageView mGeoprivacy;
    private Spinner mGeoprivacySpinner;
    private EditText mLocationSearch;
    private ImageView mClearLocation;
    private ViewGroup mLocationSearchResultsContainer;
    private ListView mLocationSearchResults;
    @State public String mPlaceGuess;
    @State public boolean mGeodecodingPlaceName;
    private TextView mActionBarPlaceGuess;
    private TextView mActionBarGeoprivacy;
    private TextView mActionBarLatitude;
    private TextView mActionBarLongtitude;
    private TextView mActionBarAccuracy;
    private Handler mHandler;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mHandler = new Handler();

        //AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();

        setContentView(R.layout.location_chooser);

        mGeoprivacySpinner = (Spinner) findViewById(R.id.geoprivacy_selection);

        if (savedInstanceState == null) {
            mLongitude = getIntent().getDoubleExtra(LONGITUDE, 0);
            mLatitude = getIntent().getDoubleExtra(LATITUDE, 0);
            mAccuracy = getIntent().getDoubleExtra(ACCURACY, 0);
            mIconicTaxonName = getIntent().getStringExtra(ICONIC_TAXON_NAME);
            mPlaceGuess = getIntent().getStringExtra(PLACE_GUESS);

            String geoPrivacyValue = getIntent().getStringExtra(GEOPRIVACY);
            List<String> names = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_items));
            mGeoprivacySpinner.setSelection(names.indexOf(geoPrivacyValue));
        }

        if ((mLongitude != 0) && (mLatitude != 0) && (savedInstanceState == null)) {
        	mZoomToLocation = true;
        }
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setShowHideAnimationEnabled(true);
        actionBar.setCustomView(R.layout.location_chooser_action_bar);
        actionBar.setDisplayShowCustomEnabled(true);

        mActionBarPlaceGuess = (TextView) actionBar.getCustomView().findViewById(R.id.place_guess);
        mActionBarLatitude = (TextView) actionBar.getCustomView().findViewById(R.id.latitude);
        mActionBarLongtitude = (TextView) actionBar.getCustomView().findViewById(R.id.longitude);
        mActionBarAccuracy = (TextView) actionBar.getCustomView().findViewById(R.id.accuracy);
        mActionBarGeoprivacy = (TextView) actionBar.getCustomView().findViewById(R.id.geoprivacy);

        refreshActionBar();

        mApp = (INaturalistApp) getApplicationContext();

        mObservationsMapMyLocation = (ImageView) findViewById(R.id.my_location);
        mObservationsChangeMapLayers = (ImageView) findViewById(R.id.change_map_layers);

        mObservationsMapMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_CURRENT_LOCATION, null, LocationChooserActivity.this, INaturalistService.class);
                ContextCompat.startForegroundService(LocationChooserActivity.this, serviceIntent);
            }
        });

        mObservationsMapMyLocation.setVisibility(mApp.isLocationPermissionGranted() ? View.VISIBLE : View.INVISIBLE);

        mObservationsChangeMapLayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
                    mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
                } else {
                    mObservationsMapType = GoogleMap.MAP_TYPE_SATELLITE;
                }

                refreshMapType();
            }
        });

        mGeoprivacy = (ImageView) findViewById(R.id.geoprivacy);
        mGeoprivacy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.selection(getString(R.string.location_visibility), getResources().getStringArray(R.array.geoprivacy_items), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGeoprivacySpinner.setSelection(which);
                        updateObservationVisibility();
                        refreshActionBar();
                    }
                });
            }
        });

        mLocationSearch = (EditText) findViewById(R.id.location_search);
        mClearLocation = (ImageView) findViewById(R.id.clear_search);

        mLocationSearchResultsContainer = (ViewGroup) findViewById(R.id.location_search_results_container);
        mLocationSearchResults = (ListView) findViewById(R.id.location_search_results);

        mClearLocation.setVisibility(View.INVISIBLE);

        mLocationSearchResultsContainer.setVisibility(View.GONE);
        mLocationSearch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showLocationSearch();
            }
        });
        mLocationSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    showLocationSearch();
                }
            }
        });

        mClearLocation.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                hideLocationSearch();
            }
        });

        refreshMapType();
        updateObservationVisibility();
    }

    private void refreshActionBar() {
        mActionBarPlaceGuess.setText(mGeodecodingPlaceName ? getString(R.string.loading) : ((mPlaceGuess == null || mPlaceGuess.length() == 0) ? getString(R.string.location) : mPlaceGuess));
        mActionBarLatitude.setText(String.format("%.2f", mLatitude));
        mActionBarLongtitude.setText(String.format("%.2f", mLongitude));
        mActionBarAccuracy.setText(String.format("%d", (int)mAccuracy));
        mActionBarGeoprivacy.setText(String.format(getString(R.string.geoprivacy_with_value), mGeoprivacySpinner.getSelectedItem()));
    }

    private void hideLocationSearch() {
        Animation moveDown = AnimationUtils.loadAnimation(LocationChooserActivity.this, R.anim.slide_down);
        moveDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                ActionBar actionBar = getSupportActionBar();
                actionBar.show();

                if (getCurrentFocus() != null) {
                    // Hide keyboard
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

                mLocationSearch.setText("");
                mLocationSearch.clearFocus();
                mClearLocation.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mLocationSearchResultsContainer.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mLocationSearchResultsContainer.startAnimation(moveDown);
    }

    private void showLocationSearch() {
        Animation moveUp = AnimationUtils.loadAnimation(LocationChooserActivity.this, R.anim.slide_up);
        moveUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mLocationSearchResultsContainer.setVisibility(View.VISIBLE);
                ActionBar actionBar = getSupportActionBar();
                actionBar.hide();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mClearLocation.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mLocationSearchResultsContainer.startAnimation(moveUp);
    }

    @Override 
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
        setUpMapIfNeeded();
        zoomToLocation();

        mLocationReceiver = new LocationReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.GET_CURRENT_LOCATION_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mLocationReceiver, filter, this);

    }

    private void updateObservationVisibility() {
        int index = mGeoprivacySpinner.getSelectedItemPosition();

        switch (index) {
            case 0:
                mGeoprivacy.setImageResource(R.drawable.ic_public_black_24dp);
                break;
            case 1:
                mGeoprivacy.setImageResource(R.drawable.ic_filter_tilt_shift_black_24dp);
                break;
            case 2:
                mGeoprivacy.setImageResource(R.drawable.ic_visibility_off_black_24dp);
                break;
        }
    }


    private void zoomToLocation() {
        
        double longitude = mLongitude;
        double latitude = mLatitude;
        
        if ((longitude != 0) && (latitude != 0)) {
        	LatLng location = new LatLng(latitude, longitude);

        	int zoom = 15;

        	if (mAccuracy > 0) {
            	DisplayMetrics metrics = new DisplayMetrics();
            	getWindowManager().getDefaultDisplay().getMetrics(metrics);

                int screenWidth = (int) (metrics.widthPixels * 0.4 * 0.2);

                double equatorLength = 40075004; // in meters
                double widthInPixels = screenWidth * 0.4 * 0.40;
                double metersPerPixel = equatorLength / 256;
                int zoomLevel = 1;
                while ((metersPerPixel * widthInPixels) > mAccuracy) {
                    metersPerPixel /= 2;
                    ++zoomLevel;
                    Logger.tag(TAG).error("\t** Zoom = " + zoomLevel + "; CurrentAcc = " + (metersPerPixel * widthInPixels) +  "; Accuracy = " + mAccuracy);
                }
                Logger.tag(TAG).error("Zoom = " + zoomLevel + "; Accuracy = " + mAccuracy);
                zoom = zoomLevel;
        	}
        	

        	if (mZoomToLocation) {
        		if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom));
        		mZoomToLocation = false;
        	} else {
        		if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom), 1, null);
        	}
        } else {

        }
        
    }

    private void refreshMapType() {
        if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_terrain_black_48dp);
        } else {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_satellite_black_48dp);
        }

        if ((mMap != null) && (mMap.getMapType() != mObservationsMapType))
            mMap.setMapType(mObservationsMapType);
    }


    @Override
    public void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mLocationReceiver, this);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
 
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.location_chooser_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    private void onCancel() {
        mHelper.confirm(getString(R.string.edit_location), getString(R.string.discard_location_changes),
                new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int buttonId) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                },
                new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int buttonId) {
                        dialog.cancel();
                    }
                },
                R.string.yes, R.string.no);
    }
    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
    		onCancel();
    		return false;
    	} else {
    		return super.onKeyDown(keyCode, event);
    	}
    }

    private void updateLocationBasedOnMap() {
        float currentZoom = mMap.getCameraPosition().zoom;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int screenWidth = (int) (metrics.widthPixels * 0.4 * 0.2);

        double equatorLength = 40075004; // in meters
        double metersPerPixel = equatorLength / 256;
        int zoomLevel = 1;
        while (zoomLevel < currentZoom) {
            metersPerPixel /= 2;
            ++zoomLevel;
        }
        mAccuracy = (double) ((screenWidth * 0.4 * 0.8) * metersPerPixel);
        Logger.tag(TAG).error("Meters per radius = " + mAccuracy + "; zoom = " + zoomLevel);

        mLatitude = mMap.getCameraPosition().target.latitude;
        mLongitude = mMap.getCameraPosition().target.longitude;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        List<String> values = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_values));

        switch (item.getItemId()) {
            case android.R.id.home:
                onCancel();

                return true;
            case R.id.save_location:
                Bundle bundle = new Bundle();

                updateLocationBasedOnMap();

                bundle.putDouble(LATITUDE, mLatitude);
                bundle.putDouble(LONGITUDE, mLongitude);
                bundle.putDouble(ACCURACY, mAccuracy);
                bundle.putString(GEOPRIVACY, (String) values.get(mGeoprivacySpinner.getSelectedItemPosition()));
                bundle.putString(PLACE_GUESS, mPlaceGuess);

                Intent resultIntent = new Intent();
                resultIntent.putExtras(bundle);
                setResult(RESULT_OK, resultIntent);

                finish();

                return true;

            case R.id.edit_locality_notes:
                editLocalityNotes();
                return true;
            case R.id.choose_pinned_location:
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return true;
                }
                return true;
            case R.id.pin_current_location:
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return true;
                }

                Intent serviceIntent = new Intent(INaturalistService.ACTION_PIN_LOCATION, null, LocationChooserActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.LATITUDE, mLatitude);
                serviceIntent.putExtra(INaturalistService.LONGITUDE, mLongitude);
                serviceIntent.putExtra(INaturalistService.ACCURACY, mAccuracy);
                serviceIntent.putExtra(INaturalistService.GEOPRIVACY, (String) values.get(mGeoprivacySpinner.getSelectedItemPosition()));
                serviceIntent.putExtra(INaturalistService.TITLE, mPlaceGuess);
                ContextCompat.startForegroundService(LocationChooserActivity.this, serviceIntent);

                Toast.makeText(getApplicationContext(), R.string.location_pinned_successfully, Toast.LENGTH_LONG).show();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void editLocalityNotes() {
        final EditText input = new EditText(LocationChooserActivity.this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        input.setMaxLines(1);
        input.setSingleLine(true);

        String placeGuess = mPlaceGuess;
        input.setText(placeGuess);
        input.setSelection(0, placeGuess.length());

        mHelper.confirm(R.string.edit_locality_notes, input, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // OK - update place guess
                mPlaceGuess = input.getText().toString();
                refreshActionBar();
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Cancel
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (getCurrentFocus() != null) {
                            // Hide keyboard
                            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                        }
                        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                        input.clearFocus();
                    }
                }, 300);
            }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }
    
    private void setUpMapIfNeeded() {
        if (mMarkerObservations == null) {
            mMarkerObservations = new HashMap<String, Observation>();
        }
        if (mMap == null) {
            ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
                @SuppressLint("MissingPermission")
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;

                    // Check if we were successful in obtaining the map.
                    if (mMap != null) {
                        // The Map is verified. It is now safe to manipulate the map.
                        if (!mMarkerObservations.isEmpty()) {
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            for (Observation o: mMarkerObservations.values()) {
                                if (o.private_latitude != null && o.private_longitude != null) {
                                    builder.include(new LatLng(o.private_latitude, o.private_longitude));
                                } else {
                                    builder.include(new LatLng(o.latitude, o.longitude));
                                }
                            }
                        }

                        mMap.clear();
                        MarkerOptions opts = new MarkerOptions().position(new LatLng(mLatitude, mLongitude)).icon(TaxonUtils.observationMarkerIcon(mIconicTaxonName));
                        Marker m = mMap.addMarker(opts);

                        zoomToLocation();

                        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
                            @Override
                            public void onCameraMove() {
                                mObservationsMapMyLocation.setColorFilter(Color.parseColor("#676767"));

                                // User moved the map view - reset place guess
                                updateLocationBasedOnMap();
                                refreshActionBar();

                                // Make sure we geodecode the location only after the map finished moving
                                mHandler.removeCallbacksAndMessages(null);
                                mHandler.postDelayed(() -> {
                                    guessLocation();
                                }, 500);
                            }
                        });

                    }
                }
            });

        }
    }

   private void guessLocation() {
        mGeodecodingPlaceName = true;
        refreshActionBar();

        (new Thread(new Runnable() {
            @Override
            public void run() {
                Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                try {
                    final StringBuilder location = new StringBuilder();
                    List<Address> addresses = geocoder.getFromLocation(mLatitude, mLongitude, 10);
                    if ((null != addresses) && (addresses.size() > 0)) {
                        for (Address address : addresses) {
                            if (address.getThoroughfare() == null) {
                                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                                    location.append(address.getAddressLine(i));
                                    location.append(" ");
                                }

                                break;
                            }
                        }

                        runOnUiThread(() -> {
                            mGeodecodingPlaceName = false;
                            mPlaceGuess = location.toString();
                            refreshActionBar();
                        });

                    } else {
                        runOnUiThread(() -> {
                            mGeodecodingPlaceName = false;
                            refreshActionBar();
                        });
                    }
                } catch (IOException e) {
                    Logger.tag(TAG).error(e);
                    runOnUiThread(() -> {
                        mGeodecodingPlaceName = false;
                        refreshActionBar();
                    });
                }
            }
        })).start();
    }
  
	@Override
	public void onLocationChanged(Location location) {
        if (location != null) {
            Logger.tag(TAG).info("Location changed: " + location.getLatitude() + " and " + location.getLongitude());
            mLocationManager.removeUpdates(this);

        	LatLng camLocation = new LatLng(location.getLatitude(), location.getLongitude());
        	if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(camLocation, 15));
        }
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		
	}


    private class LocationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            Location location = extras.getParcelable(INaturalistService.LOCATION);

            if ((location == null) || (mMap == null)) {
                return;
            }

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), MY_LOCATION_ZOOM_LEVEL),
                    1000,
                    new GoogleMap.CancelableCallback() {
                        @Override
                        public void onFinish() {
                            // TODO - refresh place guess
                            //mInitialLocationBounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                            mObservationsMapMyLocation.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                        }

                        @Override
                        public void onCancel() {
                        }
                    });
        }
    }
}
