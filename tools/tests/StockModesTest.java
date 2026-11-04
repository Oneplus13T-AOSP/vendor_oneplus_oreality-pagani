/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

public final class StockModesTest {
    public static void main(String[] args) {
        check(StockAudioModes.migrate(1), 5, "legacy Music becomes Fidelity");
        check(StockAudioModes.migrate(2), 4, "legacy Movie becomes Immersive");
        check(StockAudioModes.migrate(3), 0, "legacy Game becomes Smart");
        check(StockAudioModes.migrate(6), 6, "preserve Custom");
        check(StockAudioModes.migrate(999), 0, "invalid selection becomes Smart");
        check(StockAudioModes.resolve(5, 5, false, 0), 2, "stock Fidelity uses music, not voice");
        check(StockAudioModes.engineSlot(StockAudioModes.resolve(5, 5, false, 0)), 1, "Fidelity engine slot");
        check(StockAudioModes.resolve(4, 0, false, 0), 1, "Immersive idle uses movie");
        check(StockAudioModes.resolve(4, 1, false, 0), 12, "portrait speaker video variant");
        check(StockAudioModes.resolve(4, 1, false, 1), 1, "landscape speaker movie");
        check(StockAudioModes.resolve(4, 1, true, 0), 1, "headphones movie");
        check(StockAudioModes.resolve(4, 11, false, 0), 11, "video2 specialization");
        check(StockAudioModes.resolve(0, 3, false, 0), 3, "Smart game1");
        check(StockAudioModes.resolve(0, 2, false, 0), 2, "Smart music");
        check(StockAudioModes.resolve(6, 2, false, 0), 0, "Custom ignores scene");
        check(StockAudioModes.engineSlot(12), 8, "stock portrait engine slot");
        check(StockAudioModes.spatializerProfile(11), 4, "stock video2 spatializer translation");
        if (StockAudioModes.priority(2) <= StockAudioModes.priority(3)) throw new AssertionError("stock music priority");
        check(EqualizerPresets.gains(1)[3], -100, "stock pop notch");
        check(EqualizerPresets.gains(5)[0], -300, "stock vocal bass cut");
        check(EqualizerPresets.gains(6)[0], 600, "stock bass preset");
        check(EqualizerPresets.gains(7)[8], 600, "stock treble peak");
        check(EffectProtocol.gainQ22(EqualizerPresets.gains(6)[0]), 25165824,
                "stock bass +6dB wire value");
        int[] copy = EqualizerPresets.gains(6); copy[0] = -9999;
        check(EqualizerPresets.gains(6)[0], 600, "preset arrays cannot be mutated");
        for (int p = 1; p <= 7; p++) for (int gain : EqualizerPresets.gains(p)) {
            if (gain < -600 || gain > 600) throw new AssertionError("stock +/-6dB range");
        }
    }
    private static void check(int actual, int expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
