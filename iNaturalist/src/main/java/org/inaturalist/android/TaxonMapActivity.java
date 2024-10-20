package org.inaturalist.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import com.evernote.android.state.State;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.livefront.bridge.Bridge;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class TaxonMapActivity extends AppCompatActivity {
    private static String TAG = "TaxonMapActivity";

    public static String TAXON_ID = "taxon_id";
    public static String TAXON_NAME = "taxon_name";
    public static String MAP_LONGITUDE = "map_longitude";
    public static String MAP_LATITUDE = "map_latitude";
    public static String MAP_ZOOM = "map_zoom";
    public static String OBSERVATION = "observation";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    @State public int mTaxonId;
    @State public String mTaxonName;
    @State public double mMapLatitude;
    @State public double mMapLongitude;
    @State public float mMapZoom;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mObservation;

    private GoogleMap mMap;

    @Override
    protected void onStart()
    {
        super.onStart();


    }

    @Override
    protected void onStop()
    {
        super.onStop();

    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxonId = intent.getIntExtra(TAXON_ID, 0);
            mTaxonName = intent.getStringExtra(TAXON_NAME);
            mMapLongitude = intent.getDoubleExtra(MAP_LONGITUDE, 0);
            mMapLatitude = intent.getDoubleExtra(MAP_LATITUDE, 0);
            mMapZoom = intent.getFloatExtra(MAP_ZOOM, 0);
            mObservation = (BetterJSONObject) intent.getSerializableExtra(OBSERVATION);
        }

        setContentView(R.layout.taxon_map);

        ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.observations_map)).getMapAsync(new OnMapReadyCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                if (mApp.isLocationPermissionGranted()) {
                    mMap.setMyLocationEnabled(true);
                }
                mMap.getUiSettings().setZoomControlsEnabled(true);
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                // Set the tile overlay (for the taxon's observations map)
                TileProvider tileProvider = new INatTileProvider(mApp, 256, 256) {
                    @Override
                    public URL getTileUrl(int x, int y, int zoom) {

                        String s = String.format(Locale.ENGLISH, INaturalistService.API_HOST + "/grid/%d/%d/%d.png?taxon_id=%d&verifiable=true",
                                zoom, x, y, mTaxonId);
                        try {
                            return new URL(s);
                        } catch (MalformedURLException e) {
                            throw new AssertionError(e);
                        }
                    }
                };

                TileOverlay tileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));

                if (mObservation != null) {
                    boolean markerOnly = false;
                    boolean updateCamera = false;
                    final Observation obs = new Observation(mObservation);
                    mHelper.addMapPosition(mMap, obs, mObservation, markerOnly, updateCamera);
                    mHelper.centerObservationImmediate(mMap, obs);
                }

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mMapLatitude, mMapLongitude), mMapZoom));
            }
        });

        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);

        actionBar.setTitle(mTaxonName);

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
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            CameraPosition position = mMap.getCameraPosition();
            mMapLatitude = position.target.latitude;
            mMapLongitude = position.target.longitude;
            mMapZoom = position.zoom;
        }

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (mMap != null) mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mMapLatitude, mMapLongitude), mMapZoom));
    }
}
