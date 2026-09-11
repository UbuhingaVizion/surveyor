package io.rapidpro.surveyor.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.SurveyorPreferences;
import io.rapidpro.surveyor.activity.BaseActivity;
import io.rapidpro.surveyor.activity.PermissionsActivity;
import io.rapidpro.surveyor.data.Org;
import io.rapidpro.surveyor.utils.HostCheck;
import io.rapidpro.surveyor.utils.LanguageUtils;

/**
 * Fragment to show our settings
 */
public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // make sure we're editing the correct preferences
        getPreferenceManager().setSharedPreferencesName(getSurveyor().getPreferencesName());

        // load the preference screen from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        Preference pref = findPreference(SurveyorPreferences.HOST);
        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (!Patterns.WEB_URL.matcher((String) newValue).matches()) {
                    Toast.makeText(getActivity(), getString(R.string.error_invalid_host), Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        ListPreference language = findPreference(SurveyorPreferences.LANGUAGE);
        if (language != null) {
            populateLanguages(language);
            updateLanguageSummary(language);
        }

        Preference permissions = findPreference("permissions");
        if (permissions != null) {
            permissions.setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getActivity(), PermissionsActivity.class));
                return true;
            });
        }
    }

    /**
     * Populates the flow language list from the languages of the orgs the user has access to
     */
    private void populateLanguages(ListPreference pref) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        Set<String> orgUUIDs = getSurveyor().getPreferences().getStringSet(
                SurveyorPreferences.AUTH_ORGS, Collections.<String>emptySet());

        for (String uuid : orgUUIDs) {
            try {
                Org org = getSurveyor().getOrgService().get(uuid);
                String[] langs = org == null ? null : org.getLanguages();
                if (langs != null) {
                    codes.addAll(Arrays.asList(langs));
                }
            } catch (Exception e) {
                // org not downloaded yet, skip it
            }
        }

        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        entries.add(getString(R.string.language_default));
        values.add("");
        for (String code : codes) {
            entries.add(LanguageUtils.displayName(code));
            values.add(code);
        }

        pref.setEntries(entries.toArray(new CharSequence[0]));
        pref.setEntryValues(values.toArray(new CharSequence[0]));

        // if the saved value isn't in the list (e.g. org changed), fall back to the default
        String current = pref.getValue();
        if (current == null || !values.contains(current)) {
            pref.setValue("");
        }
    }

    private void updateLanguageSummary(ListPreference pref) {
        CharSequence entry = pref.getEntry();
        String summary = getString(R.string.pref_summary_language);
        pref.setSummary(entry == null ? summary : summary + " — " + entry);
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // key is null when all preferences are cleared (e.g. on logout/tests)
        if (SurveyorPreferences.HOST.equals(key)) {
            getSurveyor().onTembaHostChanged();
            checkHostReachable(getSurveyor().getTembaHost());
        } else if (SurveyorPreferences.LANGUAGE.equals(key)) {
            ListPreference language = findPreference(SurveyorPreferences.LANGUAGE);
            if (language != null) {
                updateLanguageSummary(language);
            }
        }
    }

    private SurveyorApplication getSurveyor() {
        return ((BaseActivity) getActivity()).getSurveyor();
    }

    /**
     * Warns the user if the newly configured host can't be reached, so a bad address is caught
     * before enumerators start collecting data.
     */
    private void checkHostReachable(final String host) {
        new Thread(() -> {
            final boolean reachable = HostCheck.reachable(host);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!reachable && isAdded()) {
                        Toast.makeText(getActivity(), getString(R.string.host_unreachable, host), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
}
