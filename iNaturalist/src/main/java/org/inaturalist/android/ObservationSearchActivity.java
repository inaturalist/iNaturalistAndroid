package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;



import org.json.JSONArray;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;

public class ObservationSearchActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "ObervationSearchActivity";

    private ListAdapter mObservationsAdapter;

    private ProgressBar mProgress;
    
    private INaturalistApp mApp;

    private String mCurrentSearchString = "";

    private SearchResultsReceiver mSearchResultsReceiver;
    private TextView mNoResults;

    private int mLastTypingTime = 0;
    private long mStartTime;

@Override
    public void onResume() {
        super.onResume();
        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }

        mSearchResultsReceiver = new SearchResultsReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.USER_SEARCH_OBSERVATIONS_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mSearchResultsReceiver, filter, this);
    }


    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mSearchResultsReceiver, this);
    }

 



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

   @Override
   public void onBackPressed() {
       finish();
   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mApp == null) { mApp = (INaturalistApp) getApplicationContext(); }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater li = LayoutInflater.from(this);
        View customView = li.inflate(R.layout.observation_search_action_bar, null);
        actionBar.setCustomView(customView);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());
        setContentView(R.layout.taxon_search);
        
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setVisibility(View.GONE);

        mNoResults = (TextView) findViewById(android.R.id.empty);
        mNoResults.setVisibility(View.GONE);

        String login = mApp.currentUserLogin();

        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        String conditions = "(_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        conditions += ") AND (is_deleted = 0 OR is_deleted is NULL)"; // Don't show deleted observations

        final Cursor cursor = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION,
                conditions, null, Observation.DEFAULT_SORT_ORDER);

        final EditText autoCompView = (EditText) customView.findViewById(R.id.search_text);
        
        autoCompView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence s, int start, int before, int count) {
                if (s.length() == 0) {
                    mProgress.setVisibility(View.GONE);
                    mNoResults.setVisibility(View.GONE);
                    return;
                } else {
                    getListView().setVisibility(View.VISIBLE);
                }

                if (!isNetworkAvailable() || !mApp.loggedIn()) {
                    // Offline search (no network or user not logged-in)
                    if ((mObservationsAdapter == null) || !(mObservationsAdapter instanceof ObservationCursorAdapter)) {
                        mObservationsAdapter = new ObservationCursorAdapter(ObservationSearchActivity.this, cursor);
                        setListAdapter(mObservationsAdapter);
                    }

                    ((ObservationCursorAdapter)mObservationsAdapter).refreshCursor(s.toString().trim());

                    if ((mObservationsAdapter.getCount() == 0) && (s.length() > 0)) {
                        mNoResults.setVisibility(View.VISIBLE);
                    } else {
                        mNoResults.setVisibility(View.GONE);
                    }

                } else {
                    // Online search
                    mCurrentSearchString = s.toString();
                    mStartTime = System.currentTimeMillis();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (System.currentTimeMillis() - mStartTime > 500) {
                                performOnlineSearch(mCurrentSearchString);
                            }
                        }
                    }, 600);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                autoCompView.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(autoCompView, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);

        getListView().setVisibility(View.GONE);
        getListView().setOnItemClickListener(this);
    }

    private void performOnlineSearch(final String query) {
        mProgress.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);
        mNoResults.setVisibility(View.GONE);

        Intent serviceIntent = new Intent(INaturalistService.ACTION_SEARCH_USER_OBSERVATIONS, null, this, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.QUERY, query);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
        if (mObservationsAdapter instanceof ObservationCursorAdapter) {
            // Offline result
            Uri uri = ContentUris.withAppendedId(Observation.CONTENT_URI, id);
            if ((!((ObservationCursorAdapter)mObservationsAdapter).isLocked(uri)) || (((ObservationCursorAdapter)mObservationsAdapter).isLocked(uri) && !mApp.getIsSyncing())) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri, this, ObservationViewerActivity.class));
            }
        } else {
            // Online result - download it locally (to our app DB) so the user can edit it
            JSONObject item = (JSONObject) v.getTag();
            int obsId = item.optInt("id");

            Cursor c = getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[] { String.valueOf(obsId)}, null);
            if (c.getCount() > 0) {
                // Observation already found locally - use that copy
                c.moveToFirst();
                long internalObsId = c.getLong(c.getColumnIndex(Observation._ID));
                c.close();
                Uri uri = ContentUris.withAppendedId(Observation.CONTENT_URI, internalObsId);
                startActivity(new Intent(Intent.ACTION_VIEW, uri, this, ObservationViewerActivity.class));

                return;
            }

            c.close();

            // Observation hasn't been previously downloaded locally - add it to the local app DB
            Uri newObs = saveObservationLocally(item);

            startActivity(new Intent(Intent.ACTION_VIEW, newObs, this, ObservationViewerActivity.class));
        }
    }

    // Saves an observation search result item locally into the app DB and returns the created Uri
    private Uri saveObservationLocally(JSONObject item) {
        Observation jsonObservation = new Observation(new BetterJSONObject(item));
        ContentValues cv = jsonObservation.getContentValues();
        cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
        cv.put(Observation.LAST_COMMENTS_COUNT, jsonObservation.comments_count);
        cv.put(Observation.LAST_IDENTIFICATIONS_COUNT, jsonObservation.identifications_count);
        Uri newObs = getContentResolver().insert(Observation.CONTENT_URI, cv);

        // Add the observation's photos as well
        for (int j = 0; j < jsonObservation.photos.size(); j++) {
            ObservationPhoto photo = jsonObservation.photos.get(j);
            photo._observation_id = jsonObservation._id;

            ContentValues opcv = photo.getContentValues();
            Logger.tag(TAG).info("OP - searchObservationLocally - Setting _SYNCED_AT - " + photo.id + ":" + photo._id + ":" + photo._observation_id + ":" + photo.observation_id);
            opcv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis()); // So we won't re-add this photo as though it was a local photo
            opcv.put(ObservationPhoto._OBSERVATION_ID, photo._observation_id);
            opcv.put(ObservationPhoto._PHOTO_ID, photo._photo_id);
            opcv.put(ObservationPhoto._ID, photo.id);
            opcv.put(ObservationPhoto.OBSERVATION_UUID, jsonObservation.uuid);

            try {
                getContentResolver().insert(ObservationPhoto.CONTENT_URI, opcv);
            } catch (SQLiteConstraintException exc) {
                Logger.tag(TAG).info("OP - searchObservationLocally - ObservationPhoto already exists - updating it");
                getContentResolver().update(ObservationPhoto.CONTENT_URI, opcv, "_id = ?", new String[] { String.valueOf(photo.id) });
            }
        }

        // Add the observation's projects

        if ((jsonObservation.projects != null) && (jsonObservation.projects.getJSONArray() != null)) {
            JSONArray projects = jsonObservation.projects.getJSONArray();

            for (int i = 0; i < projects.length(); i++) {
                JSONObject projectJson = projects.optJSONObject(i);
                ProjectObservation project = new ProjectObservation(new BetterJSONObject(projectJson));
                getContentResolver().insert(ProjectObservation.CONTENT_URI, project.getContentValues());
            }
        }

        // Add the observation's project fields
        if ((jsonObservation.field_values != null) && (jsonObservation.field_values.getJSONArray() != null)) {
            JSONArray fields = jsonObservation.field_values.getJSONArray();

            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                ProjectFieldValue fieldValue = new ProjectFieldValue(new BetterJSONObject(field));

                cv = fieldValue.getContentValues();
                cv.put(ProjectFieldValue._SYNCED_AT, System.currentTimeMillis());
                getContentResolver().insert(ProjectFieldValue.CONTENT_URI, cv);

                Cursor c = getContentResolver().query(ProjectField.CONTENT_URI, ProjectField.PROJECTION,
                        "field_id = " + fieldValue.field_id, null, Project.DEFAULT_SORT_ORDER);
                if (c.getCount() == 0) {
                    // This observation has a non-project custom field - add it as well
                    Intent serviceIntent = new Intent(INaturalistService.ACTION_ADD_PROJECT_FIELD, null, this, INaturalistService.class);
                    serviceIntent.putExtra(INaturalistService.FIELD_ID, fieldValue.field_id);
                    ContextCompat.startForegroundService(this, serviceIntent);
                }
                c.close();
            }
        }


        return newObs;
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


    private class SearchResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);

            SerializableJSONArray resultsObject;
            if (isSharedOnApp) {
                resultsObject = (SerializableJSONArray) mApp.getServiceResult(INaturalistService.USER_SEARCH_OBSERVATIONS_RESULT);
            } else {
                resultsObject = (SerializableJSONArray) intent.getSerializableExtra(INaturalistService.USER_SEARCH_OBSERVATIONS_RESULT);
            }

            String query = intent.getStringExtra(INaturalistService.QUERY);

            if (!mCurrentSearchString.equals(query)) {
                // While the search was running, the user entered new characters into the search bar - these
                // will be old results for the previous search string - don't return these
                return;
            }

            List<JSONObject> results = new ArrayList<>();
            if (resultsObject != null) {
                JSONArray jsonArray = resultsObject.getJSONArray();

                for (int i = 0; i < jsonArray.length(); i++) {
                    results.add(jsonArray.optJSONObject(i));
                }
            }

            mObservationsAdapter = new UserObservationAdapter(ObservationSearchActivity.this, results);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setListAdapter(mObservationsAdapter);
                    mProgress.setVisibility(View.GONE);
                    mListView.setVisibility(View.VISIBLE);

                    if ((mObservationsAdapter.getCount() == 0) && (mCurrentSearchString.length() > 0)) {
                        mNoResults.setVisibility(View.VISIBLE);
                    } else {
                        mNoResults.setVisibility(View.GONE);
                    }
                }
            });

        }
    }
}
