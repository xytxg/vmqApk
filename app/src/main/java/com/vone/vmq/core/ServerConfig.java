package com.vone.vmq.core;

import okhttp3.HttpUrl;

/** Immutable validated configuration; never include the secret in toString/log output. */
public final class ServerConfig {
    public final HttpUrl baseUrl;
    public final String key;

    private ServerConfig(HttpUrl url, String key) { this.baseUrl = url; this.key = key; }

    public static ServerConfig parse(String input) {
        if (input == null || input.length() > 4096) throw invalid();
        String value = input.trim();
        int split = value.lastIndexOf('/');
        if (split < 1 || split == value.length() - 1
                || (value.contains("://") && split <= value.indexOf("://") + 2)) throw invalid();
        return of(value.substring(0, split), value.substring(split + 1));
    }

    public static ServerConfig of(String host, String key) {
        if (host == null || key == null || key.trim().isEmpty() || key.length() > 512
                || !key.equals(key.trim()) || key.matches(".*[\\s/?#].*")) throw invalid();
        String address = host.trim();
        if (!address.contains("://")) address = "http://" + address;
        HttpUrl url = HttpUrl.parse(address);
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty()
                || url.query() != null || url.fragment() != null || url.host().equals("localhost")
                || url.host().equals("127.0.0.1") || url.host().equals("::1")) throw invalid();
        if (!url.encodedPath().endsWith("/")) url = url.newBuilder().addPathSegment("").build();
        return new ServerConfig(url, key);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("配置格式错误，请输入 域名/密钥 或 https://域名/路径/密钥；不要使用 localhost");
    }
}
