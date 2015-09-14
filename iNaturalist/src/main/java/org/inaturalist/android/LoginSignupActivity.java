package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.facebook.login.widget.LoginButton;
import com.flurry.android.FlurryAgent;

import org.json.JSONArray;

public class LoginSignupActivity extends Activity implements SignInTask.SignInTaskStatus {

    private static String TAG = "LoginSignupActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private ImageView mBackgroundImage;

    public static final String BACKGROUND_ID = "background_id";
    public static final String SIGNUP = "signup";

    private ImageView mEmailIcon;
    private EditText mEmail;
    private ImageView mPasswordIcon;
    private EditText mPassword;
    private ImageView mUsernameIcon;
    private EditText mUsername;
    private TextView mPasswordWarning;
    private TextView mCheckboxDescription;
    private ImageView mCheckbox;
    private boolean mUseCCLicense;
    private Button mSignup;
    private boolean mIsSignup;
    private SignInTask mSignInTask;
    private LoginButton mFacebookLoginButton;

    private UserRegisterReceiver mUserRegisterReceiver;

    @Override
    protected void onStart() {
        super.onStart();
        FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
        FlurryAgent.logEvent(this.getClass().getSimpleName());
    }

    @Override
    protected void onStop() {
        super.onStop();
        FlurryAgent.onEndSession(this);
    }


