package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
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
import java.util.Locale;

import uk.co.senab.photoview.HackyViewPager;

public class TaxonActivity extends AppCompatActivity implements TaxonomyAdapter.TaxonomyListener {
    // Max number of taxon photos we want to display
    private static final int MAX_TAXON_PHOTOS = 8;

    private static String TAG = "TaxonActivity";

    private static final int TAXON_SEARCH_REQUEST_CODE = 302;

    public static final int RESULT_COMPARE_TAXON = 0x1000;
    public static final int SELECT_TAXON_REQUEST_CODE = 0x1001;

    public static final int TAXON_SUGGESTION_NONE = 0;
    public static final int TAXON_SUGGESTION_COMPARE_AND_SELECT = 1;
    public static final int TAXON_SUGGESTION_SELECT = 2;

    public static String TAXON = "taxon";
    public static String OBSERVATION = "observation";
    public static String DOWNLOAD_TAXON = "download_taxon";
    public static String TAXON_SUGGESTION = "taxon_suggestion";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private BetterJSONObject mTaxon;
    private TaxonBoundsReceiver mTaxonBoundsReceiver;
    private TaxonReceiver mTaxonReceiver;
    private boolean mDownloadTaxon;
    private BetterJSONObject mObservation;

    private ViewGroup mPhotosContainer;
    private ViewGroup mNoPhotosContainer;
    private HackyViewPager mPhotosViewPager;
    private CirclePageIndicator mPhotosIndicator;
    private TextView mTaxonName;
    private TextView mTaxonScientificName;
    private TextView mWikipediaSummary;
    private ViewGroup mConservationStatusContainer;
    private TextView mConservationStatus;
    private TextView mConservationSource;
    private ProgressBar mLoadingPhotos;
    private GoogleMap mMap;
    private ScrollView mScrollView;
    private ImageView mTaxonomyIcon;
    private ViewGroup mViewOnINat;
    private ViewGroup mTaxonButtons;
    private ViewGroup mSelectTaxon;
    private ViewGroup mCompareTaxon;
    private ListView mTaxonomyList;
    private ImageView mTaxonicIcon;
    private ViewGroup mTaxonInactive;

