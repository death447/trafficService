package com.example.backend.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryNoGeneratorTest {

    private final EntryNoGenerator generator = new EntryNoGenerator();

    @Test
    void generatesNineteenDigitCodeWithHourPrefixAndOrderFactor() {
        String code = generator.generate(17L);

        assertEquals(19, code.length());
        assertTrue(code.matches("\\d{19}"));
        String expectedHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        assertEquals(expectedHour, code.substring(0, 10));
        assertEquals("0017", code.substring(10, 14));
    }

    @Test
    void padsOrderIdToFourDigits() {
        assertEquals("0007", generator.orderFactor(7L));
        assertEquals("0017", generator.orderFactor(17L));
        assertEquals("0999", generator.orderFactor(999L));
    }

    @Test
    void keepsLastFourDigitsWhenOrderIdExceedsFourDigits() {
        assertEquals("0017", generator.orderFactor(10017L));
        assertEquals("0217", generator.orderFactor(217L));
    }

    @Test
    void usesZeroFactorWhenOrderIsAbsent() {
        assertEquals("0000", generator.orderFactor(null));
        String code = generator.generate(null);
        assertEquals(19, code.length());
        assertEquals("0000", code.substring(10, 14));
    }

    @Test
    void randomPartVariesBetweenCalls() {
        String first = generator.generate(17L).substring(14);
        boolean differs = false;
        for (int i = 0; i < 20; i++) {
            if (!generator.generate(17L).substring(14).equals(first)) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "随机位应随时间变化的调用而不同");
    }

    @Test
    void matchesAcceptsGeneratedCodeForSameOrder() {
        Long orderId = 17L;
        assertTrue(generator.matches(generator.generate(orderId), orderId));
    }

    @Test
    void matchesRejectsCodeFromAnotherOrder() {
        String code = generator.generate(17L);
        assertFalse(generator.matches(code, 18L));
    }

    @Test
    void matchesRejectsLegacyFormatAndNull() {
        assertFalse(generator.matches("202609170001", 17L));
        assertFalse(generator.matches("", 17L));
        assertFalse(generator.matches(null, 17L));
    }

    @Test
    void generatesDifferentCodesAcrossOrders() {
        assertNotEquals(generator.generate(1L).substring(10, 14), generator.generate(2L).substring(10, 14));
    }
}
