package org.inaturalist.android;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.tinylog.Logger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.evernote.android.state.State;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.livefront.bridge.Bridge;

public class LocationChooserActivity extends AppCompatActivity implements LocationListener {
    public final static String TAG = "LocationChooserActivity";
	protected static final String LATITUDE = "latitude";
	protected static final String LONGITUDE = "longitude";
	protected static final String ACCURACY = "accuracy";
    protected static final String ICONIC_TAXON_NAME = "iconic_taxon_name";
    protected static final String GEOPRIVACY = "geoprivacy";
    protected static final String PLACE_GUESS = "place_guess";

    protected static final int REQUEST_CODE_CHOOSE_PINNED_LOCATION = 0x1000;
    private static final String REGEX_LAT_LNG = "-?\\d+(.\\d+)?[ ]*,[ ]*-?\\d+(.\\d+)?";
    private static final double EARTH_RADIUS = 6371009;

    private GoogleMap mMap;
    private HashMap<String, Observation> mMarkerObservations;
    private INaturalistApp mApp;
	@State public double mLatitude;
	@State public double mLongitude;
	private boolean mZoomToLocation = false;
	private LocationManager mLocationManager;
	@State public double mAccuracy;
    private ActivityHelper mHelper;
    @State public String mIconicTaxonName;
    private ImageView mObservationsMapMyLocation;
    private ProgressBar mMyLocationProgressView;
    private ImageView mObservationsChangeMapLayers;
    @State public int mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
    private ImageView mGeoprivacy;
    private Spinner mGeoprivacySpinner;
    private EditText mLocationSearch;
    private ImageView mClearLocation;
    private ViewGroup mLocationSearchResultsContainer;
    private ListView mLocationSearchResults;
    @State public String mPlaceGuess;
    @State public String mQuery = "";
    @State public boolean mGeodecodingPlaceName;
    private TextView mActionBarPlaceGuess;
    private TextView mActionBarGeoprivacy;
    private TextView mActionBarLatitude;
    private TextView mActionBarLongtitude;
    private TextView mActionBarAccuracy;
    private Handler mHandler;
    private AutocompleteSessionToken mAutoCompleteToken;
    private PlacesClient mPlacesClient;

    private List<INatPlace> mPlaces = new ArrayList<>();
    private LocationChooserPlaceAdapter mPlaceAdapter = null;
    private ListView mLocationList;
    private ProgressBar mLoadingSearch;
    private boolean mNoMapRefresh;
    private CountDownLatch mWaitForAllResults;
    @State public boolean mLocationSearchOpen;
    private ImageView mCrosshairs;

    @State public boolean mAskedForLocationPermission = false;
    private LocationListener mLocationListener;
    @State public boolean mGettingLocation;
    private Long mLocationRequestedAt;
    private Location mCurrentLocation;

    private static final int ONE_MINUTE = 60 * 1000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mHandler = new Handler();

