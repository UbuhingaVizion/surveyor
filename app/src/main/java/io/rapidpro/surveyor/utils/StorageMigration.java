package io.rapidpro.surveyor.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * One-time migration of user data from external app storage (which can be browsed over USB) into
 * the private internal sandbox (protected by file-based encryption and Linux UID sandboxing).
 */
public final class StorageMigration {

    private StorageMigration() {
    }

    /**
     * Moves the contents of legacyRoot into targetRoot, merging directories and skipping files that
     * already exist in the target (so newer internal data is never clobbered).
     *
     * @param legacyRoot the old directory (may be null or missing)
     * @param targetRoot the new directory
     * @return the number of files moved
     */
    public static int migrate(File legacyRoot, File targetRoot) throws IOException {
        if (legacyRoot == null || !legacyRoot.isDirectory()) {
            return 0;
        }
        return moveRecursive(legacyRoot, targetRoot);
    }

    private static int moveRecursive(File src, File dest) throws IOException {
        int moved = 0;

        if (src.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) {
                throw new IOException("Unable to create directory: " + dest);
            }

            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    moved += moveRecursive(child, new File(dest, child.getName()));
                }
            }
            src.delete();
        } else {
            if (dest.exists()) {
                // already present in the target - drop the legacy copy
                src.delete();
            } else {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Unable to create directory: " + parent);
                }

                // rename works within a filesystem; fall back to copy+delete across filesystems
                if (!src.renameTo(dest)) {
                    FileUtils.copyFile(src, dest);
                    src.delete();
                }
                moved++;
            }
        }

        return moved;
    }
}
