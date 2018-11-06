package org.inaturalist.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.cocosw.bottomsheet.BottomSheet;
import com.evernote.android.state.State;
import com.flurry.android.FlurryAgent;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.livefront.bridge.Bridge;

import org.apache.sanselan.util.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.UUID;

public class ProfileEditor extends AppCompatActivity {
    private static final String TAG = "ProfileEditor";

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1000;

    private INaturalistApp mApp;

    private ActivityHelper mHelper;

    private ImageView mUserPic;
    private EditText mUserNameText;
    private EditText mUserFullNameText;
    private EditText mUserEmailText;
    private EditText mUserBioText;
    private EditText mUserPasswordText;
    private Button mViewProfile;

    @State(AndroidStateBundlers.UriBundler.class) public Uri mFileUri;
    @State public String mUserName;
    @State public String mUserBio;
    @State public String mUserFullName;
    @State public String mUserEmail;
    @State public String mUserIconUrl;
    private UserUpdateReceiver mUserUpdateReceiver;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_profile:
                if (!isNetworkAvailable()) {
                    mHelper.alert(getString(R.string.not_connected));
                    return true;
                }

                Intent serviceIntent = new Intent(INaturalistService.ACTION_UPDATE_USER_DETAILS, null, ProfileEditor.this, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.ACTION_USERNAME, mUserNameText.getText().toString());
                serviceIntent.putExtra(INaturalistService.ACTION_FULL_NAME, mUserFullNameText.getText().toString());
                serviceIntent.putExtra(INaturalistService.ACTION_USER_EMAIL, mUserEmailText.getText().toString());
                serviceIntent.putExtra(INaturalistService.ACTION_USER_BIO, mUserBioText.getText().toString());
                serviceIntent.putExtra(INaturalistService.ACTION_USER_PASSWORD, mUserPasswordText.getText().toString());

                SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                String iconUrl = prefs.getString("user_icon_url", null);

