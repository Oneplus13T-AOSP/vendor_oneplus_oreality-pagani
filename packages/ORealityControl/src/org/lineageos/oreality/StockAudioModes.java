/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

/** Stock UI category ids and AudioEffectCenter profile ids are separate spaces. */
final class StockAudioModes {
    static final int SMART = 0, IMMERSIVE = 4, FIDELITY = 5, CUSTOM = 6;
    static final int DEFAULT = 0, MOVIE = 1, MUSIC = 2, GAME1 = 3, GAME2 = 4,
            VOICE = 5, VIDEO2 = 11;

    static int migrate(int old) {
        switch (old) {
            case 1: return FIDELITY;
            case 2: return IMMERSIVE;
            case 3: return SMART;
            case SMART: case IMMERSIVE: case FIDELITY: case CUSTOM: return old;
            default: return SMART;
        }
    }

    static int resolve(int mode, int scene, boolean headphones, int rotation) {
        // pagani stock: speaker_geq=true, high_quality/gallery_effect=false.
        if (mode == CUSTOM) return DEFAULT;
        if (mode == FIDELITY) return MUSIC;
        if (mode == IMMERSIVE) {
            return scene == VIDEO2 ? VIDEO2 : (scene == MOVIE ? movie(headphones, rotation) : MOVIE);
        }
        if (scene == MOVIE) return movie(headphones, rotation);
        return scene;
    }

    private static int movie(boolean headphones, int rotation) {
        return headphones || rotation == 1 || rotation == 3 ? MOVIE : 12;
    }

    static int engineSlot(int profile) {
        switch (profile) {
            case 0: return 6;
            case 1: return 2;
            case 2: return 1;
            case 3: return 3;
            case 4: return 4;
            case 5: return 5;
            case 11: return 7;
            case 12: return 8;
            case 13: return 9;
            case 14: return 10;
            default: return -1;
        }
    }

    static int spatializerProfile(int profile) {
        if (profile == 11) return 4; // stock d0.h.m: AudioX video2 uses spatializer game slot
        if (profile == 12 || profile == 14) return 1;
        if (profile == 13) return 2;
        return profile >= 0 && profile <= 5 ? profile : -1;
    }

    // Match stock priority across simultaneous active players, independent of callback order.
    static int priority(int scene) {
        switch (scene) {
            case MUSIC: return 7;
            case VOICE: return 6;
            case VIDEO2: return 5;
            case MOVIE: return 4;
            case GAME1: return 3;
            case GAME2: return 2;
            default: return 0;
        }
    }
}
