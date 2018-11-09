package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Adds mentions capabilities to an EditText */
public class MentionsAutoComplete implements TextWatcher, AdapterView.OnItemClickListener {
    private final Handler mHandler;
    private final Context mContext;
    private final INaturalistApp mApp;
    private final UserSearchReceiver mUserResultsReceiver;
    private final PopupWindow mPopupWindow;
    private final ListView mMentionsList;
    private final ActivityHelper mHelper;
    private EditText mEditText;
    private String mLastSearch = null;

    // The character used to trigger the mentions popup list
    private static final char MENTION_CHAR = '@';
    // What characters are valid for a username mention
    private static final String VALID_MENTION_CHARS = "[\\-a-zA-Z0-9]";

    private ArrayList<JSONObject> mResults = null;

    public MentionsAutoComplete(Context context, EditText editText) {
        mEditText = editText;
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();

        mEditText.addTextChangedListener(this);

        mHandler = new Handler();


        mUserResultsReceiver = new UserSearchReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.SEARCH_USERS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mUserResultsReceiver, filter, mContext);

        // Initialize the mentions popup menu
        mPopupWindow = new PopupWindow(mContext);
        mMentionsList = new ListView(mContext);
        mMentionsList.setOnItemClickListener(this);
        mMentionsList.setDivider(null);

