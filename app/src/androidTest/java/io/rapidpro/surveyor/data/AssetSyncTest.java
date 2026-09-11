package io.rapidpro.surveyor.data;

import org.junit.Test;

import java.io.IOException;

import io.rapidpro.surveyor.net.TembaException;
import io.rapidpro.surveyor.test.BaseApplicationTest;
import okhttp3.mockwebserver.RecordedRequest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

/**
 * Verifies incremental asset sync: a refresh with no server-side changes must not download
 * flow definitions or admin boundaries again.
 */
public class AssetSyncTest extends BaseApplicationTest {

    private static final String ORG_UUID = "b2ad9e4d-71f1-4d54-8dd6-f7a94b685d06";

    @Test
    public void onlyDownloadsChangedAssets() throws IOException, TembaException, InterruptedException {
        installOrg(ORG_UUID, io.rapidpro.surveyor.test.R.raw.org1_details, io.rapidpro.surveyor.test.R.raw.org1_flows, io.rapidpro.surveyor.test.R.raw.org1_assets);
        Org org = getSurveyor().getOrgService().get(ORG_UUID);

        // ---- first refresh: nothing stored yet, so definitions + boundaries are fetched
        enqueueFullRefresh();
        org.refresh(true, null);

        assertThat(nextPath(), is("/api/v2/org.json"));
        assertThat(nextPath(), is("/api/v2/fields.json"));
        assertThat(nextPath(), is("/api/v2/fields.json?cursor=123456789"));
        assertThat(nextPath(), is("/api/v2/groups.json"));
        assertThat(nextPath(), startsWith("/api/v2/flows.json"));
        assertThat(nextPath(), startsWith("/api/v2/definitions.json"));
        assertThat(nextPath(), is("/api/v2/boundaries.json"));
        assertThat(org.isLastRefreshChanged(), is(true));

        int afterFirst = mockServer.getRequestCount();

        // ---- second refresh: no server changes -> no definitions and no boundaries
        enqueueIncrementalRefresh();
        org.refresh(true, null);

        assertThat(nextPath(), is("/api/v2/org.json"));
        assertThat(nextPath(), is("/api/v2/fields.json"));
        assertThat(nextPath(), is("/api/v2/fields.json?cursor=123456789"));
        assertThat(nextPath(), is("/api/v2/groups.json"));
        assertThat(nextPath(), startsWith("/api/v2/flows.json"));

        assertThat(org.isLastRefreshChanged(), is(false));
        assertThat(mockServer.getRequestCount() - afterFirst, is(5));
    }

    private void enqueueFullRefresh() throws IOException {
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_org_get, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_fields_get_page_1, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_fields_get_page_2, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_groups_get, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_flows_get, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_definitions_get_v13, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_boundaries_get, "application/json", 200);
    }

    private void enqueueIncrementalRefresh() throws IOException {
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_org_get, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_fields_get_page_1, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_fields_get_page_2, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_groups_get, "application/json", 200);
        mockServerResponse(io.rapidpro.surveyor.test.R.raw.api_v2_flows_get, "application/json", 200);
    }

    private String nextPath() throws InterruptedException {
        RecordedRequest request = mockServer.takeRequest();
        return request.getPath();
    }
}
