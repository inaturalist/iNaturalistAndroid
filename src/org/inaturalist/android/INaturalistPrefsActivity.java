package org.inaturalist.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
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
	private Button mSignInButton;
	private Button mSignOutButton;
	private Button mSignUpButton;
	private SharedPreferences mPreferences;
	private SharedPreferences.Editor mPrefEditor;
	private ProgressDialog mProgressDialog;
	private ActivityHelper mHelper;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
	    
	    toggle();
	    
        mSignInButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				signIn();
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
                mHelper.confirm("Ready to sign up?", 
                        "You're about to visit iNaturalist.org, where you can sign up for a new account. " + 
                        "Once you've confirmed your new account by clicking the link in the confirmation " + 
                        "email you'll receive, you can come back here to enter your username and password.", 
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
	    	mHelper.alert("Username or password was invalid, please sign in again.");
	    }
	}
	
	@Override
	protected void onResume() {
	    super.onResume();
	    mHelper = new ActivityHelper(this);
	}
	
	private void toggle() {
	    String username = mPreferences.getString("username", null);
	    if (username == null) {
	    	mSignInLayout.setVisibility(View.VISIBLE);
	    	mSignOutLayout.setVisibility(View.GONE);
	    } else {
	    	mSignInLayout.setVisibility(View.GONE);
	    	mSignOutLayout.setVisibility(View.VISIBLE);
	    	mSignOutLabel.setText("Signed in as " + username);
	    }
	}
	
	private class SignInTask extends AsyncTask<String, Void, Boolean> {
		private String mUsername;
		private String mPassword;
		private Activity mActivity;
		
		public SignInTask(Activity activity) {
			mActivity = activity;
		}
		
		protected Boolean doInBackground(String... pieces) {
			mUsername = pieces[0];
			mPassword = pieces[1];
	        return INaturalistService.verifyCredentials(mUsername, mPassword);
	    }
		
		protected void onPreExecute() {
			mProgressDialog = ProgressDialog.show(mActivity, "", "Signing in...", true);
		}

	    protected void onPostExecute(Boolean result) {
			if (result) {
				Toast.makeText(mActivity, "Signed in!", Toast.LENGTH_SHORT).show();
				mProgressDialog.dismiss();
			} else {
				mProgressDialog.dismiss();
				mHelper.alert("Sign in failed!");
				return;
			}
			
			mPrefEditor.putString("username", mUsername);
			String credentials = Base64.encodeToString(
					(mUsername + ":" + mPassword).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
			);
			mPrefEditor.putString("credentials", credentials);
			mPrefEditor.putString("password", mPassword);
			mPrefEditor.commit();
			toggle();
	    }
	}
	
	private void signIn() {
		String username = mUsernameTextView.getText().toString().trim();
		String password = mPasswordTextView.getText().toString().trim();
		if (username.isEmpty() || password.isEmpty()) {
			mHelper.alert("Username and password cannot be blank");
			return;
		}
		
		new SignInTask(this).execute(username, password);
	}
	
	private void signOut() {
		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.commit();
		toggle();
	}
}
