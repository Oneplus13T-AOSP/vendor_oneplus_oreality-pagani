/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.oreality;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.preference.PreferenceActivity;

/** Refresh only after asynchronous service creation has completed. */
@SuppressWarnings("deprecation")
abstract class EffectPreferenceActivity extends PreferenceActivity {
    private boolean mBound;
    private EffectService mConnectedService;
    private final Runnable mRefresh = this::refresh;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            mConnectedService = ((EffectService.LocalBinder) binder).getService();
            mConnectedService.addListener(mRefresh);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            mConnectedService = null;
            refresh();
        }
    };

    @Override protected void onStart() {
        super.onStart();
        EffectService.start(this);
        mBound = bindService(new Intent(this, EffectService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override protected void onStop() {
        if (mConnectedService != null) mConnectedService.removeListener(mRefresh);
        mConnectedService = null;
        if (mBound) unbindService(mConnection);
        mBound = false;
        super.onStop();
    }

    abstract void refresh();
}
