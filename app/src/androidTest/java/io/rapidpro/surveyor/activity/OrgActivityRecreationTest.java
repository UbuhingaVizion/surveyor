package io.rapidpro.surveyor.activity;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.Collections;

import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.SurveyorIntent;
import io.rapidpro.surveyor.test.BaseApplicationTest;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * Regression test for the recreation crash where a restored FlowListFragment read the org before
 * OrgActivity had loaded it (NPE in getListItems).
 */
public class OrgActivityRecreationTest extends BaseApplicationTest {

    private static final String ORG_UUID = "b2ad9e4d-71f1-4d54-8dd6-f7a94b685d06";

    @Test
    public void survivesRecreation() throws Exception {
        installOrg(ORG_UUID, io.rapidpro.surveyor.test.R.raw.org1_details, io.rapidpro.surveyor.test.R.raw.org1_flows, io.rapidpro.surveyor.test.R.raw.org1_assets);
        login("bob@nyaruka.com", Collections.singleton(ORG_UUID));

        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), OrgActivity.class);
        intent.putExtra(SurveyorIntent.EXTRA_ORG_UUID, ORG_UUID);

        try (ActivityScenario<OrgActivity> scenario = ActivityScenario.launch(intent)) {
            // recreating restores the FlowListFragment before OrgActivity.onCreate returns
            scenario.recreate();

            onView(withId(R.id.fragment_container)).check(matches(isDisplayed()));
        }
    }
}
