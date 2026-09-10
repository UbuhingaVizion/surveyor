package io.rapidpro.surveyor.work;

import androidx.work.NetworkType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncSchedulerTest {

    @Test
    public void wifiOnlyMapsToUnmetered() {
        assertEquals(NetworkType.UNMETERED, SyncScheduler.networkTypeFor(true));
    }

    @Test
    public void mobileAllowedMapsToConnected() {
        assertEquals(NetworkType.CONNECTED, SyncScheduler.networkTypeFor(false));
    }

    @Test
    public void wifiOnlyConstraintsRequireUnmetered() {
        assertEquals(NetworkType.UNMETERED, SyncScheduler.constraintsFor(true).getRequiredNetworkType());
    }

    @Test
    public void mobileConstraintsRequireConnected() {
        assertEquals(NetworkType.CONNECTED, SyncScheduler.constraintsFor(false).getRequiredNetworkType());
    }
}
