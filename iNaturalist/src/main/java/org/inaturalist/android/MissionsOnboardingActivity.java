package org.inaturalist.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MissionsOnboardingActivity extends BaseFragmentActivity {

@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.missions_onboarding);
	    onDrawerCreate(savedInstanceState);

        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_MISSIONS_ONBOARDING);

        getSupportActionBar().setTitle(R.string.missions);

        ((Button) findViewById(R.id.got_it)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences settings = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                settings.edit().putBoolean("shown_missions_onboarding", true).commit();
                finish();
            }
        });

    }
}
