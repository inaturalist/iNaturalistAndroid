package org.inaturalist.android;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ObservationDetails extends SherlockActivity implements CommentsIdsAdapter.OnIDAdded {
    protected static final int NEW_ID_REQUEST_CODE = 0x100;

	private static String TAG = "ObservationDetails";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private JSONObject mObservation;
	private ObservationReceiver mObservationReceiver;
	private TextView mNoComments;
	private ProgressBar mProgress;
    private ArrayList<BetterJSONObject> mCommentsIds;
	private Button mAddComment;
	private Button mAddId;
	public String mLogin;
	private ListView mCommentsIdsList;

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
        super.onCreate(savedInstanceState);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        actionBar.setLogo(R.drawable.up_icon);

        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.observation_details_action_bar);
        
        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.observation_details);
        mHelper = new ActivityHelper(this);
        
        Intent intent = getIntent();

        try {
			String obsJson;
        	if (savedInstanceState == null) {
				obsJson = intent.getStringExtra("observation");
        	} else {
        		obsJson = savedInstanceState.getString("observation");
        	}

			if (obsJson == null) {
				finish();
				return;
			}

			mObservation = new JSONObject(obsJson);
        } catch (JSONException e) {
        	e.printStackTrace();
        }

        View title = (View) actionBar.getCustomView().findViewById(R.id.title);
        title.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				finish();
			}
		});

        View viewOnInat = (View) actionBar.getCustomView().findViewById(R.id.view_on_inat);
        viewOnInat.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Display a confirmation dialog
				confirm(ObservationDetails.this, R.string.details, R.string.view_on_inat_confirmation, 
						R.string.yes, R.string.no, 
						new Runnable() { public void run() {
							String inatNetwork = mApp.getInaturalistNetworkMember();
							String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

							Intent i = new Intent(Intent.ACTION_VIEW);
							try {
								i.setData(Uri.parse("http://" + inatHost + "/observations/" + mObservation.getInt("id")));
								startActivity(i);
							} catch (JSONException e) {
								e.printStackTrace();
							}
						}}, 
						null);

			}
		});
        
       
        TextView idName = (TextView) findViewById(R.id.id_name);
        TextView taxonName = (TextView) findViewById(R.id.id_taxon_name);
        idName.setTextColor(mHelper.observationColor(new Observation(new BetterJSONObject(mObservation))));
        final JSONObject taxon = mObservation.optJSONObject("taxon");
        
        if (taxon != null) {
        	String idNameString = getTaxonName(taxon);
        	if (idNameString != null) {
        		idName.setText(idNameString);
        		taxonName.setText(taxon.optString("name", ""));
        	} else {
        		idName.setText(taxon.optString("name", getResources().getString(R.string.unknown)));
        		taxonName.setText("");
        		idName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        	}
        } else {
        	String idNameStr = mObservation.isNull("species_guess") ?
        			getResources().getString(R.string.unknown) :
        			mObservation.optString("species_guess", getResources().getString(R.string.unknown));
        	idName.setText(idNameStr);
        	taxonName.setText("");
        }
        
        if (taxon != null) {
        	idName.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(ObservationDetails.this, GuideTaxonActivity.class);
					intent.putExtra("taxon", new BetterJSONObject(taxon));
					intent.putExtra("guide_taxon", false);
					intent.putExtra("download_taxon", true);
					startActivity(intent);
				}
			});
        	
        	String rank = (taxon.isNull("rank") ? null : taxon.optString("rank", null));
        	if (rank != null) {
        		if ((rank.equalsIgnoreCase("genus")) || (rank.equalsIgnoreCase("species"))) {
        			taxonName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        		}
        	}
        }
        
        final ImageView idPic = (ImageView) findViewById(R.id.id_pic);
        final ProgressBar idPicLoading = (ProgressBar) findViewById(R.id.id_pic_loading);
        JSONArray photos = mObservation.optJSONArray("observation_photos");
        if ((photos != null) && (photos.length() > 0)) {
        	// Show photo
        	JSONObject photo = photos.optJSONObject(0);
        	JSONObject innerPhoto = photo.optJSONObject("photo");
        	String photoUrl = innerPhoto.has("original_url") ? innerPhoto.optString("original_url") : innerPhoto.optString("large_url");
        	idPic.setVisibility(View.INVISIBLE);
        	idPicLoading.setVisibility(View.VISIBLE);
        	UrlImageViewHelper.setUrlDrawable(idPic, photoUrl, ObservationPhotosViewer.observationIcon(mObservation), new UrlImageViewCallback() {
				@Override
				public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
					idPic.setVisibility(View.VISIBLE);
					idPicLoading.setVisibility(View.GONE);
				}

				@Override
				public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
					// No post-processing of bitmap
					return loadedBitmap;
				}
			});

        	idPic.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View v) {
        			Intent intent = new Intent(ObservationDetails.this, ObservationPhotosViewer.class);
        			intent.putExtra("observation", mObservation.toString());
        			startActivity(intent);  
        		}
        	});


        } else {
        	// Show taxon icon
        	idPic.setImageResource(ObservationPhotosViewer.observationIcon(mObservation));
        }

        ImageView userPic = (ImageView) findViewById(R.id.user_pic);
        String photoUrl = "http://www.inaturalist.org/attachments/users/icons/" + mObservation.optInt("user_id") + "-thumb.jpg";
        UrlImageViewHelper.setUrlDrawable(userPic, photoUrl, R.drawable.usericon, new UrlImageViewCallback() {
            @Override
            public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                // Nothing to do here
            }

            @Override
            public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                // Return a circular version of the profile picture
                return ImageUtils.getCircleBitmap(loadedBitmap);
            }
        });

        TextView userName = (TextView) findViewById(R.id.user_name);
        userName.setText(mObservation.optString("user_login"));
        
        TextView location = (TextView) findViewById(R.id.location);
        if (!mObservation.isNull("place_guess")) {
        	location.setText(mObservation.optString("place_guess",
        			mObservation.optString("longitude") + ", " + mObservation.optString("latitude") ));
        } else {
            if (!mObservation.isNull("longitude")) {
                location.setText(mObservation.optString("longitude") + ", " + mObservation.optString("latitude"));
            } else {
                location.setText("");
				ImageView locationImage = (ImageView) findViewById(R.id.location_image);
                locationImage.setVisibility(View.GONE);
            }
        }

        TextView accuracy = (TextView) findViewById(R.id.accuracy);
        if (!mObservation.isNull("positional_accuracy")) {
        	accuracy.setText(String.format(getResources().getString(R.string.accuracy), mObservation.optInt("positional_accuracy")));
        } else {
        	accuracy.setText("");
        }

        TextView observedOnDate = (TextView) findViewById(R.id.observed_on_date);
        TextView observedOnTime = (TextView) findViewById(R.id.observed_on_time);
        BetterJSONObject json = new BetterJSONObject(mObservation);
        Timestamp observedOn = json.getTimestamp("time_observed_at");
        
        if (observedOn != null) {
        	observedOnDate.setText(mApp.formatDate(observedOn));
        	observedOnTime.setText(mApp.shortFormatTime(observedOn));
        } else {
            if (!mObservation.isNull("observed_on")) {
                observedOnDate.setText(mObservation.optString("observed_on", ""));
            } else {
                observedOnDate.setText("");
            }
        	observedOnTime.setText("");
        }
        
        
        mNoComments = (TextView) findViewById(android.R.id.empty);
        mProgress = (ProgressBar) findViewById(R.id.progress);
        
        mAddComment = (Button) findViewById(R.id.add_comment);
        mAddComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	if (mLogin == null) {
            		Toast.makeText(getApplicationContext(), R.string.must_login_to_add_comment, Toast.LENGTH_LONG).show(); 
            		return;
            	}

                showInputDialog();
            }
        });
        
        mAddId = (Button) findViewById(R.id.add_id);
        mAddId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	if (mLogin == null) {
            		Toast.makeText(getApplicationContext(), R.string.must_login_to_add_id, Toast.LENGTH_LONG).show(); 
            		return;
            	}

                Intent intent = new Intent(ObservationDetails.this, IdentificationActivity.class);
                startActivityForResult(intent, NEW_ID_REQUEST_CODE);
            }
        });
        
 
        // Get the observation's IDs/comments
        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationDetails.this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
        startService(serviceIntent);

        mObservationReceiver = new ObservationReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
        Log.i(TAG, "Registering ACTION_OBSERVATION_RESULT");
        registerReceiver(mObservationReceiver, filter);  

        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        mLogin = prefs.getString("username", null);
        
        mCommentsIdsList = (ListView)findViewById(R.id.comments_ids_list);

        loadResultsIntoUI();
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
        outState.putString("observation", mObservation.toString());
        super.onSaveInstanceState(outState);
    }
 
    
    /**
     * Display a confirm dialog. 
     * @param activity
     * @param title
     * @param message
     * @param positiveLabel
     * @param negativeLabel
     * @param onPositiveClick runnable to call (in UI thread) if positive button pressed. Can be null
     * @param onNegativeClick runnable to call (in UI thread) if negative button pressed. Can be null
     */
    public final void confirm(
            final Activity activity, 
            final int title, 
            final int message,
            final int positiveLabel, 
            final int negativeLabel,
            final Runnable onPositiveClick,
            final Runnable onNegativeClick) {
        mHelper.confirm(getString(title), getString(message),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (onPositiveClick != null) onPositiveClick.run();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (onNegativeClick != null) onNegativeClick.run();
                    }
                },
                positiveLabel, negativeLabel);
    }

 	// Utility function for retrieving the Taxon's name
 	private String getTaxonName(JSONObject item) {
 		JSONObject defaultName;
 		String displayName = null;


 		// Get the taxon display name according to configuration of the current iNat network
 		String inatNetwork = mApp.getInaturalistNetworkMember();
 		String networkLexicon = mApp.getStringResourceByName("inat_lexicon_" + inatNetwork);
 		try {
 			JSONArray taxonNames = item.getJSONArray("taxon_names");
 			for (int i = 0; i < taxonNames.length(); i++) {
 				JSONObject taxonName = taxonNames.getJSONObject(i);
 				String lexicon = taxonName.getString("lexicon");
 				if (lexicon.equals(networkLexicon)) {
 					// Found the appropriate lexicon for the taxon
 					displayName = taxonName.getString("name");
 					break;
 				}
 			}
 		} catch (JSONException e3) {
 			e3.printStackTrace();
 		}

 		if (displayName == null) {
 			// Couldn't extract the display name from the taxon names list - use the default one
 			try {
 				displayName = item.getString("unique_name");
 			} catch (JSONException e2) {
 				displayName = null;
 			}
 			try {
 				defaultName = item.getJSONObject("default_name");
 				displayName = defaultName.getString("name");
 			} catch (JSONException e1) {
 				// alas
 				JSONObject commonName = item.optJSONObject("common_name");
 				if (commonName != null) {
 					displayName = commonName.optString("name");
 				} else {
 					displayName = item.optString("name");
 				}
 			}
 		}

 		return displayName;

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

        private CommentsIdsAdapter mAdapter;

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
	        int taxonId = (observation.taxon_id == null ? 0 : observation.taxon_id);
            mAdapter = new CommentsIdsAdapter(ObservationDetails.this, results, taxonId, ObservationDetails.this);
            mCommentsIdsList.setAdapter(mAdapter);
	        loadResultsIntoUI();

	        Handler handler = new Handler();
	        handler.postDelayed(new Runnable() {
	        	@Override
	        	public void run() {
	        		setListViewHeightBasedOnItems(mCommentsIdsList);
	        	}
	        }, 100);
	    }

	}

	@Override
	public void onIdentificationAdded(BetterJSONObject taxon) {
		try {
			// After calling the added ID API - we'll refresh the comment/ID list
			IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
			registerReceiver(mObservationReceiver, filter);

			Intent serviceIntent = new Intent(INaturalistService.ACTION_AGREE_ID, null, ObservationDetails.this, INaturalistService.class);
			serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
			serviceIntent.putExtra(INaturalistService.TAXON_ID, taxon.getJSONObject("taxon").getInt("id"));
			startService(serviceIntent);

		} catch (JSONException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void onIdentificationRemoved(BetterJSONObject taxon) {
		// After calling the remove API - we'll refresh the comment/ID list
		IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
		registerReceiver(mObservationReceiver, filter);

		Intent serviceIntent = new Intent(INaturalistService.ACTION_REMOVE_ID, null, ObservationDetails.this, INaturalistService.class);
		serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
		serviceIntent.putExtra(INaturalistService.IDENTIFICATION_ID, taxon.getInt("id"));
		startService(serviceIntent);
	}

	@Override
	public void onCommentRemoved(BetterJSONObject comment) {

	}

	@Override
	public void onCommentUpdated(BetterJSONObject comment) {

	}

	private void loadResultsIntoUI() {
        if (mCommentsIds == null) {
            mProgress.setVisibility(View.VISIBLE);
            mCommentsIdsList.setVisibility(View.GONE);
            mNoComments.setVisibility(View.GONE);
            
            mAddComment.setEnabled(false);
            mAddId.setEnabled(false);
            
        }  else if (mCommentsIds.size() == 0) {
            mProgress.setVisibility(View.GONE);
            mCommentsIdsList.setVisibility(View.GONE);
            mNoComments.setVisibility(View.VISIBLE);
            
            mAddComment.setEnabled(true);
            mAddId.setEnabled(true);
        } else {
            mProgress.setVisibility(View.GONE);
            mCommentsIdsList.setVisibility(View.VISIBLE);
            mNoComments.setVisibility(View.GONE);
            
            mAddComment.setEnabled(true);
            mAddId.setEnabled(true);
        }
    }
 
    private void showInputDialog() {
        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));


        mHelper.confirm(R.string.add_comment, input,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String comment = input.getText().toString();

                        // Add the comment
                        Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_COMMENT, null, ObservationDetails.this, INaturalistService.class);
                        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
                        serviceIntent.putExtra(INaturalistService.COMMENT_BODY, comment);
                        startService(serviceIntent);

                        mCommentsIds = null;
                        loadResultsIntoUI();

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        // Refresh the comment/id list
                        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
                        registerReceiver(mObservationReceiver, filter);
                        Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationDetails.this, INaturalistService.class);
                        serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
                        startService(serviceIntent2);

                        // Ask for a sync (to update the comment count)
                        Intent serviceIntent3 = new Intent(INaturalistService.ACTION_SYNC, null, ObservationDetails.this, INaturalistService.class);
                        startService(serviceIntent3);
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
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

    			Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_IDENTIFICATION, null, ObservationDetails.this, INaturalistService.class);
    			serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
    			serviceIntent.putExtra(INaturalistService.TAXON_ID, taxonId);
    			serviceIntent.putExtra(INaturalistService.IDENTIFICATION_BODY, idRemarks);
    			startService(serviceIntent);


    			// Show a loading progress until the new comments/IDs are loaded
    			mCommentsIds = null;
    			loadResultsIntoUI();

    			try {
    				Thread.sleep(1000);
    			} catch (InterruptedException e) {
    				e.printStackTrace();
    			}

    			// Refresh the comment/id list
    			IntentFilter filter = new IntentFilter(INaturalistService.ACTION_OBSERVATION_RESULT);
    			registerReceiver(mObservationReceiver, filter);  
    			Intent serviceIntent2 = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationDetails.this, INaturalistService.class);
    			serviceIntent2.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
    			startService(serviceIntent2);

    		}
    	}
    }

    
    /**
     * Sets ListView height dynamically based on the height of the items.   
     *
     * @param listView to be resized
     * @return true if the listView is successfully resized, false otherwise
     */
    public boolean setListViewHeightBasedOnItems(ListView listView) {

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
    		int paddingHeight = (int)getResources().getDimension(R.dimen.abs__action_bar_default_height);
    		params.height = totalItemsHeight + totalDividersHeight + paddingHeight;
    		listView.setLayoutParams(params);
    		listView.requestLayout();

    		return true;

    	} else {
    		return false;
    	}

    } 
}
