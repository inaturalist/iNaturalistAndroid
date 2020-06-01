package org.inaturalist.android.api;

import okhttp3.Response;

/**
 * Unused class right now. Idea is to model the API on retrofit. Problem is this
 * breaks bridging adapters that do not have access to the original response. Need to
 * look inside rf code to see how they accomplish this.
 * @param <T>
 */
public abstract class ApiResponse<T> {

//    private final Response mOkResponse;
//    private final T mDecodedBody;
//
//    public ApiResponse(Response okResponse, T decoded) {
//        mOkResponse = okResponse;
//        mDecodedBody = decoded;
//    }
//
//    public T body() {
//        return mDecodedBody;
//    }
//
//    public int code() {
//        return mOkResponse.code();
//    }
//
//    public boolean isSuccessful() {
//        return mOkResponse.isSuccessful();
//    }
}
