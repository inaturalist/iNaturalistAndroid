package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DataQualityAssessment extends AppCompatActivity implements DataQualityAdapter.OnDataQualityActions {
    private static final String TAG = "DataQualityAssessment";

    public static final String OBSERVATION = "observation";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    private ListView mDataQualityList;
    private ViewGroup mIdCanBeImprovedContainer;
    private ViewGroup mIdCannotBeImprovedContainer;
    private TextView mIdCanBeImprovedText;
    private TextView mIdCannotBeImprovedText;
    private ImageView mIdCanBeImprovedIcon;
    private ImageView mIdCannotBeImprovedIcon;
    private ViewGroup mLoadingIdCanBeImproved;

    private DataQualityMetricsReceiver mDataQualityMetricsReceiver;
    private ChangeDataQualityMetricsReceiver mChangeDataQualityMetricsReceiver;
    private IdCanBeImprovedReceiver mIdCanBeImprovedReceiver;

    @State(AndroidStateBundlers.JSONArrayBundler.class) public JSONArray mMetricsVotes = new JSONArray();
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mObservation;


    @Override
	protected void onStart() {
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop() {
		super.onStop();
		FlurryAgent.onEndSession(this);
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bridge.restoreInstanceState(this, savedInstanceState);


        Intent intent = getIntent();
        if ((savedInstanceState == null) && (intent != null)) {
            mObservation = (BetterJSONObject) intent.getSerializableExtra(OBSERVATION);
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.data_quality_assessment);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.data_quality_assessment);
        mHelper = new ActivityHelper(this);

        mDataQualityList = (ListView) findViewById(R.id.data_quality_list);
        mIdCanBeImprovedContainer = (ViewGroup) findViewById(R.id.id_can_be_improved_container);
        mIdCannotBeImprovedContainer = (ViewGroup) findViewById(R.id.id_cannot_be_improved_container);
        mIdCanBeImprovedText = (TextView) findViewById(R.id.id_can_be_improved_text);
        mIdCannotBeImprovedText = (TextView) findViewById(R.id.id_cannot_be_improved_text);
        mIdCanBeImprovedIcon = (ImageView) findViewById(R.id.id_can_be_improved_icon);
        mIdCannotBeImprovedIcon = (ImageView) findViewById(R.id.id_cannot_be_improved_icon);
        mLoadingIdCanBeImproved = (ViewGroup) findViewById(R.id.loading);

        // Download the data quality votes
        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_DATA_QUALITY_METRICS, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
        ContextCompat.startForegroundService(this, serviceIntent);

        mIdCanBeImprovedContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLoadingIdCanBeImproved.setVisibility(View.VISIBLE);

                Intent serviceIntent;

                if (((Boolean) mIdCanBeImprovedContainer.getTag()) == true) {
                    // Remove vote
                    serviceIntent = new Intent(INaturalistService.ACTION_DELETE_ID_CAN_BE_IMPROVED_VOTE, null, DataQualityAssessment.this, INaturalistService.class);
                } else {
                    // Vote as can be improved
                    serviceIntent = new Intent(INaturalistService.ACTION_ID_CAN_BE_IMPROVED_VOTE, null, DataQualityAssessment.this, INaturalistService.class);
                }

                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
                ContextCompat.startForegroundService(DataQualityAssessment.this, serviceIntent);

            }
        });

        mIdCannotBeImprovedContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLoadingIdCanBeImproved.setVisibility(View.VISIBLE);
                Intent serviceIntent;

                if (((Boolean) mIdCannotBeImprovedContainer.getTag()) == true) {
                    // Remove vote
                    serviceIntent = new Intent(INaturalistService.ACTION_DELETE_ID_CAN_BE_IMPROVED_VOTE, null, DataQualityAssessment.this, INaturalistService.class);
                } else {
                    // Vote as cannot be improved
                    serviceIntent = new Intent(INaturalistService.ACTION_ID_CANNOT_BE_IMPROVED_VOTE, null, DataQualityAssessment.this, INaturalistService.class);
                }

                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
                ContextCompat.startForegroundService(DataQualityAssessment.this, serviceIntent);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMetrics();
        refreshIdCanBeImproved();

        mDataQualityMetricsReceiver = new DataQualityMetricsReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.DATA_QUALITY_METRICS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mDataQualityMetricsReceiver, filter, this);

        mChangeDataQualityMetricsReceiver = new ChangeDataQualityMetricsReceiver();
        IntentFilter filter2 = new IntentFilter(INaturalistService.AGREE_DATA_QUALITY_RESULT);
        filter2.addAction(INaturalistService.DISAGREE_DATA_QUALITY_RESULT);
        filter2.addAction(INaturalistService.DELETE_DATA_QUALITY_VOTE_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mChangeDataQualityMetricsReceiver, filter2, this);

        mIdCanBeImprovedReceiver = new IdCanBeImprovedReceiver();
        IntentFilter filter3 = new IntentFilter(INaturalistService.ID_CAN_BE_IMPROVED_RESULT);
        filter3.addAction(INaturalistService.ID_CANNOT_BE_IMPROVED_RESULT);
        filter3.addAction(INaturalistService.DELETE_ID_CAN_BE_IMPROVED_VOTE_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mIdCanBeImprovedReceiver, filter3, this);
    }



    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mDataQualityMetricsReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mChangeDataQualityMetricsReceiver, this);
    }



    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    public void onDataQualityAgree(String metric) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_DATA_QUALITY, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
        serviceIntent.putExtra(INaturalistService.METRIC, metric);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onDataQualityDisagree(String metric) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_DISAGREE_DATA_QUALITY, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
        serviceIntent.putExtra(INaturalistService.METRIC, metric);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onDataQualityVoteDelete(String metric) {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_DATA_QUALITY_VOTE, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
        serviceIntent.putExtra(INaturalistService.METRIC, metric);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private class DataQualityMetricsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "DataQualityMetricsReceiver");

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject resultsObj;
            if (isSharedOnApp) {
                resultsObj = (BetterJSONObject) mApp.getServiceResult(INaturalistService.DATA_QUALITY_METRICS_RESULT);
            } else {
                resultsObj = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.DATA_QUALITY_METRICS_RESULT);
            }

            if (resultsObj == null) {
                mMetricsVotes = new JSONArray();
                refreshMetrics();
                return;
            }

            mMetricsVotes  = resultsObj.getJSONArray("results").getJSONArray();

            refreshMetrics();
        }

    }

    private void refreshIdCanBeImproved() {
        Log.d(TAG, "refreshIdCanBeImproved");

        mLoadingIdCanBeImproved.setVisibility(View.GONE);

        mIdCanBeImprovedText.setText(R.string.yes);
        mIdCanBeImprovedText.setTypeface(null, Typeface.NORMAL);
        mIdCanBeImprovedIcon.setImageResource(R.drawable.ic_check_box_outline_blank_white_24dp);
        mIdCanBeImprovedContainer.setTag(new Boolean(false));

        mIdCannotBeImprovedText.setText(R.string.no_as_good_as_it_can_be);
        mIdCannotBeImprovedText.setTypeface(null, Typeface.NORMAL);
        mIdCannotBeImprovedIcon.setImageResource(R.drawable.ic_check_box_outline_blank_white_24dp);
        mIdCannotBeImprovedContainer.setTag(new Boolean(false));

        SerializableJSONArray votesContainer = mObservation.getJSONArray("votes");
        JSONArray votes;

        if (votesContainer == null) {
            votes = new JSONArray();
        } else {
            votes = votesContainer.getJSONArray();
        }

        int yesCount = 0;
        int noCount = 0;

        for (int i = 0; i < votes.length(); i++) {
            JSONObject vote = votes.optJSONObject(i);

            if (!vote.optString("vote_scope").equals("needs_id")) {
                continue;
            }

            boolean yes = vote.optBoolean("vote_flag", false);

            if (vote.optJSONObject("user").optString("login").equalsIgnoreCase(mApp.currentUserLogin())) {
                // Current user voted
                (yes ? mIdCanBeImprovedText: mIdCannotBeImprovedText).setTypeface(null, Typeface.BOLD);
                (yes ? mIdCanBeImprovedIcon: mIdCannotBeImprovedIcon).setImageResource(R.drawable.ic_check_box_white_24dp);
                (yes ? mIdCanBeImprovedContainer: mIdCannotBeImprovedContainer).setTag(new Boolean(true));
            }

            if (yes) {
                yesCount++;
            } else {
                noCount++;
            }
        }

        if (yesCount > 0) {
            mIdCanBeImprovedText.setText(String.format("%s (%d)", getString(R.string.yes), yesCount));
        }
        if (noCount > 0) {
            mIdCannotBeImprovedText.setText(String.format("%s (%d)", getString(R.string.no_as_good_as_it_can_be), noCount));
        }

    }


    private void refreshMetrics() {
        Log.d(TAG, "refreshMetrics - " + mMetricsVotes.length());

        List<DataQualityItem> metrics = new ArrayList<>();

        metrics.add(new DataQualityItem(R.drawable.calendar_fa, R.string.date_specified, "date_specified", false));
        metrics.add(new DataQualityItem(R.drawable.map_marker, R.string.location_specified, "location", false));
        metrics.add(new DataQualityItem(R.drawable.file_image_o, R.string.has_photos_or_sounds, "photos", false));
        metrics.add(new DataQualityItem(R.drawable.ic_id, R.string.has_id_supported_by_two_or_more, "id_supported", false));
        metrics.add(new DataQualityItem(R.drawable.calendar_check_o, R.string.date_is_accurate, "date", true));
        metrics.add(new DataQualityItem(R.drawable.bullseye, R.string.location_is_accurate, "location", true));
        metrics.add(new DataQualityItem(R.drawable.arachnida_large, R.string.organism_is_wild, "wild", true));
        metrics.add(new DataQualityItem(R.drawable.magnifying_glass, R.string.evidence_of_organism, "evidence", true));
        metrics.add(new DataQualityItem(R.drawable.clock_o, R.string.recent_evidence_of_an_organism, "recent", true));
        metrics.add(new DataQualityItem(R.drawable.leaf, R.string.community_id_at_species_level_or_lower, "community_id", false));

        for (int i = 0; i < mMetricsVotes.length(); i++) {
            JSONObject vote = mMetricsVotes.optJSONObject(i);

            int index = -1;
            switch (vote.optString("metric")) {
                case "date":
                    index = 4;
                    break;
                case "location":
                    index = 5;
                    break;
                case "wild":
                    index = 6;
                    break;
                case "evidence":
                    index = 7;
                    break;
                case "recent":
                    index = 8;
                    break;
            }


            if (index > -1) {
                if (vote.optBoolean("agree")) {
                    metrics.get(index).agreeCount++;
                } else {
                    metrics.get(index).disagreeCount++;
                }

                if (vote.optJSONObject("user") != null) {
                    if (vote.optJSONObject("user").optString("login").equalsIgnoreCase(mApp.currentUserLogin())) {
                        if (vote.optBoolean("agree")) {
                            metrics.get(index).currentUserAgrees = true;
                        } else {
                            metrics.get(index).currentUserDisagrees = true;
                        }
                    }
                }

            }
        }

        if (mObservation.getString("observed_on") != null) {
            metrics.get(0).agreeCount = 1;
        } else {
            metrics.get(0).disagreeCount = 1;
        }

        if (mObservation.getString("location") != null) {
            metrics.get(1).agreeCount = 1;
        } else {
            metrics.get(1).disagreeCount = 1;
        }

        if ((mObservation.getJSONArray("photos") != null) && (mObservation.getJSONArray("photos").getJSONArray().length() > 0)) {
            metrics.get(2).agreeCount = 1;
        } else {
            metrics.get(2).disagreeCount = 1;
        }

        if ((mObservation.getJSONArray("identifications") != null) && (mObservation.getJSONArray("identifications").getJSONArray().length() >= 2)) {
            metrics.get(3).agreeCount = 1;
        } else {
            metrics.get(3).disagreeCount = 1;
        }

        if ((mObservation.getJSONObject("community_taxon") != null) && (mObservation.getJSONObject("community_taxon").optInt("rank_level") <= 10)) {
            metrics.get(9).agreeCount = 1;
        } else {
            metrics.get(9).disagreeCount = 1;
        }

        mDataQualityList.setAdapter(new DataQualityAdapter(this, this, metrics));

        ActivityHelper.resizeList(mDataQualityList);
    }


    private class ChangeDataQualityMetricsReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "ChangeDataQualityMetricsReceiver");

            // Re-download the new metric votes

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_DATA_QUALITY_METRICS, null, DataQualityAssessment.this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.getInt("id"));
            ContextCompat.startForegroundService(DataQualityAssessment.this, serviceIntent);
	    }

	}

	private class IdCanBeImprovedReceiver extends BroadcastReceiver {

		@Override
	    public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "IdCanBeImprovedReceiver");

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject resultsObj;
            if (isSharedOnApp) {
                resultsObj = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                resultsObj = (BetterJSONObject) intent.getSerializableExtra(intent.getAction());
            }

            // Refresh ID can be improved view
            mObservation = resultsObj;

            refreshIdCanBeImproved();
	    }

	}
}
