package org.inaturalist.android;

import android.util.Log;

public class LoggingUtils {

    public static void largeLog(String tag, String content) {
        int index = 0;
        if (content == null) {
            Log.d(tag, "null");
            return;
        }

        int length = content.length();
        do {
            Log.d(tag, content.substring(index, Math.min(index + 4000, length)));
            index += 4000;
        } while ((index < length) && (index < 1024 * 500));
    }
}
