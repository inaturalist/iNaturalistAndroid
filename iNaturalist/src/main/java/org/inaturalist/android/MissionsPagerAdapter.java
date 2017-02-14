package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONObject;

import java.util.ArrayList;

public class MissionsPagerAdapter extends PagerAdapter {
    private final float mLocationExpansion;
    private UserSpeciesAdapter mAdapter;
    private ArrayList<JSONObject> mMissions;
    private Context mContext;
    private boolean mIsNearbyMissions;

    public MissionsPagerAdapter(Context context, ArrayList<JSONObject> missions, float locationExpansion, boolean isNearbyMissions) {
        mContext = context;
        mAdapter = new UserSpeciesAdapter(context, missions, UserSpeciesAdapter.VIEW_TYPE_CARDS, null);
        mMissions = missions;
        mLocationExpansion = locationExpansion;
        mIsNearbyMissions = isNearbyMissions;
    }

    @Override
    public int getCount() {
        return mAdapter.getCount();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == (View)object;
    }

    @Override
    public float getPageWidth(int position) {
        return 0.32f;
    }

    @Override
    public Object instantiateItem(ViewGroup container, final int position) {
        View view = mAdapter.getView(position, null, container);
        ((ViewPager)container).addView(view, 0);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Load the missions details screen
                Intent intent = new Intent(mContext, MissionDetails.class);
                intent.putExtra(MissionDetails.MISSION, new BetterJSONObject(mMissions.get(position)));
                intent.putExtra(MissionDetails.LOCATION_EXPANSION, mLocationExpansion);
                mContext.startActivity(intent);

                if (mIsNearbyMissions) {
                    AnalyticsClient.getInstance().logEvent(AnalyticsClient.EVENT_NAME_NEARBY_MISSION);
                }
            }
        });

        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        ((ViewPager) container).removeView((View) object);
    }
}

