/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

public final class EffectProtocolTest {
    public static void main(String[] args) {
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
