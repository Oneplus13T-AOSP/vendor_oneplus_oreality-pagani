/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import com.oplus.oreality.audio.R;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

/**
 * Holo Audio entry screen, shown directly below OReality Audio.
 *
 * <p>Holo Audio is the OPlus MetaAudio engine. This port installs its renderer
 * (the OPlus spatializer effect) and the tuning data, so the master switch and
 * the immersive level are real. Per-app placement additionally needs the
 * MetaAudio service stack, which is not installed; the screen says so instead
 * of showing controls that cannot work.
 */
@SuppressWarnings("deprecation")
public final class HoloAudioSettingsActivity extends PreferenceActivity {
    private SwitchPreference mEnabled;
    private Preference mLevel;
    private Preference mPlacement;
    private Preference mDevice;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.holo_title);
        EffectService.start(this);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(screen);

        mEnabled = new SwitchPreference(this);
        mEnabled.setTitle(R.string.holo_enable);
        mEnabled.setSummary(R.string.holo_enable_summary);
        mEnabled.setPersistent(false);
        mEnabled.setOnPreferenceChangeListener((preference, value) -> {
            EffectService service = EffectService.peek();
            if (service != null && service.isHoloSupported()) {
                service.setHoloEnabled((Boolean) value);
            }
            refresh();
            return false;
        });
        screen.addPreference(mEnabled);

        mLevel = new Preference(this);
        mLevel.setTitle(R.string.holo_level);
        mLevel.setSelectable(false);
        screen.addPreference(mLevel);

        mDevice = new Preference(this);
        mDevice.setTitle(R.string.holo_device);
        mDevice.setSelectable(false);
        screen.addPreference(mDevice);

        mPlacement = new Preference(this);
        mPlacement.setTitle(R.string.holo_placement);
        mPlacement.setSelectable(false);
        screen.addPreference(mPlacement);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EffectService.start(this);
        refresh();
    }

    private void refresh() {
        EffectService service = EffectService.peek();
        if (service == null) {
            return;
        }
        boolean supported = service.isHoloSupported();
        mEnabled.setEnabled(supported);
        mEnabled.setChecked(supported && service.isHoloEnabled());
        mLevel.setSummary(supported
                ? service.describeHoloLevel() : getString(R.string.holo_unsupported));
        mDevice.setSummary(supported && service.isHoloAvailable()
                ? getString(R.string.holo_device_ready)
                : getString(R.string.holo_device_unavailable));
        // Per-app placement requires the MetaAudio service stack.
        mPlacement.setSummary(HoloAudioEffect.isMultiAppSupported()
                ? R.string.holo_placement_pending : R.string.holo_placement_unavailable);
        mPlacement.setEnabled(false);
    }
}
