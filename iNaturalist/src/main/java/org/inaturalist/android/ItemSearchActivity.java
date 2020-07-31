package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.evernote.android.state.State;

import com.livefront.bridge.Bridge;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

    @State public String mSearchString = "";
    @State(AndroidStateBundlers.JSONListBundler.class) public List<JSONObject> mProjects;

    private ProjectsAdapter mAdapter;

    private ProgressBar mProgress;
    private INaturalistApp mApp;
    private EditText mSearchEditText;
    private TextView mNoResults;

@Override
    public void onResume() {
        super.onResume();
        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }
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
        Bridge.restoreInstanceState(this, savedInstanceState);

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

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.taxon_search);

        mNoResults = (TextView) findViewById(android.R.id.empty);
        mNoResults.setVisibility(View.GONE);

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);

        mSearchEditText = (EditText) customView.findViewById(R.id.search_text);

        if (savedInstanceState == null) {
            mAdapter = new ProjectsAdapter(this, mSearchUrl, this, new ArrayList<JSONObject>(), mIsUser ? R.drawable.ic_account_circle_black_48dp : R.drawable.ic_work_black_24dp, mIsUser);
        } else {
            mSearchEditText.setText(mSearchString);
            mAdapter = new ProjectsAdapter(this, mSearchUrl, this, mProjects, mIsUser ? R.drawable.ic_account_circle_black_48dp : R.drawable.ic_work_black_24dp, mIsUser);
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
        mProjects = mAdapter.getItems();

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

}