                if (!mUserNameText.getText().toString().equals(prefs.getString("username", ""))) {
                    // Username changed
                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_PROFILE_USERNAME_CHANGED);
                }

                if ((mUserIconUrl == null) && (iconUrl != null)) {
                    // Delete profile pic
                    serviceIntent.putExtra(INaturalistService.ACTION_USER_DELETE_PIC, true);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_PROFILE_PHOTO_REMOVED);
                }

                if ((mUserIconUrl != null) && (!mUserIconUrl.equals(iconUrl))) {
                    // New profile pic - make a copy of it

                    try {
                        JSONObject eventParams = new JSONObject();
                        eventParams.put(AnalyticsClient.EVENT_PARAM_ALREADY_HAD_PHOTO, iconUrl != null ? AnalyticsClient.EVENT_PARAM_VALUE_YES : AnalyticsClient.EVENT_PARAM_VALUE_NO);

                        AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_PROFILE_PHOTO_CHANGED, eventParams);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                    try {
                        String newFilename = null;
                        File outputFile = new File(getExternalCacheDir(), UUID.randomUUID().toString() + ".jpeg");
                        OutputStream os = new FileOutputStream(outputFile);
                        InputStream is = getContentResolver().openInputStream(Uri.parse(mUserIconUrl));
                        IOUtils.copyStreamToStream(is, os);
                        is.close();
                        os.close();

                        newFilename = outputFile.getAbsolutePath();

                        serviceIntent.putExtra(INaturalistService.ACTION_USER_PIC, newFilename);

                    } catch (Exception exc) {
                        exc.printStackTrace();
                        showError(null);
                        return true;
                    }
                }

                startService(serviceIntent);

                mHelper.loading(getString(R.string.updating_profile));

                return true;

            case android.R.id.home:
                this.onBackPressed();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        if (!isDirty()) {
            super.onBackPressed();
        } else {
            mHelper.confirm(getString(R.string.edit_profile), getString(R.string.discard_changes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            }, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            }, R.string.yes, R.string.no);
        }

    }

    private boolean isDirty() {
        SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);

        if (!mUserNameText.getText().toString().equals(prefs.getString("username", ""))) return true;
        if (!mUserFullNameText.getText().toString().equals(prefs.getString("user_full_name", ""))) return true;
        if (!mUserBioText.getText().toString().equals(prefs.getString("user_bio", ""))) return true;
        if (!mUserPasswordText.getText().toString().equals("")) return true;
        if (!mUserEmailText.getText().toString().equals(prefs.getString("user_email", ""))) return true;
        String iconUrl = prefs.getString("user_icon_url", null);
        if (((iconUrl != null) && (mUserIconUrl == null)) || ((iconUrl == null) && (mUserIconUrl != null)) || ((mUserIconUrl != null) && !mUserIconUrl.equals(iconUrl))) return true;

        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.edit_profile_menu, menu);

        return true;
    }

 
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bridge.restoreInstanceState(this, savedInstanceState);

        StrictMode.VmPolicy.Builder newBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(newBuilder.build());

        mHelper = new ActivityHelper(this);

        final Intent intent = getIntent();
        setContentView(R.layout.edit_profile);


        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(R.string.edit_profile);

        mUserNameText = (EditText) findViewById(R.id.user_name);
        mUserFullNameText = (EditText) findViewById(R.id.full_name);
        mUserEmailText = (EditText) findViewById(R.id.email);
        mUserBioText = (EditText) findViewById(R.id.bio);
        mUserPasswordText = (EditText) findViewById(R.id.password);
        mUserPic = (ImageView) findViewById(R.id.user_pic);
        mViewProfile = (Button) findViewById(R.id.view_profile);

        if (mApp == null) {
            mApp = (INaturalistApp)getApplicationContext();
        }

        mViewProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
                String username = prefs.getString("username", "");
                Intent intent = new Intent(ProfileEditor.this, UserProfile.class);
                BetterJSONObject userObj = new BetterJSONObject();
                userObj.put("login", username);
                intent.putExtra("user", userObj);
                startActivity(intent);
            }
        });

        mUserPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new BottomSheet.Builder(ProfileEditor.this).sheet(mUserIconUrl != null ? R.menu.profile_editor_photo_menu : R.menu.profile_editor_no_photo_menu).listener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent;
                        switch (which) {
                            case R.id.camera:
                                takePhoto();
                                break;
                            case R.id.upload_photo:
                                choosePhoto();
                                break;
                            case R.id.remove_photo:
                                removePhoto();
                                break;
                        }
                    }
                }).show();
            }
        });

        if (savedInstanceState == null) {
            SharedPreferences prefs = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
            mUserName = prefs.getString("username", "");
            mUserFullName = prefs.getString("user_full_name", "");
            mUserBio = prefs.getString("user_bio", "");
            mUserEmail = prefs.getString("user_email", "");
            mUserIconUrl = prefs.getString("user_icon_url", null);
        }

        refreshUserDetails();

        mUserUpdateReceiver = new UserUpdateReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_UPDATE_USER_DETAILS_RESULT);
        Log.i(TAG, "Registering ACTION_UPDATE_USER_DETAILS_RESULT");
        BaseFragmentActivity.safeRegisterReceiver(mUserUpdateReceiver, filter, this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mUserUpdateReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mApp == null) {
            mApp = (INaturalistApp) getApplicationContext();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mUserName = mUserNameText.getText().toString();
        mUserFullName = mUserFullNameText.getText().toString();
        mUserEmail = mUserEmailText.getText().toString();
        mUserBio = mUserBioText.getText().toString();

        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }

    private void refreshUserDetails() {
        String iconUrl = mUserIconUrl;

        mUserNameText.setText(mUserName);
        mUserFullNameText.setText(mUserFullName);
        mUserBioText.setText(mUserBio);
        mUserEmailText.setText(mUserEmail);

        if ((iconUrl != null) && (iconUrl.length() > 0)) {
            if (!mUserIconUrl.startsWith("http")) {
                mUserPic.setImageURI(Uri.parse(mUserIconUrl));
            } else {
                UrlImageViewHelper.setUrlDrawable(mUserPic, iconUrl + "?edit=1", new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                        return centerCrop;
                    }
                });
            }

        } else {
            mUserPic.setImageResource(R.drawable.ic_person_white_24dp);
        }

    }


    private void takePhoto() {
        if (!mApp.isCameraPermissionGranted()) {
            mApp.requestCameraPermission(this, new INaturalistApp.OnRequestPermissionResult() {
                @Override
                public void onPermissionGranted() {
                    takePhoto();
                }

                @Override
                public void onPermissionDenied() {

                }
            });
            return;
        }

        // Temp file for the photo
        mFileUri = Uri.fromFile(new File(getExternalCacheDir(), UUID.randomUUID().toString() + ".jpeg"));

        final Intent galleryIntent = new Intent();

        galleryIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        galleryIntent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);
        this.startActivityForResult(galleryIntent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    private void choosePhoto() {

        if (mApp.isExternalStoragePermissionGranted()) {
            final Intent galleryIntent = new Intent();
            galleryIntent.setType("image/*");
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
            this.startActivityForResult(galleryIntent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);

            return;
        }

        mApp.requestExternalStoragePermission(ProfileEditor.this, new INaturalistApp.OnRequestPermissionResult() {
            @Override
            public void onPermissionGranted() {
                choosePhoto();
            }

            @Override
            public void onPermissionDenied() {
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                final boolean isCamera;

                if ((data == null) || (data.getScheme() == null)) {
                    isCamera = true;
                } else {
                    final String action = data.getAction();
                    if(action == null) {
                        isCamera = false;
                    } else {
                        isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    }
                }

                Uri selectedImageUri;
                if(isCamera) {
                    selectedImageUri = mFileUri;
                } else {
                    selectedImageUri = data == null ? null : data.getData();
                }

                mUserIconUrl = selectedImageUri.toString();

                Log.v(TAG, String.format("%s: %s", isCamera, selectedImageUri));

                refreshUserDetails();

            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
            } else {
                // Image capture failed, advise user
                Toast.makeText(this,  String.format(getString(R.string.something_went_wrong), mFileUri.toString()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + (data == null ? "null" : data.getData()));
            }

        }

    }

    public void removePhoto() {
        mHelper.confirm(getString(R.string.delete_photo), getString(R.string.are_you_sure_you_want_to_remove_photo), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mFileUri = null;
                mUserIconUrl = null;
                refreshUserDetails();
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        }, R.string.yes, R.string.no);
    }


    private class UserUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Got ACTION_UPDATE_USER_DETAILS_RESULT");
            BetterJSONObject user = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.USER);

            if (user == null) {
                showError(null);
                return;
            } else if (user.has("errors")) {
                // Show all errors returned from server (usually means username is already taken)
                JSONObject errors = user.getJSONObject("errors");
                StringBuilder allErrors = new StringBuilder();
                Iterator<String> iter = errors.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    allErrors.append(key);
                    allErrors.append(" ");
                    allErrors.append(errors.optJSONArray(key).optString(0));
                    if (iter.hasNext()) allErrors.append("; ");
                }

                showError(allErrors.toString());
                return;
            }

            mHelper.stopLoading();
            refreshUserDetails();
            finish();
        }
    }

    private void showError(String error) {
        mHelper.stopLoading();
        String message;
        if (error == null) {
            message = getString(R.string.there_was_a_problem_updating_profile);
        } else {
            message = String.format(getString(R.string.there_was_a_problem_updating_profile_params), error);
        }
        mHelper.alert(message);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        mApp.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
