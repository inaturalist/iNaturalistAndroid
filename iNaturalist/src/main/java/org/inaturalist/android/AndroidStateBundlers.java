package org.inaturalist.android;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.state.Bundler;

/** Where all the android-state custom bundlers are implemented */
public class AndroidStateBundlers {

    public static final class UriBundler implements Bundler<Uri> {
        @Override
        public void put(@NonNull String key, @NonNull Uri value, @NonNull Bundle bundle) {
            bundle.putString(key, value.toString());
        }

        @Nullable
        @Override
        public Uri get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                String value = bundle.getString(key);
                if (value == null) return null;

                return Uri.parse(value);
            } else {
                return null;
            }
        }
    }
}
