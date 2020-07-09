package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MessageThreadAdapter extends RecyclerView.Adapter<MessageThreadAdapter.ViewHolder> {
    private static final String TAG = "MessageThreadAdapter";
    private List<JSONObject> mMessages;
    private Context mContext;
    private INaturalistApp mApp;
    private String mLoggedInUser;

    public MessageThreadAdapter(Context context, List<JSONObject> messages) {
        mContext = context;
        mMessages = messages;

        mApp = (INaturalistApp) mContext.getApplicationContext();
        mLoggedInUser = mApp.currentUserLogin();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_thread_item, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BetterJSONObject message = new BetterJSONObject(mMessages.get(position));

        JSONObject user = message.getJSONObject("from_user");
        String userPicUrl = user.optString("icon_url");
        boolean isLoggedInUser = user.optString("login").equals(mLoggedInUser);

        // Show layout and user photo on the right if message from logged-in user, left otherwise
        ImageView userPic;

        if (isLoggedInUser) {
            userPic = holder.userPicRight;
            userPic.setVisibility(View.VISIBLE);
            holder.userPicLeft.setVisibility(View.GONE);
            holder.header.setGravity(Gravity.RIGHT);
        } else {
            userPic = holder.userPicLeft;
            userPic.setVisibility(View.VISIBLE);
            holder.userPicRight.setVisibility(View.GONE);
            holder.header.setGravity(Gravity.LEFT);
        }

        if (userPicUrl == null) {
            userPic.setImageResource(R.drawable.ic_account_circle_black_24dp);
        } else {
            UrlImageViewHelper.setUrlDrawable(userPic, userPicUrl, R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
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

        Timestamp ts = message.getTimestamp("updated_at");
        Calendar cal = Calendar.getInstance(Locale.ENGLISH);
        cal.setTimeInMillis(ts.getTime());
        holder.date.setText(DateFormat.format("MMMM d, yyyy hh:mma", cal));

        HtmlUtils.fromHtml(holder.message, message.getString("body"));

        View.OnClickListener onUserClick = view -> {
            // Show user profile
            try {
                JSONObject item = new JSONObject();
                item.put("login", user.optString("login"));
                Intent intent = new Intent(mContext, UserProfile.class);
                intent.putExtra("user", new BetterJSONObject(item));
                mContext.startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        };

        holder.username.setOnClickListener(onUserClick);
        userPic.setOnClickListener(onUserClick);
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView username;
        public TextView date;
        public ImageView userPicLeft;
        public ImageView userPicRight;
        public TextView message;
        public LinearLayout header;

        public ViewHolder(@NonNull View view) {
            super(view);

            username = view.findViewById(R.id.user_name);
            date = view.findViewById(R.id.date);
            userPicLeft = view.findViewById(R.id.user_pic_left);
            userPicRight = view.findViewById(R.id.user_pic_right);
            message = view.findViewById(R.id.message);
            header = view.findViewById(R.id.header);
        }
    }
}
