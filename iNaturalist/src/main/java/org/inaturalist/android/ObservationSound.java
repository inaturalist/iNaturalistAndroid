package org.inaturalist.android;

import java.io.Serializable;

public class ObservationSound implements Serializable {
    public Integer id;
    public String file_url;
    public String attribution;
    public String file_content_type;
    public String subtype;
    public Integer observation_id;
    public Integer _observation_id;

    public ObservationSound(BetterJSONObject json) {
        this.id = json.getInt("id");
        this.file_url = json.getString("file_url");
        this.attribution = json.getString("attribution");
        this.file_content_type = json.getString("file_content_type");
        this.subtype = json.getString("subtype");
    }

    public boolean isSoundCloud() {
        return (subtype != null) && (subtype.equals("SoundcloudSound"));
    }
}
