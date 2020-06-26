package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;

public class NewMessageSelectUserActivity extends AppCompatActivity implements UserAdapter.UserClickListener {
    private static final String TAG = "NewMessageSelectUserActivity";

    private static final int NEW_MESSAGE_REQUEST_CODE = 0x1000;

    private INaturalistApp mApp;

    private UserSearchReceiver mUserSearchReceiver;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mUsers;

    private ProgressBar mProgress;
    private TextView mNoUsers;
    private RecyclerView mUserList;
    private EditText mSearch;
    private UserAdapter mAdapter;

    @State public String mCurrentSearchString = "";
    @State public long mStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        setContentView(R.layout.new_message_select_user);

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setElevation(0);
        ab.setTitle(R.string.new_message);
        ab.setDisplayHomeAsUpEnabled(true);

        mApp = (INaturalistApp)getApplication();

        mProgress = findViewById(R.id.progress);
        mNoUsers = findViewById(R.id.no_users_found);
        mUserList = findViewById(R.id.user_list);
        mSearch = findViewById(R.id.search);

        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                // Don't start searching immediately - wait for user to stop typing
                if (mCurrentSearchString.equals(s.toString())) return;

                mCurrentSearchString = s.toString();
                mStartTime = System.currentTimeMillis();

                mSearch.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                        mCurrentSearchString.length() > 0 ? R.drawable.bs_ic_clear_light : R.drawable.ic_fa_search,
                        0);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (System.currentTimeMillis() - mStartTime > 500) {
                            searchUsers();
                        }
                    }
                }, 600);
            }
        });

        // Handle clearing of the search field (by clicking on the right drawable)
        mSearch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;

                if (mCurrentSearchString.length() == 0) return false;

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (mSearch.getRight() - mSearch.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // Clear search
                        mSearch.setText("");
                        return true;
                    }
                }
                return false;
            }
        });

        mSearch.requestFocus();
    }

    private void searchUsers() {
        mUsers = null;

        if (mCurrentSearchString.length() == 0) {
            refreshViewState();
            return;
        }

        Intent serviceIntent = new Intent(INaturalistService.ACTION_SEARCH_USERS, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.QUERY, mCurrentSearchString);
        serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, 1);
        ContextCompat.startForegroundService(this, serviceIntent);

        refreshViewState();
    }

    private void refreshViewState() {
        if (mCurrentSearchString.length() == 0) {
            // No active search
            mProgress.setVisibility(View.GONE);
            mNoUsers.setVisibility(View.GONE);
            mUserList.setVisibility(View.GONE);
        } else if (mUsers == null) {
            // Loading users
            mProgress.setVisibility(View.VISIBLE);
            mNoUsers.setVisibility(View.GONE);
            mUserList.setVisibility(View.GONE);
        } else if (mUsers.size() == 0) {
            // No users found
            mProgress.setVisibility(View.GONE);
            mNoUsers.setVisibility(View.VISIBLE);
            mUserList.setVisibility(View.GONE);
        } else {
            // Show user results
            mUserList.setHasFixedSize(true);

            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            mUserList.setLayoutManager(layoutManager);

            DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
            dividerItemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider));
            mUserList.addItemDecoration(dividerItemDecoration);

            mAdapter = new UserAdapter(mUsers, this);
            mUserList.setAdapter(mAdapter);

            mProgress.setVisibility(View.GONE);
            mNoUsers.setVisibility(View.GONE);
            mUserList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mUserSearchReceiver = new UserSearchReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.SEARCH_USERS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mUserSearchReceiver, filter, this);

        if (mUsers == null) {
            searchUsers();
        }

        refreshViewState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mUserSearchReceiver, this);
    }

    @Override
    public void onClick(JSONObject user, int position) {
        Intent intent = new Intent(this, NewMessageActivity.class);
        intent.putExtra(NewMessageActivity.USER_ID, user.optInt("id"));
        intent.putExtra(NewMessageActivity.USERNAME, user.optString("login"));
        startActivityForResult(intent, NEW_MESSAGE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NEW_MESSAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // User sent a new message - close this screen and return the newly-created message
                String message = data.getStringExtra(NewMessageActivity.MESSAGE);

                Intent retData = new Intent();
                retData.putExtra(NewMessageActivity.MESSAGE, message);
                setResult(RESULT_OK, retData);
                finish();
            }
        }
    }

    private class UserSearchReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            String error = extras.getString("error");
            if (error != null) {
                mUsers = new ArrayList<>();
                refreshViewState();
                return;
            }

            String query = intent.getStringExtra(INaturalistService.QUERY);
            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            Object object = null;
            BetterJSONObject resultsObject;
            JSONArray results = null;

            if (((query != null) && (!query.equals(mCurrentSearchString))) || ((query == null) && mCurrentSearchString.length() > 0)) {
                // Older results (for previous search query)
                return;
            }

            if (isSharedOnApp) {
                object = mApp.getServiceResult(intent.getAction());
            } else {
                object = intent.getSerializableExtra(INaturalistService.ACTION_MESSAGES_RESULT);
            }

            if (object == null) {
                // Network error of some kind
                mUsers = new ArrayList<>();
                refreshViewState();
                return;
            }

            // Messages result
            resultsObject = (BetterJSONObject) object;
            results = resultsObject.getJSONArray("results").getJSONArray();

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            if (results == null) {
                mUsers = new ArrayList<>();
                refreshViewState();
                return;
            }

            for (int i = 0; i < results.length(); i++) {
                try {
                    JSONObject item = results.getJSONObject(i);
                    resultsArray.add(item);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                }
            }

            mUsers = resultsArray;

            refreshViewState();
        }
    }

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
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
