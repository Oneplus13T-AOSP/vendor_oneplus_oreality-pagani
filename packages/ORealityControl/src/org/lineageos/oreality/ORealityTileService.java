/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.oplus.oreality.audio.R;

/** Port extension: stock's Dolby-only tile cannot control AudioX on pagani. */
public final class ORealityTileService extends TileService {
    private EffectService mService;
    private boolean mBound, mListening, mPendingToggle;
    private final Runnable mRefresh = this::update;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((EffectService.LocalBinder) binder).getService();
            mService.addListener(mRefresh);
            if (mPendingToggle) { mPendingToggle = false; toggle(); }
            update();
            if (!mListening) disconnect();
        }
        @Override public void onServiceDisconnected(ComponentName name) { mService = null; update(); }
    };
    @Override public void onStartListening() { mListening = true; connect(); update(); }
    @Override public void onStopListening() { mListening = false; if (!mPendingToggle) disconnect(); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }
    @Override public void onClick() {
        unlockAndRun(() -> {
            if (mService != null) toggle();
            else { mPendingToggle = !mPendingToggle; connect(); }
        });
    }
    private void connect() {
        if (mBound) return;
        EffectService.start(this);
        mBound = bindService(new Intent(this, EffectService.class), mConnection, BIND_AUTO_CREATE);
    }
    private void disconnect() {
        if (mService != null) mService.removeListener(mRefresh);
        mService = null;
        if (mBound) unbindService(mConnection);
        mBound = false;
    }
    private void toggle() {
        if (mService != null && mService.isRealityPresent()) {
            mService.setRealityEnabled(!mService.isRealityEnabled());
        }
        update();
    }
    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean present = mService != null && mService.isRealityPresent();
        tile.setState(!present ? Tile.STATE_UNAVAILABLE :
                (mService.isRealityEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE));
        tile.setLabel(getString(R.string.oreality_title));
        tile.setContentDescription(getString(R.string.oreality_title));
        tile.updateTile();
    }
}