        mPopupWindow.setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPopupWindow.setElevation(5);
        }
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));

        mPopupWindow.setContentView(mMentionsList);

        mHelper = new ActivityHelper(mContext);
    }

    public void remove() {
        BaseFragmentActivity.safeUnregisterReceiver(mUserResultsReceiver, mContext);
        mEditText.removeTextChangedListener(this);
    }

    public void dismiss() {
        mResults = null;
        refreshViewState();
    }


    /** Finds the current mention word start and end boundaries (or -1,-1 if none found)  */
    private int[] getMentionBoundaries() {
        String text = mEditText.getText().toString();
        int start = -1;
        int end = text.length() - 1;

        if ((text.length() == 0) || (mEditText.getSelectionStart() == 0)) {
            return new int[] { -1, -1 };
        }

        for (int i = mEditText.getSelectionStart() - 1; i >= 0; i--) {
            String c = text.substring(i, i + 1);

            if (text.charAt(i) == MENTION_CHAR) {
                // Found the beginning of the mention
                start = i;
                break;
            } else if (!c.matches(VALID_MENTION_CHARS)) {
                // Word boundary - not part of a mention
                return new int[]{-1, -1};
            }
        }

        if (start == -1) {
            // No mention found
            return new int[] { -1, -1 };
        }

        // Find the end of the mention
        for (int i = start + 1; i < text.length(); i++) {
            String c = text.substring(i, i + 1);
            if (!c.matches(VALID_MENTION_CHARS)) {
                // Found end of the mention
                end = i - 1;
                break;
            }
        }

        if (end - start <  1) {
            // Mention must be at least one character long (e.g. "@a")
            return new int[] { -1, -1 };
        }

        // Return mention name (not including the "@" character)
        return new int[] { start + 1, end };
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
;
    }

    @Override
    public void afterTextChanged(Editable s) {
        dismiss();

        int[] mentionBoundaries = getMentionBoundaries();

        if ((mentionBoundaries[0] == -1) || (mentionBoundaries[1] == -1)) {
            // No mention typed in
            mLastSearch = null;
            return;
        }

        // Show mentions popup menu
        String text = mEditText.getText().toString();
        final String searchText = text.substring(mentionBoundaries[0], mentionBoundaries[1] + 1);

        mLastSearch = searchText;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performSearch(searchText);
                }
            }, 200);
    }

    private void performSearch(String searchText) {
        if (!searchText.equals(mLastSearch)) {
            // User typed in more characters since then
            return;
        } else if (searchText.length() == 0) {
            // No search
            return;
        }

        Intent serviceIntent = new Intent(INaturalistService.ACTION_SEARCH_USERS, null, mContext, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.QUERY, searchText);
        serviceIntent.putExtra(INaturalistService.PAGE_NUMBER, 1);
        ContextCompat.startForegroundService(mContext, serviceIntent);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JSONObject user = (JSONObject) view.getTag();
        String username = user.optString("login");

        int[] mentionBoundaries = getMentionBoundaries();

        if ((mentionBoundaries[0] == -1) || (mentionBoundaries[1] == -1)) {
            // No mention typed in
            return;
        }

        // Add the full selected username to the EditText
        String text = mEditText.getText().toString();
        StringBuilder newText = new StringBuilder();
        newText.append(text.substring(0, mentionBoundaries[0]));
        newText.append(username);
        newText.append(' ');
        newText.append(text.substring(mentionBoundaries[1] + 1));

        mEditText.setText(newText.toString());
        mEditText.setSelection(mentionBoundaries[0] + username.length() + 1);

        dismiss();
    }


    private class UserSearchReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            String error = extras.getString("error");
            String query = extras.getString(INaturalistService.QUERY);
            if ((error != null) || (!query.equals(mLastSearch))) {
                mResults = null;
                refreshViewState();
                return;
            }

            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
            BetterJSONObject resultsObject;
            SerializableJSONArray resultsJSON;

            if (isSharedOnApp) {
                resultsObject = (BetterJSONObject) mApp.getServiceResult(intent.getAction());
            } else {
                resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.RESULTS);
            }

            JSONArray results = null;

            if (resultsObject != null) {
                resultsJSON = resultsObject.getJSONArray("results");
                Integer count = resultsObject.getInt("total_results");
                if (count != null) {
                    results = resultsJSON.getJSONArray();
                }
            }

            if (results == null) {
                mResults = null;
                refreshViewState();
                return;
            }

            ArrayList<JSONObject> resultsArray = new ArrayList<JSONObject>();

            for (int i = 0; i < results.length(); i++) {
				try {
					JSONObject item = results.getJSONObject(i);
					resultsArray.add(item);
				} catch (JSONException e) {
					e.printStackTrace();
				}
            }

            mResults = resultsArray;
            refreshViewState();
        }
    }

    private void refreshViewState() {
        if ((mResults == null) || (mResults.size() == 0)) {
            mPopupWindow.dismiss();
            return;
        }

        UserMentionsAdapter adapter = new UserMentionsAdapter(mContext, mResults);
        mMentionsList.setAdapter(adapter);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity)mContext).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        mPopupWindow.setWidth(width - (int)mHelper.dpToPx(30));

        int pos = mEditText.getSelectionStart();
        Layout layout = mEditText.getLayout();
        int line = layout.getLineForOffset(pos);
        int baseline = layout.getLineBaseline(line);
        int ascent = layout.getLineAscent(line);
        final float y = baseline + ascent;

        Rect r = new Rect();
        mEditText.getWindowVisibleDisplayFrame(r);

        mPopupWindow.setHeight((int)(r.bottom - mEditText.getHeight() + y - mEditText.getScrollY() - mHelper.dpToPx(70)));

        mPopupWindow.setClippingEnabled(false);

        mPopupWindow.showAsDropDown(mEditText, 0, (int)y - mEditText.getScrollY());

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mEditText.setFocusableInTouchMode(true);
                mEditText.requestFocus();
            }
        }, 10);

        ActivityHelper.willListScroll(mMentionsList, new ActivityHelper.isListScrollable() {
            @Override
            public void isListScrollable(boolean scrollable) {
                if (!scrollable) {
                    // Set height to the number of items visible
                    ViewGroup.LayoutParams params = mMentionsList.getLayoutParams();
                    params.height = (int)(mHelper.dpToPx(48) * mMentionsList.getAdapter().getCount());
                    mMentionsList.setLayoutParams(params);
                    mMentionsList.requestLayout();

                    mPopupWindow.update(mEditText, 0, (int)y - mEditText.getScrollY(), mPopupWindow.getWidth(), params.height);

                }
            }
        });
    }


    private class UserMentionsAdapter extends ArrayAdapter<JSONObject> {

        private List<JSONObject> mItems;
        private Context mContext;

        public UserMentionsAdapter(Context context, List<JSONObject> objects) {
            super(context, R.layout.mentions_item, objects);

            mItems = objects;
            mContext = context;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public JSONObject getItem(int index) {
            return mItems.get(index);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = convertView != null ? convertView : inflater.inflate(R.layout.mentions_item, parent, false);
            JSONObject item = mItems.get(position);

            TextView username = (TextView) view.findViewById(R.id.username);
            ImageView userPic = (ImageView) view.findViewById(R.id.user_icon);

            String fullName = item.optString("name");
            if ((fullName != null) && (fullName.length() > 0) && (!item.isNull("name"))) {
                username.setText(String.format("%s (%s)", fullName, item.optString("login")));
            } else {
                username.setText(item.optString("login"));
            }

            String iconUrl = item.optString("icon");

            if ((iconUrl != null) && (iconUrl.length() > 0) && (!item.isNull("icon"))) {
                Picasso.with(mContext)
                        .load(iconUrl)
                        .transform(new UserActivitiesAdapter.CircleTransform())
                        .placeholder(R.drawable.ic_account_circle_black_48dp)
                        .into(userPic);
            } else {
                userPic.setImageResource(R.drawable.ic_account_circle_black_48dp);
            }

            view.setTag(item);

            return view;
        }
    }
}
