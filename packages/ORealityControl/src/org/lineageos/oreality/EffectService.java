/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.app.Service;
import android.hardware.display.DisplayManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.Spatializer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.util.ArrayList;

/**
 * Persistent owner of the OReality and Holo Audio effects.
 *
 * <p>Android destroys an {@code AudioEffect} when the process that created it
 * dies, and the effect is not restored automatically. The stock
 * AudioEffectCenter solves this with a persistent service that re-creates its
 * effect after boot and after audioserver restarts. This service does the same
 * for the two effects registered by this port.
 *
 * <p>All parameter writes run on the main thread.
 */
public final class EffectService extends Service {
    private static final String TAG = "ORealityService";

    private static EffectService sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Prefs mPrefs;
    private AudioXEffect mAudioX;
    private HoloAudioEffect mHolo;
    private AudioManager mAudioManager;
    private DisplayManager mDisplayManager;
    private boolean mServerReady;
    private boolean mDestroyed;
    private int mRetries;
    private boolean mRestoreHoloState = true;
    private final ArrayList<Runnable> mListeners = new ArrayList<>();

    void addListener(Runnable listener) { mListeners.add(listener); }
    void removeListener(Runnable listener) { mListeners.remove(listener); }
    private void notifyListeners() {
        for (Runnable listener : new ArrayList<>(mListeners)) listener.run();
    }
    private final Runnable mRetry = () -> rebind(false);

    final class LocalBinder extends Binder {
        EffectService getService() { return EffectService.this; }
    }

