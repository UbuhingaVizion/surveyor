package io.rapidpro.surveyor.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import io.rapidpro.surveyor.Logger;

/**
 * Stores RapidPro API tokens encrypted at rest (backed by the Android Keystore).
 *
 * Falls back to plain preferences if the keystore isn't available, so the app still works on
 * devices without a secure keystore.
 */
public final class TokenStore {

    private static final String FILE = "secure_tokens";

    private static SharedPreferences prefs;

    private TokenStore() {
    }

    private static synchronized SharedPreferences prefs(Context context) {
        if (prefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                prefs = EncryptedSharedPreferences.create(
                        context,
                        FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            } catch (Exception e) {
                Logger.e("Unable to create encrypted token store, falling back to plain preferences", e);
                prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            }
        }
        return prefs;
    }

    public static void put(Context context, String uuid, String token) {
        prefs(context).edit().putString(uuid, token).apply();
    }

    public static String get(Context context, String uuid) {
        return prefs(context).getString(uuid, null);
    }

    public static void remove(Context context, String uuid) {
        prefs(context).edit().remove(uuid).apply();
    }
}
