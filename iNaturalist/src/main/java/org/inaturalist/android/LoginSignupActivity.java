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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;

import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.FacebookSdk;
import com.facebook.login.widget.LoginButton;

import java.util.regex.Pattern;


public class LoginSignupActivity extends AppCompatActivity implements SignInTask.SignInTaskStatus {

    private static final int PERMISSIONS_REQUEST = 0x1000;

    private static String TAG = "LoginSignupActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private ImageView mBackgroundImage;

    private static final Pattern USERNAME_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z0-9_\\-]*");

    public static final String BACKGROUND_ID = "background_id";
    public static final String SIGNUP = "signup";
    public static final String VERIFY_PASSWORD = "verify_password";
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
    private TextView mCheckboxDescription2;
    private TextView mCheckboxDescription3;
    private CheckBox mCheckbox;
    private CheckBox mCheckbox2;
    private CheckBox mCheckbox3;
    private boolean mUseCCLicense;
    private boolean mUsePersonalInfo;
    private boolean mAgreeTOS;
    private Button mSignup;
    private boolean mIsSignup;
    private SignInTask mSignInTask;
    private LoginButton mFacebookLoginButton;

    private UserRegisterReceiver mUserRegisterReceiver;
    private TextView mTerms;
    private boolean mPasswordChanged;
    private boolean mVerifyPassword;

    @Override
    protected void onStart() {
        super.onStart();


    }

    @Override
    protected void onStop() {
        super.onStop();

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
                mSignInTask.signIn(INaturalistServiceImplementation.LoginType.OAUTH_PASSWORD, mUsername.getText().toString(), mPassword.getText().toString());
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_CREATE_ACCOUNT);
            }
        }
    }

    // Recreates a new instance of the sign in task if it finished running before (since an AsyncTask can only be run once).
    private void recreateSignInTaskIfNeeded() {
        if (mSignInTask.getStatus() == AsyncTask.Status.FINISHED) {
            mSignInTask = new SignInTask(this, this, mFacebookLoginButton, mVerifyPassword);
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
        mApp.applyLocaleSettings(getBaseContext());
        setContentView(R.layout.login_signup);

        mHelper = new ActivityHelper(this);

        mBackgroundImage = (ImageView) findViewById(R.id.background_image);

        int backgroundId = getIntent().getIntExtra(BACKGROUND_ID, 0);
        mIsSignup = getIntent().getBooleanExtra(SIGNUP, false);
        mPasswordChanged = getIntent().getBooleanExtra(PASSWORD_CHANGED, false);
        mVerifyPassword = getIntent().getBooleanExtra(VERIFY_PASSWORD, false);

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
        HtmlUtils.fromHtml(mCheckboxDescription, getString(R.string.use_my_license));
        mCheckboxDescription.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.alert(R.string.content_licensing, R.string.content_licensing_description);
            }
        });

        mCheckboxDescription2 = (TextView) findViewById(R.id.checkbox_description2);
        mCheckboxDescription2.setText(Html.fromHtml(mCheckboxDescription2.getText().toString()));
        mCheckboxDescription2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.alert(R.string.about_personal_information, R.string.personal_information_notes);
            }
        });

        mCheckboxDescription3 = (TextView) findViewById(R.id.checkbox_description3);
        HtmlUtils.fromHtml(mCheckboxDescription3, mCheckboxDescription3.getText().toString());

        mTerms = (TextView) findViewById(R.id.terms);
        HtmlUtils.fromHtml(mTerms, mTerms.getText().toString());

        mCheckbox = findViewById(R.id.checkbox);
        mCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mUseCCLicense = isChecked;
            }
        });

        mCheckbox2 = findViewById(R.id.checkbox2);
        mCheckbox2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mUsePersonalInfo = isChecked;
                checkFields();
            }
        });

        mCheckbox3 = findViewById(R.id.checkbox3);
        mCheckbox3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mAgreeTOS = isChecked;
                checkFields();
            }
        });


        mSignup = (Button) findViewById(R.id.sign_up);
        mSignup.setEnabled(false);

        if (!mIsSignup) {
            TextView title = (TextView) findViewById(R.id.action_bar_title);
            title.setText(mVerifyPassword ? R.string.verify_your_password : R.string.log_in);
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
        
        View loginWithFacebook = findViewById(R.id.login_with_facebook);
        loginWithFacebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FacebookSdk.setApplicationId(getString(R.string.facebook_app_id));
                FacebookSdk.sdkInitialize(getApplicationContext());
                mFacebookLoginButton = new LoginButton(LoginSignupActivity.this);
                mFacebookLoginButton.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));

                mSignInTask = new SignInTask(LoginSignupActivity.this, LoginSignupActivity.this, mFacebookLoginButton, mVerifyPassword);
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
                        (ContextCompat.checkSelfPermission(LoginSignupActivity.this, android.Manifest.permission.GET_ACCOUNTS) != PermissionChecker.PERMISSION_GRANTED)) {
                    mHelper.confirm(R.string.just_so_you_know, R.string.ask_for_g_plus_permissions, R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(LoginSignupActivity.this, new String[]{android.Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST);
                        }
                    });
                } else {
                    recreateSignInTaskIfNeeded();
                    mSignInTask.signIn(INaturalistServiceImplementation.LoginType.GOOGLE, null, null);
                }
            }
        });

        mSignInTask = new SignInTask(this, this, null, mVerifyPassword);

        mSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsSignup) {
                    // Login
                    recreateSignInTaskIfNeeded();
                    mSignInTask.signIn(INaturalistServiceImplementation.LoginType.OAUTH_PASSWORD, mUsername.getText().toString().trim(), mPassword.getText().toString());
                } else {
                    // Sign up
                    String username = mUsername.getText().toString();
                    String email = mEmail.getText().toString();
                    String password = mPassword.getText().toString();

                    if (password.length() < 6) {
                        mHelper.alert(R.string.could_not_register_user, R.string.password_too_short);
                        return;
                    } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        mHelper.alert(R.string.could_not_register_user, R.string.email_must_look_like_email_address);
                        return;
                    } else if (!USERNAME_REGEX.matcher(username).matches()) {
                        mHelper.alert(R.string.could_not_register_user, R.string.username_must_begin_with);
                        return;
                    }

                    mUserRegisterReceiver = new UserRegisterReceiver();
                    IntentFilter filter = new IntentFilter(INaturalistService.ACTION_REGISTER_USER_RESULT);
                    BaseFragmentActivity.safeRegisterReceiver(mUserRegisterReceiver, filter, LoginSignupActivity.this);

                    Intent serviceIntent = new Intent(INaturalistService.ACTION_REGISTER_USER, null, LoginSignupActivity.this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.EMAIL, mEmail.getText().toString());
                    serviceIntent.putExtra(INaturalistService.USERNAME, mUsername.getText().toString());
                    serviceIntent.putExtra(INaturalistService.PASSWORD, mPassword.getText().toString());
                    serviceIntent.putExtra(INaturalistService.LICENSE, (mUseCCLicense ? "CC-BY-NC" : "on"));
                    INaturalistService.callService(LoginSignupActivity.this, serviceIntent);

                    mHelper.loading(getString(R.string.registering));
                }
            }
        });

        if (mVerifyPassword) {
            // Pre-fill email address
            mUsername.setText(mApp.currentUserLogin());
            mUsername.setEnabled(false);
        }

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
            if (grantResults.length > 0 && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
                recreateSignInTaskIfNeeded();
                mSignInTask.signIn(INaturalistServiceImplementation.LoginType.GOOGLE, null, null);
            }
        }
    }


    private void checkFields() {
        if (((mEmail.getText().length() == 0) && (mIsSignup)) || (mPassword.getText().length() < 1) || (mUsername.getText().length() == 0)) {
            mSignup.setEnabled(false);
        } else {
            mSignup.setEnabled(!mIsSignup || (mAgreeTOS && mUsePersonalInfo));
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
    public void onLoginFailed(INaturalistServiceImplementation.LoginType loginType) {
        if ((!mIsSignup) && (isNetworkAvailable()) && (loginType == INaturalistServiceImplementation.LoginType.OAUTH_PASSWORD)) {
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

