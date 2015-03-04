package org.inaturalist.android;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONException;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class CommentsIdsActivity extends SherlockListActivity implements CommentsIdsAdapter.OnIDAdded {
    public static final String NEW_COMMENTS = "new_comments";
    public static final String NEW_IDS = "new_ids";
    public static final String TAXON_ID = "taxon_id";
    public static final String SPECIES_GUESS = "species_guess";
    public static final String ICONIC_TAXON_NAME = "iconic_taxon_name";

    protected static final int NEW_ID_REQUEST_CODE = 202;

    public static String TAG = "INAT";
	
	private String mLogin;
	
	private ObservationReceiver mObservationReceiver;

    private int mObservationId;
    
    private CommentsIdsAdapter mAdapter;

    private int mNewComments = 0;
    private int mNewIds = 0;
    private int mTaxonId;
    private String mIconicTaxonName;
    private String mSpeciesGuess;


    private Button mAddComment;
    private Button mAddId;
	
    private ArrayList<BetterJSONObject> mCommentsIds;
    private ProgressBar mProgress;
    private TextView mNoComments;
    
	@Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());

	}


    
    @Override
    protected void onPause() {
        super.onPause();

        if (mObservationReceiver != null) {
            try {
                unregisterReceiver(mObservationReceiver);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }
    }
	    
   
	private class ObservationReceiver extends BroadcastReceiver {

        @Override
	    public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mObservationReceiver);  

	        Observation observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);
	        
	        if (observation == null) {
	            // Couldn't retrieve observation details (probably deleted)
	            mNoComments.setText(R.string.could_not_load_comments);
	            mCommentsIds = new ArrayList<BetterJSONObject>();
	            loadResultsIntoUI();
	            View bottomBar = findViewById(R.id.bottom_bar);
	            bottomBar.setVisibility(View.GONE);
	            return;
	        } else {
	            mAddComment.setEnabled(true);
	            mAddId.setEnabled(true);
	        }
	        
	        JSONArray comments = observation.comments.getJSONArray();
	        JSONArray ids = observation.identifications.getJSONArray();
	        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();
	        
	        try {
	            for (int i = 0; i < comments.length(); i++) {
	                BetterJSONObject comment = new BetterJSONObject(comments.getJSONObject(i));
	                comment.put("type", "comment");
	                results.add(comment);
	            }
	            for (int i = 0; i < ids.length(); i++) {
	                BetterJSONObject id = new BetterJSONObject(ids.getJSONObject(i));
	                id.put("type", "identification");
	                results.add(id);
	            }
	        } catch (JSONException e) {
	            e.printStackTrace();
	        }
	        
	        Collections.sort(results, new Comparator<BetterJSONObject>() {
                @Override
                public int compare(BetterJSONObject lhs, BetterJSONObject rhs) {
                    Timestamp date1 = lhs.getTimestamp("created_at");
                    Timestamp date2 = rhs.getTimestamp("created_at");
                    
                    return date1.compareTo(date2);
                }
            });
	        
	        mCommentsIds = results;
            mAdapter = new CommentsIdsAdapter(CommentsIdsActivity.this, results, mTaxonId, CommentsIdsActivity.this);
	        setListAdapter(mAdapter);
	        
	        loadResultsIntoUI();

	    }
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    // Respond to the action bar's Up/Home button
	    case android.R.id.home:
	        onBackPressed();
	        return true;
	    }
	    return super.onOptionsItemSelected(item);
	} 

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mCommentsIds", mCommentsIds);
    }

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#767676")));
        actionBar.setIcon(android.R.color.transparent);

        
        setContentView(R.layout.comments_ids_list);
        
        mNoComments = (TextView) findViewById(android.R.id.empty);
        mProgress = (ProgressBar) findViewById(R.id.progress);
        
        mObservationId = getIntent().getIntExtra(INaturalistService.OBSERVATION_ID, 0);
        mTaxonId = getIntent().getIntExtra(INaturalistService.TAXON_ID, 0);
        
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        mLogin = prefs.getString("username", null);
        
        mObservationReceiver = new ObservationReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
        Log.i(TAG, "Registering ACTION_OBSERVATION_RESULT");
        registerReceiver(mObservationReceiver, filter);  
        
        mAddComment = (Button) findViewById(R.id.add_comment);
        mAddComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputDialog();
            }
        });
        
        mAddId = (Button) findViewById(R.id.add_id);
        mAddId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CommentsIdsActivity.this, IdentificationActivity.class);
                startActivityForResult(intent, NEW_ID_REQUEST_CODE);
            }
        });
        
       
        if (savedInstanceState != null) {
            mCommentsIds = (ArrayList<BetterJSONObject>) savedInstanceState.getSerializable("mCommentsIds");
            mAdapter = new CommentsIdsAdapter(CommentsIdsActivity.this, mCommentsIds, mTaxonId, this);
            setListAdapter(mAdapter);
        }
 
        loadResultsIntoUI();
    }
    
    private void loadResultsIntoUI() {
        if (mCommentsIds == null) {
            mProgress.setVisibility(View.VISIBLE);
            getListView().setVisibility(View.GONE);
            mNoComments.setVisibility(View.GONE);
            
            mAddComment.setEnabled(false);
            mAddId.setEnabled(false);
            
        }  else if (mCommentsIds.size() == 0) {
            mProgress.setVisibility(View.GONE);
            getListView().setVisibility(View.GONE);
            mNoComments.setVisibility(View.VISIBLE);
            
            mAddComment.setEnabled(true);
            mAddId.setEnabled(true);
        } else {
            mProgress.setVisibility(View.GONE);
            getListView().setVisibility(View.VISIBLE);
            mNoComments.setVisibility(View.GONE);
            
            mAddComment.setEnabled(true);
            mAddId.setEnabled(true);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NEW_ID_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Add the ID
                Integer taxonId = data.getIntExtra(IdentificationActivity.TAXON_ID, 0);
                String idRemarks = data.getStringExtra(IdentificationActivity.ID_REMARKS);
                
                mTaxonId = taxonId;
                mIconicTaxonName = data.getStringExtra(IdentificationActivity.ICONIC_TAXON_NAME);
                mSpeciesGuess = data.getStringExtra(IdentificationActivity.SPECIES_GUESS);
            
                Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_IDENTIFICATION, null, CommentsIdsActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
                serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_BODY, idRemarks);
                startService(serviceIntent);
                
                
                // Show a loading progress until the new comments/IDs are loaded
	            mCommentsIds = null;
	            loadResultsIntoUI();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // Refresh the comment/id list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                registerReceiver(mObservationReceiver, filter);  
                Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, CommentsIdsActivity.this, INaturalistService.class);
                serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
                startService(serviceIntent2);

                // Ask for a sync (to update the id count)
                Intent serviceIntent3 = new Intent(INaturalistService.ACTION_SYNC, null, CommentsIdsActivity.this, INaturalistService.class);
                startService(serviceIntent3);

                mNewIds++;

            }
        }
    }
        
    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_comment);

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() { 
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String comment = input.getText().toString();
                
                // Add the comment
                Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_COMMENT, null, CommentsIdsActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
                serviceIntent.putExtra(INaturalistService.COMMENT_BODY, comment);
                startService(serviceIntent);
                
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                
                // Refresh the comment/id list
                IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                registerReceiver(mObservationReceiver, filter);  
                Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, CommentsIdsActivity.this, INaturalistService.class);
                serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
                startService(serviceIntent2);
                
                // Ask for a sync (to update the comment count)
                Intent serviceIntent3 = new Intent(INaturalistService.ACTION_SYNC, null, CommentsIdsActivity.this, INaturalistService.class);
                startService(serviceIntent3);
                
                mNewComments++;
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();        
    }
    
    @Override
    public void onBackPressed() {
    	setResult();
       
        super.onBackPressed();
    }
    
    @Override
    public void onStop() {
    	setResult();

        super.onStop();
		FlurryAgent.onEndSession(this);
    }
    
    private void setResult() {
    	Intent intent = new Intent();
    	Bundle bundle = new Bundle();
    	bundle.putInt(NEW_COMMENTS, mNewComments);
    	bundle.putInt(NEW_IDS, mNewIds);
    	bundle.putInt(TAXON_ID, mTaxonId);
    	bundle.putInt(INaturalistService.OBSERVATION_ID, mObservationId);
    	if (mIconicTaxonName != null) bundle.putString(ICONIC_TAXON_NAME, mIconicTaxonName);
    	if (mSpeciesGuess != null) bundle.putString(SPECIES_GUESS, mSpeciesGuess);
    	
    	intent.putExtras(bundle);

    	setResult(RESULT_OK, intent);
    }

	@Override
	public void onIdentificationRemoved(BetterJSONObject taxon) {
		// After calling the remove API - we'll refresh the comment/ID list
		IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
		registerReceiver(mObservationReceiver, filter);

		Intent serviceIntent = new Intent(INaturalistService.ACTION_REMOVE_ID, null, CommentsIdsActivity.this, INaturalistService.class);
		serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
		serviceIntent.putExtra(INaturalistService.IDENTIFICATION_ID, taxon.getInt("id"));
		startService(serviceIntent);

		mNewIds--;
	} 	
	

	@Override
	public void onIdentificationAdded(BetterJSONObject item) {

		try {
			// After calling the agree API - we'll refresh the comment/ID list
			IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
			registerReceiver(mObservationReceiver, filter);

			Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_ID, null, CommentsIdsActivity.this, INaturalistService.class);
			serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
			serviceIntent.putExtra(INaturalistService.TAXON_ID, item.getJSONObject("taxon").getInt("id"));
			startService(serviceIntent);

			mNewIds++;

			mTaxonId = item.getInt("taxon_id");
			mIconicTaxonName = item.getJSONObject("taxon").getString("iconic_taxon_name");
			mSpeciesGuess = item.getJSONObject("taxon").getJSONObject("common_name").getString("name");

		} catch (JSONException e) {
			e.printStackTrace();
		}

	}


}
