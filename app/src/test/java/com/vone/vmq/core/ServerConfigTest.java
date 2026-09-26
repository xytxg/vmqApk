package com.vone.vmq.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerConfigTest {
    @Test public void legacyAndHttpsSubdirectory() {
        assertEquals("http://192.168.1.10:8080/", ServerConfig.parse("192.168.1.10:8080/secret").baseUrl.toString());
        ServerConfig config = ServerConfig.parse("https://example.com/vmq/secret");
        assertEquals("https://example.com/vmq/", config.baseUrl.toString()); assertEquals("secret", config.key);
    }
    @Test public void ipv6() { assertEquals("http://[2001:db8::1]:8080/", ServerConfig.parse("[2001:db8::1]:8080/key").baseUrl.toString()); }
    @Test public void invalidInputNeverBuildsRequest() {
        String[] inputs = {"", "abc", "https://example.com", "https://example.com/", "example.com/", "https://user:pass@example.com/key", "ftp://example.com/key", "localhost:8080/key", "https://example.com/?token=x/key", "https://example.com/#frag/key", "example.com/key key"};
        for (String input : inputs) {
            try { ServerConfig.parse(input); fail("Accepted invalid configuration"); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void signaturesMatchExactWireValues() {
        ServerConfig config = ServerConfig.parse("https://example.com/vmq/secret");
        PaymentParser.Payment payment = PaymentParser.parse("com.tencent.mm", "微信支付", "收款0.10元");
        okhttp3.HttpUrl url = Protocol.payment(config, payment, 123456L);
        assertEquals("/vmq/appPush", url.encodedPath());
        assertEquals("0.1", url.queryParameter("price"));
        assertEquals(Protocol.md5("10.1123456secret"), url.queryParameter("sign"));
        assertEquals("5d41402abc4b2a76b9719d911017c592", Protocol.md5("hello"));
        assertEquals("8.0", Protocol.payment(config, PaymentParser.parse("com.tencent.mm", "微信支付", "收款8元"), 1L).queryParameter("price"));
        assertEquals(Protocol.md5("123456secret"), Protocol.heartbeat(config, 123456L).queryParameter("sign"));
    }
}
