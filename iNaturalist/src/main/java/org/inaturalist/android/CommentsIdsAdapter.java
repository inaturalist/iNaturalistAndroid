package org.inaturalist.android;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

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
import android.graphics.Typeface;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Handler;
import android.text.Html;
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
    private final boolean mIsNewLayout;
    private final ActivityHelper mHelper;
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

	public CommentsIdsAdapter(Context context, BetterJSONObject observation, List<BetterJSONObject> objects, int taxonId, OnIDAdded onIDAddedCb, boolean isNewLayout, boolean readOnly) {
		super(context, R.layout.comment_id_item, objects);

        mObservation = observation;
        mReadOnly = readOnly;
		mItems = objects;
		mAgreeing = new ArrayList<Boolean>();
		while (mAgreeing.size() < mItems.size()) mAgreeing.add(false);
		mContext = context;
		mTaxonId = taxonId;
		mOnIDAddedCb = onIDAddedCb;
        mIsNewLayout = isNewLayout;
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
		final Resources res = mContext.getResources();
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View view = inflater.inflate(mIsNewLayout ? R.layout.comment_id_item_obs_viewer : R.layout.comment_id_item, parent, false);
		final BetterJSONObject item = mItems.get(position);

		try {
			final TextView comment = (TextView) view.findViewById(R.id.comment);
			RelativeLayout idLayout = (RelativeLayout) view.findViewById(R.id.id_layout);
            final RelativeLayout idAgreeLayout = (RelativeLayout) view.findViewById(R.id.id_agree_container);

			TextView postedOn = (TextView) view.findViewById(R.id.posted_on);
			final String username = item.getJSONObject("user").getString("login");
            Timestamp postDate = item.getTimestamp("updated_at");

            if (mIsNewLayout) {
                postedOn.setText(String.format(res.getString(item.getString("type").equals("comment") ? R.string.comment_title : R.string.id_title),
                        username, formatIdDate(postDate)));
            } else {
                SimpleDateFormat format = new SimpleDateFormat("LLL d, yyyy");
                postedOn.setText(String.format(res.getString(R.string.posted_by),
                        (mLogin != null) && username.equalsIgnoreCase(mLogin) ? res.getString(R.string.you) : username,
                        format.format(postDate)));
            }

			OnClickListener showUser = new OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(mContext, UserProfile.class);
					intent.putExtra("user", new BetterJSONObject(item.getJSONObject("user")));
					mContext.startActivity(intent);
				}
			};


            final ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
            boolean hasUserIcon = item.getJSONObject("user").getString("user_icon_url") != null;

            userPic.setOnClickListener(showUser);
            postedOn.setOnClickListener(showUser);

            if (hasUserIcon) {
                Picasso.with(mContext)
                        .load(item.getJSONObject("user").getString("user_icon_url"))
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
                if (mIsNewLayout) {
                    userPic.setAlpha(100);
                }
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
				});
			}

			if (item.getString("type").equals("comment")) {
				// Comment
				comment.setVisibility(View.VISIBLE);
				idLayout.setVisibility(View.GONE);
                loading.setVisibility(View.GONE);
                if (mIsNewLayout) idAgreeLayout.setVisibility(View.GONE);

				comment.setText(Html.fromHtml(item.getString("body")));
				Linkify.addLinks(comment, Linkify.ALL);
				comment.setMovementMethod(LinkMovementMethod.getInstance());

                if (mIsNewLayout) {
                    postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
                    if (hasUserIcon) userPic.setAlpha(255);
                }

			} else {
				// Identification
				idLayout.setVisibility(View.VISIBLE);
				String body = item.getString("body");
				if (body != null && body.length() > 0) {
					comment.setText(Html.fromHtml(body));
					Linkify.addLinks(comment, Linkify.ALL);
					comment.setMovementMethod(LinkMovementMethod.getInstance());

                    comment.setVisibility(View.VISIBLE);

                    if (!mIsNewLayout) {
                        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) comment.getLayoutParams();
                        layoutParams.setMargins(
                                layoutParams.leftMargin,
                                layoutParams.topMargin + 25,
                                layoutParams.rightMargin,
                                layoutParams.bottomMargin);
                        comment.setLayoutParams(layoutParams);
                    }
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
				
				int rankLevel = item.getJSONObject("taxon").optInt("rank_level");
                idTaxonName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
				if (rankLevel <= 20) {
					idTaxonName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
				}

				Boolean isCurrent = item.getBoolean("current");
				if ((isCurrent == null) || (!isCurrent)) {
					// An outdated identification - show as faded-out
					idName.setTextColor(idName.getTextColors().withAlpha(100));
					idTaxonName.setTextColor(idTaxonName.getTextColors().withAlpha(100));
					if (!mIsNewLayout) postedOn.setTextColor(postedOn.getTextColors().withAlpha(100));
					idPic.setAlpha(100);
					userPic.setAlpha(100);
				} else {
					idName.setTextColor(idName.getTextColors().withAlpha(255));
					idTaxonName.setTextColor(idTaxonName.getTextColors().withAlpha(255));
					if (!mIsNewLayout) postedOn.setTextColor(postedOn.getTextColors().withAlpha(255));
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

						if (!mIsNewLayout) {
                            agree.setVisibility(View.GONE);
                        }
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
                    if (mIsNewLayout) {
                        idAgreeLayout.setVisibility(View.VISIBLE);
                    } else {
                        agree.setVisibility(View.VISIBLE);
                    }
				} else {
					// Second (or more) taxon id of its kind - don't show agree button
                    if (mIsNewLayout) {
                        idAgreeLayout.setVisibility(View.GONE);
                    } else {
                        agree.setVisibility(View.GONE);
                    }
				}

                if (moreMenu != null) moreMenu.setVisibility(View.GONE);

				if ((mLogin != null) && (username.equalsIgnoreCase(mLogin))) {
					if (!mIsNewLayout) {
                        ((Button)agree).setText(R.string.remove);
                        agree.setVisibility(View.VISIBLE);
                    } else {
                        idAgreeLayout.setVisibility(View.GONE);
                        if (moreMenu != null) moreMenu.setVisibility(View.VISIBLE);
                    }

					if ((isCurrent == null) || (!isCurrent)) {
						// Faded IDs should not have a "Remove" button
                        if (mIsNewLayout) {
                            idAgreeLayout.setVisibility(View.GONE);
                        } else {
                            agree.setVisibility(View.GONE);
                        }
					}
				} else {
					if (!mIsNewLayout) ((Button)agree).setText(R.string.agree);
				}

				if ((mAgreeing.get(position) != null) && (mAgreeing.get(position) == true)) {
                    loading.setVisibility(View.VISIBLE);

                    if (!mIsNewLayout) {
                        agree.setVisibility(View.GONE);
                    } else {
                        idAgreeLayout.setVisibility(View.GONE);
                    }
				}

				if (mLogin == null) {
					// Can't agree if not logged in
                    if (!mIsNewLayout) {
                        agree.setVisibility(View.GONE);
                    } else {
                        idAgreeLayout.setVisibility(View.GONE);
                    }
				}

                if (mIsNewLayout) {
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
                    idTaxonName.setOnClickListener(listener);

                }

			}


            if (moreMenu != null) {
                if ((mLogin == null) || ((mLogin != null) && (!username.equalsIgnoreCase(mLogin)) && (mReadOnly))) {
                    moreMenu.setVisibility(View.GONE);
                }
            }
        } catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		view.setTag(item);
        if (!mIsNewLayout) view.setOnClickListener(this);

		return view;
	}

    public static String formatIdDate(Timestamp postDate) {
        Duration difference = new Duration(postDate.getTime(), (new Date()).getTime());
        long days = difference.getStandardDays();
        long hours = difference.getStandardHours();
        long minutes = difference.getStandardMinutes();

        if (days <= 30) {
            // Less than 30 days ago - display as 3m (mins), 3h (hours), 3d (days) or 3w (weeks)
            if (days < 1) {
                if (hours < 1) {
                    return String.format("%dm", minutes);
                } else {
                    return String.format("%dh", hours);
                }
            } else if (days < 7) {
                return String.format("%dd", days);
            } else {
                return String.format("%dw", days / 7);
            }
        } else {
            Calendar today = Calendar.getInstance();
            today.setTime(new Date());
            Calendar calDate = Calendar.getInstance();
            calDate.setTimeInMillis(postDate.getTime());

            String dateFormatString;
            if (today.get(Calendar.YEAR) > calDate.get(Calendar.YEAR)) {
                // Previous year(s)
                dateFormatString = "MM/dd/yy";
            } else {
                // Current year
                dateFormatString = "MMM d";
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString);
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

