package org.inaturalist.android;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ObservationListActivity extends ListActivity {
	public static String TAG = "INAT";
  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.observation_list);
        
        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(Observation.CONTENT_URI);
        }
        
        // Inform the list we provide context menus for items
        getListView().setOnCreateContextMenuListener(this);
        
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        String login = prefs.getString("username", null);
        
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        String conditions = "_synced_at IS NULL";
        if (login != null) {
            conditions += " OR user_login = '" + login + "'";
        }
        
        Cursor cursor = managedQuery(getIntent().getData(), Observation.PROJECTION, 
        		conditions, null, Observation.DEFAULT_SORT_ORDER);

        // Used to map notes entries from the database to views
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this, R.layout.list_item, cursor,
                new String[] { Observation.SPECIES_GUESS, Observation.DESCRIPTION }, 
                new int[] { R.id.speciesGuess, R.id.subContent });
        setListAdapter(adapter);
        
//        Intent serviceIntent = new Intent(INaturalistService.ACTION_PASSIVE_SYNC, null, this, INaturalistService.class);
//        startService(serviceIntent);
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
        	startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData(), this, ObservationEditor.class));
            return true;
        case R.id.observations_menu_sync:
            Intent serviceIntent = new Intent(INaturalistService.ACTION_SYNC, null, this, INaturalistService.class);
            startService(serviceIntent);
//            Toast.makeText(getApplicationContext(), "Sync started", Toast.LENGTH_SHORT).show();
            return true;
        case R.id.observations_menu_menu:
            startActivity(new Intent(this, MenuActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
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