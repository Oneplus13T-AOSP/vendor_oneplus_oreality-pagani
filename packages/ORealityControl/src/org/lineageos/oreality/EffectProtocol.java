/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

/** Wire values from the matching stock AudioEffectCenter, independent of Android APIs. */
final class EffectProtocol {
    private EffectProtocol() {}

    // Keep the port's existing preference ids stable. Stock category ids, stock
    // profile ids and AudioX engine slots are three different value spaces.
    static int spatializerProfile(int selection) {
        switch (selection) {
            case 0: return 0; // default; automatic scene classification is not ported
            case 1: return 2; // music
            case 2: return 1; // movie
            case 3: return 4; // game
            case 4: return 3; // immersion
            case 5: return 5; // fidelity / voice
            case 6: return 0; // custom
            default: return -1;
        }
    }

    static int audioxProfile(int selection) {
        switch (spatializerProfile(selection)) {
            case 0: return 6;
            case 1: return 2;
            case 2: return 1;
            case 3: return 3;
            case 4: return 4;
            case 5: return 5;
            default: return -1;
        }
    }

    static int gainQ22(int millibels) {
        // The UI persists hundredths of a dB; the stock engine takes dB in Q22.
        int clamped = Math.max(-1200, Math.min(1200, millibels));
        return (int) Math.floor((clamped / 100.0d) * 4194304.0d);
    }
}
