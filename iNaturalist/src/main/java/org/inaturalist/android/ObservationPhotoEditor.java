package org.inaturalist.android;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;
import com.yalantis.ucrop.UCropFragment;
import com.yalantis.ucrop.UCropFragmentCallback;

import org.tinylog.Logger;

import java.io.File;
import java.util.UUID;

public class ObservationPhotoEditor extends AppCompatActivity implements UCropFragmentCallback {
    public static final String PHOTO_URI = "photo_uri";
    private static final String TAG = "ObservationPhotoEditor";

    private String mToolbarTitle;
    @DrawableRes
    private int mToolbarCancelDrawable;
    @DrawableRes
    private int mToolbarCropDrawable;
    private UCropFragment mFragment;
    // Enables dynamic coloring
    private int mToolbarColor;
    private int mStatusBarColor;
    private int mToolbarWidgetColor;

    private Toolbar mToolbar;
    private boolean mShowLoader;

    private ActivityHelper mHelper;
    private INaturalistApp mApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mApp = (INaturalistApp) getApplicationContext();
        mApp.applyLocaleSettings(getBaseContext());
        
        setContentView(R.layout.observation_photo_editor);

        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        if (savedInstanceState == null) {
            String uri = intent.getStringExtra(PHOTO_URI);
            editPhoto(Uri.parse(uri));
        }
    }

    private void editPhoto(Uri sourceUri) {
        Uri destUri = Uri.fromFile(new File(getExternalCacheDir(), UUID.randomUUID().toString() + ".jpeg"));
        UCrop uCrop = UCrop.of(sourceUri, destUri);

        // Configure uCrop

        uCrop = uCrop.useSourceImageAspectRatio();

        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setFreeStyleCropEnabled(true);
        options.setAllowedGestures(UCropActivity.SCALE, UCropActivity.NONE, UCropActivity.SCALE);
        options.setToolbarColor(Color.parseColor("#FFFFFF"));
        options.setActiveWidgetColor(Color.parseColor("#74AC00"));
        options.setStatusBarColor(Color.parseColor("#74AC00"));
        options.setToolbarWidgetColor(Color.parseColor("#FFFFFF"));
        options.setRootViewBackgroundColor(Color.parseColor("#74AC00"));
        options.setCropGridColumnCount(2);
        options.setCropGridRowCount(1);
        options.setShowCropGrid(false);
        options.setActiveControlsWidgetColor(Color.parseColor("#74AC00"));
        options.setRootViewBackgroundColor(Color.parseColor("#FFFFFF"));
        options.setToolbarCancelDrawable(R.drawable.ic_arrow_back_white_24dp);

        uCrop = uCrop.withOptions(options);

        setupFragment(uCrop);
    }

    public void setupFragment(UCrop uCrop) {
        mFragment = uCrop.getFragment(uCrop.getIntent(this).getExtras());
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, mFragment, UCropFragment.TAG)
                .commitAllowingStateLoss();

        setupViews(uCrop.getIntent(this).getExtras());
    }

    public void setupViews(Bundle args) {
        mStatusBarColor = Color.parseColor("#FFFFFF");
        mToolbarColor = Color.parseColor("#FFFFFF");
        mToolbarCancelDrawable = args.getInt(UCrop.Options.EXTRA_UCROP_WIDGET_CANCEL_DRAWABLE, R.drawable.ucrop_ic_cross);
        mToolbarCropDrawable = args.getInt(UCrop.Options.EXTRA_UCROP_WIDGET_CROP_DRAWABLE, R.drawable.ucrop_ic_done);
        mToolbarWidgetColor = Color.parseColor("#000000");
        mToolbarTitle = args.getString(UCrop.Options.EXTRA_UCROP_TITLE_TEXT_TOOLBAR);
        mToolbarTitle = mToolbarTitle != null ? mToolbarTitle : getResources().getString(R.string.ucrop_label_edit_photo);

        setupAppBar();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.ucrop_menu_activity, menu);

        // Change crop & loader menu icons color to match the rest of the UI colors

        MenuItem menuItemLoader = menu.findItem(R.id.menu_loader);
        Drawable menuItemLoaderIcon = menuItemLoader.getIcon();
        if (menuItemLoaderIcon != null) {
            try {
                menuItemLoaderIcon.mutate();
                menuItemLoaderIcon.setColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP);
                menuItemLoader.setIcon(menuItemLoaderIcon);
            } catch (IllegalStateException e) {
                Log.i(this.getClass().getName(), String.format("%s - %s", e.getMessage(), getString(R.string.ucrop_mutate_exception_hint)));
            }
            ((Animatable) menuItemLoader.getIcon()).start();
        }

        MenuItem menuItemCrop = menu.findItem(R.id.menu_crop);
        Drawable menuItemCropIcon = ContextCompat.getDrawable(this, mToolbarCropDrawable == 0 ? R.drawable.ucrop_ic_done : mToolbarCropDrawable);
        if (menuItemCropIcon != null) {
            menuItemCropIcon.mutate();
            menuItemCropIcon.setColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP);
            menuItemCrop.setIcon(menuItemCropIcon);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_crop).setVisible(!mShowLoader);
        menu.findItem(R.id.menu_loader).setVisible(mShowLoader);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_crop) {
            if (mFragment != null && mFragment.isAdded())
                mFragment.cropAndSaveImage();
        } else if (item.getItemId() == android.R.id.home) {
            Logger.tag(TAG).info("Dirty:" + mFragment.isDirty());
            setResult(RESULT_CANCELED);
            closeActivity();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mFragment != null) Logger.tag(TAG).info("Dirty:" + mFragment.isDirty());
        closeActivity();
    }

    public void closeActivity() {
        if (mFragment != null && mFragment.isDirty()) {
            // Display a confirmation dialog
            mHelper.confirm(
                    getString(R.string.edit_observation),
                    getString(R.string.discard_changes),
                    (dialogInterface, i) -> finish(),
                    (dialogInterface, i) -> { },
                    R.string.yes, R.string.no
                    );

        } else {
            finish();
        }
    }


    /**
     * Configures and styles both status bar and mToolbar.
     */
    private void setupAppBar() {
        mToolbar = findViewById(R.id.toolbar);

        // Set all of the Toolbar coloring
        mToolbar.setBackgroundColor(mToolbarColor);
        mToolbar.setTitleTextColor(mToolbarWidgetColor);
        mToolbar.setVisibility(View.VISIBLE);
        final TextView toolbarTitle = mToolbar.findViewById(R.id.toolbar_title);
        toolbarTitle.setTextColor(Color.parseColor("#000000"));
        toolbarTitle.setText(mToolbarTitle);

        // Color buttons inside the Toolbar
        Drawable stateButtonDrawable = ContextCompat.getDrawable(getBaseContext(), mToolbarCancelDrawable);
        if (stateButtonDrawable != null) {
            stateButtonDrawable.mutate();
            stateButtonDrawable.setColorFilter(Color.parseColor("#000000"), PorterDuff.Mode.SRC_ATOP);
            mToolbar.setNavigationIcon(stateButtonDrawable);
        }

        setSupportActionBar(mToolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    public void loadingProgress(boolean showLoader) {
        mShowLoader = showLoader;
        supportInvalidateOptionsMenu();
    }


    @Override
    public void onCropFinish(UCropFragment.UCropResult result) {
        switch (result.mResultCode) {
            case RESULT_OK:
                Uri resultUri = UCrop.getOutput(result.mResultData);

                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putParcelable(UCrop.EXTRA_OUTPUT_URI, resultUri);
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);
                break;

            case UCrop.RESULT_ERROR:
                final Throwable cropError = UCrop.getError(result.mResultData);
                if (cropError != null) {
                    String errorMessage = cropError.getMessage();
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG);
                }
                setResult(RESULT_CANCELED);
                break;
        }
        finish();
    }

}
