package org.inaturalist.android;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;

class ProjectUserAdapter extends ArrayAdapter<String> {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;

    public ProjectUserAdapter(Context context, ArrayList<JSONObject> results) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.project_user_item, parent, false);
        JSONObject item = null;
        int count;
        try {
            JSONObject jsonObject = mResultList.get(position);
            if (jsonObject.has("observation_count")) {
                count = jsonObject.getInt("observation_count");
            } else {
                count = jsonObject.getInt("count");
            }
            item = jsonObject.getJSONObject("user");
        } catch (JSONException e) {
            e.printStackTrace();
            return view;
        }

        // Get the taxon display name according to device locale
        try {
            ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            TextView username = (TextView) view.findViewById(R.id.username);
            TextView rank = (TextView) view.findViewById(R.id.rank);
            TextView countText = (TextView) view.findViewById(R.id.count);

            DecimalFormat formatter = new DecimalFormat("#,###,###");
            rank.setText(formatter.format(position + 1));
            username.setText(item.getString("login"));
            countText.setText(formatter.format(count));

            if (item.has("icon_url") && !item.isNull("icon_url")) {
                UrlImageViewHelper.setUrlDrawable(userPic, item.getString("icon_url"), R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) { }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        // Return a circular version of the profile picture
                        Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                        return ImageUtils.getCircleBitmap(centerCrop);
                    }
                });
            } else {
                userPic.setImageResource(R.drawable.ic_account_circle_black_24dp);
            }

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

}

