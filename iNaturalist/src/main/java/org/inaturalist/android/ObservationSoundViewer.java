package org.inaturalist.android;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.viewpager.widget.ViewPager.LayoutParams;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.json.JSONObject;
import org.tinylog.Logger;

public class ObservationSoundViewer extends AppCompatActivity implements SoundPlayer.OnPlayerStatusChange {
    private static String TAG = "ObservationSoundViewer";
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	@State(AndroidStateBundlers.JSONObjectBundler.class) public JSONObject mObservation;

    public static final String SOUND_ID = "sound_id";
    public static final String DELETE_SOUND_ID = "delete_sound_id";

    @State public boolean mIsNewObservation;
    @State public int mObservationId;
    @State public int mSoundId;
    private View mDeleteSoundButton;
    @State public int mObservationIdInternal;
    private SoundPlayer mSoundPlayer;
    private ObservationSound mSound;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setLogo(R.drawable.ic_arrow_back);

        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());

        setContentView(R.layout.observation_editor_sound_player);

        mDeleteSoundButton = findViewById(R.id.delete_sound);
        
        Intent intent = getIntent();

        if (savedInstanceState == null) {
            mSoundId = intent.getIntExtra(SOUND_ID, 0);
        }

        actionBar.setTitle(R.string.observation_sound);

        ViewGroup soundPlayerContainer = findViewById(R.id.sound_player);

        Cursor c = getContentResolver().query(ObservationSound.CONTENT_URI,
                ObservationSound.PROJECTION,
                "_id = ?",
                new String[]{String.valueOf(mSoundId)},
                ObservationSound.DEFAULT_SORT_ORDER);
        c.moveToFirst();

        if (c.getCount() == 0) {
            Logger.tag(TAG).error("Can't find sound ID " + mSoundId);
            finish();
            return;
        }

        mSound = new ObservationSound(c);

        c.close();

        mSoundPlayer = new SoundPlayer(ObservationSoundViewer.this, soundPlayerContainer, mSound, this);
        View view = mSoundPlayer.getView();
        soundPlayerContainer.addView(view, 0);

        mDeleteSoundButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent data = new Intent();
                data.putExtra(DELETE_SOUND_ID, mSoundId);
                setResult(RESULT_OK, data);
                finish();
            }
        });

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
    protected void onPause() {
        super.onPause();

        if (mSoundPlayer != null) {
            mSoundPlayer.pause();
        }
    }

    @Override
    public void onPlay(SoundPlayer player) {

    }

    @Override
    public void onPause(SoundPlayer player) {

    }
}
