package io.rapidpro.surveyor.task;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.utils.AppExecutors;

/**
 * Refreshes a single org (details and assets), reporting progress on the main thread.
 * Runs on a background executor (replaces the deprecated AsyncTask).
 */
public class RefreshOrgTask {

    private final Listener listener;
    private boolean failed;

    public RefreshOrgTask(Listener listener) {
        this.listener = listener;
    }

    public void execute(final Org org) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    org.refresh(true, new Org.RefreshProgress() {
                        @Override
                        public void reportProgress(final int percent) {
                            AppExecutors.runOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onProgress(percent);
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    Logger.e("Unable to refresh org", e);
                    RefreshOrgTask.this.failed = true;
                }

                AppExecutors.runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        if (failed) {
                            listener.onFailure();
                        } else {
                            listener.onComplete();
                        }
                    }
                });
            }
        });
    }

    public interface Listener {
        void onProgress(int percent);

        void onComplete();

        void onFailure();
    }
}
