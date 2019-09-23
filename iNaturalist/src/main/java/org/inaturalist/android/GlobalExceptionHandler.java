package org.inaturalist.android;

import org.tinylog.Logger;

/** Handle global uncaught exceptions - and logs them to file using our logger (from there, continues
 * to send it up the chain to Fabric) */
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler mRootHandler;

    public GlobalExceptionHandler() {
        // Save original exception handler
        mRootHandler = Thread.getDefaultUncaughtExceptionHandler();

        // Make sure we handle global exceptions now
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(final Thread thread, final Throwable ex) {
        // Log exception
        Logger.error(ex);

        // Call original handler
        mRootHandler.uncaughtException(thread, ex);
    }
}
