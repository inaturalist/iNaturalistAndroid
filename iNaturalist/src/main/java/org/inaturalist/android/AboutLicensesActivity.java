package org.inaturalist.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.livefront.bridge.Bridge;

public class AboutLicensesActivity extends AppCompatActivity {
    private static final String TAG = "AboutLicensesActivity";

    private INaturalistApp mApp;
    private RecyclerView mLicensesList;
    private AboutLicensesAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        mApp = (INaturalistApp)getApplication();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.about_licenses);

        ActionBar ab = getSupportActionBar();
        ab.setTitle(R.string.about_licenses);
        ab.setDisplayHomeAsUpEnabled(true);

        mLicensesList = findViewById(R.id.licenses_list);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }

        mLicensesList.addItemDecoration(new DividerItemDecoration(this, 0));
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        mLicensesList.setLayoutManager(llm);
        mAdapter = new AboutLicensesAdapter(this, LicenseUtils.getAllLicenseTypes(mApp));
        mLicensesList.setAdapter(mAdapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Respond to the action bar's Up/Home button
                this.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
