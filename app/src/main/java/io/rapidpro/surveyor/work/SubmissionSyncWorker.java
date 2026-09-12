package io.rapidpro.surveyor.work;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.List;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.data.Submission;
import io.rapidpro.surveyor.data.UploadState;

/**
 * Uploads completed submissions in the background, one at a time, resuming interrupted uploads
 * (media already uploaded is skipped) and retrying transient failures with backoff.
 */
public class SubmissionSyncWorker extends Worker {

    /**
     * After this many failed attempts a submission is left alone and surfaced as failed, rather
     * than retrying forever.
     */
    public static final int MAX_ATTEMPTS = 8;

    /**
     * Incomplete (abandoned) drafts older than this are deleted during sync.
     */
    public static final long ABANDONED_DRAFT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Stale camera/video/audio capture files in the cache older than this are deleted.
     */
    public static final long TEMP_MEDIA_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    public SubmissionSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SurveyorApplication app = (SurveyorApplication) getApplicationContext();

        try {
            setForegroundSafely(app);
        } catch (Exception e) {
            Logger.e("Unable to enter foreground for sync", e);
        }

        int sent = 0;
        boolean retry = false;
        boolean failed = false;

        try {
            List<Org> orgs = app.getOrgService().getAll();

            pruneTempMedia(app);

            for (Org org : orgs) {
                // remove abandoned drafts so they don't leak storage on the device
                app.getSubmissionService().pruneAbandonedIncomplete(org, ABANDONED_DRAFT_MAX_AGE_MS);

                for (Submission submission : app.getSubmissionService().getCompleted(org)) {
                    UploadState state = submission.getUploadState();

                    if (state.isSubmitted()) {
                        submission.delete();
                        continue;
                    }

                    try {
                        submission.uploadAndSubmit();
                        sent++;
                    } catch (Exception e) {
                        Logger.e("Unable to submit " + submission.getUuid(), e);

                        state.incrementAttempts();
                        state.setLastError(e.getMessage());
                        try {
                            submission.saveUploadState(state);
                        } catch (Exception ignored) {
                        }

                        if (state.getAttempts() >= MAX_ATTEMPTS) {
                            SyncNotifier.notifyFailed(app, e.getMessage());
                            failed = true;
                        } else {
                            retry = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("Sync failed", e);
            return Result.retry();
        }

        if (sent > 0) {
            SyncNotifier.notifyResult(app, sent);
        }

        if (retry) {
            return Result.retry();
        }
        // surface a hard failure so the UI can tell the user the send didn't work
        return failed ? Result.failure() : Result.success();
    }

    private void setForegroundSafely(Context ctx) {
        Notification notification = SyncNotifier.progress(ctx);
        ForegroundInfo info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info = new ForegroundInfo(SyncNotifier.PROGRESS_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            info = new ForegroundInfo(SyncNotifier.PROGRESS_ID, notification);
        }
        setForegroundAsync(info);
    }

    /**
     * Deletes stale camera/video/audio capture files left in the cache directory
     */
    private void pruneTempMedia(SurveyorApplication app) {
        File cache = app.getExternalCacheDir();
        if (cache == null) {
            return;
        }

        long cutoff = System.currentTimeMillis() - TEMP_MEDIA_MAX_AGE_MS;
        File[] files = cache.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                if (!file.delete()) {
                    Logger.w("Unable to delete stale temp media " + file.getName());
                }
            }
        }
    }
}
