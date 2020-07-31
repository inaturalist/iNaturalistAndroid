package org.inaturalist.android;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ThirdPartyDataSharingActivity extends AppCompatActivity {

    private INaturalistApp mApp;

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

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.third_party_data_sharing);
        setTitle(R.string.third_party_data_sharing);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        HtmlUtils.fromHtml(findViewById(R.id.notes1), getString(R.string.third_party_data_sharing_notes1));
        HtmlUtils.fromHtml(findViewById(R.id.notes2), getString(R.string.third_party_data_sharing_notes2));
        HtmlUtils.fromHtml(findViewById(R.id.notes3), getString(R.string.third_party_data_sharing_notes3));
    }
}