    private final AudioManager.AudioServerStateCallback mServerCallback =
            new AudioManager.AudioServerStateCallback() {
        @Override public void onAudioServerDown() {
            mServerReady = false;
            mHandler.removeCallbacks(mRetry);
            releaseAudioX();
            notifyListeners();
        }
        @Override public void onAudioServerUp() {
            if (mDestroyed) return;
            mServerReady = true;
            mRetries = 0;
            mRestoreHoloState = true;
            rebind(true);
        }
    };
    private final AudioDeviceCallback mDeviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { rebind(false); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { rebind(false); }
    };
    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { updateHoloParameters(); }
        @Override public void onDisplayRemoved(int id) { updateHoloParameters(); }
        @Override public void onDisplayChanged(int id) { updateHoloParameters(); }
    };
    private final Spatializer.OnSpatializerStateChangedListener mSpatializerListener =
            new Spatializer.OnSpatializerStateChangedListener() {
        @Override public void onSpatializerAvailableChanged(Spatializer s, boolean available) {
            restoreHoloState();
            updateHoloParameters();
        }
        @Override public void onSpatializerEnabledChanged(Spatializer s, boolean enabled) {
            updateHoloParameters();
        }
    };

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

    private final BroadcastReceiver mVolumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
                    == AudioManager.STREAM_MUSIC) rebind(false);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mPrefs = new Prefs(this);
        mHolo = new HoloAudioEffect(this);
        mAudioManager = getSystemService(AudioManager.class);
        mDisplayManager = getSystemService(DisplayManager.class);
        mAudioManager.setAudioServerStateCallback(mHandler::post, mServerCallback);
        mAudioManager.registerAudioDeviceCallback(mDeviceCallback, mHandler);
        if (mDisplayManager != null) mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
        mHolo.addStateListener(mHandler::post, mSpatializerListener);
        registerReceiver(mVolumeReceiver, new IntentFilter(AudioManager.ACTION_VOLUME_CHANGED),
                Context.RECEIVER_NOT_EXPORTED);
        mServerReady = mAudioManager.isAudioServerRunning();
        rebind(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        rebind(false);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;
        sInstance = null;
        mAudioManager.clearAudioServerStateCallback();
        mAudioManager.unregisterAudioDeviceCallback(mDeviceCallback);
        if (mDisplayManager != null) mDisplayManager.unregisterDisplayListener(mDisplayListener);
        mHolo.removeStateListener(mSpatializerListener);
        unregisterReceiver(mVolumeReceiver);
        releaseAudioX();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    /**
     * (Re)creates the AudioX effect and applies the stored state.
     *
     * @param force release the current handle first
     */
    void rebind(boolean force) {
        if (mDestroyed || !mServerReady) return;
        if (force) releaseAudioX();
        if (mAudioX == null) {
            try {
                mAudioX = new AudioXEffect(1);
                mAudioX.setControlListener(() -> applyState());
                mHandler.removeCallbacks(mRetry);
            } catch (RuntimeException unavailable) {
                Log.w(TAG, "cannot create AudioX", unavailable);
                scheduleRetry();
            }
        }
        applyState();
    }

    private void scheduleRetry() {
        mHandler.removeCallbacks(mRetry);
        if (!mDestroyed && mServerReady && mRetries++ < 5) {
            mHandler.postDelayed(mRetry, 2000);
        }
    }

    /** Applies settings without allowing a stale native handle to crash the process. */
    private boolean applyState() {
        if (mDestroyed || !mServerReady) return false;
        boolean accepted = false;
        try {
            if (mAudioX != null && mAudioX.hasControl()) {
                accepted = mAudioX.setEnabled(mPrefs.isRealityEnabled())
                        == android.media.audiofx.AudioEffect.SUCCESS;
                accepted &= mAudioX.setProfile(mPrefs.getProfile());
                accepted &= mAudioX.setVolumeIndex(mHolo.getMusicVolumeIndex());
                accepted &= mAudioX.setEqualizer(mPrefs.isEqualizerEnabled()
                                && mPrefs.getProfile() == AudioXEffect.PROFILE_CUSTOM,
                        mHolo.getMusicVolumeIndex(), mPrefs.getEqualizerGains());
            }
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "AudioX handle is unavailable", unavailable);
            releaseAudioX();
            scheduleRetry();
        }
        restoreHoloState();
        updateHoloParameters();
        if (accepted) mRetries = 0;
        return accepted;
    }

    private void restoreHoloState() {
        if (!mRestoreHoloState || !mServerReady || mDestroyed) return;
        try {
            if (mHolo.isSupported()) {
                mHolo.setEnabled(mPrefs.isHoloEnabled());
                mRestoreHoloState = false;
            }
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "cannot restore spatializer state", unavailable);
        }
    }

    private void updateHoloParameters() {
        if (mDestroyed || !mServerReady) return;
        try {
            mHolo.setProfile(mPrefs.getProfile());
            mHolo.setVolumeIndex(mHolo.getMusicVolumeIndex());
            Display display = mDisplayManager == null ? null
                    : mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            mHolo.setRotation(display == null ? 0 : display.getRotation());
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "cannot update spatializer parameters", unavailable);
        }
        notifyListeners();
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
    }

    boolean isRealityEnabled() {
        try {
            return mAudioX != null && mAudioX.isEnabled();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    boolean isRealityPresent() {
        return mAudioX != null;
    }

    boolean setRealityEnabled(boolean enabled) {
        mPrefs.setRealityEnabled(enabled);
        return applyState();
    }

    boolean setProfile(int profile) {
        if (EffectProtocol.audioxProfile(profile) < 0) return false;
        mPrefs.setProfile(profile);
        return applyState();
    }

    int getProfile() {
        return mPrefs.getProfile();
    }

    boolean setEqualizerEnabled(boolean enabled) {
        mPrefs.setEqualizerEnabled(enabled);
        if (enabled) mPrefs.setProfile(AudioXEffect.PROFILE_CUSTOM);
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
        return applyState();
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
        mRestoreHoloState = false;
        try {
            mHolo.setEnabled(enabled);
            updateHoloParameters();
            return mHolo.isEnabled() == enabled;
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "cannot change spatializer state", unavailable);
            return false;
        }
    }

    String describeHoloLevel() {
        return mHolo.describeImmersiveLevel();
    }
}
