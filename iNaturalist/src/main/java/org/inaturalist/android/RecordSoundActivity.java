package org.inaturalist.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.flurry.android.FlurryAgent;
import com.livefront.bridge.Bridge;

import java.text.SimpleDateFormat;

public class RecordSoundActivity extends AppCompatActivity implements SoundRecorder.OnRecordingStopped {
    private static String TAG = "RecordSoundActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    
    private Button mStartRecording;
    private Button mStopRecording;

    private boolean mIsRecording = false;
    private SoundRecorder mRecorder;
    private String mOutputFilename;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        setTitle(R.string.record_sound);

        mApp = (INaturalistApp) getApplicationContext();

        setContentView(R.layout.record_sound);
        
        mStartRecording = (Button) findViewById(R.id.start_recording);
        mStopRecording = (Button) findViewById(R.id.stop_recording);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis());
        mOutputFilename = getExternalCacheDir().getAbsolutePath() + "/inaturalist_sound_" + timeStamp + ".wav";

        mRecorder = new SoundRecorder(this, mOutputFilename, this);

        mStopRecording.setVisibility(View.GONE);

        mStartRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPauseRecording();
            }
        });

        mHelper = new ActivityHelper(this);
        mStopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRecorder.stopRecording();
                mHelper.loading();
            }
        });
    }

    private void startPauseRecording() {
        if (!mApp.isAudioRecordingPermissionGranted()) {
            mApp.requestAudioRecordingPermission(RecordSoundActivity.this, new INaturalistApp.OnRequestPermissionResult() {
                @Override
                public void onPermissionGranted() {
                    startPauseRecording();
                }

                @Override
                public void onPermissionDenied() {
                }
            });

            return;

        } else if (!mApp.isExternalStoragePermissionGranted()) {
            mApp.requestExternalStoragePermission(RecordSoundActivity.this, new INaturalistApp.OnRequestPermissionResult() {
                @Override
                public void onPermissionGranted() {
                    startPauseRecording();
                }

                @Override
                public void onPermissionDenied() {
                }
            });

            return;
        }

        mStartRecording.setText(mIsRecording ? R.string.resume_recording : R.string.pause_recording);
        mStopRecording.setVisibility(View.VISIBLE);

        if (mIsRecording) {
            mRecorder.pauseRecording();
        } else {
            if (mRecorder.hasStartedRecording()) {
                mRecorder.resumeRecording();
            } else {
                mRecorder.startRecording();
            }
        }

        mIsRecording = !mIsRecording;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        mApp.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onRecordingStopped() {
        mHelper.stopLoading();

        Intent intent = new Intent();
        intent.setData(Uri.parse(mOutputFilename));
        setResult(RESULT_OK, intent);
        finish();
    }
}
