package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.viewpagerindicator.CirclePageIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import uk.co.senab.photoview.HackyViewPager;

public class CompareSuggestionActivity extends AppCompatActivity {
    private static String TAG = "CompareSuggestionActivity";

    private static final int TAXON_SEARCH_REQUEST_CODE = 0x1000;

    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_JSON = "observation_json";
    public static final String SUGGESTIONS_JSON = "suggestions";
    public static final String SUGGESTION_INDEX = "suggestion_index";

    private static final String SUGGESTION_PHOTO_POSITION = "suggestion_photo_position";
    private static final String OBSERVATION_PHOTO_POSITION = "observation_photo_position";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    private List<BetterJSONObject> mTaxonSuggestions;
    private int mObsIdInternal;
    private int mObsId;
    private BetterJSONObject mObservation;
    private int mSuggestionIndex;
    private int mObservationPhotoPosition;
    private int mSuggestionPhotoPosition;

    private View mBackButton;
    private HackyViewPager mObservationPhotosViewPager;
    private CirclePageIndicator mObservationPhotosIndicator;
    private HackyViewPager mTaxonPhotosViewPager;
    private CirclePageIndicator mTaxonPhotosIndicator;
    private ImageView mNextTaxon;
    private ImageView mPreviousTaxon;
    private View mSelectTaxon;
    private View mEnlargeTaxon;
    private View mAboutTaxon;
    private ViewGroup mTaxonMenu;
    private TextView mTaxonName;
    private ImageView mTaxonNameIcon;
    private ViewGroup mTaxonDetails;

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

