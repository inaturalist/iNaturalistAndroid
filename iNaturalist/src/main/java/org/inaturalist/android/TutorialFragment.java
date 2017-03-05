package org.inaturalist.android;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class TutorialFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle args = getArguments();

        boolean isFinalPage = args.getBoolean("final_page", false);

        View v = inflater.inflate(isFinalPage ? R.layout.tutorial_page_get_started : R.layout.tutorial_page, container, false);

        if (!isFinalPage) {
            // "Regular" tutorial page
            int imageResId = args.getInt("image");
            String title = args.getString("title");
            String description = args.getString("description");

            ImageView imageView = (ImageView) v.findViewById(R.id.tutorial_image);
            TextView titleView = (TextView) v.findViewById(R.id.tutorial_title);
            TextView descriptionView = (TextView) v.findViewById(R.id.tutorial_description);

            imageView.setImageResource(imageResId);
            if (title.length() > 0) {
                titleView.setText(title);
            } else {
                titleView.setVisibility(View.GONE);
            }

            descriptionView.setText(Html.fromHtml(description));

        } else {
            // Final tutorial page ("Let's get started")
            TextView skip = (TextView) v.findViewById(R.id.skip);
            Button viewNearbyObs = (Button) v.findViewById(R.id.view_nearby_observations);

            skip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Just close the tutorial
                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ONBOARDING_SKIP);
                    getActivity().finish();
                }
            });

            viewNearbyObs.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Close the tutorial and open up the explore screen
                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_ONBOARDING_VIEW_NEARBY_OBS);
                    ((TutorialActivity)getActivity()).startActivityIfNew(new Intent(getActivity(), INaturalistMapActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), true);
                }
            });
        }

        return v;
    }
}
