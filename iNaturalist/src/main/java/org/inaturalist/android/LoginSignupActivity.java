package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.login.widget.LoginButton;
import com.flurry.android.FlurryAgent;

public class LoginSignupActivity extends AppCompatActivity implements SignInTask.SignInTaskStatus {

    private static final int PERMISSIONS_REQUEST = 0x1000;

    private static String TAG = "LoginSignupActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private ImageView mBackgroundImage;

    public static final String BACKGROUND_ID = "background_id";
    public static final String SIGNUP = "signup";
    public static final String PASSWORD_CHANGED = "password_changed";

    private ImageView mEmailIcon;
    private EditText mEmail;
    private ImageView mPasswordIcon;
    private EditText mPassword;
    private ImageView mUsernameIcon;
    private EditText mUsername;
    private TextView mPasswordWarning;
    private TextView mForgotPassword;
    private TextView mCheckboxDescription;
    private ImageView mCheckbox;
    private boolean mUseCCLicense;
    private Button mSignup;
    private boolean mIsSignup;
    private SignInTask mSignInTask;
    private LoginButton mFacebookLoginButton;

    private UserRegisterReceiver mUserRegisterReceiver;
    private TextView mTerms;
    private boolean mPasswordChanged;

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
            BaseFragmentActivity.safeUnregisterReceiver(mUserRegisterReceiver, LoginSignupActivity.this);
            mHelper.stopLoading();

            boolean status = intent.getBooleanExtra(INaturalistService.REGISTER_USER_STATUS, false);
            String error = intent.getStringExtra(INaturalistService.REGISTER_USER_ERROR);

