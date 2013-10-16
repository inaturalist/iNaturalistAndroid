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

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.LinearGradient;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class CommentsIdsActivity extends ListActivity {
	public static String TAG = "INAT";
	
	private String mLogin;
	
	private ObservationReceiver mObservationReceiver;

    private int mObservationId;
	
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
	        
	        CommentsIdsAdapter adapter = new CommentsIdsAdapter(CommentsIdsActivity.this, results);
	        setListAdapter(adapter);
	        
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
        
        ArrayList<JSONObject> list = new ArrayList<JSONObject>();
        try {
            list.add(new JSONObject("{\"type\": \"identification\", \"body\":\"my remarks\r\nsecond line\",\"created_at\":\"2013-10-13T14:36:15+02:00\",\"current\":true,\"id\":714781,\"observation_id\":216721,\"taxon_change_id\":null,\"taxon_id\":49057,\"updated_at\":\"2013-10-13T14:36:15+02:00\",\"user_id\":13244,\"user\":{\"id\":13244,\"login\":\"budowski\",\"name\":\"\",\"user_icon_url\":\"http://www.inaturalist.org/attachments/users/icons/13244-thumb.jpg?1381663957\"},\"taxon\":{\"iconic_taxon_id\":47158,\"id\":49057,\"name\":\"Megacrania alpheus\",\"rank\":\"species\",\"iconic_taxon_name\":\"Insecta\",\"image_url\":\"http://www.inaturalist.org/images/iconic_taxa/insecta-32px.png\"}}"));
            list.add(new JSONObject("{\"type\": \"comment\", \"body\":\"bl jaksld jkjl adskldj aklklasj dklja dkljaskldj akljd akljd askljd kldasj ksajdfsk\",\"created_at\":\"2013-10-12T17:40:02+02:00\",\"id\":86003,\"parent_id\":216721,\"parent_type\":\"Observation\",\"updated_at\":\"2013-10-12T17:40:02+02:00\",\"user_id\":13244,\"user\":{\"id\":13244,\"login\":\"budowski\",\"name\":\"\",\"user_icon_url\":\"http://www.inaturalist.org/attachments/users/icons/13244-thumb.jpg?1381663957\"}}"));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public class CommentsIdsAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;
        
        public CommentsIdsAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.comment_id_item, objects);
            
            mItems = objects;
            mContext = context;
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
                            } catch (JSONException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            
                            agree.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            return view;
        }
    }
    
    
}
