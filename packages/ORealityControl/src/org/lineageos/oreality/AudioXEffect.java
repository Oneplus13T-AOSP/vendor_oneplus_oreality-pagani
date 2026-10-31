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

    /*
     * Public profile ids, exactly as stored in Settings.System
     * "system_effect_profile" and shown by the stock OReality UI. These are NOT
     * the values the engine receives: the stock AudioxEffect translates them
     * before writing PARAM_PROFILE.
     */
    static final int PROFILE_INTELLIGENCE = 0;
    static final int PROFILE_MUSIC = 1;
    static final int PROFILE_MOVIE = 2;
    static final int PROFILE_GAME = 3;
    static final int PROFILE_IMMERSION = 4;
    static final int PROFILE_FIDELITY = 5;
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
        int engine = mapProfile(profile);
        if (engine < 0) {
            return false;
        }
        return setInt(PARAM_PROFILE, engine);
    }

    /**
     * Sets the graphic EQ. {@code volumeIndex} selects the speaker tuning
     * table, so callers pass the current music stream index.
     *
     * <p>Gains are in millibels and are converted to the fixed-point format the
     * engine consumes (Q22), matching the stock service.
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
            writeInt(toEngineGain(millibels[band]), payload, 8 + band * 4);
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

    /**
     * Translates a public profile id to the index the effect expects.
     *
     * <p>Copied from the stock {@code AudioxEffect#setProfile}: every profile
     * except music, movie, game and custom maps to the engine's default slot 6.
     * The public music id 1 maps to engine slot 2, which is the trap this
     * translation exists for.
     *
     * @return the engine index, or -1 when the id is not a known profile
     */
    private static int mapProfile(int profile) {
        switch (profile) {
            case PROFILE_MUSIC:
                return 2;
            case PROFILE_MOVIE:
                return 3;
            case PROFILE_GAME:
                return 4;
            case PROFILE_CUSTOM:
                return 6;
            case PROFILE_INTELLIGENCE:
                return 6;
            case PROFILE_IMMERSION:
                return 4;
            case PROFILE_FIDELITY:
                return 5;
            default:
                return -1;
        }
    }

    /**
     * Converts millibels to the Q22 fixed-point value the AudioX engine reads.
     *
     * <p>The stock service stores the preset gains in
     * {@code Settings.System} and then computes
     * {@code (int) Math.floor(gain * Math.pow(2, 22))} before every write, so
     * the engine never receives the raw value.
     */
    private static int toEngineGain(int millibels) {
        return (int) Math.floor(millibels * 4194304.0d);
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
