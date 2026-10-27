package com.example.backend.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 入场编码生成器。
 *
 * <p>格式：{@code yyyyMMddHH}(10) + 工单因子(4) + 随机数字(5) = 19 位纯数字。
 * 例：工单 17 在 2026-10-27 14 点生成 → {@code 2026102714001748291}。
 */
@Component
public class EntryNoGenerator {

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Pattern FORMAT = Pattern.compile("^\\d{19}$");
    private static final int RANDOM_BOUND = 100000;
    private static final String NO_ORDER_FACTOR = "0000";

    private final SecureRandom random = new SecureRandom();

    public String generate(Long orderId) {
        return LocalDateTime.now().format(HOUR_FORMAT)
                + orderFactor(orderId)
                + String.format("%05d", random.nextInt(RANDOM_BOUND));
    }

    public boolean matches(String code, Long orderId) {
        if (code == null || !FORMAT.matcher(code).matches()) {
            return false;
        }
        return orderFactor(orderId).equals(code.substring(10, 14));
    }

    String orderFactor(Long orderId) {
        if (orderId == null) {
            return NO_ORDER_FACTOR;
        }
        long factor = Math.abs(orderId) % 10000L;
        return String.format("%04d", factor);
    }
}
