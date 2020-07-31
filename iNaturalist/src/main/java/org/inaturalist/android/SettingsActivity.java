package org.inaturalist.android;

import android.os.Bundle;



public class SettingsActivity extends BaseFragmentActivity {

    private INaturalistApp mApp;

    @Override
	protected void onStop()
	{
		super.onStop();

	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.settings);
        setTitle(R.string.settings);

        onDrawerCreate(savedInstanceState);
    }
}
