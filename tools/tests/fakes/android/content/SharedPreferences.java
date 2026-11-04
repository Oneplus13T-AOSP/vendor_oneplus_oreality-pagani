/* SPDX-License-Identifier: Apache-2.0 */
package android.content;
public interface SharedPreferences {
    int getInt(String key, int fallback);
    boolean getBoolean(String key, boolean fallback);
    String getString(String key, String fallback);
    Editor edit();
    interface Editor {
        Editor putInt(String key, int value);
        Editor putBoolean(String key, boolean value);
        Editor putString(String key, String value);
        void apply();
    }
}
