package io.rapidpro.surveyor.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.engine.OrgAssets;
import io.rapidpro.surveyor.net.TembaException;
import io.rapidpro.surveyor.net.TembaService;
import io.rapidpro.surveyor.net.responses.Boundary;
import io.rapidpro.surveyor.net.responses.Field;
import io.rapidpro.surveyor.net.responses.Group;
import io.rapidpro.surveyor.utils.JsonUtils;
import io.rapidpro.surveyor.utils.RawJson;

public class Org {
    /**
     * Contains the JSON representation of this org
     */
    private static final String DETAILS_FILE = "details.json";

    /**
     * Contains a goflow assets file with this org's flows, groups, fields etc
     */
    private static final String ASSETS_FILE = "assets.json";

    /**
     * Contains summaries of each flow available in this org
     */
    private static final String FLOWS_FILE = "flows.json";

    /**
     * How long admin boundaries are considered fresh (they rarely change)
     */
    private static final long BOUNDARIES_TTL_DAYS = 2;

    private String token;

    private String name;

    @SerializedName("primary_language")
    private String primaryLanguage;

    private String[] languages;

    private String timezone;

    private String country;

    @SerializedName("date_style")
    private String dateStyle;

    private boolean anon;

    private String legacySubmissionsDirectory;

    @SerializedName("last_synced")
    private String lastSynced;

    @SerializedName("last_boundaries_synced")
    private String lastBoundariesSynced;

    private transient File directory;

    private transient List<Flow> flows;

    private transient boolean lastRefreshChanged;

    /**
     * Creates an new empty org
     *
     * @param directory the directory
     * @param token     the API token
     * @return the org
     */
    public static Org create(File directory, String name, String token) throws IOException {
        directory.mkdirs();

        Org org = new Org();
        org.name = name;
        org.token = token;
        org.directory = directory;
        org.flows = new ArrayList<>();
        org.legacySubmissionsDirectory = null;

        // store the token encrypted (not in details.json)
        TokenStore.put(SurveyorApplication.get(), directory.getName(), token);

        org.save();
        FileUtils.writeStringToFile(new File(directory, FLOWS_FILE), "[]");
        return org;
    }

    /**
     * Loads an org from a directory
     *
     * @param directory the directory
     * @return the org
     */
    static Org load(File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException(directory.getPath() + " is not a valid org directory");
        }

        // read details.json
        String detailsJSON = FileUtils.readFileToString(new File(directory, DETAILS_FILE));
        Org org = JsonUtils.unmarshal(detailsJSON, Org.class);
        org.directory = directory;

        // load the token from encrypted storage, migrating a legacy plaintext token if present
        String uuid = directory.getName();
        String storedToken = TokenStore.get(SurveyorApplication.get(), uuid);
        if (storedToken != null) {
            org.token = storedToken;
        } else if (org.token != null) {
            TokenStore.put(SurveyorApplication.get(), uuid, org.token);
            org.save();
        }

        // read flows.json
        String flowsJson = FileUtils.readFileToString(new File(directory, FLOWS_FILE));

