package org.inaturalist.android;

import android.os.Bundle;

import com.flurry.android.FlurryAgent;

public class SettingsActivity extends BaseFragmentActivity {

    @Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		FlurryAgent.onEndSession(this);
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings);
        setTitle(R.string.settings);

        onDrawerCreate(savedInstanceState);
    }
}
