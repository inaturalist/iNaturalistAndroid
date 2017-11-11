package org.inaturalist.android;

import android.content.Context;
import android.graphics.Color;
import android.media.Image;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yaron on 10/07/2016.
 */
public class ProjectsAdapter extends ArrayAdapter<JSONObject> implements Filterable {

    private static final String TAG = "ProjectsAdapter";
    private final int mDefaultIcon;

    private List<JSONObject> mItems;
    private List<JSONObject> mOriginalItems;
    private Context mContext;
    private Filter mFilter;
    protected String mCurrentSearchString;
    private String mSearchUrl;

    private ActivityHelper mHelper;
    private boolean mIsUser;

    public interface OnLoading {
        public void onLoading(Boolean loading, int count);
    }

    private OnLoading mOnLoading;

    private ArrayList<JSONObject> autocomplete(String input) {
        // Retrieve the autocomplete results.
        String search = input.toString().toLowerCase();

        ArrayList<JSONObject> resultList = null;

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(mSearchUrl);
            sb.append("?q=");
            sb.append(URLEncoder.encode(search, "utf8"));

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }

        } catch (MalformedURLException e) {
            Log.e(TAG, "Error processing search API URL", e);
        } catch (IOException e) {
            Log.e(TAG, "Error connecting to search API", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        JSONArray predsJsonArray = new JSONArray();

        try {
            predsJsonArray = new JSONArray(jsonResults.toString());
        } catch (JSONException e) {
            JSONObject resultsObject = null;
            try {
                resultsObject = new JSONObject(jsonResults.toString());
                predsJsonArray = resultsObject.getJSONArray("results");
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }

        // Extract the Place descriptions from the results
        resultList = new ArrayList<JSONObject>(predsJsonArray.length());

        for (int i = 0; i < predsJsonArray.length(); i++) {
            resultList.add(predsJsonArray.optJSONObject(i));
        }

        return resultList;
    }

    public void updateItem(int index, JSONObject object) {
        mItems.set(index, object);
    }

    public ProjectsAdapter(Context context, String searchUrl, OnLoading onLoading, List<JSONObject> objects) {
        this(context, searchUrl, onLoading, objects, R.drawable.ic_work_black_24dp);
    }

    public ProjectsAdapter(Context context, String searchUrl, OnLoading onLoading, List<JSONObject> objects, int defaultIcon) {
        this(context, searchUrl, onLoading, objects, defaultIcon, false);
    }
    public ProjectsAdapter(Context context, String searchUrl, OnLoading onLoading, List<JSONObject> objects, int defaultIcon, boolean isUser) {
        super(context, R.layout.project_item, objects);

        mIsUser = isUser;
        mSearchUrl = searchUrl;
        mItems = objects;
        mOriginalItems = new ArrayList<JSONObject>(mItems);
        mContext = context;
        mHelper = new ActivityHelper(mContext);
        mOnLoading = onLoading;
        mDefaultIcon = defaultIcon;

        mFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null) {
                    if (constraint.length() == 0) {
                        filterResults.values = mOriginalItems;
                        filterResults.count = 0;

                    } else {
                        if (mOnLoading != null) mOnLoading.onLoading(true, 0);

                        // Retrieve the autocomplete results.
                        ArrayList<JSONObject> results;
                        mCurrentSearchString = (String) constraint;
                        results = autocomplete(constraint.toString());

                        if (!constraint.equals(mCurrentSearchString)) {
                            // In the meanwhile, new searches were initiated by the user - ignore this result
                            return null;
                        }

                        // Assign the data to the FilterResults
                        if (results == null) {
                            results = new ArrayList<JSONObject>();
                        }

                        filterResults.values = results;
                        filterResults.count = results.size();

                    }
                }

                if (mOnLoading != null) mOnLoading.onLoading(false, filterResults.count);

                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    mItems = (List<JSONObject>) results.values;
                    notifyDataSetChanged();
                } else {
                    if (results != null) {
                        mItems = (ArrayList<JSONObject>) results.values;
                    }

                    notifyDataSetInvalidated();
                }
            }
        };

    }

    public List<JSONObject> getItems() {
        return mItems;
    }

    public void addItemAtBeginning(JSONObject newItem) {
        mItems.add(0, newItem);
    }

    @Override
    public int getCount() {
        return (mItems != null ? mItems.size() : 0);
    }

    @Override
    public JSONObject getItem(int index) {
        return mItems.get(index);
    }


    @Override
    public Filter getFilter() {
        return mFilter;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.observation_project_item, parent, false);
        final BetterJSONObject item = new BetterJSONObject(mItems.get(position));

        TextView projectName = (TextView) view.findViewById(R.id.project_name);
        final String projectTitle = item.getString(mIsUser ? "login" : "title");
        projectName.setText(projectTitle);
        ImageView projectPic = (ImageView) view.findViewById(R.id.project_pic);
        ImageView projectPicNone = (ImageView) view.findViewById(R.id.project_pic_none);
        ImageView userPic = (ImageView) view.findViewById(R.id.user_pic);
        ViewGroup projectPicContainer = (ViewGroup) view.findViewById(R.id.project_pic_container);

        if (mIsUser) {
            userPic.setVisibility(View.VISIBLE);
            projectPicContainer.setVisibility(View.INVISIBLE);
        }

        String iconUrl = item.has("icon") ? item.getString("icon") : item.getString("icon_url");
        if ((iconUrl == null) || (iconUrl.length() == 0)) {
            projectPic.setVisibility(View.GONE);
            projectPicNone.setVisibility(View.VISIBLE);

            RequestCreator req = Picasso.with(mContext)
                    .load(mDefaultIcon)
                    .fit()
                    .centerCrop()
                    .transform(new UserActivitiesAdapter.CircleTransform());

            if (mIsUser) {
                req = req.transform(new UserActivitiesAdapter.CircleTransform());
                req.into(userPic);
                userPic.setColorFilter(Color.parseColor("#5D5D5D"));
            } else {
                req.into(projectPicNone);
            }

        } else {
            projectPic.setVisibility(View.VISIBLE);
            projectPic.setImageResource(mDefaultIcon);
            UrlImageViewHelper.setUrlDrawable(projectPic, iconUrl);

            RequestCreator req = Picasso.with(mContext)
                    .load(iconUrl)
                    .fit()
                    .centerCrop()
                    .placeholder(mDefaultIcon);

            if (mIsUser) {
                req = req.transform(new UserActivitiesAdapter.CircleTransform());
                req.into(userPic);
                userPic.setColorFilter(null);
            } else {
                req.into(projectPic);
            }

            projectPicNone.setVisibility(View.GONE);

        }

        String description = item.getString("description");
        final String noHTMLDescription = Html.fromHtml(description != null ? description : "").toString();
        if ((noHTMLDescription.length() > 0) && (!mIsUser)) {
            ((ViewGroup) view.findViewById(R.id.project_pic_container)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mHelper.alert(projectTitle, noHTMLDescription);
                }
            });
        } else {
            // No description - Hide the info button
            view.findViewById(R.id.project_pic_info).setVisibility(View.GONE);
        }


        view.setTag(item.getJSONObject().toString());

        return view;
    }

    private String getShortDescription(String description) {
        // Strip HTML tags
        if (description == null) return "";

        String noHTML = Html.fromHtml(description).toString();

        return noHTML;
    }
}

