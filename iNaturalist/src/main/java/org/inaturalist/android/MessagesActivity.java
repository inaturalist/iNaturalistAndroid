package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;
import com.melnykov.fab.FloatingActionButton;

import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class MessagesActivity extends BaseFragmentActivity implements MessageAdapter.MessageClickListener {
    private static final String TAG = "MessagesActivity";

    private static final int VIEW_MESSAGE_THREAD_REQUEST_CODE = 0x1000;
    private static final int NEW_MESSAGE_THREAD_REQUEST_CODE = 0x1001;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;

    private MessagesReceiver mMessagesReceiver;
    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mMessages;

    private ProgressBar mProgress;
    private TextView mNoMessages;
    private RecyclerView mMessageList;
    private EditText mSearch;
    private MessageAdapter mAdapter;

    @State public String mCurrentSearchString = "";
    @State public long mStartTime;
    @State public int mLastMessageViewedPosition;
    private FloatingActionButton mNewMessage;
    private SwipeRefreshLayout mListSwipeContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mApp = (INaturalistApp)getApplication();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.messages);
	    onDrawerCreate(savedInstanceState);

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setElevation(0);

        mHelper = new ActivityHelper(this);

        mProgress = findViewById(R.id.progress);
        mNoMessages = findViewById(R.id.no_messages);
        mMessageList = findViewById(R.id.message_list);
        mSearch = findViewById(R.id.search);
        mNewMessage = findViewById(R.id.new_message);
        mListSwipeContainer = findViewById(R.id.message_list_swipe);

        // Pull-to-refresh messages
        mListSwipeContainer.setOnRefreshListener(() -> searchMessages());

        mNewMessage.setOnClickListener(view -> {
            Set<String> privileges = mApp.getUserPrivileges();

            if (!privileges.contains("speech")) {
                mHelper.alert(R.string.sorry, R.string.you_must_have_three_observations);
                return;
            }

            Intent intent = new Intent(MessagesActivity.this, NewMessageSelectUserActivity.class);
            startActivityForResult(intent, NEW_MESSAGE_THREAD_REQUEST_CODE);
        });

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
                            searchMessages();
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
    }

    private void searchMessages() {
        mMessages = null;

        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_MESSAGES, null, this, INaturalistService.class);
        if (mCurrentSearchString.length() > 0) {
            serviceIntent.putExtra(INaturalistService.QUERY, mCurrentSearchString);
            serviceIntent.putExtra(INaturalistService.GROUP_BY_THREADS, false);
        } else {
            serviceIntent.putExtra(INaturalistService.GROUP_BY_THREADS, true);
        }

        serviceIntent.putExtra(INaturalistService.BOX, "any");
        ContextCompat.startForegroundService(this, serviceIntent);
        refreshUserDetails();

        refreshViewState();
    }

    private void refreshViewState() {
        if (mMessages == null) {
            // Loading messages
            if (!mListSwipeContainer.isRefreshing()) {
                mProgress.setVisibility(View.VISIBLE);
                mNoMessages.setVisibility(View.GONE);
                mMessageList.setVisibility(View.GONE);
            }
        } else if (mMessages.size() == 0) {
            // No messages
            mProgress.setVisibility(View.GONE);
            mNoMessages.setVisibility(View.VISIBLE);
            mMessageList.setVisibility(View.GONE);
            mNoMessages.setText(mCurrentSearchString.length() > 0 ? R.string.no_messages_found : R.string.no_messages);
            mListSwipeContainer.setRefreshing(false);
        } else {
            // Show messages
            mMessageList.setHasFixedSize(true);

            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            mMessageList.setLayoutManager(layoutManager);

            if (mMessageList.getItemDecorationCount() == 0) {
                DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
                dividerItemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider));
                mMessageList.addItemDecoration(dividerItemDecoration);
            }

            mAdapter = new MessageAdapter(this, mMessages, this);
            mMessageList.setAdapter(mAdapter);

            mProgress.setVisibility(View.GONE);
            mNoMessages.setVisibility(View.GONE);
            mMessageList.setVisibility(View.VISIBLE);
            mListSwipeContainer.setRefreshing(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mMessagesReceiver = new MessagesReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.ACTION_MESSAGES_RESULT);
        safeRegisterReceiver(mMessagesReceiver, filter);

        if (mMessages == null) {
            searchMessages();
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

        BaseFragmentActivity.safeUnregisterReceiver(mMessagesReceiver, this);
    }

    @Override
    public void onClick(JSONObject message, int position) {
        Intent intent = new Intent(this, MessagesThreadActivity.class);
        intent.putExtra(MessagesThreadActivity.MESSAGE, message.toString());
        mLastMessageViewedPosition = position;
        startActivityForResult(intent, VIEW_MESSAGE_THREAD_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VIEW_MESSAGE_THREAD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                if (mMessages == null) return;

                // Viewed a message thread - mark as read locally (without needing to refresh from server)
                // Mark as unread all messages in the same thread
                try {
                    int threadId = mMessages.get(mLastMessageViewedPosition).getInt("thread_id");

                    for (JSONObject message : mMessages) {
                        if (message.optInt("thread_id") != threadId) continue;

                        boolean isUnread = message.isNull("read_at");

                        if (isUnread) {
                            SharedPreferences.Editor editor = mApp.getPrefs().edit();
                            int preUnreadCount = mApp.getPrefs().getInt("user_unread_messages", 0);
                            editor.putInt("user_unread_messages", preUnreadCount - 1);
                            editor.commit();

                            message.put("read_at", "true");
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (mAdapter != null) mAdapter.notifyDataSetChanged();
            }
        } else if (requestCode == NEW_MESSAGE_THREAD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // User sent a new message - open the message thread screen
                String message = data.getStringExtra(NewMessageActivity.MESSAGE);

                Intent intent = new Intent(this, MessagesThreadActivity.class);
                intent.putExtra(MessagesThreadActivity.MESSAGE, message);
                startActivity(intent);
            }
        }
    }

    private class MessagesReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

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
                mMessages = new ArrayList<>();
                refreshViewState();
                return;
            }

            // Messages result
            resultsObject = (BetterJSONObject) object;
            SerializableJSONArray arr = resultsObject.getJSONArray("results");

            if (arr == null) {
                mMessages = new ArrayList<>();
                refreshViewState();
                return;
            }

            results = arr.getJSONArray();

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            if (results == null) {
                mMessages = new ArrayList<>();
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

            mMessages = resultsArray;

            refreshViewState();
        }
    }
}
