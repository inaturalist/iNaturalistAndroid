package org.inaturalist.android;

import android.os.Bundle;


public class AboutActivity extends BaseFragmentActivity {

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

        setContentView(R.layout.about);
        setTitle(R.string.about_this_app);

        onDrawerCreate(savedInstanceState);
    }
}
