package org.inaturalist.android;

import org.json.JSONObject;

public class INatPlace {
    public String id;
    public Double latitude;
    public Double longitude;
    public Double accuracy;
    public String title;
    public String subtitle;
    public String geoprivacy;

    public INatPlace() {

    }

    public INatPlace(JSONObject j) {
        BetterJSONObject json = new BetterJSONObject(j);

        this.id = json.getString("id");
        this.latitude = json.getDouble("latitude");
        this.longitude = json.getDouble("longitude");
        this.accuracy = json.getDouble("positional_accuracy");
        this.title = json.getString("title");
        this.geoprivacy = json.getString("geoprivacy");
    }
}
