package io.rapidpro.surveyor.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AssetDiffTest {

    private static Flow localFlow(String uuid, String modifiedOn) {
        Flow flow = new Flow(uuid, "Flow " + uuid, "13.1.0", 1, 0);
        flow.setModifiedOn(modifiedOn);
        return flow;
    }

    private static Map<String, String> server(Object... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (String) pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void detectsAddedFlows() {
        AssetDiff diff = AssetDiff.compute(server("a", "t1"), Collections.<Flow>emptyList());

        assertEquals(Collections.singleton("a"), diff.added);
        assertTrue(diff.changed.isEmpty());
        assertTrue(diff.removed.isEmpty());
        assertTrue(diff.hasChanges());
        assertEquals(Collections.singleton("a"), diff.uuidsToDownload());
    }

    @Test
    public void detectsNoChanges() {
        AssetDiff diff = AssetDiff.compute(server("a", "t1"), Arrays.asList(localFlow("a", "t1")));

        assertFalse(diff.hasChanges());
        assertTrue(diff.uuidsToDownload().isEmpty());
    }

    @Test
    public void detectsChangedFlows() {
        AssetDiff diff = AssetDiff.compute(server("a", "t2"), Arrays.asList(localFlow("a", "t1")));

        assertEquals(Collections.singleton("a"), diff.changed);
        assertTrue(diff.added.isEmpty());
        assertTrue(diff.removed.isEmpty());
        assertEquals(Collections.singleton("a"), diff.uuidsToDownload());
    }

    @Test
    public void detectsRemovedFlows() {
        AssetDiff diff = AssetDiff.compute(server("a", "t1"),
                Arrays.asList(localFlow("a", "t1"), localFlow("b", "t1")));

        assertEquals(Collections.singleton("b"), diff.removed);
        assertFalse(diff.added.contains("b"));
        assertTrue(diff.changed.isEmpty());
    }

    @Test
    public void missingServerModifiedOnIsTreatedAsChanged() {
        AssetDiff diff = AssetDiff.compute(server("a", null), Arrays.asList(localFlow("a", "t1")));

        assertEquals(Collections.singleton("a"), diff.changed);
    }
}
