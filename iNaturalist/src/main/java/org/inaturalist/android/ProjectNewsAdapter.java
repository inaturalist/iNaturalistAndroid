package org.inaturalist.android;


import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
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
import org.w3c.dom.Text;

import java.text.DecimalFormat;
import java.util.ArrayList;

class ProjectNewsAdapter extends ArrayAdapter<String> {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private JSONObject mProject;

    public ProjectNewsAdapter(Context context, JSONObject project, ArrayList<JSONObject> results) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
        mProject = project;
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.project_news_item, parent, false);
        JSONObject item = mResultList.get(position);

        try {
            ImageView projectPic = (ImageView) view.findViewById(R.id.project_pic);
            TextView projectTitle = (TextView) view.findViewById(R.id.project_title);
            TextView newsDate = (TextView) view.findViewById(R.id.news_date);
            TextView newsTitle = (TextView) view.findViewById(R.id.news_title);
            TextView newsContent = (TextView) view.findViewById(R.id.news_content);

            if (mProject.has("icon_url") && !mProject.isNull("icon_url")) {
                UrlImageViewHelper.setUrlDrawable(projectPic, mProject.getString("icon_url"));
            }
            projectTitle.setText(mProject.getString("title"));

            newsTitle.setText(item.getString("title"));
            String noHTML = Html.fromHtml(item.getString("body")).toString();
            newsContent.setText(noHTML);
            BetterJSONObject newsItem = new BetterJSONObject(item);
            newsDate.setText(CommentsIdsAdapter.formatIdDate(newsItem.getTimestamp("updated_at")));

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

}

