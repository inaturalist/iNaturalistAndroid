package org.inaturalist.android.api;

import org.inaturalist.android.INaturalistService;

import okhttp3.Call;

/**
 * Convenience class that handles errors in a generic (but appropriate to the service) manner.
 * Useful if you don't need to special-case error handling
 *
 * @param <T>
 */
public abstract class ServiceApiCallback<T> implements ApiCallback<T> {

    private final INaturalistService mService;

    public ServiceApiCallback(INaturalistService service) {
        mService = service;
    }

    @Override
    public void onApiError(Call call, ApiError e) {
        mService.handleApiError(e);
    }

    public abstract void onResponse(Call call, T response);
}
