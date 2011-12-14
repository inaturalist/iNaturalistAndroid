package org.inaturalist.android;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class ObservationItemizedOverlay extends ItemizedOverlay {
    private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
    private Context mContext;
    
    private class ObservationOverlayItem extends OverlayItem {
        private Observation mObservation;
        public ObservationOverlayItem(GeoPoint point, String title, String snippet) {
            super(point, title, snippet);
        }
        
        public ObservationOverlayItem(GeoPoint point, String title, String snippet, Observation observation) {
            super(point, title, snippet);
            mObservation = observation;
        }
        
        public Observation getObservation() {
            return mObservation;
        }
    }

    public ObservationItemizedOverlay(Drawable defaultMarker) {
        super(boundCenterBottom(defaultMarker));
    }

    public ObservationItemizedOverlay(Drawable defaultMarker, Context context) {
        this(defaultMarker);
        mContext = context;
    }

    @Override
    protected boolean onTap(int index) {
        ObservationOverlayItem item = (ObservationOverlayItem) mOverlays.get(index);
        final Uri observationUri = item.getObservation().getUri();

        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle(item.getTitle())
            .setMessage(item.getSnippet())
            .setNeutralButton("Edit", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mContext.startActivity(new Intent(Intent.ACTION_EDIT, observationUri)); 
                }
            })
            .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            })
            .show();

        return true;
    }

    public void addOverlay(OverlayItem overlay) {
        mOverlays.add(overlay);
        populate();
    }
    
    public void addObservation(Observation o) {
        if (o.latitude == null || o.longitude == null) {
            return;
        }
        int lat = ((Double) (o.latitude * 1e6)).intValue();
        int lon = ((Double) (o.longitude * 1e6)).intValue();
        GeoPoint point = new GeoPoint(lat, lon);
        OverlayItem overlayitem = new ObservationOverlayItem(point, o.species_guess, o.description, o);
        addOverlay(overlayitem);
    }

    @Override
    protected OverlayItem createItem(int i) {
        return mOverlays.get(i);
    }

    @Override
    public int size() {
        return mOverlays.size();
    }

}
