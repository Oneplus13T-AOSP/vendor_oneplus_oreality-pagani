#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the real QS service with deferred binding and SystemUI-like caching."""
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
STUBS = {
    'android/content/ComponentName.java': 'package android.content; public class ComponentName {}',
    'android/content/Intent.java': '''package android.content;
        public class Intent { public Intent(Object context, Class<?> cls) {} }''',
    'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
    'android/content/ServiceConnection.java': '''package android.content;
        public interface ServiceConnection {
            void onServiceConnected(ComponentName name, android.os.IBinder binder);
            void onServiceDisconnected(ComponentName name);
        }''',
    'android/service/quicksettings/Tile.java': '''package android.service.quicksettings;
        public class Tile {
            public static final int STATE_UNAVAILABLE=0, STATE_INACTIVE=1, STATE_ACTIVE=2;
            public int state=STATE_INACTIVE, updates;
            public void setState(int state) { this.state=state; }
            public void setLabel(String label) {}
            public void setContentDescription(String label) {}
            public void updateTile() { updates++; }
        }''',
    'android/service/quicksettings/TileService.java': '''package android.service.quicksettings;
        import android.content.*;
        public class TileService {
            public static final int BIND_AUTO_CREATE=1;
            public final Tile tile = new Tile();
            public ServiceConnection connection;
            public boolean locked;
            public int unlockRequests, binds, unbinds;
            public Runnable afterUnlock;
            public Tile getQsTile() { return tile; }
            public String getString(int id) { return "OReality"; }
            public boolean isLocked() { return locked; }
            public void unlockAndRun(Runnable action) { unlockRequests++; afterUnlock=action; }
            public boolean bindService(Intent i, ServiceConnection c, int flags) {
                connection=c; binds++; return true;
            }
            public void unbindService(ServiceConnection c) { unbinds++; connection=null; }
            public void onStartListening() {} public void onStopListening() {}
            public void onDestroy() {} public void onClick() {}
        }''',
    'com/oplus/oreality/audio/R.java': '''package com.oplus.oreality.audio;
        public class R { public static class string { public static int oreality_title=1; } }''',
    'org/lineageos/oreality/EffectService.java': '''package org.lineageos.oreality;
        import java.util.*;
        final class EffectService {
            static EffectService instance;
            boolean present=true, enabled=true;
            int writes;
            final List<Runnable> listeners = new ArrayList<>();
            static EffectService peek() { return instance; }
            static void start(Object context) {}
            void addListener(Runnable r) { listeners.add(r); }
            void removeListener(Runnable r) { listeners.remove(r); }
            boolean isRealityPresent() { return present; }
            boolean isRealityEnabled() { return enabled; }
            boolean setRealityEnabled(boolean value) {
                enabled=value; writes++;
                for (Runnable r : new ArrayList<>(listeners)) r.run();
                return true;
            }
            final class LocalBinder implements android.os.IBinder {
                EffectService getService() { return EffectService.this; }
            }
        }''',
    'org/lineageos/oreality/TileTest.java': '''package org.lineageos.oreality;
        public class TileTest {
            static void check(boolean ok, String why) {
                if (!ok) throw new AssertionError(why);
            }
            public static void main(String[] args) {
                // Cold start: an asynchronous bind must not cache unavailable.
                EffectService.instance=null;
                ORealityTileService cold=new ORealityTileService();
                cold.onStartListening();
                check(cold.tile.updates==0, "binding published a false unavailable state");
                cold.onClick(); // Arrives before the connection callback.
                EffectService owner=new EffectService();
                EffectService.instance=owner;
                cold.connection.onServiceConnected(null, owner.new LocalBinder());
                check(owner.writes==1 && !owner.enabled, "queued click was lost");
                check(cold.tile.state==1, "off must stay clickable");
                check(cold.unlockRequests==0, "unlocked click waited for keyguard");
                cold.onStopListening();
                check(owner.listeners.isEmpty() && cold.unbinds==1, "listener/binding leaked");

                // A new active-tile listening session starts without a local binding.
                ORealityTileService warm=new ORealityTileService();
                warm.onStartListening();
                check(warm.tile.state==1 && warm.tile.updates>0,
                        "persistent owner was reported unavailable before bind");
                warm.onClick();
                check(owner.enabled && warm.tile.state==2, "warm enable failed");
                warm.onClick();
                check(!owner.enabled && warm.tile.state==1, "warm disable failed");

                // Locked clicks wait for unlock and cannot change the effect early.
                warm.locked=true;
                int writes=owner.writes;
                warm.onClick();
                check(warm.unlockRequests==1 && owner.writes==writes, "lock was bypassed");
                warm.afterUnlock.run();
                check(owner.enabled && owner.writes==writes+1, "unlock click lost/duplicated");
                owner.present=false;
                warm.onStartListening();
                check(warm.tile.state==0, "missing engine must still be unavailable");
                warm.onDestroy();
            }
        }''',
}


class TileLifecycleTest(unittest.TestCase):
    def test_binding_toggle_and_keyguard(self):
        with tempfile.TemporaryDirectory(prefix='oreality-tile-') as directory:
            root = Path(directory)
            sources = []
            for name, source in STUBS.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                sources.append(str(path))
            sources.append(str(REPO / 'packages/ORealityControl/src/org/lineageos/oreality/ORealityTileService.java'))
            subprocess.run(['javac', '-d', directory, *sources], check=True)
            subprocess.run(['java', '-cp', directory, 'org.lineageos.oreality.TileTest'], check=True)


if __name__ == '__main__':
    unittest.main()
