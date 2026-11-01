/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import com.oplus.oreality.audio.R;

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.widget.Toast;

/**
 * OReality Audio entry screen, launched from Settings &gt; Sound &amp; vibration.
 *
 * <p>Presents the same choices the stock OPlus control surface offers: on/off,
 * the profile (music, movie, game, custom) and the ten-band graphic equalizer
 * that belongs to the custom profile. Enable state is read from the effect;
 * the profile and EQ controls display the persisted selection.
 */
@SuppressWarnings("deprecation")
public final class ORealitySettingsActivity extends EffectPreferenceActivity {
    private SwitchPreference mEnabled;
    private ListPreference mProfile;
    private SwitchPreference mEqualizer;
    private Preference mBands;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.oreality_title);
        EffectService.start(this);

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(screen);

        mEnabled = new SwitchPreference(this);
        mEnabled.setTitle(R.string.oreality_enable);
        mEnabled.setSummary(R.string.oreality_enable_summary);
        mEnabled.setPersistent(false);
        mEnabled.setOnPreferenceChangeListener((preference, value) -> {
            boolean wanted = (Boolean) value;
            if (!mService().setRealityEnabled(wanted)) {
                toast(R.string.oreality_unavailable);
            }
            refresh();
            return false;
        });
        screen.addPreference(mEnabled);

        mProfile = new ListPreference(this);
        mProfile.setTitle(R.string.oreality_profile);
        mProfile.setPersistent(false);
        mProfile.setEntries(new CharSequence[] {
                getString(R.string.profile_intelligence), getString(R.string.profile_music),
                getString(R.string.profile_movie), getString(R.string.profile_game),
                getString(R.string.profile_immersion), getString(R.string.profile_fidelity),
                getString(R.string.profile_custom)});
        mProfile.setEntryValues(new CharSequence[] {
                Integer.toString(AudioXEffect.PROFILE_INTELLIGENCE),
                Integer.toString(AudioXEffect.PROFILE_MUSIC),
                Integer.toString(AudioXEffect.PROFILE_MOVIE),
                Integer.toString(AudioXEffect.PROFILE_GAME),
                Integer.toString(AudioXEffect.PROFILE_IMMERSION),
                Integer.toString(AudioXEffect.PROFILE_FIDELITY),
                Integer.toString(AudioXEffect.PROFILE_CUSTOM)});
        mProfile.setOnPreferenceChangeListener((preference, value) -> {
            try {
                mService().setProfile(Integer.parseInt((String) value));
            } catch (NumberFormatException invalid) {
                toast(R.string.oreality_unavailable);
            }
            refresh();
            return false;
        });
        screen.addPreference(mProfile);

        mEqualizer = new SwitchPreference(this);
        mEqualizer.setTitle(R.string.oreality_equalizer);
        mEqualizer.setSummary(R.string.oreality_equalizer_summary);
        mEqualizer.setPersistent(false);
        mEqualizer.setOnPreferenceChangeListener((preference, value) -> {
            mService().setEqualizerEnabled((Boolean) value);
            refresh();
            return false;
        });
        screen.addPreference(mEqualizer);

        mBands = new Preference(this);
        mBands.setTitle(R.string.oreality_equalizer_bands);
        mBands.setPersistent(false);
        mBands.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(this, EqualizerActivity.class));
            return true;
        });
        screen.addPreference(mBands);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EffectService.start(this);
        refresh();
    }

    private EffectService mService() {
        return EffectService.peek();
    }

    @Override
    void refresh() {
        EffectService service = EffectService.peek();
        if (service == null) {
            mEnabled.setEnabled(false);
            mProfile.setEnabled(false);
            mEqualizer.setEnabled(false);
            mBands.setEnabled(false);
            return;
        }
        boolean present = service.isRealityPresent();
        boolean enabled = service.isRealityEnabled();
        mEnabled.setChecked(enabled);
        mEnabled.setEnabled(present);
        mProfile.setEnabled(present);
        mProfile.setValue(Integer.toString(service.getProfile()));
        mProfile.setSummary(mProfile.getEntry());
        mEqualizer.setChecked(service.isEqualizerEnabled());
        mEqualizer.setEnabled(present);
        mBands.setEnabled(present && service.isEqualizerEnabled());
        mBands.setSummary(buildBandSummary(service.getEqualizerGains()));
    }

    private String buildBandSummary(int[] gains) {
        StringBuilder builder = new StringBuilder();
        for (int band = 0; band < gains.length; band++) {
            if (band > 0) {
                builder.append("  ");
            }
            builder.append(String.format(java.util.Locale.US, "%+.1f", gains[band] / 100.0f));
        }
        return builder.toString();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
