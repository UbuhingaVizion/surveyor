package io.rapidpro.surveyor.activity;

import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Collections;

import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.SurveyorPreferences;

/**
 * Fast (Robolectric) regression test for the OrgActivity recreation crash.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OrgActivityRecreationUnitTest {

    private static final String ORG_UUID = "test-org-uuid";

    private SurveyorApplication app;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        app = (SurveyorApplication) context;
        app.getOrgService().clearCache();

        File directory = new File(app.getOrgsDirectory(), ORG_UUID);
        directory.mkdirs();
        FileUtils.writeStringToFile(new File(directory, "details.json"), "{\"name\":\"Test Org\"}");
        FileUtils.writeStringToFile(new File(directory, "flows.json"), "[]");

        app.setPreference(SurveyorPreferences.AUTH_USERNAME, "bob@example.com");
        app.setPreference(SurveyorPreferences.AUTH_ORGS, Collections.singleton(ORG_UUID));
    }

    @Test
    public void survivesRecreation() {
        Intent intent = new Intent(app, OrgActivity.class);
        intent.putExtra(SurveyorIntent.EXTRA_ORG_UUID, ORG_UUID);

        try (ActivityScenario<OrgActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.recreate();

            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
