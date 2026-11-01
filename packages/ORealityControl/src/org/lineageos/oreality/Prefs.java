/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;

/**
 * Persistent, per-user settings for both audio features.
 *
 * <p>The selection is stored in the app's device-protected storage so it is
 * available before the user unlocks and survives an audioserver restart. The
 * actual effect state is always re-read from the engine; these values are only
 * what to restore.
 */
final class Prefs {
    private static final String FILE = "oreality";

    private static final String KEY_OREALITY_ENABLED = "oreality_enabled";
    private static final String KEY_OREALITY_PROFILE = "oreality_profile";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_EQ_GAINS = "eq_gains";
    private static final String KEY_HOLO_ENABLED = "holo_enabled";

    static final int DEFAULT_PROFILE = AudioXEffect.PROFILE_MUSIC;
    private final SharedPreferences mPrefs;

    Prefs(Context context) {
        Context storage = context.isDeviceProtectedStorage()
                ? context : context.createDeviceProtectedStorageContext();
        mPrefs = storage.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isRealityEnabled() {
        return mPrefs.getBoolean(KEY_OREALITY_ENABLED, true);
    }

    void setRealityEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_OREALITY_ENABLED, enabled).apply();
    }

    int getProfile() {
        int profile = mPrefs.getInt(KEY_OREALITY_PROFILE, DEFAULT_PROFILE);
        return EffectProtocol.audioxProfile(profile) >= 0 ? profile : DEFAULT_PROFILE;
    }

    void setProfile(int profile) {
        mPrefs.edit().putInt(KEY_OREALITY_PROFILE, profile).apply();
    }

    boolean isEqualizerEnabled() {
        return mPrefs.getBoolean(KEY_EQ_ENABLED, false);
    }

    void setEqualizerEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply();
    }

    int[] getEqualizerGains() {
        String stored = mPrefs.getString(KEY_EQ_GAINS, null);
        int[] gains = new int[AudioXEffect.EQ_BANDS];
        if (stored == null || stored.isEmpty()) {
            return gains;
        }
        String[] parts = stored.split(",");
        if (parts.length != gains.length) {
            return gains;
        }
        try {
            for (int band = 0; band < gains.length; band++) {
                gains[band] = Math.max(-1200, Math.min(1200, Integer.parseInt(parts[band].trim())));
            }
        } catch (NumberFormatException invalid) {
            return new int[AudioXEffect.EQ_BANDS];
        }
        return gains;
    }

    void setEqualizerGains(int[] gains) {
        if (gains == null || gains.length != AudioXEffect.EQ_BANDS) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int band = 0; band < gains.length; band++) {
            if (band > 0) {
                builder.append(',');
            }
            builder.append(gains[band]);
        }
        mPrefs.edit().putString(KEY_EQ_GAINS, builder.toString()).apply();
    }

    boolean isHoloEnabled() {
        return mPrefs.getBoolean(KEY_HOLO_ENABLED, false);
    }

    void setHoloEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_HOLO_ENABLED, enabled).apply();
    }

    /** Whether the stock effect engine reports itself as available. */
    static boolean isEffectDeclared() {
        return SystemProperties.get("ro.oplus.audio.effect.type", "").equals("audiox_merge");
    }
}
