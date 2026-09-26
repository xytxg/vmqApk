package com.vone.vmq.core;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public final class ApiClient {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).build();
    public interface Result { void complete(boolean success, String message); }
    private ApiClient() {}
    public static Call get(HttpUrl url, Result result) {
        Call call = CLIENT.newCall(new Request.Builder().url(url).get().build());
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                result.complete(false, call.isCanceled() ? "请求已取消" : "网络连接失败或超时，请检查网络与服务器");
            }
            @Override public void onResponse(Call call, Response response) {
                boolean success = false;
                String message;
                try (Response closed = response) {
                    if (!closed.isSuccessful()) message = "服务器 HTTP " + closed.code();
                    else {
                        String body = closed.peekBody(16385).string();
                        if (body.length() > 16384) throw new JSONException("Response too large");
                        JSONObject json = new JSONObject(body);
                        success = json.optInt("code", -1) == 1;
                        // Do not display arbitrary server content, which can reflect signed URLs/secrets.
                        message = success ? "服务器验证成功" : "服务器拒绝请求，请检查密钥、手机时间及后台订单状态";
                    }
                } catch (IOException | JSONException error) { message = "服务器返回格式错误，请检查地址及反向代理"; }
                result.complete(success, message);
            }
        });
        return call;
    }
}
