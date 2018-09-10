package org.inaturalist.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class MissionDetailsMapActivity extends AppCompatActivity {
    private final static String TAG = "MissionDetailsMapActivity";
    public static final String OBSERVATIONS = "observations";

    private GoogleMap mMap;
    private INaturalistApp mApp;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mObservations;
    private HashMap<String, JSONObject> mMarkerObservations;

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

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));
        actionBar.setLogo(R.drawable.ic_arrow_back_gray_24dp);
        actionBar.setTitle(R.string.map);

        if (savedInstanceState == null) {
            mObservations = loadListFromValue(getIntent().getStringExtra(OBSERVATIONS));

            if (mObservations == null) {
                finish();
            }
        }


        setContentView(R.layout.mission_details_map);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
        setUpMapIfNeeded();

        if (mObservations != null) {
            loadObservationsToMap();
        }
    }

    private void loadObservationsToMap() {
        if (mMap == null) return;

        mMap.clear();
        final LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (int i = 0; i < mObservations.size(); i++) {
            BetterJSONObject observation = new BetterJSONObject(mObservations.get(i));
            String placeGuess = observation.getString("place_guess");
            Double latitude = observation.getDouble("latitude");
            Double longitude = observation.getDouble("longitude");

            if (i == 0) {
                if (latitude != null) {
                    LatLng latLng = new LatLng(latitude, longitude);

                    // Add the marker (it's the main one, so it's bigger in size)
                    BitmapDrawable bd = (BitmapDrawable) getResources().getDrawable(R.drawable.mm_34_dodger_blue);
                    Bitmap bitmap = bd.getBitmap();
                    Bitmap doubleBitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth() * 1.3), (int)(bitmap.getHeight() * 1.3), false);

                    MarkerOptions opts = new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromBitmap(doubleBitmap));
                    Marker m = mMap.addMarker(opts);
                    mMarkerObservations.put(m.getId(), observation.getJSONObject());
                    builder.include(latLng);
                }
            } else if (latitude != null) {
                // Add observation marker for a "regular" mission
                LatLng latLng = new LatLng(latitude, longitude);
                MarkerOptions opts = new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dodger_blue));
                Marker m = mMap.addMarker(opts);
                mMarkerObservations.put(m.getId(), observation.getJSONObject());
                builder.include(latLng);
            }
        }

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition arg0) {
                LatLngBounds bounds = builder.build();
                CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 100 /* Padding */);
                GoogleMap mMissionMap;
                mMap.moveCamera(cu);

                // Remove listener to prevent position reset on camera move.
                mMap.setOnCameraChangeListener(null);
            }
        });

        mMap.setOnMarkerClickListener(
                new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        // Show the observation viewer screen for the marker
                        JSONObject o = mMarkerObservations.get(marker.getId());

                        Intent intent = new Intent(MissionDetailsMapActivity.this, ObservationViewerActivity.class);
                        intent.putExtra("observation", o.toString());
                        intent.putExtra("read_only", true);
                        startActivity(intent);

                        return false;
                    }
                }
        );
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
        inflater.inflate(R.menu.mission_details_map_menu, menu);

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
        mMarkerObservations = new HashMap<String, JSONObject>();

        if (mMap == null) {
            ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;

                    // Check if we were successful in obtaining the map.
                    if (mMap != null) {
                        // The Map is verified. It is now safe to manipulate the map.
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setZoomControlsEnabled(false);

                        mMap.clear();

                        if (mObservations != null) {
                            loadObservationsToMap();
                        }
                    }
                }
            });

        }
    }

    private ArrayList<JSONObject> loadListFromValue(String obsString) {
        ArrayList<JSONObject> results = new ArrayList<JSONObject>();

        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(arr.getJSONObject(i));
                }

                return results;
            } catch (JSONException exc) {
                exc.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

}
