package org.inaturalist.android;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class PositionItemizedOverlay extends ItemizedOverlay {
    public final static String TAG = "PositionItemizedOverlay";
    private OverlayItem mMarker;

    public PositionItemizedOverlay(Context context) {
        super(boundCenter(context.getResources().getDrawable(R.drawable.ic_maps_indicator_current_position)));
        mMarker = new OverlayItem(new GeoPoint(0,0), "Current position", "You are here!");
        populate();
    }

    @Override
    protected OverlayItem createItem(int i) {
        return mMarker;
    }

    @Override
    public int size() {
        return 1;
    }
    
    public void updateLocation(Location location) {
        int lat = ((Double) (location.getLatitude() * 1e6)).intValue();
        int lon = ((Double) (location.getLongitude() * 1e6)).intValue();
        GeoPoint point = new GeoPoint(lat, lon);
        mMarker = new OverlayItem(point, "Current position", "You are here!");
        populate();
    }

}
