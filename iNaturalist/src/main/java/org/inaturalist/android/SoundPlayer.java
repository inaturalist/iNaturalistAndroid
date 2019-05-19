package org.inaturalist.android;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class SoundPlayer implements MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener {
    private MediaPlayer mMediaPlayer;

    private ObservationSound mSound;
    private Context mContext;
    private final INaturalistApp mApp;
    private Handler mHandler;
    private final ActivityHelper mHelper;

    private View mView;
    private ImageView mPlayerButton;
    private SeekBar mSeekBar;
    private TextView mPlayerTime;
    private boolean mIsError;

    private int mSoundLengthMs;


    public SoundPlayer(Context context, ViewGroup container, ObservationSound sound) {
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mSound = sound;

        mHandler = new Handler();
        mHelper = new ActivityHelper(mContext);

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mView = inflater.inflate(R.layout.sound_player, container, false);

        mPlayerButton = mView.findViewById(R.id.player_button);

        mPlayerTime = (TextView) mView.findViewById(R.id.player_time);

        mSeekBar = (SeekBar)mView.findViewById(R.id.player_seekbar);
        mSeekBar.setOnSeekBarChangeListener(this);

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnCompletionListener(this);

        try {
            mMediaPlayer.setDataSource(mContext, Uri.parse(sound.file_url));
            mMediaPlayer.prepare();
            mSoundLengthMs = mMediaPlayer.getDuration();
            mSeekBar.setMax(mSoundLengthMs);
            updateProgress();
        } catch (IOException e) {
            e.printStackTrace();
            mIsError = true;
            return;
        }

        if (mSound.isSoundCloud()) {
            mSeekBar.setEnabled(false);
        }

        mPlayerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsError) {
                    Toast.makeText(mContext, R.string.could_not_play_sound, Toast.LENGTH_LONG);
                    return;
                }
                if (mSound.isSoundCloud()) {
                    // Don't play SoundCloud files inside the app
                    String inatNetwork = mApp.getInaturalistNetworkMember();
                    String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
                    String obsUrl = inatHost + "/observations/" + mSound.observation_id;

                    String alertText = String.format(mContext.getString(R.string.cant_play_soundcloud), obsUrl);
                    //confirm(String title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
                    mHelper.alert(mContext.getString(R.string.soundcloud), alertText);
                    return;
                }

                if (!mMediaPlayer.isPlaying()) {
                    mPlayerButton.setImageResource(R.drawable.pause);
                    mMediaPlayer.start();

                    updateProgress();
                } else {
                    mPlayerButton.setImageResource(R.drawable.play);
                    mMediaPlayer.pause();
                }
            }
        });
    }

    public View getView() {
        return mView;
    }

    public void pause() {
        mMediaPlayer.pause();
    }

    public void destroy() {
        try {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        } catch (IllegalStateException exc) {
            exc.printStackTrace();
        }
    }

    private void updateProgress() {
        try {
            mSeekBar.setProgress(mMediaPlayer.getCurrentPosition());

            int totalSecs = (int) Math.floor(mSoundLengthMs / 1000);
            int currentSecs = (int) Math.floor(mMediaPlayer.getCurrentPosition() / 1000);

            mPlayerTime.setText(String.format("%02d:%02d / %02d:%02d",
                    (currentSecs % 3600) / 60, currentSecs % 60,
                    (totalSecs % 3600) / 60, totalSecs % 60
            ));

            if (mMediaPlayer.isPlaying()) {
                Runnable notification = new Runnable() {
                    public void run() {
                        updateProgress();
                    }
                };
                mHandler.postDelayed(notification, 100);
            }
        } catch (IllegalStateException exc) {
            // Happens if our player got destroyed while playing
            exc.printStackTrace();
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int percent) {
        mSeekBar.setSecondaryProgress(percent);
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mPlayerButton.setImageResource(R.drawable.play);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
        if (!fromUser) return;

        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
            mPlayerButton.setImageResource(R.drawable.pause);
            updateProgress();
        }

        mMediaPlayer.seekTo(mSeekBar.getProgress());
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
