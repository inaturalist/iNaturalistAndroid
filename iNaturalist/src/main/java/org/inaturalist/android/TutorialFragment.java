package org.inaturalist.android;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.util.Log;
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
        int layoutId = args.getInt("layout");

        View v = inflater.inflate(layoutId, container, false);

        if (!isFinalPage) {
            // "Regular" tutorial page
            int imageResId = args.getInt("image");
            int secondaryImageResId = args.getInt("secondaryImage", -1);
            String title = args.getString("title");
            String description = args.getString("description");

            ImageView imageView = (ImageView) v.findViewById(R.id.tutorial_image);
            ImageView secondaryImageView = (ImageView) v.findViewById(R.id.secondary_tutorial_image);
            TextView titleView = (TextView) v.findViewById(R.id.tutorial_title);
            TextView descriptionView = (TextView) v.findViewById(R.id.tutorial_description);

            imageView.setImageResource(imageResId);

            if (title.length() > 0) {
                titleView.setText(title);
            } else {
                titleView.setVisibility(View.GONE);
            }

            descriptionView.setText(Html.fromHtml(description));

            if (secondaryImageResId > -1) {
                secondaryImageView.setImageResource(secondaryImageResId);
            }
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
                    ((TutorialActivity)getActivity()).startActivityIfNew(new Intent(getActivity(), ExploreActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), true);
                }
            });
        }

        return v;
    }
}