        mAutoCompleteToken = AutocompleteSessionToken.newInstance();
        mPlacesClient = Places.createClient(this);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.location_chooser);

        mGeoprivacySpinner = (Spinner) findViewById(R.id.geoprivacy_selection);

        if (savedInstanceState == null) {
            mLongitude = getIntent().getDoubleExtra(LONGITUDE, 0);
            mLatitude = getIntent().getDoubleExtra(LATITUDE, 0);
            mAccuracy = getIntent().getDoubleExtra(ACCURACY, 0);
            mIconicTaxonName = getIntent().getStringExtra(ICONIC_TAXON_NAME);
            mPlaceGuess = getIntent().getStringExtra(PLACE_GUESS);

            String geoPrivacyValue = getIntent().getStringExtra(GEOPRIVACY);
            List<String> names = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_items));
            mGeoprivacySpinner.setSelection(names.indexOf(geoPrivacyValue));
        }

        if ((mLongitude != 0) && (mLatitude != 0) && (savedInstanceState == null)) {
        	mZoomToLocation = true;
        }
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setCustomView(R.layout.location_chooser_action_bar);
        actionBar.setDisplayShowCustomEnabled(true);

        mActionBarPlaceGuess = (TextView) actionBar.getCustomView().findViewById(R.id.place_guess);
        mActionBarLatitude = (TextView) actionBar.getCustomView().findViewById(R.id.latitude);
        mActionBarLongtitude = (TextView) actionBar.getCustomView().findViewById(R.id.longitude);
        mActionBarAccuracy = (TextView) actionBar.getCustomView().findViewById(R.id.accuracy);
        mActionBarGeoprivacy = (TextView) actionBar.getCustomView().findViewById(R.id.geoprivacy);

        refreshActionBar();

        mObservationsMapMyLocation = (ImageView) findViewById(R.id.my_location);
        mMyLocationProgressView = (ProgressBar) findViewById(R.id.my_location_progress);
        mObservationsChangeMapLayers = (ImageView) findViewById(R.id.change_map_layers);

        mObservationsMapMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mApp.isPermissionPermanentlyDenied(LocationChooserActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    // User permanently denied location permissions - we cannot show the Android OS location permissions dialog again
                    mHelper.confirm(R.string.permission_required, R.string.to_access_your_location, R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();

                            // Open the app's settings screen, so the user can enable the location permission
                            Intent intent = new Intent();
                            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        }
                    });

                    return;
                } else if (!mApp.isLocationPermissionGranted()) {
                    if (!mAskedForLocationPermission) {
                        mAskedForLocationPermission = true;
                        mApp.requestLocationPermission(LocationChooserActivity.this, new INaturalistApp.OnRequestPermissionResult() {
                            @Override
                            public void onPermissionGranted() {
                                getLocation();
                            }

                            @Override
                            public void onPermissionDenied() {
                            }
                        });
                    }

                    return;
                }
                getLocation();
            }
        });

        mMyLocationProgressView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGetLocation();
            }
        });

        mCrosshairs = (ImageView) findViewById(R.id.crosshairs);

        mObservationsMapMyLocation.setVisibility(View.VISIBLE);

        mObservationsChangeMapLayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
                    mObservationsMapType = GoogleMap.MAP_TYPE_TERRAIN;
                    mCrosshairs.setColorFilter(Color.parseColor("#000000"));
                } else {
                    mObservationsMapType = GoogleMap.MAP_TYPE_SATELLITE;
                    mCrosshairs.setColorFilter(Color.parseColor("#ffffff"));
                }

                refreshMapType();
            }
        });

        mGeoprivacy = (ImageView) findViewById(R.id.geoprivacy);
        mGeoprivacy.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.selection(getString(R.string.location_visibility), getResources().getStringArray(R.array.geoprivacy_items), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGeoprivacySpinner.setSelection(which);
                        updateObservationVisibility();
                        refreshActionBar();
                    }
                });
            }
        });

        mLocationSearch = (EditText) findViewById(R.id.location_search);
        mClearLocation = (ImageView) findViewById(R.id.clear_search);

        mLocationSearchResultsContainer = (ViewGroup) findViewById(R.id.location_search_results_container);
        mLocationSearchResults = (ListView) findViewById(R.id.location_search_results);

        mClearLocation.setVisibility(View.INVISIBLE);

        mLocationSearchResultsContainer.setVisibility(View.GONE);
        mLocationSearch.setOnTouchListener((view, motionEvent) -> {
            showLocationSearch();
            return false;
        });

        mLocationSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void afterTextChanged(Editable editable) {
                refreshPlaceQuery();
            }
        });

        mClearLocation.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                hideLocationSearch();
            }
        });
        
        mLocationList = (ListView) findViewById(R.id.location_search_results);
        mLoadingSearch = (ProgressBar) findViewById(R.id.loading_search);

        mLoadingSearch.setVisibility(View.GONE);

        mLocationList.setOnItemClickListener((adapterView, view, index, l) -> {
            mHelper.loading();

            new Thread(() -> {
                try {
                    mWaitForAllResults.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                INatPlace place = mPlaces.get(index);

                if ((place.longitude != null) && (place.latitude != null)) {
                    mLongitude = place.longitude;
                    mLatitude = place.latitude;
                    mAccuracy = place.accuracy;
                    mPlaceGuess = place.title;
                }

                runOnUiThread(() -> {
                    mHelper.stopLoading();
                    hideLocationSearch();

                    refreshActionBar();
                    zoomToLocation();
                });
            }).start();

        });

        refreshMapType();
        updateObservationVisibility();
    }

    private void refreshPlaceQuery() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(() -> {
            String query = mLocationSearch.getText().toString();

            if (query.length() == 0) {
                mLoadingSearch.setVisibility(View.GONE);
                mLocationList.setVisibility(View.VISIBLE);
                return;
            }

            mLoadingSearch.setVisibility(View.VISIBLE);
            mLocationList.setVisibility(View.GONE);

            VisibleRegion region = mMap.getProjection().getVisibleRegion();

            RectangularBounds bounds;

            if (region.farRight.latitude >= region.nearLeft.latitude) {
                bounds = RectangularBounds.newInstance(
                        region.nearLeft,
                        region.farRight);
            } else {
                 bounds = RectangularBounds.newInstance(
                        region.farRight,
                        region.nearLeft);
            }

            if (query.matches(REGEX_LAT_LNG)) {
                // Lat/lng search (not a place search)
                double lat = Double.valueOf(query.split(",")[0].trim());
                double lng = Double.valueOf(query.split(",")[1].trim());
                Logger.tag(TAG).info("Location search for lat/lng: " + lat + "/" + lng);

                mWaitForAllResults = new CountDownLatch(1);
                mPlaces = new ArrayList<>();

                INatPlace inatPlace = new INatPlace();
                inatPlace.id = null;
                inatPlace.title = String.format(getString(R.string.location_lat_lng), lat, lng);
                inatPlace.subtitle = null;
                inatPlace.latitude = lat;
                inatPlace.longitude = lng;
                inatPlace.accuracy = Double.valueOf(0);

                mPlaces.add(inatPlace);

                // Refresh results

                mPlaceAdapter = new LocationChooserPlaceAdapter(this, mPlaces);

                runOnUiThread(() -> {
                    mLocationList.setAdapter(mPlaceAdapter);
                    mLoadingSearch.setVisibility(View.GONE);
                    mLocationList.setVisibility(View.VISIBLE);
                });

                mWaitForAllResults.countDown();

                return;
            }

            FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(mAutoCompleteToken)
                    .setLocationBias(bounds)
                    .setQuery(query)
                    .build();

            mQuery = query;

            mPlacesClient.findAutocompletePredictions(request).addOnSuccessListener((response) -> {
                if (!query.equals(mQuery)) return;

                new Thread(() -> loadPlaceResults(response.getAutocompletePredictions())).start();
            }).addOnFailureListener((exception) -> {
                Logger.tag(TAG).error("Place not found: " + exception);
            });
        }, 500);
    }

    private void loadPlaceResults(List<AutocompletePrediction> predictions) {
        mWaitForAllResults = new CountDownLatch(predictions.size());

        mPlaces = new ArrayList<>();

        for (AutocompletePrediction prediction : predictions) {
            INatPlace inatPlace = new INatPlace();
            inatPlace.id = prediction.getPlaceId();
            inatPlace.title = prediction.getPrimaryText(null).toString();
            inatPlace.subtitle = prediction.getSecondaryText(null).toString();

            mPlaces.add(inatPlace);

            getPlaceDetails(prediction, mPlaces.size() - 1, mWaitForAllResults);
        }

        // Refresh results
        
        mPlaceAdapter = new LocationChooserPlaceAdapter(this, mPlaces);

        runOnUiThread(() -> {
            mLocationList.setAdapter(mPlaceAdapter);
            mLoadingSearch.setVisibility(View.GONE);
            mLocationList.setVisibility(View.VISIBLE);
        });
    }

    private void getPlaceDetails(AutocompletePrediction prediction, int index, CountDownLatch latch) {
        new Thread(() -> {
            if (prediction.getPlaceId() == null) {
                // We only show predictions with place IDs (so we can retrieve exact lat/lng)
                latch.countDown();
                return;
            }

            List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.VIEWPORT);
            FetchPlaceRequest request = FetchPlaceRequest.builder(prediction.getPlaceId(), fields).build();

            mPlacesClient.fetchPlace(request).addOnSuccessListener(fetchPlaceResponse -> {
                // Only when successfully fetching the place details -> add it to the results list
                Place googlePlace = fetchPlaceResponse.getPlace();

                LatLngBounds viewport = googlePlace.getViewport();

                Double radius = null;
                Double latitude = googlePlace.getLatLng().latitude;
                Double longitude = googlePlace.getLatLng().longitude;

                if (viewport != null) {
                    LatLng center = viewport.getCenter();
                    LatLng northeast = viewport.northeast;

                    // Radius is the largest distance from geom center to one of the bounds corners
                    radius = Math.max(
                            distanceInMeters(latitude, longitude,
                                    center.latitude, center.longitude),
                            distanceInMeters(latitude, longitude,
                                    northeast.latitude, northeast.longitude)
                    );
                } else {
                    radius = 10.0;
                }

                if (index < mPlaces.size()) {
                    mPlaces.get(index).accuracy = radius;
                    mPlaces.get(index).latitude = latitude;
                    mPlaces.get(index).longitude = longitude;
                }

                latch.countDown();
            }).addOnFailureListener(e -> {
                latch.countDown();
            });
        }).start();
    }

    private void refreshActionBar() {
        mActionBarPlaceGuess.setText(mGeodecodingPlaceName ? getString(R.string.loading) : ((mPlaceGuess == null || mPlaceGuess.length() == 0) ? getString(R.string.location) : mPlaceGuess));
        mActionBarLatitude.setText(String.format("%.2f", mLatitude));
        mActionBarLongtitude.setText(String.format("%.2f", mLongitude));
        mActionBarAccuracy.setText(String.format("%d", (int)mAccuracy));
        mActionBarGeoprivacy.setText(String.format(getString(R.string.geoprivacy_with_value), mGeoprivacySpinner.getSelectedItem()));
    }

    private void hideLocationSearch() {
        if (!mLocationSearchOpen) return;

        Animation moveDown = AnimationUtils.loadAnimation(LocationChooserActivity.this, R.anim.slide_down);
        mLocationSearchOpen = false;
        moveDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                ActionBar actionBar = getSupportActionBar();
                actionBar.show();

                if (getCurrentFocus() != null) {
                    // Hide keyboard
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

                mLocationSearch.setText("");
                mLocationSearch.clearFocus();
                mClearLocation.setVisibility(View.INVISIBLE);
                mLocationList.setAdapter(new LocationChooserPlaceAdapter(LocationChooserActivity.this, new ArrayList<>()));
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mLocationSearchResultsContainer.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mLocationSearchResultsContainer.startAnimation(moveDown);
    }

    private void showLocationSearch() {
        if (mLocationSearchOpen) return;

        Animation moveUp = AnimationUtils.loadAnimation(LocationChooserActivity.this, R.anim.slide_up);
        mLocationSearchOpen = true;
        moveUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mLocationSearchResultsContainer.setVisibility(View.VISIBLE);
                ActionBar actionBar = getSupportActionBar();
                actionBar.hide();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mClearLocation.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mLocationSearchResultsContainer.startAnimation(moveUp);
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

    private void updateObservationVisibility() {
        int index = mGeoprivacySpinner.getSelectedItemPosition();

        switch (index) {
            case 0:
                mGeoprivacy.setImageResource(R.drawable.ic_public_black_24dp);
                break;
            case 1:
                mGeoprivacy.setImageResource(R.drawable.ic_filter_tilt_shift_black_24dp);
                break;
            case 2:
                mGeoprivacy.setImageResource(R.drawable.ic_visibility_off_black_24dp);
                break;
        }
    }


    private void zoomToLocation() {
        
        double longitude = mLongitude;
        double latitude = mLatitude;
        mNoMapRefresh = true;
        
        if ((longitude != 0) && (latitude != 0)) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenWidth = metrics.widthPixels;

            // Make enough room for the accuracy circle
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            LatLng center = new LatLng(mLatitude, mLongitude);
            LatLng rightPoint = computeOffset(center, mAccuracy, 90);
            LatLng leftPoint = computeOffset(center, mAccuracy, 270);
            builder.include(center);
            builder.include(leftPoint);
            builder.include(rightPoint);
            LatLngBounds bounds = builder.build();

            if (mZoomToLocation) {
                if (mMap != null) {
                    new Handler().postDelayed(() -> mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (int) (screenWidth * 0.3))), 100);
                }
        		mZoomToLocation = false;
        	} else {
                if (mMap != null) {
                    new Handler().postDelayed(() -> mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (int) (screenWidth * 0.3)), 1, null), 100);
                }
        	}
        }
        
    }

    private void refreshMapType() {
        if (mObservationsMapType == GoogleMap.MAP_TYPE_SATELLITE) {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_terrain_black_48dp);
        } else {
            mObservationsChangeMapLayers.setImageResource(R.drawable.ic_satellite_black_48dp);
        }

        if ((mMap != null) && (mMap.getMapType() != mObservationsMapType))
            mMap.setMapType(mObservationsMapType);
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
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.location_chooser_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    private void onCancel() {
        mHelper.confirm(getString(R.string.edit_location), getString(R.string.discard_location_changes),
                new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int buttonId) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                },
                new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int buttonId) {
                        dialog.cancel();
                    }
                },
                R.string.yes, R.string.no);
    }
    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
    	    if (mLocationSearchOpen) {
    	        hideLocationSearch();
            } else {
                onCancel();
            }

            return false;
    	} else {
    		return super.onKeyDown(keyCode, event);
    	}
    }

    private void updateLocationBasedOnMap() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // First point - where the accuracy circle starts on screen
        Point p1 = new Point();
        p1.x = (int) (screenWidth * 0.3); p1.y = (int) (screenHeight * 0.5);
        LatLng leftSide = mMap.getProjection().fromScreenLocation(p1);

        // Second point - the middle of the accuracy circle (to get its radius)
        Point p2 = new Point();
        p2.x = (int) (screenWidth * 0.5); p2.y = (int) (screenHeight * 0.5);
        LatLng rightSide = mMap.getProjection().fromScreenLocation(p2);

        float[] results = new float[3];
        Location.distanceBetween(leftSide.latitude, leftSide.longitude, rightSide.latitude, rightSide.longitude, results);
        mAccuracy = results[0];
        Logger.tag(TAG).error("Meters per radius = " + mAccuracy);

        mLatitude = mMap.getCameraPosition().target.latitude;
        mLongitude = mMap.getCameraPosition().target.longitude;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        List<String> values = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_values));

        switch (item.getItemId()) {
            case android.R.id.home:
                onCancel();

                return true;
            case R.id.save_location:
                Bundle bundle = new Bundle();

                updateLocationBasedOnMap();

                if ((mLatitude == 0) || (mLongitude == 0)) {
                    // Don't allow 0/0 coordinates
                    return true;
                }

                bundle.putDouble(LATITUDE, mLatitude);
                bundle.putDouble(LONGITUDE, mLongitude);
                bundle.putDouble(ACCURACY, mAccuracy);
                int position = mGeoprivacySpinner.getSelectedItemPosition();
                bundle.putString(GEOPRIVACY, (String) values.get(position == -1 ? 0 : position));
                bundle.putString(PLACE_GUESS, mPlaceGuess);

                Intent resultIntent = new Intent();
                resultIntent.putExtras(bundle);
                setResult(RESULT_OK, resultIntent);

                finish();

                return true;

            case R.id.edit_locality_notes:
                editLocalityNotes();
                return true;

            case R.id.choose_pinned_location:
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return true;
                }

                Intent intent = new Intent(this, PinnedLocationSearchActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivityForResult(intent, REQUEST_CODE_CHOOSE_PINNED_LOCATION);
                return true;

            case R.id.pin_current_location:
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return true;
                }

                Intent serviceIntent = new Intent(INaturalistService.ACTION_PIN_LOCATION, null, LocationChooserActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.LATITUDE, mLatitude);
                serviceIntent.putExtra(INaturalistService.LONGITUDE, mLongitude);
                serviceIntent.putExtra(INaturalistService.ACCURACY, mAccuracy);
                serviceIntent.putExtra(INaturalistService.GEOPRIVACY, (String) values.get(mGeoprivacySpinner.getSelectedItemPosition()));
                serviceIntent.putExtra(INaturalistService.TITLE, mPlaceGuess);
                ContextCompat.startForegroundService(LocationChooserActivity.this, serviceIntent);

                Toast.makeText(getApplicationContext(), R.string.location_pinned_successfully, Toast.LENGTH_LONG).show();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void editLocalityNotes() {
        final EditText input = new EditText(LocationChooserActivity.this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        input.setMaxLines(1);
        input.setSingleLine(true);

        String placeGuess = mPlaceGuess;
        input.setText(placeGuess);
        input.setSelection(0, placeGuess.length());

        mHelper.confirm(R.string.edit_locality_notes, input, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // OK - update place guess
                mPlaceGuess = input.getText().toString();
                refreshActionBar();
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Cancel
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (getCurrentFocus() != null) {
                            // Hide keyboard
                            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                        }
                        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                        input.clearFocus();
                    }
                }, 300);
            }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }
    
    private void setUpMapIfNeeded() {
        if (mMarkerObservations == null) {
            mMarkerObservations = new HashMap<String, Observation>();
        }
        if (mMap == null) {
            ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(new OnMapReadyCallback() {
                @SuppressLint("MissingPermission")
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;

                    // Check if we were successful in obtaining the map.
                    if (mMap != null) {
                        // The Map is verified. It is now safe to manipulate the map.
                        if (!mMarkerObservations.isEmpty()) {
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            for (Observation o: mMarkerObservations.values()) {
                                if (o.private_latitude != null && o.private_longitude != null) {
                                    builder.include(new LatLng(o.private_latitude, o.private_longitude));
                                } else {
                                    builder.include(new LatLng(o.latitude, o.longitude));
                                }
                            }
                        }

                        mMap.clear();
                        MarkerOptions opts = new MarkerOptions().position(new LatLng(mLatitude, mLongitude)).icon(TaxonUtils.observationMarkerIcon(mIconicTaxonName));
                        Marker m = mMap.addMarker(opts);

                        zoomToLocation();

                        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
                            @Override
                            public void onCameraMove() {
                                mObservationsMapMyLocation.setColorFilter(Color.parseColor("#676767"));

                                if (!mNoMapRefresh) {
                                    // User moved the map view - reset place guess
                                    updateLocationBasedOnMap();
                                    refreshActionBar();

                                    // Make sure we geodecode the location only after the map finished moving
                                    mHandler.removeCallbacksAndMessages(null);
                                    mHandler.postDelayed(() -> {
                                        guessLocation();
                                    }, 500);
                                }

                                mNoMapRefresh = false;
                            }
                        });

                    }
                }
            });

        }
    }

   private void guessLocation() {
        mGeodecodingPlaceName = true;
        refreshActionBar();

        (new Thread(new Runnable() {
            @Override
            public void run() {
                Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                try {
                    final StringBuilder location = new StringBuilder();
                    List<Address> addresses = geocoder.getFromLocation(mLatitude, mLongitude, 10);
                    if ((null != addresses) && (addresses.size() > 0)) {
                        for (Address address : addresses) {
                            if (address.getThoroughfare() == null) {
                                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                                    location.append(address.getAddressLine(i));
                                    location.append(" ");
                                }

                                break;
                            }
                        }

                        runOnUiThread(() -> {
                            mGeodecodingPlaceName = false;
                            mPlaceGuess = location.toString();
                            refreshActionBar();
                        });

                    } else {
                        runOnUiThread(() -> {
                            mGeodecodingPlaceName = false;
                            refreshActionBar();
                        });
                    }
                } catch (IOException e) {
                    Logger.tag(TAG).error(e);
                    runOnUiThread(() -> {
                        mGeodecodingPlaceName = false;
                        refreshActionBar();
                    });
                }
            }
        })).start();
    }
  
	@Override
	public void onLocationChanged(Location location) {
        if (location != null) {
            Logger.tag(TAG).info("Location changed: " + location.getLatitude() + " and " + location.getLongitude());
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


    // Haversine distance calc, adapted from http://www.movable-type.co.uk/scripts/latlong.html
    double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        int earthRadius = 6370997; // m
        double degreesPerRadian = 57.2958;
        double dLat = (lat2 - lat1) / degreesPerRadian;
        double dLon = (lon2 - lon1) / degreesPerRadian;
        double lat1Mod = lat1 / degreesPerRadian;
        double lat2Mod = lat2 / degreesPerRadian;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1Mod) * Math.cos(lat2Mod);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = earthRadius * c;
        return d;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CHOOSE_PINNED_LOCATION) {
            if (resultCode == RESULT_OK) {
                // Update location to pinned location
                mLatitude = data.getDoubleExtra(PinnedLocationSearchActivity.LATITUDE, 0);
                mLongitude = data.getDoubleExtra(PinnedLocationSearchActivity.LONGITUDE, 0);
                mAccuracy = data.getDoubleExtra(PinnedLocationSearchActivity.ACCURACY, 0);
                List<String> values = Arrays.asList(getResources().getStringArray(R.array.geoprivacy_values));
                mGeoprivacySpinner.setSelection(values.indexOf(data.getStringExtra(PinnedLocationSearchActivity.GEOPRIVACY)));
                mPlaceGuess = data.getStringExtra(PinnedLocationSearchActivity.TITLE);

                refreshActionBar();
                zoomToLocation();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        mApp.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // Kicks off place service
    @SuppressLint("MissingPermission")
    private void getLocation() {
        if (!mApp.isLocationPermissionGranted()) {
            if (!mAskedForLocationPermission) {
                mAskedForLocationPermission = true;
                mApp.requestLocationPermission(this, new INaturalistApp.OnRequestPermissionResult() {
                    @Override
                    public void onPermissionGranted() {
                        getLocation();
                    }

                    @Override
                    public void onPermissionDenied() {
                    }
                });
            }

            return;
        }

        if (mLocationListener != null) {
            return;
        }

        mGettingLocation = true;
        mMyLocationProgressView.setVisibility(View.VISIBLE);
        mObservationsMapMyLocation.setVisibility(View.GONE);

        if (mLocationManager == null) {
            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        }

        if (mLocationListener == null) {
            // Define a listener that responds to place updates
            mLocationListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    // Called when a new place is found by the network place provider.

                    handleNewLocation(location);
                }

                public void onStatusChanged(String provider, int status, Bundle extras) {}
                public void onProviderEnabled(String provider) {}
                public void onProviderDisabled(String provider) {}
            };
        }

        // Register the listener with the Location Manager to receive place updates
        if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
        }
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        }

        mLocationRequestedAt = System.currentTimeMillis();
    }

    private void setCurrentLocation(Location location) {
        if ((location == null) || (mMap == null)) {
            return;
        }

        mCurrentLocation = location;

        // Calculate zoom level based on accuracy

        mAccuracy = location.getAccuracy();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;

        // Make enough room for the accuracy circle
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        LatLng center = new LatLng(location.getLatitude(), location.getLongitude());
        LatLng rightPoint = computeOffset(center, mAccuracy, 90);
        LatLng leftPoint = computeOffset(center, mAccuracy, 270);
        builder.include(center);
        builder.include(leftPoint);
        builder.include(rightPoint);
        LatLngBounds bounds = builder.build();

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (int) (screenWidth * 0.3)),
                1000,
                new GoogleMap.CancelableCallback() {
                    @Override
                    public void onFinish() {
                        // TODO - refresh place guess
                        //mInitialLocationBounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                        mObservationsMapMyLocation.setColorFilter(getResources().getColor(R.color.inatapptheme_color));
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    private void handleNewLocation(Location location) {
        if (isBetterLocation(location, mCurrentLocation)) {
            setCurrentLocation(location);
        }

        if (locationIsGood(mCurrentLocation)) {
            Logger.tag(TAG).debug("place was good, removing updates.  mCurrentLocation: " + mCurrentLocation);
            stopGetLocation();
        }

        if (locationRequestIsOld() && locationIsGoodEnough(mCurrentLocation)) {
            Logger.tag(TAG).debug("place request was old and place was good enough, removing updates.  mCurrentLocation: " + mCurrentLocation);
            stopGetLocation();
        }
    }

    private void stopGetLocation() {
        if (mLocationManager != null && mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }
        mLocationListener = null;
        mGettingLocation = false;
        mMyLocationProgressView.setVisibility(View.GONE);
        mObservationsMapMyLocation.setVisibility(View.VISIBLE);
    }


    private boolean locationIsGood(Location location) {
        if (!locationIsGoodEnough(location)) { return false; }
        if (location.getAccuracy() <= 10) {
            return true;
        }
        return false;
    }

    private boolean locationIsGoodEnough(Location location) {
        if (location == null || !location.hasAccuracy()) { return false; }
        if (location.getAccuracy() <= 500) { return true; }
        return false;
    }

        private boolean locationRequestIsOld() {
        long delta = System.currentTimeMillis() - mLocationRequestedAt;
        return delta > ONE_MINUTE;
    }

    private boolean isBetterLocation(Location newLocation, Location currentLocation) {
        if (currentLocation == null) {
            return true;
        }
        if (newLocation.hasAccuracy() && !currentLocation.hasAccuracy()) {
            return true;
        }
        if (!newLocation.hasAccuracy() && currentLocation.hasAccuracy()) {
            return false;
        }
        return newLocation.getAccuracy() < currentLocation.getAccuracy();
    }

    /**
     * Returns the LatLng resulting from moving a distance from an origin
     * in the specified heading (expressed in degrees clockwise from north).
     *
     * @param from     The LatLng from which to start.
     * @param distance The distance to travel.
     * @param heading  The heading in degrees clockwise from north.
     */
    public static LatLng computeOffset(LatLng from, double distance, double heading) {
        distance /= EARTH_RADIUS;
        heading = Math.toRadians(heading);
        double fromLat = Math.toRadians(from.latitude);
        double fromLng = Math.toRadians(from.longitude);
        double cosDistance = Math.cos(distance);
        double sinDistance = Math.sin(distance);
        double sinFromLat = Math.sin(fromLat);
        double cosFromLat = Math.cos(fromLat);
        double sinLat = cosDistance * sinFromLat + sinDistance * cosFromLat * Math.cos(heading);
        double dLng = Math.atan2(
                sinDistance * cosFromLat * Math.sin(heading),
                cosDistance - sinFromLat * sinLat);
        return new LatLng(Math.toDegrees(Math.asin(sinLat)), Math.toDegrees(fromLng + dLng));
    }
}
