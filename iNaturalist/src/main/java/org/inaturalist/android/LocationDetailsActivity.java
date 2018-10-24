package org.inaturalist.android;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.livefront.bridge.Bridge;

import java.util.ArrayList;
import java.util.List;

public class LocationDetailsActivity extends AppCompatActivity implements LocationListener {
    private final static String TAG = "LocationDetailsActivity";
    public static final String OBSERVATION = "observation";
    public static final String OBSERVATION_JSON = "observation_json";
    public static final String READ_ONLY = "read_only";

    private GoogleMap mMap;
    private INaturalistApp mApp;
	@State public Double mLatitude;
	@State public Double mLongitude;
	private LocationManager mLocationManager;
	@State public double mAccuracy;
    private TextView mLocationCoordinates;
    @State public Observation mObservation;
    private BetterJSONObject mObservationJson;
    @State public boolean mIsReadOnly;
    private ActivityHelper mHelper;

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
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        mObservation = (Observation)getIntent().getSerializableExtra(OBSERVATION);
        String obsJson = getIntent().getStringExtra(OBSERVATION_JSON);
        mObservationJson = obsJson != null ? new BetterJSONObject(obsJson) : null;
        mIsReadOnly = getIntent().getBooleanExtra(READ_ONLY, false);
        mLongitude = mObservation.private_longitude == null ? mObservation.longitude : mObservation.private_longitude;
        mLatitude = mObservation.private_latitude == null ? mObservation.latitude : mObservation.private_latitude;
        mAccuracy = mObservation.positional_accuracy != null ? mObservation.positional_accuracy : 0;

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));
        actionBar.setLogo(R.drawable.ic_arrow_back_gray_24dp);
        actionBar.setTitle(R.string.location);

        setContentView(R.layout.location_detail);
        
        mLocationCoordinates = (TextView) findViewById(R.id.location_coordinates);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
        setUpMapIfNeeded();
        zoomToLocation();
    }

    private void zoomToLocation() {
        
        Double longitude = mLongitude;
        Double latitude = mLatitude;
        
        if ((longitude != null) && (latitude != null)) {
        	LatLng location = new LatLng(latitude, longitude);

        	int zoom = 15;

        	if (mAccuracy > 0) {
            	DisplayMetrics metrics = new DisplayMetrics();
            	getWindowManager().getDefaultDisplay().getMetrics(metrics);

            	int screenWidth = metrics.widthPixels;
            	
                double equatorLength = 40075004; // in meters
                double widthInPixels = screenWidth * 0.4 * 0.5;
                double metersPerPixel = equatorLength / 256;
                int zoomLevel = 1;
                while ((metersPerPixel * widthInPixels) > mAccuracy) {
                    metersPerPixel /= 2;
                    ++zoomLevel;
                    Log.e(TAG, "\t** Zoom = " + zoomLevel + "; CurrentAcc = " + (metersPerPixel * widthInPixels) +  "; Accuracy = " + mAccuracy);
                }
                Log.e(TAG, "Zoom = " + zoomLevel + "; Accuracy = " + mAccuracy);
                zoom = zoomLevel - 2;
        	}


            if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom), 1, null);

            if (mAccuracy == 0) {
                 mLocationCoordinates.setText(String.format(getString(R.string.location_coords_no_acc),
                        String.format("%.5f...", mLatitude),
                        String.format("%.5f...", mLongitude)));
            } else {
                mLocationCoordinates.setText(String.format(getString(R.string.location_coords),
                        String.format("%.5f...", mLatitude),
                        String.format("%.5f...", mLongitude),
                        mAccuracy > 999 ? ">1 km" : String.format("%dm", (int) mAccuracy)));
            }
        } else {

        }
        
    }

    @Override
    public void onPause() {
        super.onPause();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
 
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mMap != null) {
        	if (mMap.getMapType() == GoogleMap.MAP_TYPE_HYBRID) {
                menu.findItem(R.id.satellite).setChecked(true);
        	} else if (mMap.getMapType() == GoogleMap.MAP_TYPE_NORMAL) {
                menu.findItem(R.id.street).setChecked(true);
            } else if (mMap.getMapType() == GoogleMap.MAP_TYPE_TERRAIN) {
                menu.findItem(R.id.terrain).setChecked(true);
        	}
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.choose_location_menu, menu);
        if (mIsReadOnly) {
            MenuItem save = menu.findItem(R.id.save_location);
            save.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.satellite:
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            return true;
        case R.id.street:
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            return true;
        case R.id.terrain:
            mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    private void setUpMapIfNeeded() {
        if (mMap == null) {
            ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;
                    // Check if we were successful in obtaining the map.
                    if (mMap != null) {
                        // The Map is verified. It is now safe to manipulate the map.
                        if (mApp.isLocationPermissionGranted()) {
                            mMap.setMyLocationEnabled(true);
                        }
                        mMap.getUiSettings().setZoomControlsEnabled(false);

                        mMap.clear();

                        mHelper.addMapPosition(mMap, mObservation, mObservationJson);

                        zoomToLocation();
                    }
                }
            });

        }
    }

   
  
	@Override
	public void onLocationChanged(Location location) {
        if (location != null) {
            Log.v("Location Changed", location.getLatitude() + " and " + location.getLongitude());
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
}
