package org.inaturalist.android;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.livefront.bridge.Bridge;

import org.tinylog.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Locale;

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
    private boolean mCancelledRecording = false;

    private static final int MAX_SOUND_LINES = 300;
    private static final int SOUND_LINE_WIDTH = 4;
    private static final int SOUND_LINE_SPACING = 4;
    private static final float MAX_SOUND_VALUE = 500f;

    private short[] mSoundsValues = new short[MAX_SOUND_LINES];
    private int mSoundPlaybackIndex = 0;
    private int mSoundWritingIndex = 0;
    private int mMaxSamplesForScreenWidth = 0;


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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        setTitle(R.string.record_sound);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.record_sound);
        
        mStartRecording = findViewById(R.id.start_recording);
        mStopRecording = findViewById(R.id.stop_recording);
        mRecordingTime = findViewById(R.id.seconds_counter);
        mVisualizerTexture = findViewById(R.id.sound_visualizer);

        mVisualizerTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Canvas canvas = mVisualizerTexture.lockCanvas();
                int canvasWidth = canvas.getWidth();
                mVisualizerTexture.unlockCanvasAndPost(canvas);

                mMaxSamplesForScreenWidth = canvasWidth / (SOUND_LINE_WIDTH + SOUND_LINE_SPACING);
                mSoundWritingIndex = mMaxSamplesForScreenWidth;
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
        File outputDirectory = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC) + File.separator + "iNaturalist");
        outputDirectory.mkdirs();
        mOutputFilename = outputDirectory + File.separator + "inaturalist_sound_" + timeStamp + ".wav";
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
                Logger.tag(TAG).info("Stop recording button pressed");
                mIsRecording = false;
                mRecorder.stopRecording();
                mHelper.loading();
            }
        });
    }

    private void drawCurrentSoundWave() {
        Canvas canvas = mVisualizerTexture.lockCanvas();

        // Canvas not ready yet
        if (canvas == null) return;

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#cb0000"));
        paint.setStrokeWidth(SOUND_LINE_WIDTH);
        paint.setStyle(Paint.Style.STROKE);

        Paint blackPaint = new Paint();
        blackPaint.setColor(Color.parseColor("#000000"));
        blackPaint.setStrokeWidth(SOUND_LINE_WIDTH);
        blackPaint.setStyle(Paint.Style.STROKE);

        int canvasWidth = canvas.getWidth();

        for (int i = 0; (i < MAX_SOUND_LINES) && (i * (SOUND_LINE_WIDTH + SOUND_LINE_SPACING) <= canvasWidth); i++) {
            // First, delete previous line
            drawBlankSoundLine(canvas, i, blackPaint);

            // Draw new line
            short soundValue = mSoundsValues[(mSoundPlaybackIndex + i) % MAX_SOUND_LINES];
            float[] line = soundValueToLine(canvas, i, soundValue);
            canvas.drawLine(line[0], line[1], line[2], line[3], paint);
        }

        mVisualizerTexture.unlockCanvasAndPost(canvas);
    }

    private void drawBlankSoundLine(Canvas canvas, int lineOffset, Paint blackPaint) {
        float yStart = 0;
        float yEnd = canvas.getHeight();
        float xStart = lineOffset * (SOUND_LINE_WIDTH + SOUND_LINE_SPACING);
        float xEnd = xStart;

        canvas.drawLine(xStart, yStart, xEnd, yEnd, blackPaint);
    }

    private float[] soundValueToLine(Canvas canvas, int lineOffset, short soundValue) {
        float xStart = lineOffset * (SOUND_LINE_WIDTH + SOUND_LINE_SPACING);

        float centerY = canvas.getHeight() / 2f;
        float yStart = centerY - (Math.min(1.0f, soundValue / MAX_SOUND_VALUE) * centerY);
        float yEnd = canvas.getHeight() - yStart;

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
                onBack();
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
        short[] shorts = new short[values.length / 2];
        long total = 0;
        ByteBuffer.wrap(values).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        for (int i = 0; i < shorts.length; i++) {
            total += shorts[i];
        }

        short average = (short) (total / shorts.length);

        mSoundsValues[mSoundWritingIndex] = average;
        mSoundWritingIndex++;
        if (mSoundWritingIndex >= MAX_SOUND_LINES) mSoundWritingIndex = 0;

        // Draw current sound wave
        drawCurrentSoundWave();

        // Move forward via the sound wave
        mSoundPlaybackIndex += 1;
        if (mSoundPlaybackIndex == MAX_SOUND_LINES) mSoundPlaybackIndex = 0;
    }

    @Override
    public void onBackPressed() {
        onBack();
    }

    private void onBack() {
        if (mRecorder.hasStartedRecording()) {
            // Need to cleanly finish recording
            mCancelledRecording = true;
            mRecorder.stopRecording();
            mHelper.loading();
        } else {
            // Just immediately exit the activity
            File outputFile = new File(mOutputFilename);
            outputFile.delete();

            setResult(RESULT_CANCELED);
            finish();
        }
    }


    @Override
    public void onRecordingStopped() {
        Logger.tag(TAG).info("onRecordingStopped: " + mCancelledRecording + ": " + mOutputFilename);
        mHelper.stopLoading();

        if (mCancelledRecording) {
            // User chose to cancel recording - delete the file
            File outputFile = new File(mOutputFilename);
            outputFile.delete();

            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
        String fileName = "recording_" + timeStamp + ".wav";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
        values.put(MediaStore.Audio.Media.IS_MUSIC, true);
        values.put(MediaStore.Audio.Media.ARTIST, "iNaturalist");
        values.put(MediaStore.Audio.Media.ALBUM, "Sound Recordings");
        values.put(MediaStore.Audio.Media.TITLE, "iNaturalist Sound Recording - " + timeStamp);

        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (Scoped Storage)
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/iNaturalist");
            uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            // Android 9 and below
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/iNaturalist", fileName);
            file.getParentFile().mkdirs();
            values.put(MediaStore.Audio.Media.DATA, file.getAbsolutePath());
            uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        }

        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri);
                 InputStream in = new FileInputStream(mOutputFilename)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
                getContentResolver().delete(uri, null, null);
            }
        }


        Intent intent = new Intent();
        intent.setData(uri);
        setResult(RESULT_OK, intent);
        finish();
    }
}
