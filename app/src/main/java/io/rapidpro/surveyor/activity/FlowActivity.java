package io.rapidpro.surveyor.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.SurveyorPreferences;
import io.rapidpro.surveyor.data.Flow;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.ui.ViewCache;
import io.rapidpro.surveyor.utils.LanguageUtils;

/**
 * Home screen for a flow - shows start button and pending submissions
 */
public class FlowActivity extends BaseSubmissionsActivity {

    private Org org;
    private Flow flow;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_flow);
    }

    @Override
    protected void onResume() {
        super.onResume();

        refresh();
    }

    protected void refresh() {
        String orgUUID = getIntent().getStringExtra(SurveyorIntent.EXTRA_ORG_UUID);
        String flowUUID = getIntent().getStringExtra(SurveyorIntent.EXTRA_FLOW_UUID);

        try {
            org = getSurveyor().getOrgService().get(orgUUID);
            flow = org.getFlow(flowUUID);
        } catch (Exception e) {
            Logger.e("Unable to load org or flow", e);
            showBugReportDialog();
            finish();
            return;
        }

        String questionString = " Questions";
        if (flow.getQuestionCount() == 1) {
            questionString = " Question";
        }

        ViewCache cache = getViewCache();
        NumberFormat nf = NumberFormat.getInstance();
        cache.setText(R.id.text_flow_name, flow.getName());
        cache.setText(R.id.text_flow_questions, nf.format(flow.getQuestionCount()) + questionString);
        cache.setText(R.id.text_flow_revision, "(v" + nf.format(flow.getRevision()) + ")");
        updateLanguageButton();

        applyOrgTheme(org);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(flow.getName());
            getSupportActionBar().setSubtitle(org.getName());
        }

        // count pending submissions off the main thread (filesystem traversal)
        updatePendingCountAsync(() -> getSurveyor().getSubmissionService().getCompletedCount(org, flow));
    }

    public void onActionStart(View view) {
        if (org == null || flow == null) {
            return;
        }

        final Set<String> required = org.getRequiredPermissions(flow.getUuid());
        final String[] permissions = required.toArray(new String[0]);

        if (permissions.length == 0) {
            startRun();
            return;
        }

        // pre-flight: get all the hardware permissions this survey needs before the interview starts
        requestPermissions(permissions, getString(R.string.permission_preflight, buildPermissionList(required)),
                new PermissionCallback() {
                    @Override
                    public void onPermissionsResult(boolean allGranted) {
                        if (allGranted) {
                            startRun();
                        } else if (isPermanentlyDenied(permissions)) {
                            AlertDialog dialog = showPermissionSettingsDialog(R.string.permission_preflight_denied);
                            dialog.setOnDismissListener(d -> startRun());
                        } else {
                            startRun();
                        }
                    }
                });
    }

    private void startRun() {
        Intent intent = new Intent(this, RunActivity.class);
        intent.putExtra(SurveyorIntent.EXTRA_ORG_UUID, org.getUuid());
        intent.putExtra(SurveyorIntent.EXTRA_FLOW_UUID, flow.getUuid());
        intent.putExtra(SurveyorIntent.EXTRA_LANGUAGE, getEffectiveLanguage());
        startActivity(intent);
    }

    /**
     * Builds a human readable list of the permissions a flow needs
     */
    private String buildPermissionList(Set<String> permissions) {
        List<String> names = new ArrayList<>();
        if (permissions.contains(Manifest.permission.CAMERA)) {
            names.add(getString(R.string.permission_label_camera));
        }
        if (permissions.contains(Manifest.permission.RECORD_AUDIO)) {
            names.add(getString(R.string.permission_label_microphone));
        }
        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
            names.add(getString(R.string.permission_label_location));
        }
        return TextUtils.join(", ", names);
    }

    /**
     * Lets the user pick the language this flow runs in, overriding the app-wide setting
     */
    public void onActionLanguage(View view) {
        if (flow == null || org == null) {
            return;
        }

        final List<String> codes = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        codes.add("");
        labels.add(getString(R.string.language_default));

        String[] languages = org.getLanguages();
        if (languages != null) {
            for (String code : languages) {
                codes.add(code);
                labels.add(LanguageUtils.displayName(code));
            }
        }

        int checked = codes.indexOf(getEffectiveLanguage());

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_flow_language)
                .setSingleChoiceItems(labels.toArray(new CharSequence[0]), checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getSurveyor().setPreference(SurveyorPreferences.languageKey(flow.getUuid()), codes.get(which));
                        updateLanguageButton();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * The language this flow will run in: the per-flow choice if set, else the app-wide setting
     */
    private String getEffectiveLanguage() {
        String perFlow = getSurveyor().getPreferences().getString(
                SurveyorPreferences.languageKey(flow.getUuid()), null);
        if (perFlow != null) {
            return perFlow;
        }
        return getSurveyor().getPreferences().getString(SurveyorPreferences.LANGUAGE, "");
    }

    private void updateLanguageButton() {
        String code = getEffectiveLanguage();
        String name = code == null || code.isEmpty() ? getString(R.string.language_default) : LanguageUtils.displayName(code);
        getViewCache().setText(R.id.button_language, getString(R.string.flow_language_format, name));
    }

    @Override
    protected Org getOrg() {
        return org;
    }
}