        final ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            mObsIdInternal = intent.getIntExtra(OBSERVATION_ID_INTERNAL, -1);
            mObsId = intent.getIntExtra(OBSERVATION_ID, -1);
            String observationJson = intent.getStringExtra(OBSERVATION_JSON);
            if (observationJson != null) {
                mObservation = new BetterJSONObject(observationJson);
            }
            mTaxonSuggestions = loadListFromBundle(intent.getExtras(), SUGGESTIONS_JSON);
            mSuggestionIndex = intent.getIntExtra(SUGGESTION_INDEX, 0);

        } else {
            mObsIdInternal = savedInstanceState.getInt(OBSERVATION_ID_INTERNAL);
            mObsId = savedInstanceState.getInt(OBSERVATION_ID);
            String observationJson = savedInstanceState.getString(OBSERVATION_JSON);
            if (observationJson != null) mObservation = new BetterJSONObject(observationJson);
            mTaxonSuggestions = loadListFromBundle(savedInstanceState, SUGGESTIONS_JSON);
            mSuggestionIndex = savedInstanceState.getInt(SUGGESTION_INDEX);
            mObservationPhotoPosition = savedInstanceState.getInt(OBSERVATION_PHOTO_POSITION);
            mSuggestionPhotoPosition = savedInstanceState.getInt(SUGGESTION_PHOTO_POSITION);
        }

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();

        if (rotation == Surface.ROTATION_90) {
            setContentView(R.layout.compare_suggestions_land_counter_clockwise);
        } else if (rotation == Surface.ROTATION_270) {
            setContentView(R.layout.compare_suggestions_land_clockwise);
        } else {
            setContentView(R.layout.compare_suggestions);
        }

        mBackButton = findViewById(R.id.back);
        mObservationPhotosViewPager = (HackyViewPager) findViewById(R.id.observation_photos);
        mObservationPhotosIndicator = (CirclePageIndicator) findViewById(R.id.observation_photos_indicator);
        mTaxonPhotosViewPager = (HackyViewPager) findViewById(R.id.taxon_photos);
        mTaxonPhotosIndicator = (CirclePageIndicator) findViewById(R.id.taxon_photos_indicator);
        mNextTaxon = (ImageView) findViewById(R.id.next_taxon);
        mPreviousTaxon = (ImageView) findViewById(R.id.previous_taxon);
        mTaxonMenu = (ViewGroup) findViewById(R.id.taxon_menu);
        mTaxonDetails = (ViewGroup) findViewById(R.id.current_taxon_details);
        mSelectTaxon = findViewById(R.id.select_taxon);
        mEnlargeTaxon = findViewById(R.id.enlarge_taxon);
        mAboutTaxon = findViewById(R.id.about_taxon);
        mTaxonName = (TextView) findViewById(R.id.taxon_name);
        mTaxonNameIcon = (ImageView) findViewById(R.id.taxon_name_icon);

        mTaxonMenu.setVisibility(View.GONE);

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        refreshViews();
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshViews();
    }

    private void refreshViews() {
        View.OnClickListener onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show full screen view of the obs photos
                Intent intent = new Intent(CompareSuggestionActivity.this, ObservationPhotosViewer.class);
                intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, mObservationPhotosViewPager.getCurrentItem());

                if (mObsIdInternal > -1) {
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObsId);
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID_INTERNAL, mObsIdInternal);
                    intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, true);
                    intent.putExtra(ObservationPhotosViewer.READ_ONLY, true);
                } else {
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION, mObservation.getJSONObject().toString());
                }
                startActivity(intent);
            }
        };
        ObservationPhotosViewer.IdPicsPagerAdapter adapter;

        if (mObsIdInternal > -1 || mObservation == null) {
            // Internal observation
            adapter = new ObservationPhotosViewer.IdPicsPagerAdapter(this, mObservationPhotosViewPager, mObsId, mObsIdInternal, onClick);
        } else {
            // External observation
            adapter = new ObservationPhotosViewer.IdPicsPagerAdapter(this, mObservationPhotosViewPager, mObservation.getJSONObject(), false, onClick);
        }
        adapter.setOnZoomListener(new ObservationPhotosViewer.IdPicsPagerAdapter.OnZoomListener() {
            @Override
            public void onZoomedIn() {
                mObservationPhotosIndicator.setVisibility(View.GONE);

                Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int rotation = display.getRotation();

                if (rotation != Surface.ROTATION_270) {
                    mBackButton.setVisibility(View.GONE);
                }

                // No padding
                mObservationPhotosViewPager.setPadding(0, 0, 0, 0);
            }

            @Override
            public void onZoomOriginal() {
                mObservationPhotosIndicator.setVisibility(mObservationPhotosViewPager.getAdapter().getCount() <= 1 ? View.GONE : View.VISIBLE);

                Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int rotation = display.getRotation();
                if (rotation != Surface.ROTATION_270) {
                    mBackButton.setVisibility(View.VISIBLE);
                }

                // Restore padding
                int padding = Math.round(16 * (getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
                mObservationPhotosViewPager.setPadding(padding, padding, padding, padding);
            }
        });

        int padding = Math.round(16 * (getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
        mObservationPhotosViewPager.setPadding(padding, padding, padding, padding);

        mObservationPhotosViewPager.setAdapter(adapter);

        mObservationPhotosViewPager.setCurrentItem(mObservationPhotoPosition);
        mObservationPhotosIndicator.setViewPager(mObservationPhotosViewPager);

        if (mObservationPhotosViewPager.getAdapter().getCount() <= 1) {
            mObservationPhotosIndicator.setVisibility(View.GONE);
        } else {
            mObservationPhotosIndicator.setVisibility(View.VISIBLE);
        }

        refreshCurrentTaxon();
    }

    private void refreshCurrentTaxon() {
        final JSONObject taxon = mTaxonSuggestions.get(mSuggestionIndex).getJSONObject().optJSONObject("taxon");

        mTaxonMenu.setVisibility(View.GONE);

        mTaxonMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTaxonMenu.setVisibility(View.GONE);
            }
        });

        View.OnClickListener onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show the taxon menu
                mTaxonMenu.setVisibility(View.VISIBLE);
            }
        };

        final View.OnClickListener moveToPreviousTaxon = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSuggestionIndex == 0) return;

                mSuggestionIndex--;
                mSuggestionPhotoPosition = 0;
                refreshCurrentTaxon();
            }
        };

        final View.OnClickListener moveToNextTaxon = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSuggestionIndex == mTaxonSuggestions.size() - 1) return;

                mSuggestionIndex++;
                mSuggestionPhotoPosition = 0;
                refreshCurrentTaxon();
            }
        };


        ObservationPhotosViewer.IdPicsPagerAdapter adapter = new ObservationPhotosViewer.IdPicsPagerAdapter(this, mTaxonPhotosViewPager, taxon, true, onClick);

        adapter.setOnZoomListener(new ObservationPhotosViewer.IdPicsPagerAdapter.OnZoomListener() {
            @Override
            public void onZoomedIn() {
                mTaxonPhotosIndicator.setVisibility(View.GONE);
                mTaxonDetails.setVisibility(View.GONE);

                Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int rotation = display.getRotation();

                if (rotation == Surface.ROTATION_270) {
                    mBackButton.setVisibility(View.GONE);
                }

                // No padding
                mTaxonPhotosViewPager.setPadding(0, 0, 0, 0);
            }

            @Override
            public void onZoomOriginal() {
                mTaxonPhotosIndicator.setVisibility(mTaxonPhotosViewPager.getAdapter().getCount() <= 1 ? View.GONE : View.VISIBLE);
                Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int rotation = display.getRotation();
                if (rotation == Surface.ROTATION_270) {
                    mBackButton.setVisibility(View.VISIBLE);
                }
                mTaxonDetails.setVisibility(View.VISIBLE);

                // Restore padding
                int padding = Math.round(16 * (getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
                int paddingBottom = Math.round(28 * (getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
                mTaxonPhotosViewPager.setPadding(padding, padding, padding, padding);
            }
        });

        int padding = Math.round(16 * (getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
        mTaxonPhotosViewPager.setPadding(padding, padding, padding, padding);


        mTaxonPhotosViewPager.setAdapter(adapter);

        mTaxonPhotosViewPager.setOnSwipeOutListener(new HackyViewPager.OnSwipeOutListener() {
            @Override
            public void onSwipeOutAtStart() {
                // Trying to swipe to previous taxon suggestion (move to last photo of previous taxon)
                if (mSuggestionIndex == 0) return;

                mSuggestionIndex--;
                mSuggestionPhotoPosition = 0;
                refreshCurrentTaxon();
                mSuggestionPhotoPosition = mTaxonPhotosViewPager.getAdapter().getCount() - 1;
                mTaxonPhotosViewPager.setCurrentItem(mSuggestionPhotoPosition);
            }

            @Override
            public void onSwipeOutAtEnd() {
                // Trying to swipe to next taxon suggestion
                moveToNextTaxon.onClick(null);
            }
        });

        mTaxonPhotosViewPager.setCurrentItem(mSuggestionPhotoPosition);

        mTaxonPhotosIndicator.setViewPager(mTaxonPhotosViewPager);

        if (mTaxonPhotosViewPager.getAdapter().getCount() <= 1) {
            mTaxonPhotosIndicator.setVisibility(View.GONE);
        } else {
            mTaxonPhotosIndicator.setVisibility(View.VISIBLE);
        }

        mTaxonPhotosViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                mSuggestionPhotoPosition = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });


        if (mSuggestionIndex == 0) {
            // No previous taxon
            mPreviousTaxon.setColorFilter(Color.parseColor("#A8C967"));
        } else {
            mPreviousTaxon.setColorFilter(Color.parseColor("#FFFFFF"));
        }


        mPreviousTaxon.setOnClickListener(moveToPreviousTaxon);

        if (mSuggestionIndex == mTaxonSuggestions.size() - 1) {
            // No next taxon
            mNextTaxon.setColorFilter(Color.parseColor("#A8C967"));
        } else {
            mNextTaxon.setColorFilter(Color.parseColor("#FFFFFF"));
        }

        mNextTaxon.setOnClickListener(moveToNextTaxon);


        mSelectTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Taxon selected - return that taxon back
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putString(TaxonSearchActivity.ID_NAME, TaxonUtils.getTaxonName(CompareSuggestionActivity.this, taxon));
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


        mEnlargeTaxon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show full screen view of the taxon photos
                Intent intent = new Intent(CompareSuggestionActivity.this, ObservationPhotosViewer.class);
                intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, mTaxonPhotosViewPager.getCurrentItem());
                intent.putExtra(ObservationPhotosViewer.OBSERVATION, taxon.toString());
                intent.putExtra(ObservationPhotosViewer.IS_TAXON, true);
                startActivity(intent);
            }
        });

        String taxonName = TaxonUtils.getTaxonName(this, taxon);
        String scientificName = taxon.optString("name");
        String htmlText = String.format(getString(R.string.taxon_name), taxonName);

        mTaxonName.setText(Html.fromHtml(htmlText));

        View.OnClickListener showTaxonPage = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show taxon details screen
                Intent intent = new Intent(CompareSuggestionActivity.this, TaxonActivity.class);
                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(taxon));
                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                intent.putExtra(TaxonActivity.TAXON_SUGGESTION, TaxonActivity.TAXON_SUGGESTION_COMPARE_AND_SELECT);
                if (mObservation != null) {
                    intent.putExtra(TaxonActivity.OBSERVATION, mObservation);
                }
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        };

        mTaxonName.setOnClickListener(showTaxonPage);
        mTaxonNameIcon.setOnClickListener(showTaxonPage);
        mAboutTaxon.setOnClickListener(showTaxonPage);
    }

    private void saveListToBundle(Bundle outState, List<BetterJSONObject> list, String key) {
        if (list != null) {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                arr.put(list.get(i).getJSONObject().toString());
            }
            outState.putString(key, arr.toString());
        }
    }

    private ArrayList<BetterJSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();

        String obsString = savedInstanceState.getString(key);
        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(new BetterJSONObject(arr.getString(i)));
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
        outState.putInt(OBSERVATION_ID_INTERNAL, mObsIdInternal);
        outState.putInt(OBSERVATION_ID, mObsId);
        outState.putString(OBSERVATION_JSON, mObservation != null ? mObservation.getJSONObject().toString() : null);
        saveListToBundle(outState, mTaxonSuggestions, SUGGESTIONS_JSON);
        outState.putInt(SUGGESTION_INDEX, mSuggestionIndex);
        mObservationPhotoPosition = mObservationPhotosViewPager.getCurrentItem();
        outState.putInt(OBSERVATION_PHOTO_POSITION, mObservationPhotoPosition);
        mSuggestionPhotoPosition = mTaxonPhotosViewPager.getCurrentItem();
        outState.putInt(SUGGESTION_PHOTO_POSITION, mSuggestionPhotoPosition);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon search directly back to the caller (e.g. suggestions screen)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            } else if (resultCode == TaxonActivity.RESULT_COMPARE_TAXON) {
                // User chose to compare this specific taxon - so basically do nothing (stay with the same taxon view)
            }
        }
    }
}
