package com.vone.vmq.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.util.Date;

/** Stores only operational status, never notification bodies, amounts or secrets. */
public final class MonitorState {
    private final SharedPreferences prefs;
    public MonitorState(Context context) { prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE); }
    public void record(String category, String message) {
        prefs.edit().putString(category, DateFormat.getTimeInstance().format(new Date()) + "  " + message).apply();
    }
    public String summary() {
        return "心跳：" + prefs.getString("heart", "尚未检测") + "\n\n收款上报："
                + prefs.getString("payment", "尚无记录") + "\n\n监听检测：" + prefs.getString("test", "尚未检测");
    }
}
