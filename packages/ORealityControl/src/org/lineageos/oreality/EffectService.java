/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Persistent owner of the OReality and Holo Audio effects.
 *
 * <p>Android destroys an {@code AudioEffect} when the process that created it
 * dies, and the effect is not restored automatically. The stock
 * AudioEffectCenter solves this with a persistent service that re-creates its
 * effect after boot and after audioserver restarts. This service does the same
 * for the two effects registered by this port.
 *
 * <p>It is also the single writer, so the Settings screen and the Quick Settings
 * tile never race each other over effect parameters.
 */
public final class EffectService extends Service {
    private static final String TAG = "ORealityService";

    private static EffectService sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Prefs mPrefs;
    private AudioXEffect mAudioX;
    private HoloAudioEffect mHolo;
    private boolean mAudioXBroken;

    /** Returns the running service, or null when it is not alive yet. */
    static EffectService peek() {
        return sInstance;
    }

    static void start(Context context) {
        try {
            context.startService(new Intent(context, EffectService.class));
        } catch (IllegalStateException | SecurityException unavailable) {
            Log.w(TAG, "cannot start the effect service", unavailable);
        }
    }

    private final BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // The audio server went away; every effect handle is stale.
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                rebind(true);
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener mFocusListener = focus -> { };

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mPrefs = new Prefs(this);
        mHolo = new HoloAudioEffect(this);
        rebind(false);
        registerReceiver(mNoisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        unregisterReceiver(mNoisyReceiver);
        releaseAudioX();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * (Re)creates the AudioX effect and applies the stored state.
     *
     * @param force release the current handle first
     */
    void rebind(boolean force) {
        if (force) {
            releaseAudioX();
        }
        if (mAudioX == null && !mAudioXBroken) {
            try {
                AudioXEffect effect = new AudioXEffect(AudioManager.AUDIO_SESSION_ID_GENERATE);
                if (!effect.isPresent()) {
                    Log.w(TAG, "AudioX effect is not registered; it will not be restored");
                    effect.release();
                    mAudioXBroken = true;
                } else {
                    mAudioX = effect;
                }
            } catch (RuntimeException unavailable) {
                Log.w(TAG, "cannot create the AudioX effect", unavailable);
                mAudioXBroken = true;
            }
        }
        applyState();
    }

    /** Applies the persisted selection to whichever effects are alive. */
    private void applyState() {
        boolean enabled = mPrefs.isRealityEnabled();
        if (mAudioX != null && mAudioX.hasControl()) {
            mAudioX.setEnabled(enabled);
            mAudioX.setProfile(mPrefs.getProfile());
            mAudioX.setVolumeIndex(mHolo.getMusicVolumeIndex());
            mAudioX.setEqualizer(mPrefs.isEqualizerEnabled(),
                    mHolo.getMusicVolumeIndex(), mPrefs.getEqualizerGains());
        }
        if (mHolo.isSupported()) {
            mHolo.setEnabled(mPrefs.isHoloEnabled());
            mHolo.setVolumeIndex(mHolo.getMusicVolumeIndex());
            mHolo.setRotation(mHolo.getMusicVolumeIndex() >= 0
                    ? getDisplayRotationQuarters() : 0);
        }
    }

    private void releaseAudioX() {
        if (mAudioX != null) {
            try {
                mAudioX.release();
            } catch (RuntimeException ignored) {
                // The handle can already be invalid after an audioserver restart.
            }
            mAudioX = null;
        }
        mAudioXBroken = false;
    }

    boolean isRealityEnabled() {
        return mAudioX != null && mAudioX.isEnabled();
    }

    boolean isRealityPresent() {
        return mAudioX != null;
    }

    boolean setRealityEnabled(boolean enabled) {
        mPrefs.setRealityEnabled(enabled);
        boolean accepted = mAudioX == null
                || mAudioX.setEnabled(enabled) == android.media.audiofx.AudioEffect.SUCCESS;
        return accepted;
    }

    boolean setProfile(int profile) {
        mPrefs.setProfile(profile);
        boolean accepted = mAudioX == null || mAudioX.setProfile(profile);
        if (mHolo.isSupported()) {
            mHolo.setProfile(profile);
        }
        return accepted;
    }

    int getProfile() {
        return mPrefs.getProfile();
    }

    boolean setEqualizerEnabled(boolean enabled) {
        mPrefs.setEqualizerEnabled(enabled);
        return pushEqualizer();
    }

    boolean isEqualizerEnabled() {
        return mPrefs.isEqualizerEnabled();
    }

    boolean setEqualizerGains(int[] gains) {
        mPrefs.setEqualizerGains(gains);
        return pushEqualizer();
    }

    int[] getEqualizerGains() {
        return mPrefs.getEqualizerGains();
    }

    private boolean pushEqualizer() {
        boolean accepted = mAudioX == null
                || mAudioX.setEqualizer(mPrefs.isEqualizerEnabled(),
                        mHolo.getMusicVolumeIndex(), mPrefs.getEqualizerGains());
        return accepted;
    }

    boolean isHoloSupported() {
        return mHolo.isSupported();
    }

    boolean isHoloAvailable() {
        return mHolo.isAvailable();
    }

    boolean isHoloEnabled() {
        return mHolo.isEnabled();
    }

    boolean setHoloEnabled(boolean enabled) {
        mPrefs.setHoloEnabled(enabled);
        mHolo.setEnabled(enabled);
        return true;
    }

    String describeHoloLevel() {
        return mHolo.describeImmersiveLevel();
    }

    private int getDisplayRotationQuarters() {
        // The spatializer expects the DisplayManager rotation, which is already
        // expressed in quarter turns (0, 1, 2, 3).
        return getDisplay() == null ? 0 : getDisplay().getRotation();
    }
}
