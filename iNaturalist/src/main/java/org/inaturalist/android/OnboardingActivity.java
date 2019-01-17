package org.inaturalist.android;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import com.facebook.login.widget.LoginButton;
import com.flurry.android.FlurryAgent;

public class OnboardingActivity extends AppCompatActivity implements SignInTask.SignInTaskStatus {
    private static final int REQUEST_CODE_SIGNUP = 0x1000;
    private static final int REQUEST_CODE_LOGIN = 0x1001;
    private static final int PERMISSIONS_REQUEST = 0x1002;

    public static final String LOGIN = "login";
    public static final String SHOW_SKIP = "show_skip";

    private static String TAG = "OnboardingActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    private ViewFlipper mBackgroundImage;
    private LoginButton mFacebookLoginButton;
    private SharedPreferences mPreferences;
    private SharedPreferences.Editor mPrefEditor;
    private SignInTask mSignInTask;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
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


        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.onboarding);

        Intent intent = getIntent();

        Boolean shouldLogin = false;
        Boolean showSkip = false;
        if (savedInstanceState == null) {
            shouldLogin = intent.getBooleanExtra(LOGIN, false);
            showSkip = intent.getBooleanExtra(SHOW_SKIP, false);
        }

        mHelper = new ActivityHelper(this);
        mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
	    mPrefEditor = mPreferences.edit();

        mBackgroundImage = (ViewFlipper) findViewById(R.id.background_image);
        mBackgroundImage.startFlipping();

        View closeButton = (View) findViewById(R.id.close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        View signUpWithEmail = findViewById(R.id.sign_up_with_email);
        signUpWithEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSignInTask.pause();

                Intent intent = new Intent(OnboardingActivity.this, LoginSignupActivity.class);
                intent.putExtra(LoginSignupActivity.SIGNUP, true);
                intent.putExtra(LoginSignupActivity.BACKGROUND_ID, mBackgroundImage.indexOfChild(mBackgroundImage.getCurrentView()));
                startActivityForResult(intent, REQUEST_CODE_SIGNUP);

                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        TextView signUpWithEmailText = findViewById(R.id.sign_up_with_email_text);
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String networkName = mApp.getStringResourceByName("network_" + inatNetwork);
        signUpWithEmailText.setText(Html.fromHtml(String.format(getString(R.string.new_to_inat_sign_up_now), networkName)));

        View login = (View) findViewById(R.id.login_with_email);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSignInTask.pause();

                Intent intent = new Intent(OnboardingActivity.this, LoginSignupActivity.class);
                intent.putExtra(LoginSignupActivity.SIGNUP, false);
                intent.putExtra(LoginSignupActivity.BACKGROUND_ID, mBackgroundImage.indexOfChild(mBackgroundImage.getCurrentView()));
                startActivityForResult(intent, REQUEST_CODE_LOGIN);

                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        Button skip = (Button) findViewById(R.id.skip);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ONBOARDING_LOGIN_SKIP);
                mSignInTask.pause();
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        if (!showSkip) {
            skip.setVisibility(View.INVISIBLE);
        } else {
            // Don't show both skip and the X (close) buttons together
            closeButton.setVisibility(View.INVISIBLE);
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

                if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.O) &&
                    (ContextCompat.checkSelfPermission(OnboardingActivity.this, android.Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED)) {
                    mHelper.confirm(R.string.just_so_you_know, R.string.ask_for_g_plus_permissions, R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(OnboardingActivity.this, new String[]{android.Manifest.permission.GET_ACCOUNTS}, PERMISSIONS_REQUEST);
                        }
                    });
                } else {
                    mSignInTask.signIn(INaturalistService.LoginType.GOOGLE, null, null);
                }
            }
        });

        mSignInTask = new SignInTask(this, this, mFacebookLoginButton);

        if (shouldLogin) {
            // Show login screen
            login.performClick();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mSignInTask.signIn(INaturalistService.LoginType.GOOGLE, null, null);
            }
        }
    }


    @Override
    public void onLoginSuccessful() {
        // Close this screen
        mSignInTask.pause();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onLoginFailed(INaturalistService.LoginType loginType) {
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mSignInTask.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) || (requestCode == REQUEST_CODE_SIGNUP)) {
            if (resultCode == RESULT_OK) {
                // Successfully registered / logged-in from the sub-activity we've opened
                Intent serviceIntent = new Intent(INaturalistService.ACTION_REFRESH_CURRENT_USER_SETTINGS, null, this, INaturalistService.class);
                ContextCompat.startForegroundService(this, serviceIntent);

                mSignInTask.pause();
                setResult(RESULT_OK);
                finish();
            } else {
                mSignInTask.resume();
            }
        }
    }

    public void onBackPressed(){
        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ONBOARDING_LOGIN_CANCEL);
        mSignInTask.pause();
        setResult(RESULT_CANCELED);
        finish();
    }

}

