package com.vone.vmq;

import android.app.Notification;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.vone.vmq.core.ApiClient;
import com.vone.vmq.core.PaymentParser;
import com.vone.vmq.core.Protocol;
import com.vone.vmq.core.ServerConfig;
import com.vone.vmq.data.ConfigStore;
import com.vone.vmq.data.MonitorState;
import com.vone.vmq.data.NotificationDeduplicator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** System-bound listener. Background work has bounded concurrency and a lifecycle. */
public class NeNotificationService2 extends NotificationListenerService {
    public static final String TEST_TEXT = "V免签通知监听测试";
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean heartBusy = new AtomicBoolean();
    private final Semaphore pending = new Semaphore(16);
    private ScheduledFuture<?> heartbeat;
    private ConfigStore configs;
    private MonitorState state;
    private NotificationDeduplicator deduplicator;
    private volatile boolean destroyed;
    private volatile boolean connected;

    @Override public void onCreate() {
        super.onCreate();
        configs = new ConfigStore(this);
        state = new MonitorState(this);
        deduplicator = new NotificationDeduplicator(this);
    }
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        if (heartbeat == null || heartbeat.isCancelled()) {
            heartbeat = worker.scheduleWithFixedDelay(this::sendHeartbeat, 0, 30, TimeUnit.SECONDS);
        }
    }
    private void sendHeartbeat() {
        ServerConfig config = configs.load();
        if (destroyed || !connected || config == null || !heartBusy.compareAndSet(false, true)) return;
        ApiClient.get(Protocol.heartbeat(config, System.currentTimeMillis()), (ok, message) -> {
            heartBusy.set(false);
            state.record("heart", message);
        });
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (destroyed || sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!pkg.equals(getPackageName()) && !pkg.equals("com.tencent.mm")
                && !pkg.equals("com.eg.android.AlipayGphone")) return;
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;
        Bundle extras = notification.extras;
        String title = text(extras, Notification.EXTRA_TITLE);
        String content = text(extras, Notification.EXTRA_TEXT);
        if (pkg.equals(getPackageName())) {
            if (TEST_TEXT.equals(content)) state.record("test", "监听正常");
            return;
        }
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        PaymentParser.Payment payment = PaymentParser.parse(pkg, title, content);
        if (payment == null) {
            content = text(extras, Notification.EXTRA_BIG_TEXT);
            payment = PaymentParser.parse(pkg, title, content);
        }
        if (payment == null || configs.load() == null) return;
        if (!pending.tryAcquire()) {
            state.record("payment", "待处理通知过多，请核对服务端订单");
            return;
        }
        final PaymentParser.Payment parsed = payment;
        final String identity = sbn.getKey() + "|" + sbn.getPostTime() + "|" + title + "|" + content;
        worker.execute(() -> {
            ServerConfig config = configs.load();
            if (destroyed || config == null || !deduplicator.claim(identity, System.currentTimeMillis())) {
                pending.release();
                return;
            }
            ApiClient.get(Protocol.payment(config, parsed, System.currentTimeMillis()), (ok, message) -> {
                pending.release();
                state.record("payment", ok ? "上报成功" : message + "；请核对订单，未自动重发");
            });
        });
    }
    private static String text(Bundle extras, String key) {
        Object value = extras.get(key);
        return value instanceof CharSequence ? value.toString() : "";
    }
    @Override public void onListenerDisconnected() {
        connected = false;
        if (heartbeat != null) heartbeat.cancel(false);
        if (Build.VERSION.SDK_INT >= 24) requestRebind(new ComponentName(this, getClass()));
        super.onListenerDisconnected();
    }
    @Override public void onDestroy() {
        destroyed = true;
        if (heartbeat != null) heartbeat.cancel(false);
        worker.shutdownNow();
        super.onDestroy();
    }
}
