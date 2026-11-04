/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

/** Wire values from the matching stock AudioEffectCenter, independent of Android APIs. */
final class EffectProtocol {
    private EffectProtocol() {}

    static int gainQ22(int millibels) {
        // The UI persists hundredths of a dB; the stock engine takes dB in Q22.
        int clamped = Math.max(-1200, Math.min(1200, millibels));
        return (int) Math.floor((clamped / 100.0d) * 4194304.0d);
    }
}
