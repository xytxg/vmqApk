package com.vone.vmq.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;
import com.vone.vmq.core.Protocol;

/** Claim before send: legacy servers cannot safely retry an ambiguous payment request. */
public final class NotificationDeduplicator {
    private final SharedPreferences prefs;
    public NotificationDeduplicator(Context context) { prefs = context.getSharedPreferences("seen_notifications", Context.MODE_PRIVATE); }
    public synchronized boolean claim(String identity, long now) {
        String key = Protocol.md5(identity);
        long previous = prefs.getLong(key, 0);
        if (previous > 0 && now - previous < 86400000L) return false;
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!(entry.getValue() instanceof Long) || now - (Long) entry.getValue() > 86400000L) editor.remove(entry.getKey());
        }
        if (prefs.getAll().size() >= 2000) {
            String oldest = null; long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Long && (Long) entry.getValue() < oldestTime) {
                    oldest = entry.getKey(); oldestTime = (Long) entry.getValue();
                }
            }
            if (oldest != null) editor.remove(oldest);
        }
        return editor.putLong(key, now).commit();
    }
}
