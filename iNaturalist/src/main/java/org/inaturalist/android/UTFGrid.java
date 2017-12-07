package org.inaturalist.android;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.geometry.Point;

import org.json.JSONArray;
import org.json.JSONObject;

/** Represents a UTFGrid initialized from a JSON object - see https://github.com/mapbox/mbtiles-spec/blob/master/1.1/utfgrid.md */
public class UTFGrid {
    private static final int EXPANSION_PIXELS = 8;

    private JSONArray mGrid;
    private JSONArray mKeys;
    private JSONObject mData;

    private static final int TILE_SIZE = 256;
    private static final String EMPTY_KEY = "";

    public UTFGrid(JSONObject json) {
        mGrid = json.optJSONArray("grid");
        mKeys = json.optJSONArray("keys");
        mData = json.optJSONObject("data");
    }

    private int decodeId(int id) {
        if (id >= 93) id--;
        if (id >= 35) id--;
        id -= 32;
        return id;
    }

    public String getKeyForPixel(int x, int y) {
        int id = 0;

        if ((x >= 0) && (y >= 0) &&
                (x < TILE_SIZE) && (y < TILE_SIZE)) {
            int factor = TILE_SIZE / mGrid.length();
            int row = y / factor;
            int col = x / factor;

            id = Character.codePointAt(mGrid.optString(row), col);
            id = decodeId(id);

            if ((id < 0) || (id >= mKeys.length())) id = 0;
        }

        String key = mKeys.optString(id);

        return key;
    }

    public String getKeyForPixelExpansive(int x, int y) {
        String key = getKeyForPixel(x, y);
        if (!key.equals(EMPTY_KEY)) return key;

        // Search nearby pixels
        int factor = TILE_SIZE / mGrid.length();
        int expansionFactor = EXPANSION_PIXELS * factor; // Search up to EXPANSION_PIXELS pixels away from all directions

        // Slowly expand the search grid around the current pixel
        for (int expansion = factor; expansion <= expansionFactor; expansion += factor) {
            key = getKeyForPixel(x - expansion, y - expansion);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x, y - expansion);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x + expansion, y - expansion);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x + expansion, y);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x + expansion, y + expansion);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x, y + expansion);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x - expansion, y + expansion);
            if (!key.equals(EMPTY_KEY)) return key;
            key = getKeyForPixel(x - expansion, y);
            if (!key.equals(EMPTY_KEY)) return key;
        }

        return EMPTY_KEY;
    }





    /** Returns the data object corresponding to the given tile position
     * @return data object corresponding to the tile position (null if no data for that position)
     */
    public JSONObject getDataForPixel(int x, int y) {
        String key = getKeyForPixelExpansive(x, y);

        // This tile position has no key attached to it - no data
        if (key.equals(EMPTY_KEY)) return null;
        // Non existent key
        if (!mData.has(key)) return null;

        return mData.optJSONObject(key);
    }

}
