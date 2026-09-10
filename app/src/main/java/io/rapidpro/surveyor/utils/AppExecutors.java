package io.rapidpro.surveyor.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small app-wide executors: a single background thread for I/O and a main-thread handler for
 * delivering results. Replaces the deprecated AsyncTask.
 */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }

    public static Handler main() {
        return MAIN;
    }

    /**
     * Runs the given runnable on the main thread
     */
    public static void runOnMain(Runnable runnable) {
        MAIN.post(runnable);
    }
}
