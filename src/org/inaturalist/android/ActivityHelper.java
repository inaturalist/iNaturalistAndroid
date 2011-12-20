package org.inaturalist.android;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;

public class ActivityHelper {
    private static String TAG = "ActivityHelper";
    private Context mContext;
    private ProgressDialog mProgressDialog;
    
    public ActivityHelper(Context context) {
        mContext = context;
    }
    
    public void alert(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(msg)
               .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                   }
               });
        AlertDialog alert = builder.create();
        alert.show();
    }
    
    public void loading(String title, String msg) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        if (title == null) { title = ""; }
        if (msg == null) { msg = "Loading..."; }
        Log.d(TAG, "title: " + title + ", msg: " + msg);
        mProgressDialog = ProgressDialog.show(mContext, title, msg, true);
    }
    
    public void loading(String msg) {
        loading(null, msg);
    }
    
    public void loading() {
        loading(null, null);
    }
    
    public void stopLoading() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }
}
