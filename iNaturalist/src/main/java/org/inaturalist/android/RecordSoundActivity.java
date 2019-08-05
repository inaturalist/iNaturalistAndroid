package org.inaturalist.android;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.livefront.bridge.Bridge;
import com.melnykov.fab.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Random;

public class RecordSoundActivity extends AppCompatActivity implements SoundRecorder.OnRecordingStatus {
    private static String TAG = "RecordSoundActivity";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
    
    private FloatingActionButton mStartRecording;
    private TextView mStopRecording;
    private TextView mRecordingTime;
    private TextureView mVisualizerTexture;

    private Long mTotalTime = null;
    private Long mLastStartTime = null;
    private boolean mIsRecording = false;
    private SoundRecorder mRecorder;
    private String mOutputFilename;

    private static final int MAX_SOUND_LINES = 1500;
    private static final int SOUND_LINE_WIDTH = 4;
    private static final int SOUND_LINE_SPACING = 4;
    private byte[] mSoundsValues = new byte[MAX_SOUND_LINES];

    private Handler mHandler = new Handler();
    private Runnable mUpdateTimer = new Runnable() {
        @Override
        public void run() {
            float timeSecs = 0;

            if (mTotalTime != null) {
                if (mLastStartTime != null) {
                    timeSecs = mTotalTime + (System.currentTimeMillis() - mLastStartTime);
                } else {
                    timeSecs = mTotalTime;
                }
            }

            timeSecs /= 1000;

            mRecordingTime.setText(String.format(getResources().getString(R.string.seconds), timeSecs));

            if (mIsRecording) {
                mHandler.postDelayed(mUpdateTimer, 100);
            }
        }
    };

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
        
        mStartRecording = findViewById(R.id.start_recording);
        mStopRecording = findViewById(R.id.stop_recording);
        mRecordingTime = findViewById(R.id.seconds_counter);
        mVisualizerTexture = findViewById(R.id.sound_visualizer);

        mVisualizerTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // TODO
                drawCurrentSoundWave();
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        mRecordingTime.setText(String.format(getResources().getString(R.string.seconds), 0f));

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
                mIsRecording = false;
                mRecorder.stopRecording();
                mHelper.loading();
            }
        });
    }

    private void drawCurrentSoundWave() {
        Canvas canvas = mVisualizerTexture.lockCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#cb0000"));
        paint.setStrokeWidth(SOUND_LINE_WIDTH);
        paint.setStyle(Paint.Style.STROKE);

        for (int i = 0; i < MAX_SOUND_LINES; i++) {
            // TODO
            mSoundsValues[i] = (byte)(new Random().nextInt(256));

            float[] line = soundValueToLine(canvas, i);
            canvas.drawLine(line[0], line[1], line[2], line[3], paint);
        }

        mVisualizerTexture.unlockCanvasAndPost(canvas);
    }

    private float[] soundValueToLine(Canvas canvas, int byteOffset) {
        byte soundValue = mSoundsValues[byteOffset];
        float lineHeight = (soundValue / 256f) * canvas.getHeight();
        float yStart = (canvas.getHeight() - lineHeight) / 2;
        float yEnd = yStart + lineHeight;
        float xStart = byteOffset * (SOUND_LINE_WIDTH + SOUND_LINE_SPACING);

        return new float[] { xStart, yStart, xStart, yEnd };
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

        mStartRecording.setImageDrawable(getResources().getDrawable(mIsRecording ? R.drawable.microphone_white : R.drawable.baseline_pause_white_36));
        mStopRecording.setVisibility(View.VISIBLE);

        if (mIsRecording) {
            mRecorder.pauseRecording();
            mTotalTime += (System.currentTimeMillis() - mLastStartTime);
            mLastStartTime = null;
        } else {
            if (mTotalTime == null) {
                mTotalTime = 0L;
            }

            mLastStartTime = System.currentTimeMillis();

            if (mRecorder.hasStartedRecording()) {
                mRecorder.resumeRecording();
            } else {
                mRecorder.startRecording();
            }

            mHandler.postDelayed(mUpdateTimer, 100);
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
    public void onSoundRecording(byte[] values, int count) {
        Log.e("AAA", "Recording: " + count + ":" + (values[0] & 0xFF));
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
