/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

public final class EffectProtocolTest {
    public static void main(String[] args) {
        // Independent stock vectors: music public id 2 -> slot 1, movie 1 -> 2.
        check(EffectProtocol.audioxProfile(1), 1, "music engine slot");
        check(EffectProtocol.audioxProfile(2), 2, "movie engine slot");
        check(EffectProtocol.audioxProfile(3), 4, "game engine slot");
        check(EffectProtocol.audioxProfile(6), 6, "custom engine slot");
        check(EffectProtocol.spatializerProfile(1), 2, "music stock profile");
        check(EffectProtocol.spatializerProfile(2), 1, "movie stock profile");
        check(EffectProtocol.spatializerProfile(6), 0, "custom stock profile");
        check(EffectProtocol.audioxProfile(-1), -1, "reject negative profile");
        check(EffectProtocol.audioxProfile(7), -1, "reject unknown profile");
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
