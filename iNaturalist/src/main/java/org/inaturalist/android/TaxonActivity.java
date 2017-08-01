package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.viewpagerindicator.CirclePageIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import uk.co.senab.photoview.HackyViewPager;

public class TaxonActivity extends AppCompatActivity {
    private static String TAG = "TaxonActivity";

    public static String TAXON = "taxon";
    public static String DOWNLOAD_TAXON = "download_taxon";

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private BetterJSONObject mTaxon;
    private TaxonReceiver mTaxonReceiver;
    private boolean mDownloadTaxon;

    private ViewGroup mPhotosContainer;
    private HackyViewPager mPhotosViewPager;
    private CirclePageIndicator mPhotosIndicator;
    private TextView mTaxonName;
    private TextView mTaxonScientificName;
    private TextView mWikipediaSummary;
    private ViewGroup mConservationStatusContainer;
    private TextView mConservationStatus;
    private TextView mConservationSource;

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
                return;
            }

            mTaxon = taxon;
            loadTaxon();
        }
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
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra(TAXON);
            mDownloadTaxon = intent.getBooleanExtra(DOWNLOAD_TAXON, false);
        } else {
        	mTaxon = (BetterJSONObject) savedInstanceState.getSerializable(TAXON);
            mDownloadTaxon = savedInstanceState.getBoolean(DOWNLOAD_TAXON);
        }

        setContentView(R.layout.taxon_page);

        mPhotosContainer = (ViewGroup) findViewById(R.id.taxon_photos_container);
        mPhotosViewPager = (HackyViewPager) findViewById(R.id.taxon_photos);
        mPhotosIndicator = (CirclePageIndicator) findViewById(R.id.photos_indicator);
        mTaxonName = (TextView) findViewById(R.id.taxon_name);
        mTaxonScientificName = (TextView) findViewById(R.id.taxon_scientific_name);
        mWikipediaSummary = (TextView) findViewById(R.id.wikipedia_summary);
        mConservationStatusContainer = (ViewGroup) findViewById(R.id.conservation_status_container);
        mConservationStatus = (TextView) findViewById(R.id.conservation_status);
        mConservationSource = (TextView) findViewById(R.id.conservation_source);

        mPhotosViewPager.setAdapter(new TaxonPhotosPagerAdapter(this, mTaxon.getJSONObject()));
        mPhotosIndicator.setViewPager(mPhotosViewPager);

        if (mPhotosViewPager.getAdapter().getCount() <= 1) {
            mPhotosIndicator.setVisibility(View.GONE);
        } else {
            mPhotosIndicator.setVisibility(View.VISIBLE);
        }

        actionBar.setHomeButtonEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (!mDownloadTaxon) {
            loadTaxon();
        }

    }

    private void loadTaxon() {
        if (mTaxon == null) {
            finish();
            return;
        }

        String taxonName = TaxonUtils.getTaxonName(this, mTaxon.getJSONObject());
        getSupportActionBar().setTitle(taxonName);

        mTaxonName.setText(taxonName);
        mTaxonScientificName.setText(mTaxon.getString("name"));


        String wikiSummary = mTaxon.getString("wikipedia_summary");

        if ((wikiSummary == null) || (wikiSummary.length() == 0)) {
            mWikipediaSummary.setVisibility(View.GONE);
        } else {
            mWikipediaSummary.setVisibility(View.VISIBLE);
            mWikipediaSummary.setText(Html.fromHtml(wikiSummary + " " + getString(R.string.source_wikipedia)));
        }

        String conservationStatus = mTaxon.getString("conservation_status_name");

        if ((conservationStatus == null) || (conservationStatus.length() == 0) ||
                (conservationStatus.equals("not_evaluated")) || (conservationStatus.equals("data_deficient"))) {
            mConservationStatusContainer.setVisibility(View.GONE);
        } else {
            mConservationStatusContainer.setVisibility(View.VISIBLE);

            int textColor = mApp.getColorResourceByName("conservation_" + conservationStatus + "_text");
            int backgroundColor = mApp.getColorResourceByName("conservation_" + conservationStatus + "_bg");

            mConservationStatus.setText(mApp.getStringResourceByName("conservation_status_" + conservationStatus));
            mConservationStatusContainer.setBackgroundColor(backgroundColor);
            mConservationStatus.setTextColor(textColor);
            mConservationSource.setTextColor(textColor);
            mConservationSource.setText(Html.fromHtml(String.format(getString(R.string.conservation_source), "Some source")));
            Drawable drawable = getResources().getDrawable(R.drawable.ic_open_in_browser_black_24dp);
            drawable.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            mConservationSource.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
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
        outState.putBoolean(DOWNLOAD_TAXON, mDownloadTaxon);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mTaxonReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mDownloadTaxon) {
            // TODO
            /*
            // Get the taxon details
            mTaxonReceiver = new TaxonReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_RESULT);
            Log.i(TAG, "Registering ACTION_GET_TAXON_RESULT");
            BaseFragmentActivity.safeRegisterReceiver(mTaxonReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TAXON_ID, mTaxonId != null ? mTaxonId : mTaxon.getInt("id"));
            startService(serviceIntent);
            */
        }
    }


    class TaxonPhotosPagerAdapter extends PagerAdapter {
 		private int mDefaultTaxonIcon;
 		private List<JSONObject> mTaxonPhotos;
        private Context mContext;


        // Load offline photos for a new observation
        public TaxonPhotosPagerAdapter(Context context, JSONObject taxon) {
            mContext = context;
            mTaxonPhotos = new ArrayList<>();

            mDefaultTaxonIcon = TaxonUtils.observationIcon(taxon);

            JSONArray taxonPhotos = taxon.optJSONArray("taxon_photos");

            for (int i = 0; i < taxonPhotos.length(); i++) {
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
 			View layout = getLayoutInflater().inflate(R.layout.taxon_photo, null, false);
 			container.addView(layout, RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);

 			final ImageView taxonPhoto = (ImageView) layout.findViewById(R.id.taxon_photo);
 			final ProgressBar loading = (ProgressBar) layout.findViewById(R.id.loading_photo);
            TextView photosAttr = (TextView) layout.findViewById(R.id.photo_attr);

            loading.setVisibility(View.VISIBLE);
            taxonPhoto.setVisibility(View.INVISIBLE);

            JSONObject taxonPhotoJSON = mTaxonPhotos.get(position);
            JSONObject innerPhotoJSON = taxonPhotoJSON.optJSONObject("photo");
            String photoUrl = (innerPhotoJSON.has("medium_url") && !innerPhotoJSON.isNull("medium_url")) ?
                    innerPhotoJSON.optString("medium_url") : innerPhotoJSON.optString("large_url");

            Picasso.with(mContext)
                    .load(photoUrl)
                    .fit()
                    .centerCrop()
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
                    intent.putExtra(ObservationPhotosViewer.OBSERVATION, mTaxon.getJSONObject().toString());
                    intent.putExtra(ObservationPhotosViewer.IS_TAXON, true);
                    startActivity(intent);
                }
            });

            photosAttr.setText(Html.fromHtml(String.format(getString(R.string.photo_attr), innerPhotoJSON.optString("attribution"))));

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
}
