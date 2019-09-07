package org.inaturalist.android;

import android.os.Bundle;



public class SettingsActivity extends BaseFragmentActivity {

@Override
	protected void onStop()
	{
		super.onStop();

	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings);
        setTitle(R.string.settings);

        onDrawerCreate(savedInstanceState);
    }
}
