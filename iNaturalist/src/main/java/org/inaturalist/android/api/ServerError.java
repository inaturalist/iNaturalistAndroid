package org.inaturalist.android.api;

import androidx.annotation.Nullable;

import java.util.Date;

/**
 * An unrecoverable error in the class of 5xx occurred. This is a server-side issue,
 * client can do nothing but wait. May optionally include a Date as calculated from
 * the HTTP Retry-After header. Servers occasionally include this header for scheduled
 * downtime to inform the clients how long they need to wait before the server should be
 * back
 */
public class ServerError extends Exception {
    public Date retryAfter;

    ServerError(@Nullable Date retryAfter) {
        this.retryAfter = retryAfter;
    }
}
