package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class CommentsIdsAdapter extends ArrayAdapter<BetterJSONObject> implements OnClickListener {

    private final Handler mMainHandler;
    private List<BetterJSONObject> mItems;
	private Context mContext;
	private ArrayList<Boolean> mAgreeing;
	private String mLogin;
	private int mTaxonId;
	private OnIDAdded mOnIDAddedCb;

	public static interface OnIDAdded {
		public void onIdentificationAdded(BetterJSONObject taxon);
		public void onIdentificationRemoved(BetterJSONObject taxon);
	};



	public boolean isEnabled(int position) { 
		return false; 
	}  

	public CommentsIdsAdapter(Context context, List<BetterJSONObject> objects, int taxonId, OnIDAdded onIDAddedCb) {
		super(context, R.layout.comment_id_item, objects);

		mItems = objects;
		mAgreeing = new ArrayList<Boolean>();
		while (mAgreeing.size() < mItems.size()) mAgreeing.add(false);
		mContext = context;
		mTaxonId = taxonId;
		mOnIDAddedCb = onIDAddedCb;

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
		final View view = inflater.inflate(R.layout.comment_id_item, parent, false); 
		final BetterJSONObject item = mItems.get(position);

		try {
			TextView comment = (TextView) view.findViewById(R.id.comment);
			RelativeLayout idLayout = (RelativeLayout) view.findViewById(R.id.id_layout);

			TextView postedOn = (TextView) view.findViewById(R.id.posted_on);
			final String username = item.getJSONObject("user").getString("login");
			Timestamp postDate = item.getTimestamp("updated_at");
			SimpleDateFormat format = new SimpleDateFormat("LLL d, yyyy");
			postedOn.setText(String.format(res.getString(R.string.posted_by),
                    (mLogin != null) && username.equalsIgnoreCase(mLogin) ? res.getString(R.string.you) : username,
                    format.format(postDate)));

			final ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
			UrlImageViewHelper.setUrlDrawable(userPic, item.getJSONObject("user").getString("user_icon_url"), R.drawable.usericon, new UrlImageViewCallback() {
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

			if (item.getString("type").equals("comment")) {
				// Comment
				comment.setVisibility(View.VISIBLE);
				idLayout.setVisibility(View.GONE);

				comment.setText(Html.fromHtml(item.getString("body")));
				comment.setMovementMethod(LinkMovementMethod.getInstance()); 

				postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
				userPic.setAlpha(255);

			} else {
				// Identification
				idLayout.setVisibility(View.VISIBLE);
				String body = item.getString("body");
				if (body != null && body.length() > 0) {
					comment.setText(Html.fromHtml(body));
					ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)comment.getLayoutParams();
					layoutParams.setMargins(
							layoutParams.leftMargin, 
							layoutParams.topMargin + 25, 
							layoutParams.rightMargin, 
							layoutParams.bottomMargin);
					comment.setLayoutParams(layoutParams);
				} else {
					comment.setVisibility(View.GONE);
				}
				ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
				UrlImageViewHelper.setUrlDrawable(idPic, item.getJSONObject("taxon").getString("image_url"), R.drawable.iconic_taxon_unknown);
				TextView idName = (TextView) view.findViewById(R.id.id_name);
				if (!item.getJSONObject("taxon").isNull("common_name")) {
					idName.setText(item.getJSONObject("taxon").getJSONObject("common_name").getString("name"));
				} else {
					idName.setText(item.getJSONObject("taxon").getString("name"));
				}
				TextView idTaxonName = (TextView) view.findViewById(R.id.id_taxon_name);
				idTaxonName.setText(item.getJSONObject("taxon").getString("name"));
				
				String rank = item.getJSONObject("taxon").optString("rank", null);
				if (rank != null) {
					if ((rank.equalsIgnoreCase("genus")) || (rank.equalsIgnoreCase("species"))) {
						idTaxonName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
					}
				}

				Boolean isCurrent = item.getBoolean("current");
				if ((isCurrent == null) || (!isCurrent)) {
					// An outdated identification - show as faded-out
					idName.setTextColor(idName.getTextColors().withAlpha(100));
					idTaxonName.setTextColor(idTaxonName.getTextColors().withAlpha(100));
					postedOn.setTextColor(postedOn.getTextColors().withAlpha(100));
					idPic.setAlpha(100);
					userPic.setAlpha(100);
				} else {
					idName.setTextColor(idName.getTextColors().withAlpha(255));
					idTaxonName.setTextColor(idTaxonName.getTextColors().withAlpha(255));
					postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
					idPic.setAlpha(255);
					userPic.setAlpha(255);
				}

				final Button agree = (Button) view.findViewById(R.id.id_agree);
				final ProgressBar loading = (ProgressBar) view.findViewById(R.id.loading);
				agree.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if ((mLogin != null) && (username.equalsIgnoreCase(mLogin))) {
							mOnIDAddedCb.onIdentificationRemoved(item);
						} else {
							mOnIDAddedCb.onIdentificationAdded(item);
							mTaxonId = item.getInt("taxon_id");
						}

						agree.setVisibility(View.GONE);
						loading.setVisibility(View.VISIBLE);
						mAgreeing.set(position, true);
					}
				});
				
				loading.setVisibility(View.GONE);
				
				// See if there's ID of the same taxon before this one
				int currentTaxonId = item.getInt("taxon_id");
				boolean foundPreviousSameTaxon = false;
				for (int i = 0; i < position; i++) {
					BetterJSONObject taxon = mItems.get(i);
					Integer taxonId = taxon.getInt("taxon_id");
					if ((taxonId != null) && (taxonId == currentTaxonId)) {
						foundPreviousSameTaxon = true;
						break;
					}
				}

				boolean didNotIdThisBefore = true;
				for (int i = 0; i < mItems.size(); i++) {
					if (mLogin == null) break;
					if (i == position) continue;

					BetterJSONObject taxon = mItems.get(i);

					if ((taxon.getJSONObject("user").getString("login").equalsIgnoreCase(mLogin))) {
						Integer taxonId = taxon.getInt("taxon_id");
						if ((taxonId != null) && (taxonId == currentTaxonId)) {
							// Agreed on the current taxon type before
							didNotIdThisBefore = false;
							break;
						}
						
					}
				}
				
				if (!foundPreviousSameTaxon && didNotIdThisBefore) {
					// First taxon id of its kind AND the current user didn't ID this taxon before -
					// show agree button
					agree.setVisibility(View.VISIBLE);
				} else {
					// Second (or more) taxon id of its kind - don't show agree button
					agree.setVisibility(View.GONE);
				}

				if ((mLogin != null) && (username.equalsIgnoreCase(mLogin))) {
					agree.setText(R.string.remove);
					agree.setVisibility(View.VISIBLE);

					if ((isCurrent == null) || (!isCurrent)) {
						// Faded IDs should not have a "Remove" button
						agree.setVisibility(View.GONE);
					}
				} else {
					agree.setText(R.string.agree);
				}

				if ((mAgreeing.get(position) != null) && (mAgreeing.get(position) == true)) {
					agree.setVisibility(View.GONE);
					loading.setVisibility(View.VISIBLE);
				}

				if (mLogin == null) {
					// Can't agree if not logged in
					agree.setVisibility(View.GONE);
				}

			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		view.setTag(item);
		view.setOnClickListener(this);

		return view;
	}

	@Override
	public void onClick(View view) {
		BetterJSONObject item = (BetterJSONObject) view.getTag();
		
		if (!item.getString("type").equals("identification")) {
			return;
		}

		Intent intent = new Intent(mContext, GuideTaxonActivity.class);
		intent.putExtra("taxon", new BetterJSONObject(item.getJSONObject("taxon")));
		intent.putExtra("guide_taxon", false);
		mContext.startActivity(intent);
	}
}

