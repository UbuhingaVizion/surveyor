package io.rapidpro.surveyor.data;

import android.net.Uri;

import com.nyaruka.goflow.mobile.Event;
import com.nyaruka.goflow.mobile.Modifier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.SurveyorApplication;
import io.rapidpro.surveyor.engine.EngineException;
import io.rapidpro.surveyor.engine.Session;
import io.rapidpro.surveyor.net.TembaException;
import io.rapidpro.surveyor.net.requests.SubmissionPayload;
import io.rapidpro.surveyor.utils.RawJson;
import io.rapidpro.surveyor.utils.SurveyUtils;

public class Submission {

    private static final String SESSION_FILE = "session.json";
    private static final String MODIFIERS_FILE = "modifiers.jsonl";
    private static final String EVENTS_FILE = "events.jsonl";
    private static final String COMPLETION_FILE = ".completed";
    private static final String UPLOAD_STATE_FILE = ".upload.json";
    private static final String MEDIA_DIR = "media";

    private Org org;
    private File directory;

    /**
     * Creates a new submission for the given org in the given directory
     *
     * @param org       the org
     * @param directory the directory
     */
    public Submission(Org org, File directory) {
        this.org = org;
        this.directory = directory;
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
     * Gets the org this submission belongs to
     *
     * @return the org
     */
    public Org getOrg() {
        return org;
    }

    /**
     * Get's the directory this submission is stored in
     *
     * @return the directory
     */
    public File getDirectory() {
        return directory;
    }

    /**
     * Get's the directory this submission's media is stored in
     *
     * @return the directory
     */
    public File getMediaDirectory() throws IOException {
        return SurveyUtils.mkdir(directory, MEDIA_DIR);
    }

    /**
     * Gets whether this submission is complete
     *
     * @return true if complete
     */
    public boolean isCompleted() {
        return new File(directory, COMPLETION_FILE).exists();
    }

    /**
     * Saves the current session
     *
     * @param session the current session
     */
    public void saveSession(Session session) throws IOException, EngineException {
        FileUtils.writeStringToFile(new File(directory, SESSION_FILE), session.toJSON());
    }

    /**
     * Saves new modifiers to this submission
     *
     * @param modifiers the modifiers to save
     */
    public void saveNewModifiers(List<Modifier> modifiers) throws IOException {
        File file = new File(directory, MODIFIERS_FILE);

        BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

        for (Modifier mod : modifiers) {
            writer.write(mod.payload());
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Saves new events to this submission
     *
     * @param events the events to save
     */
    public void saveNewEvents(List<Event> events) throws IOException {
        File file = new File(directory, EVENTS_FILE);

        BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

        for (Event event : events) {
            writer.write(event.payload());
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Saves a new media file to this submission
     *
     * @param data      the media data
     * @param extension the file extension
     * @return the URI of the saved file
     */
    public Uri saveMedia(byte[] data, String extension) throws IOException {
        File file = new File(getMediaDirectory(), UUID.randomUUID().toString() + "." + extension);
        FileUtils.writeByteArrayToFile(file, data);
        return SurveyorApplication.get().getUriForFile(file);
    }

    /**
     * Saves a new media file to this submission
     *
     * @param src the file to copy
     * @return the URI of the saved file
     */
    public Uri saveMedia(File src) throws IOException {
        String extension = FilenameUtils.getExtension(src.getName());
        File file = new File(getMediaDirectory(), UUID.randomUUID().toString() + "." + extension);
        FileUtils.copyFile(src, file);
        return SurveyorApplication.get().getUriForFile(file);
    }

    /**
     * Marks this submission as completed
     */
    public void complete() throws IOException {
        FileUtils.writeStringToFile(new File(directory, COMPLETION_FILE), "");
    }

    /**
     * Deletes this submission from the file system
     */
    public void delete() {
        try {
            FileUtils.deleteDirectory(directory);
            directory = null;
        } catch (IOException e) {
            Logger.e("Unable to delete submission " + directory.getAbsolutePath(), e);
        }
    }

    /**
     * Loads the persisted upload state for this submission (or a fresh one)
     */
    public UploadState getUploadState() throws IOException {
        File file = new File(directory, UPLOAD_STATE_FILE);
        if (!file.exists()) {
            return new UploadState();
        }
        return UploadState.fromJson(FileUtils.readFileToString(file));
    }

    /**
     * Persists the upload state for this submission
     */
    public void saveUploadState(UploadState state) throws IOException {
        FileUtils.writeStringToFile(new File(directory, UPLOAD_STATE_FILE), state.toJson());
    }

    /**
     * Gets whether this submission has already been submitted to the server
     */
    public boolean isSubmitted() throws IOException {
        return getUploadState().isSubmitted();
    }

    /**
     * @deprecated use {@link #uploadAndSubmit()} which is resumable
     */
    @Deprecated
    public void submit() throws IOException, TembaException {
        uploadAndSubmit();
    }

    /**
     * Uploads any media that hasn't yet been uploaded and submits the payload. Safe to retry:
     * media already uploaded is skipped and an already-submitted session is not sent again.
     */
    public void uploadAndSubmit() throws IOException, TembaException {
        Logger.d("Submitting submission " + getUuid() + "...");

        UploadState state = getUploadState();
        if (state.isSubmitted()) {
            delete();
            return;
        }

        // upload any media not already uploaded (resumable - persist after each success)
        if (hasMedia()) {
            SurveyorApplication app = SurveyorApplication.get();
            for (File mediaFile : getMediaDirectory().listFiles()) {
                Uri mediaUri = app.getUriForFile(mediaFile);
                if (state.getUploadedUrl(mediaUri.toString()) == null) {
                    String newUrl = app.getTembaService().uploadMedia(org.getToken(), mediaUri);
                    state.recordMedia(mediaUri.toString(), newUrl);
                    saveUploadState(state);
                    Logger.d("Uploaded media " + mediaUri + " to " + newUrl);
                }
            }
        }

        String session = FileUtils.readFileToString(new File(directory, SESSION_FILE));
        List<String> modifiers = FileUtils.readLines(new File(directory, MODIFIERS_FILE));
        List<String> events = FileUtils.readLines(new File(directory, EVENTS_FILE));

        // convert the recorded media mappings to parallel arrays of strings for replacement
        Map<String, String> mediaUrls = state.getMedia();
        String[] oldUris = mediaUrls.keySet().toArray(new String[0]);
        String[] newUrls = new String[oldUris.length];
        for (int i = 0; i < oldUris.length; i++) {
            newUrls[i] = mediaUrls.get(oldUris[i]);
            Logger.d(oldUris[i] + " --> " + newUrls[i]);
        }

        RawJson sessionJson = new RawJson(StringUtils.replaceEach(session, oldUris, newUrls));
        List<RawJson> modifiersJson = new ArrayList<>(modifiers.size());
        for (String modifier : modifiers) {
            modifiersJson.add(new RawJson(modifier));
        }
        List<RawJson> eventsJson = new ArrayList<>(events.size());
        for (String event : events) {
            eventsJson.add(new RawJson(StringUtils.replaceEach(event, oldUris, newUrls)));
        }

        SubmissionPayload payload = new SubmissionPayload(sessionJson, modifiersJson, eventsJson);

        SurveyorApplication.get().getTembaService().submit(org.getToken(), payload);

        state.setSubmitted(true);
        saveUploadState(state);

        delete();
    }

    private boolean hasMedia() {
        return new File(directory, MEDIA_DIR).exists();
    }
}