    private boolean mMapBoundsSet = false;
    private int mTaxonSuggestion = TAXON_SUGGESTION_NONE;
    private boolean mIsTaxonomyListExpanded = false;

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

    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxonReceiver);

            BetterJSONObject taxon = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_RESULT);

            if (taxon == null) {
                // Connection error
                // TODO
                return;
            }

            mTaxon = taxon;
            mDownloadTaxon = false;
            loadTaxon();
        }
    }

    private class TaxonBoundsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxonBoundsReceiver);

            BetterJSONObject boundsJson = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_OBSERVATION_BOUNDS_RESULT);

            if (boundsJson == null) {
                // Connection error or no observations yet
                findViewById(R.id.observations_map).setVisibility(View.GONE);
                return;
            }

            findViewById(R.id.observations_map).setVisibility(View.VISIBLE);

            // Set map bounds
            if (mMap != null) {
                LatLngBounds bounds = new LatLngBounds(
                        new LatLng(boundsJson.getDouble("swlat"), boundsJson.getDouble("swlng")),
                        new LatLng(boundsJson.getDouble("nelat"), boundsJson.getDouble("nelng")));
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
                centerObservation();
            }
            mMapBoundsSet = true;
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();

        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra(TAXON);
            mObservation = (BetterJSONObject) intent.getSerializableExtra(OBSERVATION);
            mDownloadTaxon = intent.getBooleanExtra(DOWNLOAD_TAXON, false);
            mTaxonSuggestion = intent.getIntExtra(TAXON_SUGGESTION, TAXON_SUGGESTION_NONE);
            mMapBoundsSet = false;
            mIsTaxonomyListExpanded = false;
        } else {
        	mTaxon = (BetterJSONObject) savedInstanceState.getSerializable(TAXON);
            mObservation = (BetterJSONObject) savedInstanceState.getSerializable(OBSERVATION);
            mDownloadTaxon = savedInstanceState.getBoolean(DOWNLOAD_TAXON);
            mMapBoundsSet = savedInstanceState.getBoolean("mMapBoundsSet");
            mTaxonSuggestion = savedInstanceState.getInt(TAXON_SUGGESTION);
            mIsTaxonomyListExpanded = savedInstanceState.getBoolean("mIsTaxonomyListExpanded");
        }

        setContentView(R.layout.taxon_page);

        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mPhotosContainer = (ViewGroup) findViewById(R.id.taxon_photos_container);
        mNoPhotosContainer = (ViewGroup) findViewById(R.id.no_taxon_photos_container);
        mPhotosViewPager = (HackyViewPager) findViewById(R.id.taxon_photos);
        mPhotosIndicator = (CirclePageIndicator) findViewById(R.id.photos_indicator);
        mTaxonName = (TextView) findViewById(R.id.taxon_name);
        mTaxonScientificName = (TextView) findViewById(R.id.taxon_scientific_name);
        mWikipediaSummary = (TextView) findViewById(R.id.wikipedia_summary);
        mConservationStatusContainer = (ViewGroup) findViewById(R.id.conservation_status_container);
        mConservationStatus = (TextView) findViewById(R.id.conservation_status);
        mConservationSource = (TextView) findViewById(R.id.conservation_source);
        ((ScrollableMapFragment)getSupportFragmentManager().findFragmentById(R.id.observations_map)).getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                mMap.setMyLocationEnabled(false);
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                mMap.getUiSettings().setAllGesturesEnabled(false);
                mMap.getUiSettings().setZoomControlsEnabled(false);

                // Set the tile overlay (for the taxon's observations map)
                TileProvider tileProvider = new UrlTileProvider(512, 512) {
                    @Override
                    public URL getTileUrl(int x, int y, int zoom) {

                        String s = String.format(INaturalistService.API_HOST + "/colored_heatmap/%d/%d/%d.png?taxon_id=%d",
                                zoom, x, y, mTaxon.getInt("id"));
                        try {
                            return new URL(s);
                        } catch (MalformedURLException e) {
                            throw new AssertionError(e);
                        }
                    }
                };

                TileOverlay tileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));

                mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        Intent intent = new Intent(TaxonActivity.this, TaxonMapActivity.class);
                        intent.putExtra(TaxonMapActivity.TAXON_ID, mTaxon.getInt("id"));
                        intent.putExtra(TaxonMapActivity.TAXON_NAME, actionBar.getTitle());
                        CameraPosition position = mMap.getCameraPosition();
                        intent.putExtra(TaxonMapActivity.MAP_LATITUDE, position.target.latitude);
                        intent.putExtra(TaxonMapActivity.MAP_LONGITUDE, position.target.longitude);
                        intent.putExtra(TaxonMapActivity.MAP_ZOOM, position.zoom);
                        intent.putExtra(TaxonMapActivity.OBSERVATION, mObservation);
                        startActivity(intent);
                    }
                });


            }
        });
        mViewOnINat = (ViewGroup) findViewById(R.id.view_on_inat);
        mLoadingPhotos = (ProgressBar) findViewById(R.id.loading_photos);
        mTaxonButtons = (ViewGroup) findViewById(R.id.taxon_buttons);
        mSelectTaxon = (ViewGroup) findViewById(R.id.select_taxon);
        mCompareTaxon = (ViewGroup) findViewById(R.id.compare_taxon);
        mTaxonicIcon = (ImageView) findViewById(R.id.taxon_iconic_taxon);
        mTaxonInactive = (ViewGroup) findViewById(R.id.taxon_inactive);

        mTaxonButtons.setVisibility(mTaxonSuggestion != TAXON_SUGGESTION_NONE ? View.VISIBLE : View.GONE);

        mSelectTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Taxon selected - return that taxon back
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                JSONObject taxon = mTaxon.getJSONObject();
                bundle.putString(TaxonSearchActivity.ID_NAME, TaxonUtils.getTaxonName(TaxonActivity.this, taxon));
                bundle.putString(TaxonSearchActivity.TAXON_NAME, taxon.optString("name"));
                bundle.putString(TaxonSearchActivity.ICONIC_TAXON_NAME, taxon.optString("iconic_taxon_name"));
                if (taxon.has("default_photo") && !taxon.isNull("default_photo")) bundle.putString(TaxonSearchActivity.ID_PIC_URL, taxon.optJSONObject("default_photo").optString("square_url"));
                bundle.putBoolean(TaxonSearchActivity.IS_CUSTOM, false);
                bundle.putInt(TaxonSearchActivity.TAXON_ID, taxon.optInt("id"));

                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        mCompareTaxon.setVisibility(mTaxonSuggestion == TAXON_SUGGESTION_COMPARE_AND_SELECT ? View.VISIBLE : View.GONE);

        mCompareTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show taxon comparison screen - we do this by indicating the calling activity (TaxonSuggestions/CompareSuggestions)
                // that the user select this taxon for comparison
                Intent intent = new Intent();
                setResult(RESULT_COMPARE_TAXON, intent);

                finish();
            }
        });

        mViewOnINat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                Locale deviceLocale = getResources().getConfiguration().locale;
                String deviceLanguage =   deviceLocale.getLanguage();
                String taxonUrl = String.format("%s/taxa/%d?locale=%s", inatHost, mTaxon.getInt("id"), deviceLanguage);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(taxonUrl));
                startActivity(i);
            }
        });

        mTaxonomyIcon = (ImageView) findViewById(R.id.taxonomy_info);
        mTaxonomyList = (ListView) findViewById(R.id.taxonomy_list);

        mTaxonomyIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHelper.confirm(getString(R.string.about_this_section), getString(R.string.taxonomy_info), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }, null, R.string.got_it, 0);
            }
        });


        // Make sure the user will be able to scroll/zoom the map (since it's inside a ScrollView)
        ((ScrollableMapFragment) getSupportFragmentManager().findFragmentById(R.id.observations_map))
                .setListener(new ScrollableMapFragment.OnTouchListener() {
                    @Override
                    public void onTouch() {
                        mScrollView.requestDisallowInterceptTouchEvent(true);
                    }
                });


        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);

        loadTaxon();
    }

    private String conservationStatusCodeToName(String code) {
        switch (code) {
            case "NE":
                return "not_evaluated";
            case "DD":
                return "data_deficient";
            case "LC":
                return "least_concern";
            case "NT":
                return "near_threatened";
            case "VU":
                return "vulnerable";
            case "EN":
                return "endangered";
            case "CR":
                return "critically_endangered";
            case "EW":
                return "extinct_in_the_wild";
            case "EX":
                return "extinct";
            default:
                return null;
        }
    }

    private void loadTaxon() {
        if (mTaxon == null) {
            finish();
            return;
        }

        final String taxonName = TaxonUtils.getTaxonName(this, mTaxon.getJSONObject());
        getSupportActionBar().setTitle(taxonName);

        mTaxonName.setText(taxonName);
        mTaxonScientificName.setText(TaxonUtils.getTaxonScientificName(mTaxon.getJSONObject()));
        mTaxonScientificName.setTypeface(null, mTaxon.getInt("rank_level") <= 20 ? Typeface.ITALIC : Typeface.NORMAL);

        String wikiSummary = mTaxon.getString("wikipedia_summary");

        if ((wikiSummary == null) || (wikiSummary.length() == 0)) {
            mWikipediaSummary.setVisibility(View.GONE);
        } else {
            mWikipediaSummary.setVisibility(View.VISIBLE);
            mWikipediaSummary.setText(Html.fromHtml(wikiSummary + " " + getString(R.string.source_wikipedia)));
        }


        JSONObject conservationStatus = mTaxon.has("conservation_status") ? mTaxon.getJSONObject("conservation_status") : null;
        String conservationStatusName = conservationStatus != null ? conservationStatusCodeToName(conservationStatus.optString("status")) : null;


        mTaxonInactive.setVisibility(mTaxon.getJSONObject().optBoolean("is_active", true) ? View.GONE : View.VISIBLE);

        mTaxonInactive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                Locale deviceLocale = getResources().getConfiguration().locale;
                String deviceLanguage =   deviceLocale.getLanguage();
                String taxonUrl = String.format("%s/taxon_changes?taxon_id=%d&locale=%s", inatHost, mTaxon.getInt("id"), deviceLanguage);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(taxonUrl));
                startActivity(i);
            }
        });



        if ((conservationStatusName == null) || (conservationStatusName.equals("not_evaluated")) || (conservationStatusName.equals("data_deficient")) ||
                (conservationStatusName.equals("least_concern")) ) {
            mConservationStatusContainer.setVisibility(View.GONE);
        } else {
            mConservationStatusContainer.setVisibility(View.VISIBLE);

            int textColor = mApp.getColorResourceByName("conservation_" + conservationStatusName + "_text");
            int backgroundColor = mApp.getColorResourceByName("conservation_" + conservationStatusName + "_bg");

            mConservationStatus.setText(mApp.getStringResourceByName("conservation_status_" + conservationStatusName));
            mConservationStatusContainer.setBackgroundColor(backgroundColor);
            mConservationStatus.setTextColor(textColor);
            mConservationSource.setTextColor(textColor);
            mConservationSource.setText(Html.fromHtml(String.format(getString(R.string.conservation_source), conservationStatus.optString("authority"))));
            Drawable drawable = getResources().getDrawable(R.drawable.ic_open_in_browser_black_24dp);
            drawable.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            mConservationSource.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }

        mPhotosViewPager.setAdapter(new TaxonPhotosPagerAdapter(this, mTaxon.getJSONObject()));
        mPhotosIndicator.setViewPager(mPhotosViewPager);
        mPhotosViewPager.setVisibility(View.VISIBLE);
        mLoadingPhotos.setVisibility(View.GONE);
        mNoPhotosContainer.setVisibility(View.GONE);
        mPhotosContainer.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams params = mPhotosContainer.getLayoutParams();
        int newHeight;

        if (mPhotosViewPager.getAdapter().getCount() <= 1) {
            mPhotosIndicator.setVisibility(View.GONE);
            newHeight = 310;

            if (mPhotosViewPager.getAdapter().getCount() == 0) {
                if (mDownloadTaxon) {
                    // Still downloading taxon
                    mLoadingPhotos.setVisibility(View.VISIBLE);
                    mPhotosViewPager.setVisibility(View.GONE);
                } else {
                    // Taxon has no photos
                    mNoPhotosContainer.setVisibility(View.VISIBLE);
                    mPhotosContainer.setVisibility(View.GONE);
                }
            }
        } else {
            mPhotosIndicator.setVisibility(View.VISIBLE);
            newHeight = 320;
        }

        params.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newHeight, getResources().getDisplayMetrics());
        mPhotosContainer.setLayoutParams(params);
        mNoPhotosContainer.setLayoutParams(params);

        final TaxonomyAdapter adapter = new TaxonomyAdapter(this, mTaxon, this);

        adapter.setExpanded(mIsTaxonomyListExpanded);
        mTaxonomyList.setAdapter(adapter);

        mHelper.resizeList(mTaxonomyList);

        mTaxonicIcon.setImageResource(TaxonUtils.observationIcon(mTaxon.getJSONObject()));

        centerObservation();
    }

    @Override
    public void onViewChildren(BetterJSONObject taxon) {
        TaxonomyAdapter childrenAdapter = new TaxonomyAdapter(TaxonActivity.this, mTaxon, true, this);
        String taxonName = TaxonUtils.getTaxonName(this, mTaxon.getJSONObject());
        mHelper.selection(taxonName, childrenAdapter);
    }

    @Override
    public void onViewTaxon(BetterJSONObject taxon) {
        Intent intent = new Intent(TaxonActivity.this, TaxonActivity.class);
        intent.putExtra(TaxonActivity.TAXON, taxon);
        intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
        intent.putExtra(TaxonActivity.TAXON_SUGGESTION, mTaxonSuggestion == TAXON_SUGGESTION_COMPARE_AND_SELECT ? TAXON_SUGGESTION_SELECT : mTaxonSuggestion);
        startActivityForResult(intent, SELECT_TAXON_REQUEST_CODE);
    }

    private void centerObservation() {
        if ((mObservation != null) && (mMap != null)) {
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
        outState.putSerializable(TAXON, mTaxon);
        outState.putSerializable(OBSERVATION, mObservation);
        outState.putBoolean(DOWNLOAD_TAXON, mDownloadTaxon);
        outState.putBoolean("mMapBoundsSet", mMapBoundsSet);
        outState.putInt(TAXON_SUGGESTION, mTaxonSuggestion);
        outState.putBoolean("mIsTaxonomyListExpanded", ((TaxonomyAdapter)mTaxonomyList.getAdapter()).isExpanded());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mTaxonBoundsReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mMapBoundsSet) {
            // Get the map bounds for the taxon (for the observations map)
            mTaxonBoundsReceiver = new TaxonBoundsReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.TAXON_OBSERVATION_BOUNDS_RESULT);
            BaseFragmentActivity.safeRegisterReceiver(mTaxonBoundsReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_OBSERVATION_BOUNDS, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxon.getInt("id"));
            startService(serviceIntent);

        }

        if (mDownloadTaxon) {
            // Get the taxon details
            mTaxonReceiver = new TaxonReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_NEW_RESULT);
            Log.i(TAG, "Registering ACTION_GET_TAXON_NEW_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mTaxonReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_NEW, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxon.getInt("id"));
            startService(serviceIntent);
        }
    }


    public static class TaxonPhotosPagerAdapter extends PagerAdapter {
 		private int mDefaultTaxonIcon;
 		private List<JSONObject> mTaxonPhotos;
        private Context mContext;
        private JSONObject mTaxon;


        // Load offline photos for a new observation
        public TaxonPhotosPagerAdapter(Context context, JSONObject taxon) {
            mContext = context;
            mTaxon = taxon;
            mTaxonPhotos = new ArrayList<>();

            mDefaultTaxonIcon = TaxonUtils.observationIcon(taxon);

            JSONArray taxonPhotos = taxon.optJSONArray("taxon_photos");

            if (taxonPhotos == null) return;

            for (int i = 0; (i < taxonPhotos.length()) && (i < MAX_TAXON_PHOTOS); i++) {
                mTaxonPhotos.add(taxonPhotos.optJSONObject(i));
            }

            // Sort by position
            Collections.sort(mTaxonPhotos, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject o1, JSONObject o2) {
                    int pos1 = o1.optInt("position", 0), pos2 = o2.optInt("position", 0);
                    return (pos1 < pos2) ? -1 : ((pos1 == pos2) ? 0 : 1);
                }
            });
        }

 		@Override
 		public int getCount() {
 			return mTaxonPhotos.size();
 		}

 		@Override
 		public View instantiateItem(ViewGroup container, final int position) {
 			View layout = ((Activity) mContext).getLayoutInflater().inflate(R.layout.taxon_photo, null, false);
 			container.addView(layout, RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);

 			final ImageView taxonPhoto = (ImageView) layout.findViewById(R.id.taxon_photo);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.loading_photo);
            TextView photosAttr = (TextView) layout.findViewById(R.id.photo_attr);

            loading.setVisibility(View.VISIBLE);
            taxonPhoto.setVisibility(View.INVISIBLE);

            JSONObject taxonPhotoJSON = mTaxonPhotos.get(position);
            JSONObject innerPhotoJSON = taxonPhotoJSON.optJSONObject("photo");
            String photoUrl = (innerPhotoJSON.has("large_url") && !innerPhotoJSON.isNull("large_url")) ?
                    innerPhotoJSON.optString("large_url") : innerPhotoJSON.optString("original_url");

            if ((photoUrl == null) || (photoUrl.length() == 0)) {
                photoUrl = innerPhotoJSON.optString("medium_url");
            }

            Picasso.with(mContext)
                    .load(photoUrl)
                    .into(taxonPhoto, new Callback() {
                        @Override
                        public void onSuccess() {
                            loading.setVisibility(View.GONE);
                            taxonPhoto.setVisibility(View.VISIBLE);
                        }
                        @Override
                        public void onError() {
                        }
                    });


            taxonPhoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, ObservationPhotosViewer.class);
                    intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, position);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION, mTaxon.toString());
                    intent.putExtra(ObservationPhotosViewer.IS_TAXON, true);
                    mContext.startActivity(intent);
                }
            });

            photosAttr.setText(Html.fromHtml(String.format(mContext.getString(R.string.photo_attr), innerPhotoJSON.optString("attribution"))));

 			return layout;
 		}

 		@Override
 		public void destroyItem(ViewGroup container, int position, Object object) {
 			container.removeView((View) object);
 		}

 		@Override
 		public boolean isViewFromObject(View view, Object object) {
 			return view == object;
 		}

 	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon search directly back to the caller (e.g. observation editor)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            }
        } else if (requestCode == SELECT_TAXON_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon selection directly back to the caller (compare screen)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            }
        }
    }
}
