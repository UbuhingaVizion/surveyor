package io.rapidpro.surveyor.work;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules the background submission sync. All syncs share a single unique work name so only one
 * runs at a time (data-friendly) and duplicate work isn't queued.
 */
public final class SyncScheduler {

    public static final String WORK_NAME = "submission-sync";

    private SyncScheduler() {
    }

    /**
     * Ensures a sync is scheduled, respecting the Wi-Fi-only setting
     */
    public static void enqueue(Context ctx, boolean wifiOnly) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest(wifiOnly));
    }

    /**
     * User explicitly asked to send now - override the Wi-Fi-only constraint and retry immediately
     */
    public static void sendNow(Context ctx) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(SubmissionSyncWorker.class)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .build());
    }

    /**
     * Cancels any scheduled sync (e.g. on logout or host change)
     */
    public static void cancelAll(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME);
    }

    private static OneTimeWorkRequest buildRequest(boolean wifiOnly) {
        Constraints constraints = constraintsFor(wifiOnly);

        return new OneTimeWorkRequest.Builder(SubmissionSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * The network constraint for a Wi-Fi-only vs mobile-allowed setting (visible for testing)
     */
    static NetworkType networkTypeFor(boolean wifiOnly) {
        return wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED;
    }

    /**
     * The constraints for a sync given the Wi-Fi-only setting (visible for testing)
     */
    static Constraints constraintsFor(boolean wifiOnly) {
        return new Constraints.Builder()
                .setRequiredNetworkType(networkTypeFor(wifiOnly))
                .build();
    }
}
