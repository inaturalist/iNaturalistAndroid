package org.inaturalist.android;

import org.inaturalist.android.ObservationProvider.Observation;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class INaturalistActivity extends ListActivity {
	public static String APP_TAG = "INAT";
  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(Observation.CONTENT_URI);
        }
        
        // Inform the list we provide context menus for items
        getListView().setOnCreateContextMenuListener(this);
        
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        Cursor cursor = managedQuery(getIntent().getData(), ObservationProvider.PROJECTION, 
        		null, null, Observation.DEFAULT_SORT_ORDER);

        // Used to map notes entries from the database to views
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
            this, R.layout.list_item, cursor,
            new String[] { Observation.SPECIES_GUESS, Observation.DESCRIPTION }, 
            new int[] { R.id.speciesGuess, R.id.subContent });
        setListAdapter(adapter);
        
//        DefaultHttpClient client = new DefaultHttpClient();
//        String url = "http://www.inaturalist.org/observations.json";
//        HttpGet request = new HttpGet(url);
//        request.setHeader("Content-Type", "application/xml");
//        try {
//            HttpResponse response = client.execute(request);
//
//            HttpEntity entity = response.getEntity();
//            String content = EntityUtils.toString(entity);
//            try {
//              JSONArray json = new JSONArray(content);
//              Log.d(APP_TAG, "species_guess: " + json.getJSONObject(0).getString("species_guess"));
//              String[] from = new String[]{};
//              String[] to = new String[]{};
//              SimpleAdapter observations = 
//                        new SimpleAdapter(this, R.layout.list_item, json, from, to);
//                setListAdapter(observations);
//            } catch (JSONException e) {
//              Log.e(APP_TAG, "JSONException: " + e.toString());
//            }
//
//            Log.d(APP_TAG, "OK: " + content.toString());
//        }
//        catch (IOException e) {
//          request.abort();
//            Log.w(getClass().getSimpleName(), "Error for URL " + url, e);
//        }

    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.observations_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.observations_menu_add:
            // Launch activity to insert a new item
//            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
        	startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), this, ObservationEditor.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user.  The have clicked on one, so return it now.
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, uri, this, ObservationEditor.class));
        }
    }
}