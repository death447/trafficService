package com.example.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void disabledExceptionReturns401AndStopMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Result<Void> result = handler.disabled(new DisabledException("disabled"));
        assertEquals(401, result.getCode());
        assertEquals("账号已停用", result.getMessage());
    }
}
