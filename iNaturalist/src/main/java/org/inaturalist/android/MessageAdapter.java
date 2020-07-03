package org.inaturalist.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private static final String TAG = "MessageAdapter";
    private List<JSONObject> mMessages;
    private MessageClickListener mClickListener;
    private Context mContext;
    private INaturalistApp mApp;
    private Set<Integer> mMutedUsers;

    public interface MessageClickListener {
        void onClick(JSONObject message, int position);
    }

    public MessageAdapter(Context context, List<JSONObject> messages, MessageClickListener clickListener) {
        mMessages = messages;
        mClickListener = clickListener;
        mContext = context;
        mApp = (INaturalistApp) mContext.getApplicationContext();

        mMutedUsers = mApp.getMutedUsers();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_item, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BetterJSONObject message = new BetterJSONObject(mMessages.get(position));

        JSONObject user = message.getJSONObject("from_user");
        String userPicUrl = user.optString("icon_url");

        if (userPicUrl == null) {
            holder.userPic.setImageResource(R.drawable.ic_account_circle_black_24dp);
        } else {
            UrlImageViewHelper.setUrlDrawable(holder.userPic, userPicUrl, R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
                @Override
                public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                }

                @Override
                public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    // Return a circular version of the profile picture
                    Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                    return ImageUtils.getCircleBitmap(centerCrop);
                }
            });
        }

        holder.username.setText(user.optString("login"));
        holder.subject.setText(message.getString("subject"));

        Timestamp ts = message.getTimestamp("updated_at");
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(ts.getTime());
        holder.date.setText(DateFormat.format("MMMM d, yyyy hh:mma", cal));

        String readAt = message.getString("read_at");

        if (readAt == null) {
            // Unread message
            holder.unreadIndicator.setVisibility(View.VISIBLE);
            holder.rootView.setBackgroundColor(Color.parseColor("#EAF9DF"));
        } else {
            holder.unreadIndicator.setVisibility(View.GONE);
            holder.rootView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        }

        boolean isMuted = mMutedUsers.contains(user.optInt("id"));
        holder.muteIndicator.setVisibility(isMuted ? View.VISIBLE : View.GONE);

        boolean unresolvedFlag = hasUnresolvedFlags(message.getJSONObject());

        holder.flagIndicator.setVisibility(unresolvedFlag ? View.VISIBLE : View.GONE);

        holder.rootView.setOnClickListener(view -> {
            mClickListener.onClick(message.getJSONObject(), position);
        });
    }

    public static boolean hasUnresolvedFlags(JSONObject message) {
        JSONArray threadFlags = message.optJSONArray("thread_flags");
        if (threadFlags == null) {
            return false;
        }

        for (int i = 0; i < threadFlags.length(); i++) {
            JSONObject flag = threadFlags.optJSONObject(i);
            if (!flag.optBoolean("resolved")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView username;
        public TextView subject;
        public TextView date;
        public ImageView userPic;
        public View unreadIndicator;
        public View muteIndicator;
        public View flagIndicator;
        public View rootView;

        public ViewHolder(@NonNull View view) {
            super(view);

            username = view.findViewById(R.id.user_name);
            subject = view.findViewById(R.id.subject);
            date = view.findViewById(R.id.date);
            userPic = view.findViewById(R.id.user_pic);
            unreadIndicator = view.findViewById(R.id.unread_indicator);
            muteIndicator = view.findViewById(R.id.mute_indicator);
            flagIndicator = view.findViewById(R.id.flag_indicator);
            rootView = view;
        }
    }
}