    private class UserRegisterReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mUserRegisterReceiver);
            mHelper.stopLoading();

            boolean status = intent.getBooleanExtra(INaturalistService.REGISTER_USER_STATUS, false);
            String error = intent.getStringExtra(INaturalistService.REGISTER_USER_ERROR);

            if (!status) {
                mHelper.alert(getString(R.string.could_not_register_user), error);
            } else {
                // Registration successful - login the user
                mSignInTask.signIn(INaturalistService.LoginType.PASSWORD, mUsername.getText().toString(), mPassword.getText().toString());
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.login_signup);

        mHelper = new ActivityHelper(this);

        mBackgroundImage = (ImageView) findViewById(R.id.background_image);

        int backgroundId = getIntent().getIntExtra(BACKGROUND_ID, 0);
        mIsSignup = getIntent().getBooleanExtra(SIGNUP, false);

        switch (backgroundId) {
            case 2:
                mBackgroundImage.setImageResource(R.drawable.signup_background_3_blurred);
                break;
            case 1:
                mBackgroundImage.setImageResource(R.drawable.signup_background_2_blurred);
                break;
            case 0:
            default:
                mBackgroundImage.setImageResource(R.drawable.signup_background_1_blurred);
                break;
        }

        View backButton = findViewById(R.id.back);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        mEmailIcon = (ImageView) findViewById(R.id.email_icon);
        mEmailIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mEmail.requestFocus();
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        mEmail = (EditText) findViewById(R.id.email);
        mEmail.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    mEmailIcon.getDrawable().setAlpha(0xff);
                } else {
                    mEmailIcon.getDrawable().setAlpha(0x7f);
                }
            }
        });
        mEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkFields();
            }
        });

        mPasswordIcon = (ImageView) findViewById(R.id.password_icon);
        mPasswordIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPassword.requestFocus();
            }
        });
        mPassword = (EditText) findViewById(R.id.password);
        mPassword.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    mPasswordIcon.getDrawable().setAlpha(0xff);
                } else {
                    mPasswordIcon.getDrawable().setAlpha(0x7f);
                }
            }
        });

        mUsernameIcon = (ImageView) findViewById(R.id.username_icon);
        mUsernameIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUsername.requestFocus();
            }
        });
        mUsername = (EditText) findViewById(R.id.username);
        mUsername.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                if (focused) {
                    mUsernameIcon.getDrawable().setAlpha(0xff);
                } else {
                    mUsernameIcon.getDrawable().setAlpha(0x7f);
                }
            }
        });
        mUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkFields();
            }
        });

        mEmailIcon.getDrawable().setAlpha(0x7f);
        mPasswordIcon.getDrawable().setAlpha(0x7f);
        mUsernameIcon.getDrawable().setAlpha(0x7f);

        mPasswordWarning = (TextView) findViewById(R.id.password_warning);
        mPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (mPassword.getText().length() >= (mIsSignup ? 6 : 1)) {
                    mPasswordWarning.setVisibility(View.GONE);
                } else {
                    mPasswordWarning.setVisibility(View.VISIBLE);
                }

                checkFields();
            }
        });

        mUseCCLicense = true;

        mCheckboxDescription = (TextView) findViewById(R.id.checkbox_description);
        mCheckboxDescription.setText(Html.fromHtml(mCheckboxDescription.getText().toString()));
        mCheckboxDescription.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.alert(R.string.content_licensing, R.string.content_licensing_description);
            }
        });

        mCheckbox = (ImageView) findViewById(R.id.checkbox);
        mCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUseCCLicense = !mUseCCLicense;
                if (mUseCCLicense) {
                    mCheckbox.setImageResource(R.drawable.ic_check_box_white_24dp);
                } else {
                    mCheckbox.setImageResource(R.drawable.ic_check_box_outline_blank_white_24dp);
                }
            }
        });

        mSignup = (Button) findViewById(R.id.sign_up);
        mSignup.setEnabled(false);

        if (!mIsSignup) {
            TextView title = (TextView) findViewById(R.id.action_bar_title);
            title.setText(R.string.log_in);
            View emailContainer = (View) findViewById(R.id.email_container);
            emailContainer.setVisibility(View.GONE);
            View checkboxContainer = (View) findViewById(R.id.checkbox_container);
            checkboxContainer.setVisibility(View.GONE);
            mUsername.setHint(R.string.username_or_email);

            View usernameContainer = (View) findViewById(R.id.username_container);
            ViewGroup parent = (ViewGroup)usernameContainer.getParent();
            parent.removeView(usernameContainer);
            parent.addView(usernameContainer, 0);

            mSignup.setText(R.string.log_in);
            mPasswordWarning.setText(R.string.forgot);
            mPasswordWarning.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Open the forgot password page on the user's browser
                    String inatNetwork = mApp.getInaturalistNetworkMember();
                    String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("http://" + inatHost + "/forgot_password.mobile"));
                    startActivity(i);
                }
            });
        } else {
            View loginButtons = findViewById(R.id.login_buttons_container);
            loginButtons.setVisibility(View.GONE);
            View loginWith = findViewById(R.id.login_with);
            loginWith.setVisibility(View.GONE);
        }
        
        mFacebookLoginButton = (LoginButton) findViewById(R.id.facebook_login_button);

        View loginWithFacebook = findViewById(R.id.login_with_facebook);
        loginWithFacebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFacebookLoginButton.performClick();
            }
        });

        View loginWithGoogle = findViewById(R.id.login_with_gplus);
        loginWithGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_LONG).show();
                    return;
                }
                mSignInTask.signIn(INaturalistService.LoginType.GOOGLE, null, null);
            }
        });

        mSignInTask = new SignInTask(this, this, mFacebookLoginButton);

        mSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsSignup) {
                    // Login
                    mSignInTask.signIn(INaturalistService.LoginType.PASSWORD, mUsername.getText().toString(), mPassword.getText().toString());
                } else {
                    // Sign up
                    mUserRegisterReceiver = new UserRegisterReceiver();
                    IntentFilter filter = new IntentFilter(INaturalistService.ACTION_REGISTER_USER_RESULT);
                    registerReceiver(mUserRegisterReceiver, filter);

                    Intent serviceIntent = new Intent(INaturalistService.ACTION_REGISTER_USER, null, LoginSignupActivity.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.EMAIL, mEmail.getText().toString());
                    serviceIntent.putExtra(INaturalistService.USERNAME, mUsername.getText().toString());
                    serviceIntent.putExtra(INaturalistService.PASSWORD, mPassword.getText().toString());
                    serviceIntent.putExtra(INaturalistService.LICENSE, (mUseCCLicense ? "CC-BY-NC" : "on"));
                    startService(serviceIntent);

                    mHelper.loading(getString(R.string.registering));
                }
            }
        });

    }

    private void checkFields() {
        if (((mEmail.getText().length() == 0) && (mIsSignup)) || (mPassword.getText().length() < (mIsSignup ? 6 : 1)) || (mUsername.getText().length() == 0)) {
            mSignup.setEnabled(false);
        } else {
            mSignup.setEnabled(true);
        }
    }

    public void onBackPressed(){
        mSignInTask.pause();
        setResult(RESULT_CANCELED);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mSignInTask.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onLoginSuccessful() {
        mSignInTask.pause();
        setResult(RESULT_OK);
        finish();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            if (mUserRegisterReceiver != null) {
                unregisterReceiver(mUserRegisterReceiver);
            }
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }

}

