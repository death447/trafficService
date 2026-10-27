package com.example.backend.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 入场编码生成器。
 *
 * <p>格式：{@code yyyyMMddHH}(10) + 随机数字(5) = 15 位纯数字。
 * 例：2026-10-27 14 点生成 → {@code 202610271418370}。
 */
@Component
public class EntryNoGenerator {

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Pattern FORMAT = Pattern.compile("^\\d{15}$");
    private static final int RANDOM_BOUND = 100000;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return LocalDateTime.now().format(HOUR_FORMAT)
                + String.format("%05d", random.nextInt(RANDOM_BOUND));
    }

    public boolean matches(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }
}
