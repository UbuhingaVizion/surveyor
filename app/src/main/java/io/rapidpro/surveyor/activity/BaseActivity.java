package io.rapidpro.surveyor.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.greysonparrelli.permiso.Permiso;
import com.greysonparrelli.permiso.PermisoActivity;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.rapidpro.surveyor.BuildConfig;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.SurveyorPreferences;
import io.rapidpro.surveyor.task.FetchOrgsTask;
import io.rapidpro.surveyor.ui.ViewCache;

/**
 * All activities for the SurveyorApplication app extend this base activity which provides convenience methods
 * for things like authentication etc.
 */
public abstract class BaseActivity extends PermisoActivity {

    private ViewCache m_viewCache;

    /**
     * Gets the instance of the application
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
        SurveyorApplication.LOG.d("Logging in as " + email + " with access to orgs " + TextUtils.join(",", orgUUIDs));

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
        SurveyorApplication.LOG.d("Logging out with error " + errorResId);

        getSurveyor().clearPreference(SurveyorPreferences.AUTH_USERNAME);
        getSurveyor().setPreference(SurveyorPreferences.AUTH_ORGS, Collections.<String>emptySet());

        Intent intent = new Intent(this, LoginActivity.class);

        // clear the activity stack
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (errorResId != -1) {
            intent.putExtra(SurveyorIntent.EXTRA_ERROR, getString(errorResId));
        }
        startActivity(intent);
    }

    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    @Override
    protected void onCreate(Bundle bundle) {
        SurveyorApplication.LOG.d(getClass().getSimpleName() + ".onCreate");

        super.onCreate(bundle);

        // make new activity come in from right
        overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);

        // if we're on an activity that requires a logged in user, and we aren't, redirect to login activity
        if (requireLogin() && !isLoggedIn()) {
            logout();
        }
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        } else if (id == R.id.action_bug_report) {
            sendBugReport();
        }
        return super.onOptionsItemSelected(item);
    }

    public void sendBugReport() {

        // Log our build and device details
        StringBuilder info = new StringBuilder();
        info.append("Version: " + BuildConfig.VERSION_NAME + "; " + BuildConfig.VERSION_CODE);
        info.append("\n  OS: " + System.getProperty("os.version") + " (API " + Build.VERSION.SDK_INT + ")");
        info.append("\n  Model: " + android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")");
        SurveyorApplication.LOG.d(info.toString());

        // generate a dump file
        File outputFile = new File(Environment.getExternalStorageDirectory(), "surveyor-debug.txt");
        try {
            Runtime.getRuntime().exec("logcat -d -f " + outputFile.getAbsolutePath() + "  \"*:E SurveyorApplication:*\" ");
        } catch (Throwable t) {
            SurveyorApplication.LOG.e("Failed to generate report", t);
        }

        Uri outputUri = FileProvider.getUriForFile(BaseActivity.this, BuildConfig.APPLICATION_ID + ".provider", outputFile);

        ShareCompat.IntentBuilder.from(this)
                .setType("message/rfc822")
                .addEmailTo("support@rapidpro.io")
                .setSubject("SurveyorApplication Bug Report")
                .setText("Please include what you were doing prior to sending this report and specific details on the error you encountered.")
                .setStream(outputUri)
                .setChooserTitle("Send Email")
                .startChooser();
    }

    public void showSendBugReport() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.confirm_bug_report))
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        sendBugReport();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .show();
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

    public AlertDialog showAlert(int title, int body) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setIcon(android.R.drawable.ic_dialog_alert).create();

        dialog.show();
        return dialog;
    }

    public void showRationaleDialog(int body, Permiso.IOnRationaleProvided callback) {
        Permiso.getInstance().showRationaleInDialog(getString(R.string.title_permissions), getString(body), null, callback);
    }
}
