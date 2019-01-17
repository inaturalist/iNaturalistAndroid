package org.inaturalist.android;


import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Transformation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class UserActivitiesAdapter extends ArrayAdapter<String> {
    private static final String TAG = "UserActivitiesAdapter";

    private final IOnUpdateViewed mOnUpdateViewed;
    private final INaturalistApp mApp;
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private ObservationReceiver mObservationReceiver;
    private Map<Integer, List<Pair<View, Integer>>> mObsIdToView;
    private Map<Integer, Boolean> mObsIdBeingDownloaded;

    public interface IOnUpdateViewed {
        void onUpdateViewed(Observation obs, int position);
    }

    public UserActivitiesAdapter(Context context, ArrayList<JSONObject> results, IOnUpdateViewed onUpdateViewed) {
        super(context, android.R.layout.simple_list_item_1);

        mOnUpdateViewed = onUpdateViewed;
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mResultList = results;
        Collections.sort(mResultList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject news1, JSONObject news2) {
                BetterJSONObject news1json = new BetterJSONObject(news1);
                BetterJSONObject news2json = new BetterJSONObject(news2);

                return news2json.getTimestamp("created_at").compareTo(news1json.getTimestamp("created_at"));
            }
        });


        registerReceivers();

        mObsIdToView = new HashMap<>();
        mObsIdBeingDownloaded = new HashMap<>();
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = convertView == null ? inflater.inflate(R.layout.user_activity_item, parent, false) : convertView;
        BetterJSONObject item = new BetterJSONObject(mResultList.get(position));

        try {
            final ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            ImageView obsPic = (ImageView) view.findViewById(R.id.obs_pic);
            final TextView activityDescription = (TextView) view.findViewById(R.id.activity_description);

            obsPic.setVisibility(View.VISIBLE);

            final String dateFormatted = CommentsIdsAdapter.formatIdDate(mContext, item.getTimestamp("created_at"));
            String userName = null;
            String userIconUrl = null;
            JSONObject user = null;
            final Integer obsId = item.getInt("resource_id");

            // Viewed or not?
            view.setBackgroundResource(item.getBoolean("viewed") ? R.drawable.activity_item_background : R.drawable.activity_unviewed_item_background );

            if (item.getString("notifier_type").equals("Identification")) {
                // Identification

                final JSONObject identification = item.getJSONObject("identification");
                final JSONObject taxon = identification.getJSONObject("taxon");
                String id;
                if (mApp.getShowScientificNameFirst()) {
                    id = TaxonUtils.getTaxonScientificNameHtml(taxon, false);
                } else {
                    id = TaxonUtils.getTaxonName(mContext, taxon);
                }
                user = identification.getJSONObject("user");
                userName = user.getString("login");
                userIconUrl = user.optString("icon_url", null);

                final String description = String.format(mContext.getString(R.string.user_activity_id), userName, id, dateFormatted);
                activityDescription.setText(Html.fromHtml(description));

            } else if (item.getString("notifier_type").equals("Comment")) {
                // Comment

                JSONObject comment = item.getJSONObject("comment");
                user = comment.getJSONObject("user");
                userName = user.getString("login");
                userIconUrl = user.optString("icon_url", null);
                final String body = comment.getString("body");

                final String description = String.format(mContext.getString(R.string.user_activity_comment), userName, body, dateFormatted);
                activityDescription.setText(Html.fromHtml(description));

                ViewTreeObserver viewTreeObserver = activityDescription.getViewTreeObserver();
                final String finalUserName = userName;
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        ViewTreeObserver viewTreeObserver = activityDescription.getViewTreeObserver();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            viewTreeObserver.removeOnGlobalLayoutListener(this);
                        } else {
                            viewTreeObserver.removeGlobalOnLayoutListener(this);
                        }

                        if (activityDescription.getLineCount() > 3) {
                            // Ellipsize to 3 lines at the most
                            int endOfLastLine = activityDescription.getLayout().getLineEnd(2);
                            String descriptionNoBody = Html.fromHtml(String.format(mContext.getString(R.string.user_activity_comment), finalUserName, "", dateFormatted)).toString();
                            int charsLeft = endOfLastLine - descriptionNoBody.length() - 3;
                            String newDescription = String.format(mContext.getString(R.string.user_activity_comment), finalUserName, body.substring(0, charsLeft) + "...", dateFormatted);
                            activityDescription.setText(Html.fromHtml(newDescription));
                        }
                    }
                });
            }

            if (userName != null) {
                if (userIconUrl != null) {
                    Picasso.with(mContext).
                            load(userIconUrl).
                            placeholder(R.drawable.ic_person_black_24dp).
                            transform(new CircleTransform()).
                            into(userPic);
                } else {
                    userPic.setImageResource(R.drawable.ic_person_black_24dp);
                }

                // Make the user name and user pic clickable
                final JSONObject finalUser = user;
                final View.OnClickListener onUserClick = new View.OnClickListener() {
                   @Override
                   public void onClick(View view) {
                       // Open the user details screen
                        Intent intent = new Intent(mContext, UserProfile.class);
                        intent.putExtra("user", new BetterJSONObject(finalUser));
                        mContext.startActivity(intent);
                   }
               };

                userPic.setOnClickListener(onUserClick);
                /*
                clickify(activityDescription, userName, new OnClickListener() {
                    @Override
                    public void onClick() {
                        onUserClick.onClick(userPic);
                    }
                });*/
            }

            view.setTag(item);

            // Load obs image
            loadObsImage(obsId, view, item, position);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return view;
    }

    private void loadObsImage(int obsId, final View view, BetterJSONObject item, final int position) {
        Log.e(TAG, obsId + ": loadObsImage " + position + ":" + view);

        ImageView obsPic = (ImageView) view.findViewById(R.id.obs_pic);
        ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
        ProgressBar loadingObs = (ProgressBar) view.findViewById(R.id.loading);
        final View loadingObsOverlay = view.findViewById(R.id.loading_overlay);

        view.post(new Runnable() {
            @Override
            public void run() {
                loadingObsOverlay.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, view.getHeight()));
            }
        });

        Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[] { String.valueOf(obsId) }, null);
        if (c.getCount() == 0) {
            // Couldn't find observation (must be an old one that isn't saved locally) - download it
            c.close();

            // Show loading status
            obsPic.setImageResource(R.drawable.iconic_taxon_unknown);
            loadingObs.setVisibility(View.VISIBLE);
            loadingObsOverlay.setVisibility(View.VISIBLE);
            userPic.setVisibility(View.GONE);
            view.setBackgroundResource(R.drawable.activity_item_background);

            if (!mObsIdBeingDownloaded.containsKey(obsId) || !mObsIdBeingDownloaded.get(obsId)) {
                Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION, null, mContext, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, obsId);
                ContextCompat.startForegroundService(mContext, serviceIntent);
                Log.e(TAG, obsId + ": Start download: " + mObsIdBeingDownloaded.containsKey(obsId));
            } else {
                Log.e(TAG, obsId + ": Downloading");
            }

            mObsIdBeingDownloaded.put(obsId, true);

            // So when we get the result - we'll know which obs pic to set
            if (!mObsIdToView.containsKey(obsId)) {
                mObsIdToView.put(obsId, new ArrayList<Pair<View, Integer>>());
            }
            List<Pair<View, Integer>> views = mObsIdToView.get(obsId);
            views.add(new Pair<>(view, position));

            return;
        }
        Log.d(TAG, obsId + ": Showing");

        final Observation obs = new Observation(c);
        c.close();

        loadingObs.setVisibility(View.GONE);
        loadingObsOverlay.setVisibility(View.GONE);
        userPic.setVisibility(View.VISIBLE);
        view.setBackgroundResource(item.getBoolean("viewed") ? R.drawable.activity_item_background : R.drawable.activity_unviewed_item_background );


        // Get first image for the observation
        Cursor opc = mContext.getContentResolver().query(ObservationPhoto.CONTENT_URI, ObservationPhoto.PROJECTION, "observation_id = ?", new String[] { String.valueOf(obsId) }, ObservationPhoto.DEFAULT_SORT_ORDER);
        int iconicTaxonDrawable = ObservationCursorAdapter.getIconicTaxonDrawable(obs.iconic_taxon_name);

        if (opc.getCount() == 0) {
            // No photos for observation - just show iconic taxon image
            obsPic.setImageResource(iconicTaxonDrawable);
        } else {
            // Show first photo
            ObservationPhoto op = new ObservationPhoto(opc);
            RequestCreator rc;
            if (op.photo_url != null) {
                // Online photo
                rc = Picasso.with(mContext).
                        load(op.photo_url);
            } else {
                // Offline photo
                rc = Picasso.with(mContext).
                        load(new File(op.photo_filename));
            }
            rc.
                    placeholder(iconicTaxonDrawable).
                    fit().
                    centerCrop().
                    into(obsPic);
        }
        opc.close();

        View.OnClickListener showObs = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnUpdateViewed.onUpdateViewed(obs, position);

                // Show observation details screen
                Intent intent;
                if ((obs.user_login != null) && (obs.user_login.equals(mApp.currentUserLogin()))) {
                    // It's our own observation - allow editing it
                    Uri uri = obs.getUri();
                    intent = new Intent(Intent.ACTION_VIEW, uri, mContext, ObservationViewerActivity.class);
                } else {
                    // It's another user's observation - read only mode
                    intent = new Intent(mContext, ObservationViewerActivity.class);
                    intent.putExtra("observation", obs.toJSONObject().toString());
                    intent.putExtra("read_only", true);
                    intent.putExtra("reload", true);
                }

                intent.putExtra(ObservationViewerActivity.SHOW_COMMENTS, true);
                intent.putExtra(ObservationViewerActivity.SCROLL_TO_COMMENTS_BOTTOM, true);

                mContext.startActivity(intent);

                try {
                    JSONObject eventParams = new JSONObject();
                    eventParams.put(AnalyticsClient.EVENT_PARAM_VIA, AnalyticsClient.EVENT_VALUE_UPDATES);

                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NAVIGATE_OBS_DETAILS, eventParams);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                try {
                    JSONObject item = mResultList.get(position);
                    item.put("viewed", true);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                view.setBackgroundResource(R.drawable.activity_item_background);

                // Mark observation update as viewed
                Intent serviceIntent = new Intent(INaturalistService.ACTION_VIEWED_UPDATE, null, mContext, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, obs.id);
                ContextCompat.startForegroundService(mContext, serviceIntent);
            }
        };

        view.setOnClickListener(showObs);
    }


    public class ClickSpan extends ClickableSpan {

        private OnClickListener mListener;

        public ClickSpan(OnClickListener listener) {
            mListener = listener;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
        }

        @Override
        public void onClick(View widget) {
            if (mListener != null) mListener.onClick();
        }

    }

    public interface OnClickListener {
        void onClick();
    }

    public void clickify(TextView view, final String clickableText, final OnClickListener listener) {
        CharSequence text = view.getText();
        String string = text.toString();
        ClickSpan span = new ClickSpan(listener);

        int start = string.indexOf(clickableText);
        int end = start + clickableText.length();
        if (start == -1) return;

        if (text instanceof Spannable) {
            ((Spannable)text).setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            SpannableString s = SpannableString.valueOf(text);
            s.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setText(s);
        }

        MovementMethod m = view.getMovementMethod();
        if ((m == null) || !(m instanceof LinkMovementMethod)) {
            view.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public static class CircleTransform implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            int size = Math.min(source.getWidth(), source.getHeight());

            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;

            Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
            if (squaredBitmap != source) {
                source.recycle();
            }

            Bitmap.Config config = source.getConfig();
            if (config == null) config = squaredBitmap.getConfig();
            Bitmap bitmap = Bitmap.createBitmap(size, size, config != null ? config : Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            BitmapShader shader = new BitmapShader(squaredBitmap,
                    BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setAntiAlias(true);

            float r = size / 2f;
            canvas.drawCircle(r, r, r, paint);

            squaredBitmap.recycle();
            return bitmap;
        }

        @Override
        public String key() {
            return "circle";
        }
    }

    public void registerReceivers() {
        mObservationReceiver = new ObservationReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION_RESULT);
        BaseFragmentActivity.safeRegisterReceiver(mObservationReceiver, filter, mContext);
    }

    public void unregisterReceivers() {
        BaseFragmentActivity.safeUnregisterReceiver(mObservationReceiver, mContext);
        mObsIdBeingDownloaded = new HashMap<>();
    }

    private class ObservationReceiver extends BroadcastReceiver {
		@Override
	    public void onReceive(Context context, Intent intent) {
            boolean isSharedOnApp = intent.getBooleanExtra(INaturalistService.IS_SHARED_ON_APP, false);
	        Observation observation;

            if (isSharedOnApp) {
                observation = (Observation) mApp.getServiceResult(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION_RESULT);
            } else {
                observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);
            }

            if (observation == null) {
                return;
            }

            Log.d(TAG, observation.id + ": Download complete");

            List<Pair<View, Integer>> views = mObsIdToView.get(observation.id);
            if (views == null) return;

            // Update all views (activity update rows) that have that obs
            for (Pair<View, Integer> pair : views) {
                View view = pair.first;
                int position = pair.second;

                BetterJSONObject update = (BetterJSONObject)view.getTag();
                Integer id = update.getInt("resource_id");
                if ((update == null) || (id == null) || (!id.equals(observation.id))) {
                    // Current obs ID doesn't match the one returned (could happen if user scrolled and the view got reused by the time we got results)
                    continue;
                }

                // Load obs image again
                Log.e(TAG, observation.id + ": Updating view " + position + ":" + view);
                loadObsImage(observation.id, view, update, position);
            }
	    }

	}
}

