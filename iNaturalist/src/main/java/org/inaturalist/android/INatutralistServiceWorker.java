package org.inaturalist.android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.tinylog.Logger;

public class INatutralistServiceWorker extends Worker {
    private static final String TAG = "INatutralistServiceWorker";
    private INaturalistApp mApp;

    public INatutralistServiceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        mApp = (INaturalistApp) context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWork() {
        Logger.tag(TAG).info("doWork");

        Data data = getInputData();

        // Convert from Worker Data class to Intent Bundle

        String action = data.getString(INaturalistService.ACTION);
        Intent intent = new Intent(action);

        String uuid = data.getString(INaturalistService.REQUEST_UUID);
        Bundle params = mApp.getServiceParams(uuid);
        if (params != null) {
            intent.putExtras(params);
        }

        INaturalistServiceImplementation implementation = new INaturalistServiceImplementation(getApplicationContext());
        implementation.onHandleIntentWorker(intent);

        return Result.success();
    }
}