package com.vone.vmq.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentParserTest {
    @Test public void alipayNumericNicknameAndTrailingNumbers() {
        PaymentParser.Payment payment = PaymentParser.parse("com.eg.android.AlipayGphone", "支付宝", "123456通过扫码向你付款12.30元，交易号987654");
        assertNotNull(payment); assertEquals(2, payment.type); assertEquals("12.30", payment.amount);
    }
    @Test public void wechatMerchant() {
        PaymentParser.Payment payment = PaymentParser.parse("com.tencent.mm", "微信收款商业版", "微信支付收款到账1,234.56元");
        assertNotNull(payment); assertEquals(1, payment.type); assertEquals("1234.56", payment.amount);
    }
    @Test public void integerAmountUsesExactDecimal() {
        assertEquals("8.00", PaymentParser.parse("com.tencent.mm", "微信支付", "微信支付收款8元").amount);
    }
    @Test public void rejectsUnrelatedOrAmbiguousMessages() {
        String[] texts = {"付款成功20元", "退款到账20元", "成功收款0元", "成功收款-10元", "成功收款1.234元", "成功收款1.2.3元", "成功收款10元，成功收款20元", "成功收款10元，今日合计30元", "订单号12345", "待收款20元", "成功收款9999999999元"};
        for (String text : texts) assertNull(text, PaymentParser.parse("com.tencent.mm", "微信支付", text));
    }
    @Test public void rejectsNonPaymentAccountCredits() {
        assertNull(PaymentParser.parse("com.eg.android.AlipayGphone", "支付宝", "红包到账10元"));
        assertNull(PaymentParser.parse("com.tencent.mm", "微信支付", "提现到账100元"));
    }
    @Test public void rejectsWrongSenderAndNullContent() {
        assertNull(PaymentParser.parse("attacker.app", "微信支付", "收款1元"));
        assertNull(PaymentParser.parse("com.tencent.mm", "普通联系人", "收款1元"));
        assertNull(PaymentParser.parse("com.tencent.mm", "微信支付", null));
    }
}
