package io.rapidpro.surveyor.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.vdurmont.semver4j.Semver;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.rapidpro.surveyor.BuildConfig;
import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.SurveyorPreferences;
import io.rapidpro.surveyor.adapter.FlowListAdapter;
import io.rapidpro.surveyor.data.Flow;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.engine.Engine;
import io.rapidpro.surveyor.fragment.FlowListFragment;
import io.rapidpro.surveyor.task.RefreshOrgTask;
import io.rapidpro.surveyor.ui.BlockingProgress;
import io.rapidpro.surveyor.utils.OrgColors;

/**
 * Home screen for an org - shows available flows and pending submissions
 */
public class OrgActivity extends BaseSubmissionsActivity implements FlowListFragment.Container {

    private Org org;
    private AlertDialog confirmRefreshDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // load the org before super.onCreate so any restored FlowListFragment can read it
        loadOrg();

        super.onCreate(savedInstanceState);

        if (org == null || isFinishing()) {
            if (org == null) {
                showBugReportDialog();
            }
            finish();
            return;
        }

        // this holds our flow list fragment which shows all available flows
        setContentView(R.layout.activity_org);

        refresh();

        if (savedInstanceState == null) {
            Fragment fragment = new FlowListFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.fragment_container, fragment).commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (confirmRefreshDialog != null) {
            confirmRefreshDialog.dismiss();
        }
    }

    protected void promptToUpgrade() {
        showConfirmDialog(R.string.unsupported_version, new ConfirmationListener() {
            @Override
            public void onConfirm() {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID)));
                } catch (android.content.ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID)));
                }
            }
        });
    }

    /**
     * Loads this activity's org from the intent. Safe to call before super.onCreate().
     */
    private void loadOrg() {
        if (org != null) {
            return;
        }

        String orgUUID = getIntent().getStringExtra(SurveyorIntent.EXTRA_ORG_UUID);
        try {
            org = getSurveyor().getOrgService().get(orgUUID);
        } catch (Exception e) {
            Logger.e("Unable to load org", e);
            org = null;
        }
    }

    protected void refresh() {
        if (org == null) {
            loadOrg();
        }

        if (org == null) {
            showBugReportDialog();
            finish();
            return;
        }

        setTitle(org.getName());
        applyOrgTheme(org);
        updateLastUpdated();

        FlowListAdapter adapter = (FlowListAdapter) getViewCache().getListViewAdapter(android.R.id.list);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // count pending submissions off the main thread (filesystem traversal)
        updatePendingCountAsync(() -> getSurveyor().getSubmissionService().getCompletedCount(getOrg()));

        if (confirmRefreshDialog == null) {
            if (!org.hasAssets()) {
                // if this org doesn't have downloaded assets, ask the user if we can download them now
                confirmRefreshOrg(R.string.confirm_org_download);
            } else {
                for (Flow flow : org.getFlows()) {
                    if (!Engine.isSpecVersionSupported(flow.getSpecVersion())) {
                        Logger.w("Found flow " + flow.getUuid() + " with unsupported version " + flow.getSpecVersion());

                        Semver flowVersion = new Semver(flow.getSpecVersion(), Semver.SemverType.LOOSE);
                        if (flowVersion.isGreaterThan(Engine.currentSpecVersion())) {
                            // if this flow is a major version ahead of us... user needs to upgrade the app
                            promptToUpgrade();
                            break;
                        } else {
                            // if it is a major version behind, they should refresh the assets
                            confirmRefreshOrg(R.string.confirm_org_refresh_old);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the "last updated" indicator with the time this org's assets were last refreshed
     */
    private void updateLastUpdated() {
        TextView view = findViewById(R.id.text_last_updated);
        if (view == null) {
            return;
        }

        String last = org.getLastSynced();
        if (last == null) {
            view.setText(R.string.never_updated);
            return;
        }

        try {
            long ms = Instant.parse(last).toEpochMilli();
            CharSequence relative = DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            view.setText(getString(R.string.last_updated, relative));
        } catch (Exception e) {
            view.setText(R.string.never_updated);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_org, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.action_search_flows));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        filterFlows(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        filterFlows(newText);
                        return true;
                    }
                });
            }
        }

        return true;
    }

    /**
     * Filters the flow list by name
     */
    private void filterFlows(String query) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof FlowListFragment) {
            ((FlowListFragment) fragment).filter(query);
        }
    }

    /**
     * Lets the user pick a custom color for this organization, or reset it to the default
     */
    public void onActionOrgColor(MenuItem item) {
        if (org == null) {
            return;
        }

        final float density = getResources().getDisplayMetrics().density;
        int size = (int) (44 * density);
        int margin = (int) (8 * density);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        int padding = (int) (16 * density);
        grid.setPadding(padding, padding, padding, padding);

        final AlertDialog[] holder = new AlertDialog[1];

        for (final int color : OrgColors.PALETTE) {
            View swatch = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            swatch.setBackground(drawable);

            swatch.setOnClickListener(v -> {
                setOrgColor(toHex(color));
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
            });
            grid.addView(swatch);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_org_color)
                .setView(grid)
                .setNegativeButton(R.string.action_reset_color, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        setOrgColor(null);
                    }
                })
                .setPositiveButton(R.string.action_cancel, null)
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    private void setOrgColor(String hex) {
        org.setColor(hex);
        try {
            org.save();
        } catch (IOException e) {
            Logger.e("Unable to save org color", e);
        }

        applyOrgTheme(org);

        FlowListAdapter adapter = (FlowListAdapter) getViewCache().getListViewAdapter(android.R.id.list);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String toHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /**
     * Lets the user jump straight to another organization without going back to the org chooser
     */
    public void onActionSwitchOrg(MenuItem item) {
        Set<String> orgUUIDs = SurveyorApplication.get().getPreferences()
                .getStringSet(SurveyorPreferences.AUTH_ORGS, Collections.<String>emptySet());

        final List<Org> orgs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (String uuid : orgUUIDs) {
            try {
                Org candidate = getSurveyor().getOrgService().get(uuid);
                if (candidate != null && (org == null || !candidate.getUuid().equals(org.getUuid()))) {
                    orgs.add(candidate);
                    names.add(candidate.getName());
                }
            } catch (Exception e) {
                Logger.e("Unable to load org " + uuid, e);
            }
        }

        if (orgs.isEmpty()) {
            Toast.makeText(this, R.string.error_no_other_orgs, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_switch_org)
                .setItems(names.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(OrgActivity.this, OrgActivity.class);
                        intent.putExtra(SurveyorIntent.EXTRA_ORG_UUID, orgs.get(which).getUuid());
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public void onActionRefresh(MenuItem item) {
        confirmRefreshOrg(R.string.confirm_org_refresh);
    }

    public void confirmRefreshOrg(int msgId) {
        confirmRefreshDialog = showConfirmDialog(msgId, new ConfirmationListener() {
            @Override
            public void onConfirm() {
                doRefresh();
            }
        });
    }

    private void doRefresh() {
        final BlockingProgress progressModal = new BlockingProgress(OrgActivity.this, R.string.one_moment, R.string.refresh_org);
        progressModal.show();

        new RefreshOrgTask(new RefreshOrgTask.Listener() {
            @Override
            public void onProgress(int percent) {
                progressModal.setProgress(percent);
            }

            @Override
            public void onComplete() {
                refresh();

                if (!getOrg().isLastRefreshChanged()) {
                    Toast.makeText(OrgActivity.this, getString(R.string.up_to_date), Toast.LENGTH_SHORT).show();
                }

                progressModal.dismiss();
            }

            @Override
            public void onFailure() {
                progressModal.dismiss();

                Toast.makeText(OrgActivity.this, getString(R.string.error_org_refresh), Toast.LENGTH_SHORT).show();
            }
        }).execute(getOrg());
    }

    @Override
    public Org getOrg() {
        return org;
    }

    /**
     * @see FlowListFragment.Container#getListItems()
     */
    @Override
    public List<Flow> getListItems() {
        return org != null ? org.getFlows() : Collections.emptyList();
    }

    /**
     * @see FlowListFragment.Container#onItemClick(Flow)
     */
    @Override
    public void onItemClick(Flow flow) {
        Intent intent = new Intent(this, FlowActivity.class);
        intent.putExtra(SurveyorIntent.EXTRA_ORG_UUID, getOrg().getUuid());
        intent.putExtra(SurveyorIntent.EXTRA_FLOW_UUID, flow.getUuid());
        startActivity(intent);
    }
}
