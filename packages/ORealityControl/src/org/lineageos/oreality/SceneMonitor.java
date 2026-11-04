/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Handler;
import android.util.Log;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local stock package catalog driven by AOSP's privileged playback callback. */
final class SceneMonitor extends AudioManager.AudioPlaybackCallback {
    private final Context mContext;
    private final AudioManager mAudio;
    private final Runnable mChanged;
    private final Map<String, List<String>> mCatalog = new HashMap<>();
    private int mScene;
    private boolean mHeadphones;
    private static final AudioAttributes MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).build();

    SceneMonitor(Context context, Handler handler, Runnable changed) {
        mContext = context;
        mAudio = context.getSystemService(AudioManager.class);
        mChanged = changed;
        try (InputStream in = context.getAssets().open("multimedia_dolby_support_apps.xml")) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                    event = parser.next()) {
                if (event == XmlPullParser.START_TAG && parser.getName().endsWith("ListApk")) {
                    String tag = parser.getName();
                    mCatalog.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(parser.nextText().trim());
                }
            }
        } catch (Exception error) {
            Log.w("ORealityScenes", "cannot load stock scene catalog; using audio attributes", error);
        }
        mAudio.registerAudioPlaybackCallback(this, handler);
        update();
    }

    void close() { mAudio.unregisterAudioPlaybackCallback(this); }
    int scene() { return mScene; }
    boolean headphones() { return mHeadphones; }

    private boolean matches(String tag, String packageName, boolean exact) {
        for (String prefix : mCatalog.getOrDefault(tag, java.util.Collections.emptyList())) {
            if (exact ? packageName.equals(prefix) : packageName.startsWith(prefix)) return true;
        }
        return false;
    }

    private int packageScene(String name) {
        if (matches("mMusicListApk", name, false)) return StockAudioModes.MUSIC;
        if (matches("mVoiceListApk", name, false)) return StockAudioModes.VOICE;
        if (matches("mVideo2ListApk", name, false)) return StockAudioModes.VIDEO2;
        if (matches("mVideoListApk", name, false)) return StockAudioModes.MOVIE;
        if (matches("mGame1ListApk", name, false)) return StockAudioModes.GAME1;
        if (matches("mGame2ListApk", name, false)) return StockAudioModes.GAME2;
        return StockAudioModes.DEFAULT;
    }

    @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
        int previous = mScene;
        boolean previousRoute = mHeadphones;
        safelyClassify(configs);
        if (previous != mScene || previousRoute != mHeadphones) mChanged.run();
    }

    void update() {
        try { safelyClassify(mAudio.getActivePlaybackConfigurations()); }
        catch (RuntimeException unavailable) { Log.w("ORealityScenes", "playback service unavailable", unavailable); }
    }

    private void safelyClassify(List<AudioPlaybackConfiguration> configs) {
        try { classify(configs); }
        catch (RuntimeException unavailable) { Log.w("ORealityScenes", "cannot classify playback", unavailable); }
    }

    private void classify(List<AudioPlaybackConfiguration> configs) {
        int best = StockAudioModes.DEFAULT;
        mHeadphones = false;
        for (AudioDeviceInfo output : mAudio.getAudioDevicesForAttributes(MEDIA)) {
            int type = output.getType();
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                mHeadphones = true;
            }
        }
        for (AudioPlaybackConfiguration player : configs) {
            if (!player.isActive() || player.getClientUid() == android.os.Process.myUid()) continue;
            AudioAttributes attributes = player.getAudioAttributes();
            int usage = attributes.getUsage();
            if (usage != AudioAttributes.USAGE_MEDIA && usage != AudioAttributes.USAGE_GAME
                    && usage != AudioAttributes.USAGE_UNKNOWN) continue;
            int scene = StockAudioModes.DEFAULT;
            boolean retain = false;
            String[] names = mContext.getPackageManager().getPackagesForUid(player.getClientUid());
            if (names != null) for (String name : names) {
                if (matches("mUseBeforeListApk", name, true)) { retain = true; continue; }
                int candidate = packageScene(name);
                if (StockAudioModes.priority(candidate) > StockAudioModes.priority(scene)) scene = candidate;
                if (scene == StockAudioModes.DEFAULT) try {
                    ApplicationInfo app = mContext.getPackageManager().getApplicationInfo(name, 0);
                    if (app.category == ApplicationInfo.CATEGORY_GAME) scene = StockAudioModes.GAME2;
                    else if (app.category == ApplicationInfo.CATEGORY_AUDIO) scene = StockAudioModes.MUSIC;
                    else if (app.category == ApplicationInfo.CATEGORY_VIDEO) scene = StockAudioModes.MOVIE;
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
            }
            if (retain && scene == StockAudioModes.DEFAULT) scene = mScene;
            if (scene == StockAudioModes.DEFAULT) {
                if (usage == AudioAttributes.USAGE_GAME) scene = StockAudioModes.GAME2;
                else switch (attributes.getContentType()) {
                    case AudioAttributes.CONTENT_TYPE_MUSIC: scene = StockAudioModes.MUSIC; break;
                    case AudioAttributes.CONTENT_TYPE_MOVIE: scene = StockAudioModes.MOVIE; break;
                    case AudioAttributes.CONTENT_TYPE_SPEECH: scene = StockAudioModes.VOICE; break;
                    default: break;
                }
            }
            if (StockAudioModes.priority(scene) > StockAudioModes.priority(best)) best = scene;
        }
        mScene = best;
    }
}
