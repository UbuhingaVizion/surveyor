package io.rapidpro.surveyor.activity;

import android.os.Bundle;

import androidx.fragment.app.FragmentTransaction;

import io.rapidpro.surveyor.fragment.SettingsFragment;


/**
 * Activity for modifying app settings
 */
public class SettingsActivity extends BaseActivity {

    public boolean requireLogin() {
        return false;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // the fragment is restored automatically on recreation - only add it on first create
        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, new SettingsFragment()).commit();
        }
    }
}
