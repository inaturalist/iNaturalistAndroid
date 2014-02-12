package org.inaturalist.android;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.json.JSONArray;
import org.json.JSONException;


public class SerializableJSONArray implements Serializable {
    private transient JSONArray jsonArray;
    
    public SerializableJSONArray() {
        this.jsonArray = new JSONArray();
    }

    public SerializableJSONArray(JSONArray jsonArray) {
        this.jsonArray = jsonArray;
    }

    public JSONArray getJSONArray() {
        return jsonArray;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeObject(jsonArray != null ? jsonArray.toString() : null);
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException, JSONException {
        ois.defaultReadObject();
        String data = (String) ois.readObject();
        jsonArray = (data != null ? new JSONArray(data) : null);
    }
}


