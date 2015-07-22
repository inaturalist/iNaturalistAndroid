package org.inaturalist.manitoba.android;

import android.os.Bundle;
import android.widget.Button;

import com.flurry.android.FlurryAgent;

public class AboutActivity extends BaseFragmentActivity {
	private static final String TAG = "INaturalistAboutActivity";

	private Button mAddObservation;
	private Button mAbout;
	private INaturalistApp mApp;

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

		if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

	    setContentView(R.layout.about);

	    onDrawerCreate(savedInstanceState);
	}
	
}
