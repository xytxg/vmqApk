package com.vone.vmq;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.vone.qrcode.R;
import com.vone.vmq.core.ApiClient;
import com.vone.vmq.core.Protocol;
import com.vone.vmq.core.ServerConfig;
import com.vone.vmq.data.ConfigStore;
import com.vone.vmq.data.MonitorState;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;

public class MainActivity extends AppCompatActivity {
    private ConfigStore configs;
    private MonitorState monitor;
    private TextView host, status, history;
    private Call request;
    private int requestGeneration;
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private boolean decoding;
    private final SharedPreferences.OnSharedPreferenceChangeListener stateChanged = (prefs, key) -> refresh();
    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) confirmConfig(result.getContents());
    });
    private final ActivityResultLauncher<String> gallery = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) decodeImage(uri);
    });
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) postTest(); else toast("发送测试通知需要通知权限；收款监听权限需单独开启");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars((getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        configs = new ConfigStore(this);
        monitor = new MonitorState(this);
        host = findViewById(R.id.txt_host);
        status = findViewById(R.id.txt_status);
        history = findViewById(R.id.txt_history);
        findViewById(R.id.btn_qrcode).setOnClickListener(v -> scanner.launch(new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("扫描 V免签后台配置二维码")
                .setBeepEnabled(false).setOrientationLocked(false)));
        findViewById(R.id.btn_gallery).setOnClickListener(v -> {
            if (!decoding) {
                try { gallery.launch("image/*"); }
                catch (ActivityNotFoundException error) { toast("没有可用的图片选择器，请使用扫码或手动配置"); }
            }
        });
        findViewById(R.id.btn_input).setOnClickListener(v -> manualInput());
        findViewById(R.id.btn_start).setOnClickListener(v -> testHeartbeat());
        findViewById(R.id.btn_checkpush).setOnClickListener(v -> testListener());
        findViewById(R.id.btn_access).setOnClickListener(v -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        findViewById(R.id.btn_battery).setOnClickListener(v -> openSettings(Build.VERSION.SDK_INT >= 23
                ? Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS : Settings.ACTION_SETTINGS));
        findViewById(R.id.btn_clear).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清除配置？").setMessage("清除后停止新的心跳和收款上报，已经发出的请求可能仍会完成。")
                .setNegativeButton("取消", null).setPositiveButton("清除", (dialog, which) -> {
                    cancelRequest(); configs.clear(); refresh();
                }).show());
        refresh();
    }
    @Override protected void onStart() {
        super.onStart();
        getSharedPreferences("monitor", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(stateChanged);
    }
    @Override protected void onResume() { super.onResume(); if (configs != null) refresh(); }
    @Override protected void onStop() {
        getSharedPreferences("monitor", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(stateChanged);
        super.onStop();
    }
    private void refresh() {
        ServerConfig config = configs.load();
        host.setText(config == null ? "尚未配置服务器" : config.baseUrl.toString()
                + "\n通讯密钥：已保存 ········\n" + (config.baseUrl.isHttps() ? "HTTPS 加密连接" : "HTTP 明文连接，建议改用 HTTPS"));
        status.setText(NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())
                ? "通知使用权已开启 · 可检测监听" : "通知使用权未开启");
        history.setText(monitor.summary());
    }
    private void manualInput() {
        EditText input = new EditText(this);
        input.setHint("https://服务器/路径/通讯密钥");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("输入后台配置数据").setView(input)
                .setNegativeButton("取消", null).setPositiveButton("下一步", (dialog, which) -> confirmConfig(input.getText().toString())).show();
    }
    private void confirmConfig(String value) {
        try {
            ServerConfig config = ServerConfig.parse(value);
            new AlertDialog.Builder(this).setTitle("确认连接服务器")
                    .setMessage(config.baseUrl.toString() + "\n\n" + (config.baseUrl.isHttps()
                            ? "验证成功后保存配置。" : "此配置使用 HTTP 明文传输，建议在后台启用 HTTPS。是否继续验证？"))
                    .setNegativeButton("取消", null).setPositiveButton("验证并保存", (dialog, which) -> check(config, true)).show();
        } catch (IllegalArgumentException error) { toast(error.getMessage()); }
    }
    private void testHeartbeat() {
        ServerConfig config = configs.load();
        if (config == null) { toast("请先扫码或手动配置"); return; }
        check(config, false);
    }
    private void cancelRequest() { requestGeneration++; if (request != null) request.cancel(); }
    private void check(ServerConfig config, boolean save) {
        cancelRequest();
        int generation = requestGeneration;
        toast("正在验证服务器…");
        request = ApiClient.get(Protocol.heartbeat(config, System.currentTimeMillis()), (ok, message) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || generation != requestGeneration) return;
            if (ok && save) configs.save(config);
            monitor.record("heart", message);
            refresh(); toast(ok && save ? "验证成功，配置已保存" : message);
        }));
    }
    private void testListener() {
        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {
            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); return;
        }
        if (Build.VERSION.SDK_INT >= 24) NotificationListenerService.requestRebind(new ComponentName(this, NeNotificationService2.class));
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        else postTest();
    }
    private void postTest() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) { toast("请先允许发送测试通知"); return; }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("listener_test", "监听检测", NotificationManager.IMPORTANCE_DEFAULT));
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) { toast("请在系统设置中允许本应用发送通知"); return; }
        monitor.record("test", "测试通知已发出，等待监听回执");
        manager.notify(1001, new NotificationCompat.Builder(this, "listener_test").setSmallIcon(R.drawable.ic_monitor)
                .setContentTitle("V免签监听检测").setContentText(NeNotificationService2.TEST_TEXT).setAutoCancel(true).build());
    }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (ActivityNotFoundException error) { toast("请手动前往系统设置，开启通知使用权并允许后台运行"); }
    }
    private void decodeImage(Uri uri) {
        decoding = true;
        toast("正在识别图片…");
        images.execute(() -> {
            String value = null;
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
                if (options.outWidth < 1 || options.outHeight < 1) throw new IllegalArgumentException();
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 1600 || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                try (InputStream input = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
                if (bitmap == null) throw new IllegalArgumentException();
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                value = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)))).getText();
            } catch (Exception error) { /* Expected for invalid images or a revoked document permission. */ }
            finally { if (bitmap != null) bitmap.recycle(); }
            String result = value;
            runOnUiThread(() -> {
                decoding = false;
                if (isFinishing() || isDestroyed()) return;
                if (result == null) toast("未识别到二维码，请选取清晰完整的配置二维码"); else confirmConfig(result);
            });
        });
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { cancelRequest(); images.shutdownNow(); super.onDestroy(); }
}
