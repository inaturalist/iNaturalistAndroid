package org.inaturalist.android;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;


public class DebugSettingsActivity extends AppCompatActivity {

    @Override
	protected void onStop() {
		super.onStop();

	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.debug_settings);
        setTitle(R.string.debug_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
