package org.inaturalist.shedd.android;

import java.util.HashMap;

import org.inaturalist.shedd.android.R;
import org.inaturalist.shedd.android.INaturalistService.LoginType;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.*;

public class GuideTaxonActivity extends SherlockActivity {
    private static String TAG = "GuideTaxonActivity";
    private static String TAXON_URL = INaturalistService.HOST + "/guide_taxa/";
    private WebView mWebView;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private BetterJSONObject mTaxon;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.web);
        mHelper = new ActivityHelper(this);
        mWebView = (WebView) findViewById(R.id.webview);
        
        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra("taxon");
        } else {
        	mTaxon = (BetterJSONObject) savedInstanceState.getSerializable("taxon");
        }

        actionBar.setTitle(mTaxon.getString("display_name"));

        mWebView.getSettings().setJavaScriptEnabled(true);

        final Activity activity = this;
        mWebView.setWebViewClient(new WebViewClient() {
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mHelper.loading();
            }
            public void onPageFinished(WebView view, String url) {
                mHelper.stopLoading();
            }
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                mHelper.stopLoading();
                mHelper.alert(String.format(getString(R.string.oh_no), description));
            }
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!mApp.loggedIn()) {
                    return false;
                }
                mWebView.loadUrl(url, getAuthHeaders());
                return true;
            }
        });

        loadTaxonPage(mTaxon.getInt("id"));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.guide_taxon_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.add_taxon:
        	// Add a new observation with the specified taxon
        	Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, this, ObservationEditor.class);
        	intent.putExtra(ObservationEditor.SPECIES_GUESS, String.format("%s (%s)", mTaxon.getString("display_name"), mTaxon.getString("name")));
        	startActivity(intent);

        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public HashMap<String,String> getAuthHeaders() {
        HashMap<String,String> headers = new HashMap<String,String>();
        if (!mApp.loggedIn()) {
            return headers;
        }
        if (mApp.getLoginType() == LoginType.PASSWORD) {
            headers.put("Authorization", "Basic " + mApp.getPrefs().getString("credentials", null));
        } else {
            headers.put("Authorization", "Bearer " + mApp.getPrefs().getString("credentials", null));
        }
        return headers;
    }
    
    public void loadTaxonPage(Integer taxonId) {
    	mWebView.getSettings().setUserAgentString(INaturalistService.USER_AGENT);

    	mWebView.loadUrl(TAXON_URL + taxonId.toString() + ".xml", getAuthHeaders());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("taxon", mTaxon);
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

 
}
