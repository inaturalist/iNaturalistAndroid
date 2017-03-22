package org.inaturalist.android;


import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
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
import java.util.Locale;
import java.util.Map;

class UserActivitiesAdapter extends ArrayAdapter<String> {
    private final IOnUpdateViewed mOnUpdateViewed;
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private ObservationReceiver mObservationReceiver;
    private Map<Integer, View> mObsIdToView;

    public interface IOnUpdateViewed {
        void onUpdateViewed(Observation obs, int position);
    }

    public UserActivitiesAdapter(Context context, ArrayList<JSONObject> results, IOnUpdateViewed onUpdateViewed) {
        super(context, android.R.layout.simple_list_item_1);

        mOnUpdateViewed = onUpdateViewed;
        mContext = context;
        mResultList = results;
        Collections.sort(mResultList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject news1, JSONObject news2) {
                BetterJSONObject news1json = new BetterJSONObject(news1);
                BetterJSONObject news2json = new BetterJSONObject(news2);

                return news2json.getTimestamp("created_at").compareTo(news1json.getTimestamp("created_at"));
            }
        });

        mObservationReceiver = new ObservationReceiver();
        IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION_RESULT);
        mContext.registerReceiver(mObservationReceiver, filter);

        mObsIdToView = new HashMap<>();
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = convertView == null ? inflater.inflate(R.layout.user_activity_item, parent, false) : convertView;
        BetterJSONObject item = new BetterJSONObject(mResultList.get(position));

        try {
            final ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            ImageView obsPic = (ImageView) view.findViewById(R.id.obs_pic);
            final TextView activityDescription = (TextView) view.findViewById(R.id.activity_description);

            obsPic.setVisibility(View.VISIBLE);

            final String dateFormatted = CommentsIdsAdapter.formatIdDate(item.getTimestamp("created_at"));
            String userName = null;
            String userIconUrl = null;
            JSONObject user = null;
            final Integer obsId = item.getInt("resource_id");

            // Viewed or not?
            view.setBackgroundColor(item.getBoolean("viewed") ? Color.parseColor("#F5F5F5") : Color.parseColor("#E1E9D0") );

            if (item.getString("notifier_type").equals("Identification")) {
                // Identification

                final JSONObject identification = item.getJSONObject("identification");
                final JSONObject taxon = identification.getJSONObject("taxon");
                String id = getTaxonName(identification.getJSONObject("taxon"));
                user = identification.getJSONObject("user");
                userName = user.getString("login");
                userIconUrl = user.optString("icon_url", null);

                final String description = String.format(mContext.getString(R.string.user_activity_id), userName, id, dateFormatted);
                activityDescription.setText(Html.fromHtml(description));

                clickify(activityDescription, id, new OnClickListener() {
                    @Override
                    public void onClick() {
                        // Show taxon details screen
                        Intent intent = new Intent(mContext, GuideTaxonActivity.class);
                        intent.putExtra("taxon", new BetterJSONObject(taxon));
                        intent.putExtra("guide_taxon", false);
                        intent.putExtra("show_add", false);
                        intent.putExtra("download_taxon", true);
                        mContext.startActivity(intent);
                    }
                });
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
                clickify(activityDescription, userName, new OnClickListener() {
                    @Override
                    public void onClick() {
                        onUserClick.onClick(userPic);
                    }
                });
            }

            view.setTag(item);

            // Load obs image
            loadObsImage(obsId, view, position);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return view;
    }

    private void downloadAndDisplayObs(int obsId, View view, ImageView obsPic, TextView activityDescription) {
        // Couldn't find observation (must be an old one) - download it

        View.OnClickListener doNothing = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Do nothing
            }
        };
        obsPic.setOnClickListener(doNothing);
        activityDescription.setOnClickListener(doNothing);
        view.setOnClickListener(doNothing);

        obsPic.setImageResource(R.drawable.iconic_taxon_unknown);

        Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_AND_SAVE_OBSERVATION, null, mContext, INaturalistService.class);
        serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, obsId);
        mContext.startService(serviceIntent);

        // So when we get the result - we'll know which obs pic to set
        mObsIdToView.put(obsId, view);
    }

    private void loadObsImage(int obsId, View view, final int position) {
        ImageView obsPic = (ImageView) view.findViewById(R.id.obs_pic);
        TextView activityDescription = (TextView) view.findViewById(R.id.activity_description);

        Cursor c = mContext.getContentResolver().query(Observation.CONTENT_URI, Observation.PROJECTION, "id = ?", new String[] { String.valueOf(obsId) }, null);
        if (c.getCount() == 0) {
            // Observation isn't saved locally - download it
            c.close();
            downloadAndDisplayObs(obsId, view, obsPic, activityDescription);
            return;
        }

        final Observation obs = new Observation(c);
        c.close();

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
            public void onClick(View view) {
                mOnUpdateViewed.onUpdateViewed(obs, position);

                // Show observation details screen
                Uri uri = obs.getUri();
                Intent intent = new Intent(Intent.ACTION_VIEW, uri, mContext, ObservationViewerActivity.class);
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

                view.setBackgroundColor(Color.parseColor("#F5F5F5"));

                // Mark observation update as viewed
                Intent serviceIntent = new Intent(INaturalistService.ACTION_VIEWED_UPDATE, null, mContext, INaturalistService.class);
                serviceIntent.putExtra(INaturalistService.OBSERVATION_ID, obs.id);
                mContext.startService(serviceIntent);
            }
        };

        obsPic.setOnClickListener(showObs);
        view.setOnClickListener(showObs);

        activityDescription.setOnClickListener(showObs);
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

    private String getTaxonName(JSONObject item) {
        JSONObject defaultName;
        String displayName = null;

        // Get the taxon display name according to device locale
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Locale deviceLocale = mContext.getResources().getConfiguration().locale;
        String deviceLexicon =   deviceLocale.getLanguage();

        try {
            JSONArray taxonNames = item.getJSONArray("taxon_names");
            for (int i = 0; i < taxonNames.length(); i++) {
                JSONObject taxonName = taxonNames.getJSONObject(i);
                String lexicon = taxonName.getString("lexicon");
                if (lexicon.equals(deviceLexicon)) {
                    // Found the appropriate lexicon for the taxon
                    displayName = taxonName.getString("name");
                    break;
                }
            }
        } catch (JSONException e3) {
            //e3.printStackTrace();
        }

        if (displayName == null) {
            // Couldn't extract the display name from the taxon names list - use the default one
            try {
                displayName = item.getString("unique_name");
            } catch (JSONException e2) {
                displayName = null;
            }
            try {
                defaultName = item.getJSONObject("default_name");
                displayName = defaultName.getString("name");
            } catch (JSONException e1) {
                // alas
                JSONObject commonName = item.optJSONObject("common_name");
                if (commonName != null) {
                    displayName = commonName.optString("name");
                } else {
                    displayName = item.optString("preferred_common_name");
                    if ((displayName == null) || (displayName.length() == 0)) {
                        displayName = item.optString("english_common_name");
                        if ((displayName == null) || (displayName.length() == 0)) {
                            displayName = item.optString("name");
                        }
                    }
                }
            }
        }

        return displayName;

    }

    public class CircleTransform implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            int size = Math.min(source.getWidth(), source.getHeight());

            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;

            Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
            if (squaredBitmap != source) {
                source.recycle();
            }

            Bitmap bitmap = Bitmap.createBitmap(size, size, source.getConfig());

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


    private class ObservationReceiver extends BroadcastReceiver {
		@Override
	    public void onReceive(Context context, Intent intent) {
	        Observation observation = (Observation) intent.getSerializableExtra(INaturalistService.OBSERVATION_RESULT);

            if (observation == null) {
                return;
            }

            View view = mObsIdToView.get(observation.id);
            if (view == null) return;

            BetterJSONObject update = (BetterJSONObject)view.getTag();
            Integer id = update.getInt("resource_id");
            if ((update == null) || (id == null) || (!id.equals(observation.id))) {
                // Current obs ID doesn't match the one returned (could happen if user scrolled and the view got reused by the time we got results)
                return;
            }

            int foundId = 0;
            for (int i = 0; i < mResultList.size(); i++) {
                try {
                    if (mResultList.get(i).getInt("resource_id") == id) {
                        foundId = i;
                        break;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            // Load obs image again
            loadObsImage(observation.id, view, foundId);
	    }

	}
}

