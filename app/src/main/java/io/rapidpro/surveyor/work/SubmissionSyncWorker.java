package io.rapidpro.surveyor.work;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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

        try {
            List<Org> orgs = app.getOrgService().getAll();

            for (Org org : orgs) {
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

        return retry ? Result.retry() : Result.success();
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
}
