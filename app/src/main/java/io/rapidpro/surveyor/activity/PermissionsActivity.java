package io.rapidpro.surveyor.activity;

import android.os.Bundle;

import androidx.fragment.app.FragmentTransaction;

import io.rapidpro.surveyor.fragment.PermissionsFragment;

/**
 * Screen showing the status of the runtime permissions Surveyor needs
 */
public class PermissionsActivity extends BaseActivity {

    public boolean requireLogin() {
        return false;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, new PermissionsFragment()).commit();
        }
    }
}
