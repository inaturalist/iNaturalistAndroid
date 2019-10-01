package org.inaturalist.android;

import android.os.Bundle;


public class DebugSettingsActivity extends BaseFragmentActivity {

@Override
	protected void onStop()
	{
		super.onStop();

	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.debug_settings);
        setTitle(R.string.debug_settings);

        onDrawerCreate(savedInstanceState);
    }
}
