package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class MissionDetails extends AppCompatActivity implements AppBarLayout.OnOffsetChangedListener {

    private static final int MAX_MAP_RESULTS = 20;
    public static final String MISSION = "mission";
    public static final String LOCATION_EXPANSION = "location_expansion";

    private INaturalistApp mApp;
    private BetterJSONObject mMission;

    private TabHost mTabHost;
    private ActivityHelper mHelper;

    private AppBarLayout mAppBarLayout;
    private CollapsingToolbarLayout mCollapsingToolbar;
    private ImageView mMissionBackground;
    private TextView mTaxonName;
    private TextView mTaxonScientificName;
    private GoogleMap mMissionMap;
    private TextView mMissionLocation;
    private ViewGroup mMapContainer;
    private ViewGroup mMissionLocationContainer;

    private ArrayList<JSONObject> mObservations;
    private ProgressBar mLoadingMap;
    private NearbyObservationsReceiver mNearbyReceiver;
    private boolean mTaxonNameHidden;
    private ViewGroup mTaxonNameContainer;
    private Button mObserve;
    private ViewPager mNearbyMissionsViewPager;
    private MissionsPagerAdapter mNearbyMissionsPageAdapter;
    private ArrayList<JSONObject> mNearByMissions;
    private TextView mAboutTaxonText;
    private ViewGroup mViewOnWikipedia;
    private ProgressBar mLoadingAbout;
    private String mAboutText;
    private String mWikiTitle;
    private TaxonReceiver mTaxonReceiver;
    private ProgressBar mLoadingNearbyObservations;
    private ViewPager mNearbyObservationsViewPager;
    private ObservationsPagerAdapter mNearbyObservationsPageAdapter;
    private float mLocationExpansion;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
        	this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = new ActivityHelper(this);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_MISSION_DETAILS);

        final Intent intent = getIntent();
        setContentView(R.layout.mission_details);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAppBarLayout = (AppBarLayout) findViewById(R.id.mission_top_bar);
        mAppBarLayout.addOnOffsetChangedListener(this);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mMission = (BetterJSONObject) intent.getSerializableExtra(MISSION);
            mLocationExpansion = intent.getFloatExtra(LOCATION_EXPANSION, 0);
            BetterJSONObject taxon = new BetterJSONObject(mMission.getJSONObject("taxon"));

            int taxonId = taxon.getInt("id");

            // Get nearby observations of the same taxon ID
            Intent getObservationsIntent = new Intent(INaturalistService.ACTION_NEARBY, null, this, INaturalistService.class);

            getObservationsIntent.putExtra("get_location", true);
            getObservationsIntent.putExtra("location_expansion", mLocationExpansion);
            getObservationsIntent.putExtra("taxon_id", taxonId);
            getObservationsIntent.putExtra("per_page", MAX_MAP_RESULTS);
            startService(getObservationsIntent);

            BetterJSONObject resultsObject = (BetterJSONObject) mApp.getServiceResult(INaturalistService.RECOMMENDED_MISSIONS_RESULT);

            if (resultsObject != null) {
                JSONArray results = resultsObject.getJSONArray("results").getJSONArray();
                ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

                for (int i = 0; i < results.length(); i++) {
                    try {
                        JSONObject item = results.getJSONObject(i);
                        JSONObject taxonRes = item.optJSONObject("taxon");
                        if (taxonRes.optInt("id", 0) != taxonId) {
                            // Don't show current mission in the nearby missions results
                            resultsArray.add(item);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                mNearByMissions = resultsArray;
            } else {
                mNearByMissions = new ArrayList<>();
            }

            // Get taxon details for wikipedia summary, etc.
            mTaxonReceiver = new TaxonReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_RESULT);
            registerReceiver(mTaxonReceiver, filter);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
            startService(serviceIntent);


        } else {
            mAboutText = savedInstanceState.getString("mAboutText");
            mWikiTitle = savedInstanceState.getString("mWikiTitle");
            mMission = (BetterJSONObject) savedInstanceState.getSerializable("mission");
            mObservations = loadListFromBundle(savedInstanceState, "mObservations");
            mNearByMissions = loadListFromBundle(savedInstanceState, "mNearbyMissions");
        }


        mCollapsingToolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        mMissionBackground = (ImageView) findViewById(R.id.mission_bg);
        mTaxonName = (TextView) findViewById(R.id.taxon_name);
        mTaxonScientificName = (TextView) findViewById(R.id.taxon_scientific_name);
        mMissionLocation = (TextView) findViewById(R.id.mission_location);
        mMissionMap = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.location_map)).getMap();
        mMapContainer = (ViewGroup) findViewById(R.id.map_container);
        mMissionLocationContainer = (ViewGroup) findViewById(R.id.mission_location_container);
        mLoadingMap = (ProgressBar) findViewById(R.id.loading_map);
        mTaxonNameContainer = (ViewGroup) findViewById(R.id.taxon_name_container);
        mObserve = (Button) findViewById(R.id.observe);
        mNearbyMissionsViewPager = (ViewPager) findViewById(R.id.nearby_missions_view_pager);
        mAboutTaxonText = (TextView) findViewById(R.id.about_taxon);
        mViewOnWikipedia = (ViewGroup) findViewById(R.id.view_on_wikipedia);
        mLoadingAbout = (ProgressBar) findViewById(R.id.loading_about);
        mLoadingNearbyObservations = (ProgressBar) findViewById(R.id.loading_nearby_observations);
        mNearbyObservationsViewPager = (ViewPager) findViewById(R.id.nearby_observations_view_pager);

        mViewOnWikipedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEARBY_WIKI_ARTICLE_FROM_MISSION);

                // Build the Wikipedia URL
                BetterJSONObject taxon = new BetterJSONObject(mMission.getJSONObject("taxon"));
                String obsUrl = null;
                try {
                    String wikiTitle = mWikiTitle;

                    if ((wikiTitle == null) || (wikiTitle.length() == 0)) {
                        wikiTitle = taxon.getString("name");
                    }

                    wikiTitle = wikiTitle.replace(" ", "_");
                    Locale deviceLocale = getResources().getConfiguration().locale;
                    String deviceLanguage =   deviceLocale.getLanguage();
                    obsUrl = "https://" + deviceLanguage + ".wikipedia.org/wiki/" + URLEncoder.encode(wikiTitle, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(obsUrl));
                startActivity(i);
            }
        });

        mMissionMap.setMyLocationEnabled(false);
        mMissionMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMissionMap.getUiSettings().setAllGesturesEnabled(false);
        mMissionMap.getUiSettings().setZoomControlsEnabled(false);

        mObserve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Show the observation editor screen with the taxon already pre-filled
                BetterJSONObject taxon = new BetterJSONObject(mMission.getJSONObject("taxon"));
                Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, MissionDetails.this, ObservationEditor.class);
                intent.putExtra(ObservationEditor.TAXON, taxon);
                startActivity(intent);
            }
        });

        refreshViewState();

        if (mMission == null) {
            finish();
            return;
        }
    }

    private void saveListToBundle(Bundle outState, ArrayList<JSONObject> list, String key) {
        if (list != null) {
        	JSONArray arr = new JSONArray(list);
        	outState.putString(key, arr.toString());
        }
    }

    private ArrayList<JSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        ArrayList<JSONObject> results = new ArrayList<JSONObject>();

        String obsString = savedInstanceState.getString(key);
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("mAboutText", mAboutText);
        outState.putString("mWikiTitle", mWikiTitle);
        outState.putSerializable("mission", mMission);
        saveListToBundle(outState, mObservations, "mObservations");
        saveListToBundle(outState, mNearByMissions, "mNearbyMissions");

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mNearbyReceiver = new NearbyObservationsReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_NEARBY);
        registerReceiver(mNearbyReceiver, filter);

        refreshViewState();
    }

    private void refreshViewState() {
        BetterJSONObject taxon = new BetterJSONObject(mMission.getJSONObject("taxon"));
        BetterJSONObject defaultPhoto = new BetterJSONObject(taxon.getJSONObject("default_photo"));

        UrlImageViewHelper.setUrlDrawable(mMissionBackground, defaultPhoto.getString("medium_url"), new UrlImageViewCallback() {
            @Override
            public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
            }

            @Override
            public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                return loadedBitmap;
            }
        });

        mTaxonName.setText(taxon.getString("preferred_common_name"));
        mCollapsingToolbar.setTitle(taxon.getString("preferred_common_name"));
        mCollapsingToolbar.setExpandedTitleColor(getResources().getColor(android.R.color.transparent));
        mCollapsingToolbar.setCollapsedTitleTextColor(Color.parseColor("#000000"));
        mCollapsingToolbar.setBackgroundColor(Color.parseColor("#FFFFFF"));
        mTaxonScientificName.setText(taxon.getString("name"));

        if (mObservations == null) {
            mLoadingMap.setVisibility(View.VISIBLE);
            mMapContainer.setVisibility(View.GONE);
            mMissionLocationContainer.setVisibility(View.GONE);

            mLoadingNearbyObservations.setVisibility(View.VISIBLE);
            mNearbyObservationsViewPager.setVisibility(View.GONE);

        } else {
            mLoadingMap.setVisibility(View.GONE);
            mMapContainer.setVisibility(View.VISIBLE);
            mMissionLocationContainer.setVisibility(View.VISIBLE);

            mLoadingNearbyObservations.setVisibility(View.GONE);
            mNearbyObservationsViewPager.setVisibility(View.VISIBLE);

            mNearbyObservationsPageAdapter = new ObservationsPagerAdapter(this, mObservations);
            mNearbyObservationsViewPager.setAdapter(mNearbyObservationsPageAdapter);

            mMissionMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    // Show the map screen
                    Intent intent = new Intent(MissionDetails.this, MissionDetailsMapActivity.class);
                    JSONArray arr = new JSONArray(mObservations);
                    intent.putExtra(MissionDetailsMapActivity.OBSERVATIONS, arr.toString());
                    startActivity(intent);
                }
            });

            mMissionMap.clear();
            final LatLngBounds.Builder builder = new LatLngBounds.Builder();

            for (int i = 0; i < mObservations.size(); i++) {
                BetterJSONObject observation = new BetterJSONObject(mObservations.get(i));
                String placeGuess = observation.getString("place_guess");
                Double latitude = observation.getDouble("latitude");
                Double longitude = observation.getDouble("longitude");

                if (i == 0) {
                    // First observation - determines initial map location, location guess text and the first big marker
                    if ((placeGuess != null) && (placeGuess.length() > 0)) {
                        mMissionLocation.setText(placeGuess);
                    } else if (latitude != null) {
                        // No place guess
                        mMissionLocation.setText(String.format(getString(R.string.location_coords_no_acc),
                                String.format("%.3f...", latitude),
                                String.format("%.3f...", longitude)));


                    } else {
                        // No location at all
                        ((ViewGroup) mMissionLocation.getParent()).setVisibility(View.GONE);
                    }

                    if (latitude != null) {
                        LatLng latLng = new LatLng(latitude, longitude);

                        // Add the marker (it's the main one, so it's bigger in size)

                        BitmapDrawable bd = (BitmapDrawable) getDrawable(R.drawable.mm_34_dodger_blue);
                        Bitmap bitmap = bd.getBitmap();
                        Bitmap doubleBitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth() * 1.3), (int)(bitmap.getHeight() * 1.3), false);

                        MarkerOptions opts = new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromBitmap(doubleBitmap));
                        Marker m = mMissionMap.addMarker(opts);
                        builder.include(latLng);
                    }
                } else if (latitude != null) {
                    // Add observation marker

                    LatLng latLng = new LatLng(latitude, longitude);
                    MarkerOptions opts = new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.mm_34_dodger_blue));
                    Marker m = mMissionMap.addMarker(opts);
                    builder.include(latLng);
                }
            }

            mMissionMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {

                @Override
                public void onCameraChange(CameraPosition arg0) {
                    LatLngBounds bounds = builder.build();
                    CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 100 /* Padding */);
                    mMissionMap.moveCamera(cu);

                    // Remove listener to prevent position reset on camera move.
                    mMissionMap.setOnCameraChangeListener(null);
                }
            });
        }
        
        mNearbyMissionsPageAdapter = new MissionsPagerAdapter(this, mNearByMissions, mLocationExpansion, true);
        mNearbyMissionsViewPager.setAdapter(mNearbyMissionsPageAdapter);

        if (mAboutText == null) {
            mLoadingAbout.setVisibility(View.VISIBLE);
            mAboutTaxonText.setVisibility(View.GONE);
            mViewOnWikipedia.setVisibility(View.GONE);
        } else {
            mLoadingAbout.setVisibility(View.GONE);
            mAboutTaxonText.setVisibility(View.VISIBLE);
            mViewOnWikipedia.setVisibility(View.VISIBLE);
            mAboutTaxonText.setText(Html.fromHtml(mAboutText));
        }
    }


    private class NearbyObservationsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String error = extras.getString("error");
            if (error != null) {
                mHelper.alert(String.format(getString(R.string.couldnt_load_nearby_observations), error));
                return;
            }

            SerializableJSONArray resultsJSON = (SerializableJSONArray) mApp.getServiceResult(INaturalistService.ACTION_NEARBY);
            JSONArray results = resultsJSON.getJSONArray();
            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }

            mObservations = resultsArray;

            refreshViewState();
        }
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int offset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(offset) / (float) maxScroll;

        if (percentage >= 0.9f) {
            if (!mTaxonNameHidden) {
                startAlphaAnimation(mMissionBackground, 100, View.INVISIBLE);
                Drawable upArrow = getResources().getDrawable(R.drawable.ic_arrow_back);
                upArrow.setColorFilter(Color.parseColor("#7A7A7A"), PorterDuff.Mode.SRC_ATOP);
                getSupportActionBar().setHomeAsUpIndicator(upArrow);
                mTaxonNameHidden = true;
            }
        } else {
            if (mTaxonNameHidden) {
                startAlphaAnimation(mMissionBackground, 100, View.VISIBLE);
                Drawable upArrow = getResources().getDrawable(R.drawable.ic_arrow_back);
                upArrow.setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.SRC_ATOP);
                getSupportActionBar().setHomeAsUpIndicator(upArrow);
                mTaxonNameHidden = false;
            }
        }
    }

    public static void startAlphaAnimation (final View v, long duration, final int visibility) {
        AlphaAnimation alphaAnimation = (visibility == View.VISIBLE)
                ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);

        alphaAnimation.setDuration(duration);
        alphaAnimation.setFillAfter(true);
        v.startAnimation(alphaAnimation);
    }

    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxonReceiver);
            BetterJSONObject taxon = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_RESULT);

            if (taxon == null) {
                return;
            }

            mAboutText = taxon.getString("wikipedia_summary");
            if (mAboutText == null) mAboutText = "";
            mWikiTitle = taxon.getString("wikipedia_title");
            if (mWikiTitle == null) mWikiTitle = "";

            refreshViewState();
        }
    }


    public class ObservationsPagerAdapter extends PagerAdapter {
        private UserObservationAdapter mAdapter;
        private ArrayList<JSONObject> mObservations;
        private Context mContext;

        public ObservationsPagerAdapter(Context context, ArrayList<JSONObject> observations) {
            mContext = context;
            mAdapter = new UserObservationAdapter(context, observations, UserObservationAdapter.VIEW_TYPE_CARDS);
            mObservations = observations;
        }

        @Override
        public int getCount() {
            return mAdapter.getCount();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == (View)object;
        }

        @Override
        public float getPageWidth(int position) {
            return 0.32f;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            View view = mAdapter.getView(position, null, container);
            ((ViewPager)container).addView(view, 0);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Load the observation details screen
                    JSONObject item = (JSONObject) view.getTag();
                    Intent intent = new Intent(MissionDetails.this, ObservationViewerActivity.class);
                    intent.putExtra("observation", item.toString());
                    intent.putExtra("read_only", true);
                    intent.putExtra("reload", true);
                    startActivity(intent);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEARBY_OBS_FROM_MISSION);
                }
            });

            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            ((ViewPager) container).removeView((View) object);
        }
    }
}
