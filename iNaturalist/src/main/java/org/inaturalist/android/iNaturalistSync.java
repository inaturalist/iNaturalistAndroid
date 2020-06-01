package org.inaturalist.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.inaturalist.android.api.ApiError;
import org.inaturalist.android.api.AuthenticationException;
import org.json.JSONArray;
import org.tinylog.Logger;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Should only be used from within the {@link INaturalistService}
 *
 * In this context, 'sync' indicates an operation which uses both the database and
 * the network at the same time, with the goal being to ensure they are in a consistent
 * state
 *
 * These types of operations can easily conflict with one another (syncing an edit and a
 * delete to the same observation at the same time). This class exists to resolve these conflicts.
 * All calls to this class are queued and run in serial. There can only be one 'sync' operation
 * using the network at one time. We can later make this more efficient, but for now this
 * makes it correct
 */
public class iNaturalistSync {

    private final INaturalistService mService;
    private final INaturalistApp mApp;

    private final Handler mHandler;
    private static String TAG = "SyncHelper";

    public static String HOST = INaturalistService.HOST;
    public static String API_HOST = INaturalistService.API_HOST;

    static final int MSG_DELETE_OBSERVATIONS = 0;

    // TODO either hold a weakreference or call stop/kill/etc
    // Calling this ctor is a littly tricky. You MUST call it from
    public iNaturalistSync(INaturalistService service, INaturalistApp app) {
        mService = service;
        mApp = app;

        HandlerThread syncThread = new HandlerThread("SyncHelper");
        syncThread.start();
        mHandler = new SyncHandler(syncThread.getLooper());
    }

    private ContentResolver getContentResolver() {
        return mService.getContentResolver();
    }
    
    private String getString(int resource) {
        return mService.getString(resource);
    }

    private static class SyncHandler extends Handler {

        SyncHandler(Looper l) { super(l); }
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_DELETE_OBSERVATIONS:

                    break;

            }
        }
    }

    private void deleteObservations(long[] idsToDelete) throws CancelSyncException, SyncFailedException, IOException, ApiError {
        Message m = mHandler.obtainMessage(MSG_DELETE_OBSERVATIONS, idsToDelete);
    }

    @WorkerThread
    private void handleDeleteObservations(long[] idsToDelete) throws CancelSyncException, SyncFailedException, IOException, ApiError {
        Cursor c;

        if (idsToDelete != null) {
            // Remotely delete selected observations only
            c = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "_id in (" + StringUtils.join(ArrayUtils.toObject(idsToDelete), ",") + ")",
                    null,
                    Observation.DEFAULT_SORT_ORDER);
        } else {
            // Remotely delete any locally-removed observations (marked for deletion)
            c = getContentResolver().query(Observation.CONTENT_URI,
                    Observation.PROJECTION,
                    "is_deleted = 1",
                    null,
                    Observation.DEFAULT_SORT_ORDER);
        }

        Logger.tag(TAG).debug("deleteObservations: Deleting " + c.getCount());

        if (c.getCount() > 0) {
            mApp.notify(getString(R.string.deleting_observations), getString(R.string.deleting_observations));
        }

        // for each observation DELETE to /observations/:id
        ArrayList<Integer> obsIds = new ArrayList<Integer>();
        ArrayList<Integer> internalObsIds = new ArrayList<Integer>();
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            Observation observation = new Observation(c);
            Logger.tag(TAG).debug("deleteObservations: Deleting " + observation);
            JSONArray results = mService.delete(HOST + "/observations/" + observation.id + ".json", null);
            if (results == null) {
                c.close();
                throw new SyncFailedException();
            }

            obsIds.add(observation.id);
            internalObsIds.add(observation._id);
            c.moveToNext();
        }

        Logger.tag(TAG).debug("deleteObservations: Deleted IDs: " + obsIds);
        c.close();

        // Now it's safe to delete all of the observations locally
        getContentResolver().delete(Observation.CONTENT_URI, "is_deleted = 1", null);
        // Delete associated project-fields and photos
        int count1 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count2 = getContentResolver().delete(ObservationSound.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count3 = getContentResolver().delete(ProjectObservation.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count4 = getContentResolver().delete(ProjectFieldValue.CONTENT_URI, "observation_id in (" + StringUtils.join(obsIds, ",") + ")", null);
        int count5 = getContentResolver().delete(ObservationPhoto.CONTENT_URI, "_observation_id in (" + StringUtils.join(internalObsIds, ",") + ")", null);
        int count6 = getContentResolver().delete(ObservationSound.CONTENT_URI, "_observation_id in (" + StringUtils.join(internalObsIds, ",") + ")", null);

        Logger.tag(TAG).debug("deleteObservations: " + count1 + ":" + count2 + ":" + count3 + ":" + count4 + ":" + count5 + ":" + count6);

        checkForCancelSync();

        return true;
    }

    private void checkForCancelSync() throws CancelSyncException {
        if (mApp.getCancelSync()) throw new CancelSyncException();
    }
}
