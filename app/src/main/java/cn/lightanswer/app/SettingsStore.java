package cn.lightanswer.app;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    final SharedPreferences prefs;
    SettingsStore(Context context) { prefs = context.getSharedPreferences("answer_settings", Context.MODE_PRIVATE); }
    boolean enabled() { return prefs.getBoolean("enabled", true); }
    boolean speaker() { return prefs.getBoolean("speaker", false); }
    int count() { return prefs.getInt("count", 0); }
    void set(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    void increment() { prefs.edit().putInt("count", count() + 1).apply(); }
}
