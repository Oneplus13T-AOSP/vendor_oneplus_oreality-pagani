/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.oplus.oreality.audio.R;

/** Stock renewed EQ: eight presets, route-specific custom memory and ten vertical bands. */
public final class EqualizerActivity extends EffectActivity {
    private TextView mRoute, mPresetName, mReset;
    private LinearLayout mPreset;
    private EqualizerBands mBands;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = content(R.string.oreality_equalizer_bands);
        mRoute = AudioUi.text(this, R.string.eq_speaker, 14);
        mRoute.setTextColor(AudioUi.secondary(this)); body.addView(mRoute, AudioUi.rowParams(this, 18));
        mPreset = new LinearLayout(this); mPreset.setGravity(Gravity.CENTER_VERTICAL);
        AudioUi.pad(mPreset, 18, 18); AudioUi.surface(mPreset); mPreset.setFocusable(true);
        mPreset.addView(AudioUi.text(this, R.string.eq_preset, 17), new LinearLayout.LayoutParams(0, -2, 1));
        mPresetName = AudioUi.text(this, R.string.profile_custom, 15);
        mPresetName.setTextColor(AudioUi.accent(this)); mPreset.addView(mPresetName);
        mPreset.setOnClickListener(v -> choosePreset()); body.addView(mPreset, AudioUi.rowParams(this, 28));
        LinearLayout caption = new LinearLayout(this); caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.addView(AudioUi.text(this, R.string.eq_bands_title, 15), new LinearLayout.LayoutParams(0, -2, 1));
        mReset = AudioUi.text(this, R.string.oreality_equalizer_reset, 14);
        mReset.setTextColor(AudioUi.accent(this)); AudioUi.pad(mReset, 8, 12);
        mReset.setOnClickListener(v -> {
            if (mService != null && !mService.setEqualizerGains(new int[10])) unavailable();
        });
        caption.addView(mReset); body.addView(caption, AudioUi.rowParams(this, 8));
        mBands = new EqualizerBands(this, gains -> {
            if (mService != null && !mService.setEqualizerGains(gains)) unavailable();
        });
        body.addView(mBands, AudioUi.rowParams(this, 20));
        TextView help = AudioUi.text(this, R.string.eq_help_stock, 13);
        help.setTextColor(AudioUi.secondary(this)); body.addView(help);
        refresh();
    }

    private void choosePreset() {
        if (mService == null) return;
        String[] names = getResources().getStringArray(R.array.eq_preset_labels);
        // Stock menu order: seven named presets, then Custom (id 0).
        String[] menu = new String[8];
        for (int i = 0; i < 7; i++) menu[i] = names[i + 1];
        menu[7] = names[0];
        int selected = mService.getEqualizerPreset();
        new AlertDialog.Builder(this).setTitle(R.string.eq_preset)
                .setSingleChoiceItems(menu, selected == 0 ? 7 : selected - 1, (dialog, which) -> {
                    if (mService != null && !mService.setEqualizerPreset(which == 7 ? 0 : which + 1)) unavailable();
                    dialog.dismiss(); refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void unavailable() { Toast.makeText(this, R.string.oreality_unavailable, Toast.LENGTH_SHORT).show(); }

    @Override void refresh() {
        if (mBands == null) return;
        boolean ready = mService != null && mService.isRealityPresent() && mService.isRealityEnabled()
                && mService.getProfile() == StockAudioModes.CUSTOM;
        mPreset.setEnabled(ready); mPreset.setAlpha(ready ? 1f : .45f);
        int preset = mService == null ? 0 : mService.getEqualizerPreset();
        mPresetName.setText(getResources().getStringArray(R.array.eq_preset_labels)[preset]);
        mRoute.setText(mService != null && mService.isHeadphoneRoute() ? R.string.eq_headphones : R.string.eq_speaker);
        int[] gains = mService == null ? new int[10] : mService.getEqualizerGains();
        mBands.setGains(gains, ready);
        boolean nonzero = false;
        for (int gain : gains) nonzero |= gain != 0;
        mReset.setEnabled(ready && preset == 0 && nonzero);
        mReset.setAlpha(mReset.isEnabled() ? 1f : .4f);
    }
}
