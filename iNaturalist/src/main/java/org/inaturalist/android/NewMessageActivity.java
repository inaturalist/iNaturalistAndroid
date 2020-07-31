package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;

public class NewMessageActivity extends AppCompatActivity {
    private static final String TAG = "NewMessageActivity";

    public static final String USER_ID = "user_id";
    public static final String USERNAME = "username";
    public static final String MESSAGE = "message";

    private INaturalistApp mApp;

    private PostMessageReceiver mPostMessageReceiver;

    @State public boolean mSendingMessage;

    @State public String mUsername;
    @State public Integer mUserId;

    private EditText mMessageSubject;
    private EditText mMessageBody;
    private ImageView mSendMessage;
    private ActivityHelper mHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mApp = (INaturalistApp)getApplication();
        mApp.applyLocaleSettings(getBaseContext());
        setContentView(R.layout.new_message);

	    Intent intent = getIntent();
        mUsername = intent.getStringExtra(USERNAME);
        mUserId = intent.getIntExtra(USER_ID, 0);

        ActionBar ab = getSupportActionBar();
        ab.setTitle(String.format(getString(R.string.new_message_to), mUsername));
        ab.setDisplayHomeAsUpEnabled(true);

        mHelper = new ActivityHelper(this);

        mSendMessage = findViewById(R.id.send_message);
        mMessageBody = findViewById(R.id.body);
        mMessageSubject = findViewById(R.id.subject);

        mSendMessage.setOnClickListener(view -> {
            if (mMessageBody.getText().length() == 0) return;
            if (mMessageSubject.getText().length() == 0) return;

            // Send new message to user

            Intent serviceIntent = new Intent(INaturalistService.ACTION_POST_MESSAGE, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TO_USER, mUserId);
            serviceIntent.putExtra(INaturalistService.SUBJECT, mMessageSubject.getText().toString());
            serviceIntent.putExtra(INaturalistService.BODY, mMessageBody.getText().toString());
            ContextCompat.startForegroundService(this, serviceIntent);

            mSendingMessage = true;
            mHelper.loading(getString(R.string.sending_message));
        });
    }

    private void refreshViewState() {
        if (mSendingMessage) {
            mHelper.loading(getString(R.string.sending_message));
        } else {
            mHelper.stopLoading();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mPostMessageReceiver = new PostMessageReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(INaturalistService.ACTION_POST_MESSAGE_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mPostMessageReceiver, filter2, this);

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

        BaseFragmentActivity.safeUnregisterReceiver(mPostMessageReceiver, this);
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

    private class PostMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            Object object = null;
            BetterJSONObject newMessage;
            JSONArray results = null;


            if (isSharedOnApp) {
                object = mApp.getServiceResult(intent.getAction());
            } else {
                object = intent.getSerializableExtra(INaturalistService.ACTION_MESSAGES_RESULT);
            }


            mHelper.stopLoading();
            mSendingMessage = false;

            if (object == null) {
                // Network error of some kind
                Toast.makeText(NewMessageActivity.this, String.format(getString(R.string.could_not_send_message), getString(R.string.not_connected)), Toast.LENGTH_LONG).show();

                return;
            }

            // Messages result
            newMessage = (BetterJSONObject) object;

            String error = newMessage.getString("error");
            if (error != null) {
                // Error sending message
                Toast.makeText(NewMessageActivity.this, String.format(getString(R.string.could_not_send_message), error), Toast.LENGTH_LONG).show();
                return;
            }

            // Return the the newly-created message
            Intent data = new Intent();
            data.putExtra(MESSAGE, newMessage.getJSONObject().toString());
            NewMessageActivity.this.setResult(RESULT_OK, data);
            finish();
        }
    }
}
