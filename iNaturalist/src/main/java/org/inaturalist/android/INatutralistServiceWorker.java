package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import static org.inaturalist.android.DataBuilderExtensionKt.isSerializable;
import static org.inaturalist.android.DataBuilderExtensionKt.getSerializable;

import org.apache.commons.lang3.ArrayUtils;
import org.tinylog.Logger;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;

public class INatutralistServiceWorker extends Worker {
    private static final String TAG = "INatutralistServiceWorker";

    public INatutralistServiceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data data = getInputData();

        // Convert from Worker Data class to Intent Bundle

        String action = data.getString(INaturalistService.ACTION);
        Intent intent = new Intent(action);

        Map<String, Object> dataMap = data.getKeyValueMap();
        for (String key : dataMap.keySet()) {
            if (key.equals(INaturalistService.ACTION)) continue;

            // Since Data.Builder doesn't support Serializable by default,
            // we need to use our extension code for this
            if (isSerializable(data, key)) {
                Serializable value = getSerializable(data, key);
                intent.putExtra(key, value);
            } else {
                Object value = dataMap.get(key);

                if (value == null) {
                    intent.putExtra(key, (String) null);
                } else if (value instanceof String) {
                    intent.putExtra(key, (String) value);
                } else if (value instanceof Float) {
                    intent.putExtra(key, (Float) value);
                } else if (value instanceof Integer) {
                    intent.putExtra(key, (Integer) value);
                } else if (value instanceof Parcelable) {
                    intent.putExtra(key, (Parcelable) value);
                } else if (value instanceof Serializable) {
                    intent.putExtra(key, (Serializable) value);
                } else if (value instanceof Boolean) {
                    intent.putExtra(key, (Boolean) value);
                } else if (value instanceof Double) {
                    intent.putExtra(key, (Double) value);
                } else if (value instanceof Long) {
                    intent.putExtra(key, (Long) value);
                } else if (value instanceof ArrayList) {
                    ArrayList<?> arrayList = ((ArrayList) value);
                    if (arrayList.size() == 0) {
                        Logger.tag(TAG).error("ArrayList is empty - cannot determine type: " + key + ": " + value);
                        continue;
                    }
                    Object arrayValue = arrayList.get(0);

                    if (arrayValue instanceof Integer) {
                        intent.putExtra(key, ((ArrayList<Integer>) arrayList).stream().mapToInt(i -> i).toArray());
                    } else if (arrayValue instanceof String) {
                        intent.putExtra(key, ((ArrayList<String>) arrayList));
                    } else if (arrayValue instanceof Boolean) {
                        intent.putExtra(key, ArrayUtils.toPrimitive((Boolean[]) arrayList.toArray()));
                    } else {
                        Logger.tag(TAG).error("Unsupported ArrayList class type: " + key + ": " + value.getClass() + " = " + value);
                    }
                } else {
                    Logger.tag(TAG).error("Unsupported class type: " + key + ": " + (value != null ? value.getClass() : "null") + " = " + value);
                }
            }
        }

        INaturalistServiceImplementation implementation = new INaturalistServiceImplementation(getApplicationContext());
        implementation.onHandleIntentWorker(intent);

        return Result.success();
    }
}