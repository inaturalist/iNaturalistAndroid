package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.cocosw.bottomsheet.BottomSheet;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class FavoritesAdapter extends ArrayAdapter<BetterJSONObject> {

    private final Handler mMainHandler;
    private final ActivityHelper mHelper;
    private List<BetterJSONObject> mItems;
	private Context mContext;
	private String mLogin;

	public boolean isEnabled(int position) {
		return false;
	}

	public FavoritesAdapter(Context context, List<BetterJSONObject> objects) {
		super(context, R.layout.favorite_item, objects);

		mItems = objects;
		mContext = context;
        mHelper = new ActivityHelper(mContext);

		SharedPreferences prefs = mContext.getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
		mLogin = prefs.getString("username", null);

        mMainHandler = new Handler(context.getMainLooper());
	}

	public void addItemAtBeginning(BetterJSONObject newItem) {
		mItems.add(0, newItem);
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		Resources res = mContext.getResources();
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.favorite_item, parent, false);
		final BetterJSONObject item = mItems.get(position);

		try {
			TextView favDate = (TextView) view.findViewById(R.id.faved_on);
			TextView userNameText = (TextView) view.findViewById(R.id.user_name);
			final String username = item.getJSONObject("user").getString("login");
			userNameText.setText(username);
			Timestamp postDate = item.getTimestamp("created_at");

			favDate.setText(CommentsIdsAdapter.formatIdDate(mContext, postDate));

            final ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            boolean hasUserIcon = item.getJSONObject("user").getString("user_icon_url") != null;

            if (hasUserIcon) {
                UrlImageViewHelper.setUrlDrawable(userPic, item.getJSONObject("user").getString("user_icon_url"), R.drawable.ic_account_circle_black_24dp, new UrlImageViewCallback() {
					@Override
					public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
						// Nothing to do here
					}

					@Override
					public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
						// Return a circular version of the profile picture
						return ImageUtils.getCircleBitmap(loadedBitmap);
					}
				});
            } else {
				userPic.setAlpha(100);
            }
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		view.setTag(item);

		return view;
	}
}

