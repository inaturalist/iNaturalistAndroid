package org.inaturalist.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.text.util.Linkify;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

public class NewsArticle extends AppCompatActivity {
    public static final String KEY_ARTICLE = "article";
    public static final String KEY_IS_USER_FEED = "is_user_feed";
    private static final String TAG = "NewsArticle";

    private INaturalistApp mApp;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mArticle;

    private ActivityHelper mHelper;

    private TextView mArticleTitle;
    private WebView mArticleContentWeb;
    private TextView mArticleContent;
    private TextView mUsername;
    private ImageView mUserPic;
    @State public boolean mIsUserFeed;



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
        	this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    } 
 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        setContentView(R.layout.article);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);
        actionBar.setTitle(R.string.article);

        mArticleTitle = (TextView) findViewById(R.id.article_title);
        mArticleContentWeb = (WebView) findViewById(R.id.article_content_web);
        mArticleContent = (TextView) findViewById(R.id.article_content);
        mUsername = (TextView) findViewById(R.id.username);
        mUserPic = (ImageView) findViewById(R.id.user_pic);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }
        
        if (savedInstanceState == null) {
            mArticle = (BetterJSONObject) intent.getSerializableExtra(KEY_ARTICLE);
            mIsUserFeed = intent.getBooleanExtra(KEY_IS_USER_FEED, false);
        }


        if (mArticle == null) {
            finish();
            return;
        }

        mArticleTitle.setText(mArticle.getString("title"));

        if (mIsUserFeed) {
            mArticleContent.setVisibility(View.GONE);
            mArticleContentWeb.setVisibility(View.VISIBLE);
            mArticleContentWeb.setBackgroundColor(Color.TRANSPARENT);
            mArticleContentWeb.getSettings().setJavaScriptEnabled(true);
            mArticleContentWeb.setVerticalScrollBarEnabled(false);

            String html = "" +
                "<html>" +
                    "<head>" +
                        "<style type=\"text/css\"> " +
                        "@font-face { " +
                            "font-family: Whitney;" +
                            "src: url(\"file:///android_asset/fonts/whitney_light_pro.otf\")" +
                        "}" +
                        "body {" +
                                "line-height: 22pt;" +
                                "margin: 0;" +
                                "padding: 0;" +
                                "font-family: \"HelveticaNeue-UltraLight\", \"Segoe UI\", \"Roboto Light\", sans-serif;" +
                                "font-size: medium;" +
                            "} " +
                            "div {max-width: 100%;} " +
                            "figure { padding: 0; margin: 0; } " +
                            "img { padding-top: 4; padding-bottom: 4; max-width: 100%; } " +
                        "</style>" +
                        "<meta name=\"viewport\" content=\"user-scalable=no, initial-scale=1.0, maximum-scale=1.0, width=device-width\" >" +
                    "</head>" +
                "<body>";
            mArticleContentWeb.loadDataWithBaseURL("", html + mArticle.getString("body") + "</body></html>", "text/html", "UTF-8", "");
        } else {
            mArticleContentWeb.setVisibility(View.GONE);
            mArticleContent.setVisibility(View.VISIBLE);
            String article = mArticle.getString("body").replace("\n", "<br/>");
            HtmlUtils.fromHtml(mArticleContent, article);
        }

        final JSONObject user = mArticle.getJSONObject("user");
        mUsername.setText(user.optString("login"));

        // Intercept link clicks (for analytics events)

        WebViewClient webClient = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView  view, String  url){
                try {
                    JSONObject eventParams = new JSONObject();
                    JSONObject item = mArticle.getJSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_LINK, url);
                    eventParams.put(AnalyticsClient.EVENT_PARAM_ARTICLE_TITLE, item.optString("title", ""));
                    eventParams.put(AnalyticsClient.EVENT_PARAM_PARENT_TYPE, item.optString("parent_type", ""));
                    JSONObject parent = item.optJSONObject("parent");
                    if (parent == null) parent = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_PARENT_NAME, parent.optString("title", parent.optString("name", "")));

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEWS_TAP_LINK, eventParams);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }

                Uri uri = Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                NewsArticle.this.startActivity(intent);
                return true;
            }
        };

        mArticleContentWeb.setWebViewClient(webClient);


        View.OnClickListener showUser = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(NewsArticle.this, UserProfile.class);
                intent.putExtra("user", new BetterJSONObject(user));
                startActivity(intent);
            }
        };


        if (user.has("user_icon_url") && !user.isNull("user_icon_url")) {
            UrlImageViewHelper.setUrlDrawable(mUserPic, user.optString("user_icon_url"), R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    // Nothing to do here
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    // Return a circular version of the profile picture
                    return ImageUtils.getCircleBitmap(loadedBitmap);
                }
            });
        }

        mUserPic.setOnClickListener(showUser);
        mUsername.setOnClickListener(showUser);

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

}
