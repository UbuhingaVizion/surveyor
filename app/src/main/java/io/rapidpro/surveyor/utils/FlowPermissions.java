package io.rapidpro.surveyor.utils;

import android.Manifest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which runtime permissions a flow needs, by inspecting the wait hints in its definition
 * and following any sub-flows it enters.
 */
public class FlowPermissions {

    private FlowPermissions() {
    }

    /**
     * Gets the runtime permissions required by the given flow (and any flows it calls)
     *
     * @param definitions the raw flow definitions from the org assets
     * @param flowUuid    the UUID of the flow being started
     * @return the set of Android permission names
     */
    public static Set<String> required(List<RawJson> definitions, String flowUuid) {
        Map<String, JsonObject> byUuid = new HashMap<>();
        for (RawJson definition : definitions) {
            JsonObject parsed = JsonUtils.unmarshal(definition.toString(), JsonObject.class);
            if (parsed != null && parsed.get("uuid") != null) {
                byUuid.put(parsed.get("uuid").getAsString(), parsed);
            }
        }

        Set<String> permissions = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(flowUuid);

        while (!queue.isEmpty()) {
            String uuid = queue.poll();
            if (uuid == null || !visited.add(uuid)) {
                continue;
            }

            JsonObject flow = byUuid.get(uuid);
            if (flow == null) {
                continue;
            }

            JsonArray nodes = flow.getAsJsonArray("nodes");
            if (nodes == null) {
                continue;
            }

            for (JsonElement nodeElem : nodes) {
                JsonObject node = nodeElem.getAsJsonObject();
                addHintPermission(permissions, node);
                addSubFlows(queue, node);
            }
        }

        return permissions;
    }

    private static void addHintPermission(Set<String> permissions, JsonObject node) {
        JsonElement routerElem = node.get("router");
        if (routerElem == null || !routerElem.isJsonObject()) {
            return;
        }

        JsonElement waitElem = routerElem.getAsJsonObject().get("wait");
        if (waitElem == null || !waitElem.isJsonObject()) {
            return;
        }

        JsonElement hintElem = waitElem.getAsJsonObject().get("hint");
        if (hintElem == null || !hintElem.isJsonObject()) {
            return;
        }

        JsonElement typeElem = hintElem.getAsJsonObject().get("type");
        if (typeElem == null) {
            return;
        }

        switch (typeElem.getAsString()) {
            case "image":
            case "video":
                permissions.add(Manifest.permission.CAMERA);
                break;
            case "audio":
                permissions.add(Manifest.permission.RECORD_AUDIO);
                break;
            case "location":
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                break;
            default:
                break;
        }
    }

    private static void addSubFlows(Deque<String> queue, JsonObject node) {
        JsonElement actionsElem = node.get("actions");
        if (actionsElem == null || !actionsElem.isJsonArray()) {
            return;
        }

        for (JsonElement actionElem : actionsElem.getAsJsonArray()) {
            JsonObject action = actionElem.getAsJsonObject();
            JsonElement typeElem = action.get("type");
            if (typeElem == null || !"enter_flow".equals(typeElem.getAsString())) {
                continue;
            }

            JsonElement flowElem = action.get("flow");
            if (flowElem == null || !flowElem.isJsonObject()) {
                continue;
            }

            JsonElement uuidElem = flowElem.getAsJsonObject().get("uuid");
            if (uuidElem != null) {
                queue.add(uuidElem.getAsString());
            }
        }
    }
}
