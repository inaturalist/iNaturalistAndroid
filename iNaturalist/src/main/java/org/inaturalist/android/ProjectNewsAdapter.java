package org.inaturalist.android;


import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
import android.util.Log;
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
import org.tinylog.Logger;
import org.w3c.dom.Text;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ProjectNewsAdapter extends ArrayAdapter<String> {
    private static final String TAG = "ProjectNewsAdapter";
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private JSONObject mProject;

    public ProjectNewsAdapter(Context context, JSONObject project, ArrayList<JSONObject> results) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
        Collections.sort(mResultList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject news1, JSONObject news2) {
                BetterJSONObject news1json = new BetterJSONObject(news1);
                BetterJSONObject news2json = new BetterJSONObject(news2);

                return news2json.getTimestamp("published_at").compareTo(news1json.getTimestamp("published_at"));
            }
        });
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
            ImageView postPic = (ImageView) view.findViewById(R.id.post_pic);
            TextView projectTitle = (TextView) view.findViewById(R.id.project_title);
            TextView newsDate = (TextView) view.findViewById(R.id.news_date);
            TextView newsTitle = (TextView) view.findViewById(R.id.news_title);
            TextView newsContent = (TextView) view.findViewById(R.id.news_content);

            JSONObject project = mProject != null ? mProject : item.getJSONObject("parent");

            if (project.has("icon_url") && !project.isNull("icon_url")) {
                UrlImageViewHelper.setUrlDrawable(projectPic, project.getString("icon_url"));
            }
            projectTitle.setText(project.optString("title", project.optString("name")));

            newsTitle.setText(item.getString("title"));
            String html = item.getString("body");
            String firstPhotoUrl = findFirstPhotoUrl(html);
            html = html.replaceAll("<img .+?>", ""); // Image tags do not get removed cleanly by toString
            HtmlUtils.fromHtml(newsContent, html, false);
            String noHTMLDescription = newsContent.getText().toString();
            newsContent.setText(noHTMLDescription.replaceAll("[\\xa0]+", "").trim()); // Strip all HTML/Markdown formatting in the preview text
            BetterJSONObject newsItem = new BetterJSONObject(item);
            newsDate.setText(CommentsIdsAdapter.formatIdDate(mContext, newsItem.getTimestamp("published_at")));

            if (firstPhotoUrl != null) {
                // Set the article photo
                postPic.setVisibility(View.VISIBLE);
                UrlImageViewHelper.setUrlDrawable(postPic, firstPhotoUrl, new UrlImageViewCallback() {
                    @Override
                    public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                    }

                    @Override
                    public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                        Bitmap centerCrop = ImageUtils.centerCropBitmap(loadedBitmap);
                        return centerCrop;
                    }
                });
            } else {
                // No article photo
                postPic.setVisibility(View.GONE);
            }

            view.setTag(item);
        } catch (JSONException e) {
            Logger.tag(TAG).error(e);
        }

        return view;
    }

    private String findFirstPhotoUrl(String html) {
        // Find an <img> HTML tag and retrieve its "src" attribute
        String regex = "<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1);
        } else {
            return null;
        }
    }

}

