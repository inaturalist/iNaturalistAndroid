package org.inaturalist.android;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.Bundler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tinylog.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Where all the android-state custom bundlers are implemented */
public class AndroidStateBundlers {

    public static final class ListPairBundler implements Bundler<List<Pair<Uri, Long>>> {
        @Override
        public void put(@NonNull String key, @NonNull List<Pair<Uri, Long>> value, @NonNull Bundle bundle) {
            String str = value.toString();
            bundle.putString(key, str.substring(1, str.length() - 1));
        }

        @Nullable
        @Override
        public List<Pair<Uri, Long>> get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                String parts[] = bundle.getString(key).split(",");
                List<Pair<Uri, Long>> results = new ArrayList<>();
                for (String value : parts) {
                    value = value.trim();
                    if (value.length() == 0) continue;

                    String[] innerParts = value.substring(5, value.length() - 1).split(" ", 2);
                    if (innerParts.length < 2) continue;
                    results.add(new Pair<Uri, Long>(Uri.parse(innerParts[0]), Long.valueOf(innerParts[1])));
                }

                return results;

            } else {
                return null;
            }
        }
    }

    public static final class SetBundler implements Bundler<Set<Long>> {
        @Override
        public void put(@NonNull String key, @NonNull Set<Long> value, @NonNull Bundle bundle) {
            String str = value.toString();
            bundle.putString(key, str.substring(1, str.length() - 1));
        }

        @Nullable
        @Override
        public Set<Long> get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                String parts[] = bundle.getString(key).split(",");
                Set<Long> results = new HashSet<>();
                for (String value : parts) {
                    value = value.trim();
                    if (value.length() == 0) continue;

                    results.add(Long.valueOf(value));
                }

                return results;

            } else {
                return null;
            }
        }
    }

    public static final class ListBundler implements Bundler<List<Integer>> {
        @Override
        public void put(@NonNull String key, @NonNull List<Integer> value, @NonNull Bundle bundle) {
            String str = value.toString();
            bundle.putString(key, str.substring(1, str.length() - 1));
        }

        @Nullable
        @Override
        public List<Integer> get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                String parts[] = bundle.getString(key).split(",");
                List<Integer> results = new ArrayList<>();
                for (String value : parts) {
                    value = value.trim();
                    if (value.length() == 0) continue;

                    results.add(Integer.valueOf(value));
                }

                return results;

            } else {
                return null;
            }
        }
    }

    public static final class SerializableBundler implements Bundler<Serializable> {
        @Override
        public void put(@NonNull String key, @NonNull Serializable value, @NonNull Bundle bundle) {
            bundle.putSerializable(key, value);
        }

        @Nullable
        @Override
        public Serializable get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                return bundle.getSerializable(key);
            } else {
                return null;
            }
        }
    }

    public static final class JSONArrayBundler implements Bundler<JSONArray> {
        private static final String TAG = "JSONArrayBundler";

        @Override
        public void put(@NonNull String key, @NonNull JSONArray value, @NonNull Bundle bundle) {
            bundle.putString(key, value.toString());
        }

        @Nullable
        @Override
        public JSONArray get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                try {
                    return new JSONArray(bundle.getString(key));
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                    return null;
                }
            } else {
                return null;
            }
        }
    }

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

    public static final class BetterJSONListBundler implements Bundler<List<BetterJSONObject>> {
        private static final String TAG = "BetterJSONListBundler";

        @Override
        public void put(@NonNull String key, @NonNull List<BetterJSONObject> value, @NonNull Bundle bundle) {
            if (value != null) {
                StringBuilder string = new StringBuilder();
                string.append('[');
                for (int i = 0; i < value.size(); i++) {
                    BetterJSONObject obj = value.get(i);
                    string.append(obj.getJSONObject().toString());
                    if (i < value.size() - 1) {
                        string.append(',');
                    }
                }
                string.append(']');
                bundle.putString(key, string.toString());
            }
        }

        @Nullable
        @Override
        public List<BetterJSONObject> get(@NonNull String key, @NonNull Bundle bundle) {
            List<BetterJSONObject> results = new ArrayList<BetterJSONObject>();

            if (!bundle.containsKey(key)) {
                return null;
            }

            String obsString = bundle.getString(key);
            if (obsString != null) {
                try {
                    JSONArray arr = new JSONArray(obsString);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        if (obj != null) {
                            results.add(new BetterJSONObject(obj));
                        }
                    }

                    return results;
                } catch (JSONException exc) {
                    Logger.tag(TAG).error(exc);
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    public static final class JSONListBundler implements Bundler<List<JSONObject>> {
        private static final String TAG = "JSONListBundler";

        @Override
        public void put(@NonNull String key, @NonNull List<JSONObject> value, @NonNull Bundle bundle) {
            if (value != null) {
                JSONArray arr = new JSONArray(value);
                bundle.putString(key, arr.toString());
            }
        }

        @Nullable
        @Override
        public List<JSONObject> get(@NonNull String key, @NonNull Bundle bundle) {
            List<JSONObject> results = new ArrayList<JSONObject>();

            if (!bundle.containsKey(key)) {
                return null;
            }

            String obsString = bundle.getString(key);
            if (obsString != null) {
                try {
                    JSONArray arr = new JSONArray(obsString);
                    for (int i = 0; i < arr.length(); i++) {
                        results.add(arr.getJSONObject(i));
                    }

                    return results;
                } catch (JSONException exc) {
                    Logger.tag(TAG).error(exc);
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    public static final class BetterJSONObjectBundler implements Bundler<BetterJSONObject> {
        @Override
        public void put(@NonNull String key, @NonNull BetterJSONObject value, @NonNull Bundle bundle) {
            bundle.putString(key, value != null ? value.getJSONObject().toString() : null);
        }

        @Nullable
        @Override
        public BetterJSONObject get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                String value = bundle.getString(key);
                if (value == null) return null;

                return new BetterJSONObject(value);

            } else {
                return null;
            }
        }
    }

        public static final class JSONObjectBundler implements Bundler<JSONObject> {
            private static final String TAG = "JSONObjectBundler";

            @Override
        public void put(@NonNull String key, @NonNull JSONObject value, @NonNull Bundle bundle) {
            bundle.putString(key, value != null ? value.toString() : null);
        }

        @Nullable
        @Override
        public JSONObject get(@NonNull String key, @NonNull Bundle bundle) {
            if (bundle.containsKey(key)) {
                String value = bundle.getString(key);
                if (value == null) return null;

                try {
                    return new JSONObject(value);
                } catch (JSONException e) {
                    Logger.tag(TAG).error(e);
                    return null;
                }

            } else {
                return null;
            }
        }
    }
}
