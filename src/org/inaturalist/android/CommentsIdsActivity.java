package org.inaturalist.android;

import java.net.IDN;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.graphics.LinearGradient;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class CommentsIdsActivity extends ListActivity {
    public static final String NEW_COMMENTS = "new_comments";
    public static final String NEW_IDS = "new_ids";
    protected static final int NEW_ID_REQUEST_CODE = 202;

    public static String TAG = "INAT";
	
	private String mLogin;
	
	private ObservationReceiver mObservationReceiver;

    private int mObservationId;
    
    private CommentsIdsAdapter mAdapter;

    private int mNewComments = 0;
    private int mNewIds = 0;
	
	private class ObservationReceiver extends BroadcastReceiver {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        Observation observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);
	        
	        JSONArray comments = observation.comments.getJSONArray();
	        JSONArray ids = observation.identifications.getJSONArray();
	        ArrayList<JSONObject> results = new ArrayList<JSONObject>();
	        
	        try {
	            for (int i = 0; i < comments.length(); i++) {
	                JSONObject comment = comments.getJSONObject(i);
	                comment.put("type", "comment");
	                results.add(comment);
	            }
	            for (int i = 0; i < ids.length(); i++) {
	                JSONObject id = ids.getJSONObject(i);
	                id.put("type", "identification");
	                results.add(id);
	            }
	        } catch (JSONException e) {
	            e.printStackTrace();
	        }
	        
	        Collections.sort(results, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject lhs, JSONObject rhs) {
                    BetterJSONObject o1 = new BetterJSONObject(lhs);
                    BetterJSONObject o2 = new BetterJSONObject(rhs);
                    Timestamp date1 = o1.getTimestamp("updated_at");
                    Timestamp date2 = o2.getTimestamp("updated_at");
                    
                    return date2.compareTo(date1);
                }
            });
	        
	        mAdapter = new CommentsIdsAdapter(CommentsIdsActivity.this, results);
	        setListAdapter(mAdapter);
	        
	        TextView loadingComments = (TextView) findViewById(android.R.id.empty);
	        loadingComments.setText(R.string.no_comments);
	    }
	} 	

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.comments_ids_list);
        
        TextView loadingComments = (TextView) findViewById(android.R.id.empty);
        loadingComments.setText(R.string.loading_comments);
        
        
        mObservationId = getIntent().getIntExtra(INaturalistService.OBSERVATION_ID, 0);
        
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        mLogin = prefs.getString("username", null);
        
        mObservationReceiver = new ObservationReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
        Log.i(TAG, "Registering ACTION_OBSERVATION_RESULT");
        registerReceiver(mObservationReceiver, filter);  
        
        Button addComment = (Button) findViewById(R.id.add_comment);
        addComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputDialog();
            }
        });
        
        Button addId = (Button) findViewById(R.id.add_id);
        addId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CommentsIdsActivity.this, IdentificationActivity.class);
                startActivityForResult(intent, NEW_ID_REQUEST_CODE);
            }
        });
 
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NEW_ID_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Add the ID
                Integer taxonId = data.getIntExtra(IdentificationActivity.TAXON_ID, 0);
                String idRemarks = data.getStringExtra(IdentificationActivity.ID_REMARKS);
            
                Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_IDENTIFICATION, null, CommentsIdsActivity.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
                serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
                serviceIntent.putExtra(INaturalistService.IDENTIFICATION_BODY, idRemarks);
                startService(serviceIntent);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // Refresh the comment/id list
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
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
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
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putInt(NEW_COMMENTS, mNewComments);
        bundle.putInt(NEW_IDS, mNewIds);
        intent.putExtras(bundle);
        
        setResult(RESULT_OK, intent);
        
        super.onBackPressed();
    }
    
    @Override
    public void onStop() {
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putInt(NEW_COMMENTS, mNewComments);
        bundle.putInt(NEW_IDS, mNewIds);
        intent.putExtras(bundle);
        
        setResult(RESULT_OK, intent);
        super.onStop();
    }
    
    public class CommentsIdsAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;
        
        public CommentsIdsAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.comment_id_item, objects);
            
            mItems = objects;
            mContext = context;
        }
        
        public void addItemAtBeginning(JSONObject newItem) {
            mItems.add(0, newItem);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) { 
            Resources res = getResources();
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.comment_id_item, parent, false); 
            final BetterJSONObject item = new BetterJSONObject(mItems.get(position));
            
            try {
                TextView comment = (TextView) view.findViewById(R.id.comment);
                LinearLayout idLayout = (LinearLayout) view.findViewById(R.id.id_layout);
                
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
                    
                    comment.setText(item.getString("body"));
                    
                } else {
                    // Identification
                    comment.setVisibility(View.GONE);
                    idLayout.setVisibility(View.VISIBLE);
                    
                    ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
                    UrlImageViewHelper.setUrlDrawable(idPic, item.getJSONObject("taxon").getString("image_url"));
                    TextView idName = (TextView) view.findViewById(R.id.id_name);
                    idName.setText(item.getJSONObject("taxon").getString("name"));
                    TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);
                    idTaxonName.setText(item.getJSONObject("taxon").getString("iconic_taxon_name"));
                    
                    final Button agree = (Button) view.findViewById(R.id.id_agree);
                    agree.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_ID, null, CommentsIdsActivity.this, INaturalistService.class);
                                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservationId);
                                serviceIntent.putExtra(INaturalistService.TAXON_ID, item.getJSONObject("taxon").getInt("id"));
                                startService(serviceIntent);
                                
                                mNewIds++;
                            } catch (JSONException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            
                            agree.setVisibility(View.INVISIBLE);
                        }
                    });
                    
                    if (username.equalsIgnoreCase(mLogin)) {
                        // Can't agree on our on identification
                        agree.setVisibility(View.INVISIBLE);
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
