package io.rapidpro.surveyor.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.view.View;
import android.widget.Toast;

import java.util.List;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.data.Submission;
import io.rapidpro.surveyor.work.SyncScheduler;

/**
 * Base for activities that have submissions ((org and flow views)
 */
public abstract class BaseSubmissionsActivity extends BaseActivity {

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
        }

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
