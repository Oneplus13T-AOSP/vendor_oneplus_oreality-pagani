/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.content.Context;
import android.media.AudioManager;
import android.media.Spatializer;


import java.util.Locale;

/**
 * Holo Audio control.
 *
 * <p>Holo Audio is the OPlus MetaAudio renderer. The OPlus spatializer effect
 * registered as {@code spatializer} is exposed by the Qualcomm effects HAL as
 * the platform spatializer, so the control surface is the public
 * {@link Spatializer} API plus the OPlus vendor parameter keys.
 *
 * <p>The vendor keys and their meanings come from the stock ColorOS 17
 * AudioEffectCenter ({@code d0.h}), which is the component that drives the same
 * effect on stock:
 *
 * <table>
 *   <tr><th>Key</th><th>Meaning</th></tr>
 *   <tr><td>0x100100BB</td><td>stock profile id (2 music, 1 movie,
 *       4 game, 0 custom; different from AudioX engine slots)</td></tr>
 *   <tr><td>0x100100BE</td><td>speaker tuning table for the volume index</td></tr>
 *   <tr><td>0x100100BF</td><td>display rotation 0..3, in quarter turns</td></tr>
 *   <tr><td>0x100100C0</td><td>fold state</td></tr>
 *   <tr><td>0x100100C1</td><td>Bluetooth device type</td></tr>
 *   <tr><td>0x100100C4</td><td>HAL channel reversal</td></tr>
 * </table>
 *
 * <p>Per-app placement needs the OPlus MetaAudio service stack
 * ({@code atlasservice} and {@code libSpecailizerPLService}) together with the
 * AudioFlinger extension libraries. Those are not part of this port, so
 * {@link #isMultiAppSupported()} reports the truth and the UI does not offer
 * placement controls.
 */
final class HoloAudioEffect {
    /** Shared parameter key; payload ids differ from AudioX engine slots. */
    static final int PARAM_PROFILE = 0x100100BB;
    /** Speaker tuning table for the current music volume index. */
    static final int PARAM_VOLUME_INDEX = 0x100100BE;
    /** Display rotation in quarter turns, 0..3. */
    static final int PARAM_ROTATION = 0x100100BF;

    private static final int MAX_ROTATION = 3;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final Spatializer mSpatializer;

    HoloAudioEffect(Context context) {
        mContext = context.getApplicationContext();
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mSpatializer = mAudioManager == null ? null : mAudioManager.getSpatializer();
    }

    /** True when the platform spatializer, i.e. the OPlus effect, is loaded. */
    boolean isSupported() {
        return mSpatializer != null
                && mSpatializer.getImmersiveAudioLevel()
                        != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE;
    }

    /** True when spatialization is possible for the current output device. */
    boolean isAvailable() {
        return mSpatializer != null && mSpatializer.isAvailable();
    }

    boolean isEnabled() {
        return mSpatializer != null && mSpatializer.isEnabled();
    }

    void setEnabled(boolean enabled) {
        if (mSpatializer != null) {
            mSpatializer.setEnabled(enabled);
        }
    }

    /** Stock spatializer profile id, or -1 when it cannot be read. */
    int getProfile() {
        return getInt(PARAM_PROFILE);
    }

    boolean setProfile(int profile) {
        int stock = EffectProtocol.spatializerProfile(profile);
        return stock >= 0 && setInt(PARAM_PROFILE, stock);
    }

    /** Volume index used to pick the speaker tuning table. */
    boolean setVolumeIndex(int index) {
        return setInt(PARAM_VOLUME_INDEX, index);
    }

    /** Rotation in quarter turns; values outside 0..3 are rejected. */
    boolean setRotation(int quarters) {
        if (quarters < 0 || quarters > MAX_ROTATION) {
            return false;
        }
        return setInt(PARAM_ROTATION, quarters);
    }

    /** Immersive level: 0 none, 1 multichannel, 2 bed + objects, -1 other. */
    int getImmersiveLevel() {
        return mSpatializer == null
                ? Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
                : mSpatializer.getImmersiveAudioLevel();
    }

    String describeImmersiveLevel() {
        switch (getImmersiveLevel()) {
            case Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL:
                return "multichannel";
            case Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_OTHER:
                return "other";
            case Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE:
            default:
                return "unsupported";
        }
    }

    static boolean isMultiAppSupported() {
        return false; // The renderer alone does not provide per-app routing.
    }

    void addStateListener(java.util.concurrent.Executor executor,
            Spatializer.OnSpatializerStateChangedListener listener) {
        if (mSpatializer != null) {
            mSpatializer.addOnSpatializerStateChangedListener(executor, listener);
            mSpatializer.setOnSpatializerOutputChangedListener(executor,
                    (spatializer, output) -> listener.onSpatializerAvailableChanged(
                            spatializer, spatializer.isAvailable()));
        }
    }

    void removeStateListener(Spatializer.OnSpatializerStateChangedListener listener) {
        if (mSpatializer != null) {
            mSpatializer.removeOnSpatializerStateChangedListener(listener);
            mSpatializer.clearOnSpatializerOutputChangedListener();
        }
    }

    boolean isHeadsetConnected() {
        return mAudioManager != null && mAudioManager.isWiredHeadsetOn();
    }

    /** Current music stream index, clamped to the engine's table range. */
    int getMusicVolumeIndex() {
        if (mAudioManager == null) {
            return 0;
        }
        int index = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // The stock service divides by ten on devices that report a 0..100 scale.
        if (max > 100) {
            index /= 10;
        }
        return Math.max(0, Math.min(16, index));
    }

    private int getInt(int key) {
        if (mSpatializer == null) {
            return -1;
        }
        byte[] value = new byte[4];
        try {
            mSpatializer.getEffectParameter(key, value);
        } catch (RuntimeException e) {
            return -1;
        }
        return (value[0] & 0xff) | (value[1] & 0xff) << 8
                | (value[2] & 0xff) << 16 | (value[3] & 0xff) << 24;
    }

    private boolean setInt(int key, int value) {
        if (mSpatializer == null || !mSpatializer.isAvailable() || !mSpatializer.isEnabled()) {
            return false;
        }
        byte[] payload = new byte[4];
        payload[0] = (byte) (value & 0xff);
        payload[1] = (byte) ((value >>> 8) & 0xff);
        payload[2] = (byte) ((value >>> 16) & 0xff);
        payload[3] = (byte) ((value >>> 24) & 0xff);
        try {
            mSpatializer.setEffectParameter(key, payload);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String hex(int value) {
        return String.format(Locale.US, "0x%08X", value);
    }
}
