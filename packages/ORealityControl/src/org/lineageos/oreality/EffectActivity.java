/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.oplus.oreality.audio.R;

/** Native AOSP host for the stock layout; does not require the COUI framework. */
abstract class EffectActivity extends Activity {
    protected EffectService mService;
    private boolean mBound;
    private final Runnable mRefresh = this::refresh;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((EffectService.LocalBinder) binder).getService();
            mService.addListener(mRefresh); refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            mService = null; refresh();
        }
    };

    LinearLayout content(int title) {
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = AudioUi.column(this);
        root.setBackgroundColor(AudioUi.color(this, android.R.attr.colorBackground));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        AudioUi.pad(header, 12, 8);
        ImageButton back = new ImageButton(this); back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(
                AudioUi.color(this, android.R.attr.textColorPrimary)));
        back.setContentDescription(getString(R.string.ui_back));
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(AudioUi.dp(this, 48), AudioUi.dp(this, 48)));
        TextView label = AudioUi.text(this, title, 24); label.setTypeface(null, 1);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = AudioUi.column(this); AudioUi.pad(body, 20, 16);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); root.requestApplyInsets(); return body;
    }

    @Override protected void onStart() {
        super.onStart(); EffectService.start(this);
        mBound = bindService(new Intent(this, EffectService.class), mConnection, Context.BIND_AUTO_CREATE);
    }
    @Override protected void onStop() {
        if (mService != null) mService.removeListener(mRefresh);
        mService = null;
        if (mBound) unbindService(mConnection);
        mBound = false; super.onStop();
    }
    abstract void refresh();
}
