package org.inaturalist.android;

import java.util.HashMap;
import java.util.Map;

import org.inaturalist.android.INaturalistService.LoginType;

import com.flurry.android.FlurryAgent;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.*;
import android.widget.Button;
import android.widget.TextView;

public class WebActivity extends BaseFragmentActivity {
    private static String TAG = "WebActivity";
    private static String HOME_URL = "%s/home.mobile";
    private WebView mWebView;
    private INaturalistApp app;
    private ActivityHelper helper;
	private String mHomeUrl;
    private Button mLogin;
    private TextView mNotLoggedIn;

    private static final int REQUEST_CODE_LOGIN = 0x1000;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);

        app = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.web);
        onDrawerCreate(savedInstanceState);
        helper = new ActivityHelper(this);
        mWebView = (WebView) findViewById(R.id.webview);
        
        mLogin = (Button) findViewById(R.id.login);
        mNotLoggedIn = (TextView) findViewById(R.id.not_logged_in);

        mLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(new Intent(WebActivity.this, OnboardingActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), REQUEST_CODE_LOGIN);
            }
        });

        mLogin.setVisibility(View.GONE);
        mNotLoggedIn.setVisibility(View.GONE);

        String inatNetwork = app.getInaturalistNetworkMember();
        String inatHost = app.getStringResourceByName("inat_host_" + inatNetwork);
        mHomeUrl = String.format(HOME_URL, inatHost);

        mWebView.getSettings().setJavaScriptEnabled(true);

        final Activity activity = this;
        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                // Activities and WebViews measure progress with different scales.
                // The progress meter will automatically disappear when we reach 100%
                activity.setProgress(progress * 1000);
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                helper.loading();
            }
            public void onPageFinished(WebView view, String url) {
                helper.stopLoading();
            }
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                helper.stopLoading();
                helper.alert(String.format(getString(R.string.oh_no), description));
            }
            // TODO this works for get requests, but it doesn't intercept POSTs, 
            // so it leads to some weird behavior. Apparently this has been a bug 
            // since 2010, which is pretty disgusting:
            // https://code.google.com/p/android/issues/detail?id=9122
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!app.loggedIn()) {
                    return false;
                }
                mWebView.loadUrl(url, getAuthHeaders());
                return true;
            }
        });
        goHome();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN){
            switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mWebView.canGoBack() == true) {
                    WebBackForwardList webBackForwardList = mWebView.copyBackForwardList();
                    String historyUrl = webBackForwardList.getItemAtIndex(webBackForwardList.getCurrentIndex()-1).getUrl();
                    // weird bug shows a blank page when just going back to the first url in history
                    if (historyUrl.equals(mHomeUrl)) {
                        mWebView.clearHistory();
                        goHome();
                    } else {
                        mWebView.goBack();
                    }
                } else {
                    finish();
                }
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.web_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.reload:
        	if (mWebView != null) mWebView.reload();
            return true;
        case R.id.view:
        	if (mWebView != null) {
        		Intent i = new Intent(Intent.ACTION_VIEW);
        		i.setData(Uri.parse(mWebView.getUrl() != null ? mWebView.getUrl() : mHomeUrl));
        		startActivity(i);
        	}
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public HashMap<String,String> getAuthHeaders() {
        HashMap<String,String> headers = new HashMap<String,String>();
        if (!app.loggedIn()) {
            return headers;
        }
        if (app.getLoginType() == LoginType.PASSWORD) {
            headers.put("Authorization", "Basic " + app.getPrefs().getString("credentials", null));
        } else {
            headers.put("Authorization", "Bearer " + app.getPrefs().getString("credentials", null));
        }
        return headers;
    }
    
    public void goHome() {
        if (app.loggedIn()) {
            mLogin.setVisibility(View.GONE);
            mNotLoggedIn.setVisibility(View.GONE);
            mWebView.setVisibility(View.VISIBLE);

            mWebView.getSettings().setUserAgentString(INaturalistService.USER_AGENT);
            mWebView.loadUrl(mHomeUrl, getAuthHeaders());
        } else {
            mLogin.setVisibility(View.VISIBLE);
            mNotLoggedIn.setVisibility(View.VISIBLE);
            mWebView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // User logged-in - Refresh web view
            mLogin.setVisibility(View.GONE);
            mNotLoggedIn.setVisibility(View.GONE);
            goHome();
        }
    }

}
