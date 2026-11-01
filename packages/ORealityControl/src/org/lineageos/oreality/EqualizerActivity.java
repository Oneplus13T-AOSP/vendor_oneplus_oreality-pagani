/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import com.oplus.oreality.audio.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Ten-band graphic equalizer, matching the band count the AudioX engine accepts.
 *
 * <p>Gains are stored in 1/100 dB steps and converted to Q22 dB at the effect, so a
 * value of 300 is +3.00 dB. The range matches the stock OPlus equalizer.
 *
 * <p>The layout is built in code so this port does not need to carry a copy of
 * the stock COUI resources, which are not available to a platform app.
 */
public final class EqualizerActivity extends Activity {
    /** Engine range, in millibels: the stock OPlus equalizer uses +/-12 dB. */
    static final int MIN_GAIN = -1200;
    static final int MAX_GAIN = 1200;
    private static final String[] BANDS = {
            "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
            "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"};

    private final SeekBar[] mBars = new SeekBar[BANDS.length];
    private final TextView[] mLabels = new TextView[BANDS.length];
    private final int[] mGains = new int[AudioXEffect.EQ_BANDS];
    private boolean mBinding;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.oreality_equalizer_bands);
        EffectService.start(this);

        int[] stored = new Prefs(this).getEqualizerGains();
        EffectService service = EffectService.peek();
        if (service != null) {
            stored = service.getEqualizerGains();
        }
        System.arraycopy(stored, 0, mGains, 0, mGains.length);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);

        TextView help = new TextView(this);
        help.setText(R.string.oreality_equalizer_help);
        help.setPadding(0, 0, 0, pad);
        content.addView(help);

        for (int band = 0; band < BANDS.length; band++) {
            content.addView(createBandRow(band));
        }

        TextView reset = new TextView(this);
        reset.setText(R.string.oreality_equalizer_reset);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, pad, 0, 0);
        reset.setOnClickListener(v -> {
            for (int band = 0; band < mGains.length; band++) {
                mBars[band].setProgress(0 - MIN_GAIN);
            }
            commit();
        });
        content.addView(reset);

        setContentView(scroll);
        applyGains();
    }

    private LinearLayout createBandRow(int band) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView frequency = new TextView(this);
        frequency.setText(BANDS[band]);
        frequency.setMinWidth(dp(72));
        row.addView(frequency);

        SeekBar bar = new SeekBar(this);
        bar.setMax(MAX_GAIN - MIN_GAIN);
        bar.setProgress(mGains[band] - MIN_GAIN);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mGains[band] = progress + MIN_GAIN;
                updateLabel(band);
                if (fromUser) {
                    commit();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setMinWidth(dp(72));
        value.setGravity(Gravity.END);
        mLabels[band] = value;
        row.addView(value);

        mBars[band] = bar;
        updateLabel(band);
        return row;
    }

    private void updateLabel(int band) {
        if (mLabels[band] != null) {
            mLabels[band].setText(String.format(java.util.Locale.US, "%+.2f dB",
                    mGains[band] / 100.0f));
        }
    }

    private void applyGains() {
        mBinding = true;
        for (int band = 0; band < mBars.length; band++) {
            mBars[band].setProgress(mGains[band] - MIN_GAIN);
            updateLabel(band);
        }
        mBinding = false;
    }

    private void commit() {
        if (mBinding) {
            return;
        }
        EffectService service = EffectService.peek();
        if (service == null) {
            EffectService.start(this);
            service = EffectService.peek();
        }
        if (service != null) {
            service.setEqualizerGains(mGains.clone());
        } else {
            new Prefs(this).setEqualizerGains(mGains.clone());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
