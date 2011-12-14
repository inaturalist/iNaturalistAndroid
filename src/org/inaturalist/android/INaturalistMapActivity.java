package org.inaturalist.android;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

public class INaturalistMapActivity extends com.google.android.maps.MapActivity {
    public final static String TAG = "INaturalistMapActivity";
    private MapView mMapView;
    private List<Overlay> mOverlays;
    private ObservationItemizedOverlay mObservationsOverlay;
    private PositionItemizedOverlay mPositionOverlay;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private boolean mLocationEnabled = false;
    private Location mCurrentLocation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate...");
        setContentView(R.layout.map);
        mMapView = (MapView) findViewById(R.id.mapview);
        mMapView.setBuiltInZoomControls(true);
        mOverlays = mMapView.getOverlays();

        Log.d(TAG, "mObservationsOverlay: " + mObservationsOverlay);
        // this might need to happen in reloadObservations
        mObservationsOverlay = new ObservationItemizedOverlay(
                this.getResources().getDrawable(R.drawable.mm_34_dodger_blue),
                this);
        mOverlays.add(mObservationsOverlay);
        mPositionOverlay = new PositionItemizedOverlay(this);
    }

    @Override 
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");
        reloadObservations();
        startLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocation();
    }


    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem layersItem = menu.findItem(R.id.layers);
        MenuItem locationItem = menu.findItem(R.id.location);
        if (mMapView.isSatellite()) {
            layersItem.setTitle("Street");
        } else {
            layersItem.setTitle("Satellite");
        }
//        if (locationEnabled()) {
//            locationItem.setTitle("Stop GPS");
//        } else {
//            locationItem.setTitle("My location");
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.map_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.add:
            // Launch activity to insert a new item
            startActivity(new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, this, ObservationEditor.class));
            return true;
        case R.id.layers:
            if (mMapView.isSatellite()) {
                mMapView.setSatellite(false);
                item.setTitle("Satellite");
            } else {
                mMapView.setSatellite(true);
                item.setTitle("Street");
            }
            return true;
        case R.id.location:
//            if (locationEnabled()) {
//                item.setTitle("My location");
//                stopLocation();
//            } else {
//                item.setTitle("Stop GPS");
//                startLocation();
//            }
            zoomToLocation(mCurrentLocation);
            return true;
        case R.id.menu:
            startActivity(new Intent(this, MenuActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return true;
        case R.id.nearby:
            //TODO reloadNearbyObservations();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void reloadObservations() {
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, 
                null, // selection 
                null, // selectionArgs
                Observation.DEFAULT_SORT_ORDER);
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            mObservationsOverlay.addObservation(new Observation(c));
            c.moveToNext();
        }
    }

    private boolean locationEnabled() {
        return mLocationEnabled;
    }

    private void startLocation() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        }

        if (mLocationListener == null) {
            // Define a listener that responds to location updates
            mLocationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    // Called when a new location is found by the network location provider.
                    handleNewLocation(location);
                }

                public void onStatusChanged(String provider, int status, Bundle extras) {}
                public void onProviderEnabled(String provider) {}
                public void onProviderDisabled(String provider) {}
            };
        }

        // Register the listener with the Location Manager to receive location updates
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);

        mOverlays.add(mPositionOverlay);
        mPositionOverlay.updateLocation(mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
        mLocationEnabled = true;
    }

    private void stopLocation() {
        if (mLocationManager != null && mLocationListener != null) {
            Log.d(TAG, "removing location updates");
            mLocationManager.removeUpdates(mLocationListener);
            mOverlays.remove(mPositionOverlay);
            mLocationEnabled = false;
        }
        mMapView.postInvalidate();
    }

    private void handleNewLocation(Location location) {
        mCurrentLocation = location;
        mPositionOverlay.updateLocation(location);
        this.runOnUiThread(new Runnable() {
            public void run() {            
                mMapView.postInvalidate();
            }
        });
    }
    
    private void zoomToLocation(Location location) {
        if (location == null) return;
        int lat = ((Double) (location.getLatitude() * 1e6)).intValue();
        int lon = ((Double) (location.getLongitude() * 1e6)).intValue();
        GeoPoint point = new GeoPoint(lat, lon);
        mMapView.getController().animateTo(point);
    }
}
