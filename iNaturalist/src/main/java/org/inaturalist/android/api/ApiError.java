package org.inaturalist.android.api;

/**
 * Parent of all errors that can emerge from interactions with the network. If you receive this
 * class specifically, it indicates the 'general case' error of 'something went wrong and we
 * don't know what or why'. Typically, you will receive a subclass specific to the type of error
 * that happened
 *
 * Internal error in the API code. Issue may be a bug in the API code, or the server may be returning
 * some error we've never seen before and do not know how to handle. This is the
 */
public class ApiError extends Exception {
    public ApiError(String message) {
        super(message);
    }
}
