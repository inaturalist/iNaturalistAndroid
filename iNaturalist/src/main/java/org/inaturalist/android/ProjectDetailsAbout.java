package org.inaturalist.android;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.regex.Pattern;

public class ProjectDetailsAbout extends Fragment {
    public static final String KEY_PROJECT = "project";
    private BetterJSONObject mProject;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.project_details_about, container, false);
        
        TextView title = (TextView) v.findViewById(R.id.project_title);
        ImageView icon = (ImageView) v.findViewById(R.id.project_pic);
        TextView projectDescription = (TextView) v.findViewById(R.id.project_description);
        
        Bundle bundle = getArguments();
        
        if (bundle != null) {
            mProject = (BetterJSONObject) bundle.getSerializable(KEY_PROJECT);
            title.setText(mProject.getString("title"));
            String iconUrl = mProject.getString("icon_url");
            if ((iconUrl != null) && (iconUrl.length() > 0)) {
                icon.setVisibility(View.VISIBLE);
                UrlImageViewHelper.setUrlDrawable(icon, iconUrl);
            } else {
                icon.setVisibility(View.GONE);
            }
            String description = mProject.getString("description");
            description = description.replace("\n", "\n<br>");
            projectDescription.setText(Html.fromHtml(description));
            Linkify.addLinks(projectDescription, Pattern.compile("www\\..+"), "http://");
            projectDescription.setMovementMethod(LinkMovementMethod.getInstance()); 
        }
        
        return v;
    }
}
