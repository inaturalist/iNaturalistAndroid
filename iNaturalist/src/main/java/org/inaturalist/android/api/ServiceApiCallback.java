package org.inaturalist.android.api;

import org.inaturalist.android.INaturalistService;

/**
 * Convenience class that handles errors in a generic (but appropriate to the service) manner.
 * Useful if you don't need to special-case error handling
 *
 * @param <T>
 */
public abstract class ServiceApiCallback<T> extends ApiCallback<T> {

    private final INaturalistService mService;

    public ServiceApiCallback(INaturalistService service) {
        mService = service;
    }

    @Override
    public void onApiError(ApiError e) {
        mService.handleApiError(e);
    }

    public abstract void onResponse(T response);
}
