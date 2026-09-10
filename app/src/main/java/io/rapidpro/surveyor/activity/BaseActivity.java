package io.rapidpro.surveyor.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import io.rapidpro.surveyor.BuildConfig;
import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.SurveyorPreferences;
import io.rapidpro.surveyor.ui.ViewCache;

/**
 * All activities for the SurveyorApplication app extend this base activity which provides convenience methods
 * for things like authentication etc.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private ViewCache m_viewCache;

    /**
     * Callback for permission requests
     */
    public interface PermissionCallback {
        void onPermissionsResult(boolean allGranted);
    }

    private PermissionCallback pendingPermissionCallback;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                PermissionCallback callback = pendingPermissionCallback;
                pendingPermissionCallback = null;
                if (callback != null) {
                    callback.onPermissionsResult(allGranted);
                }
            });

    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    @Override
    protected void onCreate(Bundle bundle) {
        Logger.d("Creating " + getClass().getSimpleName());

        // so that espresso tests always have an unlocked screen
        if (BuildConfig.DEBUG) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        super.onCreate(bundle);

        // make new activity come in from right
        overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);

        applyEdgeToEdgeInsets();

        // if we're on an activity that requires a logged in user, and we aren't, redirect to login activity
        if (requireLogin() && !isLoggedIn()) {
            logout();
        }
    }

    /**
     * Keeps content clear of the system bars. On Android 15/16 edge-to-edge is enforced and the
     * manifest opt-out is ignored, so we apply the system-bar insets as padding ourselves.
     */
    private void applyEdgeToEdgeInsets() {
        final View content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }

    /**
     * @see android.app.Activity#onCreateOptionsMenu(Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        // show the settings menu in debug mode
        if (BuildConfig.DEBUG) {
            MenuItem menuItem = menu.findItem(R.id.action_settings);
            if (menuItem != null) {
                menuItem.setVisible(true);
            }
        }

        // show logout action if we're logged in
        if (isLoggedIn()) {
            MenuItem menuItem = menu.findItem(R.id.action_logout);
            if (menuItem != null) {
                menuItem.setVisible(true);
            }
        }

        return true;
    }

    /**
     * User clicked "Settings" menu option
     *
     * @param item the menu item
     */
    public void onActionSettings(MenuItem item) {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    /**
     * User clicked "Logout" menu option
     *
     * @param item the menu item
     */
    public void onActionLogout(MenuItem item) {
        if (getSurveyor().getSubmissionService().hasSubmissions()) {
            showConfirmDialog(R.string.confirm_logout_with_submissions, new ConfirmationListener() {
                @Override
                public void onConfirm() {
                    logout();
                }
            });
        } else {
            logout();
        }
    }

    /**
     * User clicked "Privacy" menu option
     *
     * @param item the menu item
     */
    public void onActionPrivacy(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_url))));
    }

    /**
     * User clicked "Bug Report" menu option
     *
     * @param item the menu item
     */
    public void onActionBugReport(MenuItem item) {
        sendBugReport();
    }

    /**
     * Gets the instance of the application
     *
     * @return the application
     */
    public SurveyorApplication getSurveyor() {
        return (SurveyorApplication) getApplication();
    }

    /**
     * Whether this activity requires the user to be logged in
     *
     * @return true if activity requires login
     */
    public boolean requireLogin() {
        return true;
    }

    /**
     * Logs in a user for the given orgs
     */
    public void login(String email, Set<String> orgUUIDs) {
        Logger.d("Logging in as " + email + " with access to orgs " + TextUtils.join(",", orgUUIDs));

        // save email which we'll need for submissions later
        getSurveyor().setPreference(SurveyorPreferences.AUTH_USERNAME, email);
        getSurveyor().setPreference(SurveyorPreferences.PREV_USERNAME, email);
        getSurveyor().setPreference(SurveyorPreferences.AUTH_ORGS, orgUUIDs);

        // let the user pick an org...
        startActivity(new Intent(this, OrgChooseActivity.class));

        // we don't want to go back to the view that sent us here (i.e. login or create account)
        finish();
    }

    /**
     * Logs the user out and returns them to the login page
     */
    protected void logout() {
        logout(-1);
    }

    /**
     * Logs the user out and returns them to the login page showing the given error string
     */
    protected void logout(int errorResId) {
        Logger.d("Logging out with error " + errorResId);

        getSurveyor().clearPreference(SurveyorPreferences.AUTH_USERNAME);
        getSurveyor().setPreference(SurveyorPreferences.AUTH_ORGS, Collections.<String>emptySet());

        try {
            getSurveyor().getSubmissionService().clearAll();
        } catch (IOException e) {
            Logger.e("Unable to clear submissions", e);
        }

        // stop any scheduled background sync
        io.rapidpro.surveyor.work.SyncScheduler.cancelAll(this);

        Intent intent = new Intent(this, LoginActivity.class);

        // clear the activity stack
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (errorResId != -1) {
            intent.putExtra(SurveyorIntent.EXTRA_ERROR, getString(errorResId));
        }
        startActivity(intent);
    }

    public void showBugReportDialog() {
        showConfirmDialog(R.string.confirm_bug_report, new ConfirmationListener() {
            @Override
            public void onConfirm() {
                sendBugReport();
            }
        });
    }

    private void sendBugReport() {
        try {
            Uri outputUri = getSurveyor().generateLogDump();

            ShareCompat.IntentBuilder.from(this)
                    .setType("message/rfc822")
                    .addEmailTo(getString(R.string.support_email))
                    .setSubject("Surveyor Bug Report")
                    .setText("Please include what you were doing prior to sending this report and specific details on the error you encountered.")
                    .setStream(outputUri)
                    .setChooserTitle("Send Email")
                    .startChooser();

        } catch (IOException e) {
            Logger.e("Failed to generate bug report", e);
        }
    }

    public ViewCache getViewCache() {
        if (m_viewCache == null) {
            m_viewCache = new ViewCache(this, findViewById(android.R.id.content));
        }
        return m_viewCache;
    }

    /**
     * Gets the currently authenticated username
     *
     * @return the username/email
     */
    protected String getUsername() {
        return getPreferences().getString(SurveyorPreferences.AUTH_USERNAME, null);
    }

    /**
     * Checks whether we are currently authenticated
     *
     * @return truer if we are authenticated
     */
    protected boolean isLoggedIn() {
        return !TextUtils.isEmpty(getUsername());
    }

    /**
     * Gets the preferences for this application
     *
     * @return the preferences
     */
    public SharedPreferences getPreferences() {
        return getSurveyor().getPreferences();
    }

    protected void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    protected interface ConfirmationListener {
        void onConfirm();
    }

    protected AlertDialog showConfirmDialog(int msgResId, final ConfirmationListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        return builder.setMessage(msgResId)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog1, int id) {
                        listener.onConfirm();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog1, int id) {
                        dialog1.cancel();
                    }
                }).show();
    }

    /**
     * Requests the given permissions, showing a rationale dialog first if appropriate
     */
    protected void requestPermissions(String[] permissions, int rationaleResId, PermissionCallback callback) {
        if (hasAllPermissions(permissions)) {
            callback.onPermissionsResult(true);
            return;
        }

        pendingPermissionCallback = callback;

        if (shouldShowRationale(permissions)) {
            showConfirmDialog(rationaleResId, new ConfirmationListener() {
                @Override
                public void onConfirm() {
                    permissionLauncher.launch(permissions);
                }
            });
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    /**
     * Requests the given permissions without a rationale
     */
    protected void requestPermissions(String[] permissions, PermissionCallback callback) {
        if (hasAllPermissions(permissions)) {
            callback.onPermissionsResult(true);
            return;
        }

        pendingPermissionCallback = callback;
        permissionLauncher.launch(permissions);
    }

    private boolean hasAllPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldShowRationale(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }
}
