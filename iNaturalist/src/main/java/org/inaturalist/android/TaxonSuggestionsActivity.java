package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.livefront.bridge.Bridge;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class TaxonSuggestionsActivity extends AppCompatActivity {
    private static String TAG = "TaxonSuggestionsActivity";

    public static final String OBS_PHOTO_FILENAME = "obs_photo_filename";
    public static final String OBS_PHOTO_URL = "obs_photo_url";
    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String OBSERVED_ON = "observed_on";
    public static final String OBSERVATION = "observation";
    public static final String OBSERVATION_ID = "observation_id";
    public static final String OBSERVATION_ID_INTERNAL = "observation_id_internal";
    public static final String FROM_SUGGESTION = "from_suggestion";

    private static final int TAXON_SEARCH_REQUEST_CODE = 302;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	@State(AndroidStateBundlers.BetterJSONListBundler.class) public List<BetterJSONObject> mTaxonSuggestions;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mTaxonCommonAncestor;
    private TaxonSuggestionsReceiver mTaxonSuggestionsReceiver;
    @State public String mObsPhotoFilename;
    @State public String mObsPhotoUrl;
    @State public double mLatitude;
    @State public double mLongitude;
    @State public Timestamp mObservedOn;

    private ImageView mObsPhoto;
    private View mBackButton;
    private ViewGroup mSpeciesSearch;
    private TextView mSuggestionsDescription;
    private ListView mSuggestionsList;
    private ProgressBar mLoadingSuggestions;
    private ViewGroup mSuggestionsContainer;
    private TextView mNoNetwork;
    private TextView mCommonAncestorDescription;
    private ListView mCommonAncestorList;
    @State public int mObsId;
    @State public int mObsIdInternal;
    @State public String mObservationJson;
    @State public int mLastTaxonPosition;

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


    private class TaxonSuggestionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxonSuggestionsReceiver);

            BetterJSONObject resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_SUGGESTIONS);

            if ((resultsObject == null) || (!resultsObject.has("results"))) {
                // Connection error
                mNoNetwork.setVisibility(View.VISIBLE);
                mLoadingSuggestions.setVisibility(View.GONE);
                return;
            }

            mTaxonSuggestions = new ArrayList<>();

            JSONArray suggestions = resultsObject.getJSONArray("results").getJSONArray();

            for (int i = 0; i < suggestions.length(); i++) {
                mTaxonSuggestions.add(new BetterJSONObject(suggestions.optJSONObject(i)));
            }

            mTaxonCommonAncestor = null;
            if (resultsObject.has("common_ancestor")) {
                JSONObject commonAncestor = resultsObject.getJSONObject("common_ancestor");
                if ((commonAncestor != null) && (commonAncestor.has("taxon"))) {
                    mTaxonCommonAncestor = new BetterJSONObject(commonAncestor);
                }
            }

            loadSuggestions();
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();

        actionBar.hide();

        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mObsPhotoFilename = intent.getStringExtra(OBS_PHOTO_FILENAME);
            mObsPhotoUrl = intent.getStringExtra(OBS_PHOTO_URL);
            mLongitude = intent.getDoubleExtra(LONGITUDE, 0);
            mLatitude = intent.getDoubleExtra(LATITUDE, 0);
            mObservedOn = (Timestamp) intent.getSerializableExtra(OBSERVED_ON);
            mObsId = intent.getIntExtra(OBSERVATION_ID, 0);
            mObsIdInternal = intent.getIntExtra(OBSERVATION_ID_INTERNAL, -1);
            mObservationJson = intent.getStringExtra(OBSERVATION);
        }

        setContentView(R.layout.taxon_suggestions);

        mObsPhoto = (ImageView) findViewById(R.id.observation_photo);
        mBackButton = findViewById(R.id.back);
        mSpeciesSearch = (ViewGroup) findViewById(R.id.species_search);
        mSuggestionsDescription = (TextView) findViewById(R.id.suggestions_description);
        mSuggestionsList = (ListView) findViewById(R.id.suggestions_list);
        mCommonAncestorDescription = (TextView) findViewById(R.id.common_ancestor_description);
        mCommonAncestorList = (ListView) findViewById(R.id.common_ancestor_list);
        mLoadingSuggestions = (ProgressBar) findViewById(R.id.loading_suggestions);
        mSuggestionsContainer = (ViewGroup) findViewById(R.id.suggestions_container);
        mNoNetwork = (TextView) findViewById(R.id.no_network);

        mNoNetwork.setVisibility(View.GONE);

        mLoadingSuggestions.setVisibility(View.VISIBLE);
        mSuggestionsContainer.setVisibility(View.GONE);

        mSpeciesSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TaxonSuggestionsActivity.this, TaxonSearchActivity.class);
                intent.putExtra(TaxonSearchActivity.SPECIES_GUESS, "");
                intent.putExtra(TaxonSearchActivity.SHOW_UNKNOWN, true);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_ID, mObsId);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_ID_INTERNAL, mObsIdInternal);
                intent.putExtra(TaxonSearchActivity.OBSERVATION_JSON, mObservationJson);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        });

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mTaxonSuggestionsReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mTaxonSuggestions == null) {
            // Get taxon suggestions
            mTaxonSuggestionsReceiver = new TaxonSuggestionsReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_SUGGESTIONS_RESULT);
            BaseFragmentActivity.safeRegisterReceiver(mTaxonSuggestionsReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_SUGGESTIONS, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBS_PHOTO_FILENAME, mObsPhotoFilename);
            serviceIntent.putExtra(INaturalistService.OBS_PHOTO_URL, mObsPhotoUrl);
            serviceIntent.putExtra(INaturalistService.LONGITUDE, mLongitude);
            serviceIntent.putExtra(INaturalistService.LATITUDE, mLatitude);
            serviceIntent.putExtra(INaturalistService.OBSERVED_ON, mObservedOn);
            ContextCompat.startForegroundService(this, serviceIntent);

            mLoadingSuggestions.setVisibility(View.VISIBLE);
            mSuggestionsContainer.setVisibility(View.GONE);
        } else {
            loadSuggestions();
        }


        RequestCreator request;

        if (mObsPhotoFilename == null) {
            // Load online photo
            request = Picasso.with(this).load(mObsPhotoUrl);
        } else {
            // Load offline (local) photo
            request = Picasso.with(this).load(new File(mObsPhotoFilename));
        }

        request
                .fit()
                .centerCrop()
                .into(mObsPhoto, new Callback() {
                    @Override
                    public void onSuccess() {
                    }
                    @Override
                    public void onError() {
                    }
                });


        mObsPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TaxonSuggestionsActivity.this, ObservationPhotosViewer.class);
                intent.putExtra(ObservationPhotosViewer.CURRENT_PHOTO_INDEX, 0);
                intent.putExtra(ObservationPhotosViewer.OBSERVATION, mObservationJson);
                intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID, mObsId);
                intent.putExtra(ObservationPhotosViewer.OBSERVATION_ID_INTERNAL, mObsIdInternal);
                intent.putExtra(ObservationPhotosViewer.READ_ONLY, true);
                intent.putExtra(ObservationPhotosViewer.IS_NEW_OBSERVATION, mObsIdInternal == -1 ? false : true);
                startActivity(intent);
            }
        });

    }

    private void loadSuggestions() {
        mLoadingSuggestions.setVisibility(View.GONE);
        mSuggestionsContainer.setVisibility(View.VISIBLE);

        TaxonSuggestionAdapter.OnTaxonSuggestion onSuggestion = new TaxonSuggestionAdapter.OnTaxonSuggestion() {
            @Override
            public void onTaxonSelected(int position, JSONObject taxon) {
                // Taxon selected - return that taxon back
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putString(TaxonSearchActivity.ID_NAME, TaxonUtils.getTaxonName(TaxonSuggestionsActivity.this, taxon));
                bundle.putString(TaxonSearchActivity.TAXON_NAME, taxon.optString("name"));
                bundle.putString(TaxonSearchActivity.ICONIC_TAXON_NAME, taxon.optString("iconic_taxon_name"));
                if (taxon.has("default_photo") && !taxon.isNull("default_photo")) bundle.putString(TaxonSearchActivity.ID_PIC_URL, taxon.optJSONObject("default_photo").optString("square_url"));
                bundle.putBoolean(TaxonSearchActivity.IS_CUSTOM, false);
                bundle.putInt(TaxonSearchActivity.TAXON_ID, taxon.optInt("id"));
                bundle.putInt(TaxonSearchActivity.RANK_LEVEL, taxon.optInt("rank_level"));
                bundle.putBoolean(TaxonSuggestionsActivity.FROM_SUGGESTION, true);

                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onTaxonDetails(int position, JSONObject taxon) {
                // Show taxon details screen
                mLastTaxonPosition = position;
                Intent intent = new Intent(TaxonSuggestionsActivity.this, TaxonActivity.class);
                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(taxon));
                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                intent.putExtra(TaxonActivity.TAXON_SUGGESTION, TaxonActivity.TAXON_SUGGESTION_COMPARE_AND_SELECT);
                if (mObservationJson != null) {
                    intent.putExtra(TaxonActivity.OBSERVATION, new BetterJSONObject(mObservationJson));
                }
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }

            @Override
            public void onTaxonCompared(int position, JSONObject taxon) {
                // Show taxon comparison screen
                showTaxonComparison(position);
            }
        };

        if (mTaxonCommonAncestor == null) {
            // No common ancestor
            mSuggestionsDescription.setText(String.format(getString(R.string.were_not_confident), mTaxonSuggestions.size()));
            mCommonAncestorDescription.setVisibility(View.GONE);
            mCommonAncestorList.setVisibility(View.GONE);
        } else {
            // Show common ancestor
            mSuggestionsDescription.setText(String.format(getString(R.string.top_species_suggestions), mTaxonSuggestions.size()));
            List<BetterJSONObject> commonAncestor = new ArrayList<>();
            commonAncestor.add(mTaxonCommonAncestor);
            mCommonAncestorList.setAdapter(new TaxonSuggestionAdapter(this, commonAncestor, onSuggestion, false));
            mCommonAncestorDescription.setText(String.format(getString(R.string.pretty_sure_rank), mTaxonCommonAncestor.getJSONObject("taxon").optString("rank")));
            mCommonAncestorDescription.setVisibility(View.VISIBLE);
            mCommonAncestorList.setVisibility(View.VISIBLE);
        }

        mSuggestionsList.setAdapter(new TaxonSuggestionAdapter(this, mTaxonSuggestions, onSuggestion, true));

        resizeSuggestionsList();
    }

    private void showTaxonComparison(int position) {
        if (mTaxonSuggestions == null) return;

        Intent intent = new Intent(TaxonSuggestionsActivity.this, CompareSuggestionActivity.class);
        intent.putExtra(CompareSuggestionActivity.SUGGESTION_INDEX, position);
        if (mObservationJson != null) intent.putExtra(CompareSuggestionActivity.OBSERVATION_JSON, mObservationJson);
        if (mObsIdInternal > -1) intent.putExtra(CompareSuggestionActivity.OBSERVATION_ID_INTERNAL, mObsIdInternal);
        if (mObsId > -1) intent.putExtra(CompareSuggestionActivity.OBSERVATION_ID, mObsId);
        CompareSuggestionActivity.setTaxonSuggestions(mTaxonSuggestions);
        startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon search directly back to the caller (e.g. observation editor)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                bundle.putBoolean(TaxonSuggestionsActivity.FROM_SUGGESTION, false);
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            } else if (resultCode == TaxonActivity.RESULT_COMPARE_TAXON) {
                // User chose to compare this specific taxon
                showTaxonComparison(mLastTaxonPosition);
            }
        }
    }

    private void resizeSuggestionsList() {
        final Handler handler = new Handler();
        if ((mSuggestionsList.getVisibility() == View.VISIBLE) && (mSuggestionsList.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeSuggestionsList();
                }
            }, 100);

            return;
        }

        mSuggestionsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                int height = setListViewHeightBasedOnItems(mSuggestionsList);
            }
        });
    }

    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param listView to be resized
     */
    public int setListViewHeightBasedOnItems(final ListView listView) {
    	ListAdapter listAdapter = listView.getAdapter();
    	if (listAdapter != null) {

            int numberOfItems = listAdapter.getCount();

            // Get total height of all items.
            int totalItemsHeight = 0;
            for (int itemPos = 0; itemPos < numberOfItems; itemPos++) {
                View item = listAdapter.getView(itemPos, null, listView);
                item.measure(MeasureSpec.makeMeasureSpec(listView.getWidth(), MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                totalItemsHeight += item.getMeasuredHeight();
            }

            // Get total height of all item dividers.
            int totalDividersHeight = listView.getDividerHeight() *
                    (numberOfItems - 1);

            // Set list height.
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            int paddingHeight = (int) getResources().getDimension(R.dimen.actionbar_height);
            int newHeight = totalItemsHeight + totalDividersHeight;
            if (params.height != newHeight) {
                params.height = totalItemsHeight + totalDividersHeight;
                listView.setLayoutParams(params);
                listView.requestLayout();
            }

    		return params.height;

    	} else {
    		return 0;
    	}
    }
}

