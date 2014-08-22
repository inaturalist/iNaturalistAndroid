package org.inaturalist.shedd.android;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.inaturalist.shedd.android.R;
import org.inaturalist.shedd.android.INaturalistService.LoginType;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.Session.StatusCallback;
import com.facebook.UiLifecycleHelper;
import com.facebook.widget.LoginButton;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


public class INaturalistPrefsActivity extends SherlockActivity {
	private static final String TAG = "INaturalistPrefsActivity";
	public static final String REAUTHENTICATE_ACTION = "reauthenticate_action";
	
    private static final int REQUEST_CODE_LOGIN = 0x1000;
    private static final int REQUEST_CODE_ADD_ACCOUNT = 0x1001;
    
    private static final String GOOGLE_AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/plus.login https://www.googleapis.com/auth/plus.me https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";
    
	private LinearLayout mSignInLayout;
	private LinearLayout mSignOutLayout;
	private TextView mUsernameTextView;
	private TextView mPasswordTextView;
	private TextView mSignOutLabel;
	private Button mSignInButton;
	private Button mSignOutButton;
	private Button mSignUpButton;
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mPrefEditor;
	private ProgressDialog mProgressDialog;
	private ActivityHelper mHelper;
    private LoginButton mFacebookLoginButton;
    private Button mGoogleLogin;
	private View mFBSeparator;
	private RadioGroup rbPreferredLocaleSelector;	
	private INaturalistApp mApp;
	
    private UiLifecycleHelper mUiHelper;
    
    private String mGoogleUsername;
    
    private int formerSelectedRadioButton;
    

    private Session.StatusCallback mCallback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };
    private TextView mHelp;
    
    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
