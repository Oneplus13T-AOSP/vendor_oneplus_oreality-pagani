/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.oplus.oreality.audio.R;

/** pagani's renewed four-category screen with original ColorOS artwork. */
public final class ORealitySettingsActivity extends EffectActivity {
    private static final int[] MODES = {0, 4, 5, 6};
    private static final int[] TITLES = {R.string.profile_intelligence, R.string.profile_immersion,
            R.string.profile_fidelity, R.string.profile_custom};
    private static final int[] DETAILS = {R.string.mode_smart_summary, R.string.mode_immersion_summary,
            R.string.mode_fidelity_summary, R.string.mode_custom_summary};
    private static final int[] IMAGES = {R.drawable.oreality_mode_intelligence_renewal,
            R.drawable.oreality_mode_immersion_renewal, R.drawable.oreality_mode_fidelity_renewal,
            R.drawable.oreality_mode_custom_renewal};
    private final FrameLayout[] mFrames = new FrameLayout[4];
    private final LinearLayout[] mCards = new LinearLayout[4];
    private final TextView[] mChecks = new TextView[4];
    private Switch mEnabled;
    private ImageButton mCustomJump;
    private boolean mRefreshing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = content(R.string.oreality_title);
        mEnabled = new Switch(this); mEnabled.setText(R.string.oreality_enable);
        mEnabled.setTextSize(17); AudioUi.pad(mEnabled, 20, 18); AudioUi.surface(mEnabled);
        mEnabled.setOnCheckedChangeListener((button, enabled) -> {
            if (!mRefreshing && mService != null) {
                if (!mService.setRealityEnabled(enabled)) unavailable(); refresh();
            }
        });
        body.addView(mEnabled, AudioUi.rowParams(this, 22));
        TextView intro = AudioUi.text(this, R.string.oreality_summary, 14);
        intro.setTextColor(AudioUi.secondary(this)); body.addView(intro, AudioUi.rowParams(this, 24));
        TextView label = AudioUi.text(this, R.string.ui_select_mode, 16);
        label.setTypeface(null, 1); body.addView(label, AudioUi.rowParams(this, 14));
        for (int row = 0; row < 2; row++) {
            LinearLayout pair = new LinearLayout(this);
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                LinearLayout card = card(index); mCards[index] = card;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
                if (column == 0) p.setMarginEnd(AudioUi.dp(this, 12));
                pair.addView(card, p);
            }
            body.addView(pair, AudioUi.rowParams(this, 18));
        }
        refresh();
    }

    private LinearLayout card(int index) {
        LinearLayout outer = AudioUi.column(this);
        outer.setFocusable(true); outer.setClickable(true);
        outer.setContentDescription(getString(TITLES[index]) + ". " + getString(DETAILS[index]));
        outer.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.RadioButton"); info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });
        FrameLayout frame = new FrameLayout(this); mFrames[index] = frame;
        frame.setPadding(AudioUi.dp(this, 3), AudioUi.dp(this, 3), AudioUi.dp(this, 3), AudioUi.dp(this, 3));
        frame.setClipToOutline(true); frame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        ImageView art = new ImageView(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(width * 759f / 1200f), MeasureSpec.EXACTLY));
            }
        };
        art.setImageResource(IMAGES[index]); art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(art, new FrameLayout.LayoutParams(-1, -2));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x99000000, Color.TRANSPARENT}));
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(-1, AudioUi.dp(this, 54), Gravity.BOTTOM);
        frame.addView(shade, shadeParams);
        TextView title = AudioUi.text(this, TITLES[index], 12); title.setTextColor(Color.WHITE);
        AudioUi.pad(title, 12, 10);
        frame.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        TextView check = AudioUi.text(this, R.string.ui_selected_check, 14); mChecks[index] = check;
        check.setGravity(Gravity.CENTER); check.setTextColor(Color.WHITE);
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        check.setBackground(AudioUi.rounded(this, AudioUi.accent(this), Color.TRANSPARENT));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(AudioUi.dp(this, 24), AudioUi.dp(this, 24), Gravity.TOP | Gravity.END);
        cp.setMargins(0, AudioUi.dp(this, 9), AudioUi.dp(this, 9), 0); frame.addView(check, cp);
        if (MODES[index] == StockAudioModes.CUSTOM) {
            mCustomJump = new ImageButton(this); mCustomJump.setImageResource(R.drawable.ic_tune);
            mCustomJump.setContentDescription(getString(R.string.oreality_equalizer_bands));
            mCustomJump.setBackgroundColor(Color.TRANSPARENT);
            FrameLayout.LayoutParams jump = new FrameLayout.LayoutParams(AudioUi.dp(this, 44),
                    AudioUi.dp(this, 44), Gravity.BOTTOM | Gravity.END);
            frame.addView(mCustomJump, jump);
            mCustomJump.setOnClickListener(v -> {
                if (mService != null && mService.isRealityEnabled()) {
                    if (!mService.setProfile(StockAudioModes.CUSTOM)) { unavailable(); return; }
                    startActivity(new Intent(this, EqualizerActivity.class));
                }
            });
        }
        outer.addView(frame, new LinearLayout.LayoutParams(-1, -2));
        TextView detail = AudioUi.text(this, DETAILS[index], 12); detail.setTextColor(AudioUi.secondary(this));
        detail.setPadding(AudioUi.dp(this, 3), AudioUi.dp(this, 8), 0, 0); outer.addView(detail);
        outer.setOnClickListener(v -> {
            if (mService != null) { if (!mService.setProfile(MODES[index])) unavailable(); refresh(); }
        });
        return outer;
    }

    private void unavailable() { Toast.makeText(this, R.string.oreality_unavailable, Toast.LENGTH_SHORT).show(); }

    @Override void refresh() {
        if (mEnabled == null) return;
        boolean present = mService != null && mService.isRealityPresent();
        boolean enabled = present && mService.isRealityEnabled();
        int mode = mService == null ? new Prefs(this).getProfile() : mService.getProfile();
        mRefreshing = true; mEnabled.setChecked(enabled); mEnabled.setEnabled(present); mRefreshing = false;
        for (int i = 0; i < MODES.length; i++) {
            boolean selected = mode == MODES[i];
            mFrames[i].setBackground(AudioUi.rounded(this, AudioUi.surface(this),
                    selected ? AudioUi.accent(this) : Color.TRANSPARENT));
            mChecks[i].setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            mCards[i].setSelected(selected); mCards[i].setEnabled(enabled);
            mCards[i].setAlpha(enabled ? 1f : .45f);
        }
        mCustomJump.setEnabled(enabled);
    }
}
