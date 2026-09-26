package com.vone.vmq.core;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative matching: a payment verb followed directly by a currency amount. */
public final class PaymentParser {
    private static final String NUMBER = "([0-9]{1,9}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)";
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:通过扫码向你付款|成功收款|收款到账|收款|到账|向你付款)\\s*[:：]?\\s*(?:人民币\\s*)?(?:[¥￥]\\s*)?"
                    + NUMBER + "\\s*元");
    private PaymentParser() {}

    public static Payment parse(String pkg, String title, String text) {
        if (text == null || text.length() > 8192) return null;
        int type;
        if ("com.eg.android.AlipayGphone".equals(pkg)) {
            if (!text.contains("通过扫码向你付款") && !text.contains("成功收款")
                    && !text.contains("收款到账")) return null;
            type = 2;
        }
        else if ("com.tencent.mm".equals(pkg) && ("微信支付".equals(title)
                || "微信收款助手".equals(title) || "微信收款商业版".equals(title))) type = 1;
        else return null;
        if (text.contains("退款") || text.contains("退还") || text.contains("失败")
                || text.contains("提现") || text.contains("转出") || text.contains("支出")
                || text.contains("待收款") || text.contains("汇总") || text.contains("合计")) return null;
        Matcher matcher = AMOUNT.matcher(text);
        if (!matcher.find()) return null;
        BigDecimal amount = new BigDecimal(matcher.group(1).replace(",", ""));
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("999999999.99")) > 0
                || matcher.find()) return null;
        return new Payment(type, amount.setScale(2).toPlainString());
    }

    public static final class Payment {
        public final int type;
        public final String amount;
        Payment(int type, String amount) { this.type = type; this.amount = amount; }
    }
}
