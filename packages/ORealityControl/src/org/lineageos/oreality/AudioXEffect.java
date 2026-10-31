/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.media.audiofx.AudioEffect;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Thin wrapper around the OnePlus AudioX effect.
 *
 * <p>Protocol taken from the stock ColorOS 17 pagani AudioEffectCenter
 * ({@code c0.a} / {@code AudioxEffect}):
 *
 * <ul>
 *   <li>type = {@link #EFFECT_TYPE_NULL} (AOSP {@code kEffectUuidNull}, the
 *       effect has no standard type)</li>
 *   <li>impl = {@code 41f6c0f4-5d8f-11ec-bf63-0242ac130002}</li>
 *   <li>attached to the global output mix (session 0)</li>
 *   <li>parameters use the vendor parameter space {@code 0x10010000}</li>
 * </ul>
 *
 * The stock service reaches {@code AudioEffect#setParameter(int, byte[])}
 * reflectively because it is {@code @hide}; a platform-signed app can call it
 * directly, but reflection keeps this source compiling against the public SDK
 * while still using the platform API at runtime.
 */
final class AudioXEffect {
    /** AOSP {@code kEffectUuidNull}: no standard effect type. */
    static final UUID EFFECT_TYPE_NULL =
            UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210");
    /** OnePlus AudioX implementation UUID. */
    static final UUID EFFECT_UUID =
            UUID.fromString("41f6c0f4-5d8f-11ec-bf63-0242ac130002");

    /*
     * Vendor parameter ids, from the stock AudioxEffect class. The high byte
     * selects the vendor parameter space.
     */
    private static final int PARAM_PROFILE = 0x100100bb;
    private static final int PARAM_EQ_GAINS = 0x100100bc;
    private static final int PARAM_VOLUME_INDEX = 0x100100be;
    private static final int PARAM_ATTENUATION = 0x100100c2;
    private static final int PARAM_EQ_ENABLE = 0x100100c9;

    /** Number of graphic-EQ bands the engine accepts. */
    static final int EQ_BANDS = 10;

    /**
     * Profiles accepted by {@link #setProfile(int)}. These are the stock
     * AudioX profile indices; {@code 6} is the custom (user EQ) profile.
     */
    static final int PROFILE_MUSIC = 2;
    static final int PROFILE_MOVIE = 3;
    static final int PROFILE_GAME = 4;
    static final int PROFILE_CUSTOM = 6;

    private static final Method SET_PARAM_BYTES = findMethod("setParameter",
            int.class, byte[].class);
    private static final Method SET_PARAM_INT = findMethod("setParameter",
            int.class, int.class);

    private final AudioEffect mEffect;

    AudioXEffect(int priority) {
        AudioEffect effect;
        try {
            effect = (AudioEffect) AudioEffect.class
                    .getConstructor(UUID.class, UUID.class, int.class, int.class)
                    .newInstance(EFFECT_TYPE_NULL, EFFECT_UUID, priority, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create the AudioX effect", e);
        }
        mEffect = effect;
    }

    boolean isPresent() {
        for (AudioEffect.Descriptor descriptor : AudioEffect.queryEffects()) {
            if (EFFECT_UUID.equals(descriptor.uuid)) {
                return true;
            }
        }
        return false;
    }

    boolean hasControl() {
        return mEffect.hasControl();
    }

    int setEnabled(boolean enabled) {
        return mEffect.setEnabled(enabled);
    }

    boolean isEnabled() {
        return mEffect.getEnabled();
    }

    void release() {
        mEffect.release();
    }

    /** Stock profile index, see the {@code PROFILE_*} constants. */
    boolean setProfile(int profile) {
        if (!validProfile(profile)) {
            return false;
        }
        return setInt(PARAM_PROFILE, mapProfile(profile));
    }

    /**
     * Sets the graphic EQ. {@code volumeIndex} selects the speaker tuning
     * table, so callers pass the current music stream index.
     */
    boolean setEqualizer(boolean enabled, int volumeIndex, int[] millibels) {
        if (millibels == null || millibels.length != EQ_BANDS) {
            return false;
        }
        if (!setBytes(PARAM_EQ_ENABLE, intToBytes(enabled ? 1 : 0))) {
            return false;
        }
        byte[] payload = new byte[(EQ_BANDS + 2) * 4];
        writeInt(volumeIndex, payload, 0);
        writeInt(EQ_BANDS, payload, 4);
        for (int band = 0; band < EQ_BANDS; band++) {
            writeInt(millibels[band], payload, 8 + band * 4);
        }
        return setBytes(PARAM_EQ_GAINS, payload);
    }

    boolean setVolumeIndex(int index) {
        return setInt(PARAM_VOLUME_INDEX, index);
    }

    boolean setAttenuation(float decibels) {
        return setBytes(PARAM_ATTENUATION, intToBytes(Float.floatToIntBits(decibels)));
    }

    private boolean setInt(int param, int value) {
        if (SET_PARAM_INT == null) {
            return false;
        }
        return invoke(SET_PARAM_INT, param, value) == AudioEffect.SUCCESS;
    }

    private boolean setBytes(int param, byte[] value) {
        if (SET_PARAM_BYTES == null) {
            return false;
        }
        return invoke(SET_PARAM_BYTES, param, value) == AudioEffect.SUCCESS;
    }

    private int invoke(Method method, int param, Object value) {
        try {
            Object result = method.invoke(mEffect, param, value);
            return result instanceof Integer ? (Integer) result : AudioEffect.ERROR;
        } catch (ReflectiveOperationException e) {
            return AudioEffect.ERROR;
        }
    }

    private static Method findMethod(String name, Class<?>... parameters) {
        try {
            Method method = AudioEffect.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean validProfile(int profile) {
        return profile == PROFILE_MUSIC || profile == PROFILE_MOVIE
                || profile == PROFILE_GAME || profile == PROFILE_CUSTOM;
    }

    /**
     * The stock class keeps the public profile number in {@code mProfile} and
     * writes a translated engine index. Only the four profiles exposed here are
     * translated; the identity mapping covers them.
     */
    private static int mapProfile(int profile) {
        return profile;
    }

    private static byte[] intToBytes(int value) {
        byte[] out = new byte[4];
        writeInt(value, out, 0);
        return out;
    }

    private static void writeInt(int value, byte[] out, int offset) {
        out[offset] = (byte) (value & 0xff);
        out[offset + 1] = (byte) ((value >>> 8) & 0xff);
        out[offset + 2] = (byte) ((value >>> 16) & 0xff);
        out[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }
}
