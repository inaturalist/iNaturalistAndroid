package org.inaturalist.android;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Custom Tile Provider that uses the app's user agent and installation ID
public abstract class INatTileProvider implements TileProvider {
    private INaturalistApp mApp;
    private int mWidth, mHeight;

    public INatTileProvider(INaturalistApp app, int width, int height) {
        mApp = app;
        mWidth = width;
        mHeight = height;
    }
    public abstract URL getTileUrl(int x, int y, int zoom);

    @Override
    public Tile getTile(int x, int y, int zoom) {
        try {
            URL url = getTileUrl(x, y, zoom);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", INaturalistServiceImplementation.getUserAgent(mApp));
            connection.setRequestProperty("X-Installation-ID", mApp.getInstallationID());

            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteStream.write(buffer, 0, len);
            }

            byte[] tileData = byteStream.toByteArray();
            return new Tile(mWidth, mHeight, tileData);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return NO_TILE;
    }
}
