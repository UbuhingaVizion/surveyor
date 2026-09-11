package io.rapidpro.surveyor.utils;

import android.Manifest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowPermissionsTest {

    private static RawJson flow(String uuid, String nodes) {
        return new RawJson("{\"uuid\":\"" + uuid + "\",\"nodes\":[" + nodes + "]}");
    }

    private static String node(String uuid, String waitHint) {
        return "{\"uuid\":\"" + uuid + "\",\"router\":{\"wait\":{\"hint\":{\"type\":\"" + waitHint + "\"}}}}";
    }

    private static String enterFlowNode(String uuid, String targetUuid) {
        return "{\"uuid\":\"" + uuid + "\",\"actions\":[{\"type\":\"enter_flow\",\"flow\":{\"uuid\":\"" + targetUuid + "\"}}]}";
    }

    @Test
    public void mapsImageAndVideoToCamera() {
        RawJson f = flow("f1", node("n1", "image") + "," + node("n2", "video"));

        Set<String> perms = FlowPermissions.required(Collections.singletonList(f), "f1");

        assertEquals(Collections.singleton(Manifest.permission.CAMERA), perms);
    }

    @Test
    public void mapsAudioToRecordAudio() {
        RawJson f = flow("f1", node("n1", "audio"));

        Set<String> perms = FlowPermissions.required(Collections.singletonList(f), "f1");

        assertEquals(Collections.singleton(Manifest.permission.RECORD_AUDIO), perms);
    }

    @Test
    public void mapsLocationToFineAndCoarse() {
        RawJson f = flow("f1", node("n1", "location"));

        Set<String> perms = FlowPermissions.required(Collections.singletonList(f), "f1");

        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertTrue(perms.contains(Manifest.permission.ACCESS_COARSE_LOCATION));
        assertEquals(2, perms.size());
    }

    @Test
    public void textOnlyFlowHasNoPermissions() {
        RawJson f = flow("f1", node("n1", "digits"));

        Set<String> perms = FlowPermissions.required(Collections.singletonList(f), "f1");

        assertTrue(perms.isEmpty());
    }

    @Test
    public void followsSubFlowsRecursively() {
        RawJson parent = flow("parent", enterFlowNode("n1", "child"));
        RawJson child = flow("child", enterFlowNode("c1", "grandchild"));
        RawJson grandchild = flow("grandchild", node("g1", "location"));

        Set<String> perms = FlowPermissions.required(Arrays.asList(parent, child, grandchild), "parent");

        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        assertFalse(perms.contains(Manifest.permission.CAMERA));
    }

    @Test
    public void ignoresUnknownFlows() {
        RawJson f = flow("f1", node("n1", "image"));

        Set<String> perms = FlowPermissions.required(Collections.singletonList(f), "missing");

        assertTrue(perms.isEmpty());
    }
}
