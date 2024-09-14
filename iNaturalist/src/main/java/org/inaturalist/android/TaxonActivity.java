package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
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

import com.evernote.android.state.State;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
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
import com.kenilt.circleindicator.CirclePageIndicator;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import uk.co.senab.photoview.HackyViewPager;

public class TaxonActivity extends AppCompatActivity implements TaxonomyAdapter.TaxonomyListener {
    // Max number of taxon photos we want to display
    private static final int MAX_TAXON_PHOTOS = 8;

    private static String TAG = "TaxonActivity";

    // The various colors we can use for the lines
    private static final String[] ATTRIBUTE_LINE_COLORS = {
            "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf",
            "#ac9870", "#d53614", "#885059", "#f5dfbe", "#9913a9", "#6fbcd8", "#4bdb2b", "#774868", "#0418a5", "#0b01ce"
    };

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
	@State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mTaxon;
    private TaxonBoundsReceiver mTaxonBoundsReceiver;
    private TaxonReceiver mTaxonReceiver;
    @State public boolean mDownloadTaxon;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mObservation;

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
    private ViewGroup mViewObservations;
    private ViewGroup mTaxonButtons;
    private ViewGroup mSelectTaxon;
    private ViewGroup mCompareTaxon;
    private ListView mTaxonomyList;
    private ImageView mTaxonicIcon;
    private ViewGroup mTaxonInactive;
    private TabLayout mSeasonabilityTabLayout;
    private ViewPager mSeasonabilityViewPager;
    private ProgressBar mLoadingSeasonability;
    private ProgressBar mLoadingHistogram;

    @State public boolean mMapBoundsSet = false;
    @State public int mTaxonSuggestion = TAXON_SUGGESTION_NONE;
    @State public boolean mIsTaxonomyListExpanded = false;
    private HistogramReceiver mHistogramReceiver;
    
    private List<Integer> mHistogram = null;
    private List<Integer> mResearchGradeHistogram = null;
    private TreeMap<String,Map<String,List<Integer>>> mPopularFieldsByAttributes = null;
    private ArrayList<LineChart> mSeasonabilityGraph;

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

    private class HistogramReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            BetterJSONObject results;

            results = (BetterJSONObject) intent.getSerializableExtra(intent.getAction());
            int taxonId =  intent.getIntExtra(INaturalistService.TAXON_ID, -1);

            Logger.tag(TAG).debug("HistogramReceiver - " + intent.getAction());

            if ((results == null) || (taxonId != mTaxon.getInt("id"))) {
                return;
            }

            JSONObject innerResults = results.getJSONObject("results");

            if ((innerResults == null) && (intent.getAction().equals(INaturalistService.HISTOGRAM_RESULT))) {
                return;
            }

            if (intent.getAction().equals(INaturalistService.HISTOGRAM_RESULT)) {
                JSONObject months = innerResults.optJSONObject("month_of_year");
                List<Integer> monthValues = new ArrayList<>();

                for (int i = 1; i <= 12; i++) {
                    monthValues.add(months.optInt(String.valueOf(i)));
                }

                if (intent.getBooleanExtra(INaturalistService.RESEARCH_GRADE, false)) {
                    mResearchGradeHistogram = monthValues;
                } else {
                    mHistogram = monthValues;
                }

            } else if (intent.getAction().equals(INaturalistService.POPULAR_FIELD_VALUES_RESULT)) {
                SerializableJSONArray array = results.getJSONArray("results");

                if (array == null) {
                    return;
                }

                JSONArray values = array.getJSONArray();
                TreeMap<String, Map<String, List<Integer>>> attributes = new TreeMap<>();

                // Save a list of fields (e.g. flowering, budding, ...) and a list of months for each
                for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i);

