package org.inaturalist.android;

import android.text.format.DateFormat;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/** Represents explore screen filters / search params */
public class ExploreSearchFilters implements Serializable {
    public static final String QUALITY_GRADE_CASUAL = "casual";
    public static final String QUALITY_GRADE_NEEDS_ID = "needs_id";
    public static final String QUALITY_GRADE_RESEARCH = "research";


    public boolean isCurrentLocation = false;

    public transient JSONObject taxon;
    public transient JSONObject place;
    public transient JSONObject project;
    public transient JSONObject user;
    public transient LatLngBounds mapBounds;
    public Set<String> iconicTaxa = new HashSet<>();
    public Set<String> qualityGrade = new HashSet<>();

    public Set<Integer> observedOnMonths = new HashSet<>(); // List of months
    public Date observedOn; // Exact date
    // Between min and max dates
    public Date observedOnMinDate;
    public Date observedOnMaxDate;

    public static final int DATE_TYPE_ANY = 0;
    public static final int DATE_TYPE_EXACT_DATE = 1;
    public static final int DATE_TYPE_MIN_MAX_DATE = 2;
    public static final int DATE_TYPE_MONTHS = 3;

    public int dateFilterType = DATE_TYPE_ANY;

    private String placeJson;
    private String taxonJson;
    private String projectJson;
    private String userJson;

    public Integer annotationNameId;
    public String annotationName;
    public Integer annotationValueId;
    public String annotationValue;


    public ExploreSearchFilters() {
        resetToDefault();
    }

    // Reset search filters to default
    public void resetToDefault() {
        qualityGrade = new HashSet<>();
        qualityGrade.add(QUALITY_GRADE_RESEARCH);
        qualityGrade.add(QUALITY_GRADE_NEEDS_ID);

        user = null;
        project = null;
        iconicTaxa = new HashSet<>();

        observedOn = null;
        observedOnMinDate = null;
        observedOnMaxDate = null;
        observedOnMonths = new HashSet<>();

        dateFilterType = DATE_TYPE_ANY;

        annotationNameId = null;
        annotationValueId = null;
        annotationName = null;
        annotationValue = null;
    }

    public boolean isDirty() {
        return ((!iconicTaxa.isEmpty()) || (project != null) || (user != null) ||
                (qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_CASUAL)) ||
                (!qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_NEEDS_ID)) ||
                (!qualityGrade.contains(ExploreSearchFilters.QUALITY_GRADE_RESEARCH)) ||
                (dateFilterType != ExploreSearchFilters.DATE_TYPE_ANY) ||
                (observedOn != null) || (observedOnMinDate != null) ||
                (observedOnMaxDate != null) || (!observedOnMonths.isEmpty()) ||
                (annotationNameId != null) || (annotationValueId != null));
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        taxonJson = taxon != null ? taxon.toString() : null;
        placeJson = place != null ? place.toString() : null;
        projectJson = project != null ? project.toString() : null;
        userJson = user != null ? user.toString() : null;

        oos.defaultWriteObject();

        if (mapBounds != null) {
            oos.writeDouble(mapBounds.southwest.latitude);
            oos.writeDouble(mapBounds.southwest.longitude);
            oos.writeDouble(mapBounds.northeast.latitude);
            oos.writeDouble(mapBounds.northeast.longitude);
        }
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();

        try {
            user = userJson != null ? new JSONObject(userJson) : null;
            project = projectJson != null ? new JSONObject(projectJson) : null;
            place = placeJson != null ? new JSONObject(placeJson) : null;
            taxon = taxonJson != null ? new JSONObject(taxonJson) : null;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (ois.available() > 0) {
            mapBounds = new LatLngBounds(new LatLng(ois.readDouble(), ois.readDouble()), new LatLng(ois.readDouble(), ois.readDouble()));
        }
    }

    // Returns a string representation of the search filters, for use a URL search query (e.g.
    // taxon_id=1234&place_id=567&iconic_taxa=Aves )
    public String toUrlQueryString() {
        StringBuilder url = new StringBuilder();

        if (!iconicTaxa.isEmpty()) {
            url.append("&iconic_taxa=" + StringUtils.join(iconicTaxa, ","));
        }

        if (taxon != null) {
            url.append("&taxon_id=" + taxon.optInt("id"));
        }

        if (place != null) {
            url.append("&place_id=" + place.optInt("id"));
        }

        if (project != null) {
            url.append("&project_id=" + project.optInt("id"));
        }

        if (user != null) {
            if (user.has("login")) {
                url.append("&user_login=" + user.optString("login"));
            } else {
                url.append("&user_id=" + user.optInt("id"));
            }
        }

        if (!qualityGrade.isEmpty()) {
            url.append("&quality_grade=" + StringUtils.join(qualityGrade, ","));
        }

        if (mapBounds != null) {
            url.append(String.format("&swlng=%s&swlat=%s&nelng=%s&nelat=%s",
                    mapBounds.southwest.longitude, mapBounds.southwest.latitude,
                    mapBounds.northeast.longitude, mapBounds.northeast.latitude
            ));
        }

        if ((dateFilterType == DATE_TYPE_MONTHS) && (!observedOnMonths.isEmpty())) {
            url.append("&month=" + StringUtils.join(observedOnMonths, ","));
        } else if ((dateFilterType == DATE_TYPE_EXACT_DATE) && (observedOn != null)) {
            url.append("&observed_on=" + formatDate(observedOn));
        } else if (dateFilterType == DATE_TYPE_MIN_MAX_DATE) {
            if (observedOnMinDate != null) url.append("&d1=" + formatDate(observedOnMinDate));
            if (observedOnMaxDate != null) url.append("&d2=" + formatDate(observedOnMaxDate));
        }

        if (annotationNameId != null) {
            url.append("&term_id=" + annotationNameId);

            if (annotationValueId != null) {
                url.append("&term_value_id=" + annotationValueId);
            }
        }

        if (url.length() == 0) return url.toString();

        return url.substring(1);
    }

    public String formatDate(Date date) {
        return DateFormat.format("yyyy-MM-dd", date).toString();
    }
}
