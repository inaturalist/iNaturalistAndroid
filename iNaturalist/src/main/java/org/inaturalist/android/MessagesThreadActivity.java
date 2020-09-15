package org.inaturalist.android;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessagesThreadActivity extends AppCompatActivity {
    private static final String TAG = "MessagesThreadActivity";

    public static final String MESSAGE = "message";

    private static final String[] FLAG_TYPES = new String[] { "spam", "inappropriate", "other" };
    private static final int[] FLAG_TITLES = new int[] { R.string.spam, R.string.offensive_inappropriate, R.string.other };
    private static final int[] FLAG_DESCRIPTIONS = new int[] { R.string.commercial_solicitation, R.string.misleading_or_illegal_content, R.string.some_other_reason };

    private INaturalistApp mApp;

    private MessagesReceiver mMessagesReceiver;
    private PostMessageReceiver mPostMessageReceiver;
    private MuteUserReceiver mMuteUserReceiver;
    private PostFlagReceiver mPostFlagReceiver;

    @State(AndroidStateBundlers.JSONListBundler.class) public ArrayList<JSONObject> mMessages;
    @State(AndroidStateBundlers.BetterJSONObjectBundler.class) public BetterJSONObject mMessage;

    @State public boolean mSendingMessage;

    private ProgressBar mProgress;
    private RecyclerView mMessageList;
    private EditText mMessageText;
    private ImageView mSendMessage;
    private MessageThreadAdapter mAdapter;
    private ActivityHelper mHelper;

    @State public boolean mOpenedFromUrl;
    @State public boolean mMutingUser;
    @State public boolean mFlaggingConversation;
    @State public boolean mShowFlagDialog;
    private Menu mMenu;

    private AlertDialog mFlagDialog;
    private int mSelectedFlagType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mApp = (INaturalistApp)getApplication();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.messages_thread);

	    Intent intent = getIntent();

        if (savedInstanceState == null) {
            Uri uri = intent.getData();

            if ((uri != null) && (uri.getScheme().equals("https"))) {
                // User clicked on a message link (e.g. https://www.inaturalist.org/messages/123456)
                String path = uri.getPath();
                Logger.tag(TAG).info("Launched from external URL: " + uri);

                if (path.toLowerCase().startsWith("/messages/")) {
                    Pattern pattern = Pattern.compile("messages/([^ /?]+)");
                    Matcher matcher = pattern.matcher(path);
                    if (matcher.find()) {
                        String id = matcher.group(1);
                        String json = String.format("{ \"id\": %s }", id);
                        mMessage = new BetterJSONObject(json);
                        mOpenedFromUrl = true;
                    } else {
                        Logger.tag(TAG).error("Invalid URL");
                        finish();
                        return;
                    }
                } else {
                    Logger.tag(TAG).error("Invalid URL");
                    finish();
                    return;
                }
            } else {
                mMessage = new BetterJSONObject(intent.getStringExtra(MESSAGE));
            }
        }

        if (mMessage == null) {
            finish();
            return;
        }

        ActionBar ab = getSupportActionBar();
        ab.setTitle(mMessage.getString("subject"));
        ab.setDisplayHomeAsUpEnabled(true);

        mHelper = new ActivityHelper(this);

        mProgress = findViewById(R.id.progress);
        mMessageList = findViewById(R.id.message_list);
        mSendMessage = findViewById(R.id.send_message);
        mMessageText = findViewById(R.id.message);

        mSendMessage.setOnClickListener(view -> {
            if (mMessageText.getText().length() == 0) return;

            // Send message on thread

            // Make sure we send the message to the other user, not to ourselves
            Integer toUser = getOtherUserId();

            Intent serviceIntent = new Intent(INaturalistService.ACTION_POST_MESSAGE, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.TO_USER, toUser);
            serviceIntent.putExtra(INaturalistService.THREAD_ID, mMessage.getInt("thread_id"));
            serviceIntent.putExtra(INaturalistService.SUBJECT, mMessage.getString("subject"));
            serviceIntent.putExtra(INaturalistService.BODY, mMessageText.getText().toString());
            ContextCompat.startForegroundService(this, serviceIntent);

            mSendingMessage = true;
            mHelper.loading(getString(R.string.sending_message));
        });
    }

    private void refreshViewState(boolean refreshMessages, boolean scrollToBottom) {
        if (mMessages == null) {
            // Loading messages
            mProgress.setVisibility(View.VISIBLE);
            mMessageList.setVisibility(View.GONE);
        } else if (mMessages.size() == 0) {
            // No messages
            mProgress.setVisibility(View.GONE);
            mMessageList.setVisibility(View.GONE);
        } else if (refreshMessages) {
            // Show messages
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            mMessageList.setLayoutManager(layoutManager);

            if (mMessageList.getItemDecorationCount() == 0) {
                DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
                dividerItemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider));
                mMessageList.addItemDecoration(dividerItemDecoration);
            }

            mAdapter = new MessageThreadAdapter(this, mMessages);
            mMessageList.setAdapter(mAdapter);

            if (scrollToBottom) {
                // Scroll to latest message (happens when refresh latest message list after sending a new message)
                mMessageList.scrollToPosition(mMessages.size() - 1);
            }

            mProgress.setVisibility(View.GONE);
            mMessageList.setVisibility(View.VISIBLE);
        }

        if (mSendingMessage) {
            mHelper.loading(getString(R.string.sending_message));
        } else if (mMutingUser) {
            mHelper.loading(isUserMuted() ? getString(R.string.unmuting_user) : getString(R.string.muting_user));
        } else if (mFlaggingConversation) {
            mHelper.loading(getString(R.string.flagging_conversation));
        } else {
            mHelper.stopLoading();
            mMessageText.setText("");
        }

        Integer userId = getOtherUserId();

        if ((userId != null) && (mMenu != null)) {
            boolean isMuted = isUserMuted();
            mMenu.getItem(1).setVisible(isMuted);
            mMenu.getItem(2).setTitle(isMuted ? R.string.unmute_this_user : R.string.mute_this_user);

            boolean unresolvedFlag = MessageAdapter.hasUnresolvedFlags(mMessage.getJSONObject());
            mMenu.getItem(0).setVisible(unresolvedFlag);
        }
    }

    private void loadThread() {
        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_MESSAGES, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.MESSAGE_ID, mMessage.getInt("id"));
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private Integer getOtherUserId() {
        if (!mMessage.has("from_user") || !mMessage.has("to_user") || mMessage.isNull("from_user") || mMessage.isNull("to_user")) {
            if (!mMessage.has("from_user_id") || !mMessage.has("to_user_id") || !mMessage.has("user_id")) {
                return null;
            }

            int thisUserId = mMessage.getInt("user_id");

            return mMessage.getInt("from_user_id").equals(thisUserId) ?
                    mMessage.getInt("to_user_id") : mMessage.getInt("from_user_id");
        }

        return mMessage.getJSONObject("from_user").optString("login").equals(mApp.currentUserLogin()) ?
                mMessage.getJSONObject("to_user").optInt("id") : mMessage.getJSONObject("from_user").optInt("id");
    }

    private boolean isUserMuted() {
        int userId = getOtherUserId();
        Set<Integer> mutedUsers = mApp.getMutedUsers();

        return mutedUsers.contains(userId);
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
        BaseFragmentActivity.safeRegisterReceiver(mMessagesReceiver, filter, this);

        mPostMessageReceiver = new PostMessageReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(INaturalistService.ACTION_POST_MESSAGE_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mPostMessageReceiver, filter2, this);

        mMuteUserReceiver = new MuteUserReceiver();
        IntentFilter filter3 = new IntentFilter();
        filter3.addAction(INaturalistService.ACTION_MUTE_USER_RESULT);
        filter3.addAction(INaturalistService.ACTION_UNMUTE_USER_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mMuteUserReceiver, filter3, this);

        mPostFlagReceiver = new PostFlagReceiver();
        IntentFilter filter4 = new IntentFilter();
        filter4.addAction(INaturalistService.ACTION_POST_FLAG_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mPostFlagReceiver, filter4, this);

        if (mMessages == null) {
            loadThread();
        }

        refreshViewState(true, false);

        if (mShowFlagDialog) {
            showFlagDialog();
        }
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
        BaseFragmentActivity.safeUnregisterReceiver(mPostMessageReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mMuteUserReceiver, this);
        BaseFragmentActivity.safeUnregisterReceiver(mPostFlagReceiver, this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.flag_conversation:
                mShowFlagDialog = true;
                showFlagDialog();
                return true;

            case R.id.is_flagged:
                // Open flag URL
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String inatNetwork = mApp.getInaturalistNetworkMember();
                String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
                JSONObject flag = MessageAdapter.getUnresolvedFlag(mMessage.getJSONObject());
                intent.setData(Uri.parse(String.format("%s/flags/%d", inatHost, flag.optInt("id"))));
                startActivity(intent);

                return true;

            case R.id.mute_user:
                if (!isUserMuted()) {
                    // Mute user - show confirmation dialog
                    mHelper.confirm(
                            getString(R.string.mute_this_user_question),
                            getString(R.string.you_will_no_longer_receive_messages),
                            (dialogInterface, i) -> {
                                muteUser();
                            },
                            (dialogInterface, i) -> {
                            },
                            R.string.mute, R.string.cancel);
                } else {
                    // Unmute user - no need to show confirmation dialog
                    muteUser();
                }
                return true;

            case android.R.id.home:
                // Respond to the action bar's Up/Home button
                this.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFlagDialog() {
        // Build the flag dialog - allowing to choose flag type (spam/offensive/other).
        // If other is chosen, change OK button to Continue, and when pressed replace content
        // with a flag explanation edit text.

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Add title
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.dialog_title_top_bar, null, false);

        View titleBar = inflater.inflate(R.layout.dialog_title, null, false);
        ((TextView)titleBar.findViewById(R.id.title)).setText(R.string.flag_conversation);


        // Options
        content.addView(titleBar, 0);
        View options = inflater.inflate(R.layout.flag_conversation, null, false);
        content.addView(options, 1);

        ViewGroup flagExplanationContainer = content.findViewById(R.id.flag_explanation_container);
        EditText flagExplanation = content.findViewById(R.id.flag_explanation);

        flagExplanation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                boolean isEmpty = flagExplanation.getText().toString().length() == 0;
                setFlagDialogOkButton(!isEmpty, R.string.submit);
            }
        });

        flagExplanationContainer.setVisibility(View.GONE);

        RadioGroup flagTypes = content.findViewById(R.id.flag_types);

        List<RadioButton> radioButtons = new ArrayList<RadioButton>();

        for (int i = 0; i < FLAG_TYPES.length; i++) {
            final ViewGroup option = (ViewGroup) inflater.inflate(R.layout.network_option, null, false);

            TextView title = (TextView) option.findViewById(R.id.title);
            TextView subtitle = (TextView) option.findViewById(R.id.sub_title);
            final AppCompatRadioButton radioButton = option.findViewById(R.id.radio_button);

            title.setText(FLAG_TITLES[i]);
            subtitle.setText(FLAG_DESCRIPTIONS[i]);
            radioButton.setId(i);

            // Set radio button color
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{
                            new int[]{-android.R.attr.state_checked},
                            new int[]{android.R.attr.state_checked}
                    },
                    new int[]{
                            Color.DKGRAY, getResources().getColor(R.color.inatapptheme_color)
                    }
            );
            radioButton.setSupportButtonTintList(colorStateList);

            final int index = i;
            option.setOnClickListener(v -> {
                // Uncheck all other radio buttons
                for (int c = 0; c <  radioButtons.size(); c++) {
                    RadioButton r = radioButtons.get(c);
                    r.setChecked(c == index ? true : false);
                }


                setFlagDialogOkButton(true, index == 2 ? R.string.continue_text : R.string.submit);
                mSelectedFlagType = index;
            });

            radioButtons.add(radioButton);
            flagTypes.addView(option);
        }

        // Add buttons
        builder.setView(content);
        builder.setPositiveButton(R.string.submit, null);
        builder.setCancelable(false);
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            mShowFlagDialog = false;
            mFlagDialog.dismiss();
        });

        mFlagDialog = builder.create();
        mFlagDialog.show();
        setFlagDialogOkButton(false, R.string.submit);
        mFlagDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#85B623"));


        mFlagDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (mSelectedFlagType == 2) {
                // Other - show flag explanation EditText
                flagExplanationContainer.setVisibility(View.VISIBLE);
                flagTypes.setVisibility(View.GONE);
                flagExplanation.postDelayed((Runnable) () -> {
                    flagExplanation.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(flagExplanation, InputMethodManager.SHOW_IMPLICIT);
                }, 500);

                setFlagDialogOkButton(false, R.string.submit);
                mSelectedFlagType = -1;
            } else {
                // Spam, Offensive/Inappropriate, etc.
                mFlagDialog.dismiss();
                mShowFlagDialog = false;

                String flag = null;
                switch (mSelectedFlagType) {
                    case 0:
                        flag = "spam";
                        break;
                    case 1:
                        flag = "inappropriate";
                        break;
                    case -1:
                        flag = "other";
                        break;
                }

                // Post new flag
                Intent serviceIntent = new Intent(INaturalistService.ACTION_POST_FLAG, null, this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.FLAGGABLE_TYPE, "Message");
                serviceIntent.putExtra(INaturalistService.FLAGGABLE_ID, mMessage.getInt("thread_id"));
                serviceIntent.putExtra(INaturalistService.FLAG, flag);
                if (mSelectedFlagType == -1) {
                    serviceIntent.putExtra(INaturalistService.FLAG_EXPLANATION, flagExplanation.getText().toString());
                }
                ContextCompat.startForegroundService(this, serviceIntent);

                mFlaggingConversation = true;
                refreshViewState(false, false);
            }
        });
    }

    private void setFlagDialogOkButton(boolean enabled, int textRes) {
        mFlagDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(textRes);
        mFlagDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor(enabled ? "#85B623": "#B9B9B9"));
        mFlagDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
    }

    private void muteUser() {
        // Mute/Unmute user
        Integer user = getOtherUserId();

        Intent serviceIntent = new Intent(isUserMuted() ? INaturalistService.ACTION_UNMUTE_USER : INaturalistService.ACTION_MUTE_USER, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.USER, user);
        ContextCompat.startForegroundService(this, serviceIntent);

        mMutingUser = true;
        refreshViewState(false, false);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        finish();
    }

    private class MessagesReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            int messageId = intent.getIntExtra(INaturalistService.MESSAGE_ID, -1);
            Object object = null;
            BetterJSONObject resultsObject;
            JSONArray results = null;

            if (messageId != mMessage.getInt("id")) {
                // Older results (for another message thread)
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
                refreshViewState(true, false);
                return;
            }

            // Messages result
            resultsObject = (BetterJSONObject) object;
            results = resultsObject.getJSONArray("results").getJSONArray();

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            if (results == null) {
                mMessages = new ArrayList<>();
                refreshViewState(true, false);
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

            if (mOpenedFromUrl) {
                // This activity was opened from a URL, which means we don't have any other info other than the message ID.
                // So now we'll replace the almost-empty message object with a full-fledged one from the results.
                JSONObject message = CollectionUtils.find(mMessages, msg -> msg.optInt("id") == mMessage.getInt("id").intValue());
                mMessage = new BetterJSONObject(message);
                mOpenedFromUrl = false;

                ActionBar ab = getSupportActionBar();
                ab.setTitle(mMessage.getString("subject"));
            }

            mSendingMessage = false;
            refreshViewState(true, true);
        }
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

            if (object == null) {
                // Network error of some kind
                Toast.makeText(MessagesThreadActivity.this, String.format(getString(R.string.could_not_send_message), getString(R.string.not_connected)), Toast.LENGTH_LONG).show();

                mHelper.stopLoading();
                mSendingMessage = false;
                return;
            }

            // Messages result
            newMessage = (BetterJSONObject) object;

            String error = newMessage.getString("error");
            if (error != null) {
                // Error sending message
                Toast.makeText(MessagesThreadActivity.this, String.format(getString(R.string.could_not_send_message), error), Toast.LENGTH_LONG).show();

                mHelper.stopLoading();
                mSendingMessage = false;
                return;
            }

            if (!newMessage.getInt("thread_id").equals(mMessage.getInt("thread_id"))) {
                // Older result (for another message thread)
                return;
            }

            // Next, refresh the message list, as the message returned by the API doesn't contain enough information
            loadThread();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.message_thread_menu, menu);
        mMenu = menu;

        // Can't detect clicks directly (since we use a custom action view)
        mMenu.getItem(0).getActionView().setOnClickListener(view -> MessagesThreadActivity.this.onOptionsItemSelected(mMenu.getItem(0)));

        refreshViewState(false, true);

        return true;
    }


    private class MuteUserReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(INaturalistService.SUCCESS, false);

            if (!success) {
                mHelper.stopLoading();
                Toast.makeText(MessagesThreadActivity.this, isUserMuted() ? R.string.failed_unmuting_user : R.string.failed_muting_user, Toast.LENGTH_LONG).show();
                return;
            }

            Integer userId = getOtherUserId();
            Set<Integer> muted = mApp.getMutedUsers();

            if (isUserMuted()) {
                // Mark as unmuted
                muted.remove(userId);

            } else {
                // Mark as muted
                muted.add(userId);
            }

            mApp.setMutedUsers(muted);

            mMutingUser = false;
            refreshViewState(false, false);
        }
    }

    private class PostFlagReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(INaturalistService.SUCCESS, false);

            mHelper.stopLoading();

            if (!success) {
                Toast.makeText(MessagesThreadActivity.this, R.string.failed_flagging_conversation, Toast.LENGTH_LONG).show();
                return;
            }

            mFlaggingConversation = false;
            Toast.makeText(MessagesThreadActivity.this, R.string.thank_you_for_submitting_the_flag, Toast.LENGTH_LONG).show();
        }
    }
}
