package org.inaturalist.shedd.android;

import java.util.ArrayList;
import java.util.List;

import org.inaturalist.shedd.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class GuideDetails extends SherlockActivity {

    private static final String TAG = "GuideDetails";

	private INaturalistApp mApp;
	private BetterJSONObject mGuide;
	public ArrayList<JSONObject> mTaxa;
	private ProgressBar mProgress;
	private GridViewExtended mGuideTaxaGrid;
	private TextView mTaxaEmpty;

	private GuideTaxaReceiver mTaxaGuideReceiver;

	private EditText mSearchText;

	public TaxaListAdapter mAdapter;
	
	private class TaxaListAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;
		private Filter mFilter;
		private ArrayList<JSONObject> mOriginalItems;

        public TaxaListAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.guide_taxon_item, objects);

            mItems = objects;
            mOriginalItems = new ArrayList<JSONObject>(mItems);
            mContext = context;

            mFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        String search = constraint.toString().toLowerCase();
                        ArrayList<JSONObject> results = new ArrayList<JSONObject>(mOriginalItems.size());
                        for (JSONObject item : mOriginalItems) {
                            try {
                                if (item.getString("display_name").toLowerCase().indexOf(search) > -1) {
                                    results.add(item);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } 

                        // Assign the data to the FilterResults
                        filterResults.values = results;
                        filterResults.count = results.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    mItems = (List<JSONObject>) results.values;
                    notifyDataSetChanged();
                }
            };

        }

        @Override
        public Filter getFilter() {
            return mFilter;
        }

        public void addItemAtBeginning(JSONObject newItem) {
            mItems.add(0, newItem);
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public JSONObject getItem(int index) {
            return mItems.get(index);
        }

		@Override
        public View getView(int position, View convertView, ViewGroup parent) { 
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.guide_taxon_item, parent, false); 
            BetterJSONObject item = null;

            item = new BetterJSONObject(mItems.get(position));

            TextView idName = (TextView) view.findViewById(R.id.id_name);
            idName.setText(item.getString("display_name"));

            ImageView taxonPic = (ImageView) view.findViewById(R.id.taxon_pic);
            
            taxonPic.setLayoutParams(new RelativeLayout.LayoutParams(
            		mGuideTaxaGrid.getColumnWidth(),
            		mGuideTaxaGrid.getColumnWidth()
            		));
            
            SerializableJSONArray guidePhotos = item.getJSONArray("guide_photos");
            
            if (guidePhotos.getJSONArray().length() > 0) {
            	JSONObject guidePhoto;
            	try {
            		guidePhoto = guidePhotos.getJSONArray().getJSONObject(0);
            		UrlImageViewHelper.setUrlDrawable(taxonPic, guidePhoto.getJSONObject("photo").getString("small_url"), new UrlImageViewCallback() {
						@Override
						public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url,
								boolean loadedFromCache) {
							imageView.setLayoutParams(new RelativeLayout.LayoutParams(
									mGuideTaxaGrid.getColumnWidth(),
									mGuideTaxaGrid.getColumnWidth()
									));
						}
					});
            	} catch (JSONException e) {
            		e.printStackTrace();
            	}
            }
            
            view.setTag(item);

            return view;
        }
    }
 
	
	private class GuideTaxaReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxaGuideReceiver);
            
            SerializableJSONArray taxaSerializable = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.TAXA_GUIDE_RESULT);
            JSONArray taxa = (taxaSerializable == null ? new SerializableJSONArray() : taxaSerializable).getJSONArray();
            mTaxa = new ArrayList<JSONObject>();
            
            for (int i = 0; i < taxa.length(); i++) {
                try {
                    mTaxa.add(taxa.getJSONObject(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            mProgress.setVisibility(View.GONE);
            
            if (taxa.length() > 0) {
                mTaxaEmpty.setVisibility(View.GONE);
                mGuideTaxaGrid.setVisibility(View.VISIBLE);
                
                mAdapter = new TaxaListAdapter(GuideDetails.this, mTaxa);
                mGuideTaxaGrid.setAdapter(mAdapter);
                mSearchText.setEnabled(true);
                
            } else {
                mTaxaEmpty.setText(R.string.no_check_list);
                mTaxaEmpty.setVisibility(View.VISIBLE);
                mGuideTaxaGrid.setVisibility(View.GONE);
                mSearchText.setEnabled(false);
            }
 
        }
    } 	
 

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.guide_details);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        final Intent intent = getIntent();

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mGuide = (BetterJSONObject) intent.getSerializableExtra("guide");
        } else {
            mGuide = (BetterJSONObject) savedInstanceState.getSerializable("guide");
        }
 
        actionBar.setTitle(mGuide.getString("title"));
        
        
        mTaxaGuideReceiver = new GuideTaxaReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_TAXA_FOR_GUIDES_RESULT);
        Log.i(TAG, "Registering ACTION_TAXA_FOR_GUIDES_RESULT");
        registerReceiver(mTaxaGuideReceiver, filter);
        
        mSearchText = (EditText) findViewById(R.id.search_filter);
        mSearchText.setEnabled(false);
        mSearchText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAdapter != null) mAdapter.getFilter().filter(s);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });
 

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mTaxaEmpty = (TextView) findViewById(R.id.guide_taxa_empty);
        mGuideTaxaGrid = (GridViewExtended) findViewById(R.id.taxa_grid);
        mGuideTaxaGrid.setOnItemClickListener(new OnItemClickListener() {
        	@Override
        	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        		BetterJSONObject taxon = (BetterJSONObject) arg1.getTag();

        		// Show taxon details
        		Intent intent = new Intent(GuideDetails.this, GuideTaxonActivity.class);
        		intent.putExtra("taxon", taxon);
        		startActivity(intent);  

        	}
        });
 
        mProgress.setVisibility(View.VISIBLE);
        mGuideTaxaGrid.setVisibility(View.GONE);
        mTaxaEmpty.setVisibility(View.GONE);
 
        
        // Get the guide's taxa list
        int guideId = mGuide.getInt("id");
        Intent serviceIntent = new Intent(INaturalistService.ACTION_TAXA_FOR_GUIDE, null, GuideDetails.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.ACTION_GUIDE_ID, guideId);
        startService(serviceIntent);
 
 
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        if (mTaxaGuideReceiver != null) {
            Log.i(TAG, "Unregistering TAXA_GUIDE_RESULT");
            try {
                unregisterReceiver(mTaxaGuideReceiver);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
            mTaxaGuideReceiver = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("guide", mGuide);
    }

 
}
