package org.inaturalist.android.api.cbhelpers;

import org.inaturalist.android.api.ApiCallback;
import org.inaturalist.android.api.ApiError;
import org.inaturalist.android.api.Void;
import org.json.JSONArray;

import okhttp3.Call;

public class ArrayToVoidHelper implements ApiCallback<JSONArray> {

    private final ApiCallback<Void> mCallback;

    public ArrayToVoidHelper(ApiCallback<Void> voidCb) {
        mCallback = voidCb;
    }

    @Override
    public void onApiError(ApiError e) {
        mCallback.onApiError(e);
    }

    @Override
    public void onResponse(Call call, JSONArray json) {
        mCallback.onResponse(call, Void.instance);
    }
}
