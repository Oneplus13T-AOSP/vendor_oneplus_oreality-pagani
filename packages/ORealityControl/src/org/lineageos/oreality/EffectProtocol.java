/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

/** Wire values from the matching stock AudioEffectCenter, independent of Android APIs. */
final class EffectProtocol {
    private EffectProtocol() {}

    static int volumeIndex(int index, int max) {
        if (max <= 0) return 0;
        int clamped = Math.max(0, Math.min(max, index));
        // Stock engines accept 0..16, while PixelOS pagani uses 30 steps.
        // Clamping the raw index would select the loudest tuning for the entire
        // upper half of the slider. Preserve native 15/16-step ranges and map
        // larger UI scales across the complete engine range.
        return max <= 16 ? clamped : (int) Math.round(clamped * 16.0 / max);
    }

    static int gainQ22(int millibels) {
        // The UI persists hundredths of a dB; the stock engine takes dB in Q22.
        int clamped = Math.max(-1200, Math.min(1200, millibels));
        return (int) Math.floor((clamped / 100.0d) * 4194304.0d);
    }
}
