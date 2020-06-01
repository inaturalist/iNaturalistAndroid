package org.inaturalist.android.api;

public class ApiDecodingException extends ApiError {

    public ApiDecodingException(String issue) {
        super("Decoding failure: " + issue);
    }
}
