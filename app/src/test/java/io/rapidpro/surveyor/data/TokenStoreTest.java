package io.rapidpro.surveyor.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TokenStoreTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void putGetRemove() {
        assertNull(TokenStore.get(context, "org-1"));

        TokenStore.put(context, "org-1", "secret-token");
        assertEquals("secret-token", TokenStore.get(context, "org-1"));

        TokenStore.remove(context, "org-1");
        assertNull(TokenStore.get(context, "org-1"));
    }
}
