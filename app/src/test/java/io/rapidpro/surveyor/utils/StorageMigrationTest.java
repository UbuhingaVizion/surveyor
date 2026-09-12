package io.rapidpro.surveyor.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StorageMigrationTest {

    private File base;

    @Before
    public void setUp() {
        base = new File(System.getProperty("java.io.tmpdir"), "storage-migration-" + System.nanoTime());
        base.mkdirs();
    }

    @After
    public void tearDown() {
        deleteRecursive(base);
    }

    @Test
    public void movesNestedFilesAndRemovesLegacyTree() throws Exception {
        File legacy = new File(base, "legacy");
        File target = new File(base, "target");

        File media = new File(legacy, "org/flow/sub/media");
        assertTrue(media.mkdirs());
        write(new File(media, "a.jpg"), "image-bytes");

        int moved = StorageMigration.migrate(legacy, target);

        assertEquals(1, moved);
        assertTrue(new File(target, "org/flow/sub/media/a.jpg").exists());
        assertFalse(legacy.exists());
    }

    @Test
    public void keepsExistingTargetFile() throws Exception {
        File legacy = new File(base, "legacy");
        File target = new File(base, "target");

        write(new File(legacy, "a.txt"), "old");
        write(new File(target, "a.txt"), "new");

        StorageMigration.migrate(legacy, target);

        assertEquals("new", read(new File(target, "a.txt")));
    }

    @Test
    public void missingLegacyIsNoop() throws Exception {
        File target = new File(base, "target");

        assertEquals(0, StorageMigration.migrate(new File(base, "does-not-exist"), target));
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
