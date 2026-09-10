package io.rapidpro.surveyor.data;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import io.rapidpro.surveyor.SurveyorApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OrgServiceTokenTest {

    private static final String UUID = "b2ad9e4d-71f1-4d54-8dd6-f7a94b685d06";

    private Context context;
    private OrgService service;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        service = ((SurveyorApplication) context).getOrgService();
        service.clearCache();

        File directory = new File(context.getFilesDir(), "orgs/" + UUID);
        directory.mkdirs();
        FileUtils.writeStringToFile(new File(directory, "details.json"), "{\"name\":\"Test Org\"}");
        FileUtils.writeStringToFile(new File(directory, "flows.json"), "[]");

        TokenStore.put(context, UUID, "old-token");
    }

    @Test
    public void updatesRotatedTokenOnReAuth() throws Exception {
        Org org = service.getOrFetch(UUID, "Test Org", "new-token");

        assertEquals("new-token", org.getToken());
        assertEquals("new-token", TokenStore.get(context, UUID));
    }
}
