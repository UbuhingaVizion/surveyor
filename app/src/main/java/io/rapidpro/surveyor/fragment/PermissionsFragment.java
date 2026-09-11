package io.rapidpro.surveyor.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import java.util.LinkedHashMap;
import java.util.Map;

import io.rapidpro.surveyor.R;

/**
 * Shows the status of the runtime permissions Surveyor uses, so a supervisor can check a phone
 * before deploying enumerators to the field.
 */
public class PermissionsFragment extends PreferenceFragmentCompat {

    private final Map<String, Integer> labels = new LinkedHashMap<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> refresh());

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        labels.put(Manifest.permission.CAMERA, R.string.permission_label_camera);
        labels.put(Manifest.permission.RECORD_AUDIO, R.string.permission_label_microphone);
        labels.put(Manifest.permission.ACCESS_FINE_LOCATION, R.string.permission_label_location);
        labels.put(Manifest.permission.POST_NOTIFICATIONS, R.string.permission_label_notifications);

        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.pref_header_permissions);
        getPreferenceScreen().addPreference(category);

        for (Map.Entry<String, Integer> entry : labels.entrySet()) {
            final String permission = entry.getKey();
            Preference pref = new Preference(requireContext());
            pref.setKey(permission);
            pref.setTitle(entry.getValue());
            pref.setOnPreferenceClickListener(p -> {
                onPermissionClicked(permission);
                return true;
            });
            category.addPreference(pref);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        for (String permission : labels.keySet()) {
            Preference pref = findPreference(permission);
            if (pref != null) {
                pref.setSummary(isGranted(permission) ? R.string.permission_granted : R.string.permission_denied);
            }
        }
    }

    private void onPermissionClicked(String permission) {
        if (isGranted(permission)) {
            openAppSettings();
        } else {
            permissionLauncher.launch(new String[]{permission});
        }
    }

    private boolean isGranted(String permission) {
        // notifications are only a runtime permission on Android 13+
        if (Manifest.permission.POST_NOTIFICATIONS.equals(permission) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }
}
