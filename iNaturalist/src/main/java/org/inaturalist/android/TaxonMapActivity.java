package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.viewpagerindicator.CirclePageIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import uk.co.senab.photoview.HackyViewPager;

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
    private int mTaxonId;
    private String mTaxonName;
    private double mMapLatitude, mMapLongitude;
    private float mMapZoom;
    private BetterJSONObject mObservation;

    private GoogleMap mMap;

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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();

        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxonId = intent.getIntExtra(TAXON_ID, 0);
            mTaxonName = intent.getStringExtra(TAXON_NAME);
            mMapLongitude = intent.getDoubleExtra(MAP_LONGITUDE, 0);
            mMapLatitude = intent.getDoubleExtra(MAP_LATITUDE, 0);
            mMapZoom = intent.getFloatExtra(MAP_ZOOM, 0);
            mObservation = (BetterJSONObject) intent.getSerializableExtra(OBSERVATION);
        } else {
        	mTaxonId = savedInstanceState.getInt(TAXON_ID);
            mTaxonName = savedInstanceState.getString(TAXON_NAME);
            mMapLongitude = savedInstanceState.getDouble(MAP_LONGITUDE, 0);
            mMapLatitude = savedInstanceState.getDouble(MAP_LATITUDE, 0);
            mMapZoom = savedInstanceState.getFloat(MAP_ZOOM, 0);
            mObservation = (BetterJSONObject) savedInstanceState.getSerializable(OBSERVATION);
        }

        setContentView(R.layout.taxon_map);

        mMap = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.observations_map)).getMap();

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Set the tile overlay (for the taxon's observations map)
        TileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public URL getTileUrl(int x, int y, int zoom) {

                String s = String.format(INaturalistService.API_HOST + "/colored_heatmap/%d/%d/%d.png?taxon_id=%d",
                        zoom, x, y, mTaxonId);
                try {
                    return new URL(s);
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
            }
        };

        TileOverlay tileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));

        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);

        actionBar.setTitle(mTaxonName);

        if (mObservation != null) {
            boolean markerOnly = false;
            boolean updateCamera = false;
            final Observation obs = new Observation(mObservation);
            mHelper.addMapPosition(mMap, obs, mObservation, markerOnly, updateCamera);
            mHelper.centerObservation(mMap, obs);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(TAXON_ID, mTaxonId);
        outState.putString(TAXON_NAME, mTaxonName);
        CameraPosition position = mMap.getCameraPosition();
        outState.putDouble(MAP_LATITUDE, position.target.latitude);
        outState.putDouble(MAP_LONGITUDE, position.target.longitude);
        outState.putFloat(MAP_ZOOM, position.zoom);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onResume() {
        super.onResume();

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mMapLatitude, mMapLongitude), mMapZoom));
    }
}