//        Log.d(TAG, "onSessionStateChange: " + session.toString() + ":" + state.toString());
        if ((state == SessionState.CLOSED) || (state == SessionState.CLOSED_LOGIN_FAILED)) {
            signOut();
        }
    }

    
    private void askForGoogleEmail() {
      final EditText input = new EditText(INaturalistPrefsActivity.this);
      new AlertDialog.Builder(INaturalistPrefsActivity.this)
          .setTitle(R.string.google_login)
          .setMessage(R.string.email_address)
          .setView(input)
          .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                  String username = input.getText().toString(); 
                  
                  if (username.trim().length() == 0) {
                      return;
                  }
                  
                  signIn(LoginType.GOOGLE, username.trim().toLowerCase(), null);
              }
          }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                  // Do nothing.
              }
          }).show(); 
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
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
		
		ActionBar actionBar = getSupportActionBar();
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);

		
		try {
		    Log.d("KeyHash:", "ENTER");
		    PackageInfo info = getPackageManager().getPackageInfo(
		            "org.inaturalist.shedd.android", 
		            PackageManager.GET_SIGNATURES);
		    for (Signature signature : info.signatures) {
		        MessageDigest md = MessageDigest.getInstance("SHA");
		        md.update(signature.toByteArray());
		        Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
		    }
		} catch (NameNotFoundException e) {
		    Log.d("NameNotFoundException: ", e.toString());
		} catch (NoSuchAlgorithmException e) {
		    Log.d("NoSuchAlgorithmException: ", e.toString());
		}	
		
        mUiHelper = new UiLifecycleHelper(this, mCallback);
        mUiHelper.onCreate(savedInstanceState);
        
	    setContentView(R.layout.preferences);
	    
	    mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
	    mPrefEditor = mPreferences.edit();
	    mHelper = new ActivityHelper(this);
	    
	    
	    mSignInLayout = (LinearLayout) findViewById(R.id.signIn);
	    mSignOutLayout = (LinearLayout) findViewById(R.id.signOut);
	    mUsernameTextView = (TextView) findViewById(R.id.username);
	    mPasswordTextView = (TextView) findViewById(R.id.password);
	    mSignOutLabel = (TextView) findViewById(R.id.signOutLabel);
	    mSignInButton = (Button) findViewById(R.id.signInButton);
	    mSignOutButton = (Button) findViewById(R.id.signOutButton);
	    mSignUpButton = (Button) findViewById(R.id.signUpButton);
	    mHelp = (TextView) findViewById(R.id.tutorial_link);
	    mHelp.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
	    
	    rbPreferredLocaleSelector = (RadioGroup)findViewById(R.id.radioLang);
	    
	    RadioButton rbDeviceLanguage = (RadioButton)findViewById(R.id.rbDeviceLang);
	    rbDeviceLanguage.setText( rbDeviceLanguage.getText() + " (" + mApp.getFormattedDeviceLocale() + ")" );
	    
	    mHelp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(INaturalistPrefsActivity.this, TutorialActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("first_time", false);
                startActivity(intent);
            }
        });
	    
        mFacebookLoginButton = (LoginButton) findViewById(R.id.facebook_login_button);
        mGoogleLogin = (Button) findViewById(R.id.google_login_button);
        mFBSeparator = (View) findViewById(R.id.facebook_login_button_separator);
        
        mGoogleLogin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn(LoginType.GOOGLE, null, null);
            }
        });
        
        ArrayList<String> permissions = new ArrayList<String>();
        permissions.add("email");
        mFacebookLoginButton.setReadPermissions(permissions);
        
        mFacebookLoginButton.setSessionStatusCallback(new StatusCallback() {
            @Override
            public void call(Session session, SessionState state, Exception exception) {
//                Log.d(TAG, "onSessionStateChange: " + state.toString());
                if ((state == SessionState.OPENED) || (state == SessionState.OPENED_TOKEN_UPDATED)) {
                    String username = mPreferences.getString("username", null);
                    if (username == null) {
                        // First time login
                        String accessToken = session.getAccessToken();
//                        Log.d(TAG, "FB Login: " + accessToken);
                        new SignInTask(INaturalistPrefsActivity.this).execute(null, accessToken, LoginType.FACEBOOK.toString());
                    }
                }
            }
        });
        
	    toggle();
	    
    
        mSignInButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			    String username = mUsernameTextView.getText().toString().trim().toLowerCase();
			    String password = mPasswordTextView.getText().toString().trim();
				signIn(LoginType.PASSWORD, username, password);
			}
		});
        
        mSignOutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				signOut();
			}
		});
        
        mSignUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHelper.confirm(getString(R.string.ready_to_signup), 
                        getString(R.string.about_to_signup),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(INaturalistService.HOST + "/users/new"));
                        startActivity(i);
                    }
                });
            }
        });
        
	    if (getIntent().getAction() != null && getIntent().getAction().equals(REAUTHENTICATE_ACTION)) {
	    	signOut();
	    	mHelper.alert(getString(R.string.username_invalid));
	    }
	    
	    updateRadioButtonState();
	}
	
	private void updateRadioButtonState(){
		String pref_locale = mPreferences.getString("pref_locale", "");
		if(pref_locale.equalsIgnoreCase("eu")){
			rbPreferredLocaleSelector.check(R.id.rbDeviceEu);
			formerSelectedRadioButton = R.id.rbDeviceEu;
		}else if(pref_locale.equalsIgnoreCase("gl")){
			rbPreferredLocaleSelector.check(R.id.rbDeviceGl);
			formerSelectedRadioButton = R.id.rbDeviceGl;
		}else{
			rbPreferredLocaleSelector.check(R.id.rbDeviceLang);
			formerSelectedRadioButton = R.id.rbDeviceLang;
		}
	}
	
	public void onRadioButtonClicked(View view){		
	    final boolean checked = ((RadioButton) view).isChecked();
	    final int selectedRadioButtonId = view.getId();	    	    
	    //Toast.makeText(getApplicationContext(), getString(R.string.language_restart), Toast.LENGTH_LONG).show();
	    
	    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
	        @Override
	        public void onClick(DialogInterface dialog, int which) {
	            switch (which){
	            case DialogInterface.BUTTON_POSITIVE:
	            	switch(selectedRadioButtonId) {
		    	        case R.id.rbDeviceEu:
		    	            if (checked){	            	
		    	            	mPrefEditor.putString("pref_locale", "eu");
		    	            	mPrefEditor.commit();	            	
		    	            }
		    	            break;
		    	        case R.id.rbDeviceGl:
		    	            if (checked){
		    	            	mPrefEditor.putString("pref_locale", "gl");
		    	            	mPrefEditor.commit();	            	
		    	            }
		    	            break;
		    	        default:
		    	        	if(checked){
		    	        		mPrefEditor.putString("pref_locale", "");
		    	            	mPrefEditor.commit();	            	
		    	        	}
		    	        	break;
		    	    }
	            	formerSelectedRadioButton = selectedRadioButtonId;
	            	mApp.applyLocaleSettings();
	        	    mApp.restart();
	                break;

	            case DialogInterface.BUTTON_NEGATIVE:
	                //No button clicked
	            	rbPreferredLocaleSelector.check(formerSelectedRadioButton);	            	
	                break;
	            }
	        }
	    };

	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setMessage(getString(R.string.language_restart))
	    	.setPositiveButton(getString(R.string.restart_now), dialogClickListener)
	        .setNegativeButton(getString(R.string.cancel), dialogClickListener).show();	    	    	   
	}
	
	@Override
	protected void onResume() {
	    super.onResume();
	    
	    mHelper = new ActivityHelper(this);
        mUiHelper.onResume();
	}
	
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mUiHelper.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        Log.d(TAG, "onActivityResult " + requestCode + ":" + resultCode + ":" + data);
        mUiHelper.onActivityResult(requestCode, resultCode, data);
        
        if ((requestCode == REQUEST_CODE_ADD_ACCOUNT) && (resultCode == Activity.RESULT_OK)) {
            // User finished adding his account
            signIn(LoginType.GOOGLE, mGoogleUsername, null);
            
        } else if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // User finished entering his password
            signIn(LoginType.GOOGLE, mGoogleUsername, null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mUiHelper.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUiHelper.onDestroy();
    }


	
	private void toggle() {
	    LoginType loginType = LoginType.valueOf(mPreferences.getString("login_type", LoginType.PASSWORD.toString()));
	    
	    String username = mPreferences.getString("username", null);
	    if (username == null) {
	    	mSignInLayout.setVisibility(View.VISIBLE);
	    	mSignOutLayout.setVisibility(View.GONE);
    	    mFacebookLoginButton.setVisibility(View.VISIBLE);
    	    mFBSeparator.setVisibility(View.VISIBLE);
    	    mGoogleLogin.setVisibility(View.VISIBLE);
    	    
	    } else {
	    	mSignInLayout.setVisibility(View.GONE);
	    	mSignOutLayout.setVisibility(View.VISIBLE);
	    	mSignOutLabel.setText(String.format(getString(R.string.signed_in_as), username));
	    	
	    	if (loginType == LoginType.FACEBOOK) {
	    	    mSignOutButton.setVisibility(View.GONE);
	    	    mFacebookLoginButton.setVisibility(View.VISIBLE);
	    	    mFBSeparator.setVisibility(View.VISIBLE);
        	    mGoogleLogin.setVisibility(View.GONE);
	    	} else {
	    	    mSignOutButton.setVisibility(View.VISIBLE);
	    	    mFacebookLoginButton.setVisibility(View.GONE);
	    	    mFBSeparator.setVisibility(View.GONE);
        	    mGoogleLogin.setVisibility(View.GONE);
	    	}
	    }
	}
	
	private class SignInTask extends AsyncTask<String, Void, String> {
		private String mUsername;
		private String mPassword;
		private LoginType mLoginType;
		private Activity mActivity;
		private boolean mInvalidated;
		
		public SignInTask(Activity activity) {
			mActivity = activity;
		}
		
		protected String doInBackground(String... pieces) {
			mUsername = pieces[0];
			mPassword = pieces[1];
			mLoginType = LoginType.valueOf(pieces[2]);
			if (pieces.length > 3) {
			    mInvalidated = (pieces[3] == "invalidated");
			} else {
			    mInvalidated = false;
			}
			
//			Log.d(TAG, String.format("Verifying credentials for %s login with %s:%s",
//			        mLoginType.toString(), (mUsername != null ? mUsername : "<null>"), mPassword));
			
			// TODO - Support for OAuth2 login with Google/Facebook
			if (mLoginType == LoginType.PASSWORD) {
			    String result = INaturalistService.verifyCredentials(mUsername, mPassword);
			    if (result != null) {
			    	mUsername = result;
			        return "true";
			    } else {
			        return null;
			    }
			} else {
			    String[] results = INaturalistService.verifyCredentials(mPassword, mLoginType);
			    
			    if (results == null) {
			        return null;
			    }
			    
			    // Upgrade from FB/Google email to iNat username
			    mUsername = results[1];
			    
			    return results[0];
			}
	    }
		
		protected void onPreExecute() {
			mProgressDialog = ProgressDialog.show(mActivity, "", getString(R.string.signing_in), true);
		}

	    protected void onPostExecute(String result) {
	        try {
	            mProgressDialog.dismiss();
	        } catch (Exception exc) {
	            // Ignore
	        }
			if (result != null) {
				Toast.makeText(mActivity, getString(R.string.signed_in), Toast.LENGTH_SHORT).show();
			} else {
				if (mLoginType == LoginType.FACEBOOK) {
				    // Login failed - need to sign-out of Facebook as well
				    Session session = Session.getActiveSession();
				    session.closeAndClearTokenInformation();
				} else if (mLoginType == LoginType.GOOGLE && !mInvalidated) {
				    AccountManager.get(mActivity).invalidateAuthToken("com.google", mPassword);
				    INaturalistPrefsActivity a = (INaturalistPrefsActivity) mActivity;
				    a.signIn(LoginType.GOOGLE, mUsername, null, true);
				    return;
				}
                mHelper.alert(getString(R.string.signed_in_failed));
				return;
			}
			
			mPrefEditor.putString("username", mUsername);
			
			String credentials;
			if (mLoginType == LoginType.PASSWORD) {
    			credentials = Base64.encodeToString(
    					(mUsername + ":" + mPassword).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
    			);
			} else {
			    credentials = result; // Access token
			}
			mPrefEditor.putString("credentials", credentials);
			mPrefEditor.putString("password", mPassword);
			mPrefEditor.putString("login_type", mLoginType.toString());
			mPrefEditor.commit();
			toggle();
			
			// Run the first observation sync
			Intent serviceIntent = new Intent(INaturalistService.ACTION_FIRST_SYNC, null, INaturalistPrefsActivity.this, INaturalistService.class);
			startService(serviceIntent);
	    }

	}
	
	public void signIn(LoginType loginType, String username, String password) {
	    signIn(loginType, username, password, false);
	}
	
	public void signIn(LoginType loginType, String username, String password, boolean invalidated) {
	    boolean googleLogin = (loginType == LoginType.GOOGLE);

	    if (googleLogin) {
	        String googleUsername = null;
	        Account account = null;

	        // See if given account exists
	        Account[] availableAccounts = AccountManager.get(this).getAccountsByType("com.google");
	        boolean accountFound = false;

	        if (username != null) {
	            googleUsername = username.toLowerCase();
	            for (int i = 0; i < availableAccounts.length; i++) {
	                if (availableAccounts[i].name.equalsIgnoreCase(googleUsername)) {
	                    // Found the account
//	                    Log.d(TAG, "googleUsername: " + googleUsername);
	                    accountFound = true;
	                    break;
	                }
	            }
	        }

	        if (availableAccounts.length > 0) {
	            accountFound = true;
	            account = availableAccounts[0];
	        } else if (googleUsername == null) {
	            askForGoogleEmail();
	            return;
	        } else {
	            // Redirect user to add account dialog
	            mGoogleUsername = googleUsername;
	            startActivityForResult(new Intent(Settings.ACTION_ADD_ACCOUNT), REQUEST_CODE_ADD_ACCOUNT);
	            return;
	        }

	        // Google account login
	        final String boundUsername = googleUsername;
	        final String boundInvalidated = invalidated ? "invalidated" : null;
	        final AccountManagerCallback<Bundle> cb = new AccountManagerCallback<Bundle>() {
	            public void run(AccountManagerFuture<Bundle> future) {
	                try {
	                    final Bundle result = future.getResult();
	                    final String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
	                    final String authToken = result.getString(AccountManager.KEY_AUTHTOKEN);
	                    final Intent authIntent = result.getParcelable(AccountManager.KEY_INTENT);
	                    if (accountName != null && authToken != null) {
//	                        Log.d(TAG, String.format("Token: %s", authToken));
	                        new SignInTask(INaturalistPrefsActivity.this).execute(boundUsername, authToken, LoginType.GOOGLE.toString(), boundInvalidated);

	                    } else if (authIntent != null) {
	                        int flags = authIntent.getFlags();
	                        flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK; 
	                        authIntent.setFlags(flags);
	                        INaturalistPrefsActivity.this.startActivityForResult(authIntent, REQUEST_CODE_LOGIN);
	                    } else {
	                        Log.e(TAG, "AccountManager was unable to obtain an authToken.");
	                    }
	                } catch (Exception e) {
	                    Log.e(TAG, "Auth Error", e);
	                }
	            }
	        };
	        if (account == null) {
	            account = new Account(googleUsername, "com.google");
	        }
	        AccountManager.get(this).getAuthToken(account, 
	                GOOGLE_AUTH_TOKEN_TYPE,
	                null,
	                INaturalistPrefsActivity.this,
	                cb, 
	                null);

	    } else {
	        // "Regular" login
	        new SignInTask(this).execute(username, password, LoginType.PASSWORD.toString());
	    }
	}
	
	private void signOut() {
		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.remove("login_type");
        mPrefEditor.remove("last_sync_time");
		mPrefEditor.commit();
		toggle();
	}
}
