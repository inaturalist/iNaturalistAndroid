package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.tinylog.Logger;

public class DeleteAccount extends BaseFragmentActivity {
    private static final String TAG = "About";

    private TextView mMessage;
    private INaturalistApp mApp;
    private EditText mUsernameText;
    private Button mConfirm;
    private ActivityHelper mHelper;
    private DeleteAccountReceiver mDeleteAccountReceiver;

    @Override
	protected void onStop()
	{
		super.onStop();
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
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
        setContentView(R.layout.delete_account);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(R.string.delete_your_account);
        
        mHelper = new ActivityHelper(this);

        mMessage = findViewById(R.id.content);
        mUsernameText = findViewById(R.id.username);
        mConfirm = findViewById(R.id.confirm);

        mConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mUsernameText.getText().toString().equals(mApp.currentUserLogin())) {
                    mHelper.alert(String.format(getString(R.string.you_must_enter_username_in_the_form_to_delete_account), mApp.currentUserLogin()));
                    return;
                }

                // Delete the profile
                mHelper.loading();

                // Delete account
                Intent serviceIntent = new Intent(INaturalistService.ACTION_DELETE_ACCOUNT, null, DeleteAccount.this, INaturalistService.class);
                ContextCompat.startForegroundService(DeleteAccount.this, serviceIntent);
            }
        });

        HtmlUtils.fromHtml(mMessage, String.format(getString(R.string.delete_account_message), mApp.currentUserLogin()));
    }

    @Override
    protected void onPause() {
        super.onPause();

        safeUnregisterReceiver(mDeleteAccountReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();


        mDeleteAccountReceiver = new DeleteAccountReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INaturalistService.DELETE_ACCOUNT_RESULT);
        safeRegisterReceiver(mDeleteAccountReceiver, filter);

    }

    private class DeleteAccountReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();

            boolean success = extras != null ? extras.getBoolean(INaturalistService.SUCCESS) : false;

            if (!success) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_deleting_account), Toast.LENGTH_LONG).show();
                mHelper.stopLoading();
                return;
            }

            Toast.makeText(getApplicationContext(), getResources().getString(R.string.your_account_has_been_deleted), Toast.LENGTH_LONG).show();
            mHelper.stopLoading();

            DeleteAccount.this.setResult(RESULT_OK);
            DeleteAccount.this.finish();
        }
    }

}
