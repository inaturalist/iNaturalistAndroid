package org.inaturalist.android;

import java.util.HashMap;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class WebActivity extends Activity {
    private static String TAG = "WebActivity";
    private WebView mWebView;
    private INaturalistApp app;
    private ActivityHelper helper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        app = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.web);
        helper = new ActivityHelper(this);
        mWebView = (WebView) findViewById(R.id.webview);

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
                helper.alert("Oh no! " + description);
            }
        });


        if (app.loggedIn()) {
            Log.d(TAG, "setting auth...");
            HashMap<String,String> headers = new HashMap<String,String>();
            headers.put("Authorization", "Basic " + app.getPrefs().getString("credentials", null));
            mWebView.getSettings().setUserAgentString(INaturalistService.USER_AGENT);
            mWebView.loadUrl(INaturalistService.HOST + "/home", headers);
        } else {
            helper.confirm("You need to log in to view your feed.  Log in now?", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    Intent intent = new Intent(
                            app.currentUserLogin() == null ? "signin" : INaturalistPrefsActivity.REAUTHENTICATE_ACTION, 
                                    null, getBaseContext(), INaturalistPrefsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN){
            switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mWebView.canGoBack() == true) {
                    mWebView.goBack();
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
        case R.id.menu:
            startActivity(new Intent(this, MenuActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return true;
        case R.id.reload:
            mWebView.reload();
            return true;
        case R.id.view:
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(mWebView.getUrl()));
            startActivity(i);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
