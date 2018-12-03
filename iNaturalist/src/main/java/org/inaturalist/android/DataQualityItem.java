package org.inaturalist.android;

/** Represents a single data quality item (e.g. "Date is accurate", "Organism is wild") */
public class DataQualityItem {
    public int iconResource;
    public int titleResource;
    public String metricName; // e.g. "evidence"
    public int agreeCount;
    public int disagreeCount;
    public boolean currentUserAgrees;
    public boolean currentUserDisagrees;
    public boolean isVotable;

    public DataQualityItem(int icon, int title, String metric, boolean votable) {
        iconResource = icon;
        titleResource = title;
        metricName = metric;
        isVotable = votable;
    }
}
