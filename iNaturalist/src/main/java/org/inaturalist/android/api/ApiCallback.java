package org.inaturalist.android.api;

import androidx.annotation.WorkerThread;

import okhttp3.Call;

/**
 * Callback for Async network requests. Intentionally modeled after Retrofit's API to ease the
 * path forward. Once we can get everything working using this style of calling, we can migrate
 * over to retrofit and let it handle work for us.
 *
 * For time being, rather than handling 'Throwable', we subclass all possible errors and only handle
 * one type. This is specifically because the synchronous code already uses exceptions heavily
 * to bubble up issues. Using Throwable would result in really confusing code while we transition
 * (because a method like 'onApiError(Throwable)' would only ignore exception types used only in the
 * service (b/c it would only be called from the API code), which would be super confusing to readers.
 * For time being, it's much simpler to make it clear the exception is coming from stuff in the api
 * package and therefore your error handlers do not need to handle the service-specific exception
 * types
 *
 * @param <T> Successful response body type
 */
public interface ApiCallback<T> {

    @WorkerThread
    void onApiError(Call call, ApiError e);

    @WorkerThread
    void onResponse(Call call, T response);
}
