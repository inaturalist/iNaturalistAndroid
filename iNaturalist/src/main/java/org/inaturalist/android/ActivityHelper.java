package org.inaturalist.android;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

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

    public void alert(int title, int msg) {
        alert(mContext.getString(title), mContext.getString(msg));
    }

    public void alert(String title, String msg) {
        confirm(title, msg, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        }, null);
    }

    public void selection(String title, String[] items, final DialogInterface.OnClickListener onItemSelected) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View titleBar = inflater.inflate(R.layout.dialog_title, null, false);
        ((TextView)titleBar.findViewById(R.id.title)).setText(title);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);


        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.dialog_title_top_bar, null, false);
        content.addView(titleBar, 0);
        ListView listView = (ListView) inflater.inflate(R.layout.dialog_list, null, false);
        listView.setAdapter(new ArrayAdapter<String>(mContext, android.R.layout.simple_list_item_1, items));

        content.addView(listView, 2);

        builder.setView(content);
        builder.setCancelable(true);
        final AlertDialog alert = builder.create();
        alert.show();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                alert.dismiss();
                onItemSelected.onClick(alert, i);
            }
        });
   }


    public void confirm(int titleRes, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
        confirm(mContext.getString(titleRes), msg, okListener, cancelListener);
    }

    public void confirm(String title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View titleBar = inflater.inflate(R.layout.dialog_title, null, false);
        ((TextView)titleBar.findViewById(R.id.title)).setText(title);

        confirm(titleBar, msg, okListener, cancelListener);
    }


    public void confirm(String title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener, int okText, int cancelText) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View titleBar = inflater.inflate(R.layout.dialog_title, null, false);
        ((TextView)titleBar.findViewById(R.id.title)).setText(title);

        confirm(titleBar, msg, okListener, cancelListener, okText, cancelText);
    }


    public void confirm(View title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
        confirm(title, msg, okListener, cancelListener, R.string.ok, R.string.cancel);
    }

    public void confirm(View title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener, int okText, int cancelText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.dialog_title_top_bar, null, false);
        content.addView(title, 0);

        if (msg instanceof Integer) {
            msg = mContext.getString((Integer) msg);
        }

        TextView textContent;

        if (msg instanceof String) {
            textContent = (TextView) inflater.inflate(R.layout.dialog_message, null, false);
            textContent.setText((String)msg);
            content.addView(textContent, 2);
        } else if (msg instanceof View) {
            content.addView((View) msg, 2);
        }
        builder.setView(content);
        builder.setPositiveButton(okText, okListener);
        builder.setCancelable(false);
        if (cancelListener != null) builder.setNegativeButton(cancelText, cancelListener);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void loading(String title, String msg) {
        if (title == null) { title = ""; }
        if (msg == null) { msg = mContext.getString(R.string.loading); }

        if (mProgressDialog != null) {
            mProgressDialog.setTitle(title);
            mProgressDialog.setMessage(msg);
        } else {
        	try {
        		mProgressDialog = ProgressDialog.show(mContext, title, msg, true);
        		mProgressDialog.setCancelable(false);
        	} catch (Exception exc) {
        		exc.printStackTrace();
        		mProgressDialog = null;
        	}
        }
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
        		mProgressDialog = null;
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
            return Color.WHITE;
        }
    }
}
