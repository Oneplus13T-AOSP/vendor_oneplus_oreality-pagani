/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

public final class EffectProtocolTest {
    public static void main(String[] args) {
        check(EffectProtocol.volumeIndex(0, 30), 0, "muted stream");
        check(EffectProtocol.volumeIndex(15, 30), 8, "PixelOS half volume");
        check(EffectProtocol.volumeIndex(30, 30), 16, "PixelOS maximum volume");
        check(EffectProtocol.volumeIndex(24, 30), 13, "PixelOS upper range");
        check(EffectProtocol.volumeIndex(8, 15), 8, "native 15-step range");
        check(EffectProtocol.volumeIndex(16, 16), 16, "native 16-step range");
        check(EffectProtocol.volumeIndex(80, 160), 8, "stock stepless range");
        check(EffectProtocol.volumeIndex(-1, 30), 0, "negative index");
        check(EffectProtocol.volumeIndex(100, 30), 16, "index exceeds maximum");
        check(EffectProtocol.volumeIndex(5, 0), 0, "invalid range");
        check(EffectProtocol.gainQ22(0), 0, "flat EQ");
        check(EffectProtocol.gainQ22(100), 4194304, "+1 dB");
        check(EffectProtocol.gainQ22(-100), -4194304, "-1 dB");
        check(EffectProtocol.gainQ22(1), 41943, "fractional dB");
        check(EffectProtocol.gainQ22(-1), -41944, "negative rounding");
        check(EffectProtocol.gainQ22(1200), 50331648, "+12 dB without saturation");
        check(EffectProtocol.gainQ22(-1200), -50331648, "-12 dB without saturation");
        check(EffectProtocol.gainQ22(Integer.MAX_VALUE), 50331648, "clamp corrupt gain");
    }
    private static void check(int actual, int expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
