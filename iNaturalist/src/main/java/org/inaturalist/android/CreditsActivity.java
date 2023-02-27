package org.inaturalist.android;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;
import android.widget.TextView;

import org.apache.commons.collections4.ListUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CreditsActivity extends AppCompatActivity {
    private static final String TAG = "Credits";

    private TextView mAboutText;
    private INaturalistApp mApp;

	@Override
	protected void onStop()
	{
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

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());
        setContentView(R.layout.credits);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(R.string.credits_title);

        mAboutText = findViewById(R.id.inat_credits);

        StringBuilder credits = new StringBuilder();

        credits.append(getString(R.string.inat_credits2));

        // Add per-network credit
        final String[] inatNetworks = mApp.getINatNetworks();

        credits.append("<br/><br/>");
        credits.append(getString(R.string.inat_credits_networks_pre));
        credits.append("<br/><br/>");

        // Show credits, sorted alphabetically by country name
        List<String> networks = Arrays.asList(inatNetworks);
        Collections.sort(networks, (n1, n2) -> {
            String countryName1 = mApp.getStringResourceByName("inat_country_name_" + n1, "n/a");
            String countryName2 = mApp.getStringResourceByName("inat_country_name_" + n2, "n/a");
            return countryName1.compareTo(countryName2);
        });

        for (String network : networks) {
            String networkCredit = mApp.getStringResourceByName("network_credit_" + network, "n/a");
            if (networkCredit.equals("n/a")) continue;

            credits.append(networkCredit);
            credits.append("<br/><br/>");
        }

        credits.append(getString(R.string.inat_credits_post));

        HtmlUtils.fromHtml(mAboutText, credits.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

}
