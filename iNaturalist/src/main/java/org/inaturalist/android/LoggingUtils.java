package org.inaturalist.android;

import android.util.Log;

public class LoggingUtils {

    public static void largeLog(String tag, String content) {
        if (content.length() > 4000) {
            Log.d(tag, content.substring(0, 4000));
            largeLog(tag, content.substring(4000));
        } else {
            Log.d(tag, content);

        }
    }
}