        TypeToken type = new TypeToken<List<Flow>>() {
        };
        org.flows = JsonUtils.unmarshal(flowsJson, type);
        return org;
    }

    /**
     * Gets the UUID of this org (i.e. the name of its directory)
     *
     * @return the UUID
     */
    public String getUuid() {
        return directory.getName();
    }

    /**
     * Gets the directory of this org
     *
     * @return the directory
     */
    public File getDirectory() {
        return directory;
    }

    /**
     * Gets the API token for this org
     *
     * @return the API token
     */
    public String getToken() {
        return token;
    }

    /**
     * Updates the API token for this org (does not persist - see {@link #save()} and TokenStore)
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Gets the name of this org
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the country code of this org
     *
     * @return the country code
     */
    public String getCountry() {
        return country;
    }

    public String[] getLanguages() {
        return languages;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getDateStyle() {
        return dateStyle;
    }

    public boolean isAnon() {
        return anon;
    }

    /**
     * Gets the directory of legacy submissions for this org (may be null)
     *
     * @return the directory
     */
    public String getLegacySubmissionsDirectory() {
        return legacySubmissionsDirectory;
    }

    public void setLegacySubmissionsDirectory(String legacySubmissionsDirectory) {
        this.legacySubmissionsDirectory = legacySubmissionsDirectory;
    }

    /**
     * When this org's assets were last refreshed (ISO instant, may be null)
     */
    public String getLastSynced() {
        return lastSynced;
    }

    /**
     * When this org's admin boundaries were last refreshed (ISO instant, may be null)
     */
    public String getLastBoundariesSynced() {
        return lastBoundariesSynced;
    }

    /**
     * Whether the most recent refresh changed anything
     */
    public boolean isLastRefreshChanged() {
        return lastRefreshChanged;
    }

    public List<Flow> getFlows() {
        return flows;
    }

    /**
     * Gets the flow with the given UUID
     *
     * @param uuid the flow UUID
     * @return the flow or null if no such flow exists
     */
    public Flow getFlow(String uuid) {
        for (Flow flow : flows) {
            if (flow.getUuid().equals(uuid)) {
                return flow;
            }
        }
        return null;
    }

    /**
     * Gets whether this org has downloaded assets
     *
     * @return true if org has assets
     */
    public boolean hasAssets() {
        return new File(directory, ASSETS_FILE).exists();
    }

    /**
     * Gets this org's downloaded assets
     *
     * @return the assets JSON
     */
    public String getAssets() throws IOException {
        return FileUtils.readFileToString(new File(directory, ASSETS_FILE));
    }

    /**
     * Refreshes this org from RapidPro
     */
    public void refresh(boolean includeAssets, RefreshProgress progress) throws TembaException, IOException {
        TembaService svc = SurveyorApplication.get().getTembaService();
        io.rapidpro.surveyor.net.responses.Org apiOrg = svc.getOrg(this.token);

        this.name = apiOrg.getName();
        this.languages = apiOrg.getLanguages();
        this.timezone = apiOrg.getTimezone();
        this.country = apiOrg.getCountry();
        this.dateStyle = apiOrg.getDateStyle();
        this.anon = apiOrg.isAnon();
        this.save();

        if (progress != null) {
            progress.reportProgress(10);
        }

        if (includeAssets) {
            refreshAssets(progress);
        }
    }

    public void save() throws IOException {
        // (re)write org fields to details.json, never persisting the API token in plaintext
        String detailsJSON = JsonUtils.marshal(this);
        JsonObject obj = new JsonParser().parse(detailsJSON).getAsJsonObject();
        obj.remove("token");
        FileUtils.writeStringToFile(new File(directory, DETAILS_FILE), obj.toString());
    }

    private void refreshAssets(RefreshProgress progress) throws TembaException, IOException {
        TembaService svc = SurveyorApplication.get().getTembaService();

        List<Field> fields = svc.getFields(getToken());

        if (progress != null) {
            progress.reportProgress(20);
        }

        List<Group> groups = svc.getGroups(getToken());

        if (progress != null) {
            progress.reportProgress(30);
        }

        List<io.rapidpro.surveyor.net.responses.Flow> serverFlows = svc.getFlows(getToken());

        Map<String, String> serverModified = new HashMap<>();
        for (io.rapidpro.surveyor.net.responses.Flow flow : serverFlows) {
            serverModified.put(flow.getUuid(), flow.getModifiedOn());
        }

        if (progress != null) {
            progress.reportProgress(40);
        }

        // work out what actually changed so we only download changed flow definitions
        AssetDiff diff = AssetDiff.compute(serverModified, this.flows);

        OrgAssets existing = hasAssets() ? OrgAssets.fromJson(getAssets()) : null;

        List<RawJson> updated = new ArrayList<>();
        if (!diff.uuidsToDownload().isEmpty()) {
            updated = svc.getDefinitionsForUuids(getToken(), new ArrayList<>(diff.uuidsToDownload()));
        }

        if (progress != null) {
            progress.reportProgress(60);
        }

        // keep unchanged definitions, replace changed/new, drop removed
        List<RawJson> merged = mergeFlowDefinitions(existing, updated, diff.removed);

        // boundaries rarely change - only re-fetch when missing or stale
        boolean boundariesDue = existing == null || boundariesDue();
        List<Boundary> boundaries = null;
        if (boundariesDue) {
            boundaries = svc.getBoundaries(getToken());
            lastBoundariesSynced = Instant.now().toString();
        }

        if (progress != null) {
            progress.reportProgress(70);
        }

        OrgAssets assets;
        if (boundaries != null) {
            assets = OrgAssets.fromTemba(fields, groups, boundaries, merged);
        } else {
            assets = OrgAssets.fromTembaReusingLocations(fields, groups, existing.getLocations(), merged);
        }

        FileUtils.writeStringToFile(new File(directory, ASSETS_FILE), assets.toJson());

        if (progress != null) {
            progress.reportProgress(80);
        }

        // rebuild the local flow summaries, recording modified_on for future diffing
        List<Flow> summaries = assets.getFlows();
        for (Flow summary : summaries) {
            summary.setModifiedOn(serverModified.get(summary.getUuid()));
        }

        this.flows.clear();
        this.flows.addAll(summaries);
        FileUtils.writeStringToFile(new File(directory, FLOWS_FILE), JsonUtils.marshal(this.flows));

        lastSynced = Instant.now().toString();
        lastRefreshChanged = diff.hasChanges() || boundariesDue;
        save();

        if (progress != null) {
            progress.reportProgress(100);
        }

        Logger.d("Refreshed assets for org " + getUuid() + " (added=" + diff.added.size() + ", changed=" + diff.changed.size() + ", removed=" + diff.removed.size() + ", fields=" + fields.size() + ", groups=" + groups.size() + ")");
    }

    /**
     * Merges existing flow definitions with updated ones, dropping removed flows
     */
    private List<RawJson> mergeFlowDefinitions(OrgAssets existing, List<RawJson> updated, Set<String> removed) {
        Map<String, RawJson> byUuid = new LinkedHashMap<>();

        if (existing != null) {
            for (RawJson definition : existing.getFlowDefinitions()) {
                String uuid = flowUuid(definition);
                if (uuid != null && !removed.contains(uuid)) {
                    byUuid.put(uuid, definition);
                }
            }
        }

        for (RawJson definition : updated) {
            String uuid = flowUuid(definition);
            if (uuid != null) {
                byUuid.put(uuid, definition);
            }
        }

        return new ArrayList<>(byUuid.values());
    }

    private String flowUuid(RawJson definition) {
        try {
            return Flow.extract(definition).getUuid();
        } catch (Exception e) {
            Logger.e("Unable to read flow definition uuid", e);
            return null;
        }
    }

    private boolean boundariesDue() {
        if (lastBoundariesSynced == null) {
            return true;
        }
        try {
            return Instant.parse(lastBoundariesSynced).plus(BOUNDARIES_TTL_DAYS, ChronoUnit.DAYS).isBefore(Instant.now());
        } catch (Exception e) {
            return true;
        }
    }

    public interface RefreshProgress {
        void reportProgress(int percent);
    }
}
