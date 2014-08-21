package org.inaturalist.shedd.android;

import java.net.IDN;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.inaturalist.shedd.android.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class CommentsIdsActivity extends SherlockListActivity {
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
	        mAdapter = new CommentsIdsAdapter(CommentsIdsActivity.this, results);
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
            mAdapter = new CommentsIdsAdapter(CommentsIdsActivity.this, mCommentsIds);
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
    }
    
    private void setResult() {
    	Intent intent = new Intent();
    	Bundle bundle = new Bundle();
    	bundle.putInt(NEW_COMMENTS, mNewComments);
    	bundle.putInt(NEW_IDS, mNewIds);
    	bundle.putInt(TAXON_ID, mTaxonId);
    	if (mIconicTaxonName != null) bundle.putString(ICONIC_TAXON_NAME, mIconicTaxonName);
    	if (mSpeciesGuess != null) bundle.putString(SPECIES_GUESS, mSpeciesGuess);
    	
    	intent.putExtras(bundle);

    	setResult(RESULT_OK, intent);
    }
    
    public class CommentsIdsAdapter extends ArrayAdapter<BetterJSONObject> {

        private List<BetterJSONObject> mItems;
        private Context mContext;
        private ArrayList<Boolean> mAgreeing;
        
        public boolean isEnabled(int position) { 
            return false; 
        }  
        
        public CommentsIdsAdapter(Context context, List<BetterJSONObject> objects) {
            super(context, R.layout.comment_id_item, objects);
            
            mItems = objects;
            mAgreeing = new ArrayList<Boolean>();
            while (mAgreeing.size() < mItems.size()) mAgreeing.add(false);
            mContext = context;
        }
        
        public void addItemAtBeginning(BetterJSONObject newItem) {
            mItems.add(0, newItem);
        }
        
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) { 
            Resources res = getResources();
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.comment_id_item, parent, false); 
            final BetterJSONObject item = mItems.get(position);
            
            try {
                TextView comment = (TextView) view.findViewById(R.id.comment);
                RelativeLayout idLayout = (RelativeLayout) view.findViewById(R.id.id_layout);
                
                TextView postedOn = (TextView) view.findViewById(R.id.posted_on);
                String username = item.getJSONObject("user").getString("login");
                Timestamp postDate = item.getTimestamp("updated_at");
                SimpleDateFormat format = new SimpleDateFormat("LLL d, yyyy");
                postedOn.setText(String.format(res.getString(R.string.posted_by),
                        username.equalsIgnoreCase(mLogin) ? res.getString(R.string.you) : username,
                        format.format(postDate)));
                
                ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
                UrlImageViewHelper.setUrlDrawable(userPic, item.getJSONObject("user").getString("user_icon_url"));
                
                if (item.getString("type").equals("comment")) {
                    // Comment
                    comment.setVisibility(View.VISIBLE);
                    idLayout.setVisibility(View.GONE);
                    
                    comment.setText(Html.fromHtml(item.getString("body")));
                    comment.setMovementMethod(LinkMovementMethod.getInstance()); 
                    
                    postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
                    userPic.setAlpha(255);
                    
                } else {
                    // Identification
                    idLayout.setVisibility(View.VISIBLE);
                    String body = item.getString("body");
                    if (body != null && body.length() > 0) {
                        comment.setText(Html.fromHtml(body));
                        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)comment.getLayoutParams();
                        layoutParams.setMargins(
                                layoutParams.leftMargin, 
                                layoutParams.topMargin + 25, 
                                layoutParams.rightMargin, 
                                layoutParams.bottomMargin);
                        comment.setLayoutParams(layoutParams);
                    } else {
                        comment.setVisibility(View.GONE);
                    }
                    ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
                    UrlImageViewHelper.setUrlDrawable(idPic, item.getJSONObject("taxon").getString("image_url"));
                    TextView idName = (TextView) view.findViewById(R.id.id_name);
                    if (!item.getJSONObject("taxon").isNull("common_name")) {
                    	idName.setText(item.getJSONObject("taxon").getJSONObject("common_name").getString("name"));
                    } else {
                    	idName.setText(item.getJSONObject("taxon").getString("name"));
                    }
                    TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);
                    idTaxonName.setText(item.getJSONObject("taxon").getString("name"));
                    idTaxonName.setTypeface(null, Typeface.ITALIC);
                    
                    Boolean isCurrent = item.getBoolean("current");
                    if ((isCurrent == null) || (!isCurrent)) {
                        // An outdated identification - show as faded-out
                        idName.setTextColor(idName.getTextColors().withAlpha(100));
                        idTaxonName.setTextColor(idTaxonName.getTextColors().withAlpha(100));
                        postedOn.setTextColor(postedOn.getTextColors().withAlpha(100));
                        idPic.setAlpha(100);
                        userPic.setAlpha(100);
                    } else {
                        idName.setTextColor(idName.getTextColors().withAlpha(255));
                        idTaxonName.setTextColor(idTaxonName.getTextColors().withAlpha(255));
                        postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
                        idPic.setAlpha(255);
                        userPic.setAlpha(255);
                    }
                    
                    final Button agree = (Button) view.findViewById(R.id.id_agree);
                    final ProgressBar loading = (ProgressBar) view.findViewById(R.id.loading);
                    agree.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
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
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            
                            agree.setVisibility(View.GONE);
                            loading.setVisibility(View.VISIBLE);
                            mAgreeing.set(position, true);
                        }
                    });
                    
                    if ((mAgreeing.get(position) != null) && (mAgreeing.get(position) == true)) {
                    	agree.setVisibility(View.GONE);
                    	loading.setVisibility(View.VISIBLE);
                    } else {
                    	agree.setVisibility(View.VISIBLE);
                    	loading.setVisibility(View.GONE);
                    }
                    
                    if ((username.equalsIgnoreCase(mLogin)) || (mTaxonId == item.getInt("taxon_id").intValue())) {
                        // Can't agree on our on identification or when the identification is the current one
                        agree.setVisibility(View.GONE);
                        loading.setVisibility(View.GONE);
                    }
                }
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            return view;
        }
    }
    
    
}
