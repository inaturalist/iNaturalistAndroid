package org.inaturalist.android;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class ActivityHelper {
    private static String TAG = "ActivityHelper";
    private final INaturalistApp mApp;
    private Context mContext;
    private ProgressDialog mProgressDialog;

    public ActivityHelper(Context context) {
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
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

    public interface OnMultipleChoices {
        void onMultipleChoices(Set<Integer> selectedPositions);
    }

    public void multipleChoiceSelection(String title, List<String> items, Set<Integer> selectedPositions, final OnMultipleChoices onOk) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.dialog_multiple_choice_list_popup, null, false);
        ((TextView)content.findViewById(R.id.title)).setText(title);
        ListView listView = (ListView) content.findViewById(R.id.list_view);
        final MultipleChoiceAdapter adapter = new MultipleChoiceAdapter(mContext, items, selectedPositions);
        listView.setAdapter(adapter);

        builder.setView(content);
        builder.setCancelable(true);
        final AlertDialog alert = builder.create();

        content.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alert.cancel();
            }
        });

        content.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onOk != null) onOk.onMultipleChoices(adapter.getSelectedItems());
                alert.dismiss();
            }
        });

        alert.show();
   }

    public void selection(String title, ListAdapter adapter) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.dialog_list_popup, null, false);
        ((TextView)content.findViewById(R.id.title)).setText(title);
        ListView listView = (ListView) content.findViewById(R.id.list_view);
        listView.setAdapter(adapter);

        builder.setView(content);
        builder.setCancelable(true);
        final AlertDialog alert = builder.create();

        content.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alert.cancel();
            }
        });

        alert.show();
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

    public void confirm(int titleRes, int messageRes, int okText) {
        confirm(mContext.getString(titleRes), mContext.getString(messageRes), null, null, okText, 0);
    }

    public void confirm(int titleRes, int messageRes, int okText, DialogInterface.OnClickListener okListener) {
        confirm(mContext.getString(titleRes), mContext.getString(messageRes), okListener, null, okText, 0);
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

     public void confirm(String title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener, String okText, String cancelText) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View titleBar = inflater.inflate(R.layout.dialog_title, null, false);
        ((TextView)titleBar.findViewById(R.id.title)).setText(title);

        confirm(titleBar, msg, okListener, cancelListener, okText, cancelText);
    }



    public void confirm(View title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener) {
        confirm(title, msg, okListener, cancelListener, R.string.ok, R.string.cancel);
    }

    public void confirm(View title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener, String okText, String cancelText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.dialog_title_top_bar, null, false);

        if (title != null) content.addView(title, 0);

        if (msg instanceof Integer) {
            msg = mContext.getString((Integer) msg);
        }

        TextView textContent;

        if (msg instanceof String) {
            ScrollView parentView = (ScrollView) inflater.inflate(R.layout.dialog_message, null, false);
            textContent = (TextView) parentView.findViewById(R.id.text);
            textContent.setText(Html.fromHtml((String)msg));
            Linkify.addLinks(textContent, Linkify.ALL);
            textContent.setMovementMethod(LinkMovementMethod.getInstance());
            content.addView(parentView, title != null ? 2 : 1);
        } else if (msg instanceof View) {
            content.addView((View) msg, title != null ? 2 : 1);
        }
        builder.setView(content);
        builder.setPositiveButton(okText, okListener);
        builder.setCancelable(false);
        if (cancelListener != null) builder.setNegativeButton(cancelText, cancelListener);
        AlertDialog alert = builder.create();
        alert.show();
        alert.getButton(alert.BUTTON_POSITIVE).setTextColor(Color.parseColor("#85B623"));
        alert.getButton(alert.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#85B623"));
    }

    public void confirm(View title, Object msg, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener, int okText, int cancelText) {
        confirm(title, msg, okListener, cancelListener, mContext.getString(okText), cancelText != 0 ? mContext.getString(cancelText) : null);
    }

    public void loading(String title, String msg) {
        loading(title, msg, null);
    }

    public void loading(String title, String msg, final DialogInterface.OnClickListener onCancelCb) {
        if (title == null) { title = ""; }
        if (msg == null) { msg = mContext.getString(R.string.loading); }

        boolean newDialog = false;
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(mContext);
            newDialog = true;
        }
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(msg);

        if (onCancelCb != null) {
            mProgressDialog.setCancelable(false);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, mContext.getString(R.string.cancel), onCancelCb);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    onCancelCb.onClick(dialogInterface, 0);
                }
            });
        } else {
            mProgressDialog.setCancelable(false);
        }

        if (newDialog) mProgressDialog.show();
    }

    public void loading(String msg) {
        loading(null, msg);
    }

    public void loading(String msg, DialogInterface.OnClickListener onCancel) {
        loading(null, msg, onCancel);
    }

    public void loading() {
        loading(null, null, null);
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

    private CameraUpdate addCircle(GoogleMap map, LatLng latlng, int radius, Observation observation) {
        return addCircle(map, latlng, radius, observation, true);
    }

    private CameraUpdate addCircle(GoogleMap map, LatLng latlng, int radius, Observation observation, boolean updateCamera) {
        int obsColor = observationColor(observation);
        CircleOptions opts = new CircleOptions()
                .center(latlng)
                .radius(radius)
                .fillColor(0x80FFFFFF & obsColor) // Add 50% opacity
                .strokeColor(obsColor);
        map.addCircle(opts);
        if (!updateCamera) {
            return null;
        }
        LatLngBounds bounds = new LatLngBounds.Builder().
                include(SphericalUtil.computeOffset(latlng, radius, 0)).
                include(SphericalUtil.computeOffset(latlng, radius, 90)).
                include(SphericalUtil.computeOffset(latlng, radius, 180)).
                include(SphericalUtil.computeOffset(latlng, radius, 270)).build();
        return CameraUpdateFactory.newLatLngBounds(bounds, 10);
    }

    public void addMapPosition(final GoogleMap map, Observation observation, BetterJSONObject observationJson) {
        addMapPosition(map, observation, observationJson, false);
    }

    public void addMapPosition(final GoogleMap map, Observation observation, BetterJSONObject observationJson, boolean markerOnly) {
        addMapPosition(map, observation, observationJson, markerOnly, true);
    }

    public void addMapPosition(final GoogleMap map, Observation observation, BetterJSONObject observationJson, boolean markerOnly, boolean updateCamera) {
        Double lat, lon;
        lat = observation.private_latitude == null ? observation.latitude : observation.private_latitude;
        lon = observation.private_longitude == null ? observation.longitude : observation.private_longitude;
        if (lat == null || lon == null) {
            // can't do anything
            return;
        }
        LatLng latlng = new LatLng(lat, lon);
        BitmapDescriptor obsIcon = TaxonUtils.observationMarkerIcon(observation.iconic_taxon_name);
        String currentUser = mApp.currentUserLogin();
        CameraUpdate cameraUpdate = null;
        int obsColor = observationColor(observation);

        Integer publicAcc = null;
        publicAcc = observationJson != null ? observationJson.getInteger("public_positional_accuracy") : null;

        // Add a single marker
        MarkerOptions opts = new MarkerOptions().position(latlng).icon(obsIcon);
        map.addMarker(opts);

        if (((observation.geoprivacy != null) && (observation.geoprivacy.equals("private"))) ||
                (publicAcc == null) || (markerOnly)) {
            // No need to add anything other than the above marker
            cameraUpdate = CameraUpdateFactory.newLatLngZoom(latlng, 15);

        } else if ((currentUser != null) && (observation.user_login != null) && (observation.user_login.equals(currentUser)) &&
                (observation.positional_accuracy != null)) {
            // Show circle of private positional accuracy
            cameraUpdate = addCircle(map, latlng, observation.positional_accuracy, observation, updateCamera);
        } else {
            if ((observation.positional_accuracy != null) && (publicAcc != null) &&
                    (observation.positional_accuracy.equals(publicAcc))) {
                // Show circle of public positional accuracy
                cameraUpdate = addCircle(map, latlng, publicAcc, observation, updateCamera);
            } else if ((currentUser == null) || (observation.user_login == null) || (!observation.user_login.equals(currentUser))) {
                // Show uncertainty cell
                Double cellSize = 0.2;
                Double coords[] = new Double[] { lat, lon };
                Double ll[] = new Double[] {
                        coords[0] - ( coords[0] % cellSize ),
                        coords[1] - ( coords[1] % cellSize ) };
                Double uu[] = new Double[] { ll[0], ll[1] };

                for (int i = 0; i < coords.length; i++) {
                    if (coords[i] < uu[i]) {
                        uu[i] -= cellSize;
                    } else {
                        uu[i] += cellSize;
                    }
                }

                LatLng rectPoints[] = new LatLng[] {
                        new LatLng(Math.min(uu[0], ll[0]), Math.min(uu[1], ll[1])),
                        new LatLng(Math.max(uu[0], ll[0]), Math.min(uu[1], ll[1])),
                        new LatLng(Math.max(uu[0], ll[0]), Math.max(uu[1], ll[1])),
                        new LatLng(Math.min(uu[0], ll[0]), Math.max(uu[1], ll[1])),
                        new LatLng(Math.min(uu[0], ll[0]), Math.min(uu[1], ll[1]))
                };

                PolygonOptions polygonOpts = new PolygonOptions()
                        .add(rectPoints[0])
                        .add(rectPoints[1])
                        .add(rectPoints[2])
                        .add(rectPoints[3])
                        .add(rectPoints[4])
                        .fillColor(0x80FFFFFF & obsColor) // Add 50% opacity
                        .strokeColor(obsColor);
                map.addPolygon(polygonOpts);

                if (updateCamera) {
                    cameraUpdate = CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder()
                            .include(rectPoints[0])
                            .include(rectPoints[1])
                            .include(rectPoints[2])
                            .include(rectPoints[3])
                            .include(rectPoints[4])
                            .build(), 10);
                }
            }

        }

        final CameraUpdate finalCameraUpdate = cameraUpdate;
        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                try {
                    if (finalCameraUpdate != null) map.moveCamera(finalCameraUpdate);
                } catch (IllegalStateException exc) {
                    // Handles weird exception is raised ("View size is too small after padding is applied")
                    exc.printStackTrace();
                }
            }
        });

    }

    public void centerObservationImmediate(final GoogleMap map, Observation observation) {
        Double lat, lon;
        lat = observation.private_latitude == null ? observation.latitude : observation.private_latitude;
        lon = observation.private_longitude == null ? observation.longitude : observation.private_longitude;
        if (lat == null || lon == null) {
            // can't do anything with no coordinates
            return;
        }
        LatLng latlng = new LatLng(lat, lon);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(latlng);
        final CameraUpdate finalCameraUpdate = cameraUpdate;
        try {
            if (finalCameraUpdate != null) {
                map.moveCamera(finalCameraUpdate);
            }
        } catch (IllegalStateException exc) {
            // Handles weird exception is raised ("View size is too small after padding is applied")
            exc.printStackTrace();
        }
    }
    public void centerObservation(final GoogleMap map, Observation observation) {
        Double lat, lon;
        lat = observation.private_latitude == null ? observation.latitude : observation.private_latitude;
        lon = observation.private_longitude == null ? observation.longitude : observation.private_longitude;
        if (lat == null || lon == null) {
            // can't do anything with no coordinates
            return;
        }
        LatLng latlng = new LatLng(lat, lon);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(latlng);
        final CameraUpdate finalCameraUpdate = cameraUpdate;
        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                try {
                    if (finalCameraUpdate != null) {
                        map.moveCamera(finalCameraUpdate);
                    }
                } catch (IllegalStateException exc) {
                    // Handles weird exception is raised ("View size is too small after padding is applied")
                    exc.printStackTrace();
                }
            }
        });
    }

    /**
     * Sets ListView height dynamically based on the height of the items.
     *
     * @param list to be resized
     */
    public static void resizeList(final ListView list) {
        final Handler handler = new Handler();
        if ((list.getVisibility() == View.VISIBLE) && (list.getWidth() == 0)) {
            // UI not initialized yet - try later
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resizeList(list);
                }
            }, 100);

            return;
        }

        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                int height = setListViewHeightBasedOnItems(list);
            }
        });
    }

    public static int setListViewHeightBasedOnItems(final ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter != null) {

            int numberOfItems = listAdapter.getCount();

            // Get total height of all items.
            int totalItemsHeight = 0;
            for (int itemPos = 0; itemPos < numberOfItems; itemPos++) {
                View item = listAdapter.getView(itemPos, null, listView);
                item.measure(View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
                totalItemsHeight += item.getMeasuredHeight();
            }

            // Get total height of all item dividers.
            int totalDividersHeight = listView.getDividerHeight() *
                    (numberOfItems - 1);

            // Set list height.
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            int newHeight = totalItemsHeight + totalDividersHeight;
            if (params.height != newHeight) {
                params.height = totalItemsHeight + totalDividersHeight;
                listView.setLayoutParams(params);
                listView.requestLayout();
            }

            return params.height;

        } else {
            return 0;
        }
    }

    /** Checks if the list is scrollable (meaning, more items visible than space available) */
    public interface isListScrollable {
        void isListScrollable(boolean scrollable);
    }

    public static void willListScroll(final ListView listView, final isListScrollable cb) {
        listView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    listView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    listView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }

                int last = listView.getChildCount() - 1; // Last visible listitem view
                View firstChild = listView.getChildAt(0);
                View lastChild = listView.getChildAt(last);

                if ((firstChild == null) || (lastChild == null)) {
                    return;
                }

                if ((lastChild.getBottom() > listView.getHeight()) || (firstChild.getTop() < 0)) {
                    // Either the first visible list item is cutoff or the last is cutoff
                    cb.isListScrollable(true);
                } else {
                    if (listView.getChildCount() == listView.getCount()) {
                        // All visible listitem views are all the items there are (nowhere to scroll)
                        cb.isListScrollable(false);
                    }
                    else {
                        // No listitem views are cut off but there are other listitem views to scroll to
                        cb.isListScrollable(true);
                    }
                }
            }
        });
    }

    public float dpToPx(int dp) {
        Resources r = mContext.getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }



}