            if (!status) {
                mHelper.alert(getString(R.string.could_not_register_user), error);
            } else {
                // Registration successful - login the user
                recreateSignInTaskIfNeeded();
                mSignInTask.signIn(INaturalistService.LoginType.OAUTH_PASSWORD, mUsername.getText().toString(), mPassword.getText().toString());
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_CREATE_ACCOUNT);
            }
        }
    }

    // Recreates a new instance of the sign in task if it finished running before (since an AsyncTask can only be run once).
    private void recreateSignInTaskIfNeeded() {
        if (mSignInTask.getStatus() == AsyncTask.Status.FINISHED) {
            mSignInTask = new SignInTask(this, this, mFacebookLoginButton);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Make it a full screen view, that goes underneath the semi transparent status bar
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#11ffffff"));

            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }


        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.login_signup);

        mHelper = new ActivityHelper(this);

        mBackgroundImage = (ImageView) findViewById(R.id.background_image);

        int backgroundId = getIntent().getIntExtra(BACKGROUND_ID, 0);
        mIsSignup = getIntent().getBooleanExtra(SIGNUP, false);
        mPasswordChanged = getIntent().getBooleanExtra(PASSWORD_CHANGED, false);

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

        mForgotPassword = (TextView) findViewById(R.id.forgot_password);
        mForgotPassword.setVisibility(!mIsSignup ? View.VISIBLE : View.GONE);

        mPasswordWarning = (TextView) findViewById(R.id.password_warning);
        mPasswordWarning.setVisibility(mIsSignup ? View.VISIBLE : View.GONE);
        mPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if ((!mIsSignup) || (mPassword.getText().length() >= 6)) {
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

        mTerms = (TextView) findViewById(R.id.terms);
        mTerms.setText(Html.fromHtml(mTerms.getText().toString()));
        mTerms.setMovementMethod(LinkMovementMethod.getInstance());

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

            mTerms.setVisibility(View.GONE);

            View usernameContainer = (View) findViewById(R.id.username_container);
            ViewGroup parent = (ViewGroup)usernameContainer.getParent();
            parent.removeView(usernameContainer);
            parent.addView(usernameContainer, 0);

            mSignup.setText(R.string.log_in);
            mPasswordWarning.setVisibility(View.GONE);
            mForgotPassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Open the forgot password page on the user's browser
                    String inatNetwork = mApp.getInaturalistNetworkMember();
                    String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(inatHost + "/forgot_password.mobile"));
                    startActivity(i);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_FORGOT_PASSWORD);
                }
            });

            if (mPasswordChanged) {
                // User changed his password on the website - ask for new password
                SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                String username = prefs.getString("username", "");
                mUsername.setText(username);
                usernameContainer.setVisibility(username.length() == 0 ? View.VISIBLE : View.GONE);

                View loginButtons = findViewById(R.id.login_buttons_container);
                loginButtons.setVisibility(View.GONE);
                View loginWith = findViewById(R.id.login_with);
                loginWith.setVisibility(View.GONE);
                backButton.setVisibility(View.GONE);

                View passwordChanges = findViewById(R.id.password_changed);
                passwordChanges.setVisibility(View.VISIBLE);
            }
        } else {
            View loginButtons = findViewById(R.id.login_buttons_container);
            loginButtons.setVisibility(View.GONE);
            View loginWith = findViewById(R.id.login_with);
            loginWith.setVisibility(View.GONE);
            mTerms.setVisibility(View.VISIBLE);
        }
        
        mFacebookLoginButton = (LoginButton) findViewById(R.id.facebook_login_button);

        View loginWithFacebook = findViewById(R.id.login_with_facebook);
        loginWithFacebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recreateSignInTaskIfNeeded();
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

                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.O) &&
                        (ContextCompat.checkSelfPermission(LoginSignupActivity.this, android.Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED)) {
                    mHelper.confirm(R.string.just_so_you_know, R.string.ask_for_g_plus_permissions, R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(LoginSignupActivity.this, new String[]{android.Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST);
                        }
                    });
                } else {
                    recreateSignInTaskIfNeeded();
                    mSignInTask.signIn(INaturalistService.LoginType.GOOGLE, null, null);
                }
            }
        });

        mSignInTask = new SignInTask(this, this, mFacebookLoginButton);

        mSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsSignup) {
                    // Login
                    recreateSignInTaskIfNeeded();
                    mSignInTask.signIn(INaturalistService.LoginType.OAUTH_PASSWORD, mUsername.getText().toString().trim(), mPassword.getText().toString());
                } else {
                    // Sign up
                    mUserRegisterReceiver = new UserRegisterReceiver();
                    IntentFilter filter = new IntentFilter(INaturalistService.ACTION_REGISTER_USER_RESULT);
                    BaseFragmentActivity.safeRegisterReceiver(mUserRegisterReceiver, filter, LoginSignupActivity.this);

                    Intent serviceIntent = new Intent(INaturalistService.ACTION_REGISTER_USER, null, LoginSignupActivity.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.EMAIL, mEmail.getText().toString());
                    serviceIntent.putExtra(INaturalistService.USERNAME, mUsername.getText().toString());
                    serviceIntent.putExtra(INaturalistService.PASSWORD, mPassword.getText().toString());
                    serviceIntent.putExtra(INaturalistService.LICENSE, (mUseCCLicense ? "CC-BY-NC" : "on"));
                    ContextCompat.startForegroundService(LoginSignupActivity.this, serviceIntent);

                    mHelper.loading(getString(R.string.registering));
                }
            }
        });


        if (getCurrentFocus() != null) {
            // Hide keyboard
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        ((EditText)findViewById(R.id.hide_focus)).requestFocus();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreateSignInTaskIfNeeded();
                mSignInTask.signIn(INaturalistService.LoginType.GOOGLE, null, null);
            }
        }
    }


    private void checkFields() {
        if (((mEmail.getText().length() == 0) && (mIsSignup)) || (mPassword.getText().length() < (mIsSignup ? 6 : 1)) || (mUsername.getText().length() == 0)) {
            mSignup.setEnabled(false);
        } else {
            mSignup.setEnabled(true);
        }
    }

    public void onBackPressed(){
        if (!mPasswordChanged) {
            mSignInTask.pause();
            setResult(RESULT_CANCELED);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
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


    @Override
    public void onLoginFailed(INaturalistService.LoginType loginType) {
        if ((!mIsSignup) && (isNetworkAvailable()) && (loginType == INaturalistService.LoginType.OAUTH_PASSWORD)) {
            // Invalid password - clear the password field
            mPassword.setText("");
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mUserRegisterReceiver, this);
    }

}

