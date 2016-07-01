package org.inaturalist.android;

import com.facebook.login.LoginManager;
import com.flurry.android.FlurryAgent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;


public class INaturalistPrefsActivity extends BaseFragmentActivity implements SignInTask.SignInTaskStatus {
	private static final String TAG = "INaturalistPrefsActivity";
	public static final String REAUTHENTICATE_ACTION = "reauthenticate_action";
	
    private static final int REQUEST_CODE_LOGIN = 0x1000;

    private static final String GOOGLE_AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/plus.login https://www.googleapis.com/auth/plus.me https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";
    
	private LinearLayout mSignInLayout;
	private LinearLayout mSignOutLayout;
	private TextView mSignOutLabel;
	private Button mSignInButton;
	private Button mSignUpButton;
	private Button mSignOutButton;
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mPrefEditor;
	private ActivityHelper mHelper;
	private RadioGroup rbPreferredNetworkSelector;
	private RadioGroup rbPreferredLocaleSelector;
	private CheckBox mAutoSync;
	private INaturalistApp mApp;
	
    private int formerSelectedNetworkRadioButton;
    private int formerSelectedRadioButton;


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

    private TextView mHelp;
	private TextView mContactSupport;
	private TextView mVersion;
    
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }


//		try {
//		    Log.d("KeyHash:", "ENTER");
//		    PackageInfo info = getPackageManager().getPackageInfo(
//		            "org.inaturalist.android", 
//		            PackageManager.GET_SIGNATURES);
//		    for (Signature signature : info.signatures) {
//		        MessageDigest md = MessageDigest.getInstance("SHA");
//		        md.update(signature.toByteArray());
//		        Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
//		    }
//		} catch (NameNotFoundException e) {
//		    Log.d("NameNotFoundException: ", e.toString());
//		} catch (NoSuchAlgorithmException e) {
//		    Log.d("NoSuchAlgorithmException: ", e.toString());
//		}	
		
	    setContentView(R.layout.preferences);
	    
	    onDrawerCreate(savedInstanceState);
	    
	    mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
	    mPrefEditor = mPreferences.edit();
	    mHelper = new ActivityHelper(this);
	    
	    
	    mSignInLayout = (LinearLayout) findViewById(R.id.signIn);
		mAutoSync = (CheckBox) findViewById(R.id.auto_sync);
	    mSignOutLayout = (LinearLayout) findViewById(R.id.signOut);
	    mSignOutLabel = (TextView) findViewById(R.id.signOutLabel);
	    mSignInButton = (Button) findViewById(R.id.signInButton);
		mSignUpButton = (Button) findViewById(R.id.signUpButton);
	    mSignOutButton = (Button) findViewById(R.id.signOutButton);
	    mHelp = (TextView) findViewById(R.id.tutorial_link);
	    mHelp.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);

		mAutoSync.setChecked(mApp.getAutoSync());
        mAutoSync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                mApp.setAutoSync(value);
            }
        });
	    
	    mContactSupport = (TextView) findViewById(R.id.contact_support);
	    mContactSupport.setText(Html.fromHtml(mContactSupport.getText().toString()));
		mContactSupport.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
				// Get app version
				try {
					PackageManager manager = INaturalistPrefsActivity.this.getPackageManager();
					PackageInfo info = manager.getPackageInfo(INaturalistPrefsActivity.this.getPackageName(), 0);

					// Open the email client
					Intent mailer = new Intent(Intent.ACTION_SEND);
					mailer.setType("message/rfc822");
					mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});
					String username = mPreferences.getString("username", null);
					mailer.putExtra(Intent.EXTRA_SUBJECT, String.format(getString(R.string.inat_support_email_subject), info.versionName, info.versionCode, username == null ? "N/A" : username));
					startActivity(Intent.createChooser(mailer, getString(R.string.send_email)));
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
            }
        });
	    //mContactSupport.setMovementMethod(LinkMovementMethod.getInstance());
	    
	    mVersion = (TextView) findViewById(R.id.version);
	    try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			mVersion.setText(String.format("Version %s (%d)", packageInfo.versionName, packageInfo.versionCode));
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			mVersion.setText("");
		}
	    
	    // Add the iNat network settings
	    rbPreferredNetworkSelector = (RadioGroup)findViewById(R.id.radioNetworks);
	    
	    String[] networks = mApp.getINatNetworks();
	    for (int i = 0; i < networks.length; i++) {
	    	RadioButton radioButton = new RadioButton(this);
	    	radioButton.setText(mApp.getStringResourceByName("network_" + networks[i]));
	    	radioButton.setId(i);
	    	radioButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onINatNetworkRadioButtonClicked(v);
				}
			});
            rbPreferredNetworkSelector.addView(radioButton);
	    }
	    
	   makeLanguageRadioButtons(); 
	    
	    mHelp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(INaturalistPrefsActivity.this, TutorialActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("first_time", false);
                startActivity(intent);
            }
        });
	    
	    toggle();
	    
        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(INaturalistPrefsActivity.this, OnboardingActivity.class);
                intent.putExtra(OnboardingActivity.LOGIN, true);

                startActivityForResult(intent, REQUEST_CODE_LOGIN);
            }
        });

		mSignUpButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivityForResult(new Intent(INaturalistPrefsActivity.this, OnboardingActivity.class), REQUEST_CODE_LOGIN);
			}
		});

        mSignOutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
                mHelper.confirm(getString(R.string.signed_out),
						getString(R.string.alert_sign_out),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								signOut();
							}
						},
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								dialogInterface.cancel();;
							}
						}
				);
			}
		});
        
	    if (getIntent().getAction() != null && getIntent().getAction().equals(REAUTHENTICATE_ACTION)) {
	    	signOut();
	    	mHelper.alert(getString(R.string.username_invalid));
	    }
	    
	    updateINatNetworkRadioButtonState();
	    updateRadioButtonState();
	}
	
	private void updateINatNetworkRadioButtonState(){
	    String[] networks = mApp.getINatNetworks();
		String network = mApp.getInaturalistNetworkMember();

	    for (int i = 0; i < networks.length; i++) {
	    	if (networks[i].equals(network)) {
	    		rbPreferredNetworkSelector.check(i);
	    		formerSelectedNetworkRadioButton = i;
	    		break;
	    	}
	    }
	}
	
	public void onINatNetworkRadioButtonClicked(View view){		
	    final boolean checked = ((RadioButton) view).isChecked();
	    final int selectedRadioButtonId = view.getId();	    	    
	    final String[] networks = mApp.getINatNetworks();
	    
	    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
	        @Override
	        public void onClick(DialogInterface dialog, int which) {
	            switch (which){
	            case DialogInterface.BUTTON_POSITIVE:
	            	if (checked) {	            	
	            		mApp.setInaturalistNetworkMember(networks[selectedRadioButtonId]);
	            		//mPrefEditor.putString("pref_locale", mApp.getStringResourceByName("inat_network_language_" + networks[selectedRadioButtonId]));
	            		//mPrefEditor.commit();
	            	}            	

	            	formerSelectedNetworkRadioButton = selectedRadioButtonId;
	            	mApp.applyLocaleSettings();
	        	    mApp.restart();
					finish();
	                break;

	            case DialogInterface.BUTTON_NEGATIVE:
	                //No button clicked
	            	rbPreferredNetworkSelector.check(formerSelectedNetworkRadioButton);	            	
	                break;
	            }
	        }
	    };

        LayoutInflater inflater = getLayoutInflater();
		View titleBarView = inflater.inflate(R.layout.change_network_title_bar, null);
		ImageView titleBarLogo = (ImageView) titleBarView.findViewById(R.id.title_bar_logo);

	    String logoName = mApp.getStringResourceByName("inat_logo_" + networks[selectedRadioButtonId]);
	    String packageName = getPackageName();
	    int resId = getResources().getIdentifier(logoName, "drawable", packageName);
	    titleBarLogo.setImageResource(resId);

        mHelper.confirm(titleBarView, mApp.getStringResourceByName("alert_message_use_" + networks[selectedRadioButtonId]),
                dialogClickListener, dialogClickListener);
	}
	
	@Override
	protected void onResume() {
	    super.onResume();
	    
	    mHelper = new ActivityHelper(this);
	}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
			// Refresh login state
            toggle();
			refreshUserDetails();
        }
    }


	
	private void toggle() {
	    String username = mPreferences.getString("username", null);
	    if (username == null) {
	    	mSignInLayout.setVisibility(View.VISIBLE);
	    	mSignOutLayout.setVisibility(View.GONE);

	    } else {
	    	mSignInLayout.setVisibility(View.GONE);
	    	mSignOutLayout.setVisibility(View.VISIBLE);
	    	mSignOutLabel.setText(String.format(getString(R.string.signed_in_as), username));
	    }
	}
	
	private void signOut() {
        INaturalistService.LoginType loginType = INaturalistService.LoginType.valueOf(mPreferences.getString("login_type", INaturalistService.LoginType.OAUTH_PASSWORD.toString()));

        if (loginType == INaturalistService.LoginType.FACEBOOK) {
            LoginManager.getInstance().logOut();
        }

        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);

		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.remove("login_type");
        mPrefEditor.remove("last_sync_time");
		mPrefEditor.remove("observation_count");
		mPrefEditor.remove("user_icon_url");
		mPrefEditor.commit();
		
		int count1 = getContentResolver().delete(Observation.CONTENT_URI, "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", null);
		int count2 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", null);
        int count3 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "(is_new = 1) OR (is_deleted = 1)", null);
        int count4 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", null);

		Log.d(TAG, String.format("Deleted %d / %d / %d / %d unsynced observations", count1, count2, count3, count4));

		toggle();
        refreshUserDetails();
	}
	
	private boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}	


	public void makeLanguageRadioButtons()
	{
		rbPreferredLocaleSelector = (RadioGroup)findViewById(R.id.radioLang);

		String[] locales = LocaleHelper.SupportedLocales;
		for (int i=0; i < locales.length; i++) {
			RadioButton rb = (RadioButton) rbPreferredLocaleSelector.getChildAt(i);
			final int selectedButton = i;
			final Activity context = this;
			rb.setOnClickListener (new OnClickListener() {
				@Override
				public void onClick(View v) {
					PromptUserToConfirmSelection(context, selectedButton);
				}
			});
		}
	}

	private void PromptUserToConfirmSelection(Activity context, int index) {
		final int selectedButton = index;
		final String locale = LocaleHelper.SupportedLocales[index];
		final Activity thisActivity = context;
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					mPrefEditor.putString("pref_locale", locale);
					mPrefEditor.commit();
					formerSelectedRadioButton = selectedButton;
					mApp.applyLocaleSettings();
					mApp.restart();
					finish();
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					//No button clicked
					rbPreferredLocaleSelector.check(rbPreferredLocaleSelector.getChildAt(formerSelectedRadioButton).getId());
					break;
				}
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(thisActivity);
		builder.setMessage(getString(R.string.language_restart))
		.setPositiveButton(getString(R.string.restart_now), dialogClickListener)
		.setNegativeButton(getString(R.string.cancel), dialogClickListener)
		.setCancelable(false).show();;


	}
	private void updateRadioButtonState(){
		String pref_locale = mPreferences.getString("pref_locale", "");
		String[] supportedLocales = LocaleHelper.SupportedLocales;

		// if no preference is set, find app default
		if (pref_locale.equalsIgnoreCase("")) {
			// Use device locale
			RadioButton rb = (RadioButton) rbPreferredLocaleSelector.getChildAt(0);
			rb.setChecked(true);
			formerSelectedRadioButton = 0;
		}
		else {
			for (int i = 0; i < supportedLocales.length; i++) {
				if (pref_locale.equalsIgnoreCase(supportedLocales[i])) {
					RadioButton rb = (RadioButton) rbPreferredLocaleSelector.getChildAt(i);
					rb.setChecked(true);
					formerSelectedRadioButton = i;
					return;
				}
			}
		}

	}

	@Override
	public void onLoginSuccessful() {
		// Refresh the login controls
		toggle();
	}
}
