package org.inaturalist.android.api;

public class AuthenticationException extends ApiError {
    private static final long serialVersionUID = 1L;

    public AuthenticationException() {
        super("auth needed");
    }
}
