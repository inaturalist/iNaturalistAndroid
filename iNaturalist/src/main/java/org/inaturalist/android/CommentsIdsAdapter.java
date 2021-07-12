package org.inaturalist.android;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.FastDateFormat;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import com.cocosw.bottomsheet.BottomSheet;
import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Handler;
import android.text.Html;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CommentsIdsAdapter extends ArrayAdapter<BetterJSONObject> implements OnClickListener {
    private static String TAG = "CommentsIdsAdapter";
    private final Handler mMainHandler;
    private final ActivityHelper mHelper;
	private final INaturalistApp mApp;
	private BetterJSONObject mObservation;
    private List<BetterJSONObject> mItems;
	private Context mContext;
	private ArrayList<Boolean> mAgreeing;
	private String mLogin;
	private int mTaxonId;
	private OnIDAdded mOnIDAddedCb;
    private boolean mReadOnly;

    public static interface OnIDAdded {
		public void onIdentificationAdded(BetterJSONObject taxon);
		public void onIdentificationRemoved(BetterJSONObject taxon);
        public void onIdentificationUpdated(BetterJSONObject id);
        public void onIdentificationRestored(BetterJSONObject id);
		public void onCommentRemoved(BetterJSONObject comment);
		public void onCommentUpdated(BetterJSONObject comment);
	};



	public boolean isEnabled(int position) { 
		return false; 
	}

	public CommentsIdsAdapter(Context context, BetterJSONObject observation, List<BetterJSONObject> objects, int taxonId, OnIDAdded onIDAddedCb, boolean readOnly) {
		super(context, R.layout.comment_id_item, objects);

        mObservation = observation;
        mReadOnly = readOnly;
		mItems = objects;
		mAgreeing = new ArrayList<Boolean>();
		while (mAgreeing.size() < mItems.size()) mAgreeing.add(false);
		mContext = context;
		mTaxonId = taxonId;
		mOnIDAddedCb = onIDAddedCb;
        mHelper = new ActivityHelper(mContext);
        mApp = (INaturalistApp) mContext.getApplicationContext();

		SharedPreferences prefs = mContext.getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
		mLogin = prefs.getString("username", null);

        mMainHandler = new Handler(context.getMainLooper());
	}

	public void addItemAtBeginning(BetterJSONObject newItem) {
		mItems.add(0, newItem);
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) { 
		final Resources res = mContext.getResources();
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(R.layout.comment_id_item_obs_viewer, parent, false);
		final BetterJSONObject item = mItems.get(position);

		((INaturalistApp) mContext.getApplicationContext()).setStringResourceForView(view, R.id.id_agree_text, "agree_all_caps", "agree2");

		try {
			final ViewGroup userDetails = (ViewGroup) view.findViewById(R.id.user_details);
			final ViewGroup contentHidden = (ViewGroup) view.findViewById(R.id.content_hidden);
			final TextView comment = (TextView) view.findViewById(R.id.comment);
			final TextView showHiddenContent = (TextView) view.findViewById(R.id.show_hidden_content);
			RelativeLayout idLayout = (RelativeLayout) view.findViewById(R.id.id_layout);
            final RelativeLayout idAgreeLayout = (RelativeLayout) view.findViewById(R.id.id_agree_container);

			TextView postedOn = (TextView) view.findViewById(R.id.posted_on);
			final String username = item.getJSONObject("user").getString("login");
            Timestamp postDate = item.getTimestamp("updated_at");
            if (postDate == null) postDate = item.getTimestamp("created_at");

			postedOn.setText(String.format(res.getString(item.getString("type").equals("comment") ? R.string.comment_title : R.string.id_title),
					username, formatIdDate(mContext, postDate)));

			OnClickListener showUser = new OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(mContext, UserProfile.class);
					intent.putExtra("user", new BetterJSONObject(item.getJSONObject("user")));
					mContext.startActivity(intent);
				}
			};


            final ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            JSONObject user = item.getJSONObject("user");
            String userIconUrl = user.optString("user_icon_url", user.optString("icon"));
            boolean hasUserIcon = userIconUrl != null;

            userPic.setOnClickListener(showUser);
            postedOn.setOnClickListener(showUser);

            if (hasUserIcon) {
                Picasso.with(mContext)
                        .load(userIconUrl)
                        .fit()
                        .centerCrop()
                        .placeholder(R.drawable.ic_account_circle_black_24dp)
                        .transform(new UserActivitiesAdapter.CircleTransform())
                        .into(userPic, new Callback() {
                            @Override
                            public void onSuccess() {
                                // Nothing to do here
                            }

                            @Override
                            public void onError() {

                            }
                        });

            } else {
				userPic.setAlpha(100);
            }

			final ImageView moreMenu = (ImageView) view.findViewById(R.id.more_menu);
			final boolean isComment = item.getString("type").equals("comment");
			final View loading = view.findViewById(R.id.loading);

			final DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int which) {
					switch (which) {
                        case R.id.restore:
                            loading.setVisibility(View.VISIBLE);
                            mOnIDAddedCb.onIdentificationRestored(item);

                            break;

						case R.id.delete:
							// Display deletion confirmation dialog
							mHelper.confirm(mContext.getString(isComment ? R.string.delete_comment : R.string.delete_id),
									isComment ? R.string.delete_comment_message : R.string.delete_id_message,
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialogInterface, int i) {
											loading.setVisibility(View.VISIBLE);

											if (isComment) {
												mOnIDAddedCb.onCommentRemoved(item);
											} else {
												mOnIDAddedCb.onIdentificationRemoved(item);
											}
										}
									}, new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialogInterface, int i) {
										}
									}, R.string.yes, R.string.no);

							break;
						case R.id.edit:
							if (isComment) {
								mOnIDAddedCb.onCommentUpdated(item);
							} else {
                                mOnIDAddedCb.onIdentificationUpdated(item);
							}
					}
				}
			};

			if (moreMenu != null) {
				moreMenu.setVisibility(View.GONE);
				moreMenu.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View view) {
						if (loading.getVisibility() == View.VISIBLE) {
							return;
						}

						int menuResource = isComment && username.equalsIgnoreCase(mLogin) ? R.menu.comment_menu : R.menu.id_menu;

                        boolean restoreId = false;
                        if (menuResource == R.menu.id_menu) {
                            Boolean isCurrent = item.getBoolean("current");
                            if (((isCurrent == null) || (!isCurrent)) && (username.equalsIgnoreCase(mLogin)))  {
                                restoreId = true;
                            }
                        }

                        Menu popupMenu;

						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							PopupMenu popup = new PopupMenu(getContext(), moreMenu);
							popup.getMenuInflater().inflate(menuResource, popup.getMenu());
							popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
								@Override
								public boolean onMenuItemClick(android.view.MenuItem menuItem) {
									onClick.onClick(null, menuItem.getItemId());
									return true;
								}
							});

                            popupMenu = popup.getMenu();
                            popup.show();
						} else {
							BottomSheet sheet = new BottomSheet.Builder((Activity) mContext).sheet(menuResource).listener(onClick).show();
                            popupMenu = sheet.getMenu();
						}

						if (!isComment) {
							if (restoreId) {
								// Show restore ID menu option
								popupMenu.getItem(0).setVisible(false);
								popupMenu.getItem(1).setVisible(true);
							} else {
								// Show withdraw ID menu option
								popupMenu.getItem(0).setVisible(true);
								popupMenu.getItem(1).setVisible(false);
							}
						}

					}
				});
			}

			showHiddenContent.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					comment.setTypeface(null, Typeface.NORMAL);
					HtmlUtils.fromHtml(comment, item.getString("body"));
					contentHidden.setVisibility(View.VISIBLE);
					showHiddenContent.setVisibility(View.GONE);
					userDetails.setVisibility(View.VISIBLE);
				}
			});

			contentHidden.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					JSONArray modActions = item.getJSONArray("moderator_actions").getJSONArray();
					BetterJSONObject lastModAction = new BetterJSONObject(modActions.optJSONObject(modActions.length() - 1));
					Calendar cal = Calendar.getInstance();
					cal.setTimeInMillis(lastModAction.getTimestamp("created_at").getTime());
					String date = DateFormat.format("MMM dd, yyyy", cal).toString();

					String message = String.format(mContext.getString(R.string.content_hidden_because),
							lastModAction.getJSONObject("user").optString("login"),
							date,
							lastModAction.getString("reason"));

					if ((mLogin != null) && (username.equalsIgnoreCase(mLogin))) {
					    // This is our own ID/comment - allow the user to contact support
                        message += "\n" + mContext.getString(R.string.contact_support_with_link);
                    }

				    mHelper.confirm(R.string.content_hidden, message, null, null);
				}
			});

			userDetails.setVisibility(View.VISIBLE);
			contentHidden.setVisibility(View.GONE);

			if (item.getString("type").equals("comment")) {
				// Comment
				comment.setVisibility(View.VISIBLE);
				idLayout.setVisibility(View.GONE);
                loading.setVisibility(View.GONE);
				if (moreMenu != null) moreMenu.setVisibility(View.VISIBLE);
                idAgreeLayout.setVisibility(View.GONE);

				if (item.has("hidden") && item.getBoolean("hidden")) {
					// Hidden comment
					comment.setText(Html.fromHtml(mContext.getString(R.string.content_hidden)));
					comment.setTypeface(null, Typeface.ITALIC);
					userDetails.setVisibility(View.INVISIBLE);
					showHiddenContent.setVisibility(allowToShowHiddenContent(item) ? View.VISIBLE : View.GONE);
                } else {
					comment.setTypeface(null, Typeface.NORMAL);
					HtmlUtils.fromHtml(comment, item.getString("body"));
					showHiddenContent.setVisibility(View.GONE);
				}

				postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
				if (hasUserIcon) userPic.setAlpha(255);

			} else {
				// Identification
				idLayout.setVisibility(View.VISIBLE);
				String body = item.getString("body");
				if (item.has("hidden") && item.getBoolean("hidden")) {
					// Hidden ID
					comment.setText(Html.fromHtml(mContext.getString(R.string.content_hidden)));
					comment.setVisibility(View.VISIBLE);
					comment.setTypeface(null, Typeface.ITALIC);
					contentHidden.setVisibility(View.VISIBLE);
					showHiddenContent.setVisibility(allowToShowHiddenContent(item) ? View.VISIBLE : View.GONE);

				} else if (body != null && body.length() > 0) {
					comment.setTypeface(null, Typeface.NORMAL);
					HtmlUtils.fromHtml(comment, body);

                    comment.setVisibility(View.VISIBLE);
					showHiddenContent.setVisibility(View.GONE);
				} else {
					comment.setVisibility(View.GONE);
					showHiddenContent.setVisibility(View.GONE);
				}
				ImageView idPic = (ImageView) view.findViewById(R.id.id_pic);
				JSONObject taxonObject = item.getJSONObject("taxon");
				JSONObject defaultPhoto = taxonObject != null ? taxonObject.optJSONObject("default_photo") : null;
				if (defaultPhoto != null) {
					UrlImageViewHelper.setUrlDrawable(idPic, taxonObject.optString("image_url", defaultPhoto.optString("square_url")), R.drawable.iconic_taxon_unknown);
				} else {
				    idPic.setImageResource(R.drawable.iconic_taxon_unknown);
                }

				TextView idName = (TextView) view.findViewById(R.id.id_name);
				TextView idScientificName = (TextView) view.findViewById(R.id.id_taxon_name);

				if (mApp.getShowScientificNameFirst()) {
					// Show scientific name first, before common name
					TaxonUtils.setTaxonScientificName(mApp, idName, item.getJSONObject("taxon"));
					idScientificName.setText(TaxonUtils.getTaxonName(mContext, item.getJSONObject("taxon")));
				} else {
					TaxonUtils.setTaxonScientificName(mApp, idScientificName, item.getJSONObject("taxon"));
					idName.setText(TaxonUtils.getTaxonName(mContext, item.getJSONObject("taxon")));
				}

				Boolean isCurrent = item.getBoolean("current");
				if ((isCurrent == null) || (!isCurrent)) {
					// An outdated identification - show as faded-out
					idName.setTextColor(idName.getTextColors().withAlpha(100));
					idScientificName.setTextColor(idScientificName.getTextColors().withAlpha(100));
					idPic.setAlpha(100);
					userPic.setAlpha(100);
				} else {
					idName.setTextColor(idName.getTextColors().withAlpha(255));
					idScientificName.setTextColor(idScientificName.getTextColors().withAlpha(255));
					idPic.setAlpha(255);
					if (hasUserIcon) userPic.setAlpha(255);
				}

				final View agree = view.findViewById(R.id.id_agree);
				agree.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if ((mLogin != null) && (username.equalsIgnoreCase(mLogin))) {
							mOnIDAddedCb.onIdentificationRemoved(item);
						} else {
							mOnIDAddedCb.onIdentificationAdded(item);
							mTaxonId = item.getInt("taxon_id");
						}

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
						if ((taxonId != null) && (taxonId == currentTaxonId) && (taxon.getBoolean("current"))) {
							// Agreed on the current taxon type before
							didNotIdThisBefore = false;
							break;
						}
						
					}
				}
				
				if (!foundPreviousSameTaxon && didNotIdThisBefore) {
					// First taxon id of its kind AND the current user didn't ID this taxon before -
					// show agree button
					idAgreeLayout.setVisibility(View.VISIBLE);
				} else {
					// Second (or more) taxon id of its kind - don't show agree button
					idAgreeLayout.setVisibility(View.GONE);
				}

                if (moreMenu != null) moreMenu.setVisibility(View.GONE);

				if ((mLogin != null) && (username.equalsIgnoreCase(mLogin))) {
					idAgreeLayout.setVisibility(View.GONE);
					if (moreMenu != null) moreMenu.setVisibility(View.VISIBLE);

					if ((isCurrent == null) || (!isCurrent)) {
						// Faded IDs should not have a "Remove" button
						idAgreeLayout.setVisibility(View.GONE);
					}
				}

				if ((mAgreeing.get(position) != null) && (mAgreeing.get(position) == true)) {
                    loading.setVisibility(View.VISIBLE);
					idAgreeLayout.setVisibility(View.GONE);
				}

				if (mLogin == null) {
					// Can't agree if not logged in
					idAgreeLayout.setVisibility(View.GONE);
				}

				OnClickListener listener = new OnClickListener() {
					@Override
					public void onClick(View view) {
						Intent intent = new Intent(mContext, TaxonActivity.class);
						JSONObject taxon = mItems.get(position).getJSONObject("taxon");
						intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(taxon));
						intent.putExtra(TaxonActivity.OBSERVATION, mObservation);
						intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
						mContext.startActivity(intent);
					}
				};
				idLayout.setOnClickListener(listener);
				idName.setOnClickListener(listener);
				idScientificName.setOnClickListener(listener);


                boolean isActive = item.getJSONObject("taxon").optBoolean("is_active", true);
                ViewGroup taxonInactive = (ViewGroup) view.findViewById(R.id.taxon_inactive);
				taxonInactive.setVisibility(isActive ? View.GONE : View.VISIBLE);

				if (!isActive) {
                    idScientificName.setPaintFlags(idScientificName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    idName.setPaintFlags(idScientificName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }

				taxonInactive.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
					    INaturalistApp app = (INaturalistApp) mContext.getApplicationContext();
						String inatNetwork = app.getInaturalistNetworkMember();
						String inatHost = app.getStringResourceByName("inat_host_" + inatNetwork);

						String taxonUrl = String.format(Locale.ENGLISH, "%s/taxon_changes?taxon_id=%d&locale=%s", inatHost, item.getInt("taxon_id"), mApp.getLanguageCodeForAPI());
						Intent i = new Intent(Intent.ACTION_VIEW);
						i.setData(Uri.parse(taxonUrl));
						mContext.startActivity(i);
					}
				});

			}


            if (moreMenu != null) {
                if ((mLogin == null) || ((mLogin != null) && (!username.equalsIgnoreCase(mLogin)))) {
                    moreMenu.setVisibility(View.GONE);
                }
            }
        } catch (JSONException e) {
			Logger.tag(TAG).error(e);
		}
		
		view.setTag(item);

		return view;
	}

	private boolean allowToShowHiddenContent(BetterJSONObject item) {
		// Curators and admins can see hidden content
		Set<String> roles = mApp.getUserRoles();
		if (roles.contains("admin") || roles.contains("curator")) return true;

		// Owner of the comment/ID can see hidden content
		String username = item.getJSONObject("user").optString("login");
		if (username.equalsIgnoreCase(mLogin)) return true;

		return false;
	}

    public static String formatIdDate(Context context, Timestamp postDate) {
        Duration difference = new Duration(postDate.getTime(), (new Date()).getTime());
        long days = difference.getStandardDays();

        if (days <= 30) {
            // Less than 30 days ago - display as 3m (mins), 3h (hours), 3d (days) or 3w (weeks)
            if (days < 1) {
				long hours = difference.getStandardHours();
                if (hours < 1) {
					long minutes = difference.getStandardMinutes();
                    return String.format(context.getString(R.string.date_minute), minutes);
                } else {
                    return String.format(context.getString(R.string.date_hour), hours);
                }
            } else if (days < 7) {
                return String.format(context.getString(R.string.date_day), days);
            } else {
                return String.format(context.getString(R.string.date_week), days / 7);
            }
        } else {
            Calendar today = Calendar.getInstance();
            today.setTime(new Date());
            Calendar calDate = Calendar.getInstance();
            calDate.setTimeInMillis(postDate.getTime());

            String dateFormatString;
            if (today.get(Calendar.YEAR) != calDate.get(Calendar.YEAR)) {
                // Previous year(s)
                dateFormatString = context.getString(R.string.date_short);
            } else {
                // Current year
                dateFormatString = context.getString(R.string.date_short_this_year);
            }

			FastDateFormat dateFormat = FastDateFormat.getInstance(dateFormatString, context.getResources().getConfiguration().locale);
            return dateFormat.format(new Date(postDate.getTime()));
        }
    }

    @Override
	public void onClick(View view) {
		BetterJSONObject item = (BetterJSONObject) view.getTag();
		if (!item.getString("type").equals("identification")) {
			return;
		}

		Intent intent = new Intent(mContext, TaxonActivity.class);
		intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(item.getJSONObject("taxon")));
		intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
        intent.putExtra(TaxonActivity.OBSERVATION, mObservation);
        mContext.startActivity(intent);
	}

    private void copyToClipBoard(String text) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(text, text);
            clipboard.setPrimaryClip(clip);
        } else {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
        }
    }
}

