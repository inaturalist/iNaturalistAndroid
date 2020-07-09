package org.inaturalist.android;

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

import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
    private static final String TAG = "UserAdapter";
    private List<JSONObject> mUsers;
    private UserClickListener mClickListener;

    public interface UserClickListener {
        void onClick(JSONObject user, int position);
    }

    public UserAdapter(List<JSONObject> users, UserClickListener clickListener) {
        mUsers = users;
        mClickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.user_result_item, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BetterJSONObject user = new BetterJSONObject(mUsers.get(position));

        String userPicUrl = user.getString("icon");

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

        holder.username.setText(user.getString("login"));

        holder.rootView.setOnClickListener(view -> {
            mClickListener.onClick(user.getJSONObject(), position);
        });
    }

    @Override
    public int getItemCount() {
        return mUsers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView username;
        public ImageView userPic;
        public View rootView;

        public ViewHolder(@NonNull View view) {
            super(view);

            username = view.findViewById(R.id.username);
            userPic = view.findViewById(R.id.user_pic);
            rootView = view;
        }
    }
}
