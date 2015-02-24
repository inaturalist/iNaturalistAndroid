package org.inaturalist.android;

import java.sql.Timestamp;
import java.util.HashMap;

import org.inaturalist.android.INaturalistService.LoginType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.*;
import android.widget.ImageView;
import android.widget.TextView;

public class ObservationDetails extends SherlockActivity {
    private static String TAG = "ObservationDetails";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private JSONObject mObservation;

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
        	if (savedInstanceState == null) {
        		mObservation = new JSONObject(intent.getStringExtra("observation"));
        	} else {
        		mObservation = new JSONObject(savedInstanceState.getString("observation"));
        	}
        } catch (JSONException e) {
        	e.printStackTrace();
        }


        View viewOnInat = (View) actionBar.getCustomView().findViewById(R.id.view_on_inat);
        viewOnInat.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Display a confirmation dialog
				confirm(ObservationDetails.this, R.string.details, R.string.view_on_inat_confirmation, 
						R.string.yes, R.string.no, 
						new Runnable() { public void run() {
							Intent i = new Intent(Intent.ACTION_VIEW);
							try {
								i.setData(Uri.parse(INaturalistService.HOST + "/observations/" + mObservation.getInt("id")));
								startActivity(i);
							} catch (JSONException e) {
								e.printStackTrace();
							}
						}}, 
						null);

			}
		});
        
        View viewComments = (View) actionBar.getCustomView().findViewById(R.id.view_comments);
        viewComments.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ObservationDetails.this, CommentsIdsActivity.class);
				intent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
				intent.putExtra(INaturalistService.TAXON_ID, mObservation.optInt("taxon_id"));
				startActivity(intent);

				// Get the observation's IDs/comments
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_OBSERVATION, null, ObservationDetails.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, mObservation.optInt("id"));
                startService(serviceIntent);
			}
		});

        
        TextView idName = (TextView) findViewById(R.id.id_name);
        TextView taxonName = (TextView) findViewById(R.id.id_taxon_name);
        idName.setTextColor(mHelper.observationColor(new Observation(new BetterJSONObject(mObservation))));
        JSONObject taxon = mObservation.optJSONObject("taxon");
        
        if (taxon != null) {
        	idName.setText(getTaxonName(taxon));
        	taxonName.setText(taxon.optString("name", ""));
        } else {
        	String idNameStr = mObservation.isNull("species_guess") ?
        			getResources().getString(R.string.unknown) :
        			mObservation.optString("species_guess", getResources().getString(R.string.unknown));
        	idName.setText(idNameStr);
        	taxonName.setText("");
        }
        
        ImageView idPic = (ImageView) findViewById(R.id.id_pic);


        JSONArray photos = mObservation.optJSONArray("observation_photos");
        if ((photos != null) && (photos.length() > 0)) {
        	// Show photo
        	JSONObject photo = photos.optJSONObject(0);
        	JSONObject innerPhoto = photo.optJSONObject("photo");
        	String photoUrl = innerPhoto.optString("large_url");
        	UrlImageViewHelper.setUrlDrawable(idPic, photoUrl, observationIcon(mObservation));
        } else {
        	// Show taxon icon
        	idPic.setImageResource(observationIcon(mObservation));
        }
        
        
        ImageView userPic = (ImageView) findViewById(R.id.user_pic);
        if (true) {
        	String photoUrl = "http://www.inaturalist.org/attachments/users/icons/" + mObservation.optInt("user_id") + "-thumb.jpg";
        	UrlImageViewHelper.setUrlDrawable(userPic, photoUrl, R.drawable.usericon);
        } else {
        	userPic.setImageResource(R.drawable.usericon);
        }
        TextView userName = (TextView) findViewById(R.id.user_name);
        userName.setText(mObservation.optString("user_login"));
        
        TextView location = (TextView) findViewById(R.id.location);
        location.setText(mObservation.optString("place_guess",
        		mObservation.optString("longitude") + ", " + mObservation.optString("latitude") ));

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
        	observedOnDate.setText(mObservation.optString("observed_on", ""));
        	observedOnTime.setText("");
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
        outState.putString("observation", mObservation.toString());
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
    public static final void confirm(
            final Activity activity, 
            final int title, 
            final int message,
            final int positiveLabel, 
            final int negativeLabel,
            final Runnable onPositiveClick,
            final Runnable onNegativeClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setCancelable (false);
        dialog.setPositiveButton(positiveLabel,
                new DialogInterface.OnClickListener () {
            public void onClick (DialogInterface dialog, int buttonId) {
                if (onPositiveClick != null) onPositiveClick.run();
            }
        });
        dialog.setNegativeButton(negativeLabel,
                new DialogInterface.OnClickListener () {
            public void onClick (DialogInterface dialog, int buttonId) {
                if (onNegativeClick != null) onNegativeClick.run();
            }
        });
        dialog.setIcon (android.R.drawable.ic_dialog_alert);
        dialog.show();

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
 				displayName = getResources().getString(R.string.unknown);
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
 					displayName = getResources().getString(R.string.unknown);
 				}
 			}
 		}

 		return displayName;

 	}

 	private int observationIcon(JSONObject o) {
        if (!o.has("iconic_taxon_name") || o.isNull("iconic_taxon_name")) {
            return R.drawable.unknown_large;
        }
        String iconicTaxonName;
		try {
			iconicTaxonName = o.getString("iconic_taxon_name");
		} catch (JSONException e) {
			e.printStackTrace();
            return R.drawable.unknown_large;
		}
        
		if (iconicTaxonName == null) {
			return R.drawable.unknown_large;
		} else if (iconicTaxonName.equals("Animalia")) {
			return R.drawable.animalia_large;
		} else if (iconicTaxonName.equals("Plantae")) {
			return R.drawable.plantae_large;
		} else if (iconicTaxonName.equals("Chromista")) {
			return R.drawable.chromista_large;
		} else if (iconicTaxonName.equals("Fungi")) {
			return R.drawable.fungi_large;
		} else if (iconicTaxonName.equals("Protozoa")) {
			return R.drawable.protozoa_large;
		} else if (iconicTaxonName.equals("Actinopterygii")) {
			return R.drawable.actinopterygii_large;
		} else if (iconicTaxonName.equals("Amphibia")) {
			return R.drawable.amphibia_large;
		} else if (iconicTaxonName.equals("Reptilia")) {
			return R.drawable.reptilia_large;
		} else if (iconicTaxonName.equals("Aves")) {
			return R.drawable.aves_large;
		} else if (iconicTaxonName.equals("Mammalia")) {
			return R.drawable.mammalia_large;
		} else if (iconicTaxonName.equals("Mollusca")) {
			return R.drawable.mollusca_large;
		} else if (iconicTaxonName.equals("Insecta")) {
			return R.drawable.insecta_large;
		} else if (iconicTaxonName.equals("Arachnida")) {
			return R.drawable.arachnida_large;
		} else {
			return R.drawable.unknown_large;
		}


    }


}