                    String attributeName = value.optJSONObject("controlled_attribute").optString("label");
                    attributeName = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, attributeName, false);
                    String valueName = value.optJSONObject("controlled_value").optString("label");

                    if (!attributes.containsKey(attributeName)) {
                        attributes.put(attributeName, new HashMap<String, List<Integer>>());
                    }

                    Map<String, List<Integer>> attribute = attributes.get(attributeName);


                    JSONObject months = value.optJSONObject("month_of_year");
                    List<Integer> monthValues = new ArrayList<>();
                    for (int c = 1; c <= 12; c++) {
                        monthValues.add(months.optInt(String.valueOf(c)));
                    }

                    valueName = AnnotationsAdapter.getAnnotationTranslatedValue(mApp, valueName, true);
                    attribute.put(valueName, monthValues);
                }

                mPopularFieldsByAttributes = attributes;
            }

            refreshSeasonality();

        }
    }

    private void initSeasonabilityCharts() {
        TabLayout.OnTabSelectedListener tabListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (mPopularFieldsByAttributes == null) {
                    return;
                }

                mSeasonabilityViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        };
        mSeasonabilityTabLayout.setOnTabSelectedListener(tabListener);

        ViewPager.OnPageChangeListener pageListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        mSeasonabilityViewPager.addOnPageChangeListener(pageListener);
    }

    private void refreshSeasonality() {
        if (mPopularFieldsByAttributes == null) {
            // Results still loading
            mLoadingSeasonability.setVisibility(View.VISIBLE);
            mSeasonabilityTabLayout.setVisibility(View.GONE);
            mSeasonabilityViewPager.setVisibility(View.GONE);
            return;
        }

        mLoadingSeasonability.setVisibility(View.GONE);
        mSeasonabilityTabLayout.setVisibility(View.VISIBLE);
        mSeasonabilityViewPager.setVisibility(View.VISIBLE);

        if (mSeasonabilityViewPager.getAdapter() == null) {
            mSeasonabilityGraph = new ArrayList<LineChart>(Collections.nCopies(mPopularFieldsByAttributes.size() + 1, (LineChart)null));

            SeasonabilityPagerAdapter adapter = new SeasonabilityPagerAdapter(this);
            mSeasonabilityViewPager.setAdapter(adapter);
            mSeasonabilityTabLayout.setupWithViewPager(mSeasonabilityViewPager);

            // Add the tabs
            addTab(0, getString(R.string.seasonality));

            int i = 1;
            for (String attributeName : mPopularFieldsByAttributes.keySet()) {
                addTab(i, attributeName);
                i++;
            }

            mSeasonabilityViewPager.setOffscreenPageLimit(mPopularFieldsByAttributes.size()); // So we wouldn't have to recreate the views every time
        } else {
            // Just refresh the data
            refreshSeasonalityChart(0);
            int i = 1;
            for (String attributeName : mPopularFieldsByAttributes.keySet()) {
                refreshSeasonalityChart(i);
                i++;
            }
        }
    }

    private void addTab(int position, String tabContent) {
        TabLayout.Tab tab = mSeasonabilityTabLayout.getTabAt(position);
        tab.setText(tabContent);
    }

    private void refreshSeasonalityChart(int position) {
        if (position >= mSeasonabilityGraph.size()) return;

        LineChart graph = mSeasonabilityGraph.get(position);

        if (graph == null) return;

        if (position > 0) {
            // Build the data for the chart
            String attributeName = (String) mPopularFieldsByAttributes.keySet().toArray()[position - 1];

            // Add a line for each possible value of this attribute
            Map<String, List<Integer>> valuesForAttribute = mPopularFieldsByAttributes.get(attributeName);

            List<ILineDataSet> dataSets = new ArrayList<>();
            List<Integer> totalCounts = new ArrayList<>(Collections.nCopies(12, 0));

            int c = 0;
            for (String valueName : valuesForAttribute.keySet()) {
                List<Integer> months = valuesForAttribute.get(valueName);
                List<Entry> valuesData = new ArrayList<>();

                for (int i = 0; i < 12; i++) {
                    valuesData.add(new Entry(i, months.get(i)));
                    totalCounts.set(i, totalCounts.get(i) + months.get(i));
                }

                // Add the line
                LineDataSet lineDataSet = new LineDataSet(valuesData, valueName);
                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
                lineDataSet.setColor(Color.parseColor(ATTRIBUTE_LINE_COLORS[c]));
                lineDataSet.setFillColor(Color.parseColor(ATTRIBUTE_LINE_COLORS[c]));
                lineDataSet.setCircleColor(Color.parseColor(ATTRIBUTE_LINE_COLORS[c]));
                lineDataSet.setCircleHoleColor(Color.parseColor(ATTRIBUTE_LINE_COLORS[c]));
                lineDataSet.setDrawValues(false);
                lineDataSet.setDrawFilled(true);
                lineDataSet.setHighlightEnabled(false);
                lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                dataSets.add(lineDataSet);

                c++;
            }

            if (mHistogram != null) {
                // Add a data set for no annotations
                List<Entry> valuesData = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    int noAnnotations = mHistogram.get(i) - totalCounts.get(i);
                    valuesData.add(new Entry(i, noAnnotations < 0 ? 0 : noAnnotations));
                }
                LineDataSet lineDataSet = new LineDataSet(valuesData, getString(R.string.no_annotation));
                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
                lineDataSet.setColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setFillColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setCircleColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setCircleHoleColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setDrawFilled(true);
                lineDataSet.setDrawValues(false);
                lineDataSet.setHighlightEnabled(false);
                lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                dataSets.add(lineDataSet);
            }

            LineData data = new LineData(dataSets);
            graph.setData(data);

        } else if (position == 0) {
            if ((mHistogram != null) && (mResearchGradeHistogram != null) && (mLoadingHistogram != null)) {
                mLoadingHistogram.setVisibility(View.GONE);
                graph.setVisibility(View.VISIBLE);

                List<ILineDataSet> dataSets = new ArrayList<>();


                // Add verifiable histogram
                List<Entry> valuesData = new ArrayList<>();

                for (int i = 0; i < 12; i++) {
                    valuesData.add(new Entry(i, mHistogram.get(i)));
                }

                // Add the line
                LineDataSet lineDataSet = new LineDataSet(valuesData, getString(R.string.verifiable));
                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
                lineDataSet.setColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setCircleColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setCircleHoleColor(Color.parseColor("#D3D3D3"));
                lineDataSet.setDrawValues(false);
                lineDataSet.setHighlightEnabled(false);
                lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);


                dataSets.add(lineDataSet);

                // Add research grade histogram
                valuesData = new ArrayList<>();

                for (int i = 0; i < 12; i++) {
                    valuesData.add(new Entry(i, mResearchGradeHistogram.get(i)));
                }

                // Add the line
                lineDataSet = new LineDataSet(valuesData, getString(R.string.research_grade));
                lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
                lineDataSet.setFillColor(getResources().getColor(R.color.inatapptheme_color));
                lineDataSet.setDrawFilled(true);
                lineDataSet.setColor(getResources().getColor(R.color.inatapptheme_color));
                lineDataSet.setDrawValues(false);
                lineDataSet.setHighlightEnabled(false);
                lineDataSet.setCircleColor(getResources().getColor(R.color.inatapptheme_color));
                lineDataSet.setCircleHoleColor(getResources().getColor(R.color.inatapptheme_color));
                lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                dataSets.add(lineDataSet);

                LineData data = new LineData(dataSets);
                graph.setData(data);

            } else {
                if (mLoadingHistogram != null) mLoadingHistogram.setVisibility(View.VISIBLE);
                graph.setVisibility(View.GONE);
            }
        }


        // Show X axis as textual months
        XAxis xAxis = graph.getXAxis();
        xAxis.setValueFormatter(new MonthAxisFormatter());
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(1);
        xAxis.setLabelCount(12, true);
        xAxis.setDrawGridLines(false);

        if ((mObservation != null) && (mObservation.getString("observed_on") != null)) {
            // Show vertical line where the date of the observation is

            // Position = observed on date
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date date = null;
            try {
                date = format.parse(mObservation.getString("observed_on"));
            } catch (ParseException e) {
                Logger.tag(TAG).error(e);
            }

            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
                float lastDayMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

                float monthPosition = cal.get(Calendar.MONTH) + (dayOfMonth / lastDayMonth);

                if (monthPosition > 11) monthPosition = 11;

                LimitLine ll = new LimitLine(monthPosition, "");

                ll.setLineColor(mHelper.observationColor(new Observation(mObservation)));
                ll.setLineWidth(1f);

                xAxis.addLimitLine(ll);
            }
        }


        YAxis yAxis = graph.getAxisLeft();
        yAxis.setGranularityEnabled(true);
        yAxis.setGranularity(1);
        yAxis.setAxisMinimum(0);
        yAxis.setDrawGridLines(false);

        yAxis = graph.getAxisRight();
        yAxis.setGranularityEnabled(true);
        yAxis.setGranularity(1);
        yAxis.setAxisMinimum(0);
        yAxis.setDrawGridLines(false);


        graph.getDescription().setEnabled(false);

        graph.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        graph.getLegend().setFormToTextSpace(5);
        graph.getLegend().setTextSize(15);

        graph.animateXY(500, 500);

        // Refresh
        graph.invalidate();

    }

    public class SeasonabilityPagerAdapter extends PagerAdapter {
        private Context mContext;

        public SeasonabilityPagerAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return mPopularFieldsByAttributes.size() + 1;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.seasonality_graph, collection, false);

            LineChart graph = (LineChart) layout.findViewById(R.id.graph);

            if (position > 0) {
                // At this point in time, all popular-value based stats are already loaded
                layout.findViewById(R.id.loading).setVisibility(View.GONE);
            } else {
                mLoadingHistogram = (ProgressBar) layout.findViewById(R.id.loading);
            }

            mSeasonabilityGraph.set(position, graph);

            refreshSeasonalityChart(position);

            collection.addView(layout);

            return layout;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }
        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    public class MonthAxisFormatter implements IAxisValueFormatter {
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            // Show month in localized name (Jan, Feb, etc.) instead of number
            int month = (int) Math.floor(value);

            SimpleDateFormat format = new SimpleDateFormat("MMM");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.MONTH, month);
            String monthName = format.format(cal.getTime());

            return monthName;
        }
    }

    private class TaxonReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, TaxonActivity.this);


            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject taxon;

            if (isSharedOnApp) {
                taxon = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                taxon = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_RESULT);
            }


            if (taxon == null) {
                // Connection error
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
            BaseFragmentActivity.safeUnregisterReceiver(mTaxonBoundsReceiver, TaxonActivity.this);

            BetterJSONObject boundsJson = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_OBSERVATION_BOUNDS_RESULT);

            if (boundsJson == null) {
                // Connection error or no observations yet
                findViewById(R.id.observations_map).setVisibility(View.GONE);
                return;
            }

            findViewById(R.id.observations_map).setVisibility(View.VISIBLE);

            // Set map bounds
            if (mMap != null) {
                try {
                    LatLngBounds bounds = new LatLngBounds(
                            new LatLng(boundsJson.getDouble("swlat"), boundsJson.getDouble("swlng")),
                            new LatLng(boundsJson.getDouble("nelat"), boundsJson.getDouble("nelng")));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
                } catch (Exception exc) {
                    // Happens when the bounds of the taxon is too wide (entire world) - then we get an exception of "Unsupported zoom level: NaN"
                    // In this case, just center on the entire world, with minimal zoom level
                    Logger.tag(TAG).error(exc);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 2f));
                }
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
        Bridge.restoreInstanceState(this, savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra(TAXON);
            mObservation = (BetterJSONObject) intent.getSerializableExtra(OBSERVATION);
            mDownloadTaxon = intent.getBooleanExtra(DOWNLOAD_TAXON, false);
            mTaxonSuggestion = intent.getIntExtra(TAXON_SUGGESTION, TAXON_SUGGESTION_NONE);
            mMapBoundsSet = false;
            mIsTaxonomyListExpanded = false;
        }

        ViewDataBinding binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.taxon_page, null, false);
        setContentView(binding.getRoot());

        mSeasonabilityTabLayout = (TabLayout) findViewById(R.id.seasonability_tabs);
        mSeasonabilityViewPager = (ViewPager) findViewById(R.id.seasonability_view_pager);
        mLoadingSeasonability = (ProgressBar) findViewById(R.id.loading_seasonability);

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

                        String s = String.format(Locale.ENGLISH, INaturalistService.API_HOST + "/grid/%d/%d/%d.png?taxon_id=%d&verifiable=true",
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
                        if (mObservation != null) intent.putExtra(TaxonMapActivity.OBSERVATION, new BetterJSONObject(ObservationUtils.getMinimalObservation(mObservation.getJSONObject())));
                        startActivity(intent);
                    }
                });


            }
        });
        mViewObservations = (ViewGroup) findViewById(R.id.view_observations);
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

                Logger.tag(TAG).debug("On select taxon: " + bundle);
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

        mViewObservations.setOnClickListener(v -> {
            // Show explore screen with filtering by this taxon, global location
            ExploreSearchFilters searchFilters = new ExploreSearchFilters();
            searchFilters.isCurrentLocation = false;
            searchFilters.mapBounds = null;
            searchFilters.place = null;
            searchFilters.taxon = mTaxon.getJSONObject();

            Intent intent1 = new Intent(TaxonActivity.this, ExploreActivity.class);
            intent1.putExtra(ExploreActivity.SEARCH_FILTERS, searchFilters);
            intent1.putExtra(ExploreActivity.ACTIVE_TAB, ExploreActivity.VIEW_TYPE_OBSERVATIONS);
            startActivity(intent1);
        });


        mViewOnINat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                String taxonUrl = String.format(Locale.ENGLISH, "%s/taxa/%d?locale=%s", inatHost, mTaxon.getInt("id"), mApp.getPrefLocale());
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
                }, null, mApp.getStringResourceByName("got_it_all_caps", "got_it"), null);
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

        initSeasonabilityCharts();
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

        if (mApp.getShowScientificNameFirst()) {
            // Show scientific name first, before common name
            TaxonUtils.setTaxonScientificName(mApp, mTaxonName, mTaxon.getJSONObject());
            mTaxonScientificName.setText(TaxonUtils.getTaxonName(this, mTaxon.getJSONObject()));
            getSupportActionBar().setTitle(TaxonUtils.getTaxonScientificName(mApp, mTaxon.getJSONObject()));
        } else {
            TaxonUtils.setTaxonScientificName(mApp, mTaxonScientificName, mTaxon.getJSONObject());
            mTaxonName.setText(TaxonUtils.getTaxonName(this, mTaxon.getJSONObject()));
            getSupportActionBar().setTitle(taxonName);
        }


        String wikiSummary = mTaxon.getString("wikipedia_summary");

        if ((wikiSummary == null) || (wikiSummary.length() == 0)) {
            mWikipediaSummary.setVisibility(View.GONE);
        } else {
            mWikipediaSummary.setVisibility(View.VISIBLE);
            HtmlUtils.fromHtml(mWikipediaSummary, wikiSummary + " " + getString(R.string.source_wikipedia));
            mWikipediaSummary.setTextIsSelectable(true);
        }


        JSONObject conservationStatus = mTaxon.has("conservation_status") ? mTaxon.getJSONObject("conservation_status") : null;
        String conservationStatusName = conservationStatus != null ? conservationStatusCodeToName(conservationStatus.optString("status")) : null;


        mTaxonInactive.setVisibility(mTaxon.getJSONObject().optBoolean("is_active", true) ? View.GONE : View.VISIBLE);

        mTaxonInactive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                String taxonUrl = String.format(Locale.ENGLISH, "%s/taxon_changes?taxon_id=%d&locale=%s", inatHost, mTaxon.getInt("id"), mApp.getPrefLocale());
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
        String taxonName;

        if (mApp.getShowScientificNameFirst()) {
            // Show scientific name instead of common name
            taxonName = TaxonUtils.getTaxonScientificName(mApp, mTaxon.getJSONObject());
        } else {
            taxonName = TaxonUtils.getTaxonName(this, mTaxon.getJSONObject());
        }
        mHelper.selection(taxonName, childrenAdapter);
    }

    @Override
    public void onViewTaxon(BetterJSONObject taxon) {
        Intent intent = new Intent(TaxonActivity.this, TaxonActivity.class);
        intent.putExtra(TaxonActivity.TAXON, taxon);
        intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
        intent.putExtra(TaxonActivity.TAXON_SUGGESTION, mTaxonSuggestion == TAXON_SUGGESTION_COMPARE_AND_SELECT ? TAXON_SUGGESTION_SELECT : mTaxonSuggestion);
        if (mObservation != null) intent.putExtra(TaxonActivity.OBSERVATION, mObservation);
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
        mIsTaxonomyListExpanded = ((TaxonomyAdapter)mTaxonomyList.getAdapter()).isExpanded();

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
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
            INaturalistService.callService(this, serviceIntent);

        }

        if (mDownloadTaxon) {
            // Get the taxon details
            mTaxonReceiver = new TaxonReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_NEW_RESULT);
            Logger.tag(TAG).info("Registering ACTION_GET_TAXON_NEW_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mTaxonReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_NEW, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxon.getInt("id"));
            INaturalistService.callService(this, serviceIntent);
        }

        //
        // Get histogram data
        //

        mHistogramReceiver = new HistogramReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.HISTOGRAM_RESULT);
        filter.addAction(INaturalistService.POPULAR_FIELD_VALUES_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mHistogramReceiver, filter, this);

        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_HISTOGRAM, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxon.getInt("id"));
        INaturalistService.callService(this, serviceIntent);

        serviceIntent = new Intent(INaturalistService.ACTION_GET_HISTOGRAM, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxon.getInt("id"));
        serviceIntent.putExtra(INaturalistService.RESEARCH_GRADE, true);
        INaturalistService.callService(this, serviceIntent);

        serviceIntent = new Intent(INaturalistService.ACTION_GET_POPULAR_FIELD_VALUES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxon.getInt("id"));
        INaturalistService.callService(this, serviceIntent);

        refreshSeasonality();
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
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION, ObservationUtils.getMinimalTaxon(mTaxon, true).toString());
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
