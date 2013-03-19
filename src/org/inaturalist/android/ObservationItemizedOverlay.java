package org.inaturalist.android;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class ObservationItemizedOverlay extends ItemizedOverlay {
    private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
    private Context mContext;
    private INaturalistApp app;
    private static String TAG = "ObservationItemizedOverlay";

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
        app = (INaturalistApp) context.getApplicationContext();
        populate();
    }

    @Override
    protected boolean onTap(int index) {
        ObservationOverlayItem item = (ObservationOverlayItem) mOverlays.get(index);
        Observation observation = item.getObservation();
        final Uri observationUri = observation.getUri();

        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle(item.getTitle())
            .setMessage(item.getSnippet())
            .setPositiveButton(mContext.getString(R.string.close), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
        String login = app.currentUserLogin();
        if (login != null && login.equals(observation.user_login)) {
            dialog.setNeutralButton(mContext.getString(R.string.edit), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mContext.startActivity(new Intent(Intent.ACTION_EDIT, observationUri)); 
                }
            });
        }
        dialog.show();

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
