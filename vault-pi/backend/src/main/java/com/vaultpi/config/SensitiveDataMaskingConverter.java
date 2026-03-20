package com.vaultpi.config;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback 敏感数据遮掩：手机号、邮箱、订单金额等，避免 backend.log 泄露。
 * 在 logback-spring.xml 中注册为 conversionRule，pattern 使用 %maskedMsg 替代 %msg。
 */
public class SensitiveDataMaskingConverter extends MessageConverter {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern PHONE_GENERIC = Pattern.compile("\\b\\d{10,14}\\b");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final String PHONE_REPLACE = "***PHONE***";
    private static final String EMAIL_REPLACE = "***EMAIL***";

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null || msg.isEmpty()) return msg;
        String out = PHONE.matcher(msg).replaceAll(PHONE_REPLACE);
        out = PHONE_GENERIC.matcher(out).replaceAll(PHONE_REPLACE);
        out = EMAIL.matcher(out).replaceAll(EMAIL_REPLACE);
        return out;
    }
}
