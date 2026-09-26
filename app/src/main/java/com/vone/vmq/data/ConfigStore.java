package com.vone.vmq.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.vone.vmq.core.ServerConfig;

public final class ConfigStore {
    private final SharedPreferences preferences;
    public ConfigStore(Context context) { preferences = context.getSharedPreferences("vone", Context.MODE_PRIVATE); }
    public ServerConfig load() {
        try { return ServerConfig.of(preferences.getString("host", ""), preferences.getString("key", "")); }
        catch (IllegalArgumentException error) { return null; }
    }
    public void save(ServerConfig config) {
        preferences.edit().putString("host", config.baseUrl.toString()).putString("key", config.key).apply();
    }
    public void clear() { preferences.edit().remove("host").remove("key").apply(); }
}
