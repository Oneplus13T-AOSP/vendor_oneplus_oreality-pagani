/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public final class PrefsTest {
    static final class Memory implements SharedPreferences, SharedPreferences.Editor {
        final Map<String,Object> values = new HashMap<>();
        public int getInt(String k,int d) { return (Integer) values.getOrDefault(k,d); }
        public boolean getBoolean(String k,boolean d) { return (Boolean) values.getOrDefault(k,d); }
        public String getString(String k,String d) { return (String) values.getOrDefault(k,d); }
        public Editor edit() { return this; }
        public Editor putInt(String k,int v) { values.put(k,v); return this; }
        public Editor putBoolean(String k,boolean v) { values.put(k,v); return this; }
        public Editor putString(String k,String v) { values.put(k,v); return this; }
        public void apply() {}
    }
    public static void main(String[] args) {
        Memory memory = new Memory();
        Context context = new Context() {
            public SharedPreferences getSharedPreferences(String name,int mode) { return memory; }
        };
        Prefs prefs = new Prefs(context);
        check(prefs.getProfile(),0,"fresh Smart");
        memory.putInt("oreality_profile",1); check(prefs.getProfile(),5,"legacy mode migration");
        memory.putString("eq_gains","1200,0,0,0,0,0,0,0,0,-1200");
        check(prefs.getEqualizerGains(false)[0],600,"legacy range migration");
        check(prefs.getEqualizerGains(true)[9],-600,"headphone legacy fallback");
        int[] custom = new int[10]; custom[0]=400;
        prefs.setEqualizerGains(false,custom);
        prefs.setEqualizerPreset(false,6);
        check(prefs.getEqualizerGains(false)[0],600,"selected preset applies");
        prefs.setEqualizerPreset(false,0);
        check(prefs.getEqualizerGains(false)[0],400,"preset selection preserves custom memory");
        custom[0]=-200; prefs.setEqualizerGains(true,custom);
        check(prefs.getEqualizerGains(false)[0],400,"speaker memory isolated");
        check(prefs.getEqualizerGains(true)[0],-200,"headphone custom saved");
        prefs.setEqualizerPreset(true,2); prefs.setEqualizerGains(true,custom);
        check(prefs.getEqualizerPreset(true),0,"editing a preset switches to Custom");
        prefs.setEqualizerPreset(false,999);
        check(prefs.getEqualizerPreset(false),0,"invalid preset rejected");
    }
    static void check(int a,int b,String label) { if(a!=b)throw new AssertionError(label+": "+a+" != "+b); }
}
