package com.example.backend.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryNoGeneratorTest {

    private final EntryNoGenerator generator = new EntryNoGenerator();

    @Test
    void generatesFifteenDigitCodeWithHourPrefix() {
        String code = generator.generate();

        assertEquals(15, code.length());
        assertTrue(code.matches("\\d{15}"));
        String expectedHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        assertEquals(expectedHour, code.substring(0, 10));
    }

    @Test
    void randomPartVariesBetweenCalls() {
        String first = generator.generate().substring(10);
        boolean differs = false;
        for (int i = 0; i < 20; i++) {
            if (!generator.generate().substring(10).equals(first)) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "随机位应随每次调用而变化");
    }

    @Test
    void matchesAcceptsGeneratedCode() {
        assertTrue(generator.matches(generator.generate()));
    }

    @Test
    void matchesRejectsLegacyAndWrongLengthCodes() {
        assertFalse(generator.matches("202609170001"));
        assertFalse(generator.matches("2026102714001748291"));
        assertFalse(generator.matches("2026102714183"));
        assertFalse(generator.matches(""));
        assertFalse(generator.matches(null));
    }
}
