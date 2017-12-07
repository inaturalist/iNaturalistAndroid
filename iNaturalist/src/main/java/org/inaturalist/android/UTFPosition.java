package org.inaturalist.android;

import com.google.maps.android.geometry.Point;

/** Represents a single UTF Tile position */
public class UTFPosition {

    private static final int TILE_SIZE = 256;

    private int mPixelPositionX;
    private int mPixelPositionY;
    private int mTilePositionX;
    private int mTilePositionY;

    /** Returns the tile position (in which the pixel is found)  */
    public int getTilePositionX() {
        return mTilePositionX;
    }
    public int getTilePositionY() {
        return mTilePositionY;
    }

    /** Returns the pixel position (relative to the tile it's located at)  */
    public int getPixelPositionX() {
        return mPixelPositionX;
    }
    public int getPixelPositionY() {
        return mPixelPositionY;
    }

    /* Based on: https://developers.google.com/maps/documentation/javascript/examples/map-coordinates */
    public UTFPosition(float zoomLevel, double latitude, double longitude) {
        int scale = (int) Math.pow(2, Math.floor(zoomLevel));

        double[] worldCoordinate = project(latitude, longitude);
        mTilePositionX = (int)Math.floor(worldCoordinate[0] * scale / TILE_SIZE);
        mTilePositionY = (int)Math.floor(worldCoordinate[1] * scale / TILE_SIZE);

        double[] pixelCoordinate = new double[] {
                worldCoordinate[0] * scale,
                worldCoordinate[1] * scale
            };

        mPixelPositionX = (int)(pixelCoordinate[0] - mTilePositionX * TILE_SIZE);
        mPixelPositionY = (int)(pixelCoordinate[1] - mTilePositionY * TILE_SIZE);
    }

    /* Based on: https://developers.google.com/maps/documentation/javascript/examples/map-coordinates */
    private double[] project(double latitude, double longitude) {
        double siny = Math.sin(latitude * Math.PI / 180);

        // Truncating to 0.9999 effectively limits latitude to 89.189. This is
        // about a third of a tile past the edge of the world tile.
        siny = Math.min(Math.max(siny, -0.9999), 0.9999);

        return new double[] {
                (256 * (0.5 + longitude / 360)),
                (256 * (0.5 - Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI)))
        };
    }
}
