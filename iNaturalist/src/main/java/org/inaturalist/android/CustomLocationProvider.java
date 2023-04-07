package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import com.google.android.gms.maps.LocationSource;

public class CustomLocationProvider implements LocationSource, LocationListener {
    private OnLocationChangedListener listener;
    private LocationManager locationManager;

    private static final String TAG = "CustomLocationProvider";

    public CustomLocationProvider(Context context) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void activate(OnLocationChangedListener listener) {
        this.listener = listener;
        LocationProvider gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (gpsProvider != null) {
            locationManager.requestLocationUpdates(gpsProvider.getName(), 500, 5, this);
        }

        LocationProvider networkProvider = locationManager.getProvider(LocationManager.NETWORK_PROVIDER);
        if (networkProvider != null) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 60 * 3, 0, this);
        }
    }

    @Override
    public void deactivate() {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (listener != null && location != null) {
            listener.onLocationChanged(location);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}