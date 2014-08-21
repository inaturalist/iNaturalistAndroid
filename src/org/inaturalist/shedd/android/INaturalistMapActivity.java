package org.inaturalist.shedd.android;
import java.util.HashMap;
import org.inaturalist.shedd.android.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
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

public class INaturalistMapActivity extends SherlockFragmentActivity implements OnMarkerClickListener, OnInfoWindowClickListener {
    public final static String TAG = "INaturalistMapActivity";
    private GoogleMap mMap;
    private Circle mCircle;
    private NearbyObservationsReceiver mNearbyReceiver;
    private ActivityHelper mHelper;
    private HashMap<String, Observation> mMarkerObservations;
    private INaturalistApp mApp;
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.map);
    }

    @Override 
    public void onResume() {
        super.onResume();
        mHelper = new ActivityHelper(this);
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
        setUpMapIfNeeded();
        reloadObservations();
    }

    @Override
    public void onPause() {
        super.onPause();
    }
 
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem layersItem = menu.findItem(R.id.layers);
        if (mMap.getMapType() == GoogleMap.MAP_TYPE_HYBRID) {
            layersItem.setTitle(R.string.street);
        } else {
            layersItem.setTitle(R.string.satellite);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.map_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
         case android.R.id.home:
            finish();
            return true;
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
        case R.id.menu:
            startActivity(new Intent(this, MenuActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return true;
        case R.id.nearby:
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
                reloadObservations();
                mMap.setMyLocationEnabled(true);
                mMap.setOnMarkerClickListener(this);
                mMap.setOnInfoWindowClickListener(this);
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
                    mMap.setOnCameraChangeListener(new OnCameraChangeListener() {
                        @Override
                        public void onCameraChange(CameraPosition arg0) {
                            // Move camera.
                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
                            // Remove listener to prevent position reset on camera move.
                            mMap.setOnCameraChangeListener(null);
                        }
                    });
                }
            }
        }
    }

    private void reloadObservations() {
        if (mMap == null) return;
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
    }
    
    private void addObservation(Observation o) {
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
    
    private void reloadNearbyObservations() {
       mHelper.loading(); 
       mNearbyReceiver = new NearbyObservationsReceiver();
       IntentFilter filter = new IntentFilter(INaturalistService.ACTION_NEARBY);
       registerReceiver(mNearbyReceiver, filter);
       
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
            mHelper.stopLoading();
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
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.found_observations), c.getCount()), Toast.LENGTH_SHORT).show();
            unregisterReceiver(mNearbyReceiver);
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
}
