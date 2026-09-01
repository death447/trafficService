package com.example.backend.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultTest {

    @Test
    void errorWithCodeSetsHttpAlignedCode() {
        Result<Void> result = Result.error(401, "未登录或 token 无效");
        assertEquals(401, result.getCode());
        assertEquals("未登录或 token 无效", result.getMessage());
    }
}
