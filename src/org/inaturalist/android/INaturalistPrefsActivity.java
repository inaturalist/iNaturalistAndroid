package org.inaturalist.android;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Set;

import org.inaturalist.android.INaturalistService.LoginType;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.Session.StatusCallback;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.facebook.widget.LoginButton;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


public class INaturalistPrefsActivity extends Activity {
	private static final String TAG = "INaturalistPrefsActivity";
	public static final String REAUTHENTICATE_ACTION = "reauthenticate_action";
	private LinearLayout mSignInLayout;
	private LinearLayout mSignOutLayout;
	private TextView mUsernameTextView;
	private TextView mPasswordTextView;
	private TextView mSignOutLabel;
	private TextView mOrLabel;
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
	
    private UiLifecycleHelper mUiHelper;

    private Session.StatusCallback mCallback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };
    
    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
        Log.d(TAG, "onSessionStateChange: " + session.toString() + ":" + state.toString());
        
        if ((state == SessionState.CLOSED) || (state == SessionState.CLOSED_LOGIN_FAILED)) {
            signOut();
        }
    }


	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	try {
        Log.d("KeyHash:", "ENTER");
    PackageInfo info = getPackageManager().getPackageInfo(
            "org.inaturalist.android", 
            PackageManager.GET_SIGNATURES);
    for (Signature signature : info.signatures) {
        MessageDigest md = MessageDigest.getInstance("SHA");
        md.update(signature.toByteArray());
        Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
        }
} catch (NameNotFoundException e) {

} catch (NoSuchAlgorithmException e) {

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
	    mOrLabel = (TextView) findViewById(R.id.orLabel);
	    mSignInButton = (Button) findViewById(R.id.signInButton);
	    mSignOutButton = (Button) findViewById(R.id.signOutButton);
	    mSignUpButton = (Button) findViewById(R.id.signUpButton);
	    
        mFacebookLoginButton = (LoginButton) findViewById(R.id.facebook_login_button);
        mGoogleLogin = (Button) findViewById(R.id.google_login_button);
        mFBSeparator = (View) findViewById(R.id.facebook_login_button_separator);
        
        mGoogleLogin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
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
        });
        
        ArrayList<String> permissions = new ArrayList<String>();
        permissions.add("email");
        mFacebookLoginButton.setReadPermissions(permissions);
        
        mFacebookLoginButton.setSessionStatusCallback(new StatusCallback() {
            @Override
            public void call(Session session, SessionState state, Exception exception) {
                Log.d(TAG, "onSessionStateChange: " + state.toString());
                
                if ((state == SessionState.OPENED) || (state == SessionState.OPENED_TOKEN_UPDATED)) {
                    String username = mPreferences.getString("username", null);
                    if (username == null) {
                        // First time login
                        String accessToken = session.getAccessToken();
                        Log.d(TAG, "FB Login: " + accessToken);
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
    
    private String mUsr = null;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mUiHelper.onActivityResult(requestCode, resultCode, data);
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
		
		public SignInTask(Activity activity) {
			mActivity = activity;
		}
		
		protected String doInBackground(String... pieces) {
			mUsername = pieces[0];
			mPassword = pieces[1];
			mLoginType = LoginType.valueOf(pieces[2]);
			
			Log.d(TAG, String.format("Verifying credentials for %s login with %s:%s",
			        mLoginType.toString(), (mUsername != null ? mUsername : "<null>"), mPassword));
			
			// TODO - Support for OAuth2 login with Google/Facebook
			if (mLoginType == LoginType.PASSWORD) {
			    Boolean result = INaturalistService.verifyCredentials(mUsername, mPassword);
			    if (result) {
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
			if (result != null) {
				Toast.makeText(mActivity, getString(R.string.signed_in), Toast.LENGTH_SHORT).show();
				mProgressDialog.dismiss();
			} else {
				mProgressDialog.dismiss();
				mHelper.alert(getString(R.string.signed_in_failed));
				
				if (mLoginType == LoginType.FACEBOOK) {
				    // Login failed - need to sign-out of Facebook as well
				    Session session = Session.getActiveSession();
				    session.closeAndClearTokenInformation();
				}
				
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
	    }

	}
	
	private void signIn(LoginType loginType, String username, String password) {
	    boolean googleLogin = (loginType == LoginType.GOOGLE);
	    
		if (username.isEmpty() || (!googleLogin && password.isEmpty())) {
			mHelper.alert(getString(R.string.username_cannot_be_blank));
			return;
		}
		
		if (googleLogin) {
		    final String googleUsername = username.toLowerCase();
		    // Google account login
		    final AccountManagerCallback<Bundle> cb = new AccountManagerCallback<Bundle>() {
		        public void run(AccountManagerFuture<Bundle> future) {
	                try {
	                    final Bundle result = future.getResult();
	                    final String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
	                    final String authToken = result.getString(AccountManager.KEY_AUTHTOKEN);
	                    final Intent authIntent = result.getParcelable(AccountManager.KEY_INTENT);
	                    if (accountName != null && authToken != null) {
	                        Log.d(TAG, String.format("Token: %s", authToken));
	                        
                    	    new SignInTask(INaturalistPrefsActivity.this).execute(googleUsername, authToken, LoginType.GOOGLE.toString());
                    	    
	                    } else if (authIntent != null) {
            		        int flags = authIntent.getFlags();
            		        flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK; 
            		        authIntent.setFlags(flags);
	                        INaturalistPrefsActivity.this.startActivityForResult(authIntent, 666);
	                    } else {
	                        Log.e(TAG, "AccountManager was unable to obtain an authToken.");
	                    }
	                } catch (Exception e) {
	                    Log.e(TAG, "Auth Error", e);
	                }
	            }
	        };  
		   Account account = new Account(googleUsername, "com.google");
		   AccountManager.get(this).getAuthToken(account, "oauth2:https://www.googleapis.com/auth/userinfo.email", true, cb, null); 
		   
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
		mPrefEditor.commit();
		toggle();
	}
}
