/* SPDX-License-Identifier: Apache-2.0 */
package android.content;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public boolean isDeviceProtectedStorage() { return true; }
    public Context createDeviceProtectedStorageContext() { return this; }
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
}
