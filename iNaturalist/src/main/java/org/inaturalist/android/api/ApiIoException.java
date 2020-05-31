package org.inaturalist.android.api;

import java.io.IOException;

public class ApiIoException extends ApiError {

    public ApiIoException(IOException e) {
        super("network io exception");
        initCause(e);
    }
}
