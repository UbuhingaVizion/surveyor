package io.rapidpro.surveyor.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes what changed between the flows on the server and the flows we have stored locally,
 * so a refresh only downloads definitions for flows that actually changed.
 */
public class AssetDiff {

    public final Set<String> added = new LinkedHashSet<>();
    public final Set<String> changed = new LinkedHashSet<>();
    public final Set<String> removed = new LinkedHashSet<>();

    private AssetDiff() {
    }

    /**
     * @param server map of flow UUID to its server modified_on (value may be null on old servers)
     * @param local  the flow summaries we currently have stored
     */
    public static AssetDiff compute(Map<String, String> server, List<Flow> local) {
        AssetDiff diff = new AssetDiff();

        Map<String, Flow> localByUuid = new HashMap<>();
        for (Flow flow : local) {
            localByUuid.put(flow.getUuid(), flow);
        }

        for (Map.Entry<String, String> entry : server.entrySet()) {
            String uuid = entry.getKey();
            Flow existing = localByUuid.get(uuid);

            if (existing == null) {
                diff.added.add(uuid);
            } else if (isChanged(entry.getValue(), existing.getModifiedOn())) {
                diff.changed.add(uuid);
            }
        }

        Set<String> serverUuids = new HashSet<>(server.keySet());
        for (Flow flow : local) {
            if (!serverUuids.contains(flow.getUuid())) {
                diff.removed.add(flow.getUuid());
            }
        }

        return diff;
    }

    /**
     * If the server doesn't report modified_on, treat the flow as changed (safe full refresh)
     */
    private static boolean isChanged(String serverModifiedOn, String localModifiedOn) {
        if (serverModifiedOn == null || serverModifiedOn.isEmpty()) {
            return true;
        }
        return !serverModifiedOn.equals(localModifiedOn);
    }

    public boolean hasChanges() {
        return !added.isEmpty() || !changed.isEmpty() || !removed.isEmpty();
    }

    /**
     * UUIDs of flows whose definitions need (re)downloading
     */
    public Set<String> uuidsToDownload() {
        Set<String> uuids = new LinkedHashSet<>(added);
        uuids.addAll(changed);
        return uuids;
    }
}
