package com.vone.vmq.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import okhttp3.HttpUrl;

public final class Protocol {
    private Protocol() {}
    public static String md5(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : hash) hex.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static HttpUrl heartbeat(ServerConfig config, long time) {
        String t = Long.toString(time);
        return config.baseUrl.newBuilder().addPathSegment("appHeart")
                .addQueryParameter("t", t).addQueryParameter("sign", md5(t + config.key)).build();
    }
    public static HttpUrl payment(ServerConfig config, PaymentParser.Payment payment, long time) {
        String t = Long.toString(time);
        // The legacy server also uses Java Double strings for temporary-price keys.
        String price = Double.toString(Double.parseDouble(payment.amount));
        return config.baseUrl.newBuilder().addPathSegment("appPush")
                .addQueryParameter("t", t).addQueryParameter("type", Integer.toString(payment.type))
                .addQueryParameter("price", price)
                .addQueryParameter("sign", md5(payment.type + price + t + config.key)).build();
    }
}
