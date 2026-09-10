package io.rapidpro.surveyor.activity;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.view.View;
import android.widget.Toast;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.Callable;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.data.Submission;
import io.rapidpro.surveyor.ui.ViewCache;
import io.rapidpro.surveyor.utils.AppExecutors;
import io.rapidpro.surveyor.work.SyncScheduler;

/**
 * Base for activities that have submissions ((org and flow views)
 */
public abstract class BaseSubmissionsActivity extends BaseActivity {

    private boolean observingSync = false;

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
                });
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
                cache.setButtonText(R.id.button_pending, NumberFormat.getInstance().format(pending));
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

        SyncScheduler.sendNow(this);

        Toast.makeText(this, R.string.sending_submissions, Toast.LENGTH_SHORT).show();

        refresh();
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

    protected abstract List<Submission> getPendingSubmissions();

    protected abstract Org getOrg();

    protected abstract void refresh();
}
