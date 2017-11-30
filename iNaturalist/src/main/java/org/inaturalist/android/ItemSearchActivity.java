package org.inaturalist.android;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.projection.MediaProjection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemSearchActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, ProjectsAdapter.OnLoading {
    private static final String LOG_TAG = "ItemSearchActivity";

    public static final String IS_USER = "is_user";
    public static final String RETURN_RESULT = "return_result";
    public static final String RESULT_VIEWER_ACTIVITY = "result_viewer_activity";
    public static final String RESULT_VIEWER_ACTIVITY_PARAM_NAME = "result_viewer_activity_param_name";
    public static final String SEARCH_HINT_TEXT = "search_hint_text";
    public static final String SEARCH_URL = "search_url";
    public static final String RESULT = "result";

    private Class<Activity> mViewerActivity;
    private String mViewerActivityParamName;
    private String mHintText;
    private String mSearchUrl;
    private boolean mReturnResult;
    private boolean mIsUser;

    private String mSearchString = "";

    private ProjectsAdapter mAdapter;

    private ProgressBar mProgress;
    private INaturalistApp mApp;
    private EditText mSearchEditText;
    private TextView mNoResults;

    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}
    @Override
    public void onResume() {
        super.onResume();
        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }
    }
 
	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}	

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

   @Override
   public void onBackPressed() {
       setResult(RESULT_CANCELED);
       finish();
   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }


        final Intent intent = getIntent();
        mViewerActivity = (Class<Activity>) intent.getSerializableExtra(RESULT_VIEWER_ACTIVITY);
        mViewerActivityParamName = intent.getStringExtra(RESULT_VIEWER_ACTIVITY_PARAM_NAME);
        mHintText = intent.getStringExtra(SEARCH_HINT_TEXT);
        mSearchUrl = intent.getStringExtra(SEARCH_URL);
        mReturnResult = intent.getBooleanExtra(RETURN_RESULT, false);
        mIsUser = intent.getBooleanExtra(IS_USER, false);


        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.taxon_search_action_bar, null);
        actionBar.setCustomView(customView);
        actionBar.setLogo(R.drawable.ic_arrow_back);
       
        setContentView(R.layout.taxon_search);

        mNoResults = (TextView) findViewById(android.R.id.empty);
        mNoResults.setVisibility(View.GONE);

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);

        mSearchEditText = (EditText) customView.findViewById(R.id.search_text);

        if (savedInstanceState == null) {
            mAdapter = new ProjectsAdapter(this, mSearchUrl, this, new ArrayList<JSONObject>(), mIsUser ? R.drawable.ic_account_circle_black_48dp : R.drawable.ic_work_black_24dp, mIsUser);
        } else {
            mSearchString = savedInstanceState.getString("mSearchString");
            mSearchEditText.setText(mSearchString);
            mAdapter = new ProjectsAdapter(this, mSearchUrl, this, loadListFromBundle(savedInstanceState, "mProjects"), mIsUser ? R.drawable.ic_account_circle_black_48dp : R.drawable.ic_work_black_24dp, mIsUser);
        }
        if (mHintText != null) mSearchEditText.setHint(mHintText);

        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence s, int start, int before, int count) {
                if (!s.toString().equals(mSearchString)) {
                    mSearchString = s.toString();
                    if (mAdapter != null) ((Filterable) mAdapter).getFilter().filter(s);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        setListAdapter(mAdapter);
        getListView().setOnItemClickListener(this);
    }

    public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
        String item = (String) v.getTag();
        if (item != null) {
            if (mReturnResult) {
                // Return the selected result instead of opening up a viewer
                Intent data = new Intent();
                data.putExtra(RESULT, item);
                setResult(RESULT_OK, data);
                finish();
            } else {
                Intent intent = new Intent(this, mViewerActivity);
                intent.putExtra(mViewerActivityParamName, item);
                startActivity(intent);
            }
        }


    }

    private boolean isNetworkAvailable() {
         ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
         NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
         return activeNetworkInfo != null && activeNetworkInfo.isConnected();
     }


    private ListView mListView;

    protected ListView getListView() {
        if (mListView == null) {
            mListView = (ListView) findViewById(android.R.id.list);
        }
        return mListView;
    }

    protected void setListAdapter(ListAdapter adapter) {
        getListView().setAdapter(adapter);
    }


    @Override
    public void onLoading(final Boolean isLoading, final int count) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isLoading) {
                    getListView().setVisibility(View.GONE);
                    mProgress.setVisibility(View.VISIBLE);
                    mNoResults.setVisibility(View.GONE);
                } else {
                    mProgress.setVisibility(View.GONE);
                    getListView().setVisibility(View.VISIBLE);

                    if (count == 0) {
                        mNoResults.setVisibility(View.VISIBLE);
                    } else {
                        mNoResults.setVisibility(View.GONE);
                    }
                }
            }
        });
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveListToBundle(outState, mAdapter.getItems(), "mProjects");
        outState.putString("mSearchString", mSearchString);

        super.onSaveInstanceState(outState);
    }
    private void saveListToBundle(Bundle outState, List<JSONObject> list, String key) {
        if (list != null) {
            JSONArray arr = new JSONArray(list);
            outState.putString(key, arr.toString());
        }
    }

    private List<JSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        List<JSONObject> results = new ArrayList<JSONObject>();

        String obsString = savedInstanceState.getString(key);
        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(arr.getJSONObject(i));
                }

                return results;
            } catch (JSONException exc) {
                exc.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }
}
