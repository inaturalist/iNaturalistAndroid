package org.inaturalist.android;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SignInTask extends AsyncTask<String, Void, String> {
    private static final String TAG = "SignInTask";

    private static final String GOOGLE_AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/plus.login https://www.googleapis.com/auth/plus.me https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";
    private AccessTokenTracker mFacebookAccessTokenTracker = null;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private CallbackManager mFacebookCallbackManager;
    private LoginButton mFacebookLoginButton;
    private String mUsername;
    private String mPassword;
    private INaturalistService.LoginType mLoginType;
    private Activity mActivity;
    private boolean mInvalidated;
    private ProgressDialog mProgressDialog;
    private SignInTaskStatus mCallback;

    private static final int REQUEST_CODE_LOGIN = 0x3000;
    private static final int REQUEST_CODE_ADD_ACCOUNT = 0x3001;

    private String mGoogleUsername;

    private String mLoginErrorMessage = null;

    public interface SignInTaskStatus {
        void onLoginSuccessful();
        void onLoginFailed(INaturalistService.LoginType loginType);
    }

    public SignInTask(Activity activity, SignInTaskStatus callback) {
        mActivity = activity;
        mPreferences = mActivity.getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();
        mHelper = new ActivityHelper(mActivity);
        mCallback = callback;
        mFacebookLoginButton = null;
        mFacebookCallbackManager = null;
    }

    public SignInTask(Activity activity, SignInTaskStatus callback, LoginButton facebookLoginButton) {
        this(activity, callback);
        mFacebookLoginButton = facebookLoginButton;

        mFacebookAccessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(AccessToken oldToken, AccessToken newToken) {
                if (newToken != null) {
                    String username = mPreferences.getString("username", null);
                    if (username == null) {
                        // First time login
                        String accessToken = newToken.getToken();
                        execute(null, accessToken, INaturalistService.LoginType.FACEBOOK.toString());
                    }
                }
            }
        };

        mFacebookCallbackManager = CallbackManager.Factory.create();

        ArrayList<String> permissions = new ArrayList<String>();
        permissions.add("email");
        mFacebookLoginButton.setReadPermissions(permissions);

        mFacebookLoginButton.registerCallback(mFacebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {

            }

            @Override
            public void onCancel() {
                if (!isNetworkAvailable()) {
                    Toast.makeText(mActivity.getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(FacebookException exception) {
                mLoginErrorMessage = exception.getMessage();
                Toast.makeText(mActivity.getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();


                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_FROM, AnalyticsClient.EVENT_VALUE_FACEBOOK);
                    if (mLoginErrorMessage != null) eventParams.put(AnalyticsClient.EVENT_PARAM_CODE, mLoginErrorMessage);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_LOGIN_FAILED, eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }



    protected String doInBackground(String... pieces) {
        mUsername = pieces[0];
        mPassword = pieces[1];
        mLoginType = INaturalistService.LoginType.valueOf(pieces[2]);
        if (pieces.length > 3) {
            mInvalidated = (pieces[3] == "invalidated");
        } else {
            mInvalidated = false;
        }

        String[] results = INaturalistService.verifyCredentials(mActivity, mUsername, mPassword, mLoginType);

        if (results == null) {
            return null;
        }

        // Upgrade from FB/Google email to iNat username
        mUsername = results[1];

        return results[0];
    }

    protected void onPreExecute() {
        mLoginErrorMessage = null;
        try {
            mProgressDialog = ProgressDialog.show(mActivity, "", mActivity.getString(R.string.signing_in), true);
        } catch (WindowManager.BadTokenException exc) {
            // Happens when the user rotates the phone while the login happens (and mActivity is no longer valid)
        }
    }

    protected void onPostExecute(String result) {
        try {
            mProgressDialog.dismiss();
        } catch (Exception exc) {
            // Ignore
        }

        if (mFacebookAccessTokenTracker != null) {
            mFacebookAccessTokenTracker.stopTracking();
        }

        String loginType = null;
        if (mLoginType == INaturalistService.LoginType.FACEBOOK) {
            loginType = AnalyticsClient.EVENT_VALUE_FACEBOOK;
        } else if ((mLoginType == INaturalistService.LoginType.OAUTH_PASSWORD) || (mLoginType == INaturalistService.LoginType.PASSWORD)) {
            loginType = AnalyticsClient.EVENT_VALUE_INATURALIST;
        } else if (mLoginType == INaturalistService.LoginType.GOOGLE) {
            loginType = AnalyticsClient.EVENT_VALUE_GOOGLE_PLUS;
        }

        if (result != null) {
            Toast.makeText(mActivity, mActivity.getString(R.string.signed_in), Toast.LENGTH_SHORT).show();

            try {
                JSONObject eventParams = new JSONObject();
                eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, loginType);

                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_LOGIN, eventParams);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } else {
            if (mLoginType == INaturalistService.LoginType.FACEBOOK) {
                // Login failed - need to sign-out of Facebook as well
                LoginManager.getInstance().logOut();
            } else if (mLoginType == INaturalistService.LoginType.GOOGLE && !mInvalidated) {
                AccountManager.get(mActivity).invalidateAuthToken("com.google", mPassword);
                signIn(INaturalistService.LoginType.GOOGLE, mUsername, null, true);
                return;
            }

            try {
                JSONObject eventParams = new JSONObject();
                eventParams.put(AnalyticsClient.EVENT_PARAM_FROM, loginType);
                if (mLoginErrorMessage != null) eventParams.put(AnalyticsClient.EVENT_PARAM_CODE, mLoginErrorMessage);

                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_LOGIN_FAILED, eventParams);
            } catch (JSONException e) {
                e.printStackTrace();
            }


            if (!isNetworkAvailable()) {
                mHelper.alert(mActivity.getString(R.string.not_connected));
            } else {
                mHelper.alert(mActivity.getString(R.string.username_invalid));
            }
            mCallback.onLoginFailed(mLoginType);

            return;
        }

        mPrefEditor.putString("username", mUsername);

        String credentials;
        credentials = result; // Access token
        mPrefEditor.putString("credentials", credentials);
        mPrefEditor.putString("password", mPassword);
        mPrefEditor.putString("login_type", mLoginType.toString());
        mPrefEditor.commit();

        mCallback.onLoginSuccessful();

        // Run the first observation sync
        Intent serviceIntent = new Intent(INaturalistService.ACTION_FIRST_SYNC, null, mActivity, INaturalistService.class);
        mActivity.startService(serviceIntent);
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mFacebookCallbackManager != null) {
            mFacebookCallbackManager.onActivityResult(requestCode, resultCode, data);
        }

        if ((requestCode == REQUEST_CODE_ADD_ACCOUNT) && (resultCode == Activity.RESULT_OK)) {
            // User finished adding his account
            signIn(INaturalistService.LoginType.GOOGLE, mGoogleUsername, null);

        } else if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // User finished entering his password
            signIn(INaturalistService.LoginType.GOOGLE, mGoogleUsername, null);
        }
    }


    public void signIn(INaturalistService.LoginType loginType, String username, String password) {
	    signIn(loginType, username, password, false);
	}

	public void signIn(INaturalistService.LoginType loginType, String username, String password, boolean invalidated) {
	    boolean googleLogin = (loginType == INaturalistService.LoginType.GOOGLE);

	    if (googleLogin) {
	        String googleUsername = null;
	        Account account = null;

	        // See if given account exists
	        final Account[] availableAccounts = AccountManager.get(mActivity).getAccountsByType("com.google");
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
	                        execute(boundUsername, authToken, INaturalistService.LoginType.GOOGLE.toString(), boundInvalidated);

	                    } else if (authIntent != null) {
	                        int flags = authIntent.getFlags();
	                        flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
	                        authIntent.setFlags(flags);
	                        mActivity.startActivityForResult(authIntent, REQUEST_CODE_LOGIN);
	                    } else {
	                        Log.e(TAG, "AccountManager was unable to obtain an authToken.");
	                    }
	                } catch (Exception e) {
	                    Log.e(TAG, "Auth Error", e);
	                }
	            }
	        };

	        if (availableAccounts.length > 1) {
                // More than one Google account - Show multiple choice to select account
                List<String> emails = new ArrayList<String>();
                for (int i = 0; i < availableAccounts.length; i++) {
                    emails.add(availableAccounts[i].name);
                }

                mHelper.selection(mActivity.getString(R.string.select_google_account), emails.toArray(new String[emails.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AccountManager.get(mActivity).getAuthToken(availableAccounts[which],
                                GOOGLE_AUTH_TOKEN_TYPE,
                                null,
                                mActivity,
                                cb,
                                null);
                    }
                });

                return;

            } else if (availableAccounts.length == 1) {
	            accountFound = true;
	            account = availableAccounts[0];

	        } else if (googleUsername == null) {
	            askForGoogleEmail();
	            return;
	        } else {
	            // Redirect user to add account dialog
	            mGoogleUsername = googleUsername;
	            mActivity.startActivityForResult(new Intent(Settings.ACTION_ADD_ACCOUNT), REQUEST_CODE_ADD_ACCOUNT);
	            return;
	        }

	        // Google account login

	        if (account == null) {
	            account = new Account(googleUsername, "com.google");
	        }
	        AccountManager.get(mActivity).getAuthToken(account,
	                GOOGLE_AUTH_TOKEN_TYPE,
	                null,
	                mActivity,
	                cb,
	                null);

	    } else {
	        // "Regular" login
	        execute(username, password, INaturalistService.LoginType.OAUTH_PASSWORD.toString());
	    }
	}

	private void signOut() {
        String login = mPreferences.getString("username", null);

		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.remove("login_type");
        mPrefEditor.remove("last_sync_time");
		mPrefEditor.commit();

		int count1 = mActivity.getContentResolver().delete(Observation.CONTENT_URI, "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", null);
		int count2 = mActivity.getContentResolver().delete(ObservationPhoto.CONTENT_URI, "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", null);
        int count3 = mActivity.getContentResolver().delete(ProjectObservation.CONTENT_URI, "(is_new = 1) OR (is_deleted = 1)", null);
        int count4 = mActivity.getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "((_updated_at > _synced_at AND _synced_at IS NOT NULL) OR (_synced_at IS NULL))", null);

		Log.d(TAG, String.format("Deleted %d / %d / %d / %d unsynced observations", count1, count2, count3, count4));

        // TODO
		//toggle();
    }

    private void askForGoogleEmail() {
        final EditText input = new EditText(mActivity);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));

        mHelper.confirm(R.string.email_address, input, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String username = input.getText().toString();

                        if (username.trim().length() == 0) {
                            return;
                        }

                        signIn(INaturalistService.LoginType.GOOGLE, username.trim().toLowerCase(), null);
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
    }


    public void pause() {
        if (mFacebookAccessTokenTracker != null) {
            mFacebookAccessTokenTracker.stopTracking();
        }
    }
    public void resume() {
        if (mFacebookAccessTokenTracker != null) {
            mFacebookAccessTokenTracker.startTracking();
        }
    }
}

