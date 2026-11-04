/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

/** Exact pagani stock DolbyModel.EQUALIZER_PRESETS, converted from dB to millibels. */
final class EqualizerPresets {
    static final int CUSTOM = 0;
    static final int BANDS = 10;
    private static final int[][] DB = {
        {2, 3, 1, -1, 1, 2, 3, 2, 1, 1}, // pop (id 1)
        {3, 4, 2, -2, 0, 3, 4, 2, 1, 0}, // rock
        {5, 5, 3, 0, 1, 2, 3, 4, 3, 2}, // electronic
        {1, 1, 0, -1, 0, 2, 3, 2, 0, 1}, // classical
        {-3, -2, -1, 2, 3, 4, 5, 3, 2, 1}, // vocal
        {6, 5, 5, 3, 1, 0, 0, 0, 0, 0}, // bass
        {0, 0, 0, 0, 1, 2, 4, 5, 6, 5} // treble
    };
    static boolean isValid(int id) { return id >= 0 && id <= DB.length; }
    static int[] gains(int id) {
        if (id <= 0 || id > DB.length) throw new IllegalArgumentException("preset: " + id);
        int[] gains = new int[10];
        for (int i = 0; i < gains.length; i++) gains[i] = DB[id - 1][i] * 100;
        return gains;
    }
}
