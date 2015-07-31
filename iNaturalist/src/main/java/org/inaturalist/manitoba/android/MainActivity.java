package org.inaturalist.manitoba.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.flurry.android.FlurryAgent;

import org.inaturalist.manitoba.android.R;

public class MainActivity extends BaseFragmentActivity {
	private static final String TAG = "INaturalistMainActivity";

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

	    setContentView(R.layout.main);

	    onDrawerCreate(savedInstanceState);

	    mAddObservation = (Button) findViewById(R.id.add_observation);
		mAbout = (Button) findViewById(R.id.about);

		mAddObservation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(MainActivity.this, ProjectsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(intent);
			}
		});

		mAbout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(MainActivity.this, AboutActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				startActivity(intent);
			}
		});
	}
	
}
