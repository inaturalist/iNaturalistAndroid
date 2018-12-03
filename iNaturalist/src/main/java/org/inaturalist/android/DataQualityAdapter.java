package org.inaturalist.android;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class DataQualityAdapter extends ArrayAdapter<DataQualityItem> {
    private final INaturalistApp mApp;
    private final OnDataQualityActions mOnDataQualityActions;
    private final ActivityHelper mHelper;
    private Context mContext;

    private List<DataQualityItem> mItems;

    public interface OnDataQualityActions {
        // When the user agrees with a metric
        void onDataQualityAgree(String metric);
        // When the user disagrees with a metric
        void onDataQualityDisagree(String metric);
        // When the user deletes his vote on a metric
        void onDataQualityVoteDelete(String metric);
    }

    public DataQualityAdapter(Context context, OnDataQualityActions onDataQualityActions, List<DataQualityItem> items) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mOnDataQualityActions = onDataQualityActions;
        mApp = (INaturalistApp) mContext.getApplicationContext();
        mHelper = new ActivityHelper(mContext);

        mItems = items;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.data_quality_item, parent, false);
        } else {
            view = convertView;
        }

        final DataQualityItem item = mItems.get(position);

        ImageView icon = (ImageView) view.findViewById(R.id.icon);
        TextView title = (TextView) view.findViewById(R.id.name);

        ImageView agreeIcon = (ImageView) view.findViewById(R.id.agree_icon);
        ImageView disagreeIcon = (ImageView) view.findViewById(R.id.disagree_icon);
        ImageView agreePrefix = (ImageView) view.findViewById(R.id.agree_prefix);
        ImageView disagreePrefix = (ImageView) view.findViewById(R.id.disagree_prefix);
        final ViewGroup agreeContainer = (ViewGroup) view.findViewById(R.id.agree_container);
        final ViewGroup disagreeContainer = (ViewGroup) view.findViewById(R.id.disagree_container);
        TextView agreeText = (TextView) view.findViewById(R.id.agree_text);
        TextView disagreeText = (TextView) view.findViewById(R.id.disagree_text);
        final View loading = view.findViewById(R.id.loading);

        loading.setVisibility(View.GONE);

        title.setText(item.titleResource);
        icon.setImageResource(item.iconResource);
        icon.setColorFilter(Color.parseColor("#999999"));

        if (!item.isVotable) {
            agreeIcon.setVisibility(View.GONE);
            agreeText.setVisibility(View.GONE);
            disagreeIcon.setVisibility(View.GONE);
            disagreeText.setVisibility(View.GONE);
        } else {
            agreeIcon.setVisibility(View.VISIBLE);
            disagreeIcon.setVisibility(View.VISIBLE);
        }

        agreePrefix.setVisibility(View.INVISIBLE);
        disagreePrefix.setVisibility(View.INVISIBLE);

        if (item.agreeCount > item.disagreeCount) {
            agreePrefix.setVisibility(View.VISIBLE);
        } else if (item.disagreeCount > item.agreeCount) {
            disagreePrefix.setVisibility(View.VISIBLE);
        } else if (item.isVotable) {
            agreePrefix.setVisibility(View.VISIBLE);
        }

        if ((item.agreeCount > 0) && (item.isVotable)) {
            agreeText.setText(String.valueOf(item.agreeCount));
            agreeText.setVisibility(View.VISIBLE);
        } else {
            agreeText.setVisibility(View.GONE);
        }

        if ((item.disagreeCount > 0) && (item.isVotable)) {
            disagreeText.setText(String.valueOf(item.disagreeCount));
            disagreeText.setVisibility(View.VISIBLE);
        } else {
            disagreeText.setVisibility(View.GONE);
        }


        agreeContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!item.isVotable) return;

                loading.setVisibility(View.VISIBLE);

                if (((Boolean)agreeContainer.getTag()) == true) {
                    // User cancels his previous agreement
                    mOnDataQualityActions.onDataQualityVoteDelete(item.metricName);
                } else {
                    // User agrees with this
                    mOnDataQualityActions.onDataQualityAgree(item.metricName);
                }
            }
        });

        disagreeContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!item.isVotable) return;

                loading.setVisibility(View.VISIBLE);

                if (((Boolean)disagreeContainer.getTag()) == true) {
                    // User cancels his previous disagreement
                    mOnDataQualityActions.onDataQualityVoteDelete(item.metricName);
                } else {
                    // User disagrees with this
                    mOnDataQualityActions.onDataQualityDisagree(item.metricName);
                }
            }
        });

        agreeContainer.setTag(new Boolean(item.currentUserAgrees));
        disagreeContainer.setTag(new Boolean(item.currentUserDisagrees));

        agreeIcon.setColorFilter(Color.parseColor(item.currentUserAgrees ? "#808080" : "#C3C3C3"));
        disagreeIcon.setColorFilter(Color.parseColor(item.currentUserDisagrees ? "#808080" : "#C3C3C3"));

        return view;

    }
}

