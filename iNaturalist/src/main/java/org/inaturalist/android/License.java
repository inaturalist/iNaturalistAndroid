package org.inaturalist.android;

import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;

/** Represents a single license (e.g. CC-By-NC-SA) - loaded from license_types.xml by LicenseUtils class */
public class License {
    public String id;
    public String value;
    public String shortName;
    public String name;
    public boolean gbifCompatible;
    public boolean wikimediaCompatible;
    public @DrawableRes Integer logoResource;
    public String url;
    public String description;
}
