package org.inaturalist.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class DefaultLicensesActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_PAST_LICENSES = 0x1000;

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

        setContentView(R.layout.default_licenses);
        setTitle(R.string.default_licenses);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        HtmlUtils.fromHtml(findViewById(R.id.description), getString(R.string.default_licenses_description));
        findViewById(R.id.more_info).setOnClickListener(v -> {
            Intent intent = new Intent(DefaultLicensesActivity.this, AboutLicensesActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.change_licenses_for_existing_observations).setOnClickListener(v -> {
            Intent intent = new Intent(DefaultLicensesActivity.this, PastLicensesActivity.class);
            startActivityForResult(intent, REQUEST_CODE_PAST_LICENSES);
        });
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }
}
