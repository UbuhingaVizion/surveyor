package io.rapidpro.surveyor.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.view.View;
import android.widget.Toast;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.concurrent.Callable;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.ui.ViewCache;
import io.rapidpro.surveyor.utils.AppExecutors;
import io.rapidpro.surveyor.utils.HostCheck;
import io.rapidpro.surveyor.work.SyncScheduler;

/**
 * Base for activities that have submissions ((org and flow views)
 */
public abstract class BaseSubmissionsActivity extends BaseActivity {

    private boolean observingSync = false;

    private boolean awaitingSendResult = false;

    @Override
    protected void onStart() {
        super.onStart();
        observeSync();
    }

    /**
     * Observes background sync so the pending UI updates as submissions are sent
     */
    private void observeSync() {
        if (observingSync) {
            return;
        }
        observingSync = true;

        try {
            WorkManager.getInstance(this)
                    .getWorkInfosForUniqueWorkLiveData(SyncScheduler.WORK_NAME)
                    .observe(this, infos -> {
                        if (infos == null || infos.isEmpty()) {
                            return;
                        }

                        WorkInfo.State state = infos.get(0).getState();
                        setSyncing(state == WorkInfo.State.RUNNING);

                        if (state == WorkInfo.State.SUCCEEDED
                                || state == WorkInfo.State.FAILED
                                || state == WorkInfo.State.CANCELLED) {
                            refresh();
                        }

                        // if the user explicitly tapped send, tell them how it went
                        if (awaitingSendResult) {
                            if (state == WorkInfo.State.SUCCEEDED) {
                                awaitingSendResult = false;
                                Toast.makeText(BaseSubmissionsActivity.this,
                                        R.string.submissions_sent_toast, Toast.LENGTH_SHORT).show();
                            } else if (state == WorkInfo.State.FAILED) {
                                awaitingSendResult = false;
                                showSendFailureAsync();
                            }
                        }
                    });
        } catch (Exception e) {
            Logger.e("Unable to observe background sync", e);
        }
    }

    private void setSyncing(boolean syncing) {
        View button = findViewById(R.id.button_pending);
        if (button != null) {
            button.setEnabled(!syncing);
        }
    }

    /**
     * Updates the pending submissions UI from a background thread (avoids main-thread disk I/O)
     */
    protected void updatePendingCountAsync(final Callable<Integer> counter) {
        AppExecutors.io().execute(() -> {
            final int pending;
            try {
                pending = counter.call();
            } catch (Exception e) {
                Logger.e("Unable to count pending submissions", e);
                return;
            }

            AppExecutors.runOnMain(() -> {
                ViewCache cache = getViewCache();
                cache.setVisible(R.id.container_pending, pending > 0);
                cache.setButtonText(R.id.button_pending, getString(R.string.action_send_now_count, pending));
            });
        });
    }

    /**
     * User has clicked a submit button
     *
     * @param view the button
     */
    public void onActionSubmit(View view) {
        showConfirmDialog(R.string.confirm_send_submissions, new ConfirmationListener() {
            @Override
            public void onConfirm() {
                // if set to Wi-Fi only but we aren't on an unmetered network, confirm mobile data
                if (getSurveyor().isSendOverWifiOnly() && !isOnUnmeteredNetwork()) {
                    showConfirmDialog(R.string.confirm_send_over_mobile, new ConfirmationListener() {
                        @Override
                        public void onConfirm() {
                            doSendNow();
                        }
                    });
                } else {
                    doSendNow();
                }
            }
        });
    }

    private void doSendNow() {
        // Android 13+ needs runtime permission to show sync notifications
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, new PermissionCallback() {
            @Override
            public void onPermissionsResult(boolean allGranted) {
                // whether or not notifications are granted, the send can proceed
            }
        });

        final String host = getSurveyor().getTembaHost();

        // quick reachability check so a bad host fails immediately instead of silently retrying
        AppExecutors.io().execute(() -> {
            final boolean reachable = HostCheck.reachable(host);
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!reachable) {
                    new AlertDialog.Builder(BaseSubmissionsActivity.this)
                            .setTitle(R.string.sync_failed)
                            .setMessage(getString(R.string.host_unreachable, host))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }

                awaitingSendResult = true;
                SyncScheduler.sendNow(BaseSubmissionsActivity.this);
                Toast.makeText(BaseSubmissionsActivity.this, R.string.sending_submissions, Toast.LENGTH_SHORT).show();
                refresh();
            });
        });
    }

    /**
     * Looks up the last upload error off the main thread and shows it in a dialog
     */
    private void showSendFailureAsync() {
        AppExecutors.io().execute(() -> {
            final String error;
            try {
                Org org = getOrg();
                error = org == null ? null : getSurveyor().getSubmissionService().getLastError(org);
            } catch (Exception e) {
                Logger.e("Unable to read last upload error", e);
                return;
            }

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                new AlertDialog.Builder(BaseSubmissionsActivity.this)
                        .setTitle(R.string.sync_failed)
                        .setMessage(error != null ? error : getString(R.string.error_submissions_send))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    /**
     * Gets whether the active network is unmetered (e.g. Wi-Fi)
     */
    private boolean isOnUnmeteredNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    protected abstract Org getOrg();

    protected abstract void refresh();
}
