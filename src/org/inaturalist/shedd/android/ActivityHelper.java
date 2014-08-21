package org.inaturalist.shedd.android;

import org.inaturalist.shedd.android.R;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
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
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void confirm(String title, String msg, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            })
            .setPositiveButton(R.string.ok, listener);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void confirm(String msg, DialogInterface.OnClickListener listener) {
        confirm(null, msg, listener);
    }

    public void loading(String title, String msg) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        if (title == null) { title = ""; }
        if (msg == null) { msg = mContext.getString(R.string.loading); }
        mProgressDialog = ProgressDialog.show(mContext, title, msg, true);
        mProgressDialog.setCancelable(true);
    }

    public void loading(String msg) {
        loading(null, msg);
    }

    public void loading() {
        loading(null, null);
    }
    
    public boolean isLoading() {
        return mProgressDialog != null && mProgressDialog.isShowing();
    }

    public void stopLoading() {
        if (mProgressDialog != null) {
        	try {
        		mProgressDialog.dismiss();
        	} catch (Exception exc) {
        		// Nothing to do here
        		exc.printStackTrace();
        	}
        }
    }
    
    public int observationColor(Observation o) {
        if (o.iconic_taxon_name== null) {
            return Color.BLACK;
        } else if (o.iconic_taxon_name.equals("Animalia") || 
                o.iconic_taxon_name.equals("Actinopterygii") ||
                o.iconic_taxon_name.equals("Amphibia") || 
                o.iconic_taxon_name.equals("Reptilia") || 
                o.iconic_taxon_name.equals("Aves") || 
                o.iconic_taxon_name.equals("Mammalia")) {
            return Color.parseColor("#1E90FF");
        } else if (o.iconic_taxon_name.equals("Insecta") || 
                o.iconic_taxon_name.equals("Arachnida") ||
                o.iconic_taxon_name.equals("Mollusca")) {
            return Color.parseColor("#FF4500");
        } else if (o.iconic_taxon_name.equals("Protozoa")) {
            return Color.parseColor("#691776");
        } else if (o.iconic_taxon_name.equals("Plantae")) {
            return Color.parseColor("#73AC13");
        } else if (o.iconic_taxon_name.equals("Fungi")) {
            return Color.parseColor("#FF1493");
        } else if (o.iconic_taxon_name.equals("Chromista")) {
            return Color.parseColor("#993300");
        } else {
            return Color.BLACK;
        }
    }
}
