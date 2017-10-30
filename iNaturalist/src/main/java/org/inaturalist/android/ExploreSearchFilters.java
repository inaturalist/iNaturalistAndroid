package org.inaturalist.android;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/** Represents explore screen filters / search params */
public class ExploreSearchFilters implements Serializable {
    public transient JSONObject taxon;
    public transient JSONObject place;
    public transient LatLngBounds mapBounds;
    public Set<String> iconicTaxa = new HashSet<>();

    private String placeJson;
    private String taxonJson;

    private void writeObject(ObjectOutputStream oos) throws IOException {
        taxonJson = taxon != null ? taxon.toString() : null;
        placeJson = place != null ? place.toString() : null;

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
            place = placeJson != null ? new JSONObject(placeJson) : null;
            taxon = taxonJson != null ? new JSONObject(taxonJson) : null;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (ois.available() > 0) {
            mapBounds = new LatLngBounds(new LatLng(ois.readDouble(), ois.readDouble()), new LatLng(ois.readDouble(), ois.readDouble()));
        }
    }

    /** Returns true if no search filters are set at all */
    public boolean isEmpty() {
        // TODO - add more conditions for filters later on
        return (taxon == null) && (place == null) && (mapBounds == null) && (iconicTaxa.isEmpty());
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

        if (mapBounds != null) {
            url.append(String.format("&swlng=%s&swlat=%s&nelng=%s&nelat=%s",
                    mapBounds.southwest.longitude, mapBounds.southwest.latitude,
                    mapBounds.northeast.longitude, mapBounds.northeast.latitude
            ));
        }

        if (url.length() == 0) return url.toString();

        return url.substring(1);
    }
}
