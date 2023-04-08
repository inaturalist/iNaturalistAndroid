package org.inaturalist.android;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;

public class SignInTask extends AsyncTask<String, Void, String> {
    private static final String TAG = "SignInTask";

    private static final String GOOGLE_AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/plus.login https://www.googleapis.com/auth/plus.me https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";
    private boolean mPasswordVerificationForDeletion = false;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private String mUsername;
    private String mPassword;
    private INaturalistServiceImplementation.LoginType mLoginType;
    private Activity mActivity;
    private boolean mInvalidated;
    private ProgressDialog mProgressDialog;
    private SignInTaskStatus mCallback;

    private static final int REQUEST_CODE_LOGIN = 0x3000;
    private static final int REQUEST_CODE_ADD_ACCOUNT = 0x3001;
    private static final int REQUEST_CODE_CHOOSE_GOOGLE_ACCOUNT = 0x3002;


    private String mGoogleUsername;

    private String mLoginErrorMessage = null;

    public interface SignInTaskStatus {
        void onLoginSuccessful();
        void onLoginFailed(INaturalistServiceImplementation.LoginType loginType, String failureMessage);
    }

    public SignInTask(Activity activity, SignInTaskStatus callback) {
        mActivity = activity;
        mPreferences = mActivity.getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();
        mHelper = new ActivityHelper(mActivity);
        mCallback = callback;
    }

    public SignInTask(Activity activity, SignInTaskStatus callback, boolean passwordVerificationForDeletion) {
        this(activity, callback);
        mPasswordVerificationForDeletion = passwordVerificationForDeletion;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }



    protected String doInBackground(String... pieces) {
        mUsername = pieces[0];
        mPassword = pieces[1];
        mLoginType = INaturalistServiceImplementation.LoginType.valueOf(pieces[2]);
        if (pieces.length > 3) {
            mInvalidated = (pieces[3] == "invalidated");
        } else {
            mInvalidated = false;
        }

        String[] results = INaturalistServiceImplementation.verifyCredentials(mActivity, mUsername, mPassword, mLoginType, mPasswordVerificationForDeletion);

        if (results == null) {
            return null;
        } else if (results[0] == null) {
            mLoginErrorMessage = results[1];
            Logger.tag(TAG).error(mLoginErrorMessage);
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

        String loginType = null;
        if ((mLoginType == INaturalistServiceImplementation.LoginType.OAUTH_PASSWORD) || (mLoginType == INaturalistServiceImplementation.LoginType.PASSWORD)) {
            loginType = AnalyticsClient.EVENT_VALUE_INATURALIST;
        } else if (mLoginType == INaturalistServiceImplementation.LoginType.GOOGLE) {
            loginType = AnalyticsClient.EVENT_VALUE_GOOGLE_PLUS;
        }

        Logger.tag(TAG).info("onPostExecute: " + result + ":" + loginType + ":" + mInvalidated + ":" + mLoginErrorMessage);

        if (result != null) {
            Toast.makeText(mActivity, mActivity.getString(R.string.signed_in), Toast.LENGTH_SHORT).show();

            try {
                JSONObject eventParams = new JSONObject();
                eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, loginType);

                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_LOGIN, eventParams);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }

        } else {
            try {
                JSONObject eventParams = new JSONObject();
                eventParams.put(AnalyticsClient.EVENT_PARAM_FROM, loginType);
                if (mLoginErrorMessage != null) eventParams.put(AnalyticsClient.EVENT_PARAM_CODE, mLoginErrorMessage);

                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_LOGIN_FAILED, eventParams);
            } catch (JSONException e) {
                Logger.tag(TAG).error(e);
            }


            String failureMessage;
            if (mLoginErrorMessage != null) {
                failureMessage = mLoginErrorMessage;
            } else if (!isNetworkAvailable()) {
                failureMessage = mActivity.getString(R.string.not_connected);
            } else {
                failureMessage = mActivity.getString(R.string.username_invalid);
            }
            mCallback.onLoginFailed(mLoginType, mLoginErrorMessage);

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
        INaturalistService.callService(mActivity, serviceIntent);
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_CODE_ADD_ACCOUNT) && (resultCode == Activity.RESULT_OK)) {
            // User finished adding his account
            signIn(INaturalistServiceImplementation.LoginType.GOOGLE, mGoogleUsername, null);

        } else if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // User finished entering his password
            signIn(INaturalistServiceImplementation.LoginType.GOOGLE, mGoogleUsername, null);

        } else if ((requestCode == REQUEST_CODE_CHOOSE_GOOGLE_ACCOUNT) && (resultCode == Activity.RESULT_OK)) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);

            final Account[] availableAccounts = AccountManager.get(mActivity).getAccountsByType(accountType);
            Account loginAccount = null;

            for (int i = 0; i < availableAccounts.length; i++) {
                if (availableAccounts[i].name.equalsIgnoreCase(accountName)) {
                    // Found the account
                    loginAccount = availableAccounts[i];
                    break;
                }
            }

            final String boundUsername = accountName;
            final String boundInvalidated = mInvalidated ? "invalidated" : null;
            final AccountManagerCallback<Bundle> cb = new AccountManagerCallback<Bundle>() {
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        final Bundle result = future.getResult();
                        final String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
                        final String authToken = result.getString(AccountManager.KEY_AUTHTOKEN);
                        final Intent authIntent = result.getParcelable(AccountManager.KEY_INTENT);
                        if (accountName != null && authToken != null) {
//	                        Logger.tag(TAG).debug(String.format("Token: %s", authToken));
                            execute(boundUsername, authToken, INaturalistServiceImplementation.LoginType.GOOGLE.toString(), boundInvalidated);

                        } else if (authIntent != null) {
                            int flags = authIntent.getFlags();
                            flags &= ~Intent.FLAG_ACTIVITY_NEW_TASK;
                            authIntent.setFlags(flags);
                            mActivity.startActivityForResult(authIntent, REQUEST_CODE_LOGIN);
                        } else {
                            Logger.tag(TAG).error("AccountManager was unable to obtain an authToken.");
                        }
                    } catch (Exception e) {
                        Logger.tag(TAG).error("Auth Error");
                        Logger.tag(TAG).error(e);
                    }
                }
            };

            if (loginAccount == null) {
                return;
            }

            AccountManager.get(mActivity).getAuthToken(loginAccount,
                    GOOGLE_AUTH_TOKEN_TYPE,
                    null,
                    mActivity,
                    cb,
                    null);
        }
    }


    public void signIn(INaturalistServiceImplementation.LoginType loginType, String username, String password) {
	    signIn(loginType, username, password, false);
	}

	public void signIn(INaturalistServiceImplementation.LoginType loginType, String username, String password, boolean invalidated) {
	    boolean googleLogin = (loginType == INaturalistServiceImplementation.LoginType.GOOGLE);

	    if (googleLogin) {
	        String googleUsername = null;
	        Account account = null;

	        // Let the user choose an existing G+ account to login with

            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent = AccountManager.get(mActivity).newChooseAccountIntent(null, null, new String[]{"com.google"}, null, null, null, null);
            } else {
                intent = AccountManager.get(mActivity).newChooseAccountIntent(null, null, new String[]{"com.google"}, false, null, null, null, null);
            }

            mInvalidated = invalidated;
            mActivity.startActivityForResult(intent, REQUEST_CODE_CHOOSE_GOOGLE_ACCOUNT);

	    } else {
	        // "Regular" login
	        execute(username, password, INaturalistServiceImplementation.LoginType.OAUTH_PASSWORD.toString());
	    }
	}

    public void pause() {
    }
    public void resume() {
    }

}

