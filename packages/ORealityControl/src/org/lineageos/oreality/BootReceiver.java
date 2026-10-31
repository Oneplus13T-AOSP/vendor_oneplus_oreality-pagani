/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;

/**
 * Restores both effects after boot and after an audioserver restart.
 *
 * <p>{@code AudioEffect} handles do not survive an audioserver crash and are not
 * re-created by the framework. The stock AudioEffectCenter listens for the same
 * events; this receiver keeps the behaviour without running a polling loop.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final long MIN_RESTART_INTERVAL_MS = 1500;

    private static long sLastRestart;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                EffectService.start(context);
                break;
            case AudioManager.ACTION_AUDIO_BECOMING_NOISY: {
                // Debounce: several noisy broadcasts can arrive together. The
                // service also listens for this event, so only ask for a rebind
                // when it is already running; starting it would be a no-op
                // otherwise and the service recreates its effect in onCreate.
                long now = SystemClock.elapsedRealtime();
                if (now - sLastRestart >= MIN_RESTART_INTERVAL_MS) {
                    sLastRestart = now;
                    EffectService service = EffectService.peek();
                    if (service != null) {
                        service.rebind(true);
                    }
                }
                break;
            }
            default:
                break;
        }
    }
}
